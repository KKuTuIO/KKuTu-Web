<script>
	import { onMount, untrack } from 'svelte';
	import EmptyState from './EmptyState.svelte';
	import Icon from './Icon.svelte';
	import NewsCard from './NewsCard.svelte';
	import NewsDetail from './NewsDetail.svelte';
	import NewsRow from './NewsRow.svelte';
	import ObtainScreen from './ObtainScreen.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import { t } from '../i18n.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { loadGoodDetails } from '../shop.js';
	import { eventPhase, filterNews, isStage, orderNews, sectionOf } from '../news.js';
	import { whenText } from '../newsFormat.js';
	import { rise, swap } from '../motion.js';

	/**
	 * The 공지·행사 page. The server treats a notice and an event as the same thing (a notice is an entry with `bulletin`), so they share
	 * one catalog and one detail view; only the way they are listed differs.
	 *
	 * An event that is one stage (a tile roulette, a campaign) keeps all of this and only gives its pane to the event's own layout.
	 * It opens on an overview: events are cards, notices are rows. Opening one folds the list into a narrow rail beside it and the detail
	 * takes the rest, so the rail stays at hand while reading; a button at the top of the rail goes back to the overview. The page picks
	 * the layout from its own width, not the window's (the side bar can be dragged and the chat can dock): from 48rem the rail and the
	 * detail sit side by side; below that the detail takes the list's place with a way back.
	 * @type {{store:any}}
	 */
	let { store } = $props();
	const WIDE = 768; // 48rem at the usual 16px: the narrowest page that holds the rail (16rem) and a readable detail
	const net = $derived(store.net);
	let tab = $state('all');
	let selectedId = $state('');
	let now = $state(Date.now());
	let details = $state(null);
	let seen = $state(new Set());
	let width = $state(0);
	let folded = $state({ ended: true }); // the rail's groups the player folded; the ended ones start folded

	// What has not begun is not shown (the server keeps it back too): events can be set up long before their time without crowding the page.
	const all = $derived(net.events.list.filter((e) => eventPhase(e, now) !== 'upcoming'));
	const shown = $derived(orderNews(filterNews(all, tab), now));
	const audience = $derived(shown.filter((e) => sectionOf(e) === 'audience'));
	// the tab and the group of the events for newcomers or returning players: named for who they are for (a player is only ever one of them)
	const audienceLabel = $derived(t(`play.news.filter.${all.find((e) => sectionOf(e) === 'audience')?.audience?.kind ?? 'newcomer'}`));
	const campaigns = $derived(shown.filter((e) => sectionOf(e) === 'campaign'));
	const events = $derived(shown.filter((e) => sectionOf(e) === 'event'));
	const notices = $derived(shown.filter((e) => sectionOf(e) === 'notice'));
	const selected = $derived(all.find((e) => e.id === selectedId));
	const wide = $derived(width >= WIDE);
	// An event shown as one stage (a tile roulette, a campaign) keeps the tabs, the rail and the chat of the page; only its own pane changes: no big
	// header, the board as large as the pane allows.
	const stage = $derived(!!selected && isStage(selected));
	const tabs = $derived([
		{ id: 'all', label: t('play.news.filter.all'), count: all.length },
		...(all.some((e) => sectionOf(e) === 'audience') ? [{ id: 'audience', label: audienceLabel, count: all.filter((e) => sectionOf(e) === 'audience').length }] : []),
		{ id: 'campaign', label: t('play.news.filter.campaign'), count: all.filter((e) => sectionOf(e) === 'campaign').length },
		{ id: 'event', label: t('play.news.filter.event'), count: all.filter((e) => sectionOf(e) === 'event').length },
		{ id: 'notice', label: t('play.news.filter.notice'), count: all.filter((e) => sectionOf(e) === 'notice').length }
	]);
	const PILL = { ongoing: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)', upcoming: 'bg-(--kk-warn-soft) text-(color:--kk-warn)', ended: 'bg-(--kk-surface-2) text-(color:--kk-muted)' };

	// The rail: what is going in one list, and what is over (folded until asked for).
	const live = (e) => eventPhase(e, now) !== 'ended';
	const groups = $derived(
		[
			{ id: 'live', label: '', items: shown.filter(live) },
			{ id: 'ended', label: t('play.news.group.ended'), items: shown.filter((e) => !live(e)) }
		].filter((g) => g.items.length)
	);
	const isFolded = (g) => folded[g.id] && !g.items.some((e) => e.id === selectedId); // the group of the open entry stays open

	// Another tab that does not list the open entry goes back to the overview.
	$effect(() => {
		if (selectedId && !shown.some((e) => e.id === selectedId)) untrack(() => (selectedId = ''));
	});

	onMount(() => {
		seen = new Set(store.newsSeenIds ?? []); // what was new when the page opened keeps its mark until it is left
		store.loadEvents();
		store.markNewsSeen();
		loadGoodDetails().then((d) => (details = d));
		const tick = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(tick);
	});

	// What a claim or an exchange gave: the game's own "you got these" screen, so it looks like every other reward (a gacha, a mail). A reward the
	// screen has no cell for (event points) is said in a toast.
	let got = $state({ open: false, items: [], notice: '' });
	let lastReward = untrack(() => net.eventReward?.at ?? 0);
	$effect(() => {
		const reward = net.eventReward;
		if (!reward || reward.at === lastReward) return;
		lastReward = reward.at;
		const items = reward.rewards.filter((r) => r.type !== 'ep').map((r) => ({ key: r.type === 'ping' ? 'ping' : r.name, q: r.q }));
		const points = reward.rewards.filter((r) => r.type === 'ep').reduce((sum, r) => sum + r.q, 0);
		if (items.length) got = { open: true, items, notice: t(`play.news.obtain.${reward.source}`) };
		else toast(t(`play.news.obtain.${reward.source}`), { kind: 'success' });
		if (points) toast(t('play.news.obtain.ep', { n: points.toLocaleString() }), { kind: 'success' });
	});
	const fresh = (e) => eventPhase(e, now) === 'ongoing' && !seen.has(e.id);
