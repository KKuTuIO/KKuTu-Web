<script>
	import { onMount } from 'svelte';
	import { fade, fly } from 'svelte/transition';
	import Icon from './Icon.svelte';
	import Dialog from './Dialog.svelte';
	import RewardChip from './RewardChip.svelte';
	import ObtainScreen from './ObtainScreen.svelte';
	import JjoFrame from './JjoFrame.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { playGameBgm, playLobbyBgm, playResultBgm, playSound } from '../audio.svelte.js';
	import { haptic } from '../haptics.js';
	import { levelUp } from '../levelUp.svelte.js';
	import { reducedMotion } from '../motion.js';
	import { t } from '../i18n.svelte.js';
	import { ERROR_KEYS, MAX_PICK, PITCH, SIZE, clock, msLeft, preview } from '../sokssok.js';

	/**
	 * 미니 솎솎 (the arcade): a board of 6×6 jamo tiles. The player taps tiles one after another to fill the input, and a send that spells a word of
	 * the dictionary takes those tiles off; the ones above fall and new ones drop in from the top. The server keeps the board and judges every send
	 * (this only draws it and sends the places of the tapped tiles); fifteen words in the time is what pays.
	 * @type {{store:any, onback:()=>void}}
	 */
	let { store, onback } = $props();

	const sok = $derived(store.net.sok);
	const session = $derived(sok.session);
	const summary = $derived(sok.summary);
	const board = $derived(session?.board ?? []);
	const found = $derived(session?.found ?? []);
	const over = $derived(session?.status === 'over');
	const minWords = $derived(summary?.minWords ?? 15);
	let picks = $state(/** @type {number[]} */ ([]));
	let waiting = $state(false);
	let now = $state(Date.now());
	let helpOpen = $state(false);
	let rewardOpen = $state(false);
	/** the word that has just gone through, shown in the input for a moment (a toast for each one would pile up over the board) */
	let flash = $state('');
	let waitTimer;
	let flashTimer;
	let lastFinish = 0;
	const left = $derived(msLeft(session, now));
	const tiles = $derived(picks.map((i) => board[i]?.j ?? ''));
	const spelled = $derived(preview(tiles));
	const reward = $derived(over ? sok.last?.reward : null);

	// What the server answered: the picks are always cleared (after a word the board has changed; after a refusal the player starts again).
	let seen = 0;
	let wasPlaying = null; // null until the first round is seen: a round that was already over when the screen opened is not "ended" now
	$effect(() => {
		const last = sok.last;
		if (!last || last.at === seen) return;
		seen = last.at;
		waiting = false;
		picks = [];
		if (last.error) {
			if (last.error !== 'none' && last.error !== 'early') toast(t(ERROR_KEYS[last.error] ?? ERROR_KEYS.invalid), { kind: 'info', icon: 'spellcheck' });
			if (last.error !== 'none' && last.error !== 'early') playSound('fail');
		} else if (last.accepted) {
			playSound('K2');
			haptic(10);
			flash = t('play.sok.accepted', { word: last.accepted.word, score: last.accepted.score });
			clearTimeout(flashTimer);
			flashTimer = setTimeout(() => (flash = ''), 1400);
			if (found.length === minWords) {
				playSound('mission');
				toast(t('play.sok.goal'), { kind: 'info', icon: 'emoji_events' });
			}
		}
	});
	$effect(() => {
		const playing = session ? session.status === 'playing' : null;
		if (wasPlaying === true && playing === false) {
			// the round ended (the server settled it): the sound says how it went, and the reward is shown once any level-up has been
			playSound(reward ? 'success' : found.length >= minWords ? 'success' : 'timeout');
			playResultBgm();
			if (reward) setTimeout(() => levelUp.whenIdle(() => (rewardOpen = true)), 400);
		}
		wasPlaying = playing;
	});

	// the music of a round is 자퀴's, and the faster one for the last ten seconds (as in the quiz rounds)
	$effect(() => {
		if (session?.status === 'playing') playGameBgm(left > 0 && left <= 10_000 ? 'JaqwiFastBGM' : 'JaqwiBGM');
	});

	onMount(() => {
		store.sokStart();
		store.ui.hideFab = true; // the chat button would sit on the board
		try {
			if (!localStorage.getItem('kk-sok-help')) {
				helpOpen = true;
				localStorage.setItem('kk-sok-help', '1');
			}
		} catch {
			/* no storage: the help is only a button then */
		}
		const tick = setInterval(() => {
			now = Date.now();
			// the time is up: tell the server (again every second and a half until it has closed the round)
			if (session?.status === 'playing' && msLeft(session, now) === 0 && now - lastFinish > 1500) {
				lastFinish = now;
				store.sokFinish();
			}
		}, 100);
		return () => {
			clearInterval(tick);
			clearTimeout(waitTimer);
			clearTimeout(flashTimer);
			store.ui.hideFab = false;
			playLobbyBgm({ fromResult: over }); // the lobby's music comes back (crossfaded from the result's if the round had ended)
		};
	});

	function tap(i) {
		if (over || !session || left === 0) return;
		const at = picks.indexOf(i);
		if (at >= 0) {
			picks = picks.filter((p) => p !== i);
			haptic(4);
			return;
		}
		if (picks.length >= MAX_PICK) return;
		picks = [...picks, i];
		playSound('As2');
		haptic(6);
	}
	function erase() {
		if (picks.length) {
			picks = picks.slice(0, -1);
			haptic(4);
		}
	}
	function send() {
		if (over || waiting || picks.length < 2 || left === 0) return;
		waiting = true;
		haptic(12);
		store.sokSubmit(picks);
		// an error packet (the server was busy, the account is in a game) says nothing about the round: do not stay waiting for it
		clearTimeout(waitTimer);
		waitTimer = setTimeout(() => (waiting = false), 6000);
	}
	function again() {
		picks = [];
		store.sokStart(true);
	}
	const keepFocus = (e) => e.preventDefault();

	function onkey(e) {
		if (helpOpen || e.ctrlKey || e.metaKey || e.altKey) return;
		const el = /** @type {HTMLElement|null} */ (e.target);
		if (el && el.id !== 'kk-haptic' && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable)) return;
		if (e.key === 'Enter') {
			if (el?.tagName === 'BUTTON') return; // a focused button takes its own Enter
			e.preventDefault();
			send();
		} else if (e.key === 'Backspace') {
			e.preventDefault();
			erase();
		}
	}

	const recent = $derived([...found].reverse());
	const goal = $derived(found.length >= minWords);
