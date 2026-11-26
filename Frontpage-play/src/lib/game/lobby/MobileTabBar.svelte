<script>
	import { t } from '../i18n.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { league } from '../leagueStore.svelte.js';
	import Icon from './Icon.svelte';
	import Dialog from './Dialog.svelte';
	import Nudge from './Nudge.svelte';
	import Mascot from './Mascot.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { closeNudge, nudgeOpen } from '../nudges.svelte.js';
	import { haptic } from '../haptics.js';

	/**
	 * Phone navigation: a tab bar at the bottom, where the thumb is. The main places are tabs; the rest are in the 더보기 sheet.
	 * A guest has no announcements, so 도움말 takes that tab (with 학사모레미's nudge) and 이어하기 moves into the sheet.
	 * The mailbox lives in the header. Desktop keeps TopBar's menu.
	 */
	/** `noPeople`: the two columns already show the people panel, so there is no tab for it */
	/** `noHelp`: the new lobby carries the tutorial itself, so there is no 도움말 tab or entry and no nudge on 더보기 */
	/**
	 * A floating glass capsule (iOS 26). `side`: on an open folding phone that is wider than tall (Apple's iPhone Duo guidance) the navigation moves to the
	 * edge: the capsule stands upright there, its tabs one under the other, on the `edge` of the dominant hand (where the thumb is). Upright, its slot is part of the layout (the page ends beside it); along the bottom it floats over the page (the page
	 * keeps `--dock` of room at its end, see +page.svelte).
	 */
	let { store, active = 'lobby', onselect, noHelp = false, noNews = false, noPeople = false, side = false, edge = 'left', locked = false } = $props();
	const guest = $derived(store.net.guest);
	let more = $state(false);
	const tabs = $derived(
		[
			{ id: 'lobby', icon: 'home' },
			{ id: 'people', icon: 'group', badge: store.pendingFriends.incoming.length },
			{ id: 'shop', icon: 'storefront' },
			guest ? { id: 'help', icon: 'help' } : { id: 'news', icon: 'campaign', dot: store.newsAlerts.length > 0 }
		].filter((m) => !(noHelp && m.id === 'help') && !(noNews && m.id === 'news') && !(noPeople && m.id === 'people'))
	);
	const extra = $derived(
		[
			{ id: 'rank', icon: 'trophy' },
			{ id: 'dict', icon: 'menu_book' },
			{ id: 'malmoi', icon: 'collections_bookmark' },
			{ id: 'replay', icon: 'replay' },
			guest ? { id: 'progress', icon: 'cloud_upload' } : { id: 'help', icon: 'help' },
			{ id: 'settings', icon: 'settings' }
		].filter((m) => !(noHelp && m.id === 'help'))
	);
	const moreActive = $derived(['rank', 'dict', 'malmoi', 'replay'].includes(active));
	const count = $derived(tabs.length + 1);
	// which slot the focus sits on: a tab, or 더보기 for the pages in its sheet (or while it is open); -1 for a place with none
	const index = $derived(more || moreActive ? tabs.length : tabs.findIndex((m) => m.id === active));
	const pill = $derived(
		side
			? `left:.125rem;right:.125rem;top:.125rem;height:calc((100% - .25rem)/${count});transform:translateY(${index * 100}%)`
			: `top:.375rem;bottom:.375rem;left:.375rem;width:calc((100% - .75rem)/${count});transform:translateX(${index * 100}%)`
	);
	const ready = $derived(store.net.status === 'ready' && store.net.assetsReady && !store.currentRoom);
	const label = (id) => t(`play.nav.${id}`);
	// The rail has no labels (as in Apple's iPhone Duo guidance): pressing and holding a tab shows its name in the shared tooltip.
	let held = false; // the press that showed the tooltip must not also open the tab
	const onhold = () => {
		held = true;
		haptic(6);
	};
	const tipSide = $derived(edge === 'right' ? 'left' : 'right'); // beside the rail, on the page's side (the arrow points at the tab)
	function pick(id) {
		if (held) return void (held = false);
		if (locked && id !== 'settings') return void toast(t('play.league.queue.locked'), { kind: 'info' });
		haptic();
		more = false;
		if (id === 'help') closeNudge('tutorial');
		onselect?.(id);
	}
</script>

