<script>
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import RewardChip from './RewardChip.svelte';
	import ShopThumb from './ShopThumb.svelte';
	import { t } from '../i18n.svelte.js';
	import NewsExchangeDialog from './NewsExchangeDialog.svelte';
	import { rewardsOfMap } from '../news.js';
	import { rewardLabel } from '../newsFormat.js';

	/**
	 * The exchange shop as tiles: what you get (a big picture and its name), how it is limited ("일일 교환" / "기간 교환"), and the price as a pill that
	 * is gold while I can pay it and dark while I cannot. A purchase asks first (what I give, what I get, what is left), since it spends something.
	 * @type {{store:any, event:any, shop?:any, details?:any, open:boolean}}
	 */
	let { store, event, shop = {}, details = null, open } = $props();
	const net = $derived(store.net);
	const items = $derived((net.exchanges[event.id] ?? []).filter((x) => x.enabled !== false));
	const money = $derived(store.me?.money ?? 0);
	let pending = $state('');
	$effect(() => {
		net.exchanges; // an answer ends the wait
		net.eventProgress;
		pending = '';
	});
	const have = (cost) => (cost.kind === 'ping' ? money : (net.box?.[cost.id]?.value ?? 0));
	const affordable = (ex) => rewardsOfMap(ex.cost).every((c) => have(c) >= c.q);
	const daily = (ex) => (ex.buyLimitInterval ?? 86400) <= 86400;
	const title = (ex) => ex.name || rewardLabel(rewardsOfMap(ex.gives)[0] ?? { kind: 'item', id: '' }, details);

	let asking = $state(null); // the exchange the dialog is about
	let askOpen = $state(false);
	function ask(ex) {
		asking = ex;
		askOpen = true;
	}
	function confirmed() {
		const ex = asking;
		if (!ex) return;
		pending = ex.id;
		store.exchangeEvent(event.id, ex.id);
		setTimeout(() => pending === ex.id && (pending = ''), 4000);
	}
</script>

<section class="flex flex-col gap-3">
	<div class="flex items-center gap-3">
		<h3 class="text-base font-extrabold">{t('play.news.exchange')}</h3>
		<span class="ml-auto inline-flex h-9 items-center gap-1.5 rounded-xl bg-(--kk-surface-2) pr-3 pl-1.5 font-extrabold tabular-nums">
			<GameIcon name="ping" size={24} />{money.toLocaleString()}<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.currency.ping')}</span>
		</span>
	</div>
	{#if items.length}
		<ul class="grid grid-cols-[repeat(auto-fill,minmax(11rem,1fr))] gap-3">
			{#each items as ex (ex.id)}
				{@const gives = rewardsOfMap(ex.gives)}
				{@const cost = rewardsOfMap(ex.cost)}
				{@const can = affordable(ex)}
				<li class="flex">
					<button type="button" disabled={!open || !can || pending === ex.id} onclick={() => ask(ex)} class="group flex w-full flex-col items-start gap-1 rounded-xl border-[1.5px] border-(color:--kk-line) bg-(--kk-surface-2) p-3 text-left shadow-(--kk-shadow) enabled:hover:border-(--ac,var(--kk-accent)) enabled:active:translate-y-px enabled:active:shadow-none disabled:opacity-70">
						<span class="text-[11px] font-bold text-(color:--kk-muted)">{daily(ex) ? t('play.news.exchangeDaily') : t('play.news.exchangePeriod')}</span>
						<span class="grid w-full place-items-center py-2">
							{#if gives[0]?.kind === 'item'}<ShopThumb id={gives[0].id ?? ''} {shop} class="size-20" />{:else if gives[0]}<RewardChip reward={gives[0]} {shop} {details} size={56} />{/if}
						</span>
						<b class="line-clamp-2 min-h-8 text-sm leading-snug font-extrabold">{title(ex)}{#if gives[0] && gives[0].q > 1}<span class="ml-1 tabular-nums">×{gives[0].q.toLocaleString()}</span>{/if}</b>
						{#if ex.expl}<span class="line-clamp-2 text-xs text-(color:--kk-muted)">{ex.expl}</span>{/if}
						<span class="flex flex-wrap items-center gap-1.5 pt-1">
							{#each cost as c, i (i)}
								<span class="inline-flex h-7 items-center gap-1 rounded-full px-2.5 text-xs font-extrabold tabular-nums {can ? 'bg-(--kk-warn-soft) text-(color:--kk-warn)' : 'bg-neutral-700 text-white/75'}">
									{#if c.kind === 'ping'}<GameIcon name="ping" size={16} />{:else}<ShopThumb id={c.id ?? ''} {shop} class="size-4" />{/if}{c.q.toLocaleString()}
								</span>
							{:else}
								<span class="inline-flex h-7 items-center rounded-full bg-(--kk-accent-soft) px-2.5 text-xs font-extrabold text-(color:--kk-accent-strong)">{t('play.news.exchangeFree')}</span>
							{/each}
							{#if ex.buyLimit > 0}<span class="text-[11px] font-bold text-(color:--kk-muted)">{t('play.news.exchangeLimit', { n: ex.buyLimit, days: Math.max(1, Math.round((ex.buyLimitInterval || 86400) / 86400)) })}</span>{/if}
						</span>
						{#if ex.stockLimit > 0}<span class="inline-flex items-center gap-1 text-[11px] font-bold text-(color:--kk-muted)"><Icon name="inventory_2" size={14} />{t('play.news.exchangeStock', { n: ex.stockLimit })}</span>{/if}
					</button>
				</li>
			{/each}
		</ul>
	{:else}
		<p class="text-sm text-(color:--kk-muted)">{t('play.news.exchangeEmpty')}</p>
	{/if}
</section>

<NewsExchangeDialog bind:open={askOpen} exchange={asking} title={asking ? title(asking) : ''} {shop} {details} {have} onconfirm={confirmed} />
