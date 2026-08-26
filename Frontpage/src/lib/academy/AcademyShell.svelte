<script>
  import { onMount } from 'svelte';
  import { academyApi, friendlyError } from './api.js';
  import RuleBar from './RuleBar.svelte';
  import DictionaryPanel from './DictionaryPanel.svelte';
  import SimulatorPanel from './SimulatorPanel.svelte';
  import PracticePanel from './PracticePanel.svelte';
  import EnginePanel from './EnginePanel.svelte';
  import QuizPanel from './QuizPanel.svelte';
  import ReplayPanel from './ReplayPanel.svelte';
  import RestrictedPanel from './RestrictedPanel.svelte';

  const tabs = [
    { id: 'dictionary', label: '단어 찾기', icon: 'dictionary', description: '공개 사전 검색' },
    { id: 'simulator', label: '수순 실험', icon: 'conversion_path', description: '끝말잇기 시뮬레이터' },
    { id: 'practice', label: '루트 대결', icon: 'swords', description: '봇과 실전 연습' },
    { id: 'engine', label: '전략 엔진', icon: 'account_tree', description: '승패·루트 분석' },
    { id: 'quiz', label: '오늘의 퀴즈', icon: 'quiz', description: '매일 5문제' },
    { id: 'replay', label: '게임 복기', icon: 'movie_edit', description: '리플레이 학습' },
    { id: 'restricted', label: '어인정 조회', icon: 'lock', description: '토큰 제한 조회' }
  ];

  let active = $state('dictionary');
  let meta = $state(null);
  let metaError = $state('');
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

  let currentTab = $derived(tabs.find((tab) => tab.id === active) || tabs[0]);
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
    } catch (error) {
      metaError = friendlyError(error);
    }
  });
</script>

<svelte:head>
  <title>끄투리오 아카데미 - 단어 학습과 루트 분석</title>
  <meta name="description" content="끄투리오 공개 단어 사전, 끝말잇기 시뮬레이터, 루트전 연습과 전략 분석을 제공합니다." />
</svelte:head>

<div class="min-h-screen bg-slate-50 pb-20 pt-24 text-slate-900 dark:bg-slate-950 dark:text-white">
  <header class="relative overflow-hidden border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
    <div class="pointer-events-none absolute inset-0 opacity-60 dark:opacity-30">
      <div class="absolute -left-24 -top-36 h-96 w-96 rounded-full bg-emerald-200 blur-3xl dark:bg-emerald-900"></div>
      <div class="absolute right-0 top-0 h-80 w-80 rounded-full bg-violet-200 blur-3xl dark:bg-violet-900"></div>
    </div>
    <div class="relative mx-auto max-w-[1500px] px-4 py-10 sm:px-6 lg:px-8 lg:py-14">
      <div class="flex flex-col gap-8 xl:flex-row xl:items-end xl:justify-between">
        <div class="max-w-3xl">
          <div class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white/70 px-3 py-1.5 text-xs font-black text-emerald-700 shadow-sm backdrop-blur dark:border-emerald-900 dark:bg-slate-900/70 dark:text-emerald-300">
            <span class="material-symbols-outlined text-base">school</span>
            KKuTuIO WORD ACADEMY
          </div>
          <h1 class="mt-5 text-4xl font-black tracking-tight text-slate-950 dark:text-white sm:text-5xl lg:text-6xl">
            단어를 찾는 곳에서<br /><span class="bg-gradient-to-r from-emerald-600 to-violet-600 bg-clip-text text-transparent">수읽기를 배우는 곳으로.</span>
          </h1>
          <p class="mt-5 max-w-2xl text-base leading-7 text-slate-600 dark:text-slate-300">비어인정과 운영진 공개 단어를 바탕으로 검색, 수순 실험, 루트 대결, 승패 분석과 내 게임 복기를 한곳에서 제공합니다.</p>
        </div>
        <div class="grid grid-cols-3 gap-3 sm:min-w-[430px]">
          <div class="rounded-2xl border border-slate-200 bg-white/80 p-4 text-center shadow-sm backdrop-blur dark:border-slate-700 dark:bg-slate-900/80"><span class="material-symbols-outlined text-2xl text-emerald-600">verified_user</span><strong class="mt-1 block text-sm">안전한 공개 범위</strong><span class="text-xs text-slate-400">마스터 DB 분리</span></div>
          <div class="rounded-2xl border border-slate-200 bg-white/80 p-4 text-center shadow-sm backdrop-blur dark:border-slate-700 dark:bg-slate-900/80"><span class="material-symbols-outlined text-2xl text-violet-600">neurology</span><strong class="mt-1 block text-sm">전략 그래프</strong><span class="text-xs text-slate-400">승·패·루트 분석</span></div>
          <div class="rounded-2xl border border-slate-200 bg-white/80 p-4 text-center shadow-sm backdrop-blur dark:border-slate-700 dark:bg-slate-900/80"><span class="material-symbols-outlined text-2xl text-sky-600">sports_esports</span><strong class="mt-1 block text-sm">실전 연동</strong><span class="text-xs text-slate-400">리플레이 복기</span></div>
        </div>
      </div>
    </div>
  </header>

  <nav class="sticky top-16 z-40 border-b border-slate-200 bg-slate-50/90 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-950/90" aria-label="아카데미 기능">
    <div class="mx-auto max-w-[1500px] overflow-x-auto px-4 sm:px-6 lg:px-8">
      <div class="flex min-w-max gap-1 py-3">
        {#each tabs as tab}
          <button type="button" onclick={() => selectTab(tab.id)} class={`group flex items-center gap-2 rounded-2xl px-4 py-2.5 text-left transition ${active === tab.id ? 'bg-slate-900 text-white shadow-lg dark:bg-white dark:text-slate-900' : 'text-slate-500 hover:bg-white hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-white'}`} aria-current={active === tab.id ? 'page' : undefined}>
            <span class="material-symbols-outlined text-xl">{tab.icon}</span>
            <span><strong class="block text-sm">{tab.label}</strong><span class={`hidden text-[10px] sm:block ${active === tab.id ? 'opacity-60' : 'text-slate-400'}`}>{tab.description}</span></span>
          </button>
        {/each}
      </div>
    </div>
  </nav>

  <main class="mx-auto max-w-[1500px] px-4 py-6 sm:px-6 lg:px-8">
    {#if metaError}
      <div class="mb-5 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-semibold text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200">메타데이터 일부를 불러오지 못했습니다. 기본 설정으로 계속 이용할 수 있습니다. {metaError}</div>
    {/if}

    <div class="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div><p class="text-xs font-black uppercase tracking-widest text-emerald-600 dark:text-emerald-400">{currentTab.description}</p><h2 class="mt-1 text-2xl font-black text-slate-900 dark:text-white">{currentTab.label}</h2></div>
      <div class="inline-flex items-center gap-2 rounded-full bg-white px-3 py-1.5 text-xs font-bold text-slate-500 shadow-sm dark:bg-slate-900 dark:text-slate-300"><span class="h-2 w-2 rounded-full bg-emerald-500"></span>{config.dictionary === 'COMBINED' ? '통합 공개 사전' : config.dictionary === 'STANDARD' ? '표준 비어인정 사전' : '기초 사전'}</div>
    </div>

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
