<script nonce="kkutuio">
  import { onMount } from 'svelte';
  import { browser } from '$app/environment';
  import { getLevel } from '../../lib/getLevelImg.js';

  const title = 'Records';
  const ALLOWED_PAGE_SIZES = [10, 30, 50];
  const SEARCH_TYPE = {
    nickname: 'nickname',
    id: 'id',
    gameId: 'gameId'
  };
  const MODE_LABEL = {
    EKT: '영어 끄투',
    ESH: '영어 끝말잇기',
    EAP: '영어 앞말잇기',
    KKT: '한국어 쿵쿵따',
    KFT: '한국어 쿵쿵쿵따',
    KSH: '한국어 끝말잇기',
    CSQ: '자음퀴즈',
    KCW: '한국어 십자말풀이',
    KTY: '한국어 타자 대결',
    ETY: '영어 타자 대결',
    KAP: '한국어 앞말잇기',
    HUN: '훈민정음',
    KDA: '한국어 단어 대결',
    EDA: '영어 단어 대결',
    KSS: '한국어 솎솎',
    ESS: '영어 솎솎',
    KWS: '한국어 워드스택',
    EWS: '영어 워드스택',
    KDG: '한국어 그림퀴즈',
    EDG: '영어 그림퀴즈',
    KMT: '수학 대결',
    KQZ: '퀴즈 대결',
    UNKNOWN: '기타'
  };
  const OPTION_BADGE_LABEL = {
    // Short option ids
    man: '매너',
    saf: '안전',
    ext: '어인정',
    mis: '미션',
    inp: '단어장',
    ijp: '단어장',
    loa: '우리말',
    prv: '속담',
    lot: '장문',
    str: '깐깐',
    k32: '3232',
    no2: '2글자 금지',
    beg: '초보',
    nol: '길이 무제한',
    rms: '랜덤미션',
    tct: '택티컬',
    rnk: '친선전',
    asy: '유의어 제거',
    ado: '두음법칙 제거',
    pwr: '파워두음',
    etq: '에티켓',
    gnt: '젠틀',
    apm: '앞말잇기',
    itm: '아이템전',
    odw: '우리말샘',
    ulm: '무한정',
    sht: '짧음',
    ord: '순서대로',
    rmh: '힌트 제거',
    nob: '도중 입장 불가',
    // Long option ids
    manner: '매너',
    safe: '안전',
    injeong: '어인정',
    mission: '미션',
    injeongpick: '단어장',
    loanword: '우리말',
    proverb: '속담',
    longtext: '장문',
    strict: '깐깐',
    sami: '3232',
    onlybeginner: '초보',
    nolimit: '길이 무제한',
    randmission: '랜덤미션',
    tactical: '택티컬',
    rankmode: '친선전',
    antisynonym: '유의어 제거',
    antidoum: '두음법칙 제거',
    power: '파워두음',
    etiquette: '에티켓',
    gentle: '젠틀',
    apmal: '앞말잇기',
    item: '아이템전',
    opendict: '우리말샘',
    unlimited: '무한정',
    short: '짧음',
    order: '순서대로',
    removehint: '힌트 제거',
    noobserver: '도중 입장 불가'
  };
  const CHAIN_MODE_SET = new Set(['KSH', 'ESH', 'KAP', 'EAP', 'KKT', 'KFT', 'HUN', 'KDA', 'EDA', 'KSS', 'ESS']);

  let searchType = SEARCH_TYPE.nickname;
  let searchNick = '';
  let currentStatus = 'main';
  let selectedTab = 'profile';
  let uid = '';
  let loading = false;
  let loadingHistory = false;
  let errorMessage = '';
  let errorToast = '';
  let errorToastTimer = null;

  let page = 1;
  let pageSize = 10;
  let hasNext = false;

  let profile = null;
  let moremi = {};
  let historyRows = [];
  let modeStats = [];

  let expandedGameId = '';
  let detailLoading = {};
  let detailMap = {};
  let selectedRoundByGame = {};
  let hoveredChainPlayerByGame = {};
  let showItemEntriesByGame = {};
  let participantNicknameCache = {};
  let gameSearchResult = null;
  let hasResultView = false;

  $: hasResultView = (currentStatus === 'user' && !!profile) || (currentStatus === 'game' && !!gameSearchResult);

  function normalizeSearchType(value) {
    if (value === SEARCH_TYPE.id) return SEARCH_TYPE.id;
    if (value === SEARCH_TYPE.gameId) return SEARCH_TYPE.gameId;
    return SEARCH_TYPE.nickname;
  }

  function searchPlaceholder() {
    if (searchType === SEARCH_TYPE.id) return '식별번호를 입력하세요.';
    if (searchType === SEARCH_TYPE.gameId) return '경기번호를 입력하세요.';
    return '별명을 입력하세요.';
  }

  function normalizePageSize(value) {
    const parsed = Number(value);
    return ALLOWED_PAGE_SIZES.includes(parsed) ? parsed : 10;
  }

  function getRoomOptionBadges(detail) {
    const payloadOpts = detail?.replayView?.payload?.rm?.[9];
    const opts = Array.isArray(payloadOpts) ? payloadOpts : [];
    if (!opts.length) return [];
    const seen = new Set();
    const labels = [];
    for (const raw of opts) {
      const key = String(raw || '').trim();
      if (!key) continue;
      const label = OPTION_BADGE_LABEL[key] || OPTION_BADGE_LABEL[key.toLowerCase()];
      if (!label || seen.has(label)) continue;
      seen.add(label);
      labels.push(label);
    }
    return labels;
  }

  function syncQuery() {
    if (!browser) return;
    const params = new URLSearchParams();
    if (searchType !== SEARCH_TYPE.nickname) params.set('type', searchType);
    if (searchNick) params.set('q', searchNick);
    if (page > 1) params.set('page', String(page));
    if (pageSize !== 10) params.set('pageSize', String(pageSize));
    const query = params.toString();
    window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`);
  }

  function showError(message = '') {
    errorMessage = String(message || '');
    errorToast = errorMessage;
    if (errorToastTimer) clearTimeout(errorToastTimer);
    if (!errorToast) return;
    errorToastTimer = setTimeout(() => {
      errorToast = '';
    }, 4500);
  }

  function selectTextFromCurrentTarget(event) {
    if (!browser) return;
    const node = event?.currentTarget;
    if (!node || typeof window?.getSelection !== 'function') return;
    const textNode = node.querySelector?.('[data-select-text]') || node;
    const selection = window.getSelection();
    if (!selection) return;
    const range = document.createRange();
    range.selectNodeContents(textNode);
    selection.removeAllRanges();
    selection.addRange(range);
  }

  function normalizeEquip(rawEquip) {
    const result = {};
    if (!rawEquip) return result;
    for (const key of Object.keys(rawEquip)) {
      const value = rawEquip[key];
      if (!value) result[key] = 'default.png';
      else if (value === 'stars') result[key] = 'stars.gif';
      else if (typeof value === 'string' && value.endsWith('.png')) result[key] = value;
      else if (typeof value === 'string' && value.endsWith('.gif')) result[key] = value;
      else result[key] = `${value}.png`;
    }
    return result;
  }

  async function fetchJson(url) {
    const res = await fetch(url);
    let body = {};
    try {
      body = await res.json();
    } catch {
      body = {};
    }
    return { ok: res.ok, status: res.status, body };
  }

  function normalizePlayHoursFromMs(value) {
    const ms = Math.max(0, Number(value) || 0);
    return ms / 3600000;
  }

  function toEpochMs(value) {
    if (value === null || value === undefined) return 0;
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0) return 0;
    return parsed > 1000000000000 ? Math.floor(parsed) : Math.floor(parsed * 1000);
  }

  function buildModeStats(record, replayStats) {
    const rowsByMode = {};
    const fromRecord = record && typeof record === 'object' ? record : {};

    for (const key of Object.keys(fromRecord)) {
      const values = Array.isArray(fromRecord[key]) ? fromRecord[key] : [];
      rowsByMode[key] = {
        key,
        modeName: MODE_LABEL[key] || key,
        games: Number(values[0] || 0),
        wins: Number(values[1] || 0),
        exp: Number(values[2] || 0),
        playTimeMs: Math.max(0, Number(values[3] || 0)),
        acceptedWords: 0
      };
    }

    if (Array.isArray(replayStats)) {
      for (const row of replayStats) {
        const key = row?.modeName || 'UNKNOWN';
        const current = rowsByMode[key] || {
          key,
          modeName: MODE_LABEL[key] || key,
          games: 0,
          wins: 0,
          exp: 0,
          playTimeMs: 0,
          acceptedWords: 0
        };
        current.games = Number(row?.games || 0);
        current.wins = Number(row?.wins || 0);
        current.exp = Number(row?.exp || 0);
        current.playTimeMs = Math.max(0, Number(row?.playTime || 0));
        current.acceptedWords = Number(row?.acceptedWords || 0);
        rowsByMode[key] = current;
      }
    }

    const rows = Object.values(rowsByMode)
      .map((row) => ({
        ...row,
        playHours: normalizePlayHoursFromMs(row.playTimeMs),
        winRate: row.games > 0 ? Math.round((row.wins / row.games) * 10000) / 100 : 0
      }))
      .filter((row) => row.playTimeMs > 0 || row.wins > 0 || row.games > 0 || row.acceptedWords > 0 || row.exp > 0)
      .sort((a, b) => b.exp - a.exp || b.playTimeMs - a.playTimeMs || b.games - a.games);

    return rows;
  }

  function formatDate(ms) {
    if (!ms) return '-';
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(ms));
  }

  function formatAgo(ms) {
    if (!ms) return '-';
    const diff = Date.now() - ms;
    const sec = Math.floor(diff / 1000);
    if (sec < 60) return `${sec}초 전`;
    const min = Math.floor(sec / 60);
    if (min < 60) return `${min}분 전`;
    const hour = Math.floor(min / 60);
    if (hour < 24) return `${hour}시간 전`;
    const day = Math.floor(hour / 24);
    return `${day}일 전`;
  }

  function formatDuration(ms) {
    const total = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
    const min = Math.floor(total / 60);
    const sec = total % 60;
    return `${min}분 ${sec}초`;
  }

  async function decodeReplayPayload(base64, payloadCodec = 'br64') {
    if (!base64 || !browser || payloadCodec !== 'br64') return null;
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

  function buildReplayView(payload) {
    if (!payload || !Array.isArray(payload.p) || !Array.isArray(payload.w)) return null;
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
    const words = payload.w;
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

  function buildParticipants(game, replayView) {
    if (!replayView || !Array.isArray(replayView.players)) {
      const winnerIds = new Set(Array.isArray(game?.winnerIds) ? game.winnerIds : []);
      const userIds = Array.isArray(game?.userIds) ? game.userIds : [];
      return userIds.map((id) => ({
        id,
        nickname: id,
        won: winnerIds.has(id),
        robot: false,
        left: false,
        exp: 0,
        score: 0,
        placement: 0
      }));
    }
    const rankByIndex = new Map((replayView.ranking || []).map((row) => [row.playerIndex, row]));
    const winnerIds = new Set(Array.isArray(game?.winnerIds) ? game.winnerIds : []);
    const participants = replayView.players.map((player) => {
      const rankRow = rankByIndex.get(player.index);
      const left = !rankRow;
      return {
        id: player.id,
        nickname: player.nickname || player.id,
        won: rankRow ? rankRow.placement === 1 : winnerIds.has(player.id),
        robot: Boolean(player.robot),
        left,
        exp: Number(rankRow?.exp || 0),
        score: Number(rankRow?.score || 0),
        placement: Number(rankRow?.placement || 0)
      };
    });
    participants.sort((a, b) => {
      if (a.left !== b.left) return a.left ? 1 : -1;
      if (a.placement > 0 && b.placement > 0 && a.placement !== b.placement) return a.placement - b.placement;
      if (a.placement > 0 && b.placement <= 0) return -1;
      if (a.placement <= 0 && b.placement > 0) return 1;
      return b.score - a.score;
    });
    return participants;
  }

  async function fetchUserNickname(userId) {
    if (!userId || participantNicknameCache[userId]) return participantNicknameCache[userId] || null;
    const { body } = await fetchJson(`/user/${encodeURIComponent(userId)}`);
    const nickname = body?.profile?.title || null;
    if (nickname) participantNicknameCache = { ...participantNicknameCache, [userId]: nickname };
    return nickname;
  }

  async function hydrateParticipants(participants) {
    if (!Array.isArray(participants) || participants.length < 1) return participants;
    const next = participants.map((row) => ({ ...row }));
    await Promise.all(
      next.map(async (participant) => {
        if (participant.robot || !participant.id || participant.nickname !== participant.id) return;
        const nickname = await fetchUserNickname(participant.id);
        if (nickname) participant.nickname = nickname;
      })
    );
    return next;
  }

  async function copyPlayerId(id) {
    if (!browser || !id) return;
    try {
      await navigator.clipboard.writeText(id);
    } catch {
      showError('식별번호 복사에 실패했습니다.');
    }
  }

  async function searchPlayerById(id) {
    if (!id) return;
    searchType = SEARCH_TYPE.id;
    searchNick = id;
    uid = id;
    page = 1;
    currentStatus = 'main';
    gameSearchResult = null;
    await loadAll(true);
  }

  async function openAccountInfo(id) {
    await searchPlayerById(id);
    selectedTab = 'profile';
  }

  function getParticipantLabel(participant) {
    if (participant?.left) return '중도 퇴장';
    const placement = Number(participant?.placement || 0);
    if (placement === 1) return '우승';
    if (placement > 1) return `${placement}등`;
    return '-';
  }

  function getParticipantScoreText(participant) {
    if (participant?.left) return '-';
    return `${Number(participant?.score || 0).toLocaleString()}점`;
  }

  function formatSignedScore(value) {
    const n = Number(value || 0);
    return `${n > 0 ? '+' : ''}${n}`;
  }

  let recordScriptPromise = null;
  const RECORD_SCRIPT_URL = 'https://cdn.kkutu.io/js/in_record.min.js?v=4.2.0';

  function resolveRecordApi() {
    if (!browser) return null;
    const candidates = [
      window?.KKuTuRecord,
      window?.inRecord,
      window?.InRecord,
      window?.in_record,
      window?.KKUTU_RECORD
    ];
    for (const candidate of candidates) {
      if (candidate && typeof candidate.downloadKkio === 'function') return { fn: candidate.downloadKkio, ctx: candidate };
      if (candidate && typeof candidate.download === 'function') return { fn: candidate.download, ctx: candidate };
    }
    if (typeof window?.downloadKkio === 'function') return { fn: window.downloadKkio, ctx: window };
    return null;
  }

  function loadRecordScript() {
    if (!browser) return Promise.resolve(false);
    if (resolveRecordApi()) return Promise.resolve(true);
    if (recordScriptPromise) return recordScriptPromise;

    recordScriptPromise = new Promise((resolve) => {
      const existing = document.querySelector('script[data-kkutu-record="1"]');
      if (existing) {
        existing.addEventListener('load', () => resolve(Boolean(resolveRecordApi())), { once: true });
        existing.addEventListener('error', () => resolve(false), { once: true });
        return;
      }

      const script = document.createElement('script');
      script.src = RECORD_SCRIPT_URL;
      script.async = true;
      script.crossOrigin = 'anonymous';
      script.dataset.kkutuRecord = '1';
      script.addEventListener('load', () => resolve(Boolean(resolveRecordApi())), { once: true });
      script.addEventListener('error', () => resolve(false), { once: true });
      document.head.appendChild(script);
    });

    return recordScriptPromise;
  }

  async function downloadKkio(detail) {
    if (!browser) return;
    const loaded = await loadRecordScript();
    if (!loaded) {
      recordScriptPromise = null;
      showError('리플레이 내보내기 모듈 로드에 실패했습니다.');
      return;
    }
    try {
      const recordApi = resolveRecordApi();
      if (!recordApi) {
        showError('리플레이 내보내기 모듈을 찾을 수 없습니다.');
        return;
      }
      const ok = recordApi.fn.call(recordApi.ctx, detail);
      if (ok === false) showError('리플레이 파일 생성에 실패했습니다.');
    } catch (err) {
      console.error(err);
      showError('리플레이 파일 생성에 실패했습니다.');
    }
  }

  async function loadProfile() {
    const { body } = await fetchJson(`/user/${encodeURIComponent(uid)}`);
    if (body?.error || body?.result !== 200) {
      throw new Error('해당 사용자를 찾을 수 없습니다.');
    }
    const nextProfile = {
      id: body.id || uid,
      nickname: body?.profile?.title || uid,
      exordial: body.exordial || '',
      lastLoginTs: toEpochMs(body?.profile?.lastLogin),
      score: Number(body?.data?.score || 0),
      level: getLevel(Number(body?.data?.score || 0)),
      rank: null,
      record: body?.data?.record || {},
      raw: body
    };
    profile = nextProfile;
    moremi = normalizeEquip(body.equip);
    return nextProfile;
  }

  async function loadUserRanking(userId) {
    const { body } = await fetchJson(`/ranking?id=${encodeURIComponent(userId)}`);
    const rankRows = Array.isArray(body?.data?.data) ? body.data.data : [];
    const current = rankRows.find((row) => row?.id === userId) || rankRows[0];
    if (!current) return null;
    const rank = Number(current.rank);
    if (!Number.isFinite(rank)) return null;
    return rank + 1;
  }

  async function loadModeStats() {
    const { body } = await fetchJson(`/api/replay/user/${encodeURIComponent(uid)}/mode-stats`);
    if (!body?.ok) {
      if (body?.code === 429) throw new Error('모드 통계 조회 실패입니다: 과도한 요청으로 인해 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.');
      return [];
    }
    return Array.isArray(body.stats) ? body.stats : [];
  }

  async function loadHistory(targetPage = page) {
    loadingHistory = true;
    const { body } = await fetchJson(`/api/replay/user/${encodeURIComponent(uid)}?page=${targetPage}&pageSize=${pageSize}`);
    if (!body?.ok) {
      if (body?.code === 429) throw new Error('경기 내역 조회 실패입니다: 과도한 요청으로 인해 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.');
      throw new Error('경기 내역 조회 실패입니다: 알 수 없는 오류가 발생했습니다. 고객센터에 문의해 주세요.');
    }
    historyRows = body.history || [];
    hasNext = Boolean(body?.pagination?.hasNext);
    page = Number(body?.pagination?.page || targetPage);
    loadingHistory = false;
  }

  async function loadAll(resetPage = false) {
    if (!uid) return;
    loading = true;
    showError('');
    currentStatus = 'user';
    if (resetPage) page = 1;
    syncQuery();
    try {
      const [loadedProfile, , replayModeStats, ranking] = await Promise.all([loadProfile(), loadHistory(page), loadModeStats(), loadUserRanking(uid)]);
      loadedProfile.rank = ranking;
      profile = { ...loadedProfile };
      modeStats = buildModeStats(loadedProfile?.record || {}, replayModeStats || []);
      syncQuery();
    } catch (err) {
      showError(err.message || '전적을 불러오지 못했습니다.');
    } finally {
      loading = false;
      loadingHistory = false;
    }
  }

  function resetSearchResult() {
    expandedGameId = '';
    detailMap = {};
    detailLoading = {};
    hoveredChainPlayerByGame = {};
    showItemEntriesByGame = {};
    historyRows = [];
    hasNext = false;
    gameSearchResult = null;
  }

  async function searchGame() {
    const keyword = searchNick.trim();
    if (!keyword) return;
    loading = true;
    showError('');
    uid = '';
    profile = null;
    modeStats = [];
    resetSearchResult();
    currentStatus = 'game';
    syncQuery();
    try {
      const { body } = await fetchJson(`/api/replay/game/${encodeURIComponent(keyword)}?includePayload=true`);
      if (!body?.ok || !body?.game) {
        if (body?.code === 429) throw new Error('경기 조회 실패입니다: 과도한 요청으로 인해 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.');
        throw new Error('경기 조회 실패입니다: 입력한 경기번호를 찾을 수 없습니다.');
      }
      const game = body.game;
      const payload = game.payloadDecoded || await decodeReplayPayload(game.payload, game.payloadCodec);
      const replayView = buildReplayView(payload);
      const participants = await hydrateParticipants(buildParticipants(game, replayView));
      const firstRound = replayView?.chain?.roundKeys?.[0] || replayView?.roundKeys?.[0] || 0;
      selectedRoundByGame = { ...selectedRoundByGame, [game.gameId]: firstRound };
      detailMap = { ...detailMap, [game.gameId]: { ...game, participants, replayView } };
      expandedGameId = game.gameId;
      gameSearchResult = {
        gameId: game.gameId,
        startedAt: game.startedAt,
        mode: game.mode,
        modeName: game.modeName || 'UNKNOWN',
        rule: game.rule,
        lang: game.lang,
        roomTitle: game.roomTitle,
        playerCount: game.playerCount
      };
      syncQuery();
    } catch (err) {
      loading = false;
      showError(err.message || '경기 조회 실패입니다: 검색에 실패했습니다.');
      currentStatus = 'main';
    } finally {
      loading = false;
    }
  }

  async function searchUser() {
    const keyword = searchNick.trim();
    if (!keyword) return;
    loading = true;
    showError('');
    resetSearchResult();
    try {
      if (searchType === SEARCH_TYPE.nickname) {
        const { body } = await fetchJson(`/idFromNick/${encodeURIComponent(keyword)}`);
        if (body?.error || body?.result !== 200 || !body?.id) {
          throw new Error('사용자 조회 실패입니다: 입력한 별명을 가진 사용자를 찾을 수 없습니다. 별명을 고정하지 않은 사용자라면 해시태그(#) 이하를 포함하여 다시 검색해 주세요.');
        }
        uid = body.id;
      } else {
        uid = keyword;
      }
      page = 1;
      await loadAll(true);
    } catch (err) {
      loading = false;
      showError(err.message || '사용자 조회 실패입니다: 사용자 검색에 실패했습니다.');
      currentStatus = 'main';
    }
  }

  async function runSearch() {
    if (searchType === SEARCH_TYPE.gameId) {
      await searchGame();
      return;
    }
    await searchUser();
  }

  async function movePage(nextPage) {
    if (!uid || nextPage < 1) return;
    page = nextPage;
    await loadAll(false);
  }

  async function changePageSize(event) {
    pageSize = normalizePageSize(event.target.value);
    page = 1;
    await loadAll(true);
  }

  async function toggleDetail(gameId) {
    if (expandedGameId === gameId) {
      expandedGameId = '';
      return;
    }
    expandedGameId = gameId;
    if (detailMap[gameId] || detailLoading[gameId]) return;
    detailLoading = { ...detailLoading, [gameId]: true };
    const { body } = await fetchJson(`/api/replay/game/${encodeURIComponent(gameId)}?includePayload=true`);
    if (!body?.ok || !body?.game) {
      detailLoading = { ...detailLoading, [gameId]: false };
      detailMap = { ...detailMap, [gameId]: { error: body?.error || 'failed' } };
      return;
    }
    const game = body.game;
    const payload = game.payloadDecoded || await decodeReplayPayload(game.payload, game.payloadCodec);
    const replayView = buildReplayView(payload);
    const participants = await hydrateParticipants(buildParticipants(game, replayView));
    const firstRound = replayView?.chain?.roundKeys?.[0] || replayView?.roundKeys?.[0] || 0;
    selectedRoundByGame = { ...selectedRoundByGame, [gameId]: firstRound };
    detailLoading = { ...detailLoading, [gameId]: false };
    detailMap = { ...detailMap, [gameId]: { ...game, participants, replayView } };
  }

  function getModeLabel(row) {
    return MODE_LABEL[row.modeName] || MODE_LABEL[row.mode] || row.modeName || '일반';
  }

  function getTabClass(selected) {
    return `inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold transition-colors ${
      selected
        ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-900 dark:text-white'
        : 'text-slate-200 hover:bg-slate-700/70 hover:text-white'
    }`;
  }

  function getRoundButtonClass(selected) {
    return `rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors ${
      selected
        ? 'border-slate-900 bg-slate-900 text-white dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900'
        : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700'
    }`;
  }

  function getParticipantRowClass(participant, isMe = false) {
    const muted = participant?.robot || participant?.left;
    return `flex items-start gap-2 rounded-lg border px-3 py-2 text-sm sm:items-center ${
      muted ? 'opacity-60 grayscale-[0.2]' : ''
    } ${
      isMe
        ? 'border-sky-400/80 bg-sky-50/60 dark:border-sky-500/60 dark:bg-sky-900/20'
        : 'border-slate-200 bg-slate-100 dark:border-slate-700 dark:bg-slate-800'
    }`;
  }

  function getParticipantRankClass(participant) {
    if (participant?.left) return 'shrink-0 min-w-[64px] font-extrabold text-slate-400';
    if (participant?.placement === 1) return 'shrink-0 min-w-[64px] font-extrabold text-amber-500';
    if (participant?.placement === 2) return 'shrink-0 min-w-[64px] font-extrabold text-emerald-500';
    if (participant?.placement === 3) return 'shrink-0 min-w-[64px] font-extrabold text-blue-500';
    return 'shrink-0 min-w-[64px] font-extrabold text-slate-500';
  }

  function getChainEntryClass(chainEntry, highlighted) {
    const rejected = Boolean(chainEntry?.rejected);
    return `group relative inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-sm ${
      rejected
        ? 'border-rose-300 bg-rose-50 dark:border-rose-700/60 dark:bg-rose-950/30'
        : 'border-slate-200 bg-slate-100 dark:border-slate-700 dark:bg-slate-800'
    } ${highlighted ? 'ring-1 ring-sky-400/70 dark:ring-sky-500/70' : ''}`;
  }

  function getChainOrderClass(highlighted) {
    return `inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-sm transition-colors ${
      highlighted
        ? 'border-sky-400 bg-sky-50 text-sky-900 dark:border-sky-500 dark:bg-sky-900/35 dark:text-sky-100'
        : 'border-slate-300 bg-white dark:border-slate-600 dark:bg-slate-800'
    }`;
  }

  function getChainWordClass(chainEntry, highlighted) {
    const rejected = Boolean(chainEntry?.rejected);
    return `${highlighted ? 'font-extrabold' : 'font-semibold'} ${
      rejected ? 'text-rose-600 dark:text-rose-300' : 'text-slate-900 dark:text-slate-100'
    }`;
  }

  function getChainTooltipText(chainEntry) {
    return `${chainEntry.nickname} · ${(Number(chainEntry.elapsedTurnMs || 0) / 1000).toFixed(1)}초 소요`;
  }

  function getChainDeltaClass(delta) {
    const score = Number(delta || 0);
    if (score > 0) return 'font-bold text-emerald-600 dark:text-emerald-400';
    if (score < 0) return 'font-bold text-rose-600 dark:text-rose-400';
    return 'font-bold text-slate-500 dark:text-slate-300';
  }

  function getDisplayTurn(turn) {
    const n = Number(turn);
    if (!Number.isFinite(n) || n < 0) return 1;
    return n + 1;
  }

  function processNick(nick) {
    const safe = String(nick ?? '');
    const split = safe.split('#');
    return split[0] +
      (safe.includes('#') ? `<small style="color:#bbb">#${split.slice(1).join('#')}</small>` : '');
  }

  onMount(async () => {
    const params = new URLSearchParams(window.location.search);
    const typeParam = normalizeSearchType(params.get('type') || SEARCH_TYPE.nickname);
    const qParam = params.get('q') || params.get('nick') || '';
    const uidParam = params.get('uid') || '';
    const pageParam = Number(params.get('page') || 1);
    const sizeParam = normalizePageSize(params.get('pageSize') || 10);

    searchType = typeParam;
    searchNick = qParam || (typeParam === SEARCH_TYPE.id ? uidParam : '');
    uid = '';
    page = Number.isFinite(pageParam) && pageParam > 0 ? Math.floor(pageParam) : 1;
    pageSize = sizeParam;

    if (searchType === SEARCH_TYPE.gameId && searchNick) await searchGame();
    else if (searchNick) await searchUser();
    else if (uidParam) {
      uid = uidParam;
      await loadAll(false);
    }
  });
