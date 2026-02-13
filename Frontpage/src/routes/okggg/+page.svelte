<script nonce="kkutuio">
  import { onMount } from 'svelte';
  import { browser } from '$app/environment';
  import { getLevel } from '../../lib/getLevelImg.js';

  const title = 'OKG.GG';
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

  let searchType = SEARCH_TYPE.nickname;
  let searchNick = '';
  let currentStatus = 'main';
  let selectedTab = 'profile';
  let uid = '';
  let loading = false;
  let loadingHistory = false;
  let errorMessage = '';

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
  let userNameCache = {};
  let selectedRoundByGame = {};
  let gameSearchResult = null;

  function normalizeSearchType(value) {
    if (value === SEARCH_TYPE.id) return SEARCH_TYPE.id;
    if (value === SEARCH_TYPE.gameId) return SEARCH_TYPE.gameId;
    return SEARCH_TYPE.nickname;
  }

  function searchPlaceholder() {
    if (searchType === SEARCH_TYPE.id) return '식별번호를 입력하세요.';
    if (searchType === SEARCH_TYPE.gameId) return '경기 ID를 입력하세요.';
    return '별명을 입력하세요.';
  }

  function normalizePageSize(value) {
    const parsed = Number(value);
    return ALLOWED_PAGE_SIZES.includes(parsed) ? parsed : 10;
  }

  function syncQuery() {
    if (!browser) return;
    const params = new URLSearchParams();
    if (searchType !== SEARCH_TYPE.nickname) params.set('type', searchType);
    if (searchNick) params.set('q', searchNick);
    if (uid) params.set('uid', uid);
    if (page > 1) params.set('page', String(page));
    if (pageSize !== 10) params.set('pageSize', String(pageSize));
    const query = params.toString();
    window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`);
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
        playHours: normalizePlayHoursFromMs(row.playTimeMs)
      }))
      .filter((row) => row.playTimeMs > 0 || row.wins > 0 || row.games > 0 || row.acceptedWords > 0 || row.exp > 0)
      .sort((a, b) => b.playTimeMs - a.playTimeMs || b.games - a.games);

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

  async function resolveUserName(id) {
    if (!id) return '-';
    if (userNameCache[id]) return userNameCache[id];
    const { body } = await fetchJson(`/user/${encodeURIComponent(id)}`);
    const nickname = body?.profile?.title || body?.profile?.name || id;
    userNameCache = { ...userNameCache, [id]: nickname };
    return nickname;
  }

  async function decodeReplayPayload(base64) {
    if (!base64 || !browser) return null;
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
    const players = payload.p.map((row, index) => ({
      index,
      id: row[0],
      nickname: row[1],
      level: Number(row[3] || 1),
      robot: Number(row[6] || 0) === 1
    }));
    const words = payload.w;
    const extras = Array.isArray(payload.x) ? payload.x : [];
    const inputs = Array.isArray(payload.i) ? payload.i : [];
    const modeEvents = Array.isArray(payload.mv) ? payload.mv : [];
    const rounds = {};
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
      const extraRaw = extraIndex >= 0 ? String(extras[extraIndex] || '') : '';
      const extraTokens = extraRaw ? extraRaw.split(',') : [];
      let displayWord = words[wordIndex] || '(알 수 없음)';
      if (extraTokens[0] === 'D') {
        const drawerIndex = Number(extraTokens[1] || -1);
        const drawerName = drawerIndex >= 0 ? (players[drawerIndex]?.nickname || `Player#${drawerIndex}`) : '-';
        const hintsGiven = Number(extraTokens[2] || 0);
        displayWord = `${displayWord} (정답 · 힌트 ${hintsGiven} · 화가 ${drawerName})`;
      } else if (extraTokens[0] === 'CA') {
        const score = Number(extraTokens[1] || 0);
        const bonus = Number(extraTokens[2] || 0);
        displayWord = `${displayWord} (인정 +${score}${bonus ? ` · 미션 +${bonus}` : ''})`;
      } else if (extraTokens[0] === 'CR') {
        const reason = extraTokens[1] || 'OTH';
        const code = Number(extraTokens[2] || -1);
        const label = rejectReasonLabel[reason] || rejectReasonLabel.OTH;
        displayWord = `${displayWord} (거절 · ${label}${code >= 0 ? ` · 코드 ${code}` : ''})`;
      } else if (extraTokens[0] === 'J') {
        const hintsGiven = Number(extraTokens[1] || 0);
        displayWord = `${displayWord} (정답 · 힌트 ${hintsGiven})`;
      } else if (extraTokens[0] === 'C') {
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
        displayWord = `시간초과 점수 ${delta >= 0 ? '+' : ''}${delta}`;
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
          nickname: players[playerIndex]?.nickname || `Player#${playerIndex}`,
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

    return {
      players,
      ranking,
      rounds,
      roundKeys,
      acceptedChain
    };
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
      record: body?.data?.record || {},
      raw: body
    };
    profile = nextProfile;
    moremi = normalizeEquip(body.equip);
    return nextProfile;
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
    errorMessage = '';
    currentStatus = 'user';
    if (resetPage) page = 1;
    syncQuery();
    try {
      const [loadedProfile, , replayModeStats] = await Promise.all([loadProfile(), loadHistory(page), loadModeStats()]);
      modeStats = buildModeStats(loadedProfile?.record || {}, replayModeStats || []);
      syncQuery();
    } catch (err) {
      errorMessage = err.message || '전적을 불러오지 못했습니다.';
    } finally {
      loading = false;
      loadingHistory = false;
    }
  }

  function resetSearchResult() {
    expandedGameId = '';
    detailMap = {};
    detailLoading = {};
    historyRows = [];
    hasNext = false;
    gameSearchResult = null;
  }

  async function searchGame() {
    const keyword = searchNick.trim();
    if (!keyword) return;
    loading = true;
    errorMessage = '';
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
        throw new Error('경기 조회 실패입니다: 입력한 경기 ID를 찾을 수 없습니다.');
      }
      const game = body.game;
      const userIds = Array.isArray(game.userIds) ? game.userIds : [];
      const participants = await Promise.all(userIds.map(async (id) => ({
        id,
        nickname: await resolveUserName(id),
        won: Array.isArray(game.winnerIds) ? game.winnerIds.includes(id) : false
      })));
      const payload = await decodeReplayPayload(game.payload);
      const replayView = buildReplayView(payload);
      const firstRound = replayView?.roundKeys?.[0] || 0;
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
      errorMessage = err.message || '경기 조회 실패입니다: 검색에 실패했습니다.';
      currentStatus = 'main';
    } finally {
      loading = false;
    }
  }

  async function searchUser() {
    const keyword = searchNick.trim();
    if (!keyword) return;
    loading = true;
    errorMessage = '';
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
      errorMessage = err.message || '사용자 조회 실패입니다: 사용자 검색에 실패했습니다.';
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
    const userIds = Array.isArray(game.userIds) ? game.userIds : [];
    const participants = await Promise.all(userIds.map(async (id) => ({
      id,
      nickname: await resolveUserName(id),
      won: Array.isArray(game.winnerIds) ? game.winnerIds.includes(id) : false
    })));
    const payload = await decodeReplayPayload(game.payload);
    const replayView = buildReplayView(payload);
    const firstRound = replayView?.roundKeys?.[0] || 0;
    selectedRoundByGame = { ...selectedRoundByGame, [gameId]: firstRound };
    detailLoading = { ...detailLoading, [gameId]: false };
    detailMap = { ...detailMap, [gameId]: { ...game, participants, replayView } };
  }

  function getModeLabel(row) {
    return MODE_LABEL[row.modeName] || MODE_LABEL[row.mode] || row.modeName || '일반';
  }

  onMount(async () => {
    const params = new URLSearchParams(window.location.search);
    const typeParam = normalizeSearchType(params.get('type') || SEARCH_TYPE.nickname);
    const nickParam = params.get('q') || params.get('nick') || '';
    const uidParam = params.get('uid') || '';
    const pageParam = Number(params.get('page') || 1);
    const sizeParam = normalizePageSize(params.get('pageSize') || 10);

    searchType = typeParam;
    searchNick = nickParam;
    uid = uidParam;
    page = Number.isFinite(pageParam) && pageParam > 0 ? Math.floor(pageParam) : 1;
    pageSize = sizeParam;

    if (searchType === SEARCH_TYPE.gameId && searchNick) await searchGame();
    else if (uid) await loadAll(false);
    else if (searchNick) await searchUser();
  });
