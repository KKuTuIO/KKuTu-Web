/**
 * The replay-safe view of a game's stored payload (the shape `game/replayFromRecord.js` builds a recording from): players, rounds, the chain.
 * Extracted from the 전적 page, which kept it inline.
 */
const CHAIN_MODE_SET = new Set(['EKT', 'KSH', 'ESH', 'KAP', 'EAP', 'KKT', 'KFT', 'HUN', 'KDA', 'EDA']);

/** Decode a `br64` payload (Brotli, base64) in the browser. */
export async function decodeReplayPayload(base64, payloadCodec = 'br64') {
  if (!base64 || typeof atob === 'undefined' || payloadCodec !== 'br64') return null;
  try {
    const binary = atob(base64);
    const source = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) source[i] = binary.charCodeAt(i);
    if (typeof DecompressionStream === 'undefined') return null;
    const stream = new Blob([source]).stream().pipeThrough(new DecompressionStream('brotli'));
    const decompressed = await new Response(stream).arrayBuffer();
    return JSON.parse(new TextDecoder().decode(decompressed));
  } catch {
    return null;
  }
}

export function buildReplayView(payload) {
  if (!payload || !Array.isArray(payload.p)) return null;
  const modeCode = String(payload.rm?.[2] || '');
  const isChainMode = CHAIN_MODE_SET.has(modeCode);
  const players = payload.p.map((row, index) => ({
    index,
    id: row[0],
    nickname: row[1],
    exordial: row[2] || '',
    level: Number(row[3] || 1),
    robot: Number(row[6] || 0) === 1,
    team: Number(row[5] || 0),
    createdAt: Number(row[7] || 0),
    profileTitle: row[8] || '',
    profileName: row[9] || '',
    profileImage: row[10] || '',
    outfit: Array.isArray(row[11]) ? row[11] : []
  }));
  const words = Array.isArray(payload.w) ? payload.w : [];
  const extras = Array.isArray(payload.x) ? payload.x : [];
  const inputs = Array.isArray(payload.i) ? payload.i : [];
  const modeEvents = Array.isArray(payload.mv) ? payload.mv : [];
  const rounds = {};
  const chainEntriesByRound = {};
  let hasChainItemEvents = false;
  const rejectReasonLabel = {
    NCH: '체인 불일치',
    DUP: '중복 단어',
    NFD: '사전에 없음',
    RUL: '규칙 차단',
    MNR: '매너/안전 차단',
    TUR: '턴 아님',
    OTH: '기타'
  };

  const appendRoundItem = (round, item) => {
    if (!rounds[round]) rounds[round] = [];
    rounds[round].push(item);
  };

  const resolvePlayerById = (id) => players.find((player) => player.id === id);

  for (const row of inputs) {
    const round = Number(row[4] || 0);
    const playerIndex = Number(row[0] || 0);
    const wordIndex = Number(row[1] || 0);
    const elapsedTurnMs = Number(row[2] || 0);
    const elapsedGameMs = Number(row[3] || 0);
    const extraIndex = Number(row[6] || -1);
    const turn = Number(row[5] || 0);
    const extraRaw = extraIndex >= 0 ? String(extras[extraIndex] || '') : '';
    const extraTokens = extraRaw ? extraRaw.split(',') : [];

    const extraTag = extraTokens[0] || '';
    let displayWord = words[wordIndex] || '(알 수 없음)';
    if (extraTag === 'D') {
      const drawerIndex = Number(extraTokens[1] || -1);
      const drawerName = drawerIndex >= 0 ? (players[drawerIndex]?.nickname || `Player#${drawerIndex}`) : '-';
      const hintsGiven = Number(extraTokens[2] || 0);
      displayWord = `${displayWord} (정답 · 힌트 ${hintsGiven} · 화가 ${drawerName})`;
    } else if (extraTag === 'CA') {
      const score = Number(extraTokens[1] || 0);
      const bonus = Number(extraTokens[2] || 0);
      displayWord = `${displayWord} (인정 +${score}${bonus ? ` · 미션 +${bonus}` : ''})`;
    } else if (extraTag === 'CR') {
      const reason = extraTokens[1] || 'OTH';
      const code = Number(extraTokens[2] || -1);
      const label = rejectReasonLabel[reason] || rejectReasonLabel.OTH;
      displayWord = `${displayWord} (거절 · ${label}${code >= 0 ? ` · 코드 ${code}` : ''})`;
    } else if (extraTag === 'J') {
      const hintsGiven = Number(extraTokens[1] || 0);
      displayWord = `${displayWord} (정답 · 힌트 ${hintsGiven})`;
    } else if (extraTag === 'C') {
      const board = Number(extraTokens[1] || 0);
      const x = Number(extraTokens[2] || 0);
      const y = Number(extraTokens[3] || 0);
      const dir = Number(extraTokens[4] || 0);
      const isCorrect = Number(extraTokens[5] || 0) === 1;
      displayWord = `${displayWord} (${isCorrect ? '정답' : '오답'} · 보드 ${board} · ${x},${y},${dir})`;
    }
    const item = {
      playerIndex,
      nickname: players[playerIndex]?.nickname || `Player#${playerIndex}`,
      word: displayWord,
      elapsedTurnMs,
      elapsedGameMs
    };
    appendRoundItem(round, item);

    if (isChainMode && round > 0 && (extraTag === '' || extraTag === 'CA' || extraTag === 'CR')) {
      if (!chainEntriesByRound[round]) chainEntriesByRound[round] = [];
      let delta = 0;
      let rejected = false;
      let reason = '';
      if (extraTag === 'CA') {
        delta = Number(extraTokens[1] || 0) + Number(extraTokens[2] || 0);
      } else if (extraTag === 'CR') {
        rejected = true;
        reason = rejectReasonLabel[extraTokens[1] || 'OTH'] || rejectReasonLabel.OTH;
      }
      chainEntriesByRound[round].push({
        playerIndex,
        nickname: players[playerIndex]?.nickname || `Player#${playerIndex}`,
        word: words[wordIndex] || '(알 수 없음)',
        delta,
        elapsedTurnMs,
        elapsedGameMs,
        turn,
        showTurn: true,
        rejected,
        reason,
        isItem: false
      });
    }
  }

  for (const row of modeEvents) {
    const eventType = String(row[0] || '');
    const playerIndex = Number(row[1] || 0);
    const round = Number(row[2] || 0);
    const elapsedGameMs = Number(row[3] || 0);
    const extraIndex = Number(row[4] || -1);
    const extraRaw = extraIndex >= 0 ? String(extras[extraIndex] || '') : '';
    const extraTokens = extraRaw ? extraRaw.split(',') : [];

    let displayWord = '';
    if (eventType === 'TPM') {
      const tpm = Number(extraTokens[1] || 0);
      displayWord = `타/분 ${tpm}`;
    } else if (eventType === 'MQR') {
      const correct = Number(extraTokens[1] || 0);
      const wrong = Number(extraTokens[2] || 0);
      displayWord = `정답 ${correct} · 오답 ${wrong}`;
    } else if (eventType === 'SOK') {
      const word = extraTokens[1] || '(빈 단어)';
      displayWord = `조합 ${word}`;
    } else if (eventType === 'WSA') {
      const word = extraTokens[1] || '(빈 단어)';
      const targetId = extraTokens[2] || '';
      const targetName = resolvePlayerById(targetId)?.nickname || targetId || '-';
      displayWord = `공격 ${word} → ${targetName}`;
    } else if (eventType === 'WSS') {
      const craftedWords = Number(extraTokens[1] || 0);
      const craftedLetters = Number(extraTokens[2] || 0);
      displayWord = `제작 ${craftedWords}단어 · ${craftedLetters}글자`;
    } else if (eventType === 'CTO') {
      const delta = Number(extraTokens[1] || 0);
      displayWord = delta > 0 ? `공격 성공 점수 +${delta}` : `시간초과 점수 ${delta >= 0 ? '+' : ''}${delta}`;
    } else if (eventType === 'CAS') {
      const delta = Number(extraTokens[1] || 0);
      displayWord = `공격 성공 점수 +${Math.max(0, delta)}`;
    } else if (eventType === 'CIT') {
      const itemId = Number(extraTokens[1] || -1);
      const isTurnEnd = Number(extraTokens[2] || 0) === 1;
      displayWord = `아이템 ${itemId} 사용${isTurnEnd ? ' (턴 종료)' : ''}`;
    }
    if (!displayWord) continue;

    appendRoundItem(round, {
      playerIndex,
      nickname: players[playerIndex]?.nickname || `Player#${playerIndex}`,
      word: displayWord,
      elapsedTurnMs: elapsedGameMs,
      elapsedGameMs
    });

    if (isChainMode && round > 0 && (eventType === 'CTO' || eventType === 'CAS' || eventType === 'CIT')) {
      if (!chainEntriesByRound[round]) chainEntriesByRound[round] = [];
      const timeoutDelta = eventType === 'CTO' || eventType === 'CAS' ? Number(extraTokens[1] || 0) : 0;
      const timeoutPenalty = eventType === 'CTO' && timeoutDelta <= 0;
      if (eventType === 'CIT') hasChainItemEvents = true;
      chainEntriesByRound[round].push({
        playerIndex,
        nickname: players[playerIndex]?.nickname || `Player#${playerIndex}`,
        word: eventType === 'CIT'
          ? `아이템 ${Number(extraTokens[1] || -1)} 사용`
          : (timeoutDelta > 0 ? '공격 성공' : '입력 실패'),
        delta: timeoutDelta,
        elapsedTurnMs: elapsedGameMs,
        elapsedGameMs,
        turn: Number.MAX_SAFE_INTEGER,
        showTurn: false,
        rejected: timeoutPenalty,
        reason: timeoutPenalty ? '시간 초과' : '',
        isItem: eventType === 'CIT'
      });
    }
  }

  for (const key of Object.keys(rounds)) {
    rounds[key].sort((a, b) => a.elapsedGameMs - b.elapsedGameMs);
  }

  const rankingRaw = Array.isArray(payload.rs) ? payload.rs : [];
  const ranking = rankingRaw
    .map((row) => {
      const playerIndex = Number(row[0] || 0);
      return {
        playerIndex,
        playerId: players[playerIndex]?.id || '',
        nickname: players[playerIndex]?.nickname || `#${playerIndex}`,
        placement: Number(row[1] || 0) + 1,
        score: Number(row[2] || 0),
        exp: Number(row[4] || 0),
        ep: Number(row[7] || 0)
      };
    })
    .sort((a, b) => a.placement - b.placement || b.score - a.score);

  const acceptedChain = Array.isArray(payload.a)
    ? payload.a.map((index) => words[Number(index)]).filter(Boolean)
    : [];

  const roundKeys = Object.keys(rounds)
    .map((k) => Number(k))
    .sort((a, b) => a - b);

  const totalRounds = Math.max(1, Number(payload.rm?.[5] || 0));
  const chainRoundKeys = isChainMode
    ? Array.from({ length: totalRounds }, (_, idx) => idx + 1)
    : Object.keys(chainEntriesByRound).map((k) => Number(k)).sort((a, b) => a - b);
  const chainRounds = {};
  const cumulativeScoreByPlayer = players.map(() => 0);
  for (const round of chainRoundKeys) {
    const entries = (chainEntriesByRound[round] || []).slice().sort((a, b) => {
      if (a.elapsedGameMs !== b.elapsedGameMs) return a.elapsedGameMs - b.elapsedGameMs;
      if (a.turn !== b.turn) return a.turn - b.turn;
      return a.playerIndex - b.playerIndex;
    });
    const orderIndices = [];
    for (const entry of entries) {
      if (!orderIndices.includes(entry.playerIndex)) orderIndices.push(entry.playerIndex);
    }
    for (let playerIndex = 0; playerIndex < players.length; playerIndex++) {
      if (!orderIndices.includes(playerIndex)) orderIndices.push(playerIndex);
    }
    chainRounds[round] = {
      order: orderIndices.map((playerIndex, order) => ({
        playerIndex,
        order: order + 1,
        nickname: players[playerIndex]?.nickname || `Player#${playerIndex}`,
        startScore: Number(cumulativeScoreByPlayer[playerIndex] || 0)
      })),
      entries
    };
    for (const entry of entries) {
      cumulativeScoreByPlayer[entry.playerIndex] = Number(cumulativeScoreByPlayer[entry.playerIndex] || 0) + Number(entry.delta || 0);
    }
  }

  return {
    payload,
    modeCode,
    players,
    ranking,
    rounds,
    roundKeys,
    acceptedChain,
    chain: {
      enabled: isChainMode && chainRoundKeys.length > 0,
      hasItemEvents: hasChainItemEvents,
      roundKeys: chainRoundKeys,
      rounds: chainRounds
    }
  };
}