</script>

<svelte:window onkeydown={onkey} />

{#if reward}
	<ObtainScreen bind:open={rewardOpen} title={t('play.obtain.acquired')} items={[{ key: 'exp', q: reward.exp }, { key: 'ping', q: reward.ping }]} shop={store.shop} details={{}} ribbon ambient="shine" palette="teal" />
{/if}

<Dialog bind:open={helpOpen} title={t('play.sok.help.title')}>
	<div class="flex flex-col gap-3">
		<p class="text-sm leading-relaxed font-medium">{t('play.sok.help.intro')}</p>
		<ul class="flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed font-medium text-(color:--kk-muted)">
			<li>{t('play.sok.help.rule.words')}</li>
			<li>{t('play.sok.help.rule.goal', { time: summary?.time ?? 120, min: minWords })}</li>
			<li>{t('play.sok.help.rule.split')}</li>
		</ul>
		<button type="button" onclick={() => (helpOpen = false)} class="hw kk-btn kk-btn-primary min-h-11 font-extrabold">{t('play.sok.help.ok')}</button>
	</div>
</Dialog>

<div class="flex min-h-0 flex-1 flex-col">
	<div class="flex items-center gap-3 px-3 py-2">
		<button type="button" aria-label={t('play.sok.back')} onclick={onback} class="round"><Icon name="arrow_back" size={24} /></button>
		<h2 class="title text-xl font-extrabold">{t('play.sok.title')}</h2>
		<button type="button" aria-label={t('play.sok.helpButton')} onclick={() => (helpOpen = true)} class="round ml-auto"><Icon name="help" size={24} /></button>
	</div>

	<div class="flex min-h-0 flex-1 flex-col items-center gap-3 overflow-y-auto px-3 pb-3">
		<JjoFrame
			label={t('play.sok.board')}
			reserve={340}
			top={{ pct: Math.min(100, (found.length / minWords) * 100), text: t('play.sok.count', { n: found.length, min: minWords }) }}
			bottom={{ pct: over ? 0 : (left / ((summary?.time ?? 120) * 1000)) * 100, text: clock(over ? 0 : left), extreme: !over && left > 0 && left <= 10_000 }}
		>
			<div class="board" role="grid" aria-label={t('play.sok.board')}>
				{#each board as tile, i (tile.id)}
					{@const order = picks.indexOf(i)}
					<button
						type="button"
						class="tile {order >= 0 ? 'on' : ''}"
						style:left="{(i % SIZE) * PITCH}px"
						style:top="{Math.floor(i / SIZE) * PITCH}px"
						disabled={over}
						onmousedown={keepFocus}
						onclick={() => tap(i)}
						in:fly={{ y: -260, duration: reducedMotion() ? 0 : 380 }}
						out:fade={{ duration: reducedMotion() ? 0 : 120 }}
					>
						{tile.j}{#if order >= 0}<i class="ord">{order + 1}</i>{/if}
					</button>
				{/each}
			</div>
		</JjoFrame>

		{#if over}
			<div class="panel flex w-full max-w-md flex-col items-center gap-3 p-3">
				<b class="text-base font-extrabold">{t('play.sok.over')}</b>
				{#if reward}
					<div class="flex flex-wrap items-center justify-center gap-1.5"><RewardChip reward={{ kind: 'xp', q: reward.exp }} size={36} /><RewardChip reward={{ kind: 'ping', q: reward.ping }} size={36} /></div>
				{:else if sok.last?.unpaid === 'few'}
					<p class="text-sm font-bold text-(color:--kk-muted)">{t('play.sok.unpaid.few', { min: minWords })}</p>
				{:else if sok.last?.unpaid === 'cap'}
					<p class="text-sm font-bold text-(color:--kk-muted)">{t('play.sok.unpaid.cap')}</p>
				{/if}
				<button type="button" onclick={again} class="hw kk-btn kk-btn-hero kk-btn-lg w-full"><span class="kk-btn-icon"><Icon name="refresh" size={28} fill /></span><span class="kk-btn-label">{t('play.sok.again')}</span></button>
				{#if summary?.reward.pay}<p class="text-xs font-bold text-(color:--kk-muted)">{t('play.sok.left', { n: summary.left })}</p>{/if}
			</div>
		{:else}
			<div class="panel flex w-full max-w-md flex-col gap-2 p-3">
				<div class="input" aria-label={t('play.sok.input')}>
					<span class="spelled" class:empty={!spelled && !flash} class:flash={!spelled && !!flash}>{spelled || flash || t('play.sok.input.empty')}</span>
					<button type="button" class="key" aria-label={t('play.sok.key.delete')} onmousedown={keepFocus} onclick={erase} disabled={!picks.length}><Icon name="backspace" size={22} /></button>
				</div>
				<button type="button" onmousedown={keepFocus} onclick={send} disabled={picks.length < 2 || waiting} class="hw kk-btn kk-btn-primary min-h-12 w-full font-extrabold">{t('play.sok.key.send')}</button>
			</div>
		{/if}

		<div class="panel flex w-full max-w-md flex-col gap-2 p-3">
			<div class="flex items-center justify-between text-xs font-extrabold text-(color:--kk-muted)">
				<b>{t('play.sok.found.title')}</b>
				<span class="tabular-nums">{t('play.sok.score', { n: session?.score ?? 0 })}</span>
			</div>
			{#if recent.length}
				<div class="flex flex-wrap gap-1.5">
					{#each recent as f (f.word)}
						<button type="button" class="word" onclick={() => (store.ui.dictRequest = f.word)}>{f.word}<small class="tabular-nums">{f.score}</small></button>
					{/each}
				</div>
			{:else}
				<p class="text-xs font-bold text-(color:--kk-muted)">{t('play.sok.found.empty')}</p>
			{/if}
		</div>
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
		flex: none; /* in the screen's column a panel keeps its own height (a flex item would be squeezed once the frog and the keys take the room) */
		border-radius: 20px;
		color: var(--kk-text);
		background: color-mix(in srgb, var(--kk-surface) 94%, transparent);
		box-shadow: 0 3px 6px rgb(20 40 80 / 0.22);
	}
	/* the board is drawn at the frog's own size (the frame scales it): 44 px tiles 6 px apart fill his 298 px mouth. Tiles are placed by their place
	   (left, top), so one that falls slides to its new place, and a new one flies in from above */
	.board {
		position: relative;
		margin: 2px;
		width: 294px;
		height: 294px;
	}
	.tile {
		position: absolute;
		display: grid;
		place-items: center;
		width: 44px;
		height: 44px;
		border-radius: 10px;
		border: 2px solid var(--kk-line-strong);
		background: var(--kk-surface);
		color: var(--kk-text);
		font-size: 22px;
		font-weight: 800;
		box-shadow: 0 2px 0 var(--kk-line-strong);
		transition: top 0.3s cubic-bezier(0.3, 0.9, 0.4, 1), left 0.3s cubic-bezier(0.3, 0.9, 0.4, 1), background 0.12s, border-color 0.12s;
	}
	.tile.on {
		background: var(--kk-accent);
		border-color: var(--kk-accent);
		color: #fff;
		box-shadow: 0 2px 0 color-mix(in srgb, var(--kk-accent) 60%, black);
	}
	.tile:disabled {
		opacity: 0.6;
	}
	.ord {
		position: absolute;
		top: -1px;
		right: 2px;
		font-size: 0.6rem;
		font-style: normal;
		font-weight: 800;
		opacity: 0.85;
	}
	.input {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		min-height: 3rem;
		border-radius: 12px;
		border: 2px solid var(--kk-line);
		background: var(--kk-surface-2);
		padding: 0.25rem 0.25rem 0.25rem 0.9rem;
	}
	.spelled {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: 1.35rem;
		font-weight: 800;
	}
	.spelled.flash {
		font-size: 1.05rem;
		color: #3f8a4a;
	}
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .spelled.flash {
			color: #7bd488;
		}
	}
	:global(:root[data-theme='dark']) .spelled.flash {
		color: #7bd488;
	}
	.spelled.empty {
		font-size: 0.85rem;
		font-weight: 700;
		color: var(--kk-muted);
	}
	.key {
		display: grid;
		place-items: center;
		flex: none;
		width: 2.5rem;
		height: 2.5rem;
		border-radius: 8px;
		background: var(--kk-surface);
		color: var(--kk-text);
		box-shadow: 0 2px 0 var(--kk-line-strong);
	}
	.key:disabled {
		opacity: 0.5;
	}
	.word {
		display: inline-flex;
		align-items: baseline;
		gap: 0.3rem;
		border-radius: 999px;
		border: 1.5px solid var(--kk-line);
		background: var(--kk-surface-2);
		padding: 0.15rem 0.65rem;
		font-size: 0.9rem;
		font-weight: 800;
	}
	.word small {
		font-size: 0.65rem;
		color: var(--kk-muted);
	}
	@media (prefers-reduced-motion: reduce) {
		.tile {
			transition: none;
		}
	}
</style>
