<script>
	import { t } from '../i18n.svelte.js';
	import { itemImage } from '../images.js';
	import { dynItem } from '../shop.js';
	import { themeOf } from '../theme.js';
	import { titleOf } from '../title.js';
	import TitleBadge from './TitleBadge.svelte';
	import ThemeIcon from './ThemeIcon.svelte';
	/** An item's picture: its art, a theme's palette drawn as a small window, or for a dynamic item (a letter piece) the circle the legacy client draws; nothing when it has none. */
	let { id, shop, class: cls = '' } = $props();
	const dyn = $derived(dynItem(id));
	const theme = $derived(themeOf(shop[id]));
	const title = $derived(titleOf(shop[id]));
	// a title is as wide as its words: shrunk to the width of the tile it sits in
	let boxW = $state(0);
	let badgeW = $state(0);
	const narrow = $derived(boxW > 0 && boxW < 72);
	const fit = $derived(badgeW && boxW ? Math.min(1, boxW / badgeW) : 1);
</script>

{#if theme}
	<ThemeIcon item={shop[id]} class={cls} />
{:else if title}
	<span bind:clientWidth={boxW} class="grid min-w-0 place-items-center overflow-hidden {cls}"><span bind:clientWidth={badgeW} class="block w-max origin-center" style:transform="scale({fit})"><TitleBadge user={{ equip: { TTL: id } }} {shop} label={narrow ? t('item.group.TTL') : ''} /></span></span>
{:else if shop[id]}
	<img src={itemImage(id, shop)} alt="" loading="lazy" decoding="async" draggable="false" class="object-contain {cls}" />
{:else if dyn}
	<span class="grid aspect-square place-items-center rounded-full text-lg font-extrabold {cls}" style:background={dyn.bg} style:color={dyn.fg} aria-hidden="true">{dyn.text}</span>
{:else}
	<span class="aspect-square rounded-lg bg-(--kk-surface-2) {cls}" aria-hidden="true"></span>
{/if}
