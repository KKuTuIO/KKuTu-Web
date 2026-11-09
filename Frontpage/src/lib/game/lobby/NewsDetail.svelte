<script>
	import { onMount } from 'svelte';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Markdown from './Markdown.svelte';
	import NewsArtSparkles from './NewsArtSparkles.svelte';
	import NewsAttendance from './NewsAttendance.svelte';
	import NewsCampaign from './NewsCampaign.svelte';
	import NewsCampaignHistory from './NewsCampaignHistory.svelte';
	import NewsExchange from './NewsExchange.svelte';
	import NewsMissions from './NewsMissions.svelte';
	import NewsTile from './NewsTile.svelte';
	import NewsTrack from './NewsTrack.svelte';
	import NewsWaveTitle from './NewsWaveTitle.svelte';
	import NewsHowTo from './NewsHowTo.svelte';
	import NewsInvite from './NewsInvite.svelte';
	import TileTicket from './TileTicket.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import { t } from '../i18n.svelte.js';
	import { eventPhase, finishedCount, readyCount, safeAccent, safeUrl } from '../news.js';
	import { swap } from '../motion.js';
	import { drawsPossible } from '../tile.js';
	import { clockText, openLink, periodText } from '../newsFormat.js';

	/**
	 * What one event holds, in one frame for every event. The header is always the same: the title as wave text art on the left; on the right the clock
	 * (how long is left), the counters of what I have in this event (tickets, points, missions done, days attended) and the buttons of the parts that
	 * are always one press away, 행사 정보 and 교환소, which open in a window. Under it comes the event's main part (the board, the campaign, the
	 * missions and the attendance book) with the room it needs; an event with several of them has a tab for each.
	 * The art is decoration (the title, the text and every number are drawn here, so they follow the width). It is a side panel when the page is wide
	 * and replaces the list when it is narrow: the parent decides, this fills what it is given.
	 * `loading`: my progress has not arrived yet. `onback`: given only where the list is hidden. `stage`: the event is one main part that fills the pane.
	 * @type {{store:any, event:any, now:number, shop?:any, details?:any, loading?:boolean, stage?:boolean, onback?:(()=>void)|null}}
	 */
	let { store, event, now, shop = {}, details = null, loading = false, stage = false, onback = null } = $props();
	const net = $derived(store.net);
	const look = $derived(event.presentation ?? {});
	const accent = $derived(safeAccent(look.accent));
	const background = $derived(safeUrl(look.background));
	const titleArt = $derived(safeUrl(look.titleArt));
	const cutout = $derived(safeUrl(look.cutout));
	const onArt = $derived(!background); // the gradient backdrop: the header's small text is white on a dark chip
	const phase = $derived(eventPhase(event, now));
	const playable = $derived(phase === 'ongoing'); // mission and attendance claims; an exchange also works while an ended event's exchange period lasts
	const exchangeable = $derived(phase !== 'upcoming');
	const blocks = $derived(look.blocks ?? []);
	const period = $derived(periodText(event));
	const BTN = 'kk-btn kk-btn-primary inline-flex h-9 min-h-0 items-center gap-1.5 px-4 text-sm';

	// the clock of the header ticks every second
	let clock = $state(Date.now());
	onMount(() => {
		const id = setInterval(() => (clock = Date.now()), 1000);
		return () => clearInterval(id);
	});
	const timeLeft = $derived(clockText(event, Math.max(clock, now)));

	// Which parts the event really has (a block of a module the event does not have is not drawn).
	const has = (type) => blocks.some((b) => b.type === type && (type !== 'attendance' || event.attendance) && (type !== 'tileRoulette' || event.tile) && (type !== 'campaign' || event.campaign) && (type !== 'invite' || event.invite));
	const MAINS = ['tileRoulette', 'campaign', 'invite', 'missions', 'attendance'];
	const mains = $derived(MAINS.filter(has));
	const hasExchange = $derived(has('exchange'));
	const hasMain = $derived(mains.length > 0);
	// the board or the campaign is the pane itself; with it as the only main part the others (missions, attendance) wait behind buttons too
	const full = $derived(stage && (mains.includes('tileRoulette') || mains.includes('campaign')));
	const plain = $derived(blocks.filter((b) => !MAINS.includes(b.type) && b.type !== 'exchange'));
	const book = $derived(net.eventProgress.attendance.find((a) => a.eventId === event.id));
	const ready = $derived(readyCount(event.missions, net.eventProgress, event.id)); // missions waiting to be claimed
	const unchecked = $derived(playable && !book?.todayClaimed ? 1 : 0); // today's check-in is still to do
	const WAITING = $derived({ missions: ready, attendance: unchecked });

	// the counters of the header
	let boardOdds = $state(false); // the board's chances, switched from the header
	let boardTickets = $state(0); // what the board shows (it holds the number still during a run)
	const tickets = $derived(event.tile ? boardTickets : 0);
	const campaignMine = $derived(net.campaigns[event.id]?.current?.mine ?? 0);
	const counters = $derived(
		loading
			? []
			: [
					...(mains.includes('tileRoulette') ? [{ key: 'tile', token: event.tile.token, text: `×${tickets.toLocaleString()}`, label: `${t('play.news.tile.token')} ${tickets}` }] : []),
					...(event.point !== undefined ? [{ key: 'ep', icon: 'stars', text: Number(event.point).toLocaleString(), label: t('play.news.reward.ep') }] : []),
					...(mains.includes('campaign') && net.campaigns[event.id]?.current ? [{ key: 'mine', icon: 'groups', text: `${campaignMine.toLocaleString()}${t('play.news.campaign.unit')}`, label: t('play.news.campaign.mine') }] : []),
					...(mains.includes('missions') ? [{ key: 'missions', icon: 'task_alt', text: `${finishedCount(event.missions, net.eventProgress, event.id)}/${event.missions?.length ?? 0}`, label: t('play.news.missions') }] : []),
					...(mains.includes('attendance') ? [{ key: 'attendance', icon: 'event_available', text: t('play.news.summary.attendance', { n: book?.attended ?? 0 }), label: t('play.news.attendance') }] : []),
					...(ready ? [{ key: 'ready', icon: 'redeem', text: t('play.news.summary.ready', { n: ready }), label: t('play.news.summary.ready', { n: ready }), hot: true }] : [])
				]
	);

	// The buttons of the header: the parts that are one press away and open in a window. An event with no main part is its text and its exchange on
	// one page, and has no use for them.
	const SHEET_ICON = { info: 'info', history: 'history', missions: 'task_alt', attendance: 'event_available', exchange: 'storefront' };
	const sheets = $derived(
		!hasMain
			? []
			: [
					{ id: 'info', label: t('play.news.info'), count: 0 },
					...(has('campaign') && net.campaigns[event.id]?.past?.length ? [{ id: 'history', label: t('play.news.campaign.history'), count: 0 }] : []),
					...(full ? mains.filter((id) => id === 'missions' || id === 'attendance').map((id) => ({ id, label: t(`play.news.${id}`), count: WAITING[id] ?? 0 })) : []),
					...(hasExchange ? [{ id: 'exchange', label: t('play.news.exchange'), count: 0 }] : [])
				]
	);
	let sheetId = $state('');
	let sheetOpen = $state(false);
	const sheet = $derived(sheets.find((x) => x.id === sheetId));
	const sheetBlocks = $derived(sheetId === 'info' ? plain : blocks.filter((b) => b.type === sheetId));
	const openSheet = (id) => {
		sheetId = id;
		sheetOpen = true;
	};

	// The tabs of an event with several main parts; the body is that part (or the whole event when it has none).
	const pages = $derived(full ? [] : mains.map((id) => ({ id, label: t(`play.news.${id}`), count: WAITING[id] ?? 0, alert: true })));
	const paged = $derived(pages.length >= 2);
	let tab = $state('');
	// the first page opens at first, and again if the open one is gone
	$effect.pre(() => {
		if (pages.length && !pages.some((p) => p.id === tab)) tab = pages[0].id;
	});
	const page = $derived(tab);
	const shownBlocks = $derived(
		full ? blocks.filter((b) => b.type === 'tileRoulette' || b.type === 'campaign') : !hasMain ? blocks : paged ? blocks.filter((b) => b.type === page) : [...plain.filter((b) => b.type === 'rewardTrack'), ...blocks.filter((b) => b.type === mains[0])]
	);
	// the new page slides in from the side it lies on (set before the page is drawn, so the motion knows which)
	let dx = $state(24);
	let was = 0;
	$effect.pre(() => {
		const i = pages.findIndex((p) => p.id === tab);
		dx = i >= was ? 24 : -24;
		was = Math.max(0, i);
	});
	const chip = $derived(onArt ? 'bg-black/40 text-white' : 'bg-(--kk-surface-2) text-(color:--kk-text)');
