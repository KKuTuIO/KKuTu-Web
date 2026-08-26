<script>
  import { onDestroy, onMount } from 'svelte';
  import { academyApi, friendlyError } from './api.js';

  let { config } = $props();
  let difficulty = $state('STANDARD');
  let startChar = $state('');
  let challenge = $state(null);
  let answer = $state('');
  let shields = $state(0);
  let timeLeft = $state(0);
  let loading = $state(false);
  let error = $state('');
  let feedback = $state(null);
  let hintOpen = $state(false);
  let streak = $state(0);
  let solved = $state(0);
  let usedWords = $state([]);
  let history = $state([]);
  let timer;

  function stopTimer() {
    if (timer) clearInterval(timer);
    timer = null;
  }

  function startTimer(seconds) {
    stopTimer();
    timeLeft = seconds;
    if (!seconds) return;
    timer = setInterval(() => {
      timeLeft -= 1;
      if (timeLeft <= 0) {
        stopTimer();
        feedback = { accepted: false, message: '시간이 끝났습니다. 정답 후보를 확인하고 다음 문제에 도전하세요.', bestMoves: [] };
        streak = 0;
      }
    }, 1000);
  }

  async function loadChallenge(forcedChar = null, resetResources = false) {
    loading = true;
    error = '';
    feedback = null;
    hintOpen = false;
    answer = '';
    try {
      const nextChallenge = await academyApi.practice(config, difficulty, forcedChar || startChar || null, usedWords);
      challenge = nextChallenge;
      if (resetResources) shields = nextChallenge.shieldCount;
      startTimer(nextChallenge.timeLimitSeconds);
      startChar = '';
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  async function submit(event) {
    event?.preventDefault();
    if (!challenge || !answer.trim() || timeLeft < 0) return;
    stopTimer();
    loading = true;
    error = '';
    try {
      const response = await academyApi.practiceAnswer(
        config,
        challenge.requiredChar,
        usedWords,
        answer.trim(),
        shields
      );
      feedback = response;
      if (response.shieldUsed) {
        shields = Math.max(0, shields - 1);
        startTimer(challenge.timeLimitSeconds);
        answer = '';
        return;
      }
      if (!response.accepted) {
        streak = 0;
        return;
      }

      const playerWord = answer.trim();
      usedWords = [...usedWords, playerWord];
      history = [...history, { role: '나', word: playerWord, from: challenge.requiredChar, to: response.nextChallenge }];
      solved += 1;
      streak += 1;
      answer = '';

      if (!response.nextChallenge) return;
      const strategy = await academyApi.strategy(config, response.nextChallenge, usedWords, 4);
      const bot = strategy.alternatives?.[0];
      if (!bot) {
        feedback = { ...response, message: `${response.message} 상대가 이어갈 단어가 없어 승리했습니다!` };
        return;
      }
      usedWords = [...usedWords, bot.word];
      history = [...history, { role: '봇', word: bot.word, from: response.nextChallenge, to: bot.to }];
      await loadChallenge(bot.to, false);
    } catch (cause) {
      error = friendlyError(cause);
    } finally {
      loading = false;
    }
  }

  function restart() {
    stopTimer();
    usedWords = [];
    history = [];
    streak = 0;
    solved = 0;
    shields = 0;
    challenge = null;
    feedback = null;
    loadChallenge(null, true);
  }

  onMount(() => loadChallenge(null, true));
  onDestroy(stopTimer);
</script>

<div class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_350px]">
  <section class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
    <div class="bg-gradient-to-br from-emerald-600 via-emerald-600 to-teal-700 p-6 text-white md:p-8">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p class="text-sm font-black text-emerald-100">ROUTE BATTLE</p>
          <h2 class="mt-1 text-3xl font-black">루트전 연습</h2>
          <p class="mt-2 max-w-2xl text-sm leading-6 text-emerald-50/90">공개 사전의 승패 분석을 사용하는 봇과 실제 수순을 주고받으며 방어·공격 루트를 익힙니다.</p>
        </div>
        <div class="flex gap-2">
          <div class="rounded-2xl bg-white/15 px-4 py-3 text-center backdrop-blur"><span class="block text-xs text-emerald-100">연속 성공</span><strong class="text-2xl">{streak}</strong></div>
          <div class="rounded-2xl bg-white/15 px-4 py-3 text-center backdrop-blur"><span class="block text-xs text-emerald-100">해결</span><strong class="text-2xl">{solved}</strong></div>
        </div>
      </div>
    </div>

    <div class="p-5 md:p-8">
      {#if challenge}
        <div class="grid gap-5 md:grid-cols-[180px_minmax(0,1fr)]">
          <div class="grid place-items-center rounded-3xl border border-emerald-200 bg-emerald-50 p-6 text-center dark:border-emerald-900 dark:bg-emerald-950/30">
            <span class="text-xs font-black uppercase tracking-widest text-emerald-600 dark:text-emerald-400">현재 글자</span>
            <strong class="mt-2 text-7xl font-black text-emerald-700 dark:text-emerald-300">{challenge.requiredChar}</strong>
            <span class="mt-3 text-xs font-bold text-emerald-600/70">가능한 공개 단어 {challenge.availableMoveCount}개</span>
          </div>
          <div>
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600 dark:bg-slate-800 dark:text-slate-300">{challenge.difficulty}</span>
                <h3 class="mt-2 text-xl font-black text-slate-900 dark:text-white">{challenge.objective}</h3>
              </div>
              {#if challenge.timeLimitSeconds > 0}
                <div class={`grid h-16 w-16 place-items-center rounded-full border-4 text-xl font-black ${timeLeft <= 3 ? 'border-rose-300 text-rose-600' : 'border-emerald-200 text-emerald-700 dark:border-emerald-900 dark:text-emerald-300'}`}>{timeLeft}</div>
              {/if}
            </div>

            <form class="mt-5 flex flex-col gap-3 sm:flex-row" onsubmit={submit}>
              <input bind:value={answer} disabled={!!feedback && !feedback.shieldUsed && !feedback.accepted} autocomplete="off" placeholder={`‘${challenge.requiredChar}’에서 이어지는 단어`} class="min-h-14 min-w-0 flex-1 rounded-2xl border border-slate-200 bg-slate-50 px-4 text-lg font-black outline-none focus:border-emerald-500 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
              <button type="submit" disabled={loading || !answer.trim()} class="inline-flex min-h-14 items-center justify-center gap-2 rounded-2xl bg-emerald-600 px-7 font-black text-white hover:bg-emerald-700 disabled:opacity-50"><span class="material-symbols-outlined">send</span>제출</button>
            </form>

            <div class="mt-4 flex flex-wrap items-center gap-2">
              <button type="button" onclick={() => hintOpen = !hintOpen} class="inline-flex items-center gap-1 rounded-xl bg-amber-50 px-3 py-2 text-sm font-bold text-amber-700 dark:bg-amber-950/40 dark:text-amber-300"><span class="material-symbols-outlined text-lg">lightbulb</span>힌트</button>
              <span class="inline-flex items-center gap-1 rounded-xl bg-sky-50 px-3 py-2 text-sm font-bold text-sky-700 dark:bg-sky-950/40 dark:text-sky-300"><span class="material-symbols-outlined text-lg">shield</span>보호막 {shields}</span>
              <button type="button" onclick={() => loadChallenge(null, false)} class="ml-auto inline-flex items-center gap-1 rounded-xl border border-slate-200 px-3 py-2 text-sm font-bold text-slate-500 dark:border-slate-700"><span class="material-symbols-outlined text-lg">skip_next</span>다른 문제</button>
            </div>

            {#if hintOpen}
              <div class="mt-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
                {#if challenge.hint.sample}<p><strong>예시:</strong> {challenge.hint.sample}</p>{/if}
                {#if challenge.hint.firstLetter}<p><strong>첫 글자:</strong> {challenge.hint.firstLetter}</p>{/if}
                {#if challenge.hint.length}<p><strong>길이:</strong> {challenge.hint.length}자</p>{/if}
                {#if challenge.hint.theme}<p><strong>주제:</strong> {challenge.hint.theme}</p>{/if}
                {#if !challenge.hint.sample && !challenge.hint.firstLetter && !challenge.hint.length}<p>실전 난이도에서는 힌트가 제공되지 않습니다.</p>{/if}
              </div>
            {/if}
          </div>
        </div>
      {:else}
        <div class="grid min-h-72 place-items-center text-slate-500"><span class="material-symbols-outlined animate-spin text-4xl">progress_activity</span></div>
      {/if}

      {#if feedback}
        <div class={`mt-5 rounded-2xl border p-4 ${feedback.accepted ? 'border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950/30' : 'border-rose-200 bg-rose-50 dark:border-rose-900 dark:bg-rose-950/30'}`}>
          <p class="font-black text-slate-800 dark:text-white">{feedback.message}</p>
          {#if !feedback.accepted && feedback.bestMoves?.length}
            <p class="mt-2 text-xs font-bold text-slate-500">가능했던 좋은 수</p>
            <div class="mt-2 flex flex-wrap gap-2">{#each feedback.bestMoves as move}<button type="button" onclick={() => answer = move.word} class="rounded-lg bg-white px-3 py-1.5 text-sm font-bold shadow-sm dark:bg-slate-800">{move.word} → {move.to}</button>{/each}</div>
          {/if}
          {#if !feedback.accepted && !feedback.shieldUsed}
            <button type="button" onclick={() => loadChallenge(null, false)} class="mt-3 rounded-xl bg-slate-900 px-4 py-2 text-sm font-black text-white dark:bg-white dark:text-slate-900">다음 문제</button>
          {/if}
        </div>
      {/if}

      {#if error}<p class="mt-4 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</p>{/if}
    </div>
  </section>

  <aside class="grid h-fit gap-4 xl:sticky xl:top-28">
    <section class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <h3 class="font-black text-slate-900 dark:text-white">연습 설정</h3>
      <div class="mt-4 grid gap-4">
        <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">난이도
          <select bind:value={difficulty} onchange={restart} class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 dark:border-slate-700 dark:bg-slate-800 dark:text-white">
            <option value="BEGINNER">입문 · 무제한 시간</option>
            <option value="STANDARD">표준 · 15초</option>
            <option value="EXPERT">실전 · 8초</option>
          </select>
        </label>
        <label class="grid gap-1 text-sm font-bold text-slate-600 dark:text-slate-300">시작 음절 <span class="text-xs font-normal text-slate-400">비우면 자동 선택</span>
          <div class="flex gap-2"><input bind:value={startChar} maxlength="1" class="min-w-0 flex-1 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-center text-lg font-black dark:border-slate-700 dark:bg-slate-800 dark:text-white" /><button type="button" onclick={restart} class="rounded-xl bg-slate-900 px-4 font-black text-white dark:bg-white dark:text-slate-900">시작</button></div>
        </label>
      </div>
    </section>

    <section class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <div class="flex items-center justify-between"><h3 class="font-black text-slate-900 dark:text-white">대국 기록</h3><button type="button" onclick={restart} class="text-xs font-bold text-slate-400 hover:text-rose-500">초기화</button></div>
      {#if history.length}
        <ol class="mt-3 grid max-h-96 gap-2 overflow-y-auto pr-1">
          {#each history.toReversed() as item}
            <li class="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2 dark:bg-slate-800">
              <div><span class={`mr-2 text-xs font-black ${item.role === '봇' ? 'text-violet-500' : 'text-emerald-600'}`}>{item.role}</span><strong class="dark:text-white">{item.word}</strong></div><span class="text-xs text-slate-400">{item.from}→{item.to}</span>
            </li>
          {/each}
        </ol>
      {:else}<p class="mt-3 text-sm text-slate-400">정답을 제출하면 수순이 기록됩니다.</p>{/if}
    </section>
  </aside>
</div>
