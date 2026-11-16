<script>
	import 'katex/dist/katex.min.css';
	import { onMount } from 'svelte';
	import Icon from './Icon.svelte';
	import Dialog from './Dialog.svelte';
	import RewardChip from './RewardChip.svelte';
	import ObtainScreen from './ObtainScreen.svelte';
	import { confirm } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { playGameBgm, playLobbyBgm, playResultBgm, playSound } from '../audio.svelte.js';
	import { haptic } from '../haptics.js';
	import { levelUp } from '../levelUp.svelte.js';
	import { t } from '../i18n.svelte.js';
	import { CIRCLED, ERROR_KEYS, LEVEL_KEYS, clock, formulaHtml, mathHtml } from '../mathText.js';

	/**
	 * 수학 연습 (the arcade): the player picks a level and gets a set of questions, as in a mock exam, five of them. They are answered in any order within
	 * the time and sent all at once. The server draws the set, keeps the answers and grades the sheet (this only draws the questions and sends the
	 * numbers that were chosen); a set that gets enough right pays, in proportion to its points.
	 * @type {{store:any, onback:()=>void}}
	 */
	let { store, onback } = $props();

	const math = $derived(store.net.math);
	const summary = $derived(math.summary);
	const session = $derived(math.session);
	const result = $derived(math.result);
	const questions = $derived(session?.questions ?? []);
	const playing = $derived(session?.status === 'playing');
	const over = $derived(session?.status === 'over' && !!result);
	const reward = $derived(over ? math.last?.reward : null);
	const noTries = $derived(!!summary?.reward.pay && (summary?.left ?? 1) <= 0);
	const full = $derived((summary?.slots ?? []).reduce((a, b) => a + b, 0));

	/** the number chosen for each question (0: none), kept here until the sheet is sent */
	let answers = $state(/** @type {number[]} */ ([]));
	let now = $state(Date.now());
	let helpOpen = $state(false);
	let rewardOpen = $state(false);
	let sent = $state(false);
	let wasPlaying = null; // null until a set is seen: one that was already over when the screen opened is not "ended" now
	let lastSend = 0;
	let endTimer;
	const left = $derived(session ? Math.max(0, session.left - (now - session.at)) : 0);
	const answered = $derived(answers.filter((a) => a > 0).length);

	// What the server answered.
	let seen = 0;
	$effect(() => {
		const last = math.last;
		if (!last || last.at === seen) return;
		seen = last.at;
		if (last.error) {
			if (last.error === 'fast') sent = false;
			toast(t(ERROR_KEYS[last.error] ?? ERROR_KEYS.over), { kind: 'info', icon: 'calculate' });
			if (last.error !== 'none') playSound('fail');
		}
	});
	$effect(() => {
		const isPlaying = session ? session.status === 'playing' : null;
		if (isPlaying === true && wasPlaying !== true) {
			// a set has begun: a clean sheet
			answers = Array.from({ length: session.questions.length }, () => 0);
			sent = false;
		}
		if (wasPlaying === true && isPlaying === false && result) {
			// the set was graded: the whistle first, then how it went
			playSound('horr');
			clearTimeout(endTimer);
			endTimer = setTimeout(() => playSound(reward || result.correct >= (summary?.minCorrect ?? 4) ? 'success' : 'timeout'), 900);
			playResultBgm();
			if (reward) setTimeout(() => levelUp.whenIdle(() => (rewardOpen = true)), 1000);
		}
		wasPlaying = isPlaying;
	});
	// the music of a set is 자퀴's (as in the quiz rounds)
	$effect(() => {
		if (playing) playGameBgm('JaqwiBGM');
	});

	onMount(() => {
		store.mathOpen(true);
		store.ui.hideFab = true;
		try {
			if (!localStorage.getItem('kk-math-help')) {
				helpOpen = true;
				localStorage.setItem('kk-math-help', '1');
			}
		} catch {
			/* no storage: the help is only a button then */
		}
		const tick = setInterval(() => {
			now = Date.now();
			// the time is up: the sheet is sent as it is (again every two seconds until the server has graded it)
			if (playing && !sent && left === 0 && now - lastSend > 2000) send();
		}, 250);
		return () => {
			clearInterval(tick);
			clearTimeout(endTimer);
			store.ui.hideFab = false;
			playLobbyBgm({ fromResult: over });
		};
	});

	function start(level) {
		if (noTries) return;
		store.mathStart(level);
	}
	function choose(q, n) {
		if (!playing || sent) return;
		answers[q] = answers[q] === n ? 0 : n;
		playSound('Al');
		haptic(6);
	}
	function send() {
		if (!playing) return;
		lastSend = Date.now();
		sent = true;
		store.mathSubmit(answers);
	}
	async function submit() {
		if (!playing || sent) return;
		const open = questions.length - answered;
		if (open > 0 && !(await confirm({ message: t('play.math.submit.confirm', { n: open }), ok: t('play.math.submit.ok'), cancel: t('play.math.submit.cancel') }))) return;
		send();
	}
	async function leave() {
		if (playing) {
			if (!(await confirm({ message: t('play.math.leave.confirm'), ok: t('play.math.leave.ok'), cancel: t('play.math.leave.cancel') }))) return;
			// the set goes on on the server (the time runs; coming back picks it up again) and the try it took stays taken
			store.net.math.session = null;
			store.net.math.result = null;
			playLobbyBgm({ fromResult: false });
			onback();
			return;
		}
		if (over) {
			store.net.math.session = null;
			store.net.math.result = null;
			store.mathOpen();
			playLobbyBgm({ fromResult: true });
			return;
		}
		onback();
	}
	function again() {
		const level = session?.level;
		store.net.math.session = null;
		store.net.math.result = null;
		if (level) start(level);
	}
