<script>
	import { isNew, itemName } from '../shop.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import GameIcon from './GameIcon.svelte';
	import ShopThumb from './ShopThumb.svelte';
	import ItemTip from './ItemTip.svelte';
	import Icon from './Icon.svelte';
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';

	/**
	 * One item of the shelf: picture, name, price. Clicking it puts the item in the cart (and on the avatar), clicking again takes it out.
	 * New items carry the "N" mark of the legacy shop, except on the 메인 page (`plain`), which stays free of chips.
	 */
	let { item, shop, details, picked = false, plain = false, compact = false, owned = false, onpick } = $props();
	const name = $derived(itemName(details, item._id));
</script>

<Tooltip placement="top" delay={250}>
	{#snippet content()}<ItemTip {item} {details} />{/snippet}
	<button
		type="button"
		aria-pressed={picked}
		onclick={() => onpick?.(item)}
		class="relative flex h-full w-full [contain-intrinsic-size:auto_190px] [content-visibility:auto] flex-col {compact ? 'gap-1 p-1.5' : 'gap-2 p-2'} rounded-xl border-[1.5px] bg-(--kk-surface) text-left shadow-[0_3px_0_var(--kk-line)] active:translate-y-px active:shadow-[0_1px_0_var(--kk-line)] {picked ? 'border-(--shop-accent,var(--kk-accent)) shadow-[0_3px_0_var(--shop-accent,var(--kk-accent))]' : 'border-(color:--kk-line)'}"
	>
		<span class="relative flex aspect-square items-center justify-center rounded-lg bg-(--kk-surface-2)">
			<ShopThumb id={item._id} {shop} class="size-4/5" />
			{#if !plain && isNew(item)}<span class="absolute top-1.5 left-1.5 rounded-md bg-(--kk-blue) px-1.5 text-[10px] leading-4 font-extrabold text-white">N</span>{/if}
			{#if owned && !picked}<span class="absolute top-1.5 right-1.5 grid size-5 place-items-center rounded-full bg-(--kk-surface) text-(color:--kk-muted) shadow-[0_1px_0_var(--kk-line)]" title="{plainText(t('kkutu.js.alreadyGot'))}"><Icon name="inventory_2" size={13} /></span>{/if}
			{#if picked}<span class="absolute top-1.5 right-1.5 grid size-5 place-items-center rounded-full bg-(--shop-accent,var(--kk-accent)) text-white"><Icon name="check" size={14} /></span>{/if}
		</span>
		<span class="{compact ? 'line-clamp-2 min-h-8 text-xs' : 'min-h-9 text-sm'} flex-1 leading-tight font-extrabold break-keep [overflow-wrap:anywhere]">{name}</span>
		<span class="flex items-center gap-1 {compact ? 'text-xs' : ''} font-extrabold text-(color:--kk-warn) tabular-nums"><GameIcon name="ping" size={compact ? 13 : 16} />{item.cost.toLocaleString()}</span>
	</button>

</Tooltip>