</script>

{#snippet blockView(block)}
			{#if block.type === 'text'}
		<section class="flex flex-col gap-1.5">
			{#if block.title}<h3 class="text-base font-extrabold">{block.title}</h3>{/if}
			<Markdown source={block.body} onlink={openLink} />
		</section>
	{:else if block.type === 'note'}
		<p class="flex items-start gap-2 rounded-xl px-3.5 py-2.5 text-sm font-bold {block.tone === 'warn' ? 'bg-(--kk-warn-soft) text-(color:--kk-warn)' : 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)'}">
			<Icon name={block.tone === 'warn' ? 'warning' : 'info'} size={20} fill class="mt-px shrink-0" /><span>{block.body}</span>
		</p>
	{:else if block.type === 'image'}
		{#if safeUrl(block.src)}<img src={safeUrl(block.src)} alt={block.alt} loading="lazy" decoding="async" draggable="false" class="h-auto max-w-full rounded-xl" />{/if}
	{:else if block.type === 'button'}
		{#if safeUrl(block.link)}<button type="button" onclick={() => openLink(safeUrl(block.link))} class="{BTN} w-fit">{block.label}<Icon name="open_in_new" size={18} /></button>{/if}
	{:else if block.type === 'rewardTrack'}
		<NewsTrack {store} {event} {block} {shop} {details} />
	{:else if block.type === 'missions'}
		<NewsMissions {store} {event} {shop} {details} {playable} />
	{:else if block.type === 'attendance' && event.attendance}
		<NewsAttendance {store} {event} {shop} {details} {playable} />
	{:else if block.type === 'invite' && event.invite}
		<NewsInvite {store} {event} {shop} {details} {playable} />
	{:else if block.type === 'campaign' && event.campaign}
		<NewsCampaign {store} {event} {shop} {details} />
	{:else if block.type === 'tileRoulette' && event.tile}
		<NewsTile {store} {event} {shop} {details} {playable} />
	{:else if block.type === 'exchange'}
		<NewsExchange {store} {event} {shop} {details} open={exchangeable} />
	{/if}
{/snippet}

{#snippet backButton()}
	{#if onback}<button type="button" onclick={onback} class="kk-btn kk-hdr"><Icon name="arrow_back" size={18} />{t('play.news.back')}</button>{/if}
{/snippet}

<!-- Everything the header shows about the event, in one row that wraps: the clock, the counters and the buttons share one size and one shape, only
     the colour tells them apart (dark clock, amber counters, red for something waiting, plain buttons that can be pressed). -->
{#snippet cluster()}
	<div class="flex flex-wrap items-center justify-end gap-2">
		{#if counters.length}
			<ul class="contents" aria-label={t('play.news.summary.label')}>
				{#each counters as c (c.key)}
					<li aria-label={c.label} class="kk-hdr {c.hot ? 'kk-hdr-hot' : 'kk-hdr-count'}">
						{#if c.token}<TileTicket size={26} />{:else if c.icon}<Icon name={c.icon} size={16} fill />{/if}{c.text}
					</li>
				{/each}
			</ul>
		{/if}
		{#if full && mains.includes('tileRoulette')}<button type="button" aria-pressed={boardOdds} onclick={() => (boardOdds = !boardOdds)} class="kk-btn kk-hdr">{t(boardOdds ? 'play.shop.prob.hide' : 'play.shop.prob.show')}</button>{/if}
		{#each sheets as item (item.id)}
			<button type="button" onclick={() => openSheet(item.id)} class="kk-btn kk-hdr">
				<Icon name={SHEET_ICON[item.id]} size={16} />{item.label}{#if item.count}<span class="grid h-4 min-w-4 place-items-center rounded-full bg-(--kk-danger) px-1 text-[10px] leading-none font-extrabold text-white tabular-nums">{item.count}</span>{/if}
			</button>
		{/each}
		<span class="kk-hdr kk-hdr-time" role="timer" aria-label={t('play.news.clock.label')}><Icon name="schedule" size={18} fill />{timeLeft}</span>
	</div>
{/snippet}

<article style:--ac={accent || undefined} style:--chrome="13rem" class="@container relative flex min-h-0 flex-1 flex-col overflow-hidden bg-(--kk-surface)">
	{#if !full}
	<header class="relative shrink-0 overflow-hidden border-b border-(color:--kk-line) {onArt ? 'kk-arcade' : ''}" style={onArt ? '' : 'background:var(--kk-surface)'}>
		{#if onArt}
			<NewsArtSparkles />
		{:else}
			<img src={background} alt="" decoding="async" draggable="false" class="absolute inset-0 size-full object-cover opacity-90" />
			<span class="absolute inset-0 bg-gradient-to-r from-(--kk-surface)/85 via-(--kk-surface)/55 to-transparent" aria-hidden="true"></span>
		{/if}
		{#if cutout}<img src={cutout} alt="" decoding="async" draggable="false" class="pointer-events-none absolute right-72 bottom-0 hidden h-[110%] max-h-40 object-contain @3xl:block" />{/if}
		<div class="relative flex flex-wrap items-start justify-between gap-x-4 gap-y-2 p-3 @lg:p-4">
			<div class="flex min-w-0 flex-col items-start gap-1.5">
				{@render backButton()}
				{#if titleArt}<img src={titleArt} alt="" decoding="async" draggable="false" class="max-h-20 w-auto max-w-full object-contain" /><h2 class="sr-only">{event.name}</h2>
				{:else}<NewsWaveTitle text={event.name} class="text-3xl @lg:text-4xl" />{/if}
				{#if look.summary}<p class="max-w-prose rounded-lg px-2.5 py-1 text-sm font-bold {onArt ? 'bg-black/40 text-white' : 'bg-(--kk-surface-2)'}">{look.summary}</p>{/if}
			</div>
			{@render cluster()}
		</div>
	</header>
	{/if}

	{#if paged}
		<div class="shrink-0 overflow-x-auto border-b border-(color:--kk-line) px-2 [&_.kk-tab]:px-4 [&_.kk-tab]:whitespace-nowrap">
			<Tabs bind:value={tab} tabs={pages} idPrefix="newsd" label={event.name} />
		</div>
	{/if}

	{#if full}
		<!-- The board or the campaign has no header of its own: the pane is the arcade backdrop all the way up. The way back and the title sit at the top
		     left in the flow; what the header would show (the clock, the counters, the buttons) is laid at the top right, over the pane, once it is wide
		     enough to hold them next to the title, and in a row of its own under the title when it is not. -->
		<div class="relative min-h-0 flex-1 overflow-y-auto p-3 @lg:p-4 {onArt ? 'kk-arcade' : ''}" style={onArt ? '' : `background:linear-gradient(90deg,var(--kk-surface),color-mix(in srgb,var(--kk-surface) 78%,transparent)),url(${background}) center/cover`}>
			{#if onArt}<NewsArtSparkles />{/if}
			<div class="relative mb-3 flex flex-wrap items-center gap-x-3 gap-y-2">
				{@render backButton()}
				{#if titleArt}<img src={titleArt} alt="" decoding="async" draggable="false" class="max-h-14 w-auto max-w-full object-contain" /><h2 class="sr-only">{event.name}</h2>
				{:else}<NewsWaveTitle text={event.name} class="text-2xl @lg:text-3xl" />{/if}
			</div>
			<div class="relative mb-3 @5xl:absolute @5xl:top-3 @5xl:right-3 @5xl:z-10 @5xl:mb-0">{@render cluster()}</div>
			<div class="relative">
				{#if mains.includes('tileRoulette')}
					<NewsTile {store} {event} {shop} {details} {playable} compact bind:shown={boardTickets} bind:odds={boardOdds} />
				{:else}
					<NewsCampaign {store} {event} {shop} {details} compact />
				{/if}
			</div>
		</div>
	{:else}
		<div role={paged ? 'tabpanel' : undefined} id={paged ? `newsd-panel-${page}` : undefined} aria-labelledby={paged ? `newsd-tab-${page}` : undefined} class="min-h-0 flex-1 overflow-y-auto">
		{#key page}
		<div in:swap|global={{ dx }} class="flex flex-col gap-5 p-4 @lg:p-6">
			{#if !hasMain}
				{#if period}<p class="text-xs text-(color:--kk-muted) tabular-nums">{period}</p>{/if}
				{#if event.description}<Markdown source={event.description} onlink={openLink} />{/if}
			{/if}

			{#if loading && !blocks.length}<p class="text-sm text-(color:--kk-muted)" role="status">{t('play.news.loading')}</p>{/if}

			{#each shownBlocks as block, index (block.type + index)}
				{@render blockView(block)}
			{/each}
		</div>
		{/key}
		</div>
	{/if}
</article>

{#if sheets.length}
	<!-- The parts that are one press away, each in a window of its own. The window is its own container, so the blocks fit its width. -->
	<Dialog bind:open={sheetOpen} title={sheet?.label ?? ''} size="lg">
		<div style:--ac={accent || undefined} class="@container flex max-h-[75dvh] flex-col gap-5 overflow-y-auto">
			{#if sheetId === 'info'}
				<NewsHowTo {event} />
				{#if period}<p class="text-xs text-(color:--kk-muted) tabular-nums">{period}</p>{/if}
				{#if event.description}<Markdown source={event.description} onlink={openLink} />{/if}
			{/if}
			{#if sheetId === 'history'}<NewsCampaignHistory past={net.campaigns[event.id]?.past ?? []} />{/if}
			{#each sheetBlocks as block, index (block.type + index)}{@render blockView(block)}{/each}
		</div>
	</Dialog>
{/if}
