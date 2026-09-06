<script>
  import { onMount } from 'svelte';
  import { academyApi, friendlyError } from './api.js';

  let lang = $state('ko');
  let word = $state('');
  let reason = $state('관리자 공개');
  let bulkWords = $state('');
  let bulkReason = $state('관리자 일괄 공개');
  let page = $state(0);
  let response = $state({ items: [], hasNext: false, page: 0, size: 50 });
  let loading = $state(false);
  let error = $state('');
  let notice = $state('');
  let authorized = $state(null);
  let confirmDelete = $state('');

  function parseWords(value) {
    return value.split(/[\s,]+/).map((item) => item.trim()).filter(Boolean);
  }

  async function load(targetPage = 0) {
    loading = true;
    error = '';
    notice = '';
    try {
      response = await academyApi.adminPublished(lang, targetPage, 50);
      page = response.page;
      authorized = true;
    } catch (cause) {
      authorized = false;
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  async function publish(event) {
    event.preventDefault();
    if (!word.trim()) return;
    loading = true;
    error = '';
    notice = '';
    try {
      await academyApi.adminPublish(lang, word.trim(), reason.trim() || '관리자 공개');
      notice = `‘${word.trim()}’을(를) 통합 공개 사전에 추가했습니다.`;
      word = '';
      await load(0);
    } catch (cause) {
      error = friendlyError(cause);
      loading = false;
    }
  }

  async function bulkPublish(event) {
    event.preventDefault();
    const words = [...new Set(parseWords(bulkWords))];
    if (!words.length) return;
    if (words.length > 1000) {
      error = '한 번에 최대 1,000개까지 공개할 수 있습니다.';
      return;
    }
    loading = true;
    error = '';
    notice = '';
    try {
      const result = await academyApi.adminBulkPublish(lang, words, bulkReason.trim() || '관리자 일괄 공개');
      notice = `${result.affected}개 단어를 통합 공개 사전에 추가했습니다.`;
      bulkWords = '';
      await load(0);
    } catch (cause) {
      error = friendlyError(cause);
      loading = false;
    }
  }

  async function unpublish(target) {
    if (confirmDelete !== target) {
      confirmDelete = target;
      return;
    }
    loading = true;
    error = '';
    notice = '';
    try {
      const result = await academyApi.adminUnpublish(lang, target);
      notice = result.success ? `‘${target}’의 공개를 해제했습니다.` : '이미 공개가 해제된 단어입니다.';
      confirmDelete = '';
      await load(page);
    } catch (cause) {
      error = friendlyError(cause);
      loading = false;
    }
  }

  async function refresh() {
    loading = true;
    error = '';
    notice = '';
    try {
      await academyApi.adminRefresh(lang);
      notice = `${lang.toUpperCase()} 공개 코퍼스와 전략 그래프 캐시를 갱신했습니다.`;
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  function changeLang(event) {
    lang = event.currentTarget.value;
    page = 0;
    response = { items: [], hasNext: false, page: 0, size: 50 };
    load(0);
  }

  onMount(() => load(0));
</script>

<svelte:head>
  <title>끄투리오 아카데미 공개 단어 관리</title>
  <meta name="robots" content="noindex,nofollow" />
</svelte:head>

<div class="min-h-screen bg-slate-50 pb-20 pt-24 text-slate-900 dark:bg-slate-950 dark:text-white">
  <header class="border-b border-slate-200 bg-slate-900 text-white dark:border-slate-800">
    <div class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <div class="flex flex-wrap items-end justify-between gap-5">
        <div>
          <div class="inline-flex items-center gap-2 rounded-full bg-violet-500/20 px-3 py-1.5 text-xs font-black text-violet-200">
            <span class="material-symbols-outlined text-base">admin_panel_settings</span>
            WORD ADMIN
          </div>
          <h1 class="mt-4 text-3xl font-black sm:text-4xl">아카데미 공개 단어 관리</h1>
          <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-300">비어인정은 기본 공개됩니다. 이 화면에서는 예외적으로 공개할 어인정 단어만 명시적으로 관리합니다.</p>
        </div>
        <div class="flex gap-2">
          <a href="/academy" class="inline-flex min-h-11 items-center gap-2 rounded-xl border border-white/20 px-4 font-black text-white hover:bg-white/10"><span class="material-symbols-outlined text-lg">school</span>아카데미</a>
          <button type="button" onclick={refresh} disabled={loading || authorized === false} class="inline-flex min-h-11 items-center gap-2 rounded-xl bg-violet-600 px-4 font-black text-white hover:bg-violet-500 disabled:opacity-50"><span class={`material-symbols-outlined text-lg ${loading ? 'animate-spin' : ''}`}>{loading ? 'progress_activity' : 'sync'}</span>캐시 갱신</button>
        </div>
      </div>
    </div>
  </header>

  <main class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
    {#if authorized === false}
      <section class="grid min-h-[420px] place-items-center rounded-3xl border border-rose-200 bg-white p-8 text-center shadow-sm dark:border-rose-900 dark:bg-slate-900">
        <div class="max-w-md">
          <span class="material-symbols-outlined text-6xl text-rose-300 dark:text-rose-800">gpp_bad</span>
          <h2 class="mt-4 text-2xl font-black">접근할 수 없습니다.</h2>
          <p class="mt-2 text-sm leading-6 text-slate-500">통합계정 관리자이면서 단어 관리 권한을 보유해야 합니다.</p>
          {#if error}<p class="mt-4 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700 dark:bg-rose-950/30 dark:text-rose-300">{error}</p>{/if}
        </div>
      </section>
    {:else}
      <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
        <label class="flex items-center gap-3 text-sm font-black text-slate-600 dark:text-slate-300">
          언어
          <select value={lang} onchange={changeLang} class="rounded-xl border border-slate-200 bg-white px-4 py-2.5 outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-900">
            <option value="ko">한국어</option>
            <option value="en">영어</option>
          </select>
        </label>
        <div class="rounded-full bg-white px-4 py-2 text-sm font-bold text-slate-500 shadow-sm dark:bg-slate-900 dark:text-slate-300">명시 공개 {response.items.length}{response.hasNext ? '+' : ''}개 표시</div>
      </div>

      {#if error}<p class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-700 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-300">{error}</p>{/if}
      {#if notice}<p class="mb-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-300">{notice}</p>{/if}

      <div class="grid gap-5 lg:grid-cols-2">
        <section class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 md:p-6">
          <div class="flex items-center gap-3"><span class="grid h-10 w-10 place-items-center rounded-xl bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300"><span class="material-symbols-outlined">add_circle</span></span><div><h2 class="font-black">단일 공개</h2><p class="text-xs text-slate-500">등록된 어인정 단어 한 개를 공개합니다.</p></div></div>
          <form class="mt-5 grid gap-4" onsubmit={publish}>
            <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">단어
              <input bind:value={word} maxlength="128" placeholder="정확한 등록 단어" class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-lg font-black outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
            </label>
            <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">공개 사유
              <input bind:value={reason} maxlength="200" class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
            </label>
            <button type="submit" disabled={loading || !word.trim()} class="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl bg-violet-600 px-5 font-black text-white hover:bg-violet-700 disabled:opacity-50"><span class="material-symbols-outlined">public</span>통합 공개 사전에 추가</button>
          </form>
        </section>

        <section class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 md:p-6">
          <div class="flex items-center gap-3"><span class="grid h-10 w-10 place-items-center rounded-xl bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-300"><span class="material-symbols-outlined">playlist_add</span></span><div><h2 class="font-black">일괄 공개</h2><p class="text-xs text-slate-500">공백·줄바꿈·쉼표로 구분해 최대 1,000개를 등록합니다.</p></div></div>
          <form class="mt-5 grid gap-4" onsubmit={bulkPublish}>
            <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">단어 목록
              <textarea bind:value={bulkWords} rows="5" placeholder="단어1&#10;단어2&#10;단어3" class="resize-y rounded-xl border border-slate-200 bg-slate-50 p-4 font-mono text-sm outline-none focus:border-sky-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white"></textarea>
              <span class="text-right text-xs font-normal text-slate-400">{parseWords(bulkWords).length}/1000</span>
            </label>
            <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">공개 사유
              <input bind:value={bulkReason} maxlength="200" class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 outline-none focus:border-sky-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
            </label>
            <button type="submit" disabled={loading || !parseWords(bulkWords).length} class="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl bg-sky-600 px-5 font-black text-white hover:bg-sky-700 disabled:opacity-50"><span class="material-symbols-outlined">publish</span>일괄 공개</button>
          </form>
        </section>
      </div>

      <section class="mt-5 overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 p-5 dark:border-slate-800 md:px-6">
          <div><p class="text-xs font-black text-violet-600">PUBLIC OVERRIDES</p><h2 class="text-xl font-black">명시 공개 어인정</h2></div>
          <span class="text-sm font-bold text-slate-400">{page + 1}페이지</span>
        </div>
        {#if loading && !response.items.length}
          <div class="grid min-h-64 place-items-center text-slate-400"><span class="material-symbols-outlined animate-spin text-4xl">progress_activity</span></div>
        {:else if !response.items.length}
          <div class="grid min-h-64 place-items-center p-8 text-center"><div><span class="material-symbols-outlined text-5xl text-slate-300 dark:text-slate-700">inventory_2</span><p class="mt-3 font-black text-slate-600 dark:text-slate-300">명시적으로 공개한 어인정 단어가 없습니다.</p></div></div>
        {:else}
          <div class="overflow-x-auto">
            <table class="w-full min-w-[760px] text-left text-sm">
              <thead class="bg-slate-50 text-xs uppercase tracking-wider text-slate-400 dark:bg-slate-800/70"><tr><th class="px-5 py-3">단어</th><th class="px-5 py-3">사유</th><th class="px-5 py-3">처리 관리자</th><th class="px-5 py-3">처리 시각</th><th class="px-5 py-3 text-right">작업</th></tr></thead>
              <tbody>
                {#each response.items as item (item.word)}
                  <tr class="border-t border-slate-100 dark:border-slate-800">
                    <td class="px-5 py-3"><strong class="text-base text-slate-900 dark:text-white">{item.word}</strong></td>
                    <td class="max-w-xs truncate px-5 py-3 text-slate-500" title={item.reason}>{item.reason}</td>
                    <td class="px-5 py-3 font-mono text-xs text-slate-400">{item.createdBy}</td>
                    <td class="px-5 py-3 text-xs text-slate-400">{new Date(item.createdAt).toLocaleString()}</td>
                    <td class="px-5 py-3 text-right"><button type="button" onclick={() => unpublish(item.word)} class={`rounded-lg px-3 py-1.5 text-xs font-black transition ${confirmDelete === item.word ? 'bg-rose-600 text-white' : 'bg-rose-50 text-rose-600 hover:bg-rose-100 dark:bg-rose-950/30 dark:text-rose-300'}`}>{confirmDelete === item.word ? '다시 눌러 확인' : '공개 해제'}</button></td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
          <div class="flex items-center justify-center gap-3 border-t border-slate-100 p-4 dark:border-slate-800">
            <button type="button" disabled={page === 0 || loading} onclick={() => load(page - 1)} class="rounded-xl border border-slate-200 px-4 py-2 font-bold text-slate-500 disabled:opacity-40 dark:border-slate-700">이전</button>
            <button type="button" disabled={!response.hasNext || loading} onclick={() => load(page + 1)} class="rounded-xl border border-slate-200 px-4 py-2 font-bold text-slate-500 disabled:opacity-40 dark:border-slate-700">다음</button>
          </div>
        {/if}
      </section>

      <section class="mt-5 rounded-3xl border border-amber-200 bg-amber-50 p-5 text-sm leading-6 text-amber-900 dark:border-amber-900 dark:bg-amber-950/25 dark:text-amber-200">
        <div class="flex items-center gap-2 font-black"><span class="material-symbols-outlined">warning</span>운영 주의사항</div>
        <ul class="mt-2 list-inside list-disc text-xs leading-5 text-amber-800/80 dark:text-amber-300/80"><li>공개 단어는 검색·시뮬레이터·분석·퀴즈에 즉시 포함됩니다.</li><li>사용 빈도나 등록 경과 시간으로 자동 공개되지 않습니다.</li><li>비어인정은 이 allowlist에 추가할 필요가 없습니다.</li><li>공개 해제 후 캐시도 즉시 무효화됩니다.</li></ul>
      </section>
    {/if}
  </main>
</div>
