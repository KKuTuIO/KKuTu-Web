<script>
	import { onDestroy, onMount } from 'svelte';
	import { t, tList } from '../i18n.svelte.js';
	import { haptic } from '../haptics.js';
	import { reducedMotion } from '../motion.js';
	import { prefs } from '../prefs.svelte.js';
	import { maskBad } from '../badwords.js';
	import { nudgeOpen, closeNudge } from '../nudges.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import Nudge from './Nudge.svelte';
	import HoverWave from './HoverWave.svelte';
	import RoomList from './RoomList.svelte';
	import SingleScreen from './SingleScreen.svelte';
	import Mascot from './Mascot.svelte';
	import NickName from './NickName.svelte';
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';
	import StatusBubble from './StatusBubble.svelte';
	import StageBackdrop from './StageBackdrop.svelte';

	/**
	 * The match-centred lobby (설정 > 로비 선택), for the phone and the desktop: the school plaza as a stage, my Moremi standing on it, and one big
	 * 게임 시작! button. Pressing it freezes the backdrop (no parallax, no particles) and blurs it while the mode screen slides in: 멀티플레이
	 * (매칭 시작, 방 목록, 방 만들기), then 싱글플레이 (아케이드, 튜토리얼). 방 목록 and 아케이드 are screens of their own on the same blurred stage.
	 * `variant` is 'desk' (the stage under the top bar) or 'phone' (the whole page).
	 * @type {{store:any, ready?:boolean, variant?:'desk'|'phone', oncreate:()=>void, ontutorial?:()=>void, page?:string|null, screen?:import('svelte').Snippet<[import('svelte').Snippet]>, onpageclose?:()=>void, onjoin?:(no:number)=>void}}
	 */
	let { store, ready = true, variant = 'phone', oncreate, ontutorial, page = null, screen, onpageclose, onjoin } = $props();
	const net = $derived(store.net);
	const me = $derived(store.me);
	const parts = $derived(net.meta.moremiParts);
	const status = $derived(me?.statusMessage ?? '');
	const mask = (s) => (prefs.filter === 'off' ? s : maskBad(s));
	// A standing Moremi is only the character: the 배경 (back) and 휘장 (badge) items would break that, so the lobby leaves them out.
	const HIDE = ['back', 'badge'];
	let poke = $state(null);
	let pokes = 0;
	let body = $state();
	let timer;
	function press() {
		haptic(14);
		const lines = tList('play.home.poke');
		if (lines.length) {
			let text = lines[Math.floor(Math.random() * lines.length)];
			while (lines.length > 1 && text === poke?.text) text = lines[Math.floor(Math.random() * lines.length)];
			poke = { text, n: ++pokes };
			clearTimeout(timer);
			timer = setTimeout(() => (poke = null), 5000);
		}
		if (!reducedMotion())
			body?.animate(
				[{ transform: 'scale(1, 1)' }, { transform: 'scale(1.06, 0.8)', offset: 0.28 }, { transform: 'scale(0.97, 1.08)', offset: 0.6 }, { transform: 'scale(1, 1)' }],
				{ duration: 440, easing: 'ease-out' }
			);
	}

	// ---- 게임 시작: idle -> select. The backdrop stops and blurs while the mode screen slides in: one gesture, both at once. ----
	let phase = $state('idle');
	let stageEl = $state();
	let settled = $state(false); // the mode screen has finished sliding in
	$effect(() => {
		if (phase !== 'select') return void (settled = false);
		const id = setTimeout(() => (settled = true), 650);
		return () => clearTimeout(id);
	});
	function start() {
		haptic(10);
		phase = 'select';
	}
	// The room list and the arcade replace the mode screen at once (it is not animated out); the blurred stage stays behind them.
	const sub = $derived(phase === 'rooms' || phase === 'arcade' || phase === 'page');
	// 공지·행사, 사전 and 리플레이 are pages of the top bar: the page says which one is open, and the stage shows it on the blurred picture
	$effect(() => {
		if (page) phase = 'page';
		else if (phase === 'page') phase = 'idle';
	});
	function back() {
		if (phase === 'page') return onpageclose?.();
		// 공지·행사 is opened from the stage itself, so it goes back to the stage; 방 목록 and 아케이드 go back to the mode screen
		phase = phase === 'rooms' || phase === 'arcade' ? 'select' : 'idle';
	}
	// The stage's own element carries the state as CSS variables (the backdrop reads --stage-blur, --stage-zoom and --play, the Moremi --mshift),
	// and the layered backdrop freezes its parallax on 'stagepause'.
	$effect(() => {
		const el = stageEl;
		if (!el) return;
		const on = phase !== 'idle';
		el.style.setProperty('--stage-blur', on ? '12px' : '0px');
		el.style.setProperty('--stage-zoom', on ? '1.06' : '1');
		el.style.setProperty('--play', on ? 'paused' : 'running');
		el.style.setProperty('--mshift', phase === 'select' ? '1' : '0');
		el.dispatchEvent(new CustomEvent(on ? 'stagepause' : 'stageresume'));
	});
	// the tutorial's tour opens and closes the mode screen through this hook
	onMount(() => {
		store.hooks.openModes = (open) => (phase = open ? 'select' : 'idle');
		return () => delete store.hooks.openModes;
	});
	onDestroy(() => clearTimeout(timer));

	// Each card has a light-to-bold colour pair and the dark ink of its hue for the label (colours taken from the reference cards, the rest at the same lightness and chroma).
	const multi = $derived([
		{ label: t('play.lobby.matchStart'), icon: 'groups', c: ['#63d49e', '#cff28c', '#285d30'], act: () => store.hooks.openQuick(2), off: !ready, tour: 'quick-match' },
		{ label: t('play.home.rooms'), icon: 'meeting_room', c: ['#64c7c3', '#a2f4f4', '#005f5c'], act: () => (phase = 'rooms'), tour: 'room-list' },
		{ label: t('play.lobby.createRoom'), icon: 'add', c: ['#7bbde8', '#b6eaff', '#09567c'], act: oncreate, off: !ready, tour: 'create-room' },
		{ label: t('play.lobby.trophyLeague'), icon: 'emoji_events', c: ['#f6a65c', '#fee273', '#73420c'], act: () => toast(t('play.lobby.trophyLeagueTbd'), { kind: 'info', icon: 'event' }) }
	]);
	// 싱글플레이 is a section of its own: 아케이드 and 튜토리얼 side by side.
	const arcade = $derived({ label: t('play.lobby.arcade'), icon: 'sports_esports', c: ['#b2aeff', '#f0ceff', '#4b4780'], act: () => (phase = 'arcade'), off: !ready });
	// The help nudge ("플레이 방법이 궁금하지 않아?") lives here: while it is open the shine and the squish go to 튜토리얼 and 게임 시작 keeps still.
	const helpNudge = $derived(nudgeOpen('tutorial') && ready);
	const tutorial = $derived({
		label: t('play.lobby.tutorial'),
		icon: 'school',
		c: ['#fa96c3', '#ffbccb', '#753855'],
		act: () => (closeNudge('tutorial'), ontutorial?.()),
		tour: 'tutorial',
		nudge: helpNudge
	});
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && phase !== 'idle' && !e.defaultPrevented && !document.querySelector('dialog[open]') && back()} />

