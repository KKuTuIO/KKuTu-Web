<script>
  import { onMount } from 'svelte';
  import { academyApi, friendlyError } from './api.js';
  import WordCard from './WordCard.svelte';

  let { config, meta = null } = $props();
  let text = $state('');
  let match = $state('CONTAINS');
  let start = $state('');
  let end = $state('');
  let mission = $state('');
  let sort = $state('HIT_DESC');
  let page = $state(0);
  let loading = $state(false);
  let error = $state('');
  let result = $state({ items: [], hasNext: false, page: 0, size: 30 });

  async function search(targetPage = 0) {
    loading = true;
    error = '';
    try {
      result = await academyApi.search(config, {
        text,
        match,
        start,
        end,
        mission,
        sort,
        page: targetPage,
        size: 30
      });
      page = result.page;
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  function submit(event) {
    event.preventDefault();
    search(0);
  }

  function clearFilters() {
    text = '';
    start = '';
    end = '';
    mission = '';
    match = 'CONTAINS';
    sort = 'HIT_DESC';
    search(0);
  }

  onMount(() => search(0));
</script>

<div class="grid gap-5 xl:grid-cols-[340px_minmax(0,1fr)]">
  <aside class="h-fit rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 xl:sticky xl:top-28">
    <div class="flex items-center gap-3">
      <span class="grid h-11 w-11 place-items-center rounded-2xl bg-emerald-100 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300">
        <span class="material-symbols-outlined">dictionary</span>
      </span>
      <div>
        <h2 class="text-lg font-black text-slate-900 dark:text-white">단어 찾기</h2>
        <p class="text-xs text-slate-500 dark:text-slate-400">공개 학습 사전에서 전략 단어를 찾습니다.</p>
      </div>
    </div>

    <form class="mt-5 grid gap-4" onsubmit={submit}>
      <label class="grid gap-1.5 text-sm font-bold text-slate-600 dark:text-slate-300">
        검색어
        <div class="flex overflow-hidden rounded-xl border border-slate-200 bg-slate-50 focus-within:border-emerald-500 dark:border-slate-700 dark:bg-slate-800">
          <select bind:value={match} aria-label="검색 방식" class="border-r border-slate-200 bg-transparent px-2 text-xs font-bold outline-none dark:border-slate-700">
            <option value="CONTAINS">포함</option>
            <option value="EXACT">일치</option>
            <option value="STARTS_WITH">시작</option>
            <option value="ENDS_WITH">끝</option>
          </select>
          <input bind:value={text} maxlength="100" placeholder="단어 입력" class="min-w-0 flex-1 bg-transparent px-3 py-2.5 outline-none dark:text-white" />
        </div>
      </label>

      <div class="grid grid-cols-3 gap-2">
        <label class="grid gap-1 text-xs font-bold text-slate-500">
          시작
          <input bind:value={start} maxlength="1" class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-center text-base font-black outline-none focus:border-emerald-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
        </label>
        <label class="grid gap-1 text-xs font-bold text-slate-500">
          끝
          <input bind:value={end} maxlength="1" class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-center text-base font-black outline-none focus:border-emerald-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
        </label>
        <label class="grid gap-1 text-xs font-bold text-slate-500">
          미션
          <input bind:value={mission} maxlength="1" class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-center text-base font-black outline-none focus:border-emerald-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
        </label>
      </div>

      <label class="grid gap-1.5 text-sm font-bold text-slate-600 dark:text-slate-300">
        정렬
        <select bind:value={sort} class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-white">
          <option value="HIT_DESC">게임 사용 빈도순</option>
          <option value="LENGTH_DESC">긴 단어순</option>
          <option value="LENGTH_ASC">짧은 단어순</option>
          <option value="WORD_ASC">가나다순</option>
          <option value="WORD_DESC">가나다 역순</option>
        </select>
      </label>

      <div class="grid grid-cols-[1fr_auto] gap-2">
        <button type="submit" disabled={loading} class="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 font-black text-white transition hover:bg-emerald-700 disabled:opacity-50">
          <span class={`material-symbols-outlined ${loading ? 'animate-spin' : ''}`}>{loading ? 'progress_activity' : 'search'}</span>
          검색
        </button>
        <button type="button" onclick={clearFilters} class="grid h-11 w-11 place-items-center rounded-xl border border-slate-200 text-slate-500 transition hover:bg-slate-100 dark:border-slate-700 dark:hover:bg-slate-800" aria-label="검색 조건 초기화">
          <span class="material-symbols-outlined">restart_alt</span>
        </button>
      </div>
    </form>

    <div class="mt-5 rounded-2xl bg-slate-50 p-4 text-xs leading-5 text-slate-500 dark:bg-slate-800/70 dark:text-slate-400">
      <strong class="text-slate-700 dark:text-slate-200">공개 범위</strong><br />
      비어인정 단어와 운영진이 별도로 공개한 어인정만 검색됩니다. 비공개 어인정은 제한 조회 탭에서 토큰으로 확인할 수 있습니다.
    </div>
  </aside>

  <section class="min-w-0">
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-sm font-bold text-emerald-600 dark:text-emerald-400">검색 결과</p>
        <h2 class="text-2xl font-black text-slate-900 dark:text-white">{result.items.length ? `${result.items.length}개 단어` : '조건을 설정해 보세요'}</h2>
      </div>
      <div class="flex items-center gap-2 text-sm text-slate-500">
        <span class="rounded-full bg-white px-3 py-1.5 shadow-sm dark:bg-slate-900">{page + 1}페이지</span>
      </div>
    </div>

    {#if error}
      <div class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-700 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300">{error}</div>
    {/if}

    {#if loading && result.items.length === 0}
      <div class="grid min-h-80 place-items-center rounded-3xl border border-dashed border-slate-300 bg-white/60 dark:border-slate-700 dark:bg-slate-900/60">
        <div class="text-center text-slate-500">
          <span class="material-symbols-outlined animate-spin text-4xl">progress_activity</span>
          <p class="mt-2 font-bold">공개 사전을 분석하고 있습니다.</p>
        </div>
      </div>
    {:else if result.items.length === 0}
      <div class="grid min-h-80 place-items-center rounded-3xl border border-dashed border-slate-300 bg-white/60 p-8 text-center dark:border-slate-700 dark:bg-slate-900/60">
        <div>
          <span class="material-symbols-outlined text-5xl text-slate-300 dark:text-slate-600">manage_search</span>
          <h3 class="mt-3 text-lg font-black text-slate-700 dark:text-slate-200">공개 사전에서 찾지 못했습니다.</h3>
          <p class="mt-1 text-sm text-slate-500">검색 조건을 줄이거나 제한 어인정 조회를 이용해 주세요.</p>
        </div>
      </div>
    {:else}
      <div class="grid gap-3 lg:grid-cols-2">
        {#each result.items as word (word.word)}
          <WordCard {word} themeNames={meta?.themes || {}} />
        {/each}
      </div>

      <div class="mt-5 flex items-center justify-center gap-3">
        <button type="button" disabled={page === 0 || loading} onclick={() => search(page - 1)} class="inline-flex items-center gap-1 rounded-xl border border-slate-200 bg-white px-4 py-2 font-bold text-slate-600 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
          <span class="material-symbols-outlined text-lg">chevron_left</span> 이전
        </button>
        <button type="button" disabled={!result.hasNext || loading} onclick={() => search(page + 1)} class="inline-flex items-center gap-1 rounded-xl border border-slate-200 bg-white px-4 py-2 font-bold text-slate-600 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
          다음 <span class="material-symbols-outlined text-lg">chevron_right</span>
        </button>
      </div>
    {/if}
  </section>
</div>
