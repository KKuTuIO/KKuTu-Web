<script>
	import { t } from '../i18n.svelte.js';
	import { prefs } from '../prefs.svelte.js';
	import { maskBad } from '../badwords.js';
	import Icon from './Icon.svelte';
	import Logo from './Logo.svelte';
	import { Spring } from '../spring.js';

	/** The header while a game is on: which room and rules, the dictionary, and the way out. */
	let { store, ondict, oncalc } = $props();
	// 수학 대결 has a calculator where the other games have the dictionary
	const math = $derived(store.net.meta.rules[room?.modeKey]?.rule === 'Mathquiz');

	// the notch drops in from the top edge on a spring and settles with a small bounce
	function settle(node) {
		const spring = new Spring(0.55, { response: 0.45, dampingFraction: 0.55 });
		let raf = 0;
		let last = performance.now();
		const frame = (now) => {
			const moving = spring.update(Math.min((now - last) / 1000, 1 / 30));
			last = now;
			node.style.transformOrigin = '50% 0';
			node.style.transform = `scaleY(${spring.value})`; // the centring is Tailwind's own -translate-x-1/2 (the `translate` property)
			raf = moving ? requestAnimationFrame(frame) : 0;
		};
		spring.to(1);
		raf = requestAnimationFrame(frame);
		return { destroy: () => cancelAnimationFrame(raf) };
	}
	const room = $derived(store.currentRoom);
	// 퇴장 예약 is for players of a real game only: a spectator or someone practising can just leave (legacy)
	const canReserve = $derived(!!g && g.phase !== 'ended' && store.net.users[store.net.id]?.game.form !== 'S' && !room?.practice);
	$effect(() => {
		if (!canReserve && store.ui.exitReserved) store.ui.exitReserved = false;
	});
	const reserveLabel = $derived((store.net.meta.labels['kkutu.menu.reserveLeave'] ?? '').replace(/<[^>]*>|&nbsp;/g, ' ').trim());
	const g = $derived(store.game);
	const second = $derived(t('kkutu.js.second'));
	const cw = $derived(store.net.meta.rules[room?.modeKey]?.rule === 'Crossword');
	// the crossword names its boards with circled numbers when the room does not give titles
	const roundTitles = $derived(Array.from({ length: room?.rounds ?? 0 }, (_, i) => room?.game?.title?.[i] || (cw ? String.fromCharCode(0x2460 + i) : '')));
</script>

{#if room}
	<header class="kk-card relative z-20 flex min-h-14 shrink-0 items-center gap-3 rounded-none border-x-0 border-t-0 px-4 py-2">
		<span class="hidden sm:inline"><Logo class="h-7" /></span>
		<div class="flex min-w-0 items-center gap-2 {prefs.hideRoom ? 'blur-[5px] hover:blur-none' : ''}">
			<span class="font-black tabular-nums">{room.practice ? t('kkutu.js.practice') : room.no}</span>
			<h1 class="truncate font-extrabold">{maskBad(room.title || '')}</h1>
		</div>
		<span class="kk-chip shrink-0">{room.mode}</span>
		<span class="hidden min-w-0 flex-wrap items-center gap-1.5 xl:flex">
			{#each room.rules as rule (rule.key)}<span class="kk-chip">{rule.label}</span>{/each}
		</span>
		<!-- 제시어: a notch in the middle of the bar, hanging from its top edge and kept inside it -->
		<div use:settle class="absolute top-0 left-1/2 hidden h-full -translate-x-1/2 items-center gap-2 rounded-b-2xl bg-(--kk-accent-soft) px-4 shadow-[inset_0_-2px_0_color-mix(in_srgb,var(--kk-accent)_45%,transparent)] lg:flex" aria-label={t('play.game.roundLetters')}>
			<div class="flex items-center gap-1">
				{#each g?.roundsDone && cw ? [] : roundTitles as title, i (i)}
					{@const on = cw ? i + 1 === g?.selectedRound : i + 1 === g?.round}
					{#key cw && g?.cwFlash?.round === i ? g.cwFlash.key : 0}
						{#if cw}
							<button type="button" onclick={() => store.selectRound(i + 1)} aria-pressed={on} class="grid min-w-9 place-items-center rounded-xl px-2 py-0.5 text-xl font-black {on ? 'bg-(--kk-fill) text-white shadow-[0_2px_0_var(--kk-fill-edge)]' : 'bg-(--kk-surface) text-(color:--kk-muted)'} {cw && g?.cwFlash?.round === i ? 'kk-round-effect' : ''}">{title || '?'}</button>
						{:else}
							<span class="grid min-w-9 place-items-center rounded-xl px-2 py-0.5 text-xl font-black {on ? 'bg-(--kk-fill) text-white shadow-[0_2px_0_var(--kk-fill-edge)]' : 'bg-(--kk-surface) text-(color:--kk-muted)'} {cw && g?.cwFlash?.round === i ? 'kk-round-effect' : ''}">{title || '?'}</span>
						{/if}
					{/key}
				{/each}
			</div>
		</div>
		<ul class="ml-auto flex shrink-0 items-center gap-3 text-sm font-bold text-(color:--kk-muted)">
			<li class="inline-flex items-center gap-1"><Icon name="repeat" size={18} />{g?.round ? Math.min(g.round, room.rounds) : 0} / {room.rounds}</li>
			<li class="inline-flex items-center gap-1"><Icon name="timer" size={18} />{room.time}{second}</li>
			<li class="inline-flex items-center gap-1"><Icon name="group" size={18} />{room.cur} / {room.limit}</li>
		</ul>
		{#if math}
			<button type="button" onclick={oncalc} aria-label={t('play.calc.title')} class="kk-btn inline-flex shrink-0 items-center gap-1.5"><Icon name="calculate" size={20} /><span class="hidden xl:inline">{t('play.calc.title')}</span></button>
		{:else}
			<button type="button" onclick={ondict} aria-label={t('play.nav.dict')} class="kk-btn inline-flex shrink-0 items-center gap-1.5"><Icon name="menu_book" size={20} /><span class="hidden xl:inline">{t('play.nav.dict')}</span></button>
		{/if}
		{#if canReserve}
			<button type="button" onclick={() => store.toggleExitReserve()} aria-pressed={store.ui.exitReserved} aria-label={reserveLabel} class="kk-btn inline-flex shrink-0 items-center gap-1.5 border-transparent text-white {store.ui.exitReserved ? 'bg-[#db2777] shadow-[0_3px_0_#9d174d]' : 'bg-[#ec4899] shadow-[0_3px_0_#be185d]'}"><Icon name="event_busy" size={20} fill={store.ui.exitReserved} /><span class="hidden xl:inline">{reserveLabel}</span></button>
		{/if}
		<button type="button" onclick={() => store.leaveGame()} aria-label={t('play.room.leave')} class="kk-btn kk-btn-danger inline-flex shrink-0 items-center gap-1.5"><Icon name="logout" size={20} /><span class="hidden xl:inline">{t('play.room.leave')}</span></button>
	</header>
{/if}

<style>
	.kk-round-effect { animation: kk-round-effect 0.8s ease 1; }
	@keyframes kk-round-effect { 0% { transform: scale(1.35); background-color: #ffff44; } 100% { transform: scale(1); } }
	@media (prefers-reduced-motion: reduce) { .kk-round-effect { animation: none; } }
</style>
