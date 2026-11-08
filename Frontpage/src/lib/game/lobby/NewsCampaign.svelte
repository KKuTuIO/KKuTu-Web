<script>
	import { onMount } from 'svelte';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import NewsGauge from './NewsGauge.svelte';
	import RewardBundle from './RewardBundle.svelte';
	import { t } from '../i18n.svelte.js';
	import { campaignView, clockText, hmsText, roundsToday } from '../news.js';

	/**
	 * 캠페인: everybody's play in a hot time adds up to one total on this page. Top: the total of everybody; below it my own part, the tier bar (the
	 * rewards of the highest tier reached are guaranteed to everyone who played in the hot time) and, when a hot time is over, its reward to take.
	 * The numbers are asked for again every few seconds while the page is open (the server does not push them).
	 * `compact`: the event page's header already has the title.
	 * @type {{store:any, event:any, shop?:any, details?:any, compact?:boolean}}
	 */
	let { store, event, shop = {}, details = null, compact = false } = $props();
	const net = $derived(store.net);
	const info = $derived(event.campaign);
	const status = $derived(net.campaigns[event.id]);
	let clock = $state(Date.now());
	const view = $derived(campaignView(status, info, clock));
	const today = $derived(roundsToday(info, clock));
	let pending = $state('');
	let historyOpen = $state(false);

	onMount(() => {
		store.loadCampaign(event.id);
		const poll = setInterval(() => store.loadCampaign(event.id), 5000);
		const tick = setInterval(() => (clock = Date.now()), 1000);
		return () => (clearInterval(poll), clearInterval(tick));
	});
	$effect(() => {
		status; // an answer ends the wait
		pending = '';
	});
	function take(round) {
		pending = round;
		store.claimCampaign(event.id, round);
		setTimeout(() => pending === round && (pending = ''), 4000);
	}

	const num = (n) => Number(n).toLocaleString();
	const time = (ms) => new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
	const day = (ms) => new Date(ms).toLocaleDateString(undefined, { month: 'numeric', day: 'numeric', weekday: 'short' });
	const span = (r) => `${day(r.startsAt)} ${time(r.startsAt)} ~ ${time(r.endsAt)}`;
	const countdown = (ms) => {
		const [a, b, kind] = clockText(ms);
		return kind === 'h' ? t('play.news.campaign.hm', { h: a, m: b }) : t('play.news.campaign.ms', { m: a, s: b });
	};
</script>

