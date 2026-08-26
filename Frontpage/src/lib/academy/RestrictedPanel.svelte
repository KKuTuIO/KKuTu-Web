<script>
  import { academyApi, friendlyError } from './api.js';
  import WordCard from './WordCard.svelte';

  let { config, meta = null } = $props();
  let startChar = $state('');
  let mission = $state('');
  let loading = $state(false);
  let error = $state('');
  let response = $state(null);
  let confirmOpen = $state(false);

  let tokenCost = $derived(mission.trim() ? 2 : 1);

  function prepare(event) {
    event.preventDefault();
    if (startChar.trim().length !== 1 || (mission.trim() && mission.trim().length !== 1)) {
      error = '시작 글자와 미션은 각각 한 글자로 입력해 주세요.';
      return;
    }
    error = '';
    confirmOpen = true;
  }

  async function search() {
    confirmOpen = false;
    loading = true;
    error = '';
    response = null;
    try {
      response = await academyApi.restricted(config.lang, startChar.trim(), mission.trim());
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }
</script>

<div class="mx-auto max-w-5xl">
  <section class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
    <div class="bg-gradient-to-br from-slate-900 via-slate-800 to-violet-950 p-6 text-white md:p-8">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div><p class="text-sm font-black text-violet-300">RESTRICTED INJEONG</p><h2 class="mt-1 text-3xl font-black">어인정 제한 조회</h2><p class="mt-2 max-w-2xl text-sm leading-6 text-slate-300">공개되지 않은 어인정 단어는 기존 정책처럼 단어 토큰을 사용해 소량만 확인할 수 있습니다. 조회 결과를 일괄 내려받는 API는 제공하지 않습니다.</p></div>
        <div class="rounded-2xl border border-white/10 bg-white/10 px-5 py-3 text-center backdrop-blur"><span class="block text-xs text-slate-300">일일 최대</span><strong class="text-2xl">{meta?.limits?.restrictedDailyLimit || 25}회</strong></div>
      </div>
    </div>

    <div class="p-5 md:p-8">
      <div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
        <form onsubmit={prepare} class="grid gap-4">
          <div class="grid grid-cols-2 gap-3">
            <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">시작 글자
              <input bind:value={startChar} maxlength="1" placeholder="가" class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-center text-3xl font-black outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
            </label>
            <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">미션 <span class="text-xs font-normal text-slate-400">선택</span>
              <input bind:value={mission} maxlength="1" placeholder="라" class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-center text-3xl font-black outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
            </label>
          </div>
          <button type="submit" disabled={loading || startChar.trim().length !== 1} class="inline-flex min-h-14 items-center justify-center gap-2 rounded-2xl bg-violet-600 px-6 font-black text-white hover:bg-violet-700 disabled:opacity-50"><span class={`material-symbols-outlined ${loading ? 'animate-spin' : ''}`}>{loading ? 'progress_activity' : 'lock_open'}</span>단어 토큰 {tokenCost}개로 조회</button>
        </form>

        <aside class="rounded-2xl bg-amber-50 p-5 text-sm leading-6 text-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
          <div class="flex items-center gap-2 font-black"><span class="material-symbols-outlined">policy</span>악용 방지 정책</div>
          <ul class="mt-2 list-inside list-disc text-xs leading-5 text-amber-800/80 dark:text-amber-300/80">
            <li>통합계정 로그인 필수</li><li>계정·IP 기준 일일 횟수 제한</li><li>최대 {meta?.limits?.restrictedResultLimit || 20}개 결과</li><li>미션 검색은 토큰 2개 사용</li><li>공개 어인정은 일반 사전에서 무료</li>
          </ul>
        </aside>
      </div>

      {#if error}<p class="mt-5 rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</p>{/if}
      {#if response}
        <div class="mt-7 border-t border-slate-100 pt-6 dark:border-slate-800">
          <div class="mb-4 flex flex-wrap items-center justify-between gap-3"><div><p class="text-xs font-black text-violet-600">RESTRICTED RESULT</p><h3 class="text-xl font-black text-slate-900 dark:text-white">조회 결과 {response.items.length}개</h3></div><div class="flex gap-2"><span class="rounded-full bg-violet-100 px-3 py-1 text-xs font-black text-violet-700 dark:bg-violet-950 dark:text-violet-300">토큰 -{response.consumedTokens}</span><span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600 dark:bg-slate-800 dark:text-slate-300">오늘 {response.remainingDailyQueries}회 남음</span></div></div>
          {#if response.items.length}<div class="grid gap-3 md:grid-cols-2">{#each response.items as word}<WordCard {word} themeNames={meta?.themes || {}} compact={true} />{/each}</div>{:else}<p class="rounded-2xl bg-slate-50 p-5 text-center text-sm text-slate-500 dark:bg-slate-800">조건에 맞는 비공개 어인정 단어가 없습니다.</p>{/if}
        </div>
      {/if}
    </div>
  </section>
</div>

{#if confirmOpen}
  <div class="fixed inset-0 z-[100] grid place-items-center bg-slate-950/60 p-4 backdrop-blur-sm">
    <section class="w-full max-w-md rounded-3xl bg-white p-6 shadow-2xl dark:bg-slate-900">
      <span class="grid h-12 w-12 place-items-center rounded-2xl bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300"><span class="material-symbols-outlined">confirmation_number</span></span>
      <h3 class="mt-4 text-xl font-black text-slate-900 dark:text-white">단어 토큰을 사용하시겠습니까?</h3>
      <p class="mt-2 text-sm leading-6 text-slate-500">‘{startChar}’ 시작{mission ? ` · ‘${mission}’ 미션` : ''} 조건으로 비공개 어인정 단어를 조회하며 토큰 {tokenCost}개를 사용합니다.</p>
      <div class="mt-6 grid grid-cols-2 gap-3"><button type="button" onclick={() => confirmOpen = false} class="rounded-xl border border-slate-200 px-4 py-3 font-black text-slate-500 dark:border-slate-700">취소</button><button type="button" onclick={search} class="rounded-xl bg-violet-600 px-4 py-3 font-black text-white">사용하기</button></div>
    </section>
  </div>
{/if}