</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>

<div class="bg-slate-950 text-slate-100 py-4">
  <div class={`${hasResultView ? 'min-h-[50vh]' : 'min-h-screen'} rankBg relative flex h-full flex-col items-center overflow-hidden px-4 pb-20 pt-24 md:pb-28 md:pt-32`}>
    <div class="pointer-events-none absolute inset-x-0 bottom-0 h-1/4 bg-gradient-to-b from-transparent to-slate-950"></div>
    <p class="relative z-10 text-gray-200 text-lg my-4 flex items-center gap-2">
      <span class="material-symbols-outlined">insights</span>
      전적 조회
    </p>
    <h1 class="relative z-10 mb-2 text-center text-3xl font-bold text-white sm:text-4xl md:text-5xl">
      끄투리오 전적 검색
    </h1>
    <div class="relative z-10 mt-8 flex w-full max-w-3xl items-center rounded-2xl border border-white/40 bg-slate-900/60 p-2 shadow-xl backdrop-blur sm:mt-10">
      <select class="h-10 rounded-xl border border-white/20 bg-slate-950/70 px-3 text-sm text-white focus:outline-none focus:ring-2 focus:ring-sky-400/60" bind:value={searchType}>
        <option value={SEARCH_TYPE.nickname}>별명</option>
        <option value={SEARCH_TYPE.id}>식별번호</option>
        <option value={SEARCH_TYPE.gameId}>경기번호</option>
      </select>
      <input
        bind:value={searchNick}
        type="text"
        class="ml-2 min-w-0 flex-1 bg-transparent px-2 text-sm text-white outline-none placeholder:text-slate-300/80 sm:text-base"
        placeholder={searchPlaceholder()}
        on:keydown={(e) => e.key === 'Enter' && runSearch()}
      />
      <button class="inline-flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-xl text-white transition hover:bg-white/10" on:click={runSearch}>
        <i class="material-symbols-outlined icons-header">search</i>
      </button>
    </div>
  </div>

  {#if currentStatus === 'user' && profile}
    <div class="mx-2 -mt-14 mb-24 max-w-screen-xl rounded-2xl border border-slate-300/40 bg-slate-100/95 p-3 text-slate-900 shadow-2xl shadow-slate-950/20 backdrop-blur md:mx-auto md:p-4 dark:border-slate-700 dark:bg-slate-900/90 dark:text-slate-100">
      <section class="rounded-xl overflow-hidden border border-gray-300/70 dark:border-gray-700">
        <div class="flex flex-col gap-5 bg-gradient-to-br from-emerald-50 to-sky-50 p-4 sm:p-6 lg:flex-row lg:items-center lg:justify-between dark:from-slate-800 dark:to-slate-900">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:gap-5">
            <div class="relative h-24 w-24 shrink-0 overflow-hidden rounded-2xl border border-slate-200 bg-white/70 shadow-sm sm:h-28 sm:w-28 dark:border-slate-600 dark:bg-slate-800/70">
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/back/${moremi.Mback || 'default.png'}`} class="absolute h-full w-full object-cover" alt="bg" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/body/${moremi.Mbody || 'default.png'}`} class="absolute h-full w-full object-cover" alt="body" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/eye/${moremi.Meye || 'default.png'}`} class="absolute h-full w-full object-cover" alt="eye" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/mouth/${moremi.Mmouth || 'default.png'}`} class="absolute h-full w-full object-cover" alt="mouth" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/clothes/${moremi.Mclothes || 'default.png'}`} class="absolute h-full w-full object-cover" alt="clothes" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/shoes/${moremi.Mshoes || 'default.png'}`} class="absolute h-full w-full object-cover" alt="shoes" />
            </div>
            <div class="min-w-0">
              <div class="mb-2 flex flex-wrap items-center gap-2">
                <span class="rounded-full bg-slate-900 px-3 py-1 text-xs font-bold text-white dark:bg-slate-700">레벨 {profile.level}</span>
                {#if profile.rank}
                  <span class="rounded-full bg-blue-600 px-3 py-1 text-xs font-bold text-white">{Number(profile.rank).toLocaleString()}등</span>
                {/if}
                <span class="rounded-full bg-violet-600 px-3 py-1 text-xs font-bold text-white">경험치: {Number(profile.score).toLocaleString()}점</span>
              </div>
              <div class="truncate text-3xl font-bold leading-tight sm:text-4xl">{@html processNick(profile.nickname)}</div>
              <div class="mt-1 text-sm text-slate-600 dark:text-slate-300">
                {profile.exordial || '소개 한마디가 없습니다.'}
              </div>
              <div class="mt-2 flex items-center gap-1 text-xs text-slate-600 dark:text-slate-300">
                <span class="material-symbols-outlined text-sm">schedule</span>
                최근 접속:
                {#if profile.lastLoginTs}
                  {formatDate(profile.lastLoginTs)} ({formatAgo(profile.lastLoginTs)})
                {:else}
                  -
                {/if}
              </div>
            </div>
          </div>
          <button class="inline-flex items-center justify-center gap-2 rounded-xl bg-amber-400 px-4 py-3 text-sm font-extrabold text-slate-900 shadow-md shadow-amber-400/30 transition hover:bg-amber-300 disabled:cursor-not-allowed disabled:opacity-60 lg:min-w-[150px]" on:click={() => loadAll(false)} disabled={loading}>
            <span class="material-symbols-outlined text-xl">{loading ? 'progress_activity' : 'refresh'}</span>
            {loading ? '불러오는 중...' : '새로고침'}
          </button>
        </div>

        <div class="flex flex-wrap items-center gap-2 bg-slate-900 p-2">
          <button class={getTabClass(selectedTab === 'profile')} on:click={() => (selectedTab = 'profile')}>
            <span class="material-symbols-outlined text-base">person</span> 사용자 정보
          </button>
          <button class={getTabClass(selectedTab === 'stats')} on:click={() => (selectedTab = 'stats')}>
            <span class="material-symbols-outlined text-base">query_stats</span> 통계
          </button>
          <button class={getTabClass(selectedTab === 'history')} on:click={() => (selectedTab = 'history')}>
            <span class="material-symbols-outlined text-base">history</span> 경기 내역
          </button>
        </div>
      </section>

      {#if selectedTab === 'profile' || selectedTab === 'stats'}
        <section class="mt-5">
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {#if modeStats.length}
              {#each modeStats.slice(0, selectedTab === 'stats' ? modeStats.length : 3) as stat}
                <article class="rounded-2xl border border-slate-200 bg-white/95 p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700 dark:bg-slate-900">
                  <div class="text-xl font-bold text-blue-600 dark:text-blue-300 flex items-center gap-2">
                    <span class="material-symbols-outlined">stadia_controller</span>
                    {stat.modeName}
                  </div>
                  <div class="text-5xl font-black mt-3 flex items-end gap-1">
                    {stat.playHours.toFixed(1)}
                    <span class="text-xl font-medium">시간</span>
                  </div>
                  <div class="mt-4 text-lg flex justify-between items-center">
                    <span class="flex items-center gap-1"><span class="material-symbols-outlined text-base">emoji_events</span>우승</span>
                    <b>{stat.wins.toLocaleString()}회</b>
                  </div>
                  <div class="mt-2 text-lg flex justify-between items-center">
                    <span class="flex items-center gap-1"><span class="material-symbols-outlined text-base">sports_esports</span>경기</span>
                    <b>{stat.games.toLocaleString()}회</b>
                  </div>
                  <div class="mt-2 text-lg flex justify-between items-center">
                    <span class="flex items-center gap-1"><span class="material-symbols-outlined text-base">percent</span>승률</span>
                    <b>{stat.winRate.toFixed(2)}%</b>
                  </div>
                  <div class="mt-2 text-lg flex justify-between items-center">
                    <span class="flex items-center gap-1"><span class="material-symbols-outlined text-base">spellcheck</span>낱말 입력</span>
                    <b>{stat.acceptedWords.toLocaleString()}회</b>
                  </div>
                  <div class="mt-2 text-lg flex justify-between items-center">
                    <span class="flex items-center gap-1"><span class="material-symbols-outlined text-base">auto_awesome</span>획득 경험치</span>
                    <b>{stat.exp.toLocaleString()}</b>
                  </div>
                </article>
              {/each}
            {:else}
              <article class="rounded-2xl border border-slate-200 bg-white/95 p-4 text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">기록된 통계가 없습니다.</article>
            {/if}
          </div>
        </section>
      {/if}

      {#if selectedTab === 'profile' || selectedTab === 'history'}
        <section class="mt-6">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-2xl font-bold">경기 내역</h3>
            <div class="flex items-center gap-2 text-sm">
              <span>쪽 당 행</span>
              <select class="rounded-lg border border-slate-300 bg-white px-2 py-1 text-slate-700 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100" bind:value={pageSize} on:change={changePageSize}>
                {#each ALLOWED_PAGE_SIZES as size}
                  <option value={size}>{size}</option>
                {/each}
              </select>
            </div>
          </div>

          {#if loadingHistory}
            <div class="rounded-xl border border-slate-200 bg-white p-4 text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">불러오는 중...</div>
          {:else if !historyRows.length}
            <div class="rounded-xl border border-slate-200 bg-white p-4 text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">최근 3년 내 경기 기록이 없습니다.</div>
          {:else}
            <div class="space-y-3">
              {#each historyRows as row}
                <article class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900" style={`border-left: 6px solid ${row.won ? '#eab308' : '#9ca3af'}`}>
                  <button class="w-full text-left p-4 cursor-pointer" on:click={() => toggleDetail(row.gameId)}>
                    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between sm:gap-6 lg:gap-8">
                      <div class="text-3xl font-black">
                        #{row.placement}
                        <span class="text-lg font-semibold text-gray-500 dark:text-gray-300">/ {row.playerCount}</span>
                      </div>
                      <div class="flex-1 min-w-0">
                        <div class="font-bold text-lg truncate">{getModeLabel(row)}</div>
                        <div class="text-sm text-gray-500 dark:text-gray-300">{row.roomTitle || '제목 없음'}</div>
                        <div class="text-sm text-gray-500 dark:text-gray-300 mt-1">{formatAgo(row.startedAt)} · {formatDate(row.startedAt)}</div>
                      </div>
                      <div class="shrink-0 text-left sm:text-right">
                        <div class="text-2xl font-extrabold">{Number(row.score).toLocaleString()}점</div>
                        <div class="text-sm text-gray-500 dark:text-gray-300">경험치 +{Number(row.exp || 0).toLocaleString()}</div>
                      </div>
                    </div>
                  </button>

                  {#if expandedGameId === row.gameId}
                    <div class="px-4 pb-4 border-t border-gray-200 dark:border-gray-600 pt-4">
                      {#if detailLoading[row.gameId]}
                        <div class="text-sm text-gray-500 dark:text-gray-300">경기 정보를 불러오는 중...</div>
                      {:else if detailMap[row.gameId]?.error}
                        <div class="text-sm text-red-600">상세 정보를 불러오지 못했습니다.</div>
                      {:else if detailMap[row.gameId]}
                        <div class="text-sm text-gray-600 dark:text-gray-300 mb-3 flex items-center justify-between gap-2">
                          <div class="cursor-text select-text" on:click={selectTextFromCurrentTarget}>경기번호: <code data-select-text>{row.gameId}</code></div>
                          <button class="inline-flex items-center gap-1 text-xs px-2 py-1 rounded-md border bg-white dark:bg-gray-700" on:click={() => downloadKkio(detailMap[row.gameId])}>
                            <span class="material-symbols-outlined text-base">download</span> 리플레이 내려받기
                          </button>
                        </div>
                        <div class="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
                          <div>채널: <b>{detailMap[row.gameId].channel}</b></div>
                          <div>방 번호: <b>{detailMap[row.gameId].roomId}</b></div>
                          <div>
                            특수규칙:
                            {#if getRoomOptionBadges(detailMap[row.gameId]).length}
                              <span class="inline-flex flex-wrap gap-1 ml-1 align-middle">
                                {#each getRoomOptionBadges(detailMap[row.gameId]) as optionLabel}
                                  <span class="text-xs px-2 py-0.5 rounded-full bg-slate-200 text-slate-800 dark:bg-slate-600 dark:text-slate-100">{optionLabel}</span>
                                {/each}
                              </span>
                            {:else}
                              <b>-</b>
                            {/if}
                          </div>
                          <div>언어: <b>{detailMap[row.gameId].lang}</b></div>
                          <div>경기 시간: <b>{formatDuration(detailMap[row.gameId].durationMs)}</b></div>
                          <div>압축 크기: <b>{Number(detailMap[row.gameId].payloadSize || 0).toLocaleString()} bytes</b></div>
                        </div>
                        <div class="mt-3 text-sm">
                          <div class="font-semibold mb-2">참가자</div>
                          <div class="space-y-1">
                            {#each detailMap[row.gameId].participants || [] as participant}
                              <div class={getParticipantRowClass(participant, participant.id === uid)}>
                                <span class={getParticipantRankClass(participant)}>{getParticipantLabel(participant)}</span>
                                <div class="min-w-0 flex-1">
                                  <div class="truncate font-semibold">{@html processNick(participant.nickname)}</div>
                                  <div class="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-300">
                                    {#if participant.id && participant.id !== participant.nickname}
                                      <span>식별번호: {participant.id}</span>
                                    {/if}
                                    <span>획득 경험치: +{Number(participant.exp || 0).toLocaleString()}</span>
                                    {#if participant.robot}
                                      <span class="rounded bg-slate-300 px-1.5 py-0.5 text-xs text-slate-700 dark:bg-slate-700 dark:text-slate-200">BOT</span>
                                    {/if}
                                  </div>
                                  {#if participant.left}
                                    <div class="mt-0.5 text-xs text-red-600 dark:text-red-400">게임 도중 퇴장하였습니다.</div>
                                  {/if}
                                </div>
                                <div class="shrink-0 flex items-center gap-1">
                                  <button class="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-300 bg-white text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700" title="식별번호 복사" on:click={() => copyPlayerId(participant.id)}>
                                    <span class="material-symbols-outlined text-base">content_copy</span>
                                  </button>
                                  <button class="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-300 bg-white text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700" title="계정 정보 보기" disabled={participant.robot} on:click={() => openAccountInfo(participant.id)}>
                                    <span class="material-symbols-outlined text-base">account_circle</span>
                                  </button>
                                </div>
                                <div class="shrink-0 text-lg font-extrabold text-slate-700 dark:text-slate-100">{getParticipantScoreText(participant)}</div>
                              </div>
                            {/each}
                          </div>
                        </div>

                        {#if detailMap[row.gameId].replayView}
                          <div class="mt-5">
                            <div class="font-semibold mb-2">최종 순위</div>
                            <div class="overflow-x-auto">
                              <table class="w-full text-sm">
                                <thead>
                                  <tr class="text-left border-b border-gray-200 dark:border-gray-600">
                                    <th class="py-2">순위</th>
                                    <th class="py-2">별명</th>
                                    <th class="py-2 text-right">점수</th>
                                    <th class="py-2 text-right">경험치</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {#each detailMap[row.gameId].replayView.ranking as rankRow}
                                    <tr class="border-b border-gray-100 dark:border-gray-700">
                                      <td class="py-2">{rankRow.placement}위</td>
                                      <td class="py-2">{@html processNick(rankRow.nickname)}</td>
                                      <td class="py-2 text-right">{rankRow.score.toLocaleString()}</td>
                                      <td class="py-2 text-right">+{rankRow.exp.toLocaleString()}</td>
                                    </tr>
                                  {/each}
                                </tbody>
                              </table>
                            </div>
                          </div>

                          {#if detailMap[row.gameId].replayView.chain?.enabled}
                            <div class="mt-5 rounded-xl border border-gray-200 dark:border-gray-600 p-3 bg-gray-50/80 dark:bg-gray-800/40">
                              <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
                                <div class="font-semibold">라운드 기록</div>
                                <label class="text-sm inline-flex items-center gap-2">
                                  <span>아이템 기록 표시</span>
                                  <input
                                    type="checkbox"
                                    checked={Boolean(showItemEntriesByGame[row.gameId])}
                                    on:change={(e) => (showItemEntriesByGame = { ...showItemEntriesByGame, [row.gameId]: e.currentTarget.checked })}
                                  />
                                </label>
                              </div>
                              <div class="flex flex-wrap gap-2 mb-3">
                                {#each detailMap[row.gameId].replayView.chain.roundKeys as roundKey}
                                  <button class={getRoundButtonClass(selectedRoundByGame[row.gameId] === roundKey)} on:click={() => (selectedRoundByGame = { ...selectedRoundByGame, [row.gameId]: roundKey })}>
                                    라운드 {roundKey}
                                  </button>
                                {/each}
                              </div>
                              {#if detailMap[row.gameId].replayView.chain.rounds[selectedRoundByGame[row.gameId]]}
                                <div class="text-sm font-semibold mb-2">{selectedRoundByGame[row.gameId]} 라운드</div>
                                <div class="flex flex-wrap items-center gap-2">
                                  {#each detailMap[row.gameId].replayView.chain.rounds[selectedRoundByGame[row.gameId]].order as slot, idx}
                                    <div
                                      class={getChainOrderClass(hoveredChainPlayerByGame[row.gameId] === slot.playerIndex)}
                                      role="presentation"
                                      on:mouseenter={() => (hoveredChainPlayerByGame = { ...hoveredChainPlayerByGame, [row.gameId]: slot.playerIndex })}
                                      on:mouseleave={() => (hoveredChainPlayerByGame = { ...hoveredChainPlayerByGame, [row.gameId]: -1 })}
                                    >
                                      <span class="inline-flex h-5 min-w-5 items-center justify-center rounded-md bg-slate-200 px-1 text-xs font-extrabold text-slate-700 dark:bg-slate-600 dark:text-slate-100">{slot.order}</span>
                                      <span class="font-semibold">{@html processNick(slot.nickname)}</span>
                                      <span class="text-slate-500 dark:text-slate-300">{slot.startScore.toLocaleString()}점</span>
                                    </div>
                                    {#if idx < detailMap[row.gameId].replayView.chain.rounds[selectedRoundByGame[row.gameId]].order.length - 1}
                                      <span class="text-slate-400">→</span>
                                    {/if}
                                  {/each}
                                </div>
                                <div class="mt-3 flex flex-wrap items-center gap-2">
                                  {#each detailMap[row.gameId].replayView.chain.rounds[selectedRoundByGame[row.gameId]].entries.filter((entry) => Boolean(showItemEntriesByGame[row.gameId]) || !entry.isItem) as chainEntry, idx}
                                    <div
                                      class={getChainEntryClass(chainEntry, hoveredChainPlayerByGame[row.gameId] === chainEntry.playerIndex)}
                                      on:mouseenter={() => (hoveredChainPlayerByGame = { ...hoveredChainPlayerByGame, [row.gameId]: chainEntry.playerIndex })}
                                      on:mouseleave={() => (hoveredChainPlayerByGame = { ...hoveredChainPlayerByGame, [row.gameId]: -1 })}
                                    >
                                      {#if chainEntry.showTurn}
                                        <span class="inline-flex h-5 min-w-5 items-center justify-center rounded-md bg-slate-200 px-1 text-xs font-extrabold text-slate-700 dark:bg-slate-600 dark:text-slate-100">{getDisplayTurn(chainEntry.turn)}</span>
                                      {/if}
                                      <span class={getChainWordClass(chainEntry, hoveredChainPlayerByGame[row.gameId] === chainEntry.playerIndex)}>{chainEntry.word}</span>
                                      {#if chainEntry.reason}
                                        <span class="text-xs text-slate-500 dark:text-slate-300">({chainEntry.reason})</span>
                                      {/if}
                                      {#if !chainEntry.isItem}
                                        <span class={getChainDeltaClass(chainEntry.delta)}>{formatSignedScore(chainEntry.delta)}</span>
                                      {/if}
                                      <span class="pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 hidden -translate-x-1/2 group-hover:block">
                                        <span class="relative block whitespace-nowrap rounded-md bg-slate-900 px-2 py-1 text-[11px] font-medium leading-none text-white shadow-lg dark:bg-slate-100 dark:text-slate-900">
                                          {getChainTooltipText(chainEntry)}
                                          <span class="absolute left-1/2 top-full h-2 w-2 -translate-x-1/2 -translate-y-1/2 rotate-45 bg-slate-900 dark:bg-slate-100"></span>
                                        </span>
                                      </span>
                                    </div>
                                    {#if idx < detailMap[row.gameId].replayView.chain.rounds[selectedRoundByGame[row.gameId]].entries.filter((entry) => Boolean(showItemEntriesByGame[row.gameId]) || !entry.isItem).length - 1}
                                      <span class="text-slate-400">›</span>
                                    {/if}
                                  {/each}
                                </div>
                              {/if}
                            </div>
                          {:else}
                            <div class="mt-5">
                              <div class="font-semibold mb-2">낱말 내역</div>
                              <div class="flex flex-wrap gap-2 mb-3">
                                {#each detailMap[row.gameId].replayView.roundKeys as roundKey}
                                  <button class={getRoundButtonClass(selectedRoundByGame[row.gameId] === roundKey)} on:click={() => (selectedRoundByGame = { ...selectedRoundByGame, [row.gameId]: roundKey })}>
                                    라운드 {roundKey}
                                  </button>
                                {/each}
                              </div>
                              <div class="space-y-2">
                                {#each detailMap[row.gameId].replayView.rounds[selectedRoundByGame[row.gameId]] || [] as inputLog}
                                  <div class="px-3 py-2 rounded bg-gray-100 dark:bg-gray-800 text-sm flex items-center justify-between gap-3">
                                    <div>
                                      <b>{@html processNick(inputLog.nickname)}</b>
                                      <span class="mx-2 text-gray-400">→</span>
                                      <span>{inputLog.word}</span>
                                    </div>
                                    <div class="text-gray-500 dark:text-gray-300">
                                      +{(inputLog.elapsedTurnMs / 1000).toFixed(2)}초
                                    </div>
                                  </div>
                                {/each}
                              </div>
                            </div>
                          {/if}

                          {#if detailMap[row.gameId].replayView.acceptedChain?.length}
                            <div class="mt-5">
                              <div class="font-semibold mb-2">최종 체인</div>
                              <div class="text-sm bg-gray-100 dark:bg-gray-800 px-3 py-2 rounded break-words">
                                {detailMap[row.gameId].replayView.acceptedChain.join(' → ')}
                              </div>
                            </div>
                          {/if}
                        {:else}
                          <div class="mt-4 text-sm text-gray-500 dark:text-gray-300">
                            상세 낱말 내역을 표시하지 못했습니다.
                          </div>
                        {/if}
                      {/if}
                    </div>
                  {/if}
                </article>
              {/each}
            </div>

            <div class="mt-4 flex items-center justify-center gap-3">
              <button class="rounded-lg border border-slate-300 bg-white px-4 py-1.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-45 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700" on:click={() => movePage(page - 1)} disabled={page <= 1 || loading}>이전</button>
              <span class="text-sm text-slate-600 dark:text-slate-300">{page}쪽</span>
              <button class="rounded-lg border border-slate-300 bg-white px-4 py-1.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-45 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700" on:click={() => movePage(page + 1)} disabled={!hasNext || loading}>다음</button>
            </div>
          {/if}
        </section>
      {/if}
    </div>
  {/if}

  {#if currentStatus === 'game' && gameSearchResult}
    <div class="mx-2 -mt-14 mb-24 max-w-screen-xl rounded-2xl border border-slate-300/40 bg-slate-100/95 p-3 text-slate-900 shadow-2xl shadow-slate-950/20 backdrop-blur md:mx-auto md:p-4 dark:border-slate-700 dark:bg-slate-900/90 dark:text-slate-100">
      <section class="mt-2">
        <h3 class="text-2xl font-bold mb-3">경기 조회 결과</h3>
        <article class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
          <button class="w-full text-left p-4 cursor-pointer" on:click={() => toggleDetail(gameSearchResult.gameId)}>
            <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between sm:gap-6 lg:gap-8">
              <div class="break-all text-base font-black sm:text-2xl">{gameSearchResult.roomTitle || '제목 없음'}</div>
              <div class="flex-1 min-w-0">
                <div class="font-bold text-lg truncate">{getModeLabel(gameSearchResult)}</div>
                <div data-select-text class="cursor-text select-text text-sm text-gray-500 dark:text-gray-300" on:click|stopPropagation={selectTextFromCurrentTarget}>{gameSearchResult.gameId}</div>
                <div class="text-sm text-gray-500 dark:text-gray-300 mt-1">{formatAgo(gameSearchResult.startedAt)} · {formatDate(gameSearchResult.startedAt)}</div>
              </div>
              <div class="shrink-0 text-left sm:text-right">
                <div class="text-lg font-bold">{gameSearchResult.playerCount}명</div>
                <div class="text-sm text-gray-500 dark:text-gray-300">{gameSearchResult.rule} · {gameSearchResult.lang}</div>
              </div>
            </div>
          </button>

          {#if expandedGameId === gameSearchResult.gameId && detailMap[gameSearchResult.gameId]}
            <div class="px-4 pb-4 border-t border-gray-200 dark:border-gray-600 pt-4">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
                <div>채널: <b>{detailMap[gameSearchResult.gameId].channel}</b></div>
                <div>방 번호: <b>{detailMap[gameSearchResult.gameId].roomId}</b></div>
                <div>
                  특수규칙:
                  {#if getRoomOptionBadges(detailMap[gameSearchResult.gameId]).length}
                    <span class="inline-flex flex-wrap gap-1 ml-1 align-middle">
                      {#each getRoomOptionBadges(detailMap[gameSearchResult.gameId]) as optionLabel}
                        <span class="text-xs px-2 py-0.5 rounded-full bg-slate-200 text-slate-800 dark:bg-slate-600 dark:text-slate-100">{optionLabel}</span>
                      {/each}
                    </span>
                  {:else}
                    <b>-</b>
                  {/if}
                </div>
                <div>언어: <b>{detailMap[gameSearchResult.gameId].lang}</b></div>
                <div>경기 시간: <b>{formatDuration(detailMap[gameSearchResult.gameId].durationMs)}</b></div>
                <div>압축 크기: <b>{Number(detailMap[gameSearchResult.gameId].payloadSize || 0).toLocaleString()} bytes</b></div>
              </div>
              <div class="mt-3 flex justify-end">
                <button class="inline-flex items-center gap-1 text-xs px-2 py-1 rounded-md border bg-white dark:bg-gray-700" on:click={() => downloadKkio(detailMap[gameSearchResult.gameId])}>
                  <span class="material-symbols-outlined text-base">download</span>
                    리플레이 내려받기
                </button>
              </div>
              <div class="mt-3 text-sm">
                <div class="font-semibold mb-2">참가자</div>
                <div class="space-y-1">
                  {#each detailMap[gameSearchResult.gameId].participants || [] as participant}
                    <div class={getParticipantRowClass(participant, participant.id === uid)}>
                      <span class={getParticipantRankClass(participant)}>{getParticipantLabel(participant)}</span>
                      <div class="min-w-0 flex-1">
                        <div class="truncate font-semibold">{@html processNick(participant.nickname)}</div>
                        <div class="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-300">
                          {#if participant.id && participant.id !== participant.nickname}
                            <span>식별번호: {participant.id}</span>
                          {/if}
                          <span>획득 경험치: +{Number(participant.exp || 0).toLocaleString()}</span>
                          {#if participant.robot}
                            <span class="rounded bg-slate-300 px-1.5 py-0.5 text-xs text-slate-700 dark:bg-slate-700 dark:text-slate-200">BOT</span>
                          {/if}
                        </div>
                        {#if participant.left}
                          <div class="mt-0.5 text-xs text-red-600 dark:text-red-400">게임 도중 퇴장하였습니다.</div>
                        {/if}
                      </div>
                      <div class="shrink-0 flex items-center gap-1">
                        <button class="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-300 bg-white text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700" title="식별번호 복사" on:click={() => copyPlayerId(participant.id)}>
                          <span class="material-symbols-outlined text-base">content_copy</span>
                        </button>
                        <button class="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-300 bg-white text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700" title="계정 정보 보기" disabled={participant.robot} on:click={() => openAccountInfo(participant.id)}>
                          <span class="material-symbols-outlined text-base">account_circle</span>
                        </button>
                      </div>
                      <div class="shrink-0 text-lg font-extrabold text-slate-700 dark:text-slate-100">{getParticipantScoreText(participant)}</div>
                    </div>
                  {/each}
                </div>
              </div>

              {#if detailMap[gameSearchResult.gameId].replayView}
                <div class="mt-5">
                  <div class="font-semibold mb-2">최종 순위</div>
                  <div class="overflow-x-auto">
                    <table class="w-full text-sm">
                      <thead>
                        <tr class="text-left border-b border-gray-200 dark:border-gray-600">
                          <th class="py-2">순위</th>
                          <th class="py-2">별명</th>
                          <th class="py-2 text-right">점수</th>
                          <th class="py-2 text-right">경험치</th>
                        </tr>
                      </thead>
                      <tbody>
                        {#each detailMap[gameSearchResult.gameId].replayView.ranking as rankRow}
                          <tr class="border-b border-gray-100 dark:border-gray-700">
                            <td class="py-2">{rankRow.placement}위</td>
                            <td class="py-2">{@html processNick(rankRow.nickname)}</td>
                            <td class="py-2 text-right">{rankRow.score.toLocaleString()}</td>
                            <td class="py-2 text-right">+{rankRow.exp.toLocaleString()}</td>
                          </tr>
                        {/each}
                      </tbody>
                    </table>
                  </div>
                </div>

                {#if detailMap[gameSearchResult.gameId].replayView.chain?.enabled}
                  <div class="mt-5 rounded-xl border border-gray-200 dark:border-gray-600 p-3 bg-gray-50/80 dark:bg-gray-800/40">
                    <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
                      <div class="font-semibold">라운드 기록</div>
                      <label class="text-sm inline-flex items-center gap-2">
                        <span>아이템 기록 표시</span>
                        <input
                          type="checkbox"
                          checked={Boolean(showItemEntriesByGame[gameSearchResult.gameId])}
                          on:change={(e) => (showItemEntriesByGame = { ...showItemEntriesByGame, [gameSearchResult.gameId]: e.currentTarget.checked })}
                        />
                      </label>
                    </div>
                    <div class="flex flex-wrap gap-2 mb-3">
                      {#each detailMap[gameSearchResult.gameId].replayView.chain.roundKeys as roundKey}
                        <button class={getRoundButtonClass(selectedRoundByGame[gameSearchResult.gameId] === roundKey)} on:click={() => (selectedRoundByGame = { ...selectedRoundByGame, [gameSearchResult.gameId]: roundKey })}>
                          라운드 {roundKey}
                        </button>
                      {/each}
                    </div>
                    {#if detailMap[gameSearchResult.gameId].replayView.chain.rounds[selectedRoundByGame[gameSearchResult.gameId]]}
                      <div class="text-sm font-semibold mb-2">{selectedRoundByGame[gameSearchResult.gameId]} 라운드</div>
                      <div class="flex flex-wrap items-center gap-2">
                        {#each detailMap[gameSearchResult.gameId].replayView.chain.rounds[selectedRoundByGame[gameSearchResult.gameId]].order as slot, idx}
                          <div
                            class={getChainOrderClass(hoveredChainPlayerByGame[gameSearchResult.gameId] === slot.playerIndex)}
                            role="presentation"
                            on:mouseenter={() => (hoveredChainPlayerByGame = { ...hoveredChainPlayerByGame, [gameSearchResult.gameId]: slot.playerIndex })}
                            on:mouseleave={() => (hoveredChainPlayerByGame = { ...hoveredChainPlayerByGame, [gameSearchResult.gameId]: -1 })}
                          >
                            <span class="inline-flex h-5 min-w-5 items-center justify-center rounded-md bg-slate-200 px-1 text-xs font-extrabold text-slate-700 dark:bg-slate-600 dark:text-slate-100">{slot.order}</span>
                            <span class="font-semibold">{@html processNick(slot.nickname)}</span>
                            <span class="text-slate-500 dark:text-slate-300">{slot.startScore.toLocaleString()}점</span>
                          </div>
                          {#if idx < detailMap[gameSearchResult.gameId].replayView.chain.rounds[selectedRoundByGame[gameSearchResult.gameId]].order.length - 1}
                            <span class="text-slate-400">→</span>
                          {/if}
                        {/each}
                      </div>
                      <div class="mt-3 flex flex-wrap items-center gap-2">
                        {#each detailMap[gameSearchResult.gameId].replayView.chain.rounds[selectedRoundByGame[gameSearchResult.gameId]].entries.filter((entry) => Boolean(showItemEntriesByGame[gameSearchResult.gameId]) || !entry.isItem) as chainEntry, idx}
                          <div
                            class={getChainEntryClass(chainEntry, hoveredChainPlayerByGame[gameSearchResult.gameId] === chainEntry.playerIndex)}
                            on:mouseenter={() => (hoveredChainPlayerByGame = { ...hoveredChainPlayerByGame, [gameSearchResult.gameId]: chainEntry.playerIndex })}
                            on:mouseleave={() => (hoveredChainPlayerByGame = { ...hoveredChainPlayerByGame, [gameSearchResult.gameId]: -1 })}
                          >
                            {#if chainEntry.showTurn}
                              <span class="inline-flex h-5 min-w-5 items-center justify-center rounded-md bg-slate-200 px-1 text-xs font-extrabold text-slate-700 dark:bg-slate-600 dark:text-slate-100">{getDisplayTurn(chainEntry.turn)}</span>
                            {/if}
                            <span class={getChainWordClass(chainEntry, hoveredChainPlayerByGame[gameSearchResult.gameId] === chainEntry.playerIndex)}>{chainEntry.word}</span>
                            {#if chainEntry.reason}
                              <span class="text-xs text-slate-500 dark:text-slate-300">({chainEntry.reason})</span>
                            {/if}
                            {#if !chainEntry.isItem}
                              <span class={getChainDeltaClass(chainEntry.delta)}>{formatSignedScore(chainEntry.delta)}</span>
                            {/if}
                            <span class="pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 hidden -translate-x-1/2 group-hover:block">
                              <span class="relative block whitespace-nowrap rounded-md bg-slate-900 px-2 py-1 text-[11px] font-medium leading-none text-white shadow-lg dark:bg-slate-100 dark:text-slate-900">
                                {getChainTooltipText(chainEntry)}
                                <span class="absolute left-1/2 top-full h-2 w-2 -translate-x-1/2 -translate-y-1/2 rotate-45 bg-slate-900 dark:bg-slate-100"></span>
                              </span>
                            </span>
                          </div>
                          {#if idx < detailMap[gameSearchResult.gameId].replayView.chain.rounds[selectedRoundByGame[gameSearchResult.gameId]].entries.filter((entry) => Boolean(showItemEntriesByGame[gameSearchResult.gameId]) || !entry.isItem).length - 1}
                            <span class="text-slate-400">›</span>
                          {/if}
                        {/each}
                      </div>
                    {/if}
                  </div>
                {:else}
                  <div class="mt-5">
                    <div class="font-semibold mb-2">라운드 기록</div>
                    <div class="flex flex-wrap gap-2 mb-3">
                      {#each detailMap[gameSearchResult.gameId].replayView.roundKeys as roundKey}
                        <button class={getRoundButtonClass(selectedRoundByGame[gameSearchResult.gameId] === roundKey)} on:click={() => (selectedRoundByGame = { ...selectedRoundByGame, [gameSearchResult.gameId]: roundKey })}>
                          라운드 {roundKey}
                        </button>
                      {/each}
                    </div>
                    <div class="space-y-2">
                      {#each detailMap[gameSearchResult.gameId].replayView.rounds[selectedRoundByGame[gameSearchResult.gameId]] || [] as inputLog}
                        <div class="px-3 py-2 rounded bg-gray-100 dark:bg-gray-800 text-sm flex items-center justify-between gap-3">
                          <div>
                            <b>{@html processNick(inputLog.nickname)}</b>
                            <span class="mx-2 text-gray-400">→</span>
                            <span>{inputLog.word}</span>
                          </div>
                          <div class="text-gray-500 dark:text-gray-300">
                            +{(inputLog.elapsedTurnMs / 1000).toFixed(2)}초
                          </div>
                        </div>
                      {/each}
                    </div>
                  </div>
                {/if}
              {/if}
            </div>
          {/if}
        </article>
      </section>
    </div>
  {/if}

  {#if errorToast}
    <div class="fixed bottom-6 right-4 z-[90] max-w-[min(92vw,680px)] rounded-xl border border-rose-300/70 bg-rose-100 px-4 py-3 text-sm font-semibold text-rose-700 shadow-lg shadow-black/20 dark:border-rose-500/50 dark:bg-rose-950/90 dark:text-rose-100">
      {errorToast}
    </div>
  {/if}
</div>
