<script>
	import { onMount, tick as nextTick } from 'svelte';
	import Icon from './Icon.svelte';
	import Dialog from './Dialog.svelte';
	import RewardChip from './RewardChip.svelte';
	import ObtainScreen from './ObtainScreen.svelte';
	import PriceChip from './PriceChip.svelte';
	import TypingChart from './TypingChart.svelte';
	import { confirm } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { playSound } from '../audio.svelte.js';
	import { haptic } from '../haptics.js';
	import { levelUp } from '../levelUp.svelte.js';
	import { t } from '../i18n.svelte.js';
	import { ERROR_KEYS, UNPAID_KEYS, clock, live, marks } from '../typing.js';

	/**
	 * 타자 연습 (the arcade): the player sets a goal (strokes a minute; the first setting of a day is free, a change after it costs 핑), picks one of the
	 * texts of the 장문 타자대결 and types it line by line. The server holds the text and the clock and judges every line (this only draws and sends what
	 * was typed); a text typed at the goal's speed with enough accuracy pays.
	 * @type {{store:any, onback:()=>void}}
	 */
	let { store, onback } = $props();

	const typing = $derived(store.net.typing);
	const summary = $derived(typing.summary);
	const session = $derived(typing.session);
	const money = $derived(store.net.users[store.net.id]?.money ?? 0);
	const texts = $derived(summary?.texts ?? []);
	const goal = $derived(summary?.goal ?? null);

	/** 'pick' (the goal and the texts), 'count' (3, 2, 1), 'play', 'over' */
	let phase = $state('pick');
	let chosen = $state('');
	let count = $state(3);
	let typed = $state('');
	let composing = $state(false);
	let waiting = $state(false);
	let now = $state(Date.now());
	let right = $state(0);
	let total = $state(0);
	let points = $state(/** @type {{t:number, v:number}[]} */ ([]));
	let pendingGoal = $state(/** @type {number|null} */ (null));
	let changeOpen = $state(false);
	let rewardOpen = $state(false);
	let inputEl = $state(/** @type {HTMLInputElement|null} */ (null));
	let waitTimer;
	let countTimer;

	const lines = $derived(session?.lines ?? []);
	const index = $derived(session?.index ?? 0);
	const over = $derived(phase === 'over');
	const elapsed = $derived(over ? Math.round((typing.last?.result?.seconds ?? 0) * 1000) : session ? session.elapsed + (now - session.at) : 0);
	const stats = $derived(
		over && typing.last?.result
			? { speed: typing.last.result.speed, accuracy: typing.last.result.accuracy, wrong: typing.last.result.wrong }
			: live({ right, total, expected: lines[index] ?? '', typed, elapsed, composing })
	);
	const result = $derived(over ? typing.last?.result : null);
	const reward = $derived(over ? typing.last?.reward : null);
	const reached = $derived(!!result && goal !== null && result.speed >= goal && result.accuracy >= (summary?.minAccuracy ?? 95));
	const marked = $derived(marks(lines[index] ?? '', typed, composing));

	// What the server answered.
	let seen = 0;
	$effect(() => {
		const last = typing.last;
		if (!last || last.at === seen) return;
		seen = last.at;
		waiting = false;
		if (last.error) {
			const key = ERROR_KEYS[last.error] ?? ERROR_KEYS.over;
			toast(t(key), { kind: 'info', icon: 'keyboard' });
			if (last.error !== 'same') playSound('fail');
			return;
		}
		if (last.line) {
			total += last.line.strokes;
			right += last.line.strokes - last.line.wrong;
			typed = '';
			playSound(last.line.wrong ? 'fail' : 'K2');
			if (last.result) {
				phase = 'over';
				playSound(reward || reached ? 'success' : 'timeout');
				if (reward) setTimeout(() => levelUp.whenIdle(() => (rewardOpen = true)), 400);
			}
		}
	});

	onMount(() => {
		store.typingOpen();
		store.ui.hideFab = true; // the chat button would sit over the input
		const timer = setInterval(() => {
			now = Date.now();
			// the speed over the time, a point a second
			if (phase === 'play' && session) {
				const sec = Math.floor(elapsed / 1000);
				if (sec > 0 && points.at(-1)?.t !== sec) points = [...points, { t: sec, v: stats.speed }];
			}
		}, 250);
		return () => {
			clearInterval(timer);
			clearTimeout(waitTimer);
			clearInterval(countTimer);
			store.ui.hideFab = false;
		};
	});

	// ---- the goal ----
	function pickGoal(g) {
		if (g === goal) return;
		if (summary?.goalFree) store.typingGoal(g);
		else {
			pendingGoal = g;
			changeOpen = true;
		}
	}
	function changeGoal() {
		if (pendingGoal === null || money < (summary?.goalCost ?? 0)) return;
		store.typingGoal(pendingGoal);
		changeOpen = false;
	}

	// ---- a text ----
	function begin(key) {
		chosen = key;
		right = 0;
		total = 0;
		points = [];
		typed = '';
		phase = 'count';
		count = 3;
		playSound('As2');
		clearInterval(countTimer);
		countTimer = setInterval(async () => {
			count -= 1;
			if (count > 0) return playSound('As2');
			clearInterval(countTimer);
			store.typingStart(key);
			phase = 'play';
			await nextTick();
			inputEl?.focus();
		}, 800);
	}
	async function leave() {
		if (phase === 'play' || phase === 'count') {
			if (!(await confirm({ message: t('play.typing.leave.confirm'), ok: t('play.typing.leave.ok'), cancel: t('play.typing.leave.cancel') }))) return;
			clearInterval(countTimer);
			phase = 'pick';
			return;
		}
		if (phase === 'over') {
			phase = 'pick';
			store.typingOpen();
			return;
		}
		onback();
	}
	function send() {
		if (phase !== 'play' || waiting || !typed.length) return;
		waiting = true;
		haptic(8);
		store.typingSubmit(typed);
		// an error packet (the server was busy) says nothing about the text: do not stay waiting for it
		clearTimeout(waitTimer);
		waitTimer = setTimeout(() => (waiting = false), 6000);
	}
	function onkey(e) {
		if (e.key === 'Enter' && !e.isComposing) {
			e.preventDefault();
			send();
		}
	}
	function ontype() {
		if (phase === 'play') playSound('As2');
	}
