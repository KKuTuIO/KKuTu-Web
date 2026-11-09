<script>
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';
	import { itemName } from '../shop.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import ShopPreview from './ShopPreview.svelte';
	import ShopThumb from './ShopThumb.svelte';

	/** The left panel: the wide preview (avatar, banner, name skin, chat), the cart, the price and the buy button. */
	let { equip, parts, shop, user, details, cart, owned, money, total, buying, bare = false, list = false, onremove, onreset, onbare, onbuy } = $props();
	const after = $derived(money - total);
	const fmt = (n) => (n ?? 0).toLocaleString();
</script>

<aside class="flex shrink-0 flex-col gap-3 {list ? '' : 'overflow-y-auto border-b border-(color:--kk-line) p-3 max-lg:max-h-[60dvh] max-lg:overflow-y-auto lg:w-[27rem] lg:border-r lg:border-b-0'}">
	{#if !list}<ShopPreview {equip} {parts} {shop} {user} canReset={cart.length > 0 || bare} canBare={!bare} {onreset} {onbare} />{/if}

	<div class="flex min-h-0 flex-1 flex-col gap-2">
		<h3 class="flex items-center gap-1.5 font-extrabold"><Icon name="shopping_cart" size={20} />{plainText(t('kkutu.product.shopCart.title'))}{#if cart.length}<span class="rounded-full bg-(--shop-accent,var(--kk-accent)) px-2 text-xs leading-5 text-white tabular-nums">{cart.length}</span>{/if}</h3>
		{#if !cart.length}
			<p class="rounded-xl bg-(--kk-surface-2) p-4 text-center text-sm text-(color:--kk-muted)">{t('kkutu.product.shopCart.empty')}</p>
		{:else}
			<ul class="flex min-h-0 flex-col gap-1.5 overflow-y-auto pr-0.5 lg:max-h-48">
				{#each cart as c (c.id)}
					<li class="flex items-center gap-2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) p-1.5">
						<span class="grid size-10 shrink-0 place-items-center rounded-lg bg-(--kk-surface-2)"><ShopThumb id={c.id} {shop} class="size-9" /></span>
						<span class="min-w-0 flex-1">
							<span class="block truncate text-sm font-bold">{itemName(details, c.id)}{#if c.qty > 1}<span class="ml-1 text-(color:--kk-muted)">×{c.qty}</span>{/if}</span>
							<span class="flex items-center gap-1 text-xs font-bold text-(color:--kk-warn) tabular-nums"><GameIcon name="ping" size={12} />{fmt(Number(shop[c.id]?.cost) * c.qty)}</span>
						</span>
						{#if owned(c.id)}<Tooltip text={plainText(t('kkutu.js.alreadyGot'))}><span class="grid size-6 place-items-center text-(color:--kk-danger)"><Icon name="inventory_2" size={18} /></span></Tooltip>{/if}
						<Tooltip text={t('kkutu.product.shopCart.remove')}><button type="button" aria-label={t('kkutu.product.shopCart.remove')} onclick={() => onremove(c.id)} class="grid size-7 place-items-center rounded-full text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)"><Icon name="close" size={18} /></button></Tooltip>
					</li>
				{/each}
			</ul>
		{/if}
	</div>

	<div class="flex flex-col gap-1.5 rounded-xl bg-(--kk-surface-2) p-3 text-sm">
		<div class="flex justify-between"><span>{t('kkutu.dialog.purchase.pingBefore')}</span><b class="tabular-nums">{fmt(money)}{t('kkutu.js.ping')}</b></div>
		<div class="flex justify-between"><span>{t('kkutu.dialog.purchase.pingCost')}</span><b class="tabular-nums">{fmt(total)}{t('kkutu.js.ping')}</b></div>
		<div class="flex justify-between border-t border-(color:--kk-line) pt-1.5"><span>{t('kkutu.dialog.purchase.pingAfter')}</span><b class="tabular-nums {after < 0 ? 'text-(color:--kk-danger)' : ''}">{fmt(after)}{t('kkutu.js.ping')}</b></div>
		{#if after < 0}<p class="text-xs font-bold text-(color:--kk-danger)">{t('kkutu.js.notEnoughMoney')}</p>{/if}
	</div>
	<button type="button" class="kk-btn kk-btn-primary h-12" disabled={!cart.length || after < 0 || buying} onclick={onbuy}>{t('kkutu.product.shopCart.buy')}</button>
</aside>