{#snippet tabButton(m)}
	{@const on = m.id === active}
	<button type="button" data-tour={m.id === 'people' ? 'side' : m.id === 'help' ? 'help' : undefined} aria-current={on ? 'page' : undefined} aria-label={side ? label(m.id) : undefined} onpointerdown={() => (held = false)} onclick={() => pick(m.id)} oncontextmenu={side ? (e) => e.preventDefault() : undefined} class="relative z-10 flex {side ? 'h-10' : 'min-h-14'} w-full flex-col items-center justify-center gap-0.5 text-[11px] leading-none font-bold {on ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'} {locked ? 'opacity-40' : ''}">
		<span class="grid {side ? 'size-10' : 'h-7 w-14'} place-items-center"><Icon name={m.icon} size={side ? 20 : 22} fill={on} /></span>
		{#if !side}{label(m.id)}{/if}
		{#if m.badge}<span class="absolute top-1.5 left-1/2 ml-2 inline-grid h-4 min-w-4 place-items-center rounded-full bg-(--kk-danger) px-1 text-[10px] font-extrabold text-white">{m.badge}</span>
		{:else if m.dot}<span class="absolute top-2 left-1/2 ml-3 size-2 rounded-full bg-(--kk-danger)" aria-hidden="true"></span>{/if}
	</button>
{/snippet}

{#snippet tab(m)}
	{#if side}<Tooltip text={label(m.id)} placement={tipSide} hold {onhold}>{@render tabButton(m)}</Tooltip>{:else}{@render tabButton(m)}{/if}
{/snippet}

<!-- the slot in the layout; the capsule floats in it -->
<div class={side ? `flex shrink-0 flex-col justify-end pt-2 pb-[calc(max(0.75rem,env(safe-area-inset-bottom))+3.25rem)] ${edge === 'right' ? 'w-[calc(3.125rem+max(1rem,env(safe-area-inset-right)))] pr-[max(1rem,env(safe-area-inset-right))] pl-1.5' : 'w-[calc(3.125rem+max(1rem,env(safe-area-inset-left)))] pl-[max(1rem,env(safe-area-inset-left))] pr-1.5'}` : 'pointer-events-none absolute inset-x-0 bottom-0 z-10 px-4 pb-[max(0.5rem,env(safe-area-inset-bottom))]'}>
<nav data-tour="nav" aria-label={t('play.nav.label')} style={side ? `grid-template-rows:repeat(${count}, minmax(0, 1fr))` : `grid-template-columns:repeat(${count}, minmax(0, 1fr))`} class="kk-glass pointer-events-auto mx-auto grid {side ? 'w-full rounded-full p-0.5' : 'w-full max-w-md rounded-full p-1.5'}">
	{#if index >= 0}<span class="kk-glass-pill pointer-events-none absolute rounded-full bg-(--kk-accent-soft)" style={pill} aria-hidden="true"></span>{/if}
	{#each tabs as m (m.id)}
		{#if m.id === 'help'}
			<!-- 학사모레미 points at 도움말 until it is opened -->
			<Nudge id="tutorial" above={!side} beside={side ? tipSide : null} enabled={ready}>
				{#snippet content()}<Mascot {store} size={44} /><span>{t('play.nudge.tutorial')}</span>{/snippet}
				{@render tab(m)}
			</Nudge>
		{:else if m.id === 'news'}
			<Nudge id="news" onclose={() => store.dismissNewsNudge()} above={!side} beside={side ? tipSide : null} enabled={ready && !nudgeOpen('tutorial') && active !== 'news' && !!store.newsNudge && !league.lobbyOpen}>
				{#snippet content()}
					<span class="flex min-w-0 flex-col gap-1.5">
						<span class="flex flex-col px-1"><b class="text-sm font-extrabold">{t('play.news.nudge')}</b><span class="truncate text-xs font-bold text-(color:--kk-muted)">{store.newsNudge?.name}</span></span>
					</span>
				{/snippet}
				{@render tab(m)}
			</Nudge>
		{:else}
			{@render tab(m)}
		{/if}
	{/each}
	<!-- a member's 도움말 is in the sheet, so the tutorial's nudge points at the tab that holds it -->
	<Nudge id="tutorial" above={!side} beside={side ? tipSide : null} enabled={ready && !guest && !noHelp}>
		{#snippet content()}<Mascot {store} size={44} /><span>{t('play.nudge.tutorial')}</span>{/snippet}
		{#if side}<Tooltip text={t('play.nav.more')} placement={tipSide} hold {onhold}><button type="button" data-tour={guest ? undefined : 'help'} aria-expanded={more} aria-label={side ? t('play.nav.more') : undefined} onpointerdown={() => (held = false)} onclick={() => { if (held) return void (held = false); haptic(); more = !more; }} oncontextmenu={side ? (e) => e.preventDefault() : undefined} class="relative z-10 flex {side ? 'h-10' : 'min-h-14'} w-full flex-col items-center justify-center gap-0.5 text-[11px] leading-none font-bold {moreActive || more ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}">
			<span class="grid {side ? 'size-10' : 'h-7 w-14'} place-items-center"><Icon name="more_horiz" size={side ? 20 : 22} /></span>
			{#if !side}{t('play.nav.more')}{/if}
		</button></Tooltip>{:else}<button type="button" data-tour={guest ? undefined : 'help'} aria-expanded={more} aria-label={side ? t('play.nav.more') : undefined} onpointerdown={() => (held = false)} onclick={() => { if (held) return void (held = false); haptic(); more = !more; }} oncontextmenu={side ? (e) => e.preventDefault() : undefined} class="relative z-10 flex {side ? 'h-10' : 'min-h-14'} w-full flex-col items-center justify-center gap-0.5 text-[11px] leading-none font-bold {moreActive || more ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}">
			<span class="grid {side ? 'size-10' : 'h-7 w-14'} place-items-center"><Icon name="more_horiz" size={side ? 20 : 22} /></span>
			{#if !side}{t('play.nav.more')}{/if}
		</button>{/if}
	</Nudge>
</nav>
</div>

<Dialog bind:open={more} title={t('play.nav.more')}>
	<div role="menu" class="-m-2 flex flex-col">
		{#each extra as m (m.id)}
			<button type="button" role="menuitem" onclick={() => pick(m.id)} class="flex min-h-12 w-full items-center gap-3 rounded-xl px-3 text-left text-base font-bold hover:bg-(--kk-surface-2)">
				<Icon name={m.icon} size={24} />{label(m.id)}
			</button>
		{/each}
	</div>
</Dialog>