</script>

<section data-tour="news" aria-label={t('play.news.title')} bind:clientWidth={width} in:rise|global class="@container flex min-h-0 flex-1 flex-col overflow-hidden kk-card">
	<div class="flex shrink-0 flex-col gap-2 border-b border-(color:--kk-line) p-2">
		<div class="min-w-0 overflow-x-auto [&_.kk-tab]:px-4 [&_.kk-tab]:whitespace-nowrap">
			<Tabs bind:value={tab} {tabs} idPrefix="news" label={t('play.news.title')} />
		</div>
	</div>

	<div role="tabpanel" id="news-panel-{tab}" aria-labelledby="news-tab-{tab}" class="flex min-h-0 flex-1">
		{#if !shown.length}
			<div class="grid flex-1 place-items-center overflow-y-auto"><EmptyState title={t('play.news.empty.title')} hint={t('play.news.empty.hint')} /></div>
		{:else if selected}
			{#if wide}
				<nav aria-label={t('play.news.title')} class="flex w-64 shrink-0 flex-col gap-1 overflow-y-auto border-r border-(color:--kk-line) p-2">
					<button type="button" onclick={() => (selectedId = '')} class="mb-1 flex items-center gap-1.5 rounded-lg px-1.5 py-1.5 text-sm font-extrabold text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)"><Icon name="grid_view" size={18} />{t('play.news.overview')}</button>
					{#each groups as group (group.id)}
						{#if group.label}
							<button type="button" aria-expanded={!isFolded(group)} aria-label={t('play.news.groupToggle', { name: group.label })} onclick={() => (folded[group.id] = !folded[group.id])} class="flex items-center gap-1 rounded-lg px-1.5 py-1 text-xs font-extrabold text-(color:--kk-muted) hover:bg-(--kk-surface-2)">
								<Icon name="expand_more" size={18} class="transition-transform {isFolded(group) ? '-rotate-90' : ''}" />{group.label}<span class="ml-auto tabular-nums">{group.items.length}</span>
							</button>
						{/if}
						{#if !isFolded(group)}
							{#each group.items as event (event.id)}<NewsRow {event} {now} selected={event.id === selectedId} fresh={fresh(event)} onpick={(id) => (selectedId = id)} />{/each}
						{/if}
					{/each}
				</nav>
			{/if}
			<div class="flex min-h-0 min-w-0 flex-1 flex-col">
				{#key selected.id}<div in:swap|global={{ dx: 24 }} class="flex min-h-0 flex-1 flex-col"><NewsDetail {store} event={selected} {now} shop={store.shop} {details} loading={!net.eventsLoaded} {stage} onback={wide ? null : () => (selectedId = '')} /></div>{/key}
			</div>
		{:else}
			<div class="min-h-0 min-w-0 flex-1 overflow-y-auto p-3 @md:p-4">
				<!-- one list of everything, going ones first: a card says what kind it is, so the page is not cut up by kind -->
				<ul class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,17rem),1fr))] gap-3" aria-label={t('play.news.title')}>
					{#each shown as event (event.id)}
						<li class="flex"><NewsCard {event} {now} shop={store.shop} {details} selected={event.id === selectedId} fresh={fresh(event)} onpick={(id) => (selectedId = id)} /></li>
					{/each}
				</ul>
			</div>
		{/if}
	</div>
</section>

{#if details}<ObtainScreen bind:open={got.open} title={t('play.obtain.acquired')} notice={got.notice} items={got.items} shop={store.shop} {details} ribbon ambient="shine" palette="teal" />{/if}
