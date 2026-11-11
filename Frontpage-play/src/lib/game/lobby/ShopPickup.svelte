<script>
	import { t } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import { gachaPool, drawPrice, itemName } from '../shop.js';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import ShopThumb from './ShopThumb.svelte';

	/**
	 * 픽업: one gacha. The left card fills the page's height with the gacha's picture and the two ways to draw (legacy: 1 or 10,
	 * ten at once cost 10% less each). The right card has its own height and scrolls by itself: what the gacha can give (the pool
	 * the server draws from, so what is shown is what is drawn) with the odds on request.
	 */
	let { gacha, meta, shop, details, money, ondraw } = $props();
	let odds = $state(false);
	const pool = $derived(gachaPool(gacha));
	const expl = $derived(gacha.options?.gacha?.expl);
	const title = $derived(meta?.name ?? itemName(details, gacha._id));
	const color = $derived(gacha.options?.gacha?.explColor || '#f3b819');
	const draws = [
		{ count: 1, key: 'one' },
		{ count: 10, key: 'ten' }
	];
	const fmtP = (p) => `${+p.toFixed(p < 1 ? 3 : 2)}%`;
</script>

<div class="grid gap-4 lg:h-full lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:grid-rows-[minmax(0,1fr)]">
	<section class="flex min-h-0 flex-col gap-3 rounded-2xl bg-(--kk-surface-2) p-4 shadow-[0_3px_0_var(--kk-line)]" aria-label={title}>
		<div class="flex flex-col items-center gap-1 text-center">
			{#if expl}<small class="font-extrabold" style:color>{expl}</small>{/if}
			<h3 class="text-2xl font-extrabold">{title}</h3>
			{#if meta?.subtitle}<p class="text-sm font-bold text-(color:--kk-muted)">{meta.subtitle}</p>{/if}
		</div>
		<div class="grid min-h-40 flex-1 place-items-center">
			<img src={cdnImg(`kkutu/gacha/shop/${gacha._id}.png`)} alt="" draggable="false" class="max-h-full max-w-full object-contain drop-shadow-[0_6px_0_rgb(0_0_0/0.12)] max-lg:max-h-72" />
		</div>
		<div class="grid grid-cols-2 gap-3">
			{#each draws as d (d.count)}
				{@const price = drawPrice(gacha.cost, d.count)}
				<button type="button" disabled={price > money} onclick={() => ondraw(gacha, d.count)} class="kk-btn kk-btn-primary flex h-auto min-h-0 flex-col items-center gap-1.5 py-3 leading-tight">
					<span class="text-base font-extrabold">{t(`kkutu.product.shopList.draw.${d.key}`)}</span>
					<span class="inline-flex items-center gap-1 rounded-full bg-black/20 py-0.5 pr-3 pl-1.5 text-base font-extrabold tabular-nums"><GameIcon name="ping" size={22} />{price.toLocaleString()}</span>
					{#if d.count > 1 && price < gacha.cost * d.count}<s class="text-xs opacity-75 tabular-nums">{(gacha.cost * d.count).toLocaleString()}</s>{/if}
				</button>
			{/each}
		</div>
	</section>

	<section class="flex min-h-0 flex-col gap-3 rounded-2xl bg-(--kk-surface-2) p-4 shadow-[0_3px_0_var(--kk-line)] max-lg:max-h-[70dvh]" aria-label={t('play.shop.pool')}>
		<div class="flex items-center justify-between gap-2">
			<h3 class="font-extrabold">{t('play.shop.pool')}</h3>
			<div class="flex gap-2">
				{#if meta?.probability_link}<a href={meta.probability_link} target="_blank" rel="noopener noreferrer" class="kk-btn inline-flex h-9 min-h-0 items-center gap-1 px-3 text-sm">{t('play.shop.prob.link')}<Icon name="open_in_new" size={16} /></a>{/if}
				<button type="button" aria-pressed={odds} onclick={() => (odds = !odds)} class="kk-btn h-9 min-h-0 px-3 text-sm">{t(odds ? 'play.shop.prob.hide' : 'play.shop.prob.show')}</button>
			</div>
		</div>
		<ul class="grid min-h-0 flex-1 grid-cols-[repeat(auto-fill,minmax(5.5rem,1fr))] content-start gap-2.5 overflow-y-auto pr-1 pb-[3px]">
			{#each pool as e (e.key)}
				<li class="relative flex flex-col items-center justify-center gap-1 rounded-xl bg-(--kk-surface) p-2 text-center shadow-[0_2px_0_var(--kk-line)]">
					<ShopThumb id={e.key} {shop} class="size-14" />
					<span class="flex min-h-8 items-center justify-center text-xs leading-4 font-bold break-keep [overflow-wrap:anywhere]"><span class="line-clamp-2">{itemName(details, e.key)}</span></span>
					{#if odds}<span class="absolute inset-0 grid place-items-center rounded-xl bg-black/70 text-base font-extrabold text-white tabular-nums">{fmtP(e.p)}</span>{/if}
				</li>
			{/each}
		</ul>
	</section>
</div>
