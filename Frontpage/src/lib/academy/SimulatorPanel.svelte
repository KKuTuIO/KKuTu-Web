<script>
  import { onMount } from 'svelte';
  import { academyApi, friendlyError } from './api.js';

  let { config } = $props();
  let chain = $state([]);
  let input = $state('');
  let shields = $state(1);
  let botLevel = $state('EXPERT');
  let autoBot = $state(true);
  let loading = $state(false);
  let error = $state('');
  let result = $state(null);
  let suggestion = $state(null);

  let chainRequiredChar = $derived(
    chain.length
      ? config.direction === 'REVERSE'
        ? chain.at(-1)?.[0] || ''
        : chain.at(-1)?.at(-1) || ''
      : ''
  );
  let requiredChar = $derived(result?.nextChar || chainRequiredChar);

  function stateLabel(state) {
    if (state === 'WIN') return '승리';
    if (state === 'LOSS') return '패배';
    if (state === 'ROUTE') return '루트';
    return state || '-';
  }

  onMount(() => {
    const params = new URLSearchParams(window.location.search);
    const start = params.get('start');
    const savedChain = params.get('chain');
    if (params.get('dictionary')) config.dictionary = params.get('dictionary');
    if (start) input = start;
    if (savedChain) chain = savedChain.split(',').map((item) => item.trim()).filter(Boolean).slice(0, 100);
  });

  async function submit(event) {
    event?.preventDefault();
    if (!input.trim()) return;
    loading = true;
    error = '';
    suggestion = null;
    try {
      const response = await academyApi.simulator(
        config,
        chain,
        input.trim(),
        shields,
        autoBot ? botLevel : null
      );
      result = response;
      if (response.shieldUsed) shields = Math.max(0, shields - 1);
      if (response.accepted) {
        chain = response.chain;
        input = '';
        if (autoBot && response.botMove) {
          chain = [...chain, response.botMove.word];
          result = { ...response, nextChar: response.botMove.to };
        }
      }
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  async function expertPick() {
    const target = requiredChar || input.trim().slice(0, 1);
    if (!target) {
      error = '첫 단어를 입력하거나 시작 음절을 입력해 주세요.';
      return;
    }
    loading = true;
    error = '';
    try {
      const response = await academyApi.strategy(config, target, chain, 8);
      suggestion = response.alternatives?.[0] || null;
      if (!suggestion) error = '현재 음절에서 추천할 수 있는 단어가 없습니다.';
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  function undo() {
    if (!chain.length) return;
    chain = chain.slice(0, -1);
    result = null;
    suggestion = null;
  }

  function reset() {
    chain = [];
    input = '';
    result = null;
    suggestion = null;
    shields = 1;
  }

  async function copySequence() {
    const text = chain.join(' → ');
    if (!text) return;
    await navigator.clipboard.writeText(text);
  }

  async function share() {
    const url = new URL(window.location.href);
    url.searchParams.set('tab', 'simulator');
    url.searchParams.set('dictionary', config.dictionary);
    if (chain[0]) url.searchParams.set('start', chain[0]);
    if (chain.length) url.searchParams.set('chain', chain.join(','));
    await navigator.clipboard.writeText(url.toString());
  }
</script>

<div class="grid min-w-0 gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
  <section class="min-w-0 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 md:p-7">
    <div class="flex min-w-0 flex-wrap items-center justify-between gap-4">
      <h2 class="text-2xl font-black text-slate-900 dark:text-white">끝말잇기 수순 시뮬레이터</h2>
      <div class="flex shrink-0 gap-2">
        <button type="button" onclick={undo} disabled={!chain.length} class="inline-flex items-center gap-1 rounded-xl border border-slate-200 px-3 py-2 text-sm font-bold text-slate-600 disabled:opacity-40 dark:border-slate-700 dark:text-slate-300"><span class="material-symbols-outlined text-lg">undo</span>한 수 뒤로</button>
        <button type="button" onclick={reset} class="grid h-10 w-10 place-items-center rounded-xl border border-slate-200 text-slate-500 dark:border-slate-700" aria-label="초기화"><span class="material-symbols-outlined">restart_alt</span></button>
      </div>
    </div>

    <div class="mt-6 min-h-44 min-w-0 rounded-3xl bg-gradient-to-br from-slate-50 to-emerald-50/60 p-4 dark:from-slate-950 dark:to-emerald-950/20">
      {#if chain.length === 0}
        <div class="grid min-h-36 place-items-center text-center">
          <div>
            <span class="material-symbols-outlined text-5xl text-emerald-300 dark:text-emerald-800">conversion_path</span>
            <p class="mt-2 font-black text-slate-700 dark:text-slate-200">첫 단어를 입력하세요.</p>
          </div>
        </div>
      {:else}
        <div class="flex min-w-0 flex-wrap items-center gap-2">
          {#each chain as word, index}
            <div class={`max-w-full rounded-2xl border px-4 py-3 shadow-sm ${autoBot && index % 2 === 1 ? 'border-violet-200 bg-violet-50 text-violet-800 dark:border-violet-900 dark:bg-violet-950/50 dark:text-violet-200' : 'border-emerald-200 bg-white text-slate-800 dark:border-emerald-900 dark:bg-slate-900 dark:text-white'}`}>
              <span class="block text-[10px] font-black tracking-wider opacity-50">{autoBot && index % 2 === 1 ? '봇' : `${index + 1}수`}</span>
              <strong class="break-all text-lg">{word}</strong>
            </div>
            {#if index < chain.length - 1}<span class="material-symbols-outlined text-slate-300">arrow_forward</span>{/if}
          {/each}
        </div>
      {/if}
    </div>

    <form class="mt-5 min-w-0" onsubmit={submit}>
      <div class="flex min-w-0 flex-col gap-3 sm:flex-row">
        <div class="relative min-w-0 flex-1">
          {#if requiredChar}
            <span class="absolute left-4 top-1/2 -translate-y-1/2 rounded-lg bg-emerald-100 px-2 py-1 text-sm font-black text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">{requiredChar}</span>
          {/if}
          <input bind:value={input} autocomplete="off" placeholder={requiredChar ? `‘${requiredChar}’에서 이어지는 단어` : '첫 단어를 입력하세요'} class={`w-full min-w-0 rounded-2xl border border-slate-200 bg-slate-50 py-4 pr-4 text-lg font-black outline-none transition focus:border-emerald-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white ${requiredChar ? 'pl-16' : 'pl-4'}`} />
        </div>
        <button type="submit" disabled={loading || !input.trim()} class="inline-flex min-h-14 shrink-0 items-center justify-center gap-2 rounded-2xl bg-emerald-600 px-6 font-black text-white transition hover:bg-emerald-700 disabled:opacity-50">
          <span class={`material-symbols-outlined ${loading ? 'animate-spin' : ''}`}>{loading ? 'progress_activity' : 'play_arrow'}</span>입력
        </button>
        <button type="button" onclick={expertPick} disabled={loading} class="inline-flex min-h-14 shrink-0 items-center justify-center gap-2 rounded-2xl bg-slate-900 px-5 font-black text-white transition hover:bg-slate-700 disabled:opacity-50 dark:bg-white dark:text-slate-900">
          <span class="material-symbols-outlined">psychology</span>고수픽
        </button>
      </div>
    </form>

    {#if error}
      <p class="mt-3 break-words rounded-xl bg-rose-50 px-4 py-3 text-sm font-bold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</p>
    {/if}
    {#if result}
      <div class={`mt-4 rounded-2xl border p-4 ${result.accepted ? 'border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950/30' : 'border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/30'}`}>
        <div class="flex items-center gap-2 font-black text-slate-800 dark:text-white">
          <span class="material-symbols-outlined">{result.accepted ? 'check_circle' : result.shieldUsed ? 'shield' : 'error'}</span>
          {result.message}
        </div>
        {#if result.analysis}
          <p class="mt-2 text-sm text-slate-600 dark:text-slate-300">다음 글자 <strong>{result.analysis.to}</strong> · 방어 {result.analysis.defenseCount}개 · {stateLabel(result.analysis.resultingState)}</p>
        {/if}
      </div>
    {/if}

    {#if suggestion}
      <button type="button" onclick={() => input = suggestion.word} class="mt-4 w-full min-w-0 rounded-2xl border border-violet-200 bg-violet-50 p-4 text-left transition hover:border-violet-400 dark:border-violet-900 dark:bg-violet-950/30">
        <span class="text-xs font-black text-violet-600 dark:text-violet-300">추천</span>
        <div class="mt-1 flex min-w-0 flex-wrap items-end justify-between gap-3">
          <strong class="min-w-0 break-all text-xl text-slate-900 dark:text-white">{suggestion.word}</strong>
          <span class="text-sm text-slate-500">{suggestion.to} · 방어 {suggestion.defenseCount}개 · {stateLabel(suggestion.resultingState)}</span>
        </div>
      </button>
    {/if}

    {#if result?.alternatives?.length}
      <div class="mt-5 min-w-0">
        <h3 class="text-sm font-black text-slate-700 dark:text-slate-200">다른 선택지</h3>
        <div class="mt-2 flex min-w-0 flex-wrap gap-2">
          {#each result.alternatives as move}
            <button type="button" onclick={() => input = move.word} class="max-w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-left text-sm transition hover:border-emerald-400 dark:border-slate-700 dark:bg-slate-800">
              <strong class="break-all text-slate-800 dark:text-white">{move.word}</strong>
              <span class="ml-1 text-xs text-slate-400">→ {move.to} ({move.defenseCount})</span>
            </button>
          {/each}
        </div>
      </div>
    {/if}
  </section>

  <aside class="grid h-fit min-w-0 gap-4 xl:sticky xl:top-28">
    <section class="min-w-0 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <h3 class="font-black text-slate-900 dark:text-white">실험 설정</h3>
      <div class="mt-4 grid min-w-0 gap-4">
        <label class="flex min-w-0 items-center justify-between rounded-xl bg-slate-50 px-3 py-3 text-sm font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-200">
          봇 자동 응답
          <input type="checkbox" bind:checked={autoBot} class="h-4 w-4 shrink-0 accent-emerald-600" />
        </label>
        <label class="grid min-w-0 gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">
          봇 선택 방식
          <select bind:value={botLevel} disabled={!autoBot} class="w-full min-w-0 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-800 dark:text-white">
            <option value="RANDOM">무작위</option>
            <option value="BALANCED">표준</option>
            <option value="EXPERT">고수 선택</option>
          </select>
        </label>
        <div class="rounded-2xl bg-sky-50 p-4 dark:bg-sky-950/30">
          <div class="flex items-center justify-between">
            <span class="text-sm font-black text-sky-700 dark:text-sky-300">보호막</span>
            <strong class="text-xl text-sky-800 dark:text-sky-200">{shields}</strong>
          </div>
        </div>
      </div>
    </section>

    <section class="min-w-0 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <h3 class="font-black text-slate-900 dark:text-white">수순 도구</h3>
      <div class="mt-3 grid grid-cols-2 gap-2">
        <button type="button" onclick={copySequence} disabled={!chain.length} class="inline-flex items-center justify-center gap-1 rounded-xl bg-slate-100 px-3 py-2.5 text-sm font-bold text-slate-600 disabled:opacity-40 dark:bg-slate-800 dark:text-slate-300"><span class="material-symbols-outlined text-lg">content_copy</span>복사</button>
        <button type="button" onclick={share} class="inline-flex items-center justify-center gap-1 rounded-xl bg-slate-100 px-3 py-2.5 text-sm font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300"><span class="material-symbols-outlined text-lg">share</span>공유</button>
      </div>
      <dl class="mt-4 grid grid-cols-2 gap-3 text-center">
        <div class="rounded-xl bg-slate-50 p-3 dark:bg-slate-800"><dt class="text-xs text-slate-400">수순 길이</dt><dd class="mt-1 text-lg font-black dark:text-white">{chain.length}</dd></div>
        <div class="rounded-xl bg-slate-50 p-3 dark:bg-slate-800"><dt class="text-xs text-slate-400">현재 글자</dt><dd class="mt-1 text-lg font-black dark:text-white">{requiredChar || '-'}</dd></div>
      </dl>
    </section>
  </aside>
</div>
