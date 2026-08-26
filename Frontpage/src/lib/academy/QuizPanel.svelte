<script>
  import { onMount } from 'svelte';
  import { academyApi, friendlyError } from './api.js';

  const total = 5;
  let index = $state(0);
  let question = $state(null);
  let selected = $state('');
  let result = $state(null);
  let score = $state(0);
  let answered = $state(0);
  let loading = $state(false);
  let error = $state('');
  let streak = $state(0);

  async function load(target = index) {
    loading = true;
    error = '';
    result = null;
    selected = '';
    try {
      question = await academyApi.quiz(target);
      index = target;
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  async function answer(option) {
    if (!question || result || loading) return;
    selected = option;
    loading = true;
    try {
      result = await academyApi.answerQuiz(question.questionId, option);
      answered += 1;
      if (result.correct) score += 1;
      saveProgress();
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  function next() {
    if (index + 1 < total) load(index + 1);
    else finishDay();
  }

  function finishDay() {
    const today = new Date().toISOString().slice(0, 10);
    const previous = JSON.parse(localStorage.getItem('kkutu-academy-quiz') || '{}');
    const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
    streak = previous.date === yesterday ? (previous.streak || 0) + 1 : previous.date === today ? previous.streak || 1 : 1;
    localStorage.setItem('kkutu-academy-quiz', JSON.stringify({ date: today, streak, score, total }));
    question = null;
  }

  function saveProgress() {
    sessionStorage.setItem('kkutu-academy-quiz-progress', JSON.stringify({ index, score, answered }));
  }

  function restart() {
    index = 0;
    score = 0;
    answered = 0;
    load(0);
  }

  onMount(() => {
    const saved = JSON.parse(localStorage.getItem('kkutu-academy-quiz') || '{}');
    streak = saved.streak || 0;
    load(0);
  });
</script>

<section class="min-w-0 overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
  <div class="bg-gradient-to-r from-amber-400 via-orange-400 to-rose-400 p-6 text-white md:p-8">
    <div class="flex flex-wrap items-center justify-between gap-5">
      <h2 class="text-3xl font-black">오늘의 낱말 퀴즈</h2>
      <div class="flex flex-wrap gap-3">
        <div class="rounded-2xl bg-white/20 px-5 py-3 text-center backdrop-blur"><span class="block text-xs">오늘 점수</span><strong class="text-2xl">{score}/{total}</strong></div>
        <div class="rounded-2xl bg-white/20 px-5 py-3 text-center backdrop-blur"><span class="block text-xs">연속 학습</span><strong class="text-2xl">{streak}일</strong></div>
      </div>
    </div>
  </div>

  <div class="p-5 md:p-8">
    <div class="mb-6 flex gap-2" aria-label="퀴즈 진행률">
      {#each Array(total) as _, step}
        <div class={`h-2 flex-1 rounded-full ${step < answered ? 'bg-emerald-500' : step === index && question ? 'bg-amber-400' : 'bg-slate-100 dark:bg-slate-800'}`}></div>
      {/each}
    </div>

    {#if error}<p class="mb-4 break-words rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</p>{/if}

    {#if loading && !question}
      <div class="grid min-h-96 place-items-center text-slate-400"><span class="material-symbols-outlined animate-spin text-5xl">progress_activity</span></div>
    {:else if question}
      <div class="mx-auto min-w-0 max-w-3xl">
        <div class="text-right text-sm font-bold text-slate-400">{index + 1} / {total}</div>
        <h3 class="mt-5 text-center text-2xl font-black leading-10 text-slate-900 dark:text-white md:text-3xl">{question.prompt}</h3>
        {#if question.explanationHint}<p class="mt-2 text-center text-sm text-slate-400">{question.explanationHint}</p>{/if}

        <div class="mt-8 grid min-w-0 gap-3 sm:grid-cols-2">
          {#each question.options as option, optionIndex}
            <button type="button" onclick={() => answer(option)} disabled={!!result || loading} class={`group flex min-h-20 min-w-0 items-center gap-4 rounded-2xl border p-4 text-left transition ${result && option === result.answer ? 'border-emerald-400 bg-emerald-50 dark:bg-emerald-950/30' : result && option === selected && !result.correct ? 'border-rose-400 bg-rose-50 dark:bg-rose-950/30' : 'border-slate-200 bg-slate-50 hover:-translate-y-0.5 hover:border-amber-400 hover:bg-amber-50 dark:border-slate-700 dark:bg-slate-800 dark:hover:bg-amber-950/20'} disabled:cursor-default`}>
              <span class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-white font-black text-slate-400 shadow-sm dark:bg-slate-900">{String.fromCharCode(65 + optionIndex)}</span>
              <strong class="min-w-0 break-all text-lg text-slate-800 dark:text-white">{option}</strong>
              {#if result && option === result.answer}<span class="material-symbols-outlined ml-auto shrink-0 text-emerald-500">check_circle</span>{/if}
              {#if result && option === selected && !result.correct}<span class="material-symbols-outlined ml-auto shrink-0 text-rose-500">cancel</span>{/if}
            </button>
          {/each}
        </div>

        {#if result}
          <div class={`mt-6 rounded-2xl border p-5 ${result.correct ? 'border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950/30' : 'border-rose-200 bg-rose-50 dark:border-rose-900 dark:bg-rose-950/30'}`}>
            <div class="flex items-center gap-2"><span class={`material-symbols-outlined ${result.correct ? 'text-emerald-600' : 'text-rose-600'}`}>{result.correct ? 'celebration' : 'school'}</span><strong class="text-lg text-slate-900 dark:text-white">{result.correct ? '정답입니다!' : `정답은 ${result.answer}입니다.`}</strong></div>
            <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{result.explanation}</p>
            <button type="button" onclick={next} class="mt-4 inline-flex items-center gap-1 rounded-xl bg-slate-900 px-5 py-2.5 font-black text-white dark:bg-white dark:text-slate-900">{index + 1 < total ? '다음 문제' : '학습 완료'}<span class="material-symbols-outlined">arrow_forward</span></button>
          </div>
        {/if}
      </div>
    {:else}
      <div class="grid min-h-96 place-items-center text-center"><div><span class="material-symbols-outlined text-6xl text-amber-400">workspace_premium</span><h3 class="mt-3 text-2xl font-black text-slate-900 dark:text-white">오늘 학습을 마쳤습니다.</h3><p class="mt-2 text-slate-500">{score}/{total}문제를 맞혔습니다.</p><button type="button" onclick={restart} class="mt-5 rounded-xl border border-slate-200 px-5 py-2.5 font-black text-slate-600 dark:border-slate-700 dark:text-slate-300">다시 풀기</button></div></div>
    {/if}
  </div>
</section>
