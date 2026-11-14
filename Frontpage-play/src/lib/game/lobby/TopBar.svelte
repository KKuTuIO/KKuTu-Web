<script>
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import ProfileSwitcher from '$lib/ProfileSwitcher.svelte';
	import Icon from './Icon.svelte';
	import Logo from './Logo.svelte';
	import GameIcon from './GameIcon.svelte';
	import MailMenu from './MailMenu.svelte';
	import FullscreenButton from './FullscreenButton.svelte';
	import { confirm } from '../ui/confirm.svelte.js';
	import Nudge from './Nudge.svelte';
	import Mascot from './Mascot.svelte';
	import { closeNudge, nudgeOpen } from '../nudges.svelte.js';
	/** `noHelp`: the new lobby carries the tutorial itself (a 튜토리얼 button and the nudge), so the bar has no 도움말 */
	let { store, active = 'lobby', onselect, phone = false, noHelp = false, noNews = false } = $props();
	// `noNews`: the new lobby has 공지·행사 as an icon on its stage, so the bar leaves it out while the lobby is showing
	const me = $derived(store.me);
	const guest = $derived(store.net.guest);
	// Anywhere in the client the logo asks first: it leaves the game page for the portal.
	async function leaveToPortal(e) {
		e.preventDefault();
		if (await confirm({ message: store.currentRoom?.playing ? t('play.leave.playing') : t('play.leave.message'), ok: t('play.leave.go'), cancel: t('play.leave.stay') })) location.href = '/';
	}

	// `color` is each button's theme colour from the legacy client's menu.html. It is shown only while the
	// item is open.
	const menu = [
		{ id: 'lobby', icon: 'home', color: '#B0D2F3' },
		{ id: 'rank', icon: 'trophy', color: '#FFADD3' },
		{ id: 'shop', icon: 'storefront', color: '#B3E7B7' },
		{ id: 'dict', icon: 'menu_book', color: '#73D07A' },
		{ id: 'replay', icon: 'replay', color: '#D9FF82' },
		{ id: 'news', icon: 'campaign', color: '#FFC67F' }
	];
	// Guests have no mailbox, balance or announcements; 이어하기 takes the announcements' place (as in the legacy client).
	const guestMenu = menu.map((m) => (m.id === 'news' ? { id: 'progress', icon: 'cloud_upload', color: m.color } : m));
	const memberUtilities = [
		{ id: 'mail', icon: 'mail', color: '#FECDD3' },
		{ id: 'help', icon: 'help', color: '#BBBBBB' },
		{ id: 'settings', icon: 'settings', color: '#CCCCCC' }
	];
	const items = $derived((guest ? guestMenu : menu).filter((m) => !(noNews && m.id === 'news')));
	const utilities = $derived((guest ? memberUtilities.filter((u) => u.id !== 'mail') : memberUtilities).filter((u) => !(noHelp && u.id === 'help')));
	const fmt = (n) => (n ?? 0).toLocaleString();
</script>

<!--
	Labels cost width. On tablets only the current page keeps its label (the others get a tooltip);
	from xl up everything is spelled out. The phone strip scrolls, so it always shows labels.
	Utilities (mail / help / settings) live at the right of the bar and are always icon-only.
