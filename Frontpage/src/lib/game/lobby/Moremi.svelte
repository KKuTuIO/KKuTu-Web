<script>
	import { t } from '../i18n.svelte.js';
	import { moremiLayers } from '../images.js';
	/** Layered avatar. `equip` is the user's equip map, `shop` the item catalogue, `parts` the layer order. */
	let { equip = {}, shop = {}, parts = [], size = 40, label = '', frame = true, class: cls = '' } = $props();
	const layers = $derived(moremiLayers(equip, shop, parts));
</script>

<span class="relative inline-block shrink-0 {frame ? 'overflow-hidden rounded-full bg-(--kk-surface-2)' : ''} {cls}" style:width="{size}px" style:height="{size}px" role="img" aria-label={label || t('play.moremi')}>
	{#each layers as l (l.part)}
		<img src={l.src} alt="" loading="lazy" draggable="false" class="absolute inset-0 size-full {l.flip ? '-scale-x-100' : ''}" />
	{/each}
</span>
