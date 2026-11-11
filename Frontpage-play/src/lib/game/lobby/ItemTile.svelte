<script>
	import { t } from '../i18n.svelte.js';
	import { itemName } from '../shop.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Icon from './Icon.svelte';
	import ItemTip from './ItemTip.svelte';
	import ShopThumb from './ShopThumb.svelte';

	/**
	 * An item of my box as a small icon: only the picture; the name and everything else are in the tooltip. A worn item has an accent
	 * border and a check, how many I have (when more than one) is on its corner, an enchanted copy shows its +level on the top left (and is never merged into the count), and a delete button shows on the tile of an item that is
	 * not worn when the pointer or the keyboard is on it (a right click deletes too).
	 */
	let { item, shop, details, own, onpick, onremove } = $props();
	const name = $derived(itemName(details, item._id));
</script>

<Tooltip placement="top" delay={150}>
	{#snippet content()}<ItemTip {item} {details} {own} />{/snippet}
	<span class="group relative block aspect-square">
		<button
			type="button"
			aria-label={name}
			aria-pressed={own.equipped}
			onclick={() => onpick?.(item)}
			oncontextmenu={(e) => (!own.equipped && onremove ? (e.preventDefault(), onremove(item)) : null)}
			class="grid size-full place-items-center rounded-xl border-[1.5px] bg-(--kk-surface-2) shadow-[0_2px_0_var(--kk-line)] active:translate-y-px active:shadow-none {own.equipped ? 'border-(--kk-accent) shadow-[0_2px_0_var(--kk-accent)]' : 'border-(color:--kk-line)'}"
		>
			<ShopThumb id={item._id} {shop} class="size-4/5" />
		</button>
		{#if own.equipped}<span class="pointer-events-none absolute top-0.5 right-0.5 grid size-4 place-items-center rounded-full bg-(--kk-accent) text-white"><Icon name="check" size={11} weight={700} /></span>{/if}
		{#if own.level > 0}<b class="pointer-events-none absolute top-0.5 left-0.5 rounded-md bg-amber-400 px-1 text-[10px] leading-4 font-extrabold text-slate-900 tabular-nums">+{own.level}</b>{/if}
		{#if own.count > 1}<b class="pointer-events-none absolute right-0.5 bottom-0.5 rounded-md bg-(--kk-surface)/90 px-1 text-[10px] leading-4 tabular-nums">×{own.count}</b>{/if}
		{#if onremove && !own.equipped}
			<button type="button" aria-label={t('kkutu.product.shopCart.remove')} onclick={() => onremove(item)} class="absolute top-0.5 left-0.5 grid size-5 place-items-center rounded-full bg-(--kk-surface) text-(color:--kk-muted) opacity-0 shadow-[0_1px_0_var(--kk-line)] group-focus-within:opacity-100 group-hover:opacity-100 hover:text-(color:--kk-danger)"><Icon name="delete" size={13} /></button>
		{/if}
	</span>
</Tooltip>
