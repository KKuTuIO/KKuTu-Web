<script>
  import { academyApi, friendlyError } from './api.js';

  let { config } = $props();
  let maxPly = $state(7);
  let exclusions = $state('');
  let comparedExclusions = $state('');
  let analysis = $state(null);
  let comparison = $state(null);
  let strategy = $state(null);
  let startChar = $state('');
  let strategyDepth = $state(10);
  let stateTab = $state('WIN');
  let stateQuery = $state('');
  let loading = $state(false);
  let error = $state('');

  let stateRows = $derived(
    analysis
      ? Object.values(analysis.states)
          .filter((item) => item.state === stateTab)
          .filter((item) => !stateQuery || item.syllable.includes(stateQuery))
          .sort((a, b) => (a.ply ?? 999) - (b.ply ?? 999) || a.syllable.localeCompare(b.syllable))
          .slice(0, 500)
      : []
  );

  function words(value) {
    return value.split(/[\s,]+/).map((item) => item.trim()).filter(Boolean).slice(0, 1000);
  }

  function rule(excluded = exclusions) {
    return { ...config, excludedWords: words(excluded) };
  }

  async function runAnalysis() {
    loading = true;
    error = '';
    comparison = null;
    try {
      analysis = await academyApi.analyze(rule(), { maxPly, routeGroupLimit: 50, criticalWordLimit: 500 });
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  async function runCompare() {
    loading = true;
    error = '';
    try {
      comparison = await academyApi.compare(rule(), rule(comparedExclusions));
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  async function runStrategy() {
    if (startChar.trim().length !== 1) {
      error = '전략을 탐색할 시작 음절을 한 글자로 입력해 주세요.';
      return;
    }
    loading = true;
    error = '';
    try {
      strategy = await academyApi.strategy(rule(), startChar.trim(), words(exclusions), strategyDepth);
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  function downloadJson() {
    if (!analysis) return;
    const blob = new Blob([JSON.stringify(analysis, null, 2)], { type: 'application/json' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `kkutu-academy-analysis-${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(link.href);
  }
</script>

<div class="grid gap-5 2xl:grid-cols-[360px_minmax(0,1fr)]">
  <aside class="h-fit rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 2xl:sticky 2xl:top-28">
    <div class="flex items-center gap-3">
      <span class="grid h-11 w-11 place-items-center rounded-2xl bg-violet-100 text-violet-700 dark:bg-violet-950/60 dark:text-violet-300"><span class="material-symbols-outlined">account_tree</span></span>
      <div><h2 class="font-black text-slate-900 dark:text-white">전략 엔진</h2><p class="text-xs text-slate-500">승·패·루트와 임계 단어를 계산합니다.</p></div>
    </div>

    <div class="mt-5 grid gap-4">
      <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">n수 이내 승리 깊이
        <div class="flex items-center gap-3"><input type="range" min="1" max="20" bind:value={maxPly} class="min-w-0 flex-1 accent-violet-600" /><strong class="w-10 text-center text-lg text-violet-700 dark:text-violet-300">{maxPly}</strong></div>
      </label>
      <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">제외 단어
        <textarea bind:value={exclusions} rows="6" placeholder="한 줄 또는 쉼표로 구분\n이미 사용한 단어를 붙여넣을 수 있습니다." class="resize-y rounded-2xl border border-slate-200 bg-slate-50 p-3 font-mono text-sm outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white"></textarea>
        <span class="text-right text-xs font-normal text-slate-400">{words(exclusions).length}/1000</span>
      </label>
      <button type="button" onclick={runAnalysis} disabled={loading} class="inline-flex min-h-12 items-center justify-center gap-2 rounded-2xl bg-violet-600 px-5 font-black text-white hover:bg-violet-700 disabled:opacity-50"><span class={`material-symbols-outlined ${loading ? 'animate-spin' : ''}`}>{loading ? 'progress_activity' : 'calculate'}</span>전체 분석</button>
    </div>

    <div class="mt-6 border-t border-slate-100 pt-5 dark:border-slate-800">
      <h3 class="text-sm font-black text-slate-800 dark:text-slate-100">제외 전후 비교</h3>
      <textarea bind:value={comparedExclusions} rows="4" placeholder="비교할 제외 단어 목록" class="mt-2 w-full resize-y rounded-2xl border border-slate-200 bg-slate-50 p-3 font-mono text-sm outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white"></textarea>
      <button type="button" onclick={runCompare} disabled={loading} class="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-xl border border-violet-200 px-4 py-2.5 text-sm font-black text-violet-700 hover:bg-violet-50 disabled:opacity-50 dark:border-violet-900 dark:text-violet-300 dark:hover:bg-violet-950/30"><span class="material-symbols-outlined text-lg">difference</span>상태 변화 비교</button>
    </div>
  </aside>

  <section class="min-w-0">
    {#if error}<p class="mb-4 rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</p>{/if}

    {#if !analysis}
      <div class="grid min-h-[520px] place-items-center rounded-3xl border border-dashed border-slate-300 bg-white/60 p-8 text-center dark:border-slate-700 dark:bg-slate-900/60">
        <div class="max-w-lg">
          <span class="material-symbols-outlined text-6xl text-violet-300 dark:text-violet-800">schema</span>
          <h2 class="mt-4 text-2xl font-black text-slate-800 dark:text-white">공개 사전의 전체 음절 그래프를 분석합니다.</h2>
          <p class="mt-2 text-sm leading-6 text-slate-500">승리·패배·순환 루트, n수 이내 승리 음절, 임계 단어와 대표 필승 수순을 계산합니다. 제외 단어를 넣으면 현재 게임 상태도 근사할 수 있습니다.</p>
          <button type="button" onclick={runAnalysis} class="mt-5 rounded-2xl bg-violet-600 px-6 py-3 font-black text-white">분석 시작</button>
        </div>
      </div>
    {:else}
      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"><span class="text-xs font-bold text-slate-400">공개 단어</span><strong class="mt-1 block text-2xl text-slate-900 dark:text-white">{analysis.corpusSize.toLocaleString()}</strong></div>
        <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"><span class="text-xs font-bold text-slate-400">음절</span><strong class="mt-1 block text-2xl text-slate-900 dark:text-white">{analysis.syllableCount.toLocaleString()}</strong></div>
        <div class="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 dark:border-emerald-900 dark:bg-emerald-950/30"><span class="text-xs font-bold text-emerald-600">승리</span><strong class="mt-1 block text-2xl text-emerald-800 dark:text-emerald-200">{analysis.counts.WIN || 0}</strong></div>
        <div class="rounded-2xl border border-rose-200 bg-rose-50 p-4 dark:border-rose-900 dark:bg-rose-950/30"><span class="text-xs font-bold text-rose-600">패배</span><strong class="mt-1 block text-2xl text-rose-800 dark:text-rose-200">{analysis.counts.LOSS || 0}</strong></div>
        <div class="rounded-2xl border border-sky-200 bg-sky-50 p-4 dark:border-sky-900 dark:bg-sky-950/30"><span class="text-xs font-bold text-sky-600">루트</span><strong class="mt-1 block text-2xl text-sky-800 dark:text-sky-200">{analysis.counts.ROUTE || 0}</strong></div>
      </div>

      <section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div><p class="text-xs font-black text-violet-600">N-PLY MAP</p><h3 class="text-xl font-black text-slate-900 dark:text-white">{maxPly}수 이내 승리 음절</h3></div>
          <span class="rounded-full bg-violet-100 px-3 py-1 text-sm font-black text-violet-700 dark:bg-violet-950 dark:text-violet-300">{analysis.winningWithinPly?.[maxPly]?.length || 0}개</span>
        </div>
        <div class="mt-4 flex max-h-44 flex-wrap gap-2 overflow-y-auto">
          {#each analysis.winningWithinPly?.[maxPly] || [] as syllable}
            <button type="button" onclick={() => { startChar = syllable; runStrategy(); }} class="grid h-10 w-10 place-items-center rounded-xl border border-violet-100 bg-violet-50 font-black text-violet-700 transition hover:border-violet-400 dark:border-violet-900 dark:bg-violet-950/30 dark:text-violet-300">{syllable}</button>
          {/each}
        </div>
      </section>

      <div class="mt-5 grid gap-5 xl:grid-cols-2">
        <section class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h3 class="text-lg font-black text-slate-900 dark:text-white">음절 상태</h3>
            <input bind:value={stateQuery} maxlength="4" placeholder="음절 찾기" class="w-28 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
          </div>
          <div class="mt-3 flex gap-2">
            {#each ['WIN','LOSS','ROUTE'] as state}
              <button type="button" onclick={() => stateTab = state} class={`rounded-full px-3 py-1.5 text-sm font-black ${stateTab === state ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900' : 'bg-slate-100 text-slate-500 dark:bg-slate-800'}`}>{state}</button>
            {/each}
          </div>
          <div class="mt-3 max-h-96 overflow-y-auto rounded-2xl border border-slate-100 dark:border-slate-800">
            {#each stateRows as row}
              <button type="button" onclick={() => { startChar = row.syllable; runStrategy(); }} class="grid w-full grid-cols-[48px_1fr_auto] items-center gap-3 border-b border-slate-100 px-3 py-2 text-left last:border-0 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800/50">
                <strong class="text-xl text-slate-900 dark:text-white">{row.syllable}</strong>
                <span class="truncate text-sm text-slate-500">{row.representativeWord || '대표 수 없음'}</span>
                <span class="text-xs font-bold text-slate-400">{row.ply === null ? '∞' : `${row.ply}수`} · {row.moveCount}개</span>
              </button>
            {/each}
          </div>
        </section>

        <section class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
          <div class="flex items-center justify-between"><h3 class="text-lg font-black text-slate-900 dark:text-white">임계 단어</h3><span class="text-xs text-slate-400">승리 → 패배 상태</span></div>
          <div class="mt-3 max-h-[430px] overflow-y-auto">
            {#each analysis.criticalWords as word}
              <button type="button" onclick={() => { startChar = word.from; runStrategy(); }} class="flex w-full items-center justify-between gap-3 border-b border-slate-100 py-2.5 text-left last:border-0 dark:border-slate-800">
                <div><strong class="text-slate-800 dark:text-white">{word.word}</strong><span class="ml-2 text-xs text-slate-400">{word.from}→{word.to}</span></div>
                <span class="rounded-lg bg-rose-50 px-2 py-1 text-xs font-bold text-rose-600 dark:bg-rose-950/30">방어 {word.defenseCount}</span>
              </button>
            {/each}
          </div>
        </section>
      </div>

      <section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div class="flex flex-wrap items-end gap-3">
          <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">시작 음절<input bind:value={startChar} maxlength="1" class="w-24 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-center text-xl font-black dark:border-slate-700 dark:bg-slate-800 dark:text-white" /></label>
          <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">수순 깊이<input type="number" min="1" max="30" bind:value={strategyDepth} class="w-24 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 dark:border-slate-700 dark:bg-slate-800 dark:text-white" /></label>
          <button type="button" onclick={runStrategy} disabled={loading} class="min-h-11 rounded-xl bg-slate-900 px-5 font-black text-white disabled:opacity-50 dark:bg-white dark:text-slate-900">필승 전략 탐색</button>
          <button type="button" onclick={downloadJson} class="ml-auto inline-flex min-h-11 items-center gap-1 rounded-xl border border-slate-200 px-4 text-sm font-bold text-slate-500 dark:border-slate-700"><span class="material-symbols-outlined text-lg">download</span>분석 JSON</button>
        </div>

        {#if strategy}
          <div class="mt-5">
            <div class="flex items-center gap-2"><span class={`rounded-full px-3 py-1 text-xs font-black ${strategy.state === 'WIN' ? 'bg-emerald-100 text-emerald-700' : strategy.state === 'LOSS' ? 'bg-rose-100 text-rose-700' : 'bg-sky-100 text-sky-700'}`}>{strategy.state || 'UNKNOWN'}</span><strong class="dark:text-white">{strategy.startChar}</strong><span class="text-sm text-slate-400">{strategy.ply === null ? '순환 루트' : `${strategy.ply}수 상태`}</span></div>
            <div class="mt-3 flex flex-wrap items-center gap-2">
              {#each strategy.line as step}
                <div class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-700 dark:bg-slate-800"><span class="block text-[10px] font-bold text-slate-400">{step.turn} · {step.beforeState}</span><strong class="dark:text-white">{step.word}</strong><span class="ml-1 text-xs text-slate-400">→{step.to}</span></div><span class="material-symbols-outlined text-slate-300">arrow_forward</span>
              {/each}
            </div>
          </div>
        {/if}
      </section>

      <section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <h3 class="text-lg font-black text-slate-900 dark:text-white">순환 루트 그룹</h3>
        <div class="mt-3 grid gap-3 lg:grid-cols-2">
          {#each analysis.routeGroups as group, index}
            <article class="rounded-2xl bg-sky-50 p-4 dark:bg-sky-950/25"><div class="flex items-center justify-between"><strong class="text-sky-800 dark:text-sky-200">루트 {index + 1}</strong><span class="text-xs text-sky-600">{group.syllables.length}음절 · {group.edgeCount}단어</span></div><p class="mt-2 break-all font-black text-slate-800 dark:text-white">{group.syllables.join(' · ')}</p><p class="mt-2 text-xs leading-5 text-slate-500">{group.sampleWords.join(' → ')}</p></article>
          {/each}
        </div>
      </section>
    {/if}

    {#if comparison}
      <section class="mt-5 rounded-3xl border border-amber-200 bg-amber-50 p-5 dark:border-amber-900 dark:bg-amber-950/25">
        <div class="flex flex-wrap items-center justify-between gap-3"><div><p class="text-xs font-black text-amber-600">RULE DIFF</p><h3 class="text-xl font-black text-slate-900 dark:text-white">제외 단어 적용 결과</h3></div><span class="rounded-full bg-white px-3 py-1 text-sm font-black text-amber-700 dark:bg-slate-900">{comparison.baseCorpusSize.toLocaleString()} → {comparison.comparedCorpusSize.toLocaleString()}</span></div>
        <div class="mt-4 grid max-h-72 gap-2 overflow-y-auto sm:grid-cols-2 lg:grid-cols-3">
          {#each comparison.changed as item}<div class="flex items-center justify-between rounded-xl bg-white px-3 py-2 text-sm dark:bg-slate-900"><strong class="text-lg dark:text-white">{item.syllable}</strong><span class="font-bold text-slate-500">{item.before || '-'} → {item.after || '-'}</span></div>{/each}
        </div>
      </section>
    {/if}
  </section>
</div>