<section class="flex flex-col gap-4">
	{#if status?.past?.length}
		<div class="flex justify-end">
			<button type="button" onclick={() => (historyOpen = true)} class="kk-btn inline-flex h-9 min-h-0 items-center gap-1.5 px-3 text-sm"><Icon name="history" size={18} />{t('play.news.campaign.history')}</button>
		</div>
		{@render historyDialog()}
	{/if}
	{#if !compact}<h3 class="text-base font-extrabold">{t('play.news.campaign')}</h3>{/if}

	<!-- The hot time's clock, top centre: a black plate whose rim and label say which clock it is, the label sitting on the rim. While the hot time is on it
	     is fire (a flame, orange to red); while it is waited for it is cool (an hourglass, sky to indigo). Nothing at all once the campaign is over. -->
	{#if view.current || view.next}
		{@const live = !!view.current}
		<div class="flex flex-wrap items-center justify-center gap-x-5 gap-y-3">
		<div class="relative w-full max-w-sm pt-4" role="timer" aria-label={live ? t('play.news.campaign.hot') : t('play.news.campaign.untilHot')}>
			<span class="absolute top-0 left-1/2 z-10 inline-flex -translate-x-1/2 items-center gap-1.5 rounded-full px-5 py-1 text-base font-extrabold whitespace-nowrap text-white shadow-[0_2px_0_rgb(0_0_0/0.35)] {live ? 'bg-[#f97316]' : 'bg-[#4f6bed]'}">
				<Icon name={live ? 'local_fire_department' : 'hourglass_top'} size={20} fill />{live ? t('play.news.campaign.hot') : t('play.news.campaign.untilHot')}
			</span>
			<div class="rounded-3xl border-[5px] border-transparent px-4 pt-6 pb-3 text-center" style="background:{live ? 'radial-gradient(120% 100% at 50% 0%,#5a210b 0%,#241008 55%,#0c0604 100%)' : 'radial-gradient(120% 100% at 50% 0%,#1b3a6e 0%,#0e1b38 55%,#060b18 100%)'} padding-box,{live ? 'linear-gradient(180deg,#fb923c,#dc2626)' : 'linear-gradient(180deg,#7dd3fc,#6366f1)'} border-box">
				<b class="bg-gradient-to-b bg-clip-text text-5xl font-extrabold tabular-nums text-transparent @lg:text-6xl {live ? 'from-white via-[#ffe7cf] to-[#fb923c] drop-shadow-[0_0_14px_rgb(249_115_22/0.55)]' : 'from-white via-[#e0f2fe] to-[#7cb7ff] drop-shadow-[0_0_14px_rgb(96_165_250/0.5)]'}">{hmsText(live ? view.left : view.until)}</b>
				{#if !live}<p class="mt-1 text-xs font-bold text-white/70 tabular-nums">{span(view.next)}</p>{/if}
			</div>
		</div>
		{#if today.length}
			<ol aria-label={t('play.news.campaign.rounds')} class="flex min-w-48 flex-col gap-1.5 rounded-2xl bg-black/45 px-3 py-2.5 text-sm font-bold text-white shadow-[0_3px_0_rgb(0_0_0/0.25)]">
				{#each today as r (r.n)}
					<li class="flex items-center gap-2 tabular-nums {r.state === 'past' ? 'opacity-55' : ''}">
						<span class="font-extrabold">{t('play.news.campaign.round', { n: r.n })}</span>
						<span>{r.from}~{r.to}</span>
						{#if r.state === 'live'}<span class="ml-auto rounded-full bg-[#f97316] px-2 py-px text-xs font-extrabold">{t('play.news.campaign.roundLive')}</span>
						{:else if r.state === 'past'}<Icon name="check" size={16} class="ml-auto" />{/if}
					</li>
				{/each}
			</ol>
		{/if}
		</div>
	{:else}
		<p class="mx-auto rounded-full bg-black/40 px-4 py-1 text-sm font-extrabold text-white">{t('play.news.campaign.over')}</p>
	{/if}

	<!-- The total of everybody and my own part, side by side (they wrap under each other when the pane is narrow). -->
	<div class="flex flex-wrap gap-3">
		<section aria-label={t('play.news.campaign.total')} class="flex min-w-56 flex-1 flex-col items-center justify-center gap-1 rounded-2xl bg-(--kk-surface-2) px-4 py-4 text-center shadow-[0_3px_0_var(--kk-line)]">
			<span class="text-sm font-extrabold text-(color:--kk-muted)">{t('play.news.campaign.total')}</span>
			<b class="text-4xl font-extrabold tabular-nums text-(--ac,var(--kk-accent-strong)) @lg:text-5xl" aria-live="polite">{num(view.total)}<span class="ml-1 text-xl">{t('play.news.campaign.unit')}</span></b>
		</section>
		<section aria-label={t('play.news.campaign.mine')} class="flex min-w-56 flex-1 flex-col justify-center gap-2 rounded-2xl border border-(color:--kk-line) bg-(--kk-surface-2) p-4">
			<div class="flex items-baseline justify-between text-sm"><span class="font-extrabold">{t('play.news.campaign.mine')}</span><b class="text-lg tabular-nums">{num(view.mine)}{t('play.news.campaign.unit')}</b></div>
			{#if info.cap}<p class="text-xs font-bold text-(color:--kk-muted) tabular-nums">{t('play.news.campaign.cap', { n: num(info.cap) })}</p>{/if}
			{#if view.current}
				<p class="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
					<span class="inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-extrabold text-white {view.current.joined ? 'bg-(--kk-accent-strong)' : 'bg-(--kk-danger)'}" role="status">
						<Icon name={view.current.joined ? 'check_circle' : 'cancel'} size={16} fill />{view.current.joined ? t('play.news.campaign.eligible') : t('play.news.campaign.ineligible')}
					</span>
					<span><b class="font-extrabold">{t('play.news.campaign.condition')}</b>: {t('play.news.campaign.conditionText')}</span>
				</p>
			{/if}
		</section>
	</div>

	<!-- The gauge of the hot time, as wide as the page: one bar from 0 to the last checkpoint, and every checkpoint is a mark on it at its own share of
	     the way (a checkpoint at 150 of 600 sits a quarter in), with its reward above it and its points below. A reached checkpoint is checked. The track
	     scrolls sideways rather than squeeze checkpoints that lie close together. -->
	<section aria-label={t('play.news.campaign.rewards')} class="flex flex-col gap-2 rounded-2xl border-2 border-white/60 bg-black/45 p-3 shadow-[0_5px_0_rgb(0_0_0/0.25)]">
		<div class="flex flex-wrap items-baseline justify-between gap-x-3 text-white [text-shadow:0_1px_2px_rgb(0_0_0/0.6)]">
			<h4 class="text-sm font-extrabold">{view.goal ? t('play.news.campaign.toNext', { n: num(Math.max(0, view.goal.points - view.total)) }) : t('play.news.campaign.allDone')}</h4>
			{#if view.reached >= 0}<span class="text-xs font-bold">{t('play.news.campaign.guaranteed', { n: view.reached + 1 })}</span>{/if}
		</div>
		<NewsGauge points={view.total} tiers={info.tiers} {shop} {details} label={t('play.news.campaign.tiers')} />
	</section>

	{#if view.claimable.length}
		<section aria-label={t('play.news.campaign.claim')} class="flex flex-col gap-2">
			{#each view.claimable as past (past.key)}
				<div class="flex flex-col gap-2 rounded-xl border-[1.5px] border-(--ac,var(--kk-accent)) bg-(--kk-accent-soft) p-3 @md:flex-row @md:items-center">
					<div class="min-w-0 flex-1"><b class="text-sm font-extrabold">{span(past)}</b><span class="block text-xs font-bold text-(color:--kk-muted) tabular-nums">{t('play.news.campaign.result', { n: num(past.total), tier: past.tier + 1 })}</span></div>
					<RewardBundle rewards={info.tiers[past.tier].rewards} {shop} {details} />
					<button type="button" disabled={pending === past.key} onclick={() => take(past.key)} class="kk-btn kk-btn-primary inline-flex h-9 min-h-0 shrink-0 items-center justify-center px-4 text-sm">{t('play.news.claim')}</button>
				</div>
			{/each}
		</section>
	{/if}
</section>

{#snippet historyDialog()}
	<Dialog bind:open={historyOpen} title={t('play.news.campaign.history')}>
		<ul class="flex flex-col gap-1">
			{#each status.past.slice(0, 5) as past (past.key)}
				<li class="flex items-center gap-2 rounded-lg bg-(--kk-surface-2) px-3 py-1.5 text-xs font-bold tabular-nums">
					<span class="min-w-0 flex-1 truncate">{span(past)}</span>
					<span>{num(past.total)}{t('play.news.campaign.unit')}</span>
					<span class="w-14 text-right {past.tier >= 0 ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}">{past.tier >= 0 ? t('play.news.campaign.tierN', { n: past.tier + 1 }) : t('play.news.campaign.missed')}</span>
					<span class="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-extrabold text-white {past.joined ? 'bg-(--kk-accent-strong)' : 'bg-(--kk-danger)'}">{past.joined ? t('play.news.campaign.eligible') : t('play.news.campaign.ineligible')}</span>
					{#if past.joined && past.tier >= 0}<Icon name={past.claimed ? 'check_circle' : 'redeem'} size={16} fill class={past.claimed ? 'text-(color:--kk-accent-strong)' : 'text-(--ac,var(--kk-accent-strong))'} />{/if}
				</li>
			{/each}
		</ul>
	</Dialog>
{/snippet}