</script>

{#if reward}
	<ObtainScreen bind:open={rewardOpen} title={t('play.obtain.acquired')} items={[{ key: 'exp', q: reward.exp }, { key: 'ping', q: reward.ping }]} shop={store.shop} details={{}} ribbon ambient="shine" palette="teal" />
{/if}

<Dialog bind:open={changeOpen} title={t('play.typing.goal.change.title')}>
	<div class="flex flex-col gap-3">
		<p class="text-sm leading-relaxed font-medium">{t('play.typing.goal.change.body', { goal: pendingGoal ?? 0 })}</p>
		<button type="button" onclick={changeGoal} disabled={money < (summary?.goalCost ?? 0)} class="hw kk-btn kk-btn-primary inline-flex min-h-12 items-center justify-center gap-2.5 px-5 font-extrabold">
			{t('play.typing.goal.change.ok')}<PriceChip price={summary?.goalCost ?? 0} lacking={money < (summary?.goalCost ?? 0)} />
		</button>
		{#if money < (summary?.goalCost ?? 0)}<p class="text-center text-xs font-bold text-(color:--kk-muted)">{t('play.typing.error.poor')}</p>{/if}
	</div>
</Dialog>

<div class="flex min-h-0 flex-1 flex-col">
	<div class="flex items-center gap-3 px-3 py-2">
		<button type="button" aria-label={t('play.typing.back')} onclick={leave} class="round"><Icon name="arrow_back" size={24} /></button>
		<h2 class="title text-xl font-extrabold">{t('play.typing.title')}</h2>
	</div>

	<div class="flex min-h-0 flex-1 flex-col items-center gap-3 overflow-y-auto px-3 pb-3">
		{#if phase === 'pick'}
			<div class="panel flex w-full max-w-md flex-col gap-2 p-3">
				<div class="flex items-baseline justify-between gap-2">
					<b class="text-sm font-extrabold">{t('play.typing.goal.title')}</b>
					<span class="text-xs font-bold text-(color:--kk-muted)">{summary?.goalFree ? t('play.typing.goal.free') : t('play.typing.goal.changeNote')}</span>
				</div>
				<div class="flex flex-wrap gap-1.5">
					{#each summary?.goals ?? [] as g (g)}
						<button type="button" class="chip" class:on={g === goal} onclick={() => pickGoal(g)}>{t('play.typing.speed', { n: g.toLocaleString() })}</button>
					{/each}
				</div>
				<p class="text-xs font-medium text-(color:--kk-muted)">{goal === null ? t('play.typing.goal.none') : t('play.typing.goal.rule', { goal, acc: summary?.minAccuracy ?? 95 })}</p>
				{#if summary?.reward.pay}
					<div class="flex items-center justify-between gap-2 text-xs font-bold text-(color:--kk-muted)">
						<span>{t('play.typing.leftToday', { n: summary.left })}</span>
						{#if goal !== null}
							{@const mult = [...summary.reward.tiers].reverse().find((x) => goal >= x.from)?.mult ?? 1}
							<span class="flex items-center gap-1"><RewardChip reward={{ kind: 'xp', q: Math.round(summary.reward.exp * mult) }} size={28} /><RewardChip reward={{ kind: 'ping', q: Math.round(summary.reward.ping * mult) }} size={28} /></span>
						{/if}
					</div>
				{/if}
			</div>

			<div class="panel flex w-full max-w-md flex-col gap-2 p-3">
				<b class="text-sm font-extrabold">{t('play.typing.text.title')}</b>
				<div class="flex flex-col gap-1.5">
					{#each texts as x (x.key)}
						<button type="button" class="text" onclick={() => begin(x.key)}>
							<b class="font-extrabold">{x.title}</b>
							<small class="font-bold text-(color:--kk-muted)">{t('play.typing.text.lines', { n: x.lines })}</small>
						</button>
					{/each}
				</div>
			</div>
		{:else if phase === 'count'}
			<div class="panel flex w-full max-w-md flex-col items-center gap-2 p-8">
				<span class="count" aria-live="polite">{count}</span>
				<span class="text-sm font-bold text-(color:--kk-muted)">{texts.find((x) => x.key === chosen)?.title ?? ''}</span>
			</div>
		{:else}
			<div class="panel flex w-full max-w-md flex-col gap-3 p-3">
				<div class="flex items-center justify-between text-xs font-extrabold text-(color:--kk-muted)">
					<b>{session?.title ?? ''}</b>
					<span class="tabular-nums">{Math.min(index + (over ? 0 : 1), lines.length)} / {lines.length}</span>
				</div>
				<div class="lines" aria-live="off">
					{#each lines as line, i (i)}
						{#if i === index && !over}
							<div class="cur">
								<div class="prompt" lang="ko">{#each marked as m, k (k)}<span class:right={m.ok === true} class:wrong={m.ok === false}>{m.ch}</span>{/each}</div>
								<input
									bind:this={inputEl}
									bind:value={typed}
									oncompositionstart={() => (composing = true)}
									oncompositionend={() => (composing = false)}
									oninput={ontype}
									onkeydown={onkey}
									class="entry"
									lang="ko"
									autocomplete="off"
									autocapitalize="off"
									spellcheck="false"
									autocorrect="off"
									aria-label={t('play.typing.input')}
									placeholder={t('play.typing.input.empty')}
									disabled={waiting}
								/>
							</div>
						{:else if i > index - 2 && i < index + 4}
							<div class="line" class:done={i < index}>{line}</div>
						{/if}
					{/each}
				</div>
			</div>

			<div class="panel grid w-full max-w-md grid-cols-3 gap-2 p-3 text-center">
				<div><small>{t('play.typing.stat.time')}</small><b class="tabular-nums">{clock(elapsed)}</b></div>
				<div><small>{t('play.typing.stat.speed')}</small><b class="tabular-nums">{stats.speed}</b></div>
				<div><small>{t('play.typing.stat.accuracy')}</small><b class="tabular-nums">{stats.accuracy.toFixed(1)}</b><i>{t('play.typing.stat.wrong', { n: stats.wrong })}</i></div>
			</div>

			{#if over && result}
				<div class="panel flex w-full max-w-md flex-col gap-3 p-3">
					<b class="text-center text-lg font-extrabold">{reached ? t('play.typing.result.success', { goal: goal ?? 0 }) : t('play.typing.result.fail')}</b>
					<div>
						<b class="text-xs font-extrabold text-(color:--kk-muted)">{t('play.typing.chart.title')}</b>
						<TypingChart {points} {goal} label={t('play.typing.chart.title')} />
					</div>
					{#if reward}
						<div class="flex flex-wrap items-center justify-center gap-1.5"><RewardChip reward={{ kind: 'xp', q: reward.exp }} size={36} /><RewardChip reward={{ kind: 'ping', q: reward.ping }} size={36} /></div>
					{:else if typing.last?.unpaid && UNPAID_KEYS[typing.last.unpaid]}
						<p class="text-center text-sm font-bold text-(color:--kk-muted)">{t(UNPAID_KEYS[typing.last.unpaid], { goal: goal ?? 0, acc: summary?.minAccuracy ?? 95 })}</p>
					{/if}
					<div class="flex gap-2">
						<button type="button" onclick={leave} class="hw kk-btn min-h-12 flex-1 font-extrabold">{t('play.typing.stop')}</button>
						<button type="button" onclick={() => begin(chosen)} class="hw kk-btn kk-btn-primary min-h-12 flex-1 font-extrabold">{t('play.typing.again')}</button>
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
	.chip {
		border-radius: 999px;
		border: 1.5px solid var(--kk-line);
		background: var(--kk-surface-2);
		padding: 0.3rem 0.8rem;
		font-size: 0.85rem;
		font-weight: 800;
		font-variant-numeric: tabular-nums;
	}
	.chip.on {
		border-color: var(--kk-accent);
		background: var(--kk-accent);
		color: #fff;
	}
	.text {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 0.5rem;
		border-radius: 12px;
		border: 1.5px solid var(--kk-line);
		background: var(--kk-surface-2);
		padding: 0.6rem 0.9rem;
		text-align: left;
		transition: border-color 0.15s;
	}
	.text:hover {
		border-color: var(--kk-accent);
	}
	.count {
		font-size: 4rem;
		font-weight: 800;
		line-height: 1;
		color: var(--kk-accent);
	}
	.lines {
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
	}
	.line {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		padding: 0.15rem 0.6rem;
		font-size: 1rem;
		font-weight: 700;
		color: var(--kk-muted);
	}
	.line.done {
		opacity: 0.55;
	}
	.cur {
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
		border-radius: 12px;
		background: var(--kk-surface-2);
		padding: 0.5rem 0.6rem;
		border: 2px solid var(--kk-accent);
	}
	.prompt {
		font-size: 1.15rem;
		font-weight: 800;
		line-height: 1.5;
		overflow-wrap: anywhere;
	}
	.prompt .right {
		color: #3f8a4a;
	}
	.prompt .wrong {
		color: #d33b3b;
		text-decoration: underline;
	}
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .prompt .right {
			color: #7bd488;
		}
		:global(:root:not([data-theme='light'])) .prompt .wrong {
			color: #ff8a8a;
		}
	}
	:global(:root[data-theme='dark']) .prompt .right {
		color: #7bd488;
	}
	:global(:root[data-theme='dark']) .prompt .wrong {
		color: #ff8a8a;
	}
	.entry {
		width: 100%;
		border: 0;
		border-bottom: 2px solid var(--kk-line-strong);
		background: transparent;
		padding: 0.35rem 0.1rem;
		font-size: 1.15rem;
		font-weight: 700;
		color: var(--kk-text);
		outline: none;
	}
	.entry:focus {
		border-bottom-color: var(--kk-accent);
	}
	.grid small {
		display: block;
		font-size: 0.65rem;
		font-weight: 800;
		color: var(--kk-muted);
	}
	.grid b {
		font-size: 1.25rem;
		font-weight: 800;
	}
	.grid i {
		display: block;
		font-size: 0.65rem;
		font-style: normal;
		font-weight: 700;
		color: var(--kk-muted);
	}
</style>