-->
{#snippet item(m, o = {})}
	{#if m.id === 'help'}
		<!-- 학사모레미 points at 도움말 until the player opens it (not inside a room, where the lobby's help isn't the point) -->
		<Nudge id="tutorial" enabled={store.net.status === 'ready' && store.net.assetsReady && !store.currentRoom}>
			{#snippet content()}<Mascot {store} size={44} /><span>{t('play.nudge.tutorial')}</span>{/snippet}
			{@render navButton(m, o)}
		</Nudge>
	{:else if m.id === 'news'}
		<!-- a new event the player has not seen: its picture under the menu. It waits for the tutorial's nudge, so two bubbles never compete; pressing it opens the page, which counts the event as seen. -->
		<Nudge id="news" enabled={store.net.status === 'ready' && store.net.assetsReady && !store.currentRoom && !nudgeOpen('tutorial') && active !== 'news' && !!store.newsNudge}>
			{#snippet content()}
				<span class="flex min-w-0 flex-col gap-1.5">
					<span class="flex flex-col px-1"><b class="text-sm font-extrabold">{t('play.news.nudge')}</b><span class="truncate text-xs font-bold text-(color:--kk-muted)">{store.newsNudge?.name}</span></span>
					<img src={store.newsNudge?.image} alt="" draggable="false" decoding="async" class="aspect-[518/190] w-72 max-w-full rounded-xl object-cover" />
				</span>
			{/snippet}
			{@render navButton(m, o)}
		</Nudge>
	{:else}
		{@render navButton(m, o)}
	{/if}
{/snippet}
{#snippet navButton(m, { strip = false, utility = false } = {})}
	{@const on = m.id === active}
	<!--
		Moving between menus animates like a chipped nav: the chosen item fills with its colour and its label slides open,
		the one left behind folds its label back in (on the phone strip it is the icon that folds in and out instead).
	-->
	<Tooltip text={t(`play.nav.${m.id}`)} placement="bottom">
		<button
			type="button"
			aria-current={on ? 'page' : undefined}
			aria-label={m.id === 'news' && store.newsAlerts.length ? t('play.news.alertAria', { n: store.newsAlerts.length }) : t(`play.nav.${m.id}`)}
			data-tour={utility ? m.id : undefined}
			onclick={() => {
				if (m.id === 'help') closeNudge('tutorial');
				onselect?.(m.id);
			}}
			style:--c={m.color}
			class="relative inline-flex h-10 min-w-10 shrink-0 items-center justify-center overflow-hidden rounded-xl font-bold whitespace-nowrap transition-all duration-300 ease-out {on
				? 'bg-(--c) px-3.5 font-extrabold text-[#111]'
				: `${utility ? 'px-2.5' : 'px-2.5 xl:px-3.5'} bg-transparent text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)`}"
		>
			{#if m.id === 'news' && store.newsAlerts.length && !on}<span class="absolute top-1.5 right-1.5 size-2 rounded-full bg-(--kk-danger)" aria-hidden="true"></span>{/if}
			<!-- width goes 0 -> its own size through a 0fr -> 1fr grid track, so the motion follows the real width, not a guessed maximum -->
			<span class="grid shrink-0 items-center transition-[grid-template-columns,opacity] duration-300 ease-out {strip && !on ? 'grid-cols-[0fr] opacity-0' : 'grid-cols-[1fr] opacity-100'}"><span class="flex min-w-0 items-center overflow-hidden"><Icon name={m.icon} size={22} fill={on} /></span></span>
			<span
				class="grid shrink-0 items-center transition-[grid-template-columns,opacity,margin] duration-300 ease-out {on || strip
					? utility && !on
						? 'ml-0 grid-cols-[0fr] opacity-0'
						: `grid-cols-[1fr] opacity-100 ${strip && !on ? 'ml-0' : 'ml-1.5'}`
					: utility
						? 'ml-0 grid-cols-[0fr] opacity-0'
						: 'ml-0 grid-cols-[0fr] opacity-0 xl:ml-1.5 xl:grid-cols-[1fr] xl:opacity-100'}"><span class="flex min-w-0 items-center overflow-hidden whitespace-nowrap leading-none">{t(`play.nav.${m.id}`)}</span></span>
		</button>
	</Tooltip>
{/snippet}

<header class="flex min-h-16 shrink-0 items-center gap-3 border-b border-(color:--kk-line) bg-(--kk-surface) px-4 md:px-6">
	<a href="/" class="flex shrink-0 items-center" aria-label={t('play.home')} data-own-guard onclick={leaveToPortal}>
		<!-- Symbol only, at every size. -->
		<Logo class="h-8" />
	</a>
	<nav data-tour="nav" aria-label={t('play.nav.label')} class="hidden min-w-0 items-center gap-1 md:ml-4 md:flex">
		{#each items as m (m.id)}{@render item(m)}{/each}
	</nav>
	<div class="ml-auto flex shrink-0 items-center gap-2">
		{#if me && !guest}
			<Tooltip text={t('play.currency.pingHint')}><span class="inline-flex h-10 items-center gap-2 rounded-xl bg-(--kk-surface-2) pr-4 pl-2 font-extrabold tabular-nums"><GameIcon name="ping" size={26} />{fmt(me.money)}<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.currency.ping')}</span></span></Tooltip>
			<Tooltip text={t('play.stat.winHint')}><span class="hidden h-10 items-center gap-2 rounded-xl bg-(--kk-surface-2) pr-4 pl-2 font-extrabold tabular-nums lg:inline-flex"><GameIcon name="win" size={26} />{fmt(me.wins)}<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.stat.win')}</span></span></Tooltip>
		{/if}
		<FullscreenButton class="max-md:hidden" />
		{#if phone && !guest}<MailMenu {store} />{/if}
		<div class="hidden items-center gap-1 md:flex">
			{#each utilities as m (m.id)}{#if m.id === 'mail'}<MailMenu {store} />{:else}{@render item(m, { utility: true })}{/if}{/each}
		</div>
		{#if guest && store.net.id}
			<a href="/login" rel="external" class="kk-btn kk-btn-primary inline-flex h-10 min-h-0 items-center gap-1.5 px-4 text-sm"><Icon name="login" size={20} />{t('play.signIn')}</a>
		{:else if store.net.id}
			<ProfileSwitcher profileSeed={store.net.id} profileName={me?.name ?? ''} accountLabel={store.net.guest ? t('play.guest') : ''} />
		{/if}
	</div>
</header>
