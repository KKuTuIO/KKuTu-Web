<script>
	import { onMount } from 'svelte';
	import Icon from './Icon.svelte';
	import GameIcon from './GameIcon.svelte';
	import PriceChip from './PriceChip.svelte';
	import JjoFrame from './JjoFrame.svelte';
	import Dialog from './Dialog.svelte';
	import RewardChip from './RewardChip.svelte';
	import ObtainScreen from './ObtainScreen.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import { playSound } from '../audio.svelte.js';
	import { haptic } from '../haptics.js';
	import { levelUp } from '../levelUp.svelte.js';
	import { reducedMotion } from '../motion.js';
	import { t } from '../i18n.svelte.js';
	import { ERROR_KEYS, KEY_ROWS, LENGTH, MAX_TRIES, countdown, dailyNumber, jamoOfKey, keyMarks, shareText, typeJamo } from '../riddle.js';

	/**
	 * 리들 (the arcade): six jamo, six tries. The server holds the answer and judges every try; this draws the board, takes the typing (the
	 * on-screen keys, or the physical keyboard by the place of the key) and shows what the server answered. `mode` is the daily puzzle or a free one.
	 * A try is shown the way Wordle does: its six tiles turn over one after another, and show their colour on the far side. Everything that would give
	 * the answer away (the keyboard's colours, the result, the sound of success, the reward) waits until the last tile has turned.
	 * @type {{store:any, mode:'daily'|'free', onback:()=>void}}
	 */
	let { store, mode, onback } = $props();

	const FLIP_MS = 600; // one tile's turn
	const STEP_MS = 280; // the gap between two tiles starting to turn

	const riddle = $derived(store.net.riddle);
	const board = $derived(riddle.boards[mode]);
	const summary = $derived(riddle.summary);
	const rows = $derived(board?.rows ?? []);
	let typed = $state('');
	let waiting = $state(false);
	let now = $state(Date.now());
	let waitTimer;
	let revealTimer;
	let rewardOpen = $state(false);
	let helpOpen = $state(false);
	let hintOpen = $state(false);
	// the letters on the board can be hidden (a screenshot or a stream must not give the word away): the tiles keep their colours; the choice is kept
	let hideLetters = $state(false);
	try {
		hideLetters = localStorage.getItem('kk-riddle-hide') === '1';
	} catch {
		/* no storage */
	}
	function toggleLetters() {
		hideLetters = !hideLetters;
		haptic(6);
		try {
			localStorage.setItem('kk-riddle-hide', hideLetters ? '1' : '0');
		} catch {
			/* no storage */
		}
	}
	/** the row whose tiles are turning now (-1: none) */
	let flipRow = $state(-1);
	let revealing = $state(false);
	const over = $derived(!!board && board.status !== 'playing' && !revealing);

	const revealMs = () => (reducedMotion() ? 0 : (LENGTH - 1) * STEP_MS + FLIP_MS);

	// The server's answer ends the wait; a try that did not count is said once; one that did is answered with a sound.
	let seen = 0;
	let hintAsked = false;
	$effect(() => {
		const last = riddle.last;
		if (!last || last.at === seen) return;
		seen = last.at;
		waiting = false;
		if (last.mode !== mode) return;
		// the answer to the hint button is not a try: the board only gains its hint
		if (hintAsked) {
			hintAsked = false;
			if (last.error === 'nohint' || last.error === 'poor') toast(t(ERROR_KEYS[last.error]), { kind: 'info', icon: 'lightbulb' });
			return;
		}
		if (last.error) {
			if (last.error !== 'done') {
				toast(t(ERROR_KEYS[last.error] ?? ERROR_KEYS.invalid), { kind: 'info', icon: 'spellcheck' });
				playSound('fail');
			}
			return;
		}
		typed = '';
		// K2 when any jamo matched (in place or elsewhere), fail when none did; success waits for the turn to end
		const row = board?.rows[board.rows.length - 1];
		if (board?.status === 'won') {
			setTimeout(() => {
				playSound('success');
				// the level-up (if the experience brought one) is announced first
				if (last.reward) setTimeout(() => levelUp.whenIdle(() => (rewardOpen = true)), 400);
			}, revealMs());
		} else playSound(row && /[cp]/.test(row.m) ? 'K2' : 'fail');
	});

	// A row that appears after the board was first seen is turned; the rows that were already there are not.
	let shown = -1;
	$effect(() => {
		if (!board) return;
		const n = rows.length;
		if (shown < 0 || n < shown) return void (shown = n);
		if (n === shown) return;
		shown = n;
		const total = revealMs();
		if (!total) return;
		flipRow = n - 1;
		revealing = true;
		clearTimeout(revealTimer);
		revealTimer = setTimeout(() => {
			revealing = false;
			flipRow = -1;
		}, total + 50);
	});

	onMount(() => {
		if (mode === 'daily') store.riddleOpen('daily');
		else store.riddleNew();
		store.ui.hideFab = true; // the chat button would sit on the keys
		try {
			if (!localStorage.getItem('kk-riddle-help')) {
				helpOpen = true;
				localStorage.setItem('kk-riddle-help', '1');
			}
		} catch {
			/* no storage: the help is only a button then */
		}
		const tick = setInterval(() => (now = Date.now()), 1000);
		return () => {
			clearInterval(tick);
			clearTimeout(waitTimer);
			clearTimeout(revealTimer);
			store.ui.hideFab = false;
		};
	});

	// what the keyboard shows leaves out the row that is still turning
	const marks = $derived(keyMarks(revealing ? rows.slice(0, -1) : rows));
	const reward = $derived(riddle.last?.mode === mode && over ? riddle.last.reward : null);

	function add(jamo) {
		if (over || waiting || revealing || !board) return;
		const next = typeJamo(typed, jamo);
		if (next === typed) return;
		typed = next;
		playSound('As2');
		haptic(6);
	}
	function erase() {
		if (over || waiting || revealing || !typed) return;
		typed = typed.slice(0, -1);
		haptic(6);
	}
	function submit() {
		if (over || waiting || revealing || !board) return;
		if (typed.length < LENGTH) {
			playSound('fail');
			return void toast(t(ERROR_KEYS.invalid), { kind: 'info', icon: 'spellcheck' });
		}
		haptic(12);
		waiting = true;
		store.riddleGuess(mode, typed);
		// an error packet (the server was busy, the account is in a game) says nothing about the riddle: do not stay waiting for it
		clearTimeout(waitTimer);
		waitTimer = setTimeout(() => (waiting = false), 6000);
	}
	/** the small dictionary, opened on the answer (it is only known once the puzzle is over) */
	function wordInfo() {
		if (board?.answer) store.ui.dictRequest = board.answer;
	}
	async function share() {
		const text = shareText({ rows, won: board?.status === 'won', number: mode === 'daily' && summary ? dailyNumber(summary.resetAt) : null, title: t('play.riddle.free'), hint: !!board?.hint, paid: !!board?.paid });
		try {
			await navigator.clipboard.writeText(text);
			toast(t('play.riddle.copied'), { kind: 'info', icon: 'content_copy' });
		} catch {
			toast(t('play.riddle.copyFailed'), { kind: 'info', icon: 'content_copy' });
		}
	}
	const money = $derived(store.net.users[store.net.id]?.money ?? 0);
	const cost = $derived(summary?.hintCost ?? 0);
	const canHint = $derived(!!board && !over && !revealing && !waiting);
	function hint() {
		if (!canHint || board.hint) return;
		hintOpen = false;
		hintAsked = true;
		playSound('mission');
		haptic(10);
		store.riddleHint(mode);
	}
	function buyHint() {
		if (!canHint || board.paid || money < cost) return;
		hintOpen = false;
		hintAsked = true;
		playSound('mission');
		haptic(14);
		store.riddleBuyHint(mode);
	}
	/**
	 * The way out of a free puzzle is a give-up (GG): once a try has been made, leaving asks first, and the server then drops the puzzle (its word
	 * is not kept for a later visit). The daily keeps what was tried, so leaving it asks nothing.
	 */
	async function leave() {
		if (mode === 'free' && board?.status === 'playing') {
			if (rows.length && !(await confirm({ message: t('play.riddle.leave.confirm'), ok: t('play.riddle.leave.ok'), cancel: t('play.riddle.leave.cancel') }))) return;
			store.riddleGiveUp();
		}
		onback();
	}
	function again() {
		typed = '';
		store.riddleNew(true);
	}

	// a key pressed with the mouse must not keep the focus: Enter would press it again instead of sending the try
	const keepFocus = (e) => e.preventDefault();

	function onkey(e) {
		if (helpOpen) return;
		const el = /** @type {HTMLElement|null} */ (e.target);
		// (the haptics' hidden checkbox is not a field the player types in)
		if (el && el.id !== 'kk-haptic' && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable)) return;
		if (e.ctrlKey || e.metaKey || e.altKey) return;
		if (e.key === 'Enter') {
			if (el?.tagName === 'BUTTON') return; // a focused button takes its own Enter
			e.preventDefault();
			return over ? undefined : submit();
		}
		if (e.key === 'Backspace') {
			e.preventDefault();
			return erase();
		}
		const jamo = jamoOfKey(e.code, e.shiftKey);
		if (jamo) {
			e.preventDefault();
			add(jamo);
		}
	}

	const title = $derived(t(mode === 'daily' ? 'play.riddle.daily' : 'play.riddle.free'));
	const streak = $derived(summary?.streak ?? 0);
	const left = $derived(mode === 'free' && summary ? summary.free.left : 0);
	const tries = $derived(rows.length);
	const status = $derived(
		!board || revealing
			? ''
			: board.status === 'won'
				? t('play.riddle.status.won', { n: tries })
				: board.status === 'lost'
					? t('play.riddle.status.lost', { word: hideLetters ? '○'.repeat(board.answer.length) : board.answer })
					: t('play.riddle.status.playing', { n: tries + 1, max: MAX_TRIES })
	);

	// the help's examples: a row of jamo with one tile of each colour
	const EXAMPLES = [
		{ j: 'ㅍㅗㅎㅗㅣㄱ', m: 'c-----', text: 'play.riddle.help.ex.correct' },
		{ j: 'ㅅㅅㅏㅇㅜㅁ', m: '--p---', text: 'play.riddle.help.ex.present' },
		{ j: 'ㄱㅣㅅㅡㄹㄱ', m: '---a--', text: 'play.riddle.help.ex.absent' }
	];
