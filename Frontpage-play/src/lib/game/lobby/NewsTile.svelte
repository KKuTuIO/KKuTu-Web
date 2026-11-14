<script>
	import { onMount, untrack } from 'svelte';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import ObtainScreen from './ObtainScreen.svelte';
	import ShopThumb from './ShopThumb.svelte';
	import TileTicket from './TileTicket.svelte';
	import PriceChip from './PriceChip.svelte';
	import NewsGauge from './NewsGauge.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { playSound } from '../audio.svelte.js';
	import { reducedMotion } from '../motion.js';
	import { rewardOf } from '../news.js';
	import { rewardLabel } from '../newsFormat.js';
	import { COLUMNS, drawsPossible, gaugeView, ownedTiles, planSweep } from '../tile.js';

	/**
	 * 타일 룰렛. A 5x5 board: a ticket is spent, a highlight runs over the tiles with a tick for every hop, then suddenly slows and stops on one with
	 * the success sound, and that tile's reward is given. Which tile it stops on was decided by the server before the highlight starts moving
	 * (the answer is already in `net.tileResult`); this only shows the way there. A reward that is given once and is already owned still comes up,
	 * with a check and "보유 중" on its tile and a note that nothing was given.
	 *
	 * While the highlight runs, the tickets, the gauge and the "보유 중" marks stay as they were before the draw, so nothing is given away early
	 * (the server's `obtain` has already changed the box); they catch up when the run ends.
	 * `compact`: the event page has the title, the tickets and the button of the chances in its top right, so there is no top row here. `odds` (the
	 * chances shown over the tiles) and `shown` (the tickets this board shows, held still during a run) are given to the page for that.
	 * @type {{store:any, event:any, shop?:any, details?:any, playable:boolean, compact?:boolean, shown?:number, odds?:boolean}}
	 */
	let { store, event, shop = {}, details = null, playable, compact = false, shown = $bindable(0), odds = $bindable(false) } = $props();
	const net = $derived(store.net);
	const tile = $derived(event.tile);
	const cells = $derived(tile.cells);
	const owns = (id) => (net.box?.[id]?.value ?? 0) > 0;

	let frozen = $state(null); // what the board showed before the draw: {tokens, points, owned}
	const tokens = $derived(frozen ? frozen.tokens : (net.box?.[tile.token]?.value ?? 0));
	const points = $derived(frozen ? frozen.points : (tile.gauge?.points ?? 0));
	const owned = $derived(frozen ? frozen.owned : ownedTiles(cells, owns));
	const gauge = $derived(gaugeView(tile.gauge ? { ...tile.gauge, points } : undefined));

	$effect(() => {
		shown = tokens;
	});
	let phase = $state('idle'); // idle | waiting (for the server's answer) | playing
	let lit = $state(-1); // the tile the highlight is on
	let landed = $state(new Set()); // tiles that came up in this round
	let last = $state(-1); // the one that came up last
	let note = $state(''); // what the last tile gave, for the eye and for screen readers
	let got = $state({ open: false, items: [], notice: '' });
	const busy = $derived(phase !== 'idle');
	const canDraw = (count) => playable && !busy && drawsPossible(tokens, tile.cost) >= count;

	let alive = true;
	let skipping = false;
	let wake = null;
	let waitTimer;
	let seen = untrack(() => net.tileResult?.at ?? 0);
	onMount(() => () => {
		alive = false;
		clearTimeout(waitTimer);
		wake?.();
	});
	const sleep = (ms) =>
		new Promise((resolve) => {
			if (skipping || reducedMotion() || !alive) return resolve();
			const id = setTimeout(() => ((wake = null), resolve()), ms);
			wake = () => (clearTimeout(id), (wake = null), resolve());
		});

	function start(count) {
		if (!canDraw(count)) return;
		frozen = { tokens, points, owned: new Set(owned) };
		phase = 'waiting';
		landed = new Set();
		last = -1;
		lit = -1;
		note = '';
		skipping = false;
		store.drawTile(event.id, count);
		// no answer (an error came back instead, which is said in a toast): the board is free again
		waitTimer = setTimeout(() => phase === 'waiting' && reset(), 6000);
	}
	function reset() {
		frozen = null;
		phase = 'idle';
		lit = -1;
	}
	function skip() {
		skipping = true;
		wake?.();
	}

	$effect(() => {
		const result = net.tileResult;
		if (!result || result.at === seen || result.eventId !== event.id || phase !== 'waiting') return;
		seen = result.at;
		clearTimeout(waitTimer);
		untrack(() => run(result));
	});

	async function hopTo(final, fast) {
		const plan = planSweep(final, { fast });
		for (let i = 0; i < plan.path.length - 1 && !skipping && alive; i++) {
			lit = plan.path[i];
			playSound('As10');
			await sleep(plan.delays[i]);
		}
		lit = final;
	}

	async function run(result) {
		phase = 'playing';
		const many = result.draws.length > 1;
		for (const draw of result.draws) {
			if (!alive) return;
			await hopTo(draw.cell, many ? 12 : 26);
			if (!alive) return;
			landed = new Set([...landed, draw.cell]);
			last = draw.cell;
			playSound('success');
			const reward = rewardOf(cells[draw.cell].reward);
			const name = rewardLabel(reward, details);
			note = draw.owned ? t('play.news.tile.alreadyOwned', { name }) : reward.q > 1 ? t('play.news.tile.wonQty', { name, q: reward.q.toLocaleString() }) : t('play.news.tile.won', { name });
			await sleep(many ? 550 : 350);
		}
		finish(result);
	}

	function finish(result) {
		frozen = null; // the tickets, the gauge and the marks catch up
		phase = 'idle';
		lit = -1;
		const items = result.rewards.filter((r) => r.type !== 'ep').map((r) => ({ key: r.type === 'ping' ? 'ping' : r.name, q: r.q }));
		const reached = result.gauge?.reached?.length ?? 0;
		if (items.length) got = { open: true, items, notice: t(reached ? 'play.news.obtain.tileGauge' : 'play.news.obtain.tile') };
		const eventPoints = result.rewards.filter((r) => r.type === 'ep').reduce((sum, r) => sum + r.q, 0);
		if (eventPoints) toast(t('play.news.obtain.ep', { n: eventPoints.toLocaleString() }), { kind: 'success' });
	}

	const tileLabel = (cell) => {
		const reward = rewardOf(cell.reward);
		return `${rewardLabel(reward, details)}${reward.q > 1 ? ` ×${reward.q.toLocaleString()}` : ''}`;
	};
	const fmtP = (p) => `${+p.toFixed(p < 1 ? 3 : 2)}%`; // as the shop writes a chance
