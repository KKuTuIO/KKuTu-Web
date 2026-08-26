<script>
  import { onMount } from 'svelte';
  import { academyApi, friendlyError } from './api.js';

  let { config } = $props();
  let gameId = $state('');
  let replay = $state(null);
  let loading = $state(false);
  let error = $state('');
  let query = $state('');
  let selectedWord = $state('');
  let alternatives = $state(null);

  function decodeWords(indices, dictionary) {
    if (!Array.isArray(indices)) return [];
    return indices.map((index) => dictionary[index]).filter(Boolean);
  }

  function decodePlayerChains(payload, dictionary) {
    if (!Array.isArray(payload?.ap)) return [];
    return payload.ap
      .filter((row) => Array.isArray(row) && Array.isArray(row[1]))
      .map(([playerIndex, indices]) => ({
        playerIndex,
        player: payload?.p?.[playerIndex]?.[1] || payload?.p?.[playerIndex]?.[0] || `플레이어 ${playerIndex + 1}`,
        words: decodeWords(indices, dictionary)
      }))
      .filter((entry) => entry.words.length);
  }

  let payload = $derived(replay?.game?.detailPayload || null);
  let words = $derived(Array.isArray(payload?.w) ? payload.w : []);
  let playerChains = $derived(decodePlayerChains(payload, words));
  let globalAccepted = $derived(decodeWords(payload?.a, words));
  let accepted = $derived(
    globalAccepted.length
      ? globalAccepted
      : playerChains.flatMap((entry) => entry.words)
  );
  let uniqueAccepted = $derived([...new Set(accepted)]);
  let filtered = $derived(uniqueAccepted.filter((word) => !query || word.includes(query)));
  let longest = $derived(uniqueAccepted.reduce((best, word) => word.length > (best?.length || 0) ? word : best, ''));
  let inputRows = $derived(
    Array.isArray(payload?.i)
      ? payload.i.map((row) => ({
          player: payload?.p?.[row[0]]?.[1] || payload?.p?.[row[0]]?.[0] || '알 수 없음',
          word: words[row[1]] || '',
          elapsedTurnMs: row[2] || 0,
          round: row[4] || 0,
          turn: row[5] || 0,
          extra: payload?.x?.[row[6]] || ''
        }))
      : []
  );

  onMount(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('game')) {
      gameId = params.get('game');
      load();
    }
  });

  async function load(event) {
    event?.preventDefault();
    if (!gameId.trim()) return;
    loading = true;
    error = '';
    replay = null;
    alternatives = null;
    try {
      const response = await academyApi.replay(gameId.trim());
      if (!response?.ok || !response?.game) throw new Error(response?.error || '게임 기록을 찾지 못했습니다.');
      replay = response;
      config.lang = response.game.lang || config.lang;
      const url = new URL(window.location.href);
      url.searchParams.set('tab', 'replay');
      url.searchParams.set('game', gameId.trim());
      history.replaceState(null, '', url);
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  async function inspect(word) {
    selectedWord = word;
    alternatives = null;
    const required = config.direction === 'REVERSE' ? word[0] : word.at(-1);
    if (!required) return;
    loading = true;
    try {
      alternatives = await academyApi.strategy(config, required, uniqueAccepted, 6);
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  async function copyChain() {
    if (accepted.length) await navigator.clipboard.writeText(accepted.join(' → '));
  }

  function formatDuration(value) {
    if (!Number.isFinite(value)) return '-';
    const seconds = Math.floor(value / 1000);
    return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`;
  }
</script>

<div class="grid gap-5 xl:grid-cols-[330px_minmax(0,1fr)]">
  <aside class="h-fit rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 xl:sticky xl:top-28">
    <div class="flex items-center gap-3"><span class="grid h-11 w-11 place-items-center rounded-2xl bg-sky-100 text-sky-700 dark:bg-sky-950/60 dark:text-sky-300"><span class="material-symbols-outlined">movie_edit</span></span><div><h2 class="font-black text-slate-900 dark:text-white">내 게임 복기</h2><p class="text-xs text-slate-500">실제 수순에서 놓친 선택지를 찾습니다.</p></div></div>
    <form class="mt-5 grid gap-3" onsubmit={load}>
      <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">게임 ID
        <input bind:value={gameId} placeholder="리플레이 게임 ID" class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 font-mono text-sm outline-none focus:border-sky-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
      </label>
      <button type="submit" disabled={loading || !gameId.trim()} class="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-sky-600 px-4 font-black text-white hover:bg-sky-700 disabled:opacity-50"><span class={`material-symbols-outlined ${loading ? 'animate-spin' : ''}`}>{loading ? 'progress_activity' : 'search'}</span>기록 불러오기</button>
    </form>
    <div class="mt-4 rounded-2xl bg-slate-50 p-4 text-xs leading-5 text-slate-500 dark:bg-slate-800/70 dark:text-slate-400">전적 화면이나 공유받은 리플레이의 게임 ID를 입력하세요. 서버의 기존 공개 범위와 참가자 권한을 그대로 따릅니다.</div>

    {#if replay?.game}
      <dl class="mt-4 grid grid-cols-2 gap-2 text-center">
        <div class="rounded-xl bg-slate-50 p-3 dark:bg-slate-800"><dt class="text-xs text-slate-400">모드</dt><dd class="mt-1 font-black dark:text-white">{replay.game.modeName}</dd></div>
        <div class="rounded-xl bg-slate-50 p-3 dark:bg-slate-800"><dt class="text-xs text-slate-400">인원</dt><dd class="mt-1 font-black dark:text-white">{replay.game.playerCount}명</dd></div>
        <div class="rounded-xl bg-slate-50 p-3 dark:bg-slate-800"><dt class="text-xs text-slate-400">플레이 시간</dt><dd class="mt-1 font-black dark:text-white">{formatDuration(replay.game.durationMs)}</dd></div>
        <div class="rounded-xl bg-slate-50 p-3 dark:bg-slate-800"><dt class="text-xs text-slate-400">성공 단어</dt><dd class="mt-1 font-black dark:text-white">{uniqueAccepted.length}개</dd></div>
      </dl>
    {/if}
  </aside>

  <section class="min-w-0">
    {#if error}<p class="mb-4 rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</p>{/if}
    {#if !replay}
      <div class="grid min-h-[500px] place-items-center rounded-3xl border border-dashed border-slate-300 bg-white/60 p-8 text-center dark:border-slate-700 dark:bg-slate-900/60"><div><span class="material-symbols-outlined text-6xl text-sky-300 dark:text-sky-800">sports_esports</span><h3 class="mt-4 text-2xl font-black text-slate-800 dark:text-white">실제 게임을 최고의 교재로 바꾸세요.</h3><p class="mt-2 max-w-lg text-sm leading-6 text-slate-500">성공 수순, 플레이어별 입력 시간과 각 단어 뒤에 가능했던 공개 사전 대안을 확인할 수 있습니다.</p></div></div>
    {:else}
      <div class="grid gap-3 sm:grid-cols-3">
        <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"><span class="text-xs font-bold text-slate-400">최장 단어</span><strong class="mt-1 block break-all text-lg text-slate-900 dark:text-white">{longest || '-'}</strong></div>
        <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"><span class="text-xs font-bold text-slate-400">고유 입력 단어</span><strong class="mt-1 block text-2xl text-slate-900 dark:text-white">{new Set(words).size}</strong></div>
        <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"><span class="text-xs font-bold text-slate-400">입력 이벤트</span><strong class="mt-1 block text-2xl text-slate-900 dark:text-white">{inputRows.length}</strong></div>
      </div>

      <section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div class="flex flex-wrap items-center justify-between gap-3"><div><p class="text-xs font-black text-sky-600">ACCEPTED CHAIN</p><h3 class="text-xl font-black text-slate-900 dark:text-white">{globalAccepted.length ? '성공 수순' : '전체 성공 단어'}</h3></div><div class="flex gap-2"><input bind:value={query} placeholder="수순 내 검색" class="w-36 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-white" /><button type="button" onclick={copyChain} class="grid h-10 w-10 place-items-center rounded-xl border border-slate-200 text-slate-500 dark:border-slate-700" aria-label="수순 복사"><span class="material-symbols-outlined text-lg">content_copy</span></button></div></div>
        {#if uniqueAccepted.length}
          <div class="mt-4 flex max-h-80 flex-wrap items-center gap-2 overflow-y-auto">
            {#each filtered as word, index}<button type="button" onclick={() => inspect(word)} class={`rounded-xl border px-3 py-2 text-left transition ${selectedWord === word ? 'border-sky-400 bg-sky-50 dark:bg-sky-950/30' : 'border-slate-200 bg-slate-50 hover:border-sky-300 dark:border-slate-700 dark:bg-slate-800'}`}><span class="block text-[10px] font-bold text-slate-400">{index + 1}</span><strong class="text-slate-800 dark:text-white">{word}</strong></button>{/each}
          </div>
        {:else}<p class="mt-4 text-sm text-slate-400">이 리플레이에는 복기 가능한 성공 체인이 기록되지 않았습니다. 아래 입력 이벤트를 이용해 복기할 수 있습니다.</p>{/if}
      </section>

      {#if playerChains.length}
        <section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
          <div><p class="text-xs font-black text-violet-600">PER-PLAYER CHAIN</p><h3 class="text-xl font-black text-slate-900 dark:text-white">플레이어별 성공 수순</h3></div>
          <div class="mt-4 grid gap-3 lg:grid-cols-2">
            {#each playerChains as entry}
              <article class="rounded-2xl border border-violet-100 bg-violet-50/60 p-4 dark:border-violet-900 dark:bg-violet-950/20">
                <div class="flex items-center justify-between gap-3"><strong class="truncate text-violet-800 dark:text-violet-200">{entry.player}</strong><span class="shrink-0 rounded-full bg-white px-2.5 py-1 text-xs font-black text-violet-600 dark:bg-slate-900 dark:text-violet-300">{entry.words.length}개</span></div>
                <div class="mt-3 flex max-h-40 flex-wrap gap-1.5 overflow-y-auto">
                  {#each entry.words as word}<button type="button" onclick={() => inspect(word)} class="rounded-lg bg-white px-2.5 py-1.5 text-sm font-bold text-slate-700 shadow-sm hover:text-sky-600 dark:bg-slate-900 dark:text-slate-200">{word}</button>{/each}
                </div>
              </article>
            {/each}
          </div>
        </section>
      {/if}

      {#if alternatives}
        <section class="mt-5 rounded-3xl border border-sky-200 bg-sky-50 p-5 dark:border-sky-900 dark:bg-sky-950/25">
          <div class="flex items-center justify-between"><div><p class="text-xs font-black text-sky-600">WHAT ELSE?</p><h3 class="text-lg font-black text-slate-900 dark:text-white">‘{selectedWord}’ 뒤의 공개 사전 대안</h3></div><span class="rounded-full bg-white px-3 py-1 text-xs font-black text-sky-700 dark:bg-slate-900">{alternatives.state || 'UNKNOWN'}</span></div>
          <div class="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{#each alternatives.alternatives.slice(0, 12) as move}<div class="rounded-xl bg-white p-3 dark:bg-slate-900"><strong class="dark:text-white">{move.word}</strong><span class="ml-2 text-xs text-slate-400">→{move.to}</span><p class="mt-1 text-xs text-slate-500">방어 {move.defenseCount}개 · {move.resultingState}</p></div>{/each}</div>
        </section>
      {/if}

      <section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <h3 class="text-lg font-black text-slate-900 dark:text-white">입력 타임라인</h3>
        <div class="mt-3 max-h-[480px] overflow-y-auto">
          {#each inputRows as row}
            <div class="grid grid-cols-[auto_1fr_auto] items-center gap-3 border-b border-slate-100 py-2.5 last:border-0 dark:border-slate-800"><span class="rounded-lg bg-slate-100 px-2 py-1 text-xs font-black text-slate-500 dark:bg-slate-800">R{row.round} T{row.turn}</span><div class="min-w-0"><strong class="block truncate text-slate-800 dark:text-white">{row.word}</strong><span class="text-xs text-slate-400">{row.player}</span></div><div class="text-right"><strong class="text-sm text-slate-600 dark:text-slate-300">{(row.elapsedTurnMs / 1000).toFixed(2)}초</strong><span class={`ml-2 rounded px-1.5 py-0.5 text-[10px] font-black ${String(row.extra).startsWith('CR') ? 'bg-rose-100 text-rose-600' : 'bg-emerald-100 text-emerald-600'}`}>{String(row.extra).startsWith('CR') ? '실패' : '성공'}</span></div></div>
          {/each}
        </div>
      </section>
    {/if}
  </section>
</div>