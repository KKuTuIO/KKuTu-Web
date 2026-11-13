<script>
	import { onDestroy } from 'svelte';
	import { t, tList } from '../i18n.svelte.js';
	import { haptic } from '../haptics.js';
	import { reducedMotion } from '../motion.js';
	import { groupModes } from '../lobbyModel.js';
	import { prefs } from '../prefs.svelte.js';
	import { maskBad } from '../badwords.js';
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';
	import StatusBubble from './StatusBubble.svelte';
	import Tabs from '../ui/Tabs.svelte';

	/**
	 * The phone's home (the 로비 tab): my Moremi in full with the profile's speech balloon (my status message, or a greeting while I have none),
	 * the places that used to hide in 더보기, then the games. A game starts a quick match of it (the 빠른 매칭 window opens at it, for the
	 * options); the room list is a page of its own behind a button as plain as 방 만들기 (`onrooms`).
	 * @type {{store:any, ready?:boolean, onselect:(id:string)=>void, onrooms:()=>void, oncreate:()=>void}}
	 */
	let { store, ready = true, onselect, onrooms, oncreate } = $props();
	const net = $derived(store.net);
	const me = $derived(store.me);
	const parts = $derived(net.meta.moremiParts);
	const labels = $derived(net.meta.labels);

	// The balloon: what I set as my status message (editable here as in the profile), else the greeting (not an editable text).
	const status = $derived(me?.statusMessage ?? '');
	const mask = (s) => (prefs.filter === 'off' ? s : maskBad(s));

	// Pressing my Moremi: it is squashed and springs back (the press you feel), the phone ticks, and the balloon pops up with a new line
	// (the balloon is keyed on `n`, so it is a new one each time, with the pop that a newly set status message has). Never the same line twice running.
	let poke = $state(null); // { text, n } while a line is showing
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
			timer = setTimeout(() => (poke = null), 5000); // then the greeting (or my status message) is back
		}
		if (!reducedMotion())
			body?.animate(
				[{ transform: 'scale(1, 1)' }, { transform: 'scale(1.06, 0.8)', offset: 0.28 }, { transform: 'scale(0.97, 1.08)', offset: 0.6 }, { transform: 'scale(1, 1)' }],
				{ duration: 440, easing: 'ease-out' }
			);
	}
	onDestroy(() => clearTimeout(timer));

	// The places that were in 더보기, one tap away.
	const shortcuts = [
		{ id: 'rank', icon: 'trophy' },
		{ id: 'dict', icon: 'menu_book' },
		{ id: 'replay', icon: 'replay' },
		{ id: 'help', icon: 'help' }
	];

	const groups = $derived(groupModes(net.meta.modes, net.meta.rules));
	let opened = $state(null);
	const category = $derived(opened && groups.some((g) => g.id === opened) ? opened : (groups[0]?.id ?? 'ko'));
	const current = $derived(groups.find((g) => g.id === category));
	const tabs = $derived(groups.map((g) => ({ id: g.id, label: t(`play.create.group.${g.id}`) })));
	// Inside a category the language prefix is redundant ("한국어 끝말잇기" -> "끝말잇기").
	function shortName(key) {
		const full = labels[`game.mode.${key}.name`] ?? key;
		const prefix = t(`play.create.group.${category}`) + ' ';
		return full.startsWith(prefix) ? full.slice(prefix.length) : full;
	}
	const rooms = $derived(Object.values(net.rooms));
	const waitingOf = (index) => rooms.filter((r) => !r.playing && !r.locked && r.cur < r.max && r.modeIndex === index).length;
</script>

<div class="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pb-16">
	<!-- me, in full; the balloon is the profile's own. Pressing me is just for fun (the profile is on the 소셜 tab). -->
	<div class="relative flex h-52 shrink-0 flex-col items-center justify-end pb-1">
		<button type="button" bind:this={body} onclick={press} aria-label={t('play.home.poke')} class="relative origin-bottom rounded-2xl">
			<Moremi equip={me?.equip ?? {}} shop={store.shop} {parts} size={132} label={me?.name ?? ''} frame={false} />
		</button>
		{#if me}
			{#key poke?.n ?? 0}
				{#if poke}<StatusBubble message={poke.text} />
				{:else if status}<StatusBubble message={status} isMe {mask} onsave={(m) => store.setStatusMessage(m)} />
				{:else}<StatusBubble message={t('play.home.greeting', { name: me.name })} />{/if}
			{/key}
		{/if}
	</div>

	<section aria-label={t('play.home.play')} class="flex shrink-0 flex-col gap-2.5">
		<button type="button" disabled={!ready} data-tour="quick-match" onclick={() => store.hooks.openQuick(2)} class="kk-btn kk-btn-hero kk-btn-lg w-full"><span class="kk-btn-icon"><Icon name="play_arrow" size={28} fill /></span><span class="kk-btn-label">{t('play.lobby.quickMatch')}</span></button>
		<!-- the room list and making a room: the same width, the same rank -->
		<div class="grid grid-cols-2 gap-2.5">
			<button type="button" data-tour="room-list" onclick={onrooms} class="kk-btn inline-flex min-h-12 items-center justify-center gap-1.5 px-2 whitespace-nowrap"><Icon name="meeting_room" size={20} />{t('play.home.rooms')}</button>
			<button type="button" disabled={!ready} data-tour="create-room" onclick={oncreate} class="kk-btn inline-flex min-h-12 items-center justify-center gap-1.5 px-2 whitespace-nowrap"><Icon name="add" size={20} />{t('play.lobby.createRoom')}</button>
		</div>
	</section>

	<nav aria-label={t('play.home.shortcuts')} class="grid shrink-0 grid-cols-4 gap-2">
		{#each shortcuts as s (s.id)}
			<button type="button" onclick={() => onselect(s.id)} class="flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl text-xs font-bold text-(color:--kk-muted) active:bg-(--kk-surface-2)">
				<Icon name={s.icon} size={26} class="text-(color:--kk-text)" />
				{t(`play.nav.${s.id}`)}
			</button>
		{/each}
	</nav>

	<!-- the games -->
	<section aria-label={t('play.home.play')} class="flex shrink-0 flex-col gap-3">
		{#if groups.length > 1}<Tabs bind:value={() => category, (v) => (opened = v)} {tabs} idPrefix="home-cat" label={t('play.home.categories')} />{/if}
		<ul class="grid grid-cols-2 gap-2.5">
			{#each current?.modes ?? [] as m (m.key)}
				{@const waiting = waitingOf(m.index)}
				<li>
					<button type="button" disabled={!ready} onclick={() => store.hooks.openQuick(m.index)} class="kk-btn flex min-h-14 w-full items-center justify-between gap-2 px-3 text-left">
						<span class="min-w-0 text-[15px] leading-tight font-extrabold break-keep">{shortName(m.key)}</span>
						{#if waiting}<span class="shrink-0 text-xs font-bold text-(color:--kk-accent-strong) tabular-nums" aria-label={t('play.home.modeWaiting', { count: waiting })}>{waiting}</span>{/if}
					</button>
				</li>
			{/each}
		</ul>
	</section>
</div>