</script>

<section class="flex flex-col gap-3">
	{#if !compact}
		<div class="flex flex-wrap items-center gap-x-3 gap-y-2">
			<h3 class="text-base font-extrabold">{t('play.news.tileRoulette')}</h3>
			<span class="inline-flex h-9 items-center gap-1.5 rounded-xl bg-(--kk-surface-2) pr-3 pl-1.5 font-extrabold tabular-nums" aria-label="{t('play.news.tile.token')} {tokens}">
				<TileTicket size={30} />×{tokens.toLocaleString()}
			</span>
			<button type="button" aria-pressed={odds} onclick={() => (odds = !odds)} class="kk-btn ml-auto h-9 min-h-0 px-3 text-sm">{t(odds ? 'play.shop.prob.hide' : 'play.shop.prob.show')}</button>
		</div>
	{/if}

	<div class="flex flex-col gap-4 @2xl:flex-row @2xl:items-start">
		<div class="flex shrink-0 flex-col gap-2 @2xl:w-[clamp(15rem,calc(100dvh-var(--chrome,21rem)),26rem)]">
		<ol aria-label={t('play.news.tile.board')} class="grid gap-1 rounded-2xl border border-white/25 bg-black/55 p-1.5 shadow-[0_6px_0_rgb(0_0_0/0.25)]" style:grid-template-columns="repeat({COLUMNS}, minmax(0, 1fr))">
			{#each cells as cell, i (i)}
				{@const reward = rewardOf(cell.reward)}
				{@const isOwned = owned.has(i)}
				<li
					aria-label="{tileLabel(cell)}{isOwned ? ` — ${t('play.news.tile.owned')}` : ''}"
					class="relative aspect-square rounded-xl border-[1.5px] transition-[transform,box-shadow,background-color] duration-100 {i === lit ? 'z-10 scale-110 border-(--ac,var(--kk-accent)) bg-(--kk-accent-soft) shadow-[0_0_0_3px_var(--ac,var(--kk-accent))]' : last === i && phase === 'idle' ? 'border-(--ac,var(--kk-accent)) bg-(--kk-accent-soft)' : landed.has(i) ? 'border-(--ac,var(--kk-accent)) bg-(--kk-surface)' : 'border-(color:--kk-line) bg-(--kk-surface)'} {i === lit && phase === 'idle' ? 'motion-safe:animate-pulse' : ''}"
				>
					<Tooltip text={rewardLabel(reward, details)} placement="top">
						<span class="absolute inset-0 grid place-items-center p-1 {isOwned ? 'opacity-45' : ''}">
							{#if reward.kind === 'item'}<ShopThumb id={reward.id ?? ''} {shop} class="size-3/5" />
							{:else if reward.kind === 'ping'}<span class="flex flex-col items-center gap-0.5 text-[11px] leading-none font-extrabold tabular-nums"><GameIcon name="ping" size={22} />{reward.q.toLocaleString()}</span>
							{:else}<span class="flex flex-col items-center gap-0.5 text-[11px] leading-none font-extrabold tabular-nums"><Icon name="stars" size={22} fill class="text-(color:--kk-warn)" />{reward.q.toLocaleString()}</span>{/if}
						</span>
					</Tooltip>
					{#if reward.kind === 'item' && reward.q > 1}<b class="pointer-events-none absolute right-0.5 bottom-0 rounded-md bg-(--kk-surface)/90 px-1 text-[10px] leading-4 whitespace-nowrap tabular-nums {isOwned ? 'hidden' : ''}">×{reward.q.toLocaleString()}</b>{/if}
					{#if isOwned}
						<Icon name="check_circle" size={20} fill class="pointer-events-none absolute top-0.5 right-0.5 rounded-full bg-(--kk-surface) text-(color:--kk-accent-strong)" />
						<span class="pointer-events-none absolute inset-x-0 bottom-0 rounded-b-[10px] bg-(--kk-accent-strong)/90 py-px text-center text-[10px] leading-4 font-extrabold text-white">{t('play.news.tile.owned')}</span>
					{/if}
					{#if odds}<span class="pointer-events-none absolute inset-0 z-20 grid place-items-center rounded-xl bg-black/70 text-sm font-extrabold text-white tabular-nums">{fmtP(cell.percent)}</span>{/if}
				</li>
			{/each}
		</ol>
		{#if odds}<p class="text-xs font-bold text-white [text-shadow:0_1px_2px_rgb(0_0_0/0.6)]">{t('play.news.tile.oddsNote')}</p>{/if}
		</div>

		<div class="flex min-w-0 flex-1 flex-col gap-3">
			<p role="status" aria-live="polite" class="min-h-10 rounded-xl bg-(--kk-surface-2) px-3 py-2 text-sm font-bold {note ? '' : 'text-(color:--kk-muted)'}">{note || t('play.news.tile.idle')}</p>

			<!-- the shop's draw buttons: the label, and the price as a pill (here the tickets) -->
			<div class="grid grid-cols-[repeat(auto-fit,minmax(8rem,1fr))] gap-3">
				{#each [1, ...tile.multi] as count (count)}
					<button type="button" disabled={!canDraw(count)} onclick={() => start(count)} class="kk-btn kk-btn-primary flex h-auto min-h-0 flex-col items-center gap-1.5 py-3 leading-tight">
						<span class="text-base font-extrabold">{count === 1 ? t('kkutu.product.shopList.draw.one') : t('play.news.tile.drawN', { n: count })}</span>
						<PriceChip price={tile.cost * count}>{#snippet glyph()}<TileTicket size={28} />{/snippet}</PriceChip>
					</button>
				{/each}
			</div>
			{#if phase === 'playing'}<button type="button" onclick={skip} class="kk-btn inline-flex h-9 min-h-0 items-center justify-center gap-1.5 px-4 text-sm"><Icon name="skip_next" size={20} />{t('play.news.tile.skip')}</button>{/if}
			{#if gauge}
			<!-- the mileage: the same gauge as the campaign's, in the column beside the board, the checkpoints at their share of the way -->
			<section aria-label={t('play.news.tile.gauge')} class="flex flex-col gap-2 rounded-2xl border-2 border-white/60 bg-black/45 p-3 shadow-[0_5px_0_rgb(0_0_0/0.25)]">
				<h4 class="flex items-baseline justify-between text-sm font-extrabold text-white [text-shadow:0_1px_2px_rgb(0_0_0/0.6)]">{t('play.news.tile.gauge')}<span class="tabular-nums">{Math.min(gauge.points, gauge.last).toLocaleString()} / {gauge.last.toLocaleString()}</span></h4>
				<NewsGauge points={gauge.points} tiers={tile.gauge.steps} {shop} {details} label={t('play.news.tile.gauge')} size={56} minWidth="20rem" />
			</section>
		{/if}
		</div>
	</div>
</section>

{#if details}<ObtainScreen bind:open={got.open} title={t('play.obtain.acquired')} notice={got.notice} items={got.items} {shop} {details} ribbon ambient="shine" palette="teal" />{/if}
