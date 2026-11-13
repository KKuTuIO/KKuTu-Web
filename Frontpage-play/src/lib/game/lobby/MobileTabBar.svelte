<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Dialog from './Dialog.svelte';
	import Nudge from './Nudge.svelte';
	import Mascot from './Mascot.svelte';
	import { closeNudge, nudgeOpen } from '../nudges.svelte.js';
	import { haptic } from '../haptics.js';

	/**
	 * Phone navigation: a tab bar at the bottom, where the thumb is. The main places are tabs; the rest are in the 더보기 sheet.
	 * A guest has no announcements, so 도움말 takes that tab (with 학사모레미's nudge) and 이어하기 moves into the sheet.
	 * The mailbox lives in the header. Desktop keeps TopBar's menu.
	 */
	let { store, active = 'lobby', onselect } = $props();
	const guest = $derived(store.net.guest);
	let more = $state(false);
	const tabs = $derived([
		{ id: 'lobby', icon: 'home' },
		{ id: 'people', icon: 'group', badge: store.pendingFriends.incoming.length },
		{ id: 'shop', icon: 'storefront' },
		guest ? { id: 'help', icon: 'help' } : { id: 'news', icon: 'campaign', dot: store.newsAlerts.length > 0 }
	]);
	const extra = $derived([
		{ id: 'rank', icon: 'trophy' },
		{ id: 'dict', icon: 'menu_book' },
		{ id: 'replay', icon: 'replay' },
		guest ? { id: 'progress', icon: 'cloud_upload' } : { id: 'help', icon: 'help' },
		{ id: 'settings', icon: 'settings' }
	]);
	const moreActive = $derived(['rank', 'dict', 'replay'].includes(active));
	const ready = $derived(store.net.status === 'ready' && store.net.assetsReady && !store.currentRoom);
	const label = (id) => t(`play.nav.${id}`);
	function pick(id) {
		haptic();
		more = false;
		if (id === 'help') closeNudge('tutorial');
		onselect?.(id);
	}
</script>

{#snippet tab(m)}
	{@const on = m.id === active}
	<button type="button" data-tour={m.id === 'people' ? 'side' : m.id === 'help' ? 'help' : undefined} aria-current={on ? 'page' : undefined} onclick={() => pick(m.id)} class="relative flex min-h-14 w-full flex-col items-center justify-center gap-0.5 text-[11px] leading-none font-bold {on ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}">
		<span class="grid h-7 w-14 place-items-center rounded-full transition-colors {on ? 'bg-(--kk-accent-soft)' : ''}"><Icon name={m.icon} size={22} fill={on} /></span>
		{label(m.id)}
		{#if m.badge}<span class="absolute top-1.5 left-1/2 ml-2 inline-grid h-4 min-w-4 place-items-center rounded-full bg-(--kk-danger) px-1 text-[10px] font-extrabold text-white">{m.badge}</span>
		{:else if m.dot}<span class="absolute top-2 left-1/2 ml-3 size-2 rounded-full bg-(--kk-danger)" aria-hidden="true"></span>{/if}
	</button>
{/snippet}

<nav data-tour="nav" aria-label={t('play.nav.label')} class="grid shrink-0 grid-cols-5 border-t border-(color:--kk-line) bg-(--kk-surface) pb-[env(safe-area-inset-bottom)]">
	{#each tabs as m (m.id)}
		{#if m.id === 'help'}
			<!-- 학사모레미 points at 도움말 until it is opened -->
			<Nudge id="tutorial" above enabled={ready}>
				{#snippet content()}<Mascot {store} size={44} /><span>{t('play.nudge.tutorial')}</span>{/snippet}
				{@render tab(m)}
			</Nudge>
		{:else if m.id === 'news'}
			<Nudge id="news" above enabled={ready && !nudgeOpen('tutorial') && active !== 'news' && !!store.newsNudge}>
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
	<Nudge id="tutorial" above enabled={ready && !guest}>
		{#snippet content()}<Mascot {store} size={44} /><span>{t('play.nudge.tutorial')}</span>{/snippet}
		<button type="button" data-tour={guest ? undefined : 'help'} aria-expanded={more} onclick={() => (haptic(), (more = !more))} class="flex min-h-14 w-full flex-col items-center justify-center gap-0.5 text-[11px] leading-none font-bold {moreActive || more ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}">
			<span class="grid h-7 w-14 place-items-center rounded-full transition-colors {moreActive || more ? 'bg-(--kk-accent-soft)' : ''}"><Icon name="more_horiz" size={22} /></span>
			{t('play.nav.more')}
		</button>
	</Nudge>
</nav>

<Dialog bind:open={more} title={t('play.nav.more')}>
	<div role="menu" class="-m-2 flex flex-col">
		{#each extra as m (m.id)}
			<button type="button" role="menuitem" onclick={() => pick(m.id)} class="flex min-h-12 w-full items-center gap-3 rounded-xl px-3 text-left text-base font-bold hover:bg-(--kk-surface-2)">
				<Icon name={m.icon} size={24} />{label(m.id)}
			</button>
		{/each}
	</div>
</Dialog>
