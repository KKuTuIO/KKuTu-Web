<script>
  import { academyClient } from './academyClient.js';
  import { friendlyError } from './api.js';

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

  let stateRows = $derived(analysis
    ? Object.values(analysis.states)
        .filter((item) => item.state === stateTab)
        .filter((item) => !stateQuery || item.syllable.includes(stateQuery))
        .sort((a, b) => (a.ply ?? 999) - (b.ply ?? 999) || a.syllable.localeCompare(b.syllable))
        .slice(0, 500)
    : []);

  function words(value) { return value.split(/[\s,]+/).map((item) => item.trim()).filter(Boolean).slice(0, 1000); }
  function stateLabel(state) { return state === 'WIN' ? '승리' : state === 'LOSS' ? '패배' : state === 'ROUTE' ? '루트' : state || '-'; }
  function rule(excluded = exclusions) { return { ...config, excludedWords: words(excluded) }; }

  async function runAnalysis() {
    loading = true; error = ''; comparison = null;
    try { analysis = await academyClient.analyze(rule(), { maxPly, routeGroupLimit: 50, criticalWordLimit: 500 }); }
    catch (cause) { error = friendlyError(cause); }
    finally { loading = false; }
  }

  async function runCompare() {
    loading = true; error = '';
    try { comparison = await academyClient.compare(rule(), rule(comparedExclusions)); }
    catch (cause) { error = friendlyError(cause); }
    finally { loading = false; }
  }

  async function runStrategy() {
    if (startChar.trim().length !== 1) { error = '전략을 탐색할 시작 음절을 한 글자로 입력해 주세요.'; return; }
    loading = true; error = '';
    try { strategy = await academyClient.strategy(rule(), startChar.trim(), words(exclusions), strategyDepth); }
    catch (cause) { error = friendlyError(cause); }
    finally { loading = false; }
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

<div class="grid min-w-0 gap-5 2xl:grid-cols-[360px_minmax(0,1fr)]">
  <aside class="h-fit min-w-0 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 2xl:sticky 2xl:top-28">
    <div class="flex items-center gap-3"><span class="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-violet-100 text-violet-700 dark:bg-violet-950/60 dark:text-violet-300"><span class="material-symbols-outlined">account_tree</span></span><div><h2 class="font-black text-slate-900 dark:text-white">전략 엔진</h2><p class="text-xs text-slate-400">브라우저에서 계산</p></div></div>
    <div class="mt-5 grid min-w-0 gap-4">
      <label class="grid min-w-0 gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">n수 이내 승리 깊이<div class="flex min-w-0 items-center gap-3"><input type="range" min="1" max="20" bind:value={maxPly} class="min-w-0 flex-1 accent-violet-600" /><strong class="w-10 shrink-0 text-center text-lg text-violet-700 dark:text-violet-300">{maxPly}</strong></div></label>
      <label class="grid min-w-0 gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">제외 단어<textarea bind:value={exclusions} rows="6" placeholder="한 줄 또는 쉼표로 구분" class="w-full min-w-0 resize-y rounded-2xl border border-slate-200 bg-slate-50 p-3 font-mono text-sm outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white"></textarea><span class="text-right text-xs font-normal text-slate-400">{words(exclusions).length}/1000</span></label>
      <button type="button" onclick={runAnalysis} disabled={loading} class="inline-flex min-h-12 min-w-0 items-center justify-center gap-2 rounded-2xl bg-violet-600 px-5 font-black text-white hover:bg-violet-700 disabled:opacity-50"><span class={`material-symbols-outlined ${loading ? 'animate-spin' : ''}`}>{loading ? 'progress_activity' : 'calculate'}</span>전체 분석</button>
    </div>
    <div class="mt-6 min-w-0 border-t border-slate-100 pt-5 dark:border-slate-800"><h3 class="text-sm font-black text-slate-800 dark:text-slate-100">제외 전후 비교</h3><textarea bind:value={comparedExclusions} rows="4" placeholder="비교할 제외 단어 목록" class="mt-2 w-full min-w-0 resize-y rounded-2xl border border-slate-200 bg-slate-50 p-3 font-mono text-sm outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white"></textarea><button type="button" onclick={runCompare} disabled={loading} class="mt-2 inline-flex w-full min-w-0 items-center justify-center gap-2 rounded-xl border border-violet-200 px-4 py-2.5 text-sm font-black text-violet-700 hover:bg-violet-50 disabled:opacity-50 dark:border-violet-900 dark:text-violet-300"><span class="material-symbols-outlined text-lg">difference</span>상태 변화 비교</button></div>
  </aside>

  <section class="min-w-0">
    {#if error}<p class="mb-4 break-words rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</p>{/if}
    {#if !analysis}
      <div class="grid min-h-[520px] min-w-0 place-items-center rounded-3xl border border-dashed border-slate-300 bg-white/60 p-8 text-center dark:border-slate-700 dark:bg-slate-900/60"><div><span class="material-symbols-outlined text-6xl text-violet-300 dark:text-violet-800">schema</span><h2 class="mt-4 text-2xl font-black text-slate-800 dark:text-white">전체 음절 그래프 분석</h2><p class="mt-2 text-sm text-slate-400">계산은 현재 기기의 Web Worker에서 실행됩니다.</p><button type="button" onclick={runAnalysis} class="mt-5 rounded-2xl bg-violet-600 px-6 py-3 font-black text-white">분석 시작</button></div></div>
    {:else}
      <div class="grid min-w-0 gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <div class="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><span class="text-xs text-slate-400">단어</span><strong class="mt-1 block text-2xl dark:text-white">{analysis.corpusSize.toLocaleString()}</strong></div>
        <div class="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"><span class="text-xs text-slate-400">음절</span><strong class="mt-1 block text-2xl dark:text-white">{analysis.syllableCount.toLocaleString()}</strong></div>
        <div class="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 dark:border-emerald-900 dark:bg-emerald-950/30"><span class="text-xs text-emerald-600">승리</span><strong class="mt-1 block text-2xl text-emerald-800 dark:text-emerald-200">{analysis.counts.WIN || 0}</strong></div>
        <div class="rounded-2xl border border-rose-200 bg-rose-50 p-4 dark:border-rose-900 dark:bg-rose-950/30"><span class="text-xs text-rose-600">패배</span><strong class="mt-1 block text-2xl text-rose-800 dark:text-rose-200">{analysis.counts.LOSS || 0}</strong></div>
        <div class="rounded-2xl border border-sky-200 bg-sky-50 p-4 dark:border-sky-900 dark:bg-sky-950/30"><span class="text-xs text-sky-600">루트</span><strong class="mt-1 block text-2xl text-sky-800 dark:text-sky-200">{analysis.counts.ROUTE || 0}</strong></div>
      </div>

      <section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900"><div class="flex flex-wrap items-center justify-between gap-3"><h3 class="text-xl font-black dark:text-white">{maxPly}수 이내 승리 음절</h3><div class="flex items-center gap-2"><span class="rounded-full bg-violet-100 px-3 py-1 text-sm font-black text-violet-700 dark:bg-violet-950 dark:text-violet-300">{analysis.winningWithinPly?.[maxPly]?.length || 0}개</span><button type="button" onclick={downloadJson} class="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-bold text-slate-500 dark:border-slate-700">JSON</button></div></div><div class="mt-4 flex max-h-44 flex-wrap gap-2 overflow-y-auto">{#each analysis.winningWithinPly?.[maxPly] || [] as syllable}<button type="button" onclick={() => { startChar = syllable; runStrategy(); }} class="grid h-10 w-10 place-items-center rounded-xl border border-violet-100 bg-violet-50 font-black text-violet-700 dark:border-violet-900 dark:bg-violet-950/30 dark:text-violet-300">{syllable}</button>{/each}</div></section>

      <div class="mt-5 grid min-w-0 gap-5 xl:grid-cols-2">
        <section class="rounded-3xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900"><div class="flex flex-wrap items-center justify-between gap-3"><h3 class="text-lg font-black dark:text-white">음절 상태</h3><input bind:value={stateQuery} maxlength="4" placeholder="음절 찾기" class="w-28 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-800 dark:text-white" /></div><div class="mt-3 flex gap-2">{#each ['WIN','LOSS','ROUTE'] as state}<button type="button" onclick={() => stateTab = state} class={`rounded-full px-3 py-1.5 text-sm font-black ${stateTab === state ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-900' : 'bg-slate-100 text-slate-500 dark:bg-slate-800'}`}>{stateLabel(state)}</button>{/each}</div><div class="mt-3 max-h-96 overflow-y-auto rounded-2xl border border-slate-100 dark:border-slate-800">{#each stateRows as row}<button type="button" onclick={() => { startChar = row.syllable; runStrategy(); }} class="grid w-full grid-cols-[48px_minmax(0,1fr)_auto] items-center gap-3 border-b border-slate-100 px-3 py-2 text-left last:border-0 dark:border-slate-800"><strong class="text-xl dark:text-white">{row.syllable}</strong><span class="truncate text-sm text-slate-500">{row.representativeWord || '대표 수 없음'}</span><span class="text-xs text-slate-400">{row.ply === null ? '∞' : `${row.ply}수`} · {row.moveCount}개</span></button>{/each}</div></section>
        <section class="rounded-3xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900"><h3 class="text-lg font-black dark:text-white">임계 단어</h3><div class="mt-3 max-h-[430px] overflow-y-auto">{#each analysis.criticalWords as word}<button type="button" onclick={() => { startChar = word.from; runStrategy(); }} class="flex w-full items-center justify-between gap-3 border-b border-slate-100 py-2.5 text-left last:border-0 dark:border-slate-800"><div class="min-w-0"><strong class="break-all dark:text-white">{word.word}</strong><span class="ml-2 text-xs text-slate-400">{word.from}→{word.to}</span></div><span class="shrink-0 rounded-lg bg-rose-50 px-2 py-1 text-xs font-bold text-rose-600 dark:bg-rose-950/30">방어 {word.defenseCount}</span></button>{/each}</div></section>
      </div>

      <section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900"><div class="flex flex-wrap items-end gap-3"><label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">시작 음절<input bind:value={startChar} maxlength="1" class="w-24 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-center text-xl font-black dark:border-slate-700 dark:bg-slate-800 dark:text-white" /></label><label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">수순 깊이<input type="number" min="1" max="30" bind:value={strategyDepth} class="w-24 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 dark:border-slate-700 dark:bg-slate-800 dark:text-white" /></label><button type="button" onclick={runStrategy} disabled={loading} class="min-h-11 rounded-xl bg-slate-900 px-5 font-black text-white dark:bg-white dark:text-slate-900">전략 탐색</button></div>{#if strategy}<div class="mt-4 flex flex-wrap gap-2">{#each strategy.line as step}<div class="rounded-xl bg-slate-50 px-3 py-2 dark:bg-slate-800"><span class="text-xs text-slate-400">{step.turn}수 · {step.from}→{step.to}</span><strong class="ml-2 dark:text-white">{step.word}</strong><span class="ml-2 text-xs text-slate-400">방어 {step.defenseCount}</span></div>{/each}</div>{/if}</section>

      {#if analysis.routeGroups?.length}<section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900"><h3 class="text-lg font-black dark:text-white">루트 그룹</h3><div class="mt-3 grid gap-3 md:grid-cols-2">{#each analysis.routeGroups.slice(0, 20) as group}<div class="rounded-2xl bg-slate-50 p-4 dark:bg-slate-800"><div class="flex flex-wrap gap-1">{#each group.syllables.slice(0, 30) as syllable}<span class="rounded-lg bg-white px-2 py-1 text-sm font-black dark:bg-slate-900">{syllable}</span>{/each}</div><p class="mt-2 text-xs text-slate-400">간선 {group.edgeCount}개 · {group.sampleWords.slice(0, 5).join(', ')}</p></div>{/each}</div></section>{/if}

      {#if comparison}<section class="mt-5 rounded-3xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900"><h3 class="text-lg font-black dark:text-white">상태 변화 {comparison.changed.length}개</h3><div class="mt-3 max-h-72 overflow-y-auto">{#each comparison.changed.slice(0, 500) as item}<div class="grid grid-cols-[52px_1fr] gap-3 border-b border-slate-100 py-2 text-sm dark:border-slate-800"><strong class="text-xl dark:text-white">{item.syllable}</strong><span class="text-slate-500">{stateLabel(item.before)} {item.beforePly ?? '∞'} → {stateLabel(item.after)} {item.afterPly ?? '∞'}</span></div>{/each}</div></section>{/if}
    {/if}
  </section>
</div>
