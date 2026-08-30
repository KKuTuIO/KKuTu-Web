<script>
  import { academyApi, friendlyError } from './api.js';
  import WordCard from './WordCard.svelte';

  let { config, meta = null } = $props();
  let position = $state('START');
  let edgeChar = $state('');
  let mission = $state('');
  let loading = $state(false);
  let error = $state('');
  let response = $state(null);
  let confirmOpen = $state(false);

  let tokenCost = $derived(mission.trim() ? 2 : 1);
  let positionLabel = $derived(position === 'END' ? '끝 글자' : '시작 글자');

  function prepare(event) {
    event.preventDefault();
    if (edgeChar.trim().length !== 1 || (mission.trim() && mission.trim().length !== 1)) {
      error = `${positionLabel}와 미션은 각각 한 글자로 입력해 주세요.`;
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
      response = await academyApi.restricted(config.lang, position, edgeChar.trim(), mission.trim());
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }
</script>

<div class="mx-auto max-w-3xl">
  <section class="min-w-0 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 md:p-7">
    <div class="flex min-w-0 items-center gap-3">
      <span class="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-violet-100 text-violet-700 dark:bg-violet-950/60 dark:text-violet-300">
        <span class="material-symbols-outlined">lock_open</span>
      </span>
      <h2 class="min-w-0 text-2xl font-black text-slate-900 dark:text-white">어인정 조회</h2>
    </div>

    <form onsubmit={prepare} class="mt-6 grid min-w-0 gap-4">
      <div class="grid min-w-0 grid-cols-2 gap-2 rounded-2xl bg-slate-100 p-1 dark:bg-slate-800">
        <button type="button" onclick={() => position = 'START'} class={`rounded-xl px-4 py-2.5 text-sm font-black transition ${position === 'START' ? 'bg-white text-violet-700 shadow-sm dark:bg-slate-700 dark:text-violet-300' : 'text-slate-500 dark:text-slate-400'}`}>시작하는 단어</button>
        <button type="button" onclick={() => position = 'END'} class={`rounded-xl px-4 py-2.5 text-sm font-black transition ${position === 'END' ? 'bg-white text-violet-700 shadow-sm dark:bg-slate-700 dark:text-violet-300' : 'text-slate-500 dark:text-slate-400'}`}>끝나는 단어</button>
      </div>

      <div class="grid min-w-0 grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="grid min-w-0 gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">
          {positionLabel}
          <input bind:value={edgeChar} maxlength="1" placeholder={position === 'END' ? '가' : '가'} class="w-full min-w-0 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-center text-3xl font-black outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
        </label>
        <label class="grid min-w-0 gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">
          <span>미션 <span class="text-xs font-normal text-slate-400">선택</span></span>
          <input bind:value={mission} maxlength="1" placeholder="라" class="w-full min-w-0 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-center text-3xl font-black outline-none focus:border-violet-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
        </label>
      </div>
      <button type="submit" disabled={loading || edgeChar.trim().length !== 1} class="inline-flex min-h-14 min-w-0 items-center justify-center gap-2 rounded-2xl bg-violet-600 px-6 font-black text-white hover:bg-violet-700 disabled:opacity-50">
        <span class={`material-symbols-outlined ${loading ? 'animate-spin' : ''}`}>{loading ? 'progress_activity' : 'confirmation_number'}</span>
        단어 토큰 {tokenCost}개로 조회
      </button>
    </form>

    {#if error}
      <p class="mt-5 break-words rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</p>
    {/if}

    {#if response}
      <div class="mt-7 border-t border-slate-100 pt-6 dark:border-slate-800">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-xl font-black text-slate-900 dark:text-white">조회 결과 {response.items.length}개</h3>
          <div class="flex flex-wrap gap-2">
            <span class="rounded-full bg-violet-100 px-3 py-1 text-xs font-black text-violet-700 dark:bg-violet-950 dark:text-violet-300">토큰 -{response.consumedTokens}</span>
            <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600 dark:bg-slate-800 dark:text-slate-300">오늘 {response.remainingDailyQueries}회 남음</span>
          </div>
        </div>
        {#if response.items.length}
          <div class="grid min-w-0 gap-3 md:grid-cols-2">
            {#each response.items as word}
              <WordCard {word} themeNames={meta?.themes || {}} compact={true} />
            {/each}
          </div>
        {:else}
          <p class="rounded-2xl bg-slate-50 p-5 text-center text-sm text-slate-500 dark:bg-slate-800">조건에 맞는 어인정 단어가 없습니다.</p>
        {/if}
      </div>
    {/if}
  </section>
</div>

{#if confirmOpen}
  <div class="fixed inset-0 z-[100] grid place-items-center bg-slate-950/60 p-4 backdrop-blur-sm">
    <section class="w-full max-w-md rounded-3xl bg-white p-6 shadow-2xl dark:bg-slate-900">
      <span class="grid h-12 w-12 place-items-center rounded-2xl bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300"><span class="material-symbols-outlined">confirmation_number</span></span>
      <h3 class="mt-4 text-xl font-black text-slate-900 dark:text-white">단어 토큰을 사용하시겠습니까?</h3>
      <p class="mt-2 text-sm leading-6 text-slate-500">‘{edgeChar}’로 {position === 'END' ? '끝나는' : '시작하는'} 단어{mission ? ` · ‘${mission}’ 미션` : ''}을 조회하며 토큰 {tokenCost}개를 사용합니다.</p>
      <div class="mt-6 grid grid-cols-2 gap-3">
        <button type="button" onclick={() => confirmOpen = false} class="rounded-xl border border-slate-200 px-4 py-3 font-black text-slate-500 dark:border-slate-700">취소</button>
        <button type="button" onclick={search} class="rounded-xl bg-violet-600 px-4 py-3 font-black text-white">사용하기</button>
      </div>
    </section>
  </div>
{/if}
