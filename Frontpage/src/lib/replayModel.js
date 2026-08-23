const REJECT_REASON_LABEL = {
  NCH: '체인 불일치',
  DUP: '중복 단어',
  NFD: '사전에 없음',
  RUL: '규칙 차단',
  MNR: '매너/안전 차단',
  TUR: '턴 아님',
  OTH: '기타'
};

const SUPPORTED_MODE_CODES = new Set(['EKT', 'ESH', 'EAP', 'KKT', 'KFT', 'KSH', 'KAP', 'HUN', 'KDA', 'EDA']);
const SUPPORTED_RULES = new Set(['Classic', 'Daneo', 'Hunmin']);

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
  let label = rawWord;
  let description = '';
  let scoreDelta = 0;
  let accepted = tag !== 'CR';
  let rejected = tag === 'CR';

  if (tag === 'CA') {
    const score = numberOr(tokens[1]);
    const bonus = numberOr(tokens[2]);
    scoreDelta = score + bonus;
    description = `인정 +${score}${bonus ? ` · 미션 +${bonus}` : ''}`;
  } else if (tag === 'CR') {
    description = REJECT_REASON_LABEL[tokens[1] || 'OTH'] || REJECT_REASON_LABEL.OTH;
  } else if (tag === 'D') {
    description = `정답 · 힌트 ${numberOr(tokens[2])}개 · 화가 ${playerName(players, numberOr(tokens[1], -1))}`;
  } else if (tag === 'J') {
    description = `정답 · 힌트 ${numberOr(tokens[1])}개`;
  } else if (tag === 'C') {
    accepted = numberOr(tokens[5]) === 1;
    rejected = !accepted;
    description = `${accepted ? '정답' : '오답'} · 보드 ${numberOr(tokens[1])}`;
  } else {
    description = '입력';
  }

  return {
    time: Math.max(0, numberOr(row[3])),
    elapsedTurnMs: Math.max(0, numberOr(row[2])),
    round: Math.max(0, numberOr(row[4])),
    turn: numberOr(row[5], -1),
    playerIndex,
    playerName: playerName(players, playerIndex),
    label,
    description,
    scoreDelta,
    accepted,
    rejected,
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
  let path = [];
  let targetPlayerId = '';
  let summary = null;

  if (type === 'TPM') {
    label = `${numberOr(tokens[1])} 타/분`;
    description = '타자 속도';
    summary = { tpm: numberOr(tokens[1]) };
  } else if (type === 'MQR') {
    label = `정답 ${numberOr(tokens[1])} · 오답 ${numberOr(tokens[2])}`;
    description = '수학 대결 결과';
    summary = { correct: numberOr(tokens[1]), wrong: numberOr(tokens[2]) };
  } else if (type === 'SOK') {
    label = tokens[1] || '(빈 단어)';
    description = '단어 조합';
  } else if (type === 'WSA') {
    label = tokens[1] || '(빈 단어)';
    targetPlayerId = tokens[2] || '';
    description = `공격 → ${players.find((player) => player.id === targetPlayerId)?.nickname || targetPlayerId || '-'}`;
  } else if (type === 'WSS') {
    label = `${numberOr(tokens[1])}단어 · ${numberOr(tokens[2])}글자`;
    description = '제작 결과';
    summary = { words: numberOr(tokens[1]), letters: numberOr(tokens[2]) };
  } else if (type === 'LNK') {
    label = tokens[0] || '(빈 단어)';
    path = tokens.slice(1).map((value) => numberOr(value, -1)).filter((value) => value >= 0);
    description = `글자판 연결 · ${path.length}칸`;
  } else if (type === 'LNR') {
    label = tokens[0] || '(빈 단어)';
    path = [numberOr(tokens[1], -1)].filter((value) => value >= 0);
    description = `글자 교체 · ${tokens[2] || '?'} → ${tokens[3] || '?'}`;
    summary = { previous: tokens[2] || '', replacement: tokens[3] || '' };
  } else if (type === 'CTO' || type === 'CAS') {
    scoreDelta = numberOr(tokens[1]);
    rejected = type === 'CTO' && scoreDelta <= 0;
    label = scoreDelta > 0 ? '공격 성공' : '입력 실패';
    description = `점수 ${scoreDelta > 0 ? '+' : ''}${scoreDelta}`;
  } else if (type === 'CIT') {
    label = `아이템 ${numberOr(tokens[1], -1)} 사용`;
    description = numberOr(tokens[2]) === 1 ? '턴 종료' : '아이템전';
  } else {
    return null;
  }

  return {
    time: Math.max(0, numberOr(row[3])),
    elapsedTurnMs: 0,
    round: Math.max(0, numberOr(row[2])),
    turn: -1,
    playerIndex,
    playerName: playerName(players, playerIndex),
    label,
    description,
    scoreDelta,
    accepted: !rejected,
    rejected,
    kind: type,
    path,
    targetPlayerId,
    summary
  };
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

  const lastEventTime = events.length ? events[events.length - 1].time : 0;
  const durationMs = Math.max(1, numberOr(detail?.durationMs), numberOr(payload.d), lastEventTime);
  const roundStarts = new Map();
  const roundEnds = new Map();
  for (const event of events) {
    if (!roundStarts.has(event.round)) roundStarts.set(event.round, event.time);
    roundEnds.set(event.round, event.time);
  }

  return {
    gameId: detail?.gameId || payload.g || '',
    roomTitle: detail?.roomTitle || payload.rm?.[7] || '제목 없음',
    modeName: detail?.modeName || payload.rm?.[2] || '',
    rule: detail?.rule || payload.rm?.[3] || '',
    lang: detail?.lang || payload.rm?.[4] || '',
    modeCode: String(payload.rm?.[2] || detail?.modeName || ''),
    boardType: support.boardType,
    totalRounds: Math.max(1, numberOr(payload.rm?.[5], 1)),
    durationMs,
    players,
    events,
    roundStarts,
    roundEnds
  };
}