{#snippet remi(size)}
	<!-- one box the size of the Moremi: the balloon sits right above its head and the nickname plate right under its feet, at any stage size -->
	<div class="relative shrink-0" style:width="{size}px" style:height="{size}px">
		<button type="button" bind:this={body} onclick={press} aria-label={t('play.home.poke')} style:filter="brightness(calc(1 - var(--night, 0) * 0.1))" class="relative origin-bottom rounded-2xl">
			<Moremi equip={me?.equip ?? {}} shop={store.shop} {parts} {size} label={me?.name ?? ''} frame={false} hide={HIDE} />
		</button>
		{#if me}
			<div class="absolute -inset-x-20 h-0" style:top="-5.4rem">
				{#key poke?.n ?? 0}
					{#if poke}<StatusBubble message={poke.text} />
					{:else if status}<StatusBubble message={status} isMe {mask} onsave={(m) => store.setStatusMessage(m)} />
					{:else}<StatusBubble message={t('play.home.greeting', { name: me.name })} />{/if}
				{/key}
			</div>
			<span class="nick pointer-events-none absolute top-full left-1/2 mt-1 -translate-x-1/2 rounded-full px-3 py-0.5 text-sm font-extrabold whitespace-nowrap"><NickName user={me} /></span>
		{/if}
	</div>
{/snippet}

<!-- the big button: a shine sweeps over it now and then (the enchant button's), and a checkerboard drifts in under it on hover -->
{#snippet startButton(cls)}
	<button type="button" disabled={!ready} data-tour="start" onclick={start} class="hw start kk-btn kk-btn-hero {cls} {helpNudge ? 'calm' : ''}">
		<span class="start-check" aria-hidden="true"><span class="kk-checker"></span></span>
		<Icon name="play_arrow" size={34} fill />
		<span class="mlabel {cls.includes('flat') ? 'text-[1.5rem]' : 'text-[1.8rem]'}"><HoverWave text={t('play.lobby.start')} /></span>
	</button>
{/snippet}

{#snippet card(i, m, big, span = false)}
	<button type="button" disabled={m.off} data-tour={m.tour} onclick={m.act} style:--i={i} style:--c1={m.c[0]} style:--c2={m.c[1]} style:--ink={m.c[2]} class="hw mcard {big ? 'mcard-big' : 'mcard-sm'} {span ? 'col-span-2' : ''} {m.nudge ? 'nudge' : ''}">
		<span class="mcard-check" aria-hidden="true"><span class="kk-checker"></span></span>
		<Icon name={m.icon} size={big ? 52 : 34} fill class={big ? 'max-md:!text-[42px]' : 'max-md:!text-[28px]'} />
		<span class="mlabel {big ? 'text-[1.85rem] max-md:text-[1.45rem]' : 'text-[1.45rem] max-md:text-[1.1rem]'}"><HoverWave text={m.label} /></span>
	</button>
{/snippet}

<div bind:this={stageEl} data-stage class="relative isolate flex min-h-0 flex-1 flex-col overflow-clip rounded-2xl max-md:rounded-none">
<StageBackdrop />
{#if variant === 'desk'}
	<!-- the whole stage (the area the room list used to fill): the buildings stand at the two edges, me in the middle on the plaza (the mode screen
	     moves me to the left), the one big button in the lower right -->
	<div class="@container relative z-10 min-h-0 flex-1 overflow-hidden">
		<div data-stage-moremi style:transform="translate3d(calc(var(--px, 0) * 12px), calc(var(--py, 0) * 6px), 0)" style:opacity={sub ? 0 : 1} style:left="calc(var(--mx, 50%) - var(--mshift, 0) * 28%)" style:top="var(--my, 75%)" style:translate="-50% -100%" style:scale="clamp(0.7, calc(var(--ms, 0.8) / 0.8), 1.25)" style:transition="left 0.6s cubic-bezier(0.2, 0.8, 0.2, 1)" class="absolute flex h-[24rem] w-80 origin-bottom flex-col items-center justify-end">{@render remi(180)}</div>
		<section aria-label={t('play.home.play')} style:opacity={phase === 'idle' ? 1 : 0} style:pointer-events={phase === 'idle' ? undefined : 'none'} class="absolute right-0 bottom-24 transition-opacity duration-300">
			{@render startButton('edge')}
		</section>
	</div>
{:else}
<div class="relative z-10 flex min-h-0 flex-1 flex-col items-center justify-center gap-5 overflow-y-auto px-3 pt-3 pb-16 max-md:justify-end md:pb-6">
	<div data-stage-moremi style:transform="translate3d(calc(var(--px, 0) * 12px), calc(var(--py, 0) * 6px), 0)" style:opacity={sub ? 0 : 1} style:left="var(--mx, 50%)" style:top="calc(var(--my, 60%) - var(--mshift, 0) * 220px)" style:translate="-50% -100%" style:scale="clamp(0.7, calc(var(--ms, 0.63) / 0.63), 1.25)" style:transition="top 0.6s cubic-bezier(0.2, 0.8, 0.2, 1)" class="absolute flex h-52 w-72 origin-bottom flex-col items-center justify-end pb-1">
		{@render remi(132)}
	</div>

	<section aria-label={t('play.home.play')} style:opacity={phase !== 'idle' ? 0 : 1} style:pointer-events={phase !== 'idle' ? 'none' : undefined} class="flex w-full max-w-md shrink-0 flex-col gap-2.5 transition-opacity duration-300">
		{@render startButton('flat')}
	</section>
</div>
{/if}

{#if sub}
	<!-- 방 목록 / 아케이드: the same frozen, blurred stage behind; one glass panel that fills it, the back button in the header row beside the search and the filters -->
	{#snippet backBtn()}
		<button type="button" aria-label={t('play.dialog.back')} onclick={back} class="grid size-11 shrink-0 place-items-center rounded-full bg-white/90 text-slate-700 shadow-md transition-transform hover:scale-105 hover:bg-white"><Icon name="arrow_back" size={24} /></button>
	{/snippet}
	{#snippet roomsLead()}
		{@render backBtn()}
		<button type="button" disabled={!ready} onclick={oncreate} class="hw kk-btn inline-flex min-h-11 shrink-0 items-center gap-1.5 px-3.5 font-extrabold max-md:hidden"><Icon name="add" size={20} /><HoverWave text={t('play.lobby.createRoom')} /></button>
	{/snippet}
	<div role="dialog" aria-modal="true" aria-label={phase === 'rooms' ? t('play.home.rooms') : phase === 'page' ? t(`play.nav.${page}`) : t('play.lobby.arcade')} class="sub absolute inset-0 z-30 p-1 pt-2">
		<div class="glass relative flex h-full min-h-0 w-full flex-col overflow-hidden">
			{#if phase === 'rooms'}
				<RoomList {store} onjoin={(no) => onjoin?.(no)} bare dense lead={roomsLead} />
				<!-- phone: the button floats at the lower left (the chat button is at the right), so the header stays one row -->
				<button type="button" disabled={!ready} onclick={oncreate} class="hw kk-btn absolute bottom-3 left-3 z-10 inline-flex min-h-12 items-center gap-1.5 px-4 font-extrabold shadow-lg md:hidden"><Icon name="add" size={22} /><HoverWave text={t('play.lobby.createRoom')} /></button>
			{:else if phase === 'page'}
				{@render screen?.(backBtn)}
			{:else}
				<SingleScreen {store} lead={backBtn} />
			{/if}
		</div>
	</div>
{:else if phase === 'select'}
	<!-- the mode screen: me on the left, the cards on the right (a phone: the cards in the lower half) -->
	<div role="dialog" aria-modal="true" aria-label={t('play.lobby.modeTitle')} class="absolute inset-0 z-30 flex items-center justify-end p-6 max-md:items-end max-md:justify-center max-md:p-3 max-md:pb-20">
		<button type="button" aria-label={t('play.dialog.back')} onclick={back} class="absolute top-4 left-4 grid size-11 place-items-center rounded-full bg-white/90 text-slate-700 shadow-md transition-transform hover:scale-105 hover:bg-white"><Icon name="arrow_back" size={24} /></button>
		<div class="mstack flex h-full max-h-[32rem] w-[min(60%,640px)] flex-col gap-4 max-md:h-auto max-md:w-full">
			<div data-tour="mode-multi" class="mpanel relative grid min-h-0 flex-1 grid-cols-2 grid-rows-[minmax(0,3fr)_minmax(0,2fr)] gap-3 max-md:flex-none max-md:grid-rows-[5.2rem_3.4rem]">
				<span class="mchip">{t('play.lobby.group.multi')}</span>
				{@render card(0, multi[0], true)}
				{@render card(1, multi[1], true)}
				{@render card(2, multi[2], false)}
				{@render card(3, multi[3], false)}
			</div>
			<div data-tour="mode-single" class="mpanel mpanel-row relative grid grid-cols-2 gap-3 max-md:flex-none">
				<span class="mchip">{t('play.lobby.group.single')}</span>
				{@render card(4, arcade, false)}
				<!-- 학사모레미 points at 튜토리얼 until it is opened or dismissed (the bubble waits while the mode screen is closed: no target, no bubble) -->
				<Nudge id="tutorial" above enabled={ready && phase === 'select' && settled}>
					{#snippet content()}<Mascot {store} size={44} /><span>{t('play.nudge.tutorial')}</span>{/snippet}
					{@render card(5, tutorial, false)}
				</Nudge>
			</div>
		</div>
	</div>
{/if}
</div>

<style>
	/* ---- the big 게임 시작 button ---- */
	.start {
		position: relative;
		isolation: isolate;
		overflow: hidden;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.35rem;
		aspect-ratio: 16 / 9;
		border-radius: 18px;
		box-shadow: 0 5px 0 var(--kk-cta-edge), 0 14px 28px rgb(10 14 50 / 0.3), inset 0 1px 0 rgb(255 255 255 / 0.3);
		transition: transform 0.16s ease;
	}
	.start.flat {
		aspect-ratio: auto;
		width: 100%;
		height: 4.75rem;
	}
	/* on the desktop the button sits flush against the stage's right edge: rounded on the left only */
	.start.edge {
		aspect-ratio: auto;
		width: 14.5rem;
		height: 4.5rem;
		border-radius: 0.75rem 0 0 0.75rem;
	}
	.start.edge:hover:not(:disabled) {
		transform: translateX(-4px);
	}
	/* the nudge: the label squishes now and then (squash down and wide, stretch up and narrow, settle), in the gaps between the shine's sweeps */
	.start:not(.calm) .mlabel,
	.mcard.nudge .mlabel {
		display: inline-block;
		transform-origin: 50% 88%;
		animation: start-squish 2.8s ease-in-out infinite;
	}
	@keyframes start-squish {
		0%,
		10%,
		44%,
		100% {
			transform: scale(1, 1);
		}
		18% {
			transform: scale(1.16, 0.8);
		}
		27% {
			transform: scale(0.92, 1.14);
		}
		36% {
			transform: scale(1.04, 0.97);
		}
	}
	.start :global(.material-symbols-outlined) {
		color: #fff;
		-webkit-text-stroke: 4px color-mix(in srgb, var(--kk-accent) 40%, #000);
		paint-order: stroke fill;
		filter: drop-shadow(0 2px 0 color-mix(in srgb, var(--kk-accent) 40%, #000));
	}
	.start:hover:not(:disabled) {
		transform: translateY(-3px);
	}
	.start:active:not(:disabled) {
		transform: translateY(2px);
	}
	.start-check {
		position: absolute;
		inset: 0;
		z-index: -1;
		overflow: hidden;
		opacity: 0;
		transition: opacity 0.22s;
		pointer-events: none;
	}
	.start:hover .start-check {
		opacity: 1;
	}
	/* the shine that sweeps across now and then: the enchant button's, a transform only */
	.start:not(:disabled):not(.calm)::after,
	.mcard.nudge:not(:disabled)::after {
		content: '';
		position: absolute;
		inset: 0 auto 0 0;
		z-index: 2;
		width: 30%;
		background: linear-gradient(100deg, transparent, rgb(255 255 255 / 0.55), transparent);
		transform: translateX(-160%) skewX(-20deg);
		animation: start-shine 2.8s ease-in-out infinite;
		pointer-events: none;
	}
	@keyframes start-shine {
		0%,
		55% {
			transform: translateX(-160%) skewX(-20deg);
		}
		100% {
			transform: translateX(450%) skewX(-20deg);
		}
	}

	/* the nickname plate under the feet, as in the shop's preview */
	.nick {
		background: rgb(255 255 255 / 0.88);
		box-shadow: 0 2px 5px rgb(20 40 80 / 0.25);
	}
	.nick :global(*) {
		color: #1d2433;
	}

	/* ---- the title lettering: white, an outline in a dark of the card's colour ---- */
	.mlabel {
		position: relative;
		z-index: 1;
		font-family: 'ONE-Mobile-POP', 'NanumSquare Neo', sans-serif;
		font-weight: 400;
		line-height: 1.1;
		letter-spacing: 0.02em;
		color: #fff;
		-webkit-text-stroke: 5px color-mix(in srgb, var(--c, var(--kk-accent)) 62%, #000);
		paint-order: stroke fill;
		text-shadow: 0 3px 0 color-mix(in srgb, var(--c, var(--kk-accent)) 45%, #000);
	}

	/* ---- the mode screen ---- */
	/* on the mode cards the outline and the shadow go to the icon; the label is plain: a dark solid of the card's own tint */
	.mcard .mlabel {
		color: var(--ink);
		-webkit-text-stroke: 0;
		text-shadow: none;
		letter-spacing: 0.01em;
	}
	/* the lower-ranked buttons are one line: the icon beside the label */
	.mcard.mcard-sm {
		flex-direction: row;
		gap: 0.55rem;
	}
	.mcard :global(.material-symbols-outlined) {
		color: #fff;
		-webkit-text-stroke: 4px var(--ink);
		paint-order: stroke fill;
		filter: drop-shadow(0 2px 0 var(--ink));
	}
		/* the group's name is a chip sitting on the panel's edge (flat colour, no shadowed lettering) */
	.mchip {
		position: absolute;
		top: -0.95rem;
		left: 1.1rem;
		z-index: 2;
		padding: 0.3rem 1.05rem;
		border-radius: 999px;
		background: var(--kk-accent-strong, var(--kk-accent));
		color: #fff;
		font-size: 0.95rem;
		font-weight: 900;
		letter-spacing: 0.02em;
		line-height: 1.25;
	}
	/* the sub screens: a glass panel, light at the top and fading toward the bottom, and a quick fade-in (no slide) */
	.sub {
		animation: sub-in 0.22s ease-out both;
	}
	@keyframes sub-in {
		from {
			opacity: 0;
			transform: translateY(8px);
		}
		to {
			opacity: 1;
			transform: none;
		}
	}
	/* no container: the list sits straight on the stage's blur; only the header's rule goes and a soft shade rises behind the buttons at the bottom */
	.glass :global(section) {
		background: transparent;
	}
	.glass :global([class*='border-b']) {
		border-color: transparent;
	}
	/* the rooms are the content: compact tiles, scrolling under a fade, the two buttons floating over the fade */
	.glass :global(button.kk-card) {
		min-height: 5.25rem;
	}
	.glass :global(.overflow-y-auto) {
		padding-bottom: 1rem;
		-webkit-mask-image: linear-gradient(to bottom, #000 calc(100% - 2rem), transparent);
		mask-image: linear-gradient(to bottom, #000 calc(100% - 2rem), transparent);
	}
	.glass :global(button[class*='grid-cols']) {
		background: color-mix(in srgb, var(--kk-surface) 72%, transparent);
		margin-bottom: 2px;
		border-radius: 10px;
		border-bottom-color: transparent;
	}
	/* the seat count stays a number: no gauge */
	.glass :global(.kk-bar) {
		display: none;
	}
	.glass :global(button[class*='grid-cols']:hover) {
		background: color-mix(in srgb, var(--kk-surface) 92%, transparent);
	}
	@media (max-width: 767px) {
		.glass :global(.overflow-y-auto) {
			padding-bottom: 4.5rem;
		}
	}
	.glass :global(.min-h-8) {
		color: #fff;
		text-shadow: 0 1px 3px rgb(10 14 50 / 0.6);
	}
	@media (prefers-reduced-motion: reduce) {
		.sub {
			animation: none;
		}
	}
	/* the whole column (the multiplayer group and the single-play card) slides in together */
	.mstack {
		animation: mp-in 0.55s cubic-bezier(0.2, 0.9, 0.25, 1) backwards;
	}
	.mpanel-row :global(.mcard) {
		height: 3.6rem;
	}
	@media (max-width: 767px) {
		.mpanel-row :global(.mcard) {
			height: 3.4rem;
		}
	}
	.mpanel {
		padding: 1.5rem 1rem 1rem;
		border-radius: 22px;
		background: rgb(255 255 255 / 0.14);
		box-shadow: 0 18px 40px rgb(10 14 50 / 0.3), inset 0 0 0 2px var(--kk-accent);
	}
	@keyframes mp-in {
		from {
			opacity: 0;
			transform: translateX(110px);
		}
		to {
			opacity: 1;
			transform: none;
		}
	}
	.mcard {
		position: relative;
		isolation: isolate;
		overflow: hidden;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 0.1rem;
		width: 100%;
		height: 100%;
		min-height: 3.4rem;
		border-radius: 20px;
		background: linear-gradient(to bottom, var(--c1), var(--c2));
		color: #fff;
		box-shadow: 0 3px 6px rgb(20 40 80 / 0.28);
		transition: transform 0.18s ease, filter 0.18s ease;
	}
	.mcard:hover:not(:disabled) {
		transform: translateY(-3px) scale(1.025);
		filter: saturate(1.1) brightness(1.04);
	}
	.mcard:active:not(:disabled) {
		transform: translateY(1px) scale(0.99);
	}
	.mcard:disabled {
		opacity: 0.55;
	}
	.mcard-check {
		position: absolute;
		inset: 0;
		z-index: -1;
		overflow: hidden;
		opacity: 0;
		transition: opacity 0.22s;
		pointer-events: none;
	}
	.mcard:hover .mcard-check {
		opacity: 1;
	}
	@media (prefers-reduced-motion: reduce) {
		.start::after {
			animation: none;
			display: none;
		}
		.start .mlabel,
		.mcard .mlabel,
		.mcard.nudge::after {
			animation: none;
		}
		.mstack {
			animation: none;
		}
	}
</style>
