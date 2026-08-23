const REJECT_REASON_LABEL = {
  NCH: '체인 불일치',
  DUP: '이미 사용한 단어',
  NFD: '사용 가능한 단어 없음',
  RUL: '이 게임에서 사용할 수 없는 단어',
  MNR: '매너/안전 규칙에 맞지 않는 단어',
  TUR: '현재 턴이 아님',
  OTH: '입력 오류'
};

const BEAT = [null, '10000000', '10001000', '10010010', '10011010', '11011010', '11011110', '11011111', '11111111'];
const SUPPORTED_MODE_CODES = new Set(['EKT', 'ESH', 'EAP', 'KKT', 'KFT', 'KSH', 'KAP', 'HUN', 'KDA', 'EDA']);
const SUPPORTED_RULES = new Set(['Classic', 'Daneo', 'Hunmin']);
const REVERSE_MODE_CODES = new Set(['KAP', 'EAP']);
const KKT_MODE_CODES = new Set(['KKT', 'KFT']);

export function getReplaySupport(detail) {
  const payload = detail?.replayView?.payload;
  if (!payload) return { supported: false, boardType: 'unavailable' };
  const modeCode = String(payload.rm?.[2] || detail?.modeName || '');
  const rule = String(payload.rm?.[3] || detail?.rule || '');
  const supported = SUPPORTED_RULES.has(rule) || SUPPORTED_MODE_CODES.has(modeCode);
  return { supported, boardType: supported ? 'chain' : 'unavailable' };
}