export function getReplayState(model, requestedTimeMs) {
  if (!model) return null;
  const timeMs = Math.min(model.durationMs, Math.max(0, numberOr(requestedTimeMs)));
  const scores = model.players.map(() => 0);
  const visibleEvents = [];
  const boardEvents = [];
  let activeEvent = null;
  let nextEvent = null;
  let acceptedCount = 0;

  for (const event of model.events) {
    if (event.time > timeMs) {
      nextEvent = event;
      break;
    }
    activeEvent = event;
    scores[event.playerIndex] = numberOr(scores[event.playerIndex]) + numberOr(event.scoreDelta);
    if (event.accepted && event.kind === 'input') acceptedCount++;
    visibleEvents.push(event);
    if (event.kind !== 'input') boardEvents.push(event);
    if (visibleEvents.length > 5) visibleEvents.shift();
  }

  if (timeMs >= model.durationMs) {
    for (const player of model.players) scores[player.index] = player.finalScore;
  }

  const currentRound = activeEvent?.round || nextEvent?.round || 1;
  const roundStart = model.roundStarts.get(currentRound) ?? 0;
  const roundEnd = Math.max(roundStart + 1, model.roundEnds.get(currentRound) ?? model.durationMs);
  const roundProgress = Math.min(1, Math.max(0, (timeMs - roundStart) / (roundEnd - roundStart)));
  const nextTime = nextEvent?.time ?? model.durationMs;
  const previousTime = activeEvent?.time ?? 0;
  const turnProgress = Math.min(1, Math.max(0, (timeMs - previousTime) / Math.max(1, nextTime - previousTime)));

  return {
    timeMs,
    scores,
    activeEvent,
    nextEvent,
    visibleEvents,
    boardEvents,
    currentRound,
    acceptedCount,
    roundProgress,
    turnProgress
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
