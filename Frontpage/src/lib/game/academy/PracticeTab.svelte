<script>
	import { onDestroy, onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { academyClient } from './academyClient.js';
	import { friendlyError } from './api.js';
	import Select from '../ui/Select.svelte';
	import Icon from '../lobby/Icon.svelte';

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

	const levels = $derived(['BEGINNER', 'STANDARD', 'EXPERT'].map((v) => ({ value: v, label: t(`play.academy.practice.diffOpt.${v}`) })));

	const stopTimer = () => (timer && clearInterval(timer), (timer = null));
	function startTimer(seconds) {
		stopTimer();
		timeLeft = seconds;
		if (!seconds) return;
		timer = setInterval(() => {
			if (--timeLeft <= 0) {
				stopTimer();
				feedback = { accepted: false, message: t('play.academy.practice.timeUp'), bestMoves: [] };
				streak = 0;
			}
		}, 1000);
	}
	function show(next) {
		challenge = next;
		feedback = null;
		hintOpen = false;
		answer = '';
		shields = next.shieldCount;
		startTimer(next.timeLimitSeconds);
	}
	async function load(forced = null) {
		loading = true;
		error = '';
		feedback = null;
		hintOpen = false;
		answer = '';
		try {
			show(await academyClient.practice(config, difficulty, forced || startChar || null, usedWords));
			startChar = '';
		} catch (cause) {
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
	async function submit(e) {
		e?.preventDefault();
		if (!challenge || !answer.trim() || timeLeft < 0) return;
		stopTimer();
		loading = true;
		error = '';
		try {
			const r = await academyClient.practiceAnswer(config, difficulty, challenge.requiredChar, usedWords, answer.trim(), shields);
			feedback = r;
			if (r.shieldUsed) {
				shields = Math.max(0, shields - 1);
				startTimer(challenge.timeLimitSeconds);
				answer = '';
				return;
			}
			if (!r.accepted) return void (streak = 0);
			const mine = answer.trim();
			usedWords = [...usedWords, mine];
			history = [...history, { bot: false, word: mine, from: challenge.requiredChar, to: r.nextChallenge }];
			solved += 1;
			streak += 1;
			answer = '';
			const bot = r.botMove;
			if (!bot) return void (feedback = { ...r, message: t('play.academy.practice.wonNoMove', { message: r.message }) });
			usedWords = [...usedWords, bot.word];
			history = [...history, { bot: true, word: bot.word, from: r.nextChallenge, to: bot.to }];
			if (!r.followingChallenge) return void (feedback = { ...r, accepted: false, message: t('play.academy.practice.botEnded', { word: bot.word }) });
			show(r.followingChallenge);
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
		streak = solved = 0;
		challenge = feedback = null;
		load();
	}
	onMount(() => load());
	onDestroy(stopTimer);
</script>

<div class="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_17rem]">
	<section class="kk-card flex min-w-0 flex-col overflow-hidden">
		<div class="flex flex-wrap items-center justify-between gap-3 bg-(--kk-fill) px-4 py-3 text-white">
			<h2 class="text-xl font-black">{t('play.academy.tab.practice')}</h2>
			<div class="flex gap-2">
				{#each [[t('play.academy.practice.streak'), streak], [t('play.academy.practice.solved'), solved]] as [label, n] (label)}
					<div class="rounded-xl bg-white/20 px-3 py-1.5 text-center"><span class="block text-[11px] font-bold">{label}</span><strong class="text-lg leading-none">{n}</strong></div>
				{/each}
			</div>
		</div>
		<div class="flex min-w-0 flex-col gap-4 p-4 md:p-5">
			{#if challenge}
				<div class="grid min-w-0 gap-4 md:grid-cols-[9rem_minmax(0,1fr)]">
					<div class="grid place-items-center rounded-2xl border-[1.5px] border-(color:--kk-accent) bg-(--kk-accent-soft) p-4 text-center">
						<span class="text-xs font-extrabold text-(color:--kk-accent-strong)">{t('play.academy.practice.current')}</span>
						<strong class="text-6xl leading-tight font-black text-(color:--kk-accent-strong)">{challenge.requiredChar}</strong>
						<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.practice.available', { n: challenge.availableMoveCount })}</span>
					</div>
					<div class="flex min-w-0 flex-col gap-3">
						<div class="flex items-start justify-between gap-3">
							<div class="min-w-0"><span class="kk-chip">{t(`play.academy.practice.diff.${challenge.difficulty}`)}</span><h3 class="mt-1 text-lg font-black break-words">{challenge.objective}</h3></div>
							{#if challenge.timeLimitSeconds > 0}<div class="grid size-14 shrink-0 place-items-center rounded-full border-4 text-lg font-black {timeLeft <= 3 ? 'border-(color:--kk-danger) text-(color:--kk-danger)' : 'border-(color:--kk-accent) text-(color:--kk-accent-strong)'}">{timeLeft}</div>{/if}
						</div>
						<form onsubmit={submit} class="flex flex-col gap-2 sm:flex-row">
							<input bind:value={answer} disabled={!!feedback && !feedback.shieldUsed && !feedback.accepted} autocomplete="off" aria-label={t('play.academy.practice.submit')} placeholder={t('play.academy.sim.continues', { c: challenge.requiredChar })} class="h-13 min-w-0 flex-1 kk-input px-3 text-lg font-black" />
							<button class="kk-btn kk-btn-primary inline-flex min-h-13 items-center justify-center gap-1.5 px-6" disabled={loading || !answer.trim()}><Icon name="send" size={20} fill />{t('play.academy.practice.submit')}</button>
						</form>
						<div class="flex flex-wrap items-center gap-2">
							<button type="button" onclick={() => (hintOpen = !hintOpen)} class="kk-btn inline-flex items-center gap-1 text-sm"><Icon name="lightbulb" size={18} />{t('play.academy.practice.hint')}</button>
							<span class="kk-chip inline-flex items-center gap-1"><Icon name="shield" size={14} />{t('play.academy.practice.shields', { n: shields })}</span>
							<button type="button" onclick={() => load()} class="kk-btn ml-auto inline-flex items-center gap-1 text-sm"><Icon name="skip_next" size={18} />{t('play.academy.practice.other')}</button>
						</div>
						{#if hintOpen}
							<div class="rounded-xl bg-(--kk-warn-soft) p-3 text-sm text-(color:--kk-warn)">
								{#if challenge.hint.sample}<p><strong>{t('play.academy.practice.hint.sample')}:</strong> {challenge.hint.sample}</p>{/if}
								{#if challenge.hint.firstLetter}<p><strong>{t('play.academy.practice.hint.first')}:</strong> {challenge.hint.firstLetter}</p>{/if}
								{#if challenge.hint.length}<p><strong>{t('play.academy.practice.hint.length')}:</strong> {t('play.academy.practice.hint.lengthVal', { n: challenge.hint.length })}</p>{/if}
								{#if challenge.hint.theme}<p><strong>{t('play.academy.practice.hint.theme')}:</strong> {challenge.hint.theme}</p>{/if}
								{#if !challenge.hint.sample && !challenge.hint.firstLetter && !challenge.hint.length}<p>{t('play.academy.practice.hint.none')}</p>{/if}
							</div>
						{/if}
					</div>
				</div>
			{:else}
				<p class="grid min-h-60 place-items-center text-sm font-bold text-(color:--kk-muted)"><Icon name="progress_activity" size={32} class="animate-spin" /></p>
			{/if}

			{#if feedback}
				<div class="rounded-xl border-[1.5px] p-3 {feedback.accepted ? 'border-(color:--kk-accent) bg-(--kk-accent-soft)' : 'border-(color:--kk-danger) bg-(--kk-danger-soft)'}">
					<p class="font-extrabold break-words">{feedback.message}</p>
					{#if !feedback.accepted && feedback.bestMoves?.length}
						<p class="mt-2 text-xs font-bold text-(color:--kk-muted)">{t('play.academy.practice.possible')}</p>
						<div class="mt-1 flex flex-wrap gap-2">{#each feedback.bestMoves as move (move.word)}<button type="button" onclick={() => (answer = move.word)} class="kk-btn text-sm break-all">{move.word} → {move.to}</button>{/each}</div>
					{/if}
					{#if !feedback.accepted && !feedback.shieldUsed}<button type="button" onclick={() => load()} class="kk-btn kk-btn-primary mt-3 text-sm">{t('play.academy.practice.next')}</button>{/if}
				</div>
			{/if}
			{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-4 py-3 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>{/if}
		</div>
	</section>

	<aside class="flex h-fit min-w-0 flex-col gap-4">
		<section class="kk-card flex flex-col gap-3 p-4">
			<h3 class="font-extrabold">{t('play.academy.practice.settings')}</h3>
			<div class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.practice.difficulty')}</span><Select label={t('play.academy.practice.difficulty')} bind:value={difficulty} options={levels} onchange={restart} /></div>
			<div class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.practice.startSyl')} <span class="font-medium">· {t('play.academy.practice.auto')}</span></span>
				<div class="flex gap-2"><input bind:value={startChar} maxlength="1" aria-label={t('play.academy.practice.startSyl')} class="h-11 min-w-0 flex-1 kk-input px-2 text-center text-lg font-black" /><button type="button" onclick={restart} class="kk-btn kk-btn-primary px-4">{t('play.academy.practice.start')}</button></div>
			</div>
		</section>
		<section class="kk-card flex flex-col gap-2 p-4">
			<div class="flex items-center justify-between"><h3 class="font-extrabold">{t('play.academy.practice.log')}</h3><button type="button" onclick={restart} class="text-xs font-bold text-(color:--kk-muted) hover:text-(color:--kk-danger)">{t('play.academy.practice.clear')}</button></div>
			{#if history.length}
				<ol class="flex max-h-96 flex-col gap-1.5 overflow-y-auto">{#each history.toReversed() as item, i (history.length - i)}<li class="flex items-center justify-between gap-2 rounded-lg bg-(--kk-surface-2) px-3 py-1.5"><span class="min-w-0"><span class="mr-1.5 text-xs font-extrabold {item.bot ? 'text-(color:--kk-blue)' : 'text-(color:--kk-accent-strong)'}">{item.bot ? t('play.academy.practice.bot') : t('play.academy.practice.me')}</span><strong class="break-all">{item.word}</strong></span><span class="shrink-0 text-xs text-(color:--kk-muted)">{item.from}→{item.to}</span></li>{/each}</ol>
			{:else}<p class="text-sm text-(color:--kk-muted)">{t('play.academy.practice.noLog')}</p>{/if}
		</section>
	</aside>
</div>
