<script>
	import { t } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import { itemImage } from '../images.js';
	import { itemName, maxQty, bonusKind, bonusValue, BONUS_KINDS } from '../shop.js';
	import Rich from '../ui/Rich.svelte';
	import GameIcon from './GameIcon.svelte';
	import ShopQty from './ShopQty.svelte';

	/**
	 * 보너스권: a deco on top with the large tickets of the chosen kind (통합 / 경험치 / 핑; prerendered, curved and in perspective; recipe in the workspace's local design-recipes/shop-bonus-deco), and below it a row for each ticket
	 * with its price and how many to buy; the cart on the left adds it up. The ticket art is KKuTu-Static img/shop/bonus/<id>.svg.
	 */
	let { items, shop, details, cart, kind = $bindable('both'), onqty } = $props();
	const qtyOf = (id) => cart.find((c) => c.id === id)?.qty ?? 0;
	const list = $derived(items.filter((g) => bonusKind(g._id) === kind).sort((a, b) => bonusValue(a._id) - bonusValue(b._id)));
	// The deco takes its colour from the kind (the tickets' own: blue, green, yellow).
	const TINT = { both: ['#dbeafe', '#1e3a8a'], exp: ['#d1fae5', '#064e3b'], mny: ['#fef3c7', '#78350f'] };
	// Each kind has its own colour, the ticket's: blue, green, yellow (the label on yellow is dark).
	const IDENT = { both: { bg: '#3b82f6', edge: '#1d4ed8', fg: '#fff' }, exp: { bg: '#10b981', edge: '#047857', fg: '#fff' }, mny: { bg: '#f5b400', edge: '#b45309', fg: '#3b2a00' } };
	const art = (id) => cdnImg(`shop/bonus/${id}.svg`);
	// A ticket without art of its own falls back to the shop's small picture.
	const fallback = (e, id) => ((e.currentTarget.onerror = null), (e.currentTarget.src = itemImage(id, shop)));
</script>

<!-- 통합: the XP and the ping icon overlap like an avatar stack, the ping one on top with a ring in the button's colour -->
{#snippet kindIcons(k)}
	{#if k === 'both'}
		<span class="flex items-center"><GameIcon name="xp" size={22} /><span class="-ml-2 rounded-full ring-2 ring-(--stack-bg)"><GameIcon name="ping" size={22} /></span></span>
	{:else}<GameIcon name={k === 'exp' ? 'xp' : 'ping'} size={22} />{/if}
{/snippet}

<div class="flex flex-col gap-4">
	<div class="shop-bonus-deco relative flex h-56 items-center justify-between gap-4 overflow-hidden rounded-2xl pl-6 shadow-[0_3px_0_var(--kk-line)] md:h-64 md:pl-10" style:--bonus-bg={TINT[kind][0]} style:--bonus-bg-dark={TINT[kind][1]}>
		<div class="min-w-0">
			<h3 class="text-xl leading-tight font-extrabold md:text-3xl">{t(`play.shop.bonus.title.${kind}`)}</h3>
			<p class="mt-2 text-sm leading-relaxed font-bold text-(color:--kk-muted) md:text-base"><Rich html={t(`play.shop.bonus.copy.${kind}`)} /></p>
		</div>
		<img src={cdnImg(`shop/bonus/deco-${kind}.png`)} alt="" draggable="false" class="h-full w-auto max-w-[58%] shrink-0 object-contain object-right" />
	</div>

	<div role="group" aria-label={t('play.shop.sub.bonus')} class="flex flex-wrap gap-2">
		{#each BONUS_KINDS as k (k)}
			{@const on = kind === k}
			<button
				type="button"
				aria-pressed={on}
				onclick={() => (kind = k)}
				style:--stack-bg={on ? IDENT[k].bg : 'var(--kk-surface)'}
				style="--k:{IDENT[k].bg};--e:{IDENT[k].edge}"
				class="inline-flex h-11 items-center gap-2 rounded-xl border-[1.5px] px-4 font-extrabold {on ? 'border-transparent bg-(--k) shadow-[0_3px_0_var(--e)]' : 'border-(--k) bg-(--kk-surface) shadow-[0_3px_0_var(--k)]'}"
				style:color={on ? IDENT[k].fg : null}
			>
				{@render kindIcons(k)}{t(`play.shop.bonus.${k}`)}
			</button>
		{/each}
	</div>

	<ul class="flex flex-col gap-2.5">
		{#each list as item (item._id)}
			{@const name = itemName(details, item._id)}
			<li class="flex items-center gap-3 rounded-2xl border-[1.5px] bg-(--kk-surface) p-2.5 shadow-[0_3px_0_var(--kk-line)] {qtyOf(item._id) ? 'border-(--shop-accent,var(--kk-accent))' : 'border-(color:--kk-line)'}">
				<img src={art(item._id)} alt="" draggable="false" onerror={(e) => fallback(e, item._id)} class="size-20 shrink-0 object-contain" />
				<span class="min-w-0 flex-1">
					<b class="block truncate text-base">{name}</b>
					{#if details?.[item._id]?.detail}<span class="block truncate text-xs text-(color:--kk-muted)"><Rich html={details[item._id].detail} /></span>{/if}
					<span class="mt-0.5 flex items-center gap-1 font-extrabold text-(color:--kk-warn) tabular-nums"><GameIcon name="ping" size={16} />{item.cost.toLocaleString()}</span>
				</span>
				<ShopQty value={qtyOf(item._id)} max={maxQty(item)} {name} onchange={(q) => onqty(item, q)} />
			</li>
		{/each}
	</ul>
</div>

<style>
	.shop-bonus-deco {
		background: radial-gradient(ellipse at 50% 110%, var(--bonus-bg) 0%, color-mix(in srgb, var(--bonus-bg) 55%, var(--kk-surface-2)) 70%);
	}
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .shop-bonus-deco {
			background: radial-gradient(ellipse at 50% 110%, color-mix(in srgb, var(--bonus-bg-dark) 80%, black) 0%, var(--kk-surface-2) 75%);
		}
	}
	:global(:root[data-theme='dark']) .shop-bonus-deco {
		background: radial-gradient(ellipse at 50% 110%, color-mix(in srgb, var(--bonus-bg-dark) 80%, black) 0%, var(--kk-surface-2) 75%);
	}
</style>
