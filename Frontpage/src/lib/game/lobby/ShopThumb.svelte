<script>
	import { itemImage } from '../images.js';
	import { dynItem } from '../shop.js';
	/** An item's picture: its art, or for a dynamic item (a letter piece) the circle the legacy client draws; nothing when it has neither. */
	let { id, shop, class: cls = '' } = $props();
	const dyn = $derived(dynItem(id));
</script>

{#if shop[id]}
	<img src={itemImage(id, shop)} alt="" loading="lazy" decoding="async" draggable="false" class="object-contain {cls}" />
{:else if dyn}
	<span class="grid aspect-square place-items-center rounded-full text-lg font-extrabold {cls}" style:background={dyn.bg} style:color={dyn.fg} aria-hidden="true">{dyn.text}</span>
{:else}
	<span class="aspect-square rounded-lg bg-(--kk-surface-2) {cls}" aria-hidden="true"></span>
{/if}
