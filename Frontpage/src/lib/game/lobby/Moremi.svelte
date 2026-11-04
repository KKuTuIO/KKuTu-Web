<script>
	import { t } from '../i18n.svelte.js';
	import { moremiLayers, IMAGE_CDN, IMAGE_VERSION } from '../images.js';
	import { prefs } from '../prefs.svelte.js';
	import MoremiVector from './MoremiVector.svelte';
	/**
	 * Layered avatar. `equip` is the user's equip map, `shop` the item catalogue, `parts` the layer order.
	 * The default body, eyes and mouth are vectors; when the body is the default one the avatar is "alive": it blinks
	 * (default eyes only) and breathes (everything except the shoes sinks and squashes a little over the legs).
	 * Any other body (robot, foot remover, carrot...) or a tiny size leaves the avatar still.
	 */
	let { equip = {}, shop = {}, parts = [], size = 40, label = '', frame = true, maskable = false, animated = true, class: cls = '' } = $props();
	const uid = $props.id();
	const masked = $derived(maskable && prefs.hideUsers);
	const layers = $derived(moremiLayers(equip, shop, parts));
	let broken = $state({}); // layer pictures that failed to load: hidden instead of drawing the browser's broken-image icon
	const vectorBody = $derived(layers.some((l) => l.vector === 'body'));
	const vectorEye = $derived(layers.some((l) => l.vector === 'eye'));
	const alive = $derived(animated && !masked && size >= 48 && (vectorBody || vectorEye));
	// Spread avatars out of phase so a whole room doesn't blink in unison.
	const phase = $derived.by(() => {
		let h = 0;
		for (const c of uid + label) h = (h * 31 + c.charCodeAt(0)) >>> 0;
		return h % 1000;
	});
	const style = $derived(`--alive-delay:-${((phase % 380) / 100).toFixed(2)}s;--breath-dur:3.8s;--blink-dur:${(4.2 + (phase % 260) / 100).toFixed(2)}s`);
</script>

<span class="relative inline-block shrink-0 {frame ? 'overflow-hidden rounded-full bg-(--kk-surface-2)' : ''} {alive ? 'alive' : ''} {cls}" style:width="{size}px" style:height="{size}px" {style} role="img" aria-label={label || t('play.moremi')}>
	{#if masked}
		<img src="{IMAGE_CDN}kkutu/anonymous.png{IMAGE_VERSION}" alt="" draggable="false" class="absolute inset-0 size-full" />
	{:else}
		{#each layers as l (l.part)}
			{@const sinks = vectorBody && l.part !== 'shoes' && l.part !== 'body'}
			{#if l.vector}
				<MoremiVector part={l.vector} class={sinks ? 'breathe' : ''} />
			{:else}
				<!-- The right hand is mirrored on a wrapper of its own: the mirror (Tailwind's -scale-x-100) and the breathing both use the CSS `scale` property, so on the picture itself the animation would cancel the mirror. -->
				{#if l.flip}
					<span class="absolute inset-0 -scale-x-100"><img src={l.src} alt="" loading="lazy" draggable="false" onerror={() => (broken[l.src] = true)} class="absolute inset-0 size-full {sinks ? 'breathe' : ''} {broken[l.src] ? 'invisible' : ''}" /></span>
				{:else}
					<img src={l.src} alt="" loading="lazy" draggable="false" onerror={() => (broken[l.src] = true)} class="absolute inset-0 size-full {sinks ? 'breathe' : ''} {broken[l.src] ? 'invisible' : ''}" />
				{/if}
			{/if}
		{/each}
	{/if}
</span>

<style>
	.alive :global(.breathe) {
		transform-origin: 50% 91.67%;
		animation: breathe var(--breath-dur) ease-in-out var(--alive-delay) infinite;
	}
	/* The mirrored right hand has its own wrapper (see above), so animating `scale` here is safe. */
	@keyframes breathe {
		0%,
		100% {
			translate: 0 0;
			scale: 1 1;
		}
		50% {
			translate: 0 1.1%;
			scale: 1 0.972;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.alive :global(.breathe) {
			animation: none;
		}
	}
</style>