</script>

{#if reward}
	<ObtainScreen bind:open={rewardOpen} title={t('play.obtain.acquired')} items={[{ key: 'exp', q: reward.exp }, { key: 'ping', q: reward.ping }]} shop={store.shop} details={{}} ribbon ambient="shine" palette="teal" />
{/if}

<Dialog bind:open={helpOpen} title={t('play.math.help.title')}>
	<div class="flex flex-col gap-3">
		<p class="text-sm leading-relaxed font-medium">{t('play.math.help.intro', { n: summary?.slots.length ?? 5, min: Math.round((summary?.time ?? 900) / 60) })}</p>
		<ul class="flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed font-medium text-(color:--kk-muted)">
			<li>{t('play.math.help.rule.pass', { min: summary?.minCorrect ?? 4 })}</li>
			<li>{t('play.math.help.rule.points', { exp: summary?.reward.expPerPoint ?? 5, ping: summary?.reward.pingPerPoint ?? 0.5 })}</li>
			<li>{t('play.math.help.rule.tries', { n: summary?.perDay ?? 5 })}</li>
			<li>{t('play.math.help.rule.leave')}</li>
			<li>{t('play.math.help.rule.pool')}</li>
		</ul>
		<button type="button" onclick={() => (helpOpen = false)} class="hw kk-btn kk-btn-primary min-h-11 font-extrabold">{t('play.math.help.ok')}</button>
	</div>
</Dialog>

<div class="flex min-h-0 flex-1 flex-col">
	<div class="flex items-center gap-3 px-3 py-2">
		<button type="button" aria-label={t('play.math.back')} onclick={leave} class="round"><Icon name="arrow_back" size={24} /></button>
		<h2 class="title text-xl font-extrabold">{t('play.math.title')}</h2>
		<button type="button" aria-label={t('play.math.helpButton')} onclick={() => (helpOpen = true)} class="round ml-auto"><Icon name="help" size={24} /></button>
	</div>

	<div class="flex min-h-0 flex-1 flex-col items-center gap-3 overflow-y-auto px-3 pb-3">
		{#if !session}
			<div class="panel flex w-full max-w-md flex-col gap-2 p-3">
				<div class="flex items-center justify-between gap-2">
					<b class="text-sm font-extrabold">{t('play.math.level.title')}</b>
					{#if summary?.reward.pay}<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.math.leftToday', { n: summary.left })}</span>{/if}
				</div>
				<div class="flex flex-col gap-1.5">
					{#each Object.keys(LEVEL_KEYS) as level (level)}
						{@const ready = summary?.levels.includes(level)}
						<button type="button" class="level" disabled={!ready || noTries} onclick={() => start(level)}>
							<b class="font-extrabold">{t(LEVEL_KEYS[level])}</b>
							<small class="font-bold text-(color:--kk-muted)">{ready ? t(`${LEVEL_KEYS[level]}.desc`) : t('play.arcade.soon')}</small>
						</button>
					{/each}
					{#each ['e1', 'e2', 'm'] as level (level)}
						<button type="button" class="level" disabled>
							<b class="font-extrabold">{t(`play.math.level.${level}`)}</b>
							<small class="font-bold text-(color:--kk-muted)">{t('play.arcade.soon')}</small>
						</button>
					{/each}
				</div>
				{#if summary?.reward.pay}
					<div class="flex items-center justify-between gap-2 text-xs font-bold text-(color:--kk-muted)">
						<span>{t('play.math.fullReward')}</span>
						<span class="flex items-center gap-1"><RewardChip reward={{ kind: 'xp', q: Math.round(summary.reward.expPerPoint * full) }} size={28} /><RewardChip reward={{ kind: 'ping', q: Math.round(summary.reward.pingPerPoint * full) }} size={28} /></span>
					</div>
				{/if}
			</div>
		{:else}
			<div class="panel flex w-full max-w-md items-center justify-between gap-2 px-3 py-2 text-sm font-extrabold tabular-nums">
				<span>{t('play.math.progress', { n: over ? questions.length : answered, total: questions.length })}</span>
				<span class:hurry={playing && left <= 60_000}>{over ? t('play.math.done') : clock(left)}</span>
			</div>

			{#each questions as q, i (i)}
				{@const mark = over ? result.marks[i] : null}
				<div class="panel flex w-full max-w-md flex-col gap-2 p-3">
					<div class="flex items-center justify-between gap-2 text-xs font-extrabold text-(color:--kk-muted)">
						<span>{t('play.math.question', { n: i + 1 })}{#if q.unit}<span class="unit">{q.unit}</span>{/if}</span>
						<span class="flex items-center gap-1.5">
							{#if mark}<span class={mark.right ? 'right' : 'wrong'}>{mark.right ? t('play.math.right') : t('play.math.wrong')}</span>{/if}
							<span>{t('play.math.points', { n: q.points })}</span>
						</span>
					</div>
					<div class="stem">{@html mathHtml(q.stem)}</div>
					<div class="flex flex-col gap-1.5">
						{#each q.choices as c, n (n)}
							{@const picked = (over ? mark.mine : answers[i]) === n + 1}
							<button
								type="button"
								class="choice"
								class:on={picked && !over}
								class:good={over && mark.answer === n + 1}
								class:bad={over && picked && !mark.right}
								disabled={over || sent}
								onclick={() => choose(i, n + 1)}
							>
								<span class="num">{CIRCLED[n]}</span><span class="formula">{@html formulaHtml(c)}</span>
							</button>
						{/each}
					</div>
					{#if over && result.explanations[i]}
						<div class="why"><b>{t('play.math.explanation')}</b> {@html mathHtml(result.explanations[i])}</div>
					{/if}
				</div>
			{/each}

			{#if playing}
				<button type="button" onclick={submit} disabled={sent} class="hw kk-btn kk-btn-hero kk-btn-lg w-full max-w-md"><span class="kk-btn-label">{t('play.math.submit')}</span></button>
			{:else if over}
				<div class="panel flex w-full max-w-md flex-col gap-3 p-3">
					<b class="text-center text-lg font-extrabold">{reward || result.correct >= (summary?.minCorrect ?? 4) ? t('play.math.result.success', { n: result.correct, points: result.points }) : t('play.math.result.fail', { n: result.correct, points: result.points })}</b>
					{#if reward}
						<div class="flex flex-wrap items-center justify-center gap-1.5"><RewardChip reward={{ kind: 'xp', q: reward.exp }} size={36} /><RewardChip reward={{ kind: 'ping', q: reward.ping }} size={36} /></div>
					{:else if math.last?.unpaid === 'few'}
						<p class="text-center text-sm font-bold text-(color:--kk-muted)">{t('play.math.unpaid.few', { min: summary?.minCorrect ?? 4 })}</p>
					{/if}
					<div class="flex gap-2">
						<button type="button" onclick={leave} class="hw kk-btn min-h-12 flex-1 font-extrabold">{t('play.math.stop')}</button>
						<button type="button" onclick={again} disabled={noTries} class="hw kk-btn kk-btn-primary min-h-12 flex-1 font-extrabold">{t('play.math.again')}</button>
					</div>
				</div>
			{/if}
		{/if}
	</div>
</div>

<style>
	.title {
		color: #fff;
		text-shadow: 0 1px 4px rgb(10 14 50 / 0.65);
	}
	.round {
		display: grid;
		place-items: center;
		flex: none;
		width: 2.75rem;
		height: 2.75rem;
		border-radius: 999px;
		background: rgb(255 255 255 / 0.9);
		color: #334155;
		box-shadow: 0 4px 6px rgb(20 40 80 / 0.2);
		transition: transform 0.15s, background 0.15s;
	}
	.round:hover {
		transform: scale(1.05);
		background: #fff;
	}
	.panel {
		flex: none;
		border-radius: 20px;
		color: var(--kk-text);
		background: color-mix(in srgb, var(--kk-surface) 94%, transparent);
		box-shadow: 0 3px 6px rgb(20 40 80 / 0.22);
	}
	.level {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 0.5rem;
		border-radius: 12px;
		border: 1.5px solid var(--kk-line);
		background: var(--kk-surface-2);
		padding: 0.7rem 0.9rem;
		text-align: left;
		transition: border-color 0.15s;
	}
	.level:hover:not(:disabled) {
		border-color: var(--kk-accent);
	}
	.level:disabled {
		opacity: 0.55;
	}
	.hurry {
		color: #d33b3b;
	}
	.unit {
		margin-left: 0.5rem;
		font-weight: 700;
		opacity: 0.8;
	}
	.stem {
		overflow-x: auto;
		overflow-y: hidden;
		font-size: 1rem;
		font-weight: 600;
		line-height: 1.7;
	}
	.choice {
		display: flex;
		align-items: center;
		gap: 0.6rem;
		overflow-x: auto;
		border-radius: 12px;
		border: 1.5px solid var(--kk-line);
		background: var(--kk-surface-2);
		padding: 0.5rem 0.8rem;
		text-align: left;
		transition: border-color 0.12s, background 0.12s;
	}
	.choice .num {
		flex: none;
		font-weight: 800;
	}
	.choice:hover:not(:disabled) {
		border-color: var(--kk-accent);
	}
	.choice.on {
		border-color: var(--kk-accent);
		background: color-mix(in srgb, var(--kk-accent) 16%, var(--kk-surface-2));
	}
	.choice.good {
		border-color: #3f8a4a;
		background: color-mix(in srgb, #3f8a4a 16%, var(--kk-surface-2));
	}
	.choice.bad {
		border-color: #d33b3b;
		background: color-mix(in srgb, #d33b3b 16%, var(--kk-surface-2));
	}
	.right {
		color: #3f8a4a;
	}
	.wrong {
		color: #d33b3b;
	}
	.why {
		border-radius: 12px;
		background: var(--kk-surface-2);
		padding: 0.5rem 0.8rem;
		font-size: 0.85rem;
		line-height: 1.6;
	}
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .right {
			color: #7bd488;
		}
		:global(:root:not([data-theme='light'])) .wrong,
		:global(:root:not([data-theme='light'])) .hurry {
			color: #ff8a8a;
		}
	}
	:global(:root[data-theme='dark']) .right {
		color: #7bd488;
	}
	:global(:root[data-theme='dark']) .wrong,
	:global(:root[data-theme='dark']) .hurry {
		color: #ff8a8a;
	}
</style>