</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>

<div class="dark:bg-gray-900">
  <div class="min-h-screen h-full py-40 px-4 flex flex-col items-center rankBg">
    <p class="text-gray-200 text-lg my-4 flex items-center gap-2">
      <span class="material-symbols-outlined">insights</span>
      전적 조회
    </p>
    <h1 class="text-white text-5xl font-bold mb-2 flex items-center gap-3">
      <span class="material-symbols-outlined text-5xl">sports_score</span>
      끄투리오 전적 검색
    </h1>
    <div class="search-wrap flex items-center border-3 border-white rounded-full p-2 mt-10">
      <select class="search-type-select" bind:value={searchType}>
        <option value={SEARCH_TYPE.nickname}>별명</option>
        <option value={SEARCH_TYPE.id}>식별번호</option>
        <option value={SEARCH_TYPE.gameId}>경기 ID</option>
      </select>
      <input
        bind:value={searchNick}
        type="text"
        class="ml-3 w-72 bg-transparent text-white outline-none"
        placeholder={searchPlaceholder()}
        on:keydown={(e) => e.key === 'Enter' && runSearch()}
      />
      <button class="text-white px-4 cursor-pointer" on:click={runSearch}>
        <i class="material-symbols-outlined icons-header">search</i>
      </button>
    </div>
    {#if errorMessage}
      <div class="mt-4 px-4 py-2 rounded-lg bg-red-100 text-red-700 text-sm">
        {errorMessage}
      </div>
    {/if}
  </div>

  {#if currentStatus === 'user' && profile}
    <div class="okgg-wrap lg:shadow-md mx-2 lg:mx-auto max-w-screen-xl -mt-16 mb-24 p-3 lg:p-4 bg-gray-100 dark:bg-gray-800 dark:text-white rounded-lg">
      <section class="rounded-xl overflow-hidden border border-gray-300/70 dark:border-gray-700">
        <div class="profile-header px-6 py-8 flex items-center justify-between gap-6">
          <div class="flex items-center gap-6">
            <div class="w-[108px] h-[108px] relative shrink-0 rounded-2xl bg-white/40">
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/back/${moremi.Mback || 'default.png'}`} class="absolute object-cover w-[108px] h-[108px]" alt="bg" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/body/${moremi.Mbody || 'default.png'}`} class="absolute object-cover w-[108px] h-[108px]" alt="body" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/eye/${moremi.Meye || 'default.png'}`} class="absolute object-cover w-[108px] h-[108px]" alt="eye" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/mouth/${moremi.Mmouth || 'default.png'}`} class="absolute object-cover w-[108px] h-[108px]" alt="mouth" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/clothes/${moremi.Mclothes || 'default.png'}`} class="absolute object-cover w-[108px] h-[108px]" alt="clothes" />
              <img src={`https://cdn.kkutu.io/img/kkutu/moremi/shoes/${moremi.Mshoes || 'default.png'}`} class="absolute object-cover w-[108px] h-[108px]" alt="shoes" />
            </div>
            <div>
              <div class="flex items-center gap-2 mb-2">
                <span class="badge-level">레벨 {profile.level}</span>
                <span class="badge-score">경험치: {Number(profile.score).toLocaleString()}점</span>
              </div>
              <div class="text-4xl font-bold leading-tight">{profile.nickname}</div>
              <div class="text-sm text-gray-600 dark:text-gray-300 mt-1">
                {profile.exordial || '소개 한마디가 없습니다.'}
              </div>
              <div class="text-xs text-gray-600 dark:text-gray-300 mt-2 flex items-center gap-1">
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
          <button class="refresh-btn" on:click={() => loadAll(false)} disabled={loading}>
            <span class="material-symbols-outlined text-xl">{loading ? 'progress_activity' : 'refresh'}</span>
            {loading ? '불러오는 중...' : '새로고침'}
          </button>
        </div>

        <div class="px-3 py-2 bg-neutral-800 flex items-center gap-2">
          <button class:selected={selectedTab === 'profile'} class="tab-btn" on:click={() => (selectedTab = 'profile')}>
            <span class="material-symbols-outlined text-base">person</span> 사용자 정보
          </button>
          <button class:selected={selectedTab === 'stats'} class="tab-btn" on:click={() => (selectedTab = 'stats')}>
            <span class="material-symbols-outlined text-base">query_stats</span> 통계
          </button>
          <button class:selected={selectedTab === 'history'} class="tab-btn" on:click={() => (selectedTab = 'history')}>
            <span class="material-symbols-outlined text-base">history</span> 경기 내역
          </button>
        </div>
      </section>

      {#if selectedTab === 'profile' || selectedTab === 'stats'}
        <section class="mt-5">
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {#if modeStats.length}
              {#each modeStats.slice(0, selectedTab === 'stats' ? modeStats.length : 3) as stat}
                <article class="stat-card">
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
              <article class="stat-card text-gray-500">기록된 통계가 없습니다.</article>
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
              <select class="rounded-md border px-2 py-1 bg-white dark:bg-gray-700" bind:value={pageSize} on:change={changePageSize}>
                {#each ALLOWED_PAGE_SIZES as size}
                  <option value={size}>{size}</option>
                {/each}
              </select>
            </div>
          </div>

          {#if loadingHistory}
            <div class="rounded-xl border bg-white dark:bg-gray-700 p-4 text-gray-500">불러오는 중...</div>
          {:else if !historyRows.length}
            <div class="rounded-xl border bg-white dark:bg-gray-700 p-4 text-gray-500">최근 3년 내 경기 기록이 없습니다.</div>
          {:else}
            <div class="space-y-3">
              {#each historyRows as row}
                <article class="match-card bg-white dark:bg-gray-700" style={`border-left: 6px solid ${row.won ? '#eab308' : '#9ca3af'}`}>
                  <button class="w-full text-left p-4 cursor-pointer" on:click={() => toggleDetail(row.gameId)}>
                    <div class="flex items-center justify-between gap-3">
                      <div class="text-3xl font-black">
                        #{row.placement}
                        <span class="text-lg font-semibold text-gray-500 dark:text-gray-300">/ {row.playerCount}</span>
                      </div>
                      <div class="flex-1 min-w-0">
                        <div class="font-bold text-lg truncate">{getModeLabel(row)}</div>
                        <div class="text-sm text-gray-500 dark:text-gray-300">{row.roomTitle || '제목 없음'}</div>
                        <div class="text-sm text-gray-500 dark:text-gray-300 mt-1">{formatAgo(row.startedAt)} · {formatDate(row.startedAt)}</div>
                      </div>
                      <div class="text-right shrink-0">
                        <div class="text-2xl font-extrabold">{Number(row.score).toLocaleString()}점</div>
                        <div class="text-sm text-gray-500 dark:text-gray-300">EXP +{Number(row.exp || 0).toLocaleString()}</div>
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
                        <div class="text-sm text-gray-600 dark:text-gray-300 mb-3">
                          경기 ID: <code>{row.gameId}</code>
                        </div>
                        <div class="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
                          <div>채널: <b>{detailMap[row.gameId].channel}</b></div>
                          <div>방 번호: <b>{detailMap[row.gameId].roomId}</b></div>
                          <div>규칙: <b>{detailMap[row.gameId].rule}</b></div>
                          <div>언어: <b>{detailMap[row.gameId].lang}</b></div>
                          <div>경기 시간: <b>{formatDuration(detailMap[row.gameId].durationMs)}</b></div>
                          <div>압축 크기: <b>{Number(detailMap[row.gameId].payloadSize || 0).toLocaleString()} bytes</b></div>
                        </div>
                        <div class="mt-3 text-sm">
                          <div class="font-semibold mb-2">참가자</div>
                          <div class="space-y-1">
                            {#each detailMap[row.gameId].participants || [] as participant}
                              <div class:font-bold={participant.id === uid} class="flex items-center justify-between px-3 py-2 rounded bg-gray-100 dark:bg-gray-800">
                                <span>{participant.nickname}</span>
                                {#if participant.won}
                                  <span class="text-yellow-600">우승</span>
                                {/if}
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
                                    <th class="py-2">닉네임</th>
                                    <th class="py-2 text-right">점수</th>
                                    <th class="py-2 text-right">경험치</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {#each detailMap[row.gameId].replayView.ranking as rankRow}
                                    <tr class="border-b border-gray-100 dark:border-gray-700">
                                      <td class="py-2">{rankRow.placement}위</td>
                                      <td class="py-2">{rankRow.nickname}</td>
                                      <td class="py-2 text-right">{rankRow.score.toLocaleString()}</td>
                                      <td class="py-2 text-right">+{rankRow.exp.toLocaleString()}</td>
                                    </tr>
                                  {/each}
                                </tbody>
                              </table>
                            </div>
                          </div>

                          <div class="mt-5">
                            <div class="font-semibold mb-2">낱말 내역</div>
                            <div class="flex flex-wrap gap-2 mb-3">
                              {#each detailMap[row.gameId].replayView.roundKeys as roundKey}
                                <button
                                  class:round-selected={selectedRoundByGame[row.gameId] === roundKey}
                                  class="round-btn"
                                  on:click={() => (selectedRoundByGame = { ...selectedRoundByGame, [row.gameId]: roundKey })}
                                >
                                  라운드 {roundKey}
                                </button>
                              {/each}
                            </div>
                            <div class="space-y-2">
                              {#each detailMap[row.gameId].replayView.rounds[selectedRoundByGame[row.gameId]] || [] as inputLog}
                                <div class="px-3 py-2 rounded bg-gray-100 dark:bg-gray-800 text-sm flex items-center justify-between gap-3">
                                  <div>
                                    <b>{inputLog.nickname}</b>
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
              <button class="page-btn" on:click={() => movePage(page - 1)} disabled={page <= 1 || loading}>이전</button>
              <span class="text-sm text-gray-600 dark:text-gray-300">{page}쪽</span>
              <button class="page-btn" on:click={() => movePage(page + 1)} disabled={!hasNext || loading}>다음</button>
            </div>
          {/if}
        </section>
      {/if}
    </div>
  {/if}

  {#if currentStatus === 'game' && gameSearchResult}
    <div class="okgg-wrap lg:shadow-md mx-2 lg:mx-auto max-w-screen-xl -mt-16 mb-24 p-3 lg:p-4 bg-gray-100 dark:bg-gray-800 dark:text-white rounded-lg">
      <section class="mt-2">
        <h3 class="text-2xl font-bold mb-3">경기 조회 결과</h3>
        <article class="match-card bg-white dark:bg-gray-700">
          <button class="w-full text-left p-4 cursor-pointer" on:click={() => toggleDetail(gameSearchResult.gameId)}>
            <div class="flex items-center justify-between gap-3">
              <div class="text-2xl font-black">{gameSearchResult.gameId}</div>
              <div class="flex-1 min-w-0">
                <div class="font-bold text-lg truncate">{getModeLabel(gameSearchResult)}</div>
                <div class="text-sm text-gray-500 dark:text-gray-300">{gameSearchResult.roomTitle || '제목 없음'}</div>
                <div class="text-sm text-gray-500 dark:text-gray-300 mt-1">{formatAgo(gameSearchResult.startedAt)} · {formatDate(gameSearchResult.startedAt)}</div>
              </div>
              <div class="text-right shrink-0">
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
                <div>규칙: <b>{detailMap[gameSearchResult.gameId].rule}</b></div>
                <div>언어: <b>{detailMap[gameSearchResult.gameId].lang}</b></div>
                <div>경기 시간: <b>{formatDuration(detailMap[gameSearchResult.gameId].durationMs)}</b></div>
                <div>압축 크기: <b>{Number(detailMap[gameSearchResult.gameId].payloadSize || 0).toLocaleString()} bytes</b></div>
              </div>
              <div class="mt-3 text-sm">
                <div class="font-semibold mb-2">참가자</div>
                <div class="space-y-1">
                  {#each detailMap[gameSearchResult.gameId].participants || [] as participant}
                    <div class="flex items-center justify-between px-3 py-2 rounded bg-gray-100 dark:bg-gray-800">
                      <span>{participant.nickname}</span>
                      {#if participant.won}
                        <span class="text-yellow-600">우승</span>
                      {/if}
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
                          <th class="py-2">닉네임</th>
                          <th class="py-2 text-right">점수</th>
                          <th class="py-2 text-right">EXP</th>
                        </tr>
                      </thead>
                      <tbody>
                        {#each detailMap[gameSearchResult.gameId].replayView.ranking as rankRow}
                          <tr class="border-b border-gray-100 dark:border-gray-700">
                            <td class="py-2">{rankRow.placement}위</td>
                            <td class="py-2">{rankRow.nickname}</td>
                            <td class="py-2 text-right">{rankRow.score.toLocaleString()}</td>
                            <td class="py-2 text-right">+{rankRow.exp.toLocaleString()}</td>
                          </tr>
                        {/each}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="mt-5">
                  <div class="font-semibold mb-2">라운드 낱말 로그</div>
                  <div class="flex flex-wrap gap-2 mb-3">
                    {#each detailMap[gameSearchResult.gameId].replayView.roundKeys as roundKey}
                      <button
                        class:round-selected={selectedRoundByGame[gameSearchResult.gameId] === roundKey}
                        class="round-btn"
                        on:click={() => (selectedRoundByGame = { ...selectedRoundByGame, [gameSearchResult.gameId]: roundKey })}
                      >
                        라운드 {roundKey}
                      </button>
                    {/each}
                  </div>
                  <div class="space-y-2">
                    {#each detailMap[gameSearchResult.gameId].replayView.rounds[selectedRoundByGame[gameSearchResult.gameId]] || [] as inputLog}
                      <div class="px-3 py-2 rounded bg-gray-100 dark:bg-gray-800 text-sm flex items-center justify-between gap-3">
                        <div>
                          <b>{inputLog.nickname}</b>
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
            </div>
          {/if}
        </article>
      </section>
    </div>
  {/if}
</div>

<style>
  .okgg-wrap {
    border: 1px solid rgba(156, 163, 175, 0.24);
    box-shadow: 0 20px 40px rgba(15, 23, 42, 0.08);
    backdrop-filter: blur(6px);
  }
  .profile-header {
    background: linear-gradient(135deg, #d8e8cd 0%, #c7dcc5 42%, #dfeeda 100%);
  }
  .badge-level {
    background: #111827;
    color: #fff;
    font-weight: 700;
    border-radius: 999px;
    padding: 2px 10px;
    font-size: 14px;
  }
  .badge-score {
    background: #8b5cf6;
    color: #fff;
    font-weight: 700;
    border-radius: 999px;
    padding: 2px 10px;
    font-size: 14px;
  }
  .refresh-btn {
    background: #fbbf24;
    color: #111827;
    font-weight: 800;
    border-radius: 12px;
    padding: 12px 20px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    box-shadow: 0 8px 20px rgba(251, 191, 36, 0.3);
  }
  .refresh-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  .tab-btn {
    color: #fff;
    border-radius: 10px 10px 0 0;
    padding: 10px 14px;
    font-weight: 700;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    transition: background-color 0.15s ease;
  }
  .tab-btn.selected {
    background: #fff;
    color: #111827;
  }
  .stat-card {
    border: 1px solid rgba(156, 163, 175, 0.35);
    border-radius: 14px;
    background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
    padding: 16px;
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
    transition: transform 0.15s ease, box-shadow 0.15s ease;
  }
  .stat-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 26px rgba(15, 23, 42, 0.12);
  }
  .match-card {
    border: 1px solid rgba(156, 163, 175, 0.35);
    border-radius: 14px;
    overflow: hidden;
    box-shadow: 0 6px 20px rgba(15, 23, 42, 0.05);
  }
  .page-btn {
    border: 1px solid rgba(107, 114, 128, 0.35);
    border-radius: 10px;
    padding: 6px 14px;
    background: white;
    cursor: pointer;
  }
  .page-btn:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
  .search-wrap {
    width: min(560px, 92vw);
    background: rgba(15, 23, 42, 0.4);
    box-shadow: 0 12px 30px rgba(15, 23, 42, 0.32);
  }
  .search-type-select {
    background: rgba(17, 24, 39, 0.82);
    color: #fff;
    border-radius: 9999px;
    border: 1px solid rgba(255, 255, 255, 0.35);
    padding: 6px 12px;
  }
  .round-btn {
    border: 1px solid rgba(107, 114, 128, 0.35);
    border-radius: 999px;
    padding: 5px 12px;
    font-size: 13px;
    cursor: pointer;
    background: #fff;
  }
  .round-selected {
    background: #111827;
    color: #fff;
  }
</style>