</script>

<svelte:window onkeydown={onkey} />

{#if reward}
	<ObtainScreen bind:open={rewardOpen} title={t('play.obtain.acquired')} subtitle={reward.bonusPercent > 0 ? t('play.riddle.streakBonus', { n: streak, percent: reward.bonusPercent }) : ''} items={[{ key: 'exp', q: reward.exp }, { key: 'ping', q: reward.ping }]} shop={store.shop} details={{}} ribbon ambient="shine" palette="teal" />
{/if}

<Dialog bind:open={helpOpen} title={t('play.riddle.help.title')}>
	<div class="flex max-h-[68dvh] flex-col gap-3 overflow-y-auto">
		<p class="text-sm leading-relaxed font-medium">{t('play.riddle.help.intro')}</p>
		{#each EXAMPLES as ex (ex.j)}
			<div class="flex flex-col items-center gap-1">
				<div class="grid grid-cols-6 gap-1">
					{#each [...ex.j] as ch, i (i)}<span class="tile tile-sm" data-m={ex.m[i] === '-' ? '' : ex.m[i]} data-filled="true">{ch}</span>{/each}
				</div>
				<p class="text-xs font-bold text-(color:--kk-muted)">{t(ex.text)}</p>
			</div>
		{/each}
		<ul class="flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed font-medium text-(color:--kk-muted)">
			<li>{t('play.riddle.help.rule.split')}</li>
			<li>{t('play.riddle.help.rule.words')}</li>
			<li>{t(mode === 'daily' ? 'play.riddle.help.rule.daily' : 'play.riddle.help.rule.free')}</li>
		</ul>
		<button type="button" onclick={() => (helpOpen = false)} class="hw kk-btn kk-btn-primary min-h-11 font-extrabold">{t('play.riddle.help.ok')}</button>
	</div>
</Dialog>

<Dialog bind:open={hintOpen} title={t('play.riddle.hint.title')}>
	<div class="flex flex-col gap-3">
		<div class="hint-card">
			<div class="flex flex-col gap-0.5">
				<b class="text-sm font-extrabold">{t('play.riddle.hint.free.title')}</b>
				<span class="text-xs font-medium text-(color:--kk-muted)">{t('play.riddle.hint.free.desc')}</span>
			</div>
			<button type="button" onclick={hint} disabled={!canHint || !!board?.hint} class="hw kk-btn kk-btn-primary inline-flex min-h-12 shrink-0 items-center justify-center px-5 font-extrabold">{board?.hint ? t('play.riddle.hint.used') : t('play.riddle.hint.free.free')}</button>
		</div>
		<div class="hint-card">
			<div class="flex flex-col gap-0.5">
				<b class="text-sm font-extrabold">{t('play.riddle.hint.paid.title')}</b>
				<span class="text-xs font-medium text-(color:--kk-muted)">{t('play.riddle.hint.paid.desc')}</span>
			</div>
			<button type="button" onclick={buyHint} disabled={!canHint || !!board?.paid || money < cost} class="hw kk-btn kk-btn-primary inline-flex min-h-12 shrink-0 items-center justify-center gap-2.5 px-5 font-extrabold">
				{#if board?.paid}{t('play.riddle.hint.used')}{:else}{t('play.riddle.hint.paid.buy')}<PriceChip price={cost} lacking={money < cost} />{/if}
			</button>
		</div>
		{#if !board?.paid && money < cost}<p class="text-center text-xs font-bold text-(color:--kk-muted)">{t('play.riddle.error.poor')}</p>{/if}
	</div>
</Dialog>

<div class="flex min-h-0 flex-1 flex-col">
	<div class="flex items-center gap-3 px-3 py-2">
		<button type="button" aria-label={t('play.riddle.back')} onclick={leave} class="round"><Icon name="arrow_back" size={24} /></button>
		<h2 class="title text-xl font-extrabold">{title}</h2>
		{#if mode === 'daily' && streak > 0}<span class="chip">{t('play.riddle.streak', { n: streak })}</span>{/if}
		<button type="button" aria-label={t('play.riddle.hint.button')} onclick={() => (hintOpen = true)} disabled={!canHint} class="round ml-auto disabled:opacity-50"><Icon name="lightbulb" size={24} fill={!board?.hint || !board?.paid} /></button>
		<button type="button" aria-label={t('play.riddle.helpButton')} onclick={() => (helpOpen = true)} class="round"><Icon name="help" size={24} /></button>
	</div>

	<div class="flex min-h-0 flex-1 flex-col items-center gap-3 overflow-y-auto px-3 pb-3">
		<div class="panel flex w-full max-w-md flex-col items-center gap-3 p-3">
			<!-- not `min-h-8`: the lobby's glass panel paints that class white (its header rows sit on the stage) -->
			<div class="flex h-8 items-center justify-center gap-1.5">
				<p class="text-center text-sm font-bold" aria-live="polite">{status}</p>
				{#if board}
					<button type="button" aria-label={t(hideLetters ? 'play.riddle.showLetters' : 'play.riddle.hideLetters')} aria-pressed={hideLetters} onclick={toggleLetters} class="grid size-8 shrink-0 place-items-center rounded-full text-(color:--kk-muted) transition-colors hover:bg-(--kk-surface-2)"><Icon name={hideLetters ? 'visibility_off' : 'visibility'} size={20} /></button>
				{/if}
			</div>
			{#if board?.hint && !over}<p class="hint-line flex items-center gap-1 text-xs font-bold"><Icon name="lightbulb" size={16} fill />{t('play.riddle.hint.text', { n: board.hint.place + 1, jamo: hideLetters ? '○' : board.hint.jamo })}</p>{/if}
			{#if board?.paid && !over}<p class="hint-line flex items-center gap-1 text-xs font-bold"><GameIcon name="ping" size={16} />{t('play.riddle.hint.paid.text', { range: t('play.riddle.hint.paid.range', { from: board.paid.start + 1, to: board.paid.start + board.paid.jamo.length }), text: hideLetters ? '○'.repeat(board.paid.text.length) : board.paid.text })}</p>{/if}

		</div>

		<JjoFrame label={t('play.riddle.board', { title })} reserve={470} top={{ pct: (rows.length / MAX_TRIES) * 100 }}>
			<div class="rboard {hideLetters ? 'hide-letters' : ''}" role="grid" aria-label={t('play.riddle.board', { title })}>
				{#each { length: MAX_TRIES } as _, r (r)}
					{@const row = rows[r]}
					<div class="rrow" role="row">
						{#each { length: LENGTH } as _, c (c)}
							{@const active = !row && r === rows.length && !over && !revealing}
							{@const ch = row ? row.j[c] : active ? typed[c] : ''}
							{@const ghostJamo = active && !ch ? (board?.hint?.place === c ? board.hint.jamo : board?.paid && c >= board.paid.start && c < board.paid.start + board.paid.jamo.length ? board.paid.jamo[c - board.paid.start] : '') : ''}
							{@const ghost = !!ghostJamo}
							<span role="gridcell" class="tile {r === flipRow ? 'flip' : ''} {ghost ? 'ghost' : ''}" style:--d="{c * STEP_MS}ms" data-m={row ? row.m[c] : ''} data-filled={!!ch}>{ghost ? ghostJamo : (ch ?? '')}</span>
						{/each}
					</div>
				{/each}
			</div>
		</JjoFrame>

		{#if over}
			<div class="panel flex w-full max-w-md flex-col items-center gap-3 p-3">
				{#if reward}
					<div class="flex flex-wrap items-center justify-center gap-1.5"><RewardChip reward={{ kind: 'xp', q: reward.exp }} size={36} /><RewardChip reward={{ kind: 'ping', q: reward.ping }} size={36} /></div>
					{#if reward.bonusPercent > 0}<p class="text-xs font-bold text-(color:--kk-muted)">{t('play.riddle.streakBonus', { n: streak, percent: reward.bonusPercent })}</p>{/if}
				{:else if board.status === 'won' && mode === 'free' && riddle.last?.unpaid === 'fast'}
					<p class="text-sm font-bold text-(color:--kk-muted)">{t('play.riddle.tooFast')}</p>
				{:else if board.status === 'won' && mode === 'free' && summary?.reward.pay}
					<p class="text-sm font-bold text-(color:--kk-muted)">{t('play.riddle.allPaid')}</p>
				{/if}
				<div class="flex w-full gap-2">
					<button type="button" onclick={share} class="hw kk-btn inline-flex min-h-11 flex-1 items-center justify-center gap-1.5 font-extrabold"><Icon name="content_copy" size={20} />{t('play.riddle.copy')}</button>
					<button type="button" onclick={wordInfo} class="hw kk-btn inline-flex min-h-11 flex-1 items-center justify-center gap-1.5 font-extrabold"><Icon name="menu_book" size={20} />{t('play.riddle.wordInfo')}</button>
				</div>
				{#if mode === 'free'}
					<button type="button" onclick={again} class="hw kk-btn kk-btn-hero kk-btn-lg w-full"><span class="kk-btn-icon"><Icon name="refresh" size={28} fill /></span><span class="kk-btn-label">{t('play.riddle.next')}</span></button>
					{#if summary?.reward.pay}<p class="text-xs font-bold text-(color:--kk-muted)">{t('play.riddle.left', { n: left })}</p>{/if}
				{:else if summary}
					<p class="text-sm font-bold tabular-nums">{t('play.riddle.nextIn', { time: countdown(summary.resetAt, now) })}</p>
				{/if}
			</div>
		{:else}
			<div class="keys flex w-full max-w-md flex-col gap-1.5" role="group" aria-label={t('play.riddle.keyboard')}>
				{#each KEY_ROWS as keys, i (i)}
					<div class="flex justify-center gap-1">
						{#if i === 2}<button type="button" class="key key-wide" onmousedown={keepFocus} onclick={erase}>{t('play.riddle.key.delete')}</button>{/if}
						{#each keys as k (k)}
							<button type="button" class="key" data-m={marks[k] ?? ''} onmousedown={keepFocus} onclick={() => add(k)}>{k}</button>
						{/each}
						{#if i === 2}<button type="button" class="key key-wide" onmousedown={keepFocus} onclick={submit} disabled={waiting || revealing}>{t('play.riddle.key.enter')}</button>{/if}
					</div>
				{/each}
			</div>
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
	.chip {
		border-radius: 999px;
		background: rgb(255 255 255 / 0.9);
		padding: 0.15rem 0.7rem;
		font-size: 0.8rem;
		font-weight: 800;
		color: #285d30;
	}
	.panel {
		flex: none; /* in the screen's column a panel keeps its own height (a flex item would be squeezed once the frog and the keys take the room) */
		border-radius: 20px;
		color: var(--kk-text);
		background: color-mix(in srgb, var(--kk-surface) 94%, transparent);
		box-shadow: 0 3px 6px rgb(20 40 80 / 0.22);
	}
	/* the board is drawn at the frog's own size (44 px tiles, 6 px apart, 294 px of his 298 px mouth) and the frame scales it */
	.rboard {
		display: flex;
		flex-direction: column;
		gap: 6px;
		margin: 2px;
		width: 294px;
	}
	.rrow {
		display: grid;
		grid-template-columns: repeat(6, 44px);
		gap: 6px;
	}
	.rboard .tile {
		--s: 44px;
	}
	/* (the tile of the help's examples sizes itself, `tile-sm`) */
	.tile {
		--s: 2.4rem;
		display: grid;
		place-items: center;
		width: var(--s);
		height: var(--s);
		border-radius: 10px;
		border: 2px solid var(--kk-line-strong);
		background: var(--kk-surface);
		color: var(--kk-text);
		font-size: calc(var(--s) * 0.5);
		font-weight: 800;
	}
	/* the letters go clear, the colours stay: what a screenshot shows says how the tries went but not the word */
	.hide-letters .tile {
		color: transparent !important;
	}
	/* the hint: its jamo waits in its place in the row being typed, in the green of "right place" but only drawn, not filled */
	.tile.ghost {
		border: 2px dashed #5fb86a;
		color: #5fb86a;
	}
	.hint-card {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
		border-radius: 14px;
		border: 1.5px solid var(--kk-line);
		padding: 0.75rem;
	}
	.hint-line {
		color: #3f8a4a;
	}
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .hint-line {
			color: #7bd488;
		}
	}
	:global(:root[data-theme='dark']) .hint-line {
		color: #7bd488;
	}
	.tile-sm {
		--s: 2.4rem;
	}
	.tile[data-filled='true'] {
		border-color: var(--kk-text);
	}
	[data-m='c'] {
		--fb: #5fb86a;
	}
	[data-m='p'] {
		--fb: #d9a62e;
	}
	[data-m='a'] {
		--fb: #8d97ad;
	}
	.tile[data-m='c'],
	.tile[data-m='p'],
	.tile[data-m='a'],
	.key[data-m='c'],
	.key[data-m='p'],
	.key[data-m='a'] {
		background: var(--fb);
		border-color: var(--fb);
		color: #fff;
	}
	/* a try turns its tiles one after another (--d is the tile's wait): the face that shows the colour comes up at the half turn */
	.tile.flip {
		animation: flip 0.6s ease-in-out var(--d) both;
	}
	@keyframes flip {
		0% {
			transform: perspective(400px) rotateY(0);
			background: var(--kk-surface);
			border-color: var(--kk-text);
			color: var(--kk-text);
		}
		49% {
			transform: perspective(400px) rotateY(90deg);
			background: var(--kk-surface);
			border-color: var(--kk-text);
			color: var(--kk-text);
		}
		50% {
			transform: perspective(400px) rotateY(90deg);
			background: var(--fb);
			border-color: var(--fb);
			color: #fff;
		}
		100% {
			transform: perspective(400px) rotateY(0);
			background: var(--fb);
			border-color: var(--fb);
			color: #fff;
		}
	}
	.key {
		display: grid;
		place-items: center;
		min-width: 0;
		flex: 1 1 0;
		max-width: 2.9rem;
		height: 2.75rem;
		border-radius: 8px;
		background: var(--kk-surface-2);
		color: var(--kk-text);
		font-size: 1.1rem;
		font-weight: 800;
		box-shadow: 0 2px 0 var(--kk-line-strong);
	}
	.key-wide {
		flex: 1.6 1 0;
		max-width: 4.6rem;
		font-size: 0.85rem;
	}
	.key:active:not(:disabled) {
		transform: translateY(1px);
		box-shadow: 0 1px 0 var(--kk-line-strong);
	}
	.key:disabled {
		opacity: 0.6;
	}
	@media (prefers-reduced-motion: reduce) {
		.tile.flip {
			animation: none;
		}
	}
</style>
