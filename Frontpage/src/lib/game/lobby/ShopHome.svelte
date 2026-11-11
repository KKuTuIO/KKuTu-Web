<script>
	import { t } from '../i18n.svelte.js';
	import { sectionItems } from '../shop.js';
	import ShopHero from './ShopHero.svelte';
	import ShopCard from './ShopCard.svelte';
	import Icon from './Icon.svelte';

	/**
	 * The 메인 page: the banner carousel, the tiles under it (one per banner: a tile moves the carousel to its banner, a banner
	 * goes to its link) and the sections, all from the home page's data (KKuTu-Static img/shop/home/home.json). A season,
	 * an event or a new product line is a change of that file, not of the client.
	 */
	let { home, base, items, shop, details, gachas = {}, picked, onpick, onaction } = $props();
	let index = $state(0);
	const sections = $derived(home.sections.map((s) => ({ ...s, list: sectionItems(s, items) })).filter((s) => s.list.length));
	// A banner that leads to a gacha takes its name and subtitle from shop.json (where the gachas are described); the rest from the data.
	const named = $derived(
		home.hero.map((h) => {
			const g = h.target?.tab === 'pickup' ? gachas[h.target.sub] : null;
			const tile = { ...h.tile, ...(g ? { title: g.name, subtitle: g.subtitle } : {}) };
			return { ...h, tile, alt: h.alt ?? tile.title };
		})
	);
	const tiles = $derived(named.map((h, i) => ({ i, id: h.id, ...h.tile })).filter((t) => t.title));
</script>

<div class="flex flex-col gap-6">
	<div class="flex flex-col gap-3">
		<ShopHero slides={named} {base} bind:index {onaction} />
		{#if tiles.length}
			<div class="grid gap-3" style:grid-template-columns="repeat({tiles.length}, minmax(0, 1fr))">
				{#each tiles as tile (tile.id)}
					{@const on = tile.i === index}
					<button
						type="button"
						aria-current={on}
						onclick={() => (index = tile.i)}
						class="relative aspect-[16/7] overflow-hidden rounded-xl text-left {on ? 'shadow-[0_3px_0_var(--shop-accent,var(--kk-accent))] ring-2 ring-(--shop-accent,var(--kk-accent))' : 'opacity-70 shadow-[0_3px_0_var(--kk-line)]'}"
					>
						{#if tile.image}<img src={base + tile.image} alt="" draggable="false" class="absolute inset-0 size-full object-cover" />{/if}
						<span class="absolute inset-0 bg-gradient-to-r from-black/55 via-black/15 to-transparent"></span>
						<span class="absolute bottom-2 left-3 flex max-w-[65%] flex-col text-white [text-shadow:0_1px_2px_rgb(0_0_0/0.6)]">
							<b class="text-xs leading-tight md:text-base">{tile.title}</b>
							{#if tile.subtitle}<small class="hidden text-xs opacity-90 md:block">{tile.subtitle}</small>{/if}
						</span>
					</button>
				{/each}
			</div>
		{/if}
	</div>

	{#each sections as s (s.id)}
		<section aria-label={s.title} class="flex flex-col gap-3">
			<div class="flex items-center gap-1">
				<h3 class="flex min-w-0 flex-1 items-center text-lg leading-tight font-extrabold">
					<Icon name="chevron_right" size={30} weight={700} class="-ml-1.5 shrink-0 text-(--shop-accent,var(--kk-accent))" />
					{s.title}
				</h3>
				{#if s.tab}<button type="button" onclick={() => onaction?.({ tab: s.tab })} class="inline-flex items-center text-sm font-bold text-(color:--kk-muted)">{t('play.shop.seeAll')}<Icon name="chevron_right" size={18} /></button>{/if}
			</div>
			<div class="-mx-1 grid auto-cols-[9.5rem] grid-flow-col gap-3 overflow-x-auto px-1 pt-1 pb-2">
				{#each s.list as item (item._id)}
					<ShopCard {item} {shop} {details} plain picked={picked.has(item._id)} {onpick} />
				{/each}
			</div>
		</section>
	{/each}
</div>
