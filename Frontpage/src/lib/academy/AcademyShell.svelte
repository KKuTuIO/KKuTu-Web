<script>
  import { onMount } from 'svelte';
  import { academyApi } from './api.js';
  import RuleBar from './RuleBar.svelte';
  import DictionaryPanel from './DictionaryPanel.svelte';
  import SimulatorPanel from './SimulatorPanel.svelte';
  import PracticePanel from './PracticePanel.svelte';
  import EnginePanel from './EnginePanel.svelte';
  import QuizPanel from './QuizPanel.svelte';
  import ReplayPanel from './ReplayPanel.svelte';
  import RestrictedPanel from './RestrictedPanel.svelte';

  const tabs = [
    { id: 'dictionary', label: '단어 찾기', icon: 'dictionary' },
    { id: 'simulator', label: '수순 실험', icon: 'conversion_path' },
    { id: 'practice', label: '루트 대결', icon: 'swords' },
    { id: 'engine', label: '전략 엔진', icon: 'account_tree' },
    { id: 'quiz', label: '오늘의 퀴즈', icon: 'quiz' },
    { id: 'replay', label: '게임 복기', icon: 'movie_edit' },
    { id: 'restricted', label: '어인정 조회', icon: 'lock' }
  ];

  let active = $state('dictionary');
  let meta = $state(null);
  let metaError = $state(false);
  let config = $state({
    lang: 'ko',
    dictionary: 'COMBINED',
    direction: 'FORWARD',
    duum: true,
    minLength: 2,
    maxLength: 64,
    includeLoanword: true,
    includeSpaced: true,
    includeDialect: true,
    includeOld: true,
    includeCultural: true,
    includeKung: true,
    themes: [],
    excludedThemes: [],
    excludedWords: []
  });

  let showRules = $derived(!['quiz', 'replay', 'restricted'].includes(active));

  function selectTab(id) {
    active = id;
    const url = new URL(window.location.href);
    url.searchParams.set('tab', id);
    history.replaceState(null, '', url);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  onMount(async () => {
    const requested = new URLSearchParams(window.location.search).get('tab');
    if (tabs.some((tab) => tab.id === requested)) active = requested;
    try {
      meta = await academyApi.meta();
    } catch (_) {
      metaError = true;
    }
  });
</script>

<svelte:head>
  <title>끄투리오 아카데미 - 단어 학습과 루트 분석</title>
  <meta name="description" content="끄투리오 단어 검색, 끝말잇기 시뮬레이터, 루트전 연습과 전략 분석을 제공합니다." />
</svelte:head>

<div class="min-h-screen bg-slate-50 pb-20 pt-16 text-slate-900 dark:bg-slate-950 dark:text-white">
  <nav class="sticky top-16 z-40 border-b border-slate-200 bg-slate-50/90 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-950/90" aria-label="아카데미 기능">
    <div class="mx-auto max-w-[1500px] overflow-x-auto px-4 sm:px-6 lg:px-8">
      <div class="flex min-w-max gap-1 py-3">
        {#each tabs as tab}
          <button type="button" onclick={() => selectTab(tab.id)} class={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-black transition ${active === tab.id ? 'bg-slate-900 text-white shadow-sm dark:bg-white dark:text-slate-900' : 'text-slate-500 hover:bg-white hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-white'}`} aria-current={active === tab.id ? 'page' : undefined}>
            <span class="material-symbols-outlined text-xl">{tab.icon}</span>
            {tab.label}
          </button>
        {/each}
      </div>
    </div>
  </nav>

  <main class="mx-auto max-w-[1500px] px-4 py-6 sm:px-6 lg:px-8">
    {#if metaError}
      <div class="mb-5 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-semibold text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200">설정을 불러오지 못했습니다. 기본 설정으로 계속 이용할 수 있습니다.</div>
    {/if}

    {#if showRules}
      <div class="mb-5"><RuleBar bind:config {meta} /></div>
    {/if}

    {#if active === 'dictionary'}
      <DictionaryPanel {config} {meta} />
    {:else if active === 'simulator'}
      <SimulatorPanel {config} />
    {:else if active === 'practice'}
      <PracticePanel {config} />
    {:else if active === 'engine'}
      <EnginePanel {config} />
    {:else if active === 'quiz'}
      <QuizPanel />
    {:else if active === 'replay'}
      <ReplayPanel {config} />
    {:else if active === 'restricted'}
      <RestrictedPanel {config} {meta} />
    {/if}
  </main>
</div>
