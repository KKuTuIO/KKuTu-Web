<script>
	import { t } from '../i18n.svelte.js';
	import { itemName } from '../shop.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Icon from './Icon.svelte';
	import ItemTip from './ItemTip.svelte';
	import ShopThumb from './ShopThumb.svelte';

	/**
	 * The worn 보너스권 as a slot of its own (the way an equipment screen shows its slots: a title over a tinted tile with the item in it):
	 * the ticket I wear (its name and what it gives are in the tooltip), or an empty slot that leads to my tickets. Pressing it shows my tickets in the list.
	 * @type {{item:any|null, shop:Record<string,any>, details:Record<string,any>|null, onopen?:()=>void}}
	 */
	let { item, shop, details, onopen } = $props();
</script>

<Tooltip placement="bottom" delay={150}>
	{#snippet content()}{#if item}<ItemTip {item} {details} own={{ equipped: true }} />{:else}<span class="block max-w-52"><b>{t('play.bonus.none')}</b><br />{t('play.bonus.hint')}</span>{/if}{/snippet}
	<button type="button" onclick={() => onopen?.()} class="group flex w-fit flex-col items-start gap-1" aria-label="{t('play.bonus.slot')}: {item ? itemName(details, item._id) : t('play.bonus.none')}">
		<b class="px-1 text-xs font-extrabold tracking-tight text-(color:--kk-accent-strong)">{t('play.bonus.slot')}</b>
		<span class="grid size-16 place-items-center rounded-xl border-[1.5px] bg-gradient-to-br shadow-[0_2px_0_var(--kk-line)] transition-transform group-hover:-translate-y-0.5 group-active:translate-y-0 {item ? 'border-(color:--kk-accent) from-(--kk-accent-soft) to-(--kk-surface-2)' : 'border-dashed border-(color:--kk-line-strong) from-transparent to-transparent'}">
			{#if item}<ShopThumb id={item._id} {shop} class="size-12" />{:else}<Icon name="add" size={26} class="text-(color:--kk-muted)" />{/if}
		</span>
	</button>
</Tooltip>