function numberOr(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function playerName(players, index) {
  return players[index]?.nickname || `Player#${index}`;
}

function eventExtra(extras, index) {
  const raw = numberOr(index, -1) >= 0 ? String(extras[numberOr(index, -1)] || '') : '';
  return raw ? raw.split(',') : [];
}

function inputEvent(row, words, extras, players) {
  if (!Array.isArray(row)) return null;
  const playerIndex = numberOr(row[0]);
  const tokens = eventExtra(extras, row[6]);
  const tag = tokens[0] || '';
  const rawWord = words[numberOr(row[1], -1)] || '(알 수 없음)';
  let description = '';
  let scoreDelta = 0;
  let baseScore = 0;
  let bonusScore = 0;
  let accepted = tag !== 'CR';
  const rejected = tag === 'CR';
  const rejectReason = rejected ? (tokens[1] || 'OTH') : '';

  if (tag === 'CA') {
    scoreDelta = numberOr(tokens[1]);
    bonusScore = numberOr(tokens[2]);
    baseScore = scoreDelta - bonusScore;
    description = `인정 +${baseScore}${bonusScore ? ` · 미션 +${bonusScore}` : ''}`;
  } else if (tag === 'CR') {
    description = REJECT_REASON_LABEL[rejectReason] || REJECT_REASON_LABEL.OTH;
  } else if (tag === 'D') {
    description = `정답 · 힌트 ${numberOr(tokens[2])}개 · 화가 ${playerName(players, numberOr(tokens[1], -1))}`;
  } else if (tag === 'J') {
    description = `정답 · 힌트 ${numberOr(tokens[1])}개`;
  } else if (tag === 'C') {
    accepted = numberOr(tokens[5]) === 1;
    description = `${accepted ? '정답' : '오답'} · 보드 ${numberOr(tokens[1])}`;
  } else {
    description = '입력';
  }

  return {
    time: Math.max(0, numberOr(row[3])),
    elapsedTurnMs: Math.max(0, numberOr(row[2])),
    round: Math.max(1, numberOr(row[4], 1)),
    turn: numberOr(row[5], -1),
    playerIndex,
    playerName: playerName(players, playerIndex),
    label: rawWord,
    description,
    scoreDelta,
    baseScore,
    bonusScore,
    accepted,
    rejected,
    rejectReason,
    errorCode: rejected ? numberOr(tokens[2]) : 0,
    showsFailure: rejected && rejectReason !== 'NCH' && rejectReason !== 'TUR',
    kind: 'input'
  };
}

function modeEvent(row, extras, players) {
  if (!Array.isArray(row)) return null;
  const type = String(row[0] || '');
  const playerIndex = numberOr(row[1]);
  const tokens = eventExtra(extras, row[4]);
  let label = '';
  let description = '';
  let scoreDelta = 0;
  let rejected = false;

  if (type === 'CTO' || type === 'CAS') {
    scoreDelta = numberOr(tokens[1]);
    rejected = type === 'CTO' && scoreDelta <= 0;
    label = scoreDelta > 0 ? '공격 성공' : '입력 실패';
    description = `점수 ${scoreDelta > 0 ? '+' : ''}${scoreDelta}`;
  } else if (type === 'CRJ') {
    const reason = tokens[1] || 'OTH';
    rejected = true;
    label = REJECT_REASON_LABEL[reason] || REJECT_REASON_LABEL.OTH;
    description = label;
  } else if (type === 'CIT') {
    label = `아이템 ${numberOr(tokens[1], -1)} 사용`;
    description = numberOr(tokens[2]) === 1 ? '턴 종료' : '아이템전';
  } else {
    return null;
  }

  return {
    time: Math.max(0, numberOr(row[3])),
    elapsedTurnMs: 0,
    round: Math.max(1, numberOr(row[2], 1)),
    turn: -1,
    playerIndex,
    playerName: playerName(players, playerIndex),
    label,
    description,
    scoreDelta,
    baseScore: scoreDelta,
    bonusScore: 0,
    accepted: !rejected,
    rejected,
    rejectReason: type === 'CRJ' ? (tokens[1] || 'OTH') : '',
    errorCode: type === 'CRJ' ? numberOr(tokens[2]) : 0,
    showsFailure: type === 'CRJ' && tokens[1] !== 'NCH' && tokens[1] !== 'TUR',
    kind: type
  };
}

function decodeOutfit(outfit) {
  if (!Array.isArray(outfit)) return {};
  const decoded = {};
  for (let index = 0; index + 1 < outfit.length; index += 2) {
    const key = String(outfit[index] || '');
    const value = String(outfit[index + 1] || '');
    if (key && value) decoded[key] = value;
  }
  return decoded;
}

function normalizedPlayers(replayView) {
  const rankingByIndex = new Map((replayView?.ranking || []).map((row) => [numberOr(row.playerIndex), row]));
  return (replayView?.players || []).map((player, index) => ({
    ...player,
    index: numberOr(player.index, index),
    nickname: player.nickname || player.id || `Player#${index}`,
    outfit: decodeOutfit(player.outfit),
    finalScore: numberOr(rankingByIndex.get(numberOr(player.index, index))?.score),
    placement: numberOr(rankingByIndex.get(numberOr(player.index, index))?.placement)
  }));
}

function getTurnSpeed(roundTimeMs) {
  if (roundTimeMs < 5000) return 10;
  if (roundTimeMs < 11000) return 9;
  if (roundTimeMs < 18000) return 8;
  if (roundTimeMs < 26000) return 7;
  if (roundTimeMs < 35000) return 6;
  if (roundTimeMs < 45000) return 5;
  if (roundTimeMs < 56000) return 4;
  if (roundTimeMs < 68000) return 3;
  if (roundTimeMs < 81000) return 2;
  if (roundTimeMs < 95000) return 1;
  return 0;
}

function initialRoundTitles(raw, totalRounds) {
  const values = Array.isArray(raw) ? raw : (raw ? [raw] : []);
  return Array.from({ length: totalRounds }, (_, index) => String(values[index] ?? index + 1));
}

function promptFromWord(word, reverse) {
  const value = String(word || '');
  if (!value) return '';
  return reverse ? value.charAt(0) : value.charAt(value.length - 1);
}

function findOrCreateTurn(turns, event) {
  const inferredStart = Math.max(0, event.time - event.elapsedTurnMs);
  let turn = turns.find((candidate) => candidate.round === event.round
    && candidate.turnIndex === event.turn
    && Math.abs(candidate.startTime - inferredStart) < 80);
  if (!turn) {
    turn = {
      id: `r${event.round}-t${event.turn}-${Math.round(inferredStart)}`,
      round: event.round,
      turnIndex: event.turn,
      playerIndex: event.playerIndex,
      startTime: inferredStart,
      events: []
    };
    turns.push(turn);
  }
  turn.events.push(event);
  return turn;
}

function revealSchedule(word, turnTimeMs, lang) {
  const text = String(word || '');
  const tickMs = turnTimeMs / 96;
  const spellMs = turnTimeMs / 12;
  const beat = BEAT[text.length];
  if (beat) {
    const schedule = [];
    let letterIndex = 0;
    [...beat].forEach((flag, beatIndex) => {
      if (flag !== '1' || letterIndex >= text.length) return;
      schedule.push({ letterIndex, at: beatIndex * tickMs });
      letterIndex++;
    });
    return schedule;
  }
  const usesSpeedKey = lang === 'en' && text.length < 10;
  return [...text].map((_, letterIndex) => ({
    letterIndex,
    at: letterIndex * spellMs / Math.max(1, text.length),
    sound: usesSpeedKey ? null : 'Al'
  }));
}

function buildTurnsAndCues(events, model) {
  const turns = [];
  const reverse = REVERSE_MODE_CODES.has(model.modeCode);

  for (const event of events) {
    if (event.kind === 'input') findOrCreateTurn(turns, event);
  }
  turns.sort((a, b) => a.startTime - b.startTime || a.turnIndex - b.turnIndex);

  // Timeout rows do not store elapsedTurnMs. Reconstruct their turn start from
  // the same post-answer delay used by the game server before inserting them.
  let estimateRound = -1;
  let estimateRemaining = model.roomTimeMs;
  let estimateChain = 0;
  for (const turn of turns) {
    if (turn.round !== estimateRound) {
      estimateRound = turn.round;
      estimateRemaining = model.roomTimeMs;
      estimateChain = 0;
    }
    estimateRemaining = Math.min(estimateRemaining, Math.max(10000, 150000 - estimateChain * 1500));
    const estimateSpeed = getTurnSpeed(estimateRemaining);
    const estimateTurnTime = 15000 - 1400 * estimateSpeed;
    const accepted = turn.events.find((event) => event.kind === 'input' && event.accepted);
    turn.transitionEnd = accepted ? accepted.time + estimateTurnTime / 6 : turn.startTime + estimateTurnTime;
    if (accepted) {
      estimateRemaining = Math.max(0, estimateRemaining - accepted.elapsedTurnMs);
      estimateChain++;
    }
  }

  for (const timeout of events.filter((event) => event.kind === 'CTO')) {
    const existing = turns.find((turn) => turn.round === timeout.round
      && turn.playerIndex === timeout.playerIndex
      && turn.startTime <= timeout.time
      && !turn.events.some((event) => event.accepted));
    if (existing) {
      existing.events.push(timeout);
      continue;
    }
    const previous = [...turns].reverse().find((turn) => turn.round === timeout.round && turn.startTime < timeout.time);
    turns.push({
      id: `r${timeout.round}-timeout-${Math.round(timeout.time)}`,
      round: timeout.round,
      turnIndex: -1,
      playerIndex: timeout.playerIndex,
      startTime: previous?.transitionEnd ?? Math.max(0, timeout.time - 15000),
      events: [timeout]
    });
  }
  turns.sort((a, b) => a.startTime - b.startTime || a.turnIndex - b.turnIndex);

  const audioCues = [];
  const roundReadyTimes = new Map();
  const roundEndTimes = new Map();
  let lastRound = -1;
  let roundRemaining = model.roomTimeMs;
  let chainCount = 0;
  let previousAcceptedWord = '';

  for (const turn of turns) {
    if (turn.round !== lastRound) {
      lastRound = turn.round;
      roundRemaining = model.roomTimeMs;
      chainCount = 0;
      previousAcceptedWord = '';
      const readyTime = Math.max(0, turn.startTime - 2400);
      roundReadyTimes.set(turn.round, readyTime);
      audioCues.push({ id: `round-${turn.round}`, time: readyTime, sound: 'round_start', type: 'effect' });
    }

    roundRemaining = Math.min(roundRemaining, Math.max(10000, 150000 - chainCount * 1500));
    turn.speed = getTurnSpeed(roundRemaining);
    turn.turnTimeMs = 15000 - 1400 * turn.speed;
    turn.roundTimeAtStart = roundRemaining;
    turn.prompt = previousAcceptedWord
      ? promptFromWord(previousAcceptedWord, reverse)
      : model.roundTitles[turn.round - 1] || '';
    turn.endEvent = turn.events.find((event) => event.kind === 'input' && event.accepted)
      || turn.events.find((event) => event.kind === 'CTO')
      || null;
    turn.endTime = turn.endEvent?.time ?? Math.min(model.durationMs, turn.startTime + Math.min(turn.turnTimeMs, roundRemaining));
    turn.transitionEnd = turn.endEvent?.kind === 'input'
      ? Math.min(model.durationMs, turn.endTime + turn.turnTimeMs / 6)
      : turn.endTime;
    turn.roundTimeAfter = turn.endEvent?.kind === 'input'
      ? Math.max(0, roundRemaining - turn.endEvent.elapsedTurnMs)
      : Math.max(0, roundRemaining - Math.max(0, turn.endTime - turn.startTime));

    audioCues.push({
      id: `turn-${turn.id}`,
      time: turn.startTime,
      endTime: turn.endTime,
      sound: `T${turn.speed}`,
      type: 'turn',
      turnId: turn.id
    });

    for (const event of turn.events) {
      event.turnId = turn.id;
      event.speed = turn.speed;
      event.turnTimeMs = turn.turnTimeMs;
      event.turnStartTime = turn.startTime;
      event.roundTimeAtStart = turn.roundTimeAtStart;
      event.roundTimeAfter = event.accepted ? turn.roundTimeAfter : turn.roundTimeAtStart;

      if (event.rejected && event.showsFailure) {
        audioCues.push({ id: `fail-${event.id}`, time: event.time, sound: 'fail', type: 'effect' });
      }
      if (event.kind !== 'input' || !event.accepted) continue;

      event.tickMs = turn.turnTimeMs / 96;
      event.spellMs = turn.turnTimeMs / 12;
      event.transitionEnd = turn.transitionEnd;
      event.revealSchedule = revealSchedule(event.label, turn.turnTimeMs, model.lang);
      for (const item of event.revealSchedule) {
        audioCues.push({
          id: `letter-${event.id}-${item.letterIndex}`,
          time: event.time + item.at,
          sound: item.sound || `As${turn.speed}`,
          type: 'effect'
        });
      }
      if (event.bonusScore > 0) {
        audioCues.push({ id: `mission-${event.id}`, time: event.time, sound: 'mission', type: 'effect' });
      }
      if (KKT_MODE_CODES.has(model.modeCode)) {
        audioCues.push({ id: `kung-a-${event.id}`, time: event.time + event.spellMs, sound: 'kung', type: 'effect' });
        audioCues.push({ id: `kung-b-${event.id}`, time: event.time + event.spellMs + event.tickMs * 4, sound: 'kung', type: 'effect' });
      } else {
        audioCues.push({ id: `finish-${event.id}`, time: event.time + event.spellMs, sound: `K${turn.speed}`, type: 'effect' });
      }
      event.historyAt = event.time + event.spellMs + event.tickMs * 4;
    }

    if (turn.endEvent?.kind === 'input' && turn.endEvent.accepted) {
      roundRemaining = turn.roundTimeAfter;
      chainCount++;
      previousAcceptedWord = turn.endEvent.label;
    }
    if (turn.endEvent?.kind === 'CTO') {
      audioCues.push({ id: `timeout-${turn.endEvent.id}`, time: turn.endEvent.time, sound: 'timeout', type: 'effect' });
      roundRemaining = turn.roundTimeAfter;
    }
    roundEndTimes.set(turn.round, Math.max(roundEndTimes.get(turn.round) || 0, turn.transitionEnd));
  }

  for (const event of events) {
    if (event.kind === 'CRJ' && event.showsFailure) {
      audioCues.push({ id: `fail-${event.id}`, time: event.time, sound: 'fail', type: 'effect' });
    }
  }

  audioCues.sort((a, b) => a.time - b.time || a.id.localeCompare(b.id));
  return { turns, audioCues, roundReadyTimes, roundEndTimes };
}

function failureText(event) {
  const prefix = REJECT_REASON_LABEL[event.rejectReason] || REJECT_REASON_LABEL.OTH;
  if (event.kind === 'CRJ') return prefix;
  return event.label && event.label !== '(알 수 없음)' ? `${prefix}: ${event.label}` : prefix;
}

function scoreAnimationValue(before, after, elapsedMs) {
  if (elapsedMs <= 0) return before;
  const frames = Math.floor(elapsedMs / (1000 / 60));
  const remainder = (after - before) * Math.pow(0.8, frames);
  return Math.abs(remainder) < 1 ? after : Math.round(after - remainder);
}

export function createReplayModel(detail) {
  const replayView = detail?.replayView;
  const payload = replayView?.payload;
  if (!payload || !Array.isArray(replayView?.players) || replayView.players.length < 1) return null;

  const players = normalizedPlayers(replayView);
  const support = getReplaySupport(detail);
  const words = Array.isArray(payload.w) ? payload.w : [];
  const extras = Array.isArray(payload.x) ? payload.x : [];
  const events = [];

  for (const row of Array.isArray(payload.i) ? payload.i : []) {
    const event = inputEvent(row, words, extras, players);
    if (event) events.push(event);
  }
  for (const row of Array.isArray(payload.mv) ? payload.mv : []) {
    const event = modeEvent(row, extras, players);
    if (event) events.push(event);
  }
  events.sort((a, b) => a.time - b.time || a.playerIndex - b.playerIndex);
  events.forEach((event, index) => { event.id = `event-${index}`; });

  const runningScores = players.map(() => 0);
  for (const event of events) {
    event.scoreBefore = numberOr(runningScores[event.playerIndex]);
    runningScores[event.playerIndex] = event.scoreBefore + numberOr(event.scoreDelta);
    event.scoreAfter = runningScores[event.playerIndex];
  }

  const lastEventTime = events.length ? events[events.length - 1].time : 0;
  const durationMs = Math.max(1, numberOr(detail?.durationMs), numberOr(payload.d), lastEventTime);
  const totalRounds = Math.max(1, numberOr(payload.rm?.[5], 1));
  const roomTimeMs = Math.max(1000, numberOr(payload.rm?.[6], 60) * 1000);
  const model = {
    gameId: detail?.gameId || payload.g || '',
    roomTitle: detail?.roomTitle || payload.rm?.[7] || '제목 없음',
    modeName: detail?.modeName || payload.rm?.[2] || '',
    rule: detail?.rule || payload.rm?.[3] || '',
    lang: detail?.lang || payload.rm?.[4] || '',
    modeCode: String(payload.rm?.[2] || detail?.modeName || ''),
    boardType: support.boardType,
    totalRounds,
    roomTimeMs,
    roundTitles: initialRoundTitles(payload.rm?.[8], totalRounds),
    durationMs,
    players,
    events
  };
  Object.assign(model, buildTurnsAndCues(events, model));
  return model;
}

export function getReplayState(model, requestedTimeMs) {
  if (!model) return null;
  const timeMs = Math.min(model.durationMs, Math.max(0, numberOr(requestedTimeMs)));
  const scores = model.players.map(() => 0);
  const visibleEvents = [];
  const scorePopups = [];
  let activeEvent = null;

  for (const event of model.events) {
    if (event.time > timeMs) break;
    activeEvent = event;
    scores[event.playerIndex] = numberOr(scores[event.playerIndex]) + numberOr(event.scoreDelta);
    if (event.kind === 'input' && event.accepted && timeMs >= (event.historyAt ?? event.time)) visibleEvents.unshift(event);

    if (event.baseScore && timeMs < event.time + 2000) {
      scorePopups.push({ id: `${event.id}-base`, playerIndex: event.playerIndex, value: event.baseScore, bonus: false });
    }
    if (event.bonusScore && timeMs >= event.time + 500 && timeMs < event.time + 2500) {
      scorePopups.push({ id: `${event.id}-bonus`, playerIndex: event.playerIndex, value: event.bonusScore, bonus: true });
    }
  }
  visibleEvents.splice(6);

  for (let playerIndex = 0; playerIndex < scores.length; playerIndex++) {
    const recent = [...model.events].reverse().find((event) => event.playerIndex === playerIndex
      && event.scoreDelta && event.time <= timeMs);
    if (recent && timeMs - recent.time < 1000) {
      scores[playerIndex] = scoreAnimationValue(recent.scoreBefore, recent.scoreAfter, timeMs - recent.time);
    }
  }
  if (timeMs >= model.durationMs) {
    for (const player of model.players) scores[player.index] = player.finalScore;
  }

  const activeTurn = model.turns.find((turn) => timeMs >= turn.startTime && timeMs < turn.endTime) || null;
  const lastTurn = [...model.turns].reverse().find((turn) => turn.startTime <= timeMs) || null;
  const currentRound = activeTurn?.round || lastTurn?.round || model.turns[0]?.round || 1;
  const latestFailure = [...model.events].reverse().find((event) => event.showsFailure
    && event.time <= timeMs && timeMs < event.time + 1800);
  const latestAccepted = [...model.events].reverse().find((event) => event.kind === 'input' && event.accepted
    && event.time <= timeMs && timeMs < (event.transitionEnd ?? event.time));

  let displayMode = 'prompt';
  let displayText = activeTurn?.prompt || lastTurn?.prompt || model.roundTitles[currentRound - 1] || '';
  let displayLetters = [];
  if (latestFailure && (!latestAccepted || latestFailure.time > latestAccepted.time)) {
    displayMode = 'failure';
    displayText = failureText(latestFailure);
  } else if (latestAccepted) {
    displayMode = 'letters';
    displayText = latestAccepted.label;
    const elapsed = timeMs - latestAccepted.time;
    const visibleIndices = new Set((latestAccepted.revealSchedule || [])
      .filter((item) => item.at <= elapsed)
      .map((item) => item.letterIndex));
    displayLetters = [...latestAccepted.label].map((char, index) => ({ char, visible: visibleIndices.has(index), index }));
  } else if (timeMs >= model.durationMs) {
    displayText = '경기 종료';
  }

  const timerTurn = activeTurn || (lastTurn && timeMs < lastTurn.transitionEnd ? lastTurn : null);
  let turnRemainingMs = 0;
  let turnTotalMs = timerTurn?.turnTimeMs || 1;
  let roundRemainingMs = model.roomTimeMs;
  if (timerTurn) {
    if (activeTurn) {
      const elapsed = Math.max(0, timeMs - activeTurn.startTime);
      turnRemainingMs = Math.max(0, Math.min(activeTurn.turnTimeMs, activeTurn.roundTimeAtStart) - elapsed);
      roundRemainingMs = Math.max(0, activeTurn.roundTimeAtStart - elapsed);
    } else {
      const elapsed = timerTurn.endEvent?.elapsedTurnMs ?? Math.max(0, timerTurn.endTime - timerTurn.startTime);
      turnRemainingMs = Math.max(0, Math.min(timerTurn.turnTimeMs, timerTurn.roundTimeAtStart) - elapsed);
      roundRemainingMs = timerTurn.roundTimeAfter;
    }
  } else {
    const completed = [...model.turns].reverse().find((turn) => turn.round === currentRound && turn.endTime <= timeMs);
    if (completed) roundRemainingMs = completed.roundTimeAfter;
  }

  const acceptedCount = model.events.filter((event) => event.kind === 'input' && event.accepted
    && event.round === currentRound && event.time <= timeMs).length;

  return {
    timeMs,
    scores,
    activeEvent,
    activeTurn,
    visibleEvents,
    scorePopups,
    currentRound,
    acceptedCount,
    displayMode,
    displayText,
    displayLetters,
    turnRemainingMs,
    roundRemainingMs,
    turnRatio: Math.min(1, Math.max(0, turnRemainingMs / Math.max(1, turnTotalMs))),
    roundRatio: Math.min(1, Math.max(0, roundRemainingMs / Math.max(1, model.roomTimeMs)))
  };
}

export function formatReplayTime(ms) {
  const totalSeconds = Math.max(0, Math.floor(numberOr(ms) / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}
