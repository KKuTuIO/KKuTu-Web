<!--
	Ported from React Bits "HoloCard" (https://github.com/DavidHDev/react-bits).
	Copyright (c) 2026 David Haz. MIT + Commons Clause License Condition v1.0, see LICENSE.md in this folder.
	Changes here: the markup and style of HoloCard.jsx / HoloCard.css as a Svelte component; the drawing is in holo.js.
-->
<script>
	import { createHoloCard } from './holo.js';

	let { image, backImage = '', alt = '', width = 320, radius = 14, preset = 'bursts', intensity = 0.85, scale = 1, edgeSparkle = 0.8, frame = 4, glare = 0.5, foilColor = '#e2e6ec', tiltMax = 14, hoverScale = 1.04, idle = true } = $props();
	let root = $state();
	let rotor = $state();
	let canvas = $state();
	let shadow = $state();
	let ready = $state(false);
	let ratio = $state(63 / 88);
	let flipped = $state(false);
	let holo;
	const flippable = $derived(!!backImage);

	$effect(() => {
		const options = { image, preset, intensity, scale, edgeSparkle, frame, glare, foilColor, radius, tiltMax, hoverScale, idle };
		holo = createHoloCard({ root, rotor, canvas, shadow }, options, { onready: (v) => (ready = v), onratio: (v) => (ratio = v) });
		return () => holo.destroy();
	});
	$effect(() => holo?.flip(flippable && flipped));

	function key(e) {
		if (!flippable || (e.key !== 'Enter' && e.key !== ' ')) return;
		e.preventDefault();
		if (!e.repeat) flipped = !flipped;
	}
</script>

<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
<div
	bind:this={root}
	class="holo-card"
	role={flippable ? 'button' : 'img'}
	tabindex={flippable ? 0 : undefined}
	aria-label={alt || undefined}
	aria-pressed={flippable ? flipped : undefined}
	data-ready={ready ? '' : undefined}
	data-flippable={flippable ? '' : undefined}
	onpointerenter={(e) => holo?.track(e)}
	onpointermove={(e) => holo?.track(e)}
	onpointerdown={(e) => holo?.track(e)}
	onpointerleave={() => holo?.leave()}
	onpointercancel={() => holo?.leave()}
	onclick={() => flippable && (flipped = !flipped)}
	onkeydown={key}
	style:--hc-w="{width}px"
	style:--hc-ratio={ratio}
	style:--hc-radius="{radius}px"
>
	<span bind:this={shadow} class="shadow" aria-hidden="true"></span>
	<div bind:this={rotor} class="rotor">
		<div class="face">
			<img src={image} alt="" draggable="false" />
			<canvas bind:this={canvas}></canvas>
		</div>
		{#if flippable}
			<div class="face back"><img src={backImage} alt="" draggable="false" /></div>
		{/if}
	</div>
</div>

<style>
	.holo-card {
		position: relative;
		display: inline-block;
		width: var(--hc-w);
		max-width: 100%;
		aspect-ratio: var(--hc-ratio);
		border-radius: var(--hc-radius);
		outline: none;
		user-select: none;
		-webkit-user-select: none;
		-webkit-touch-callout: none;
		-webkit-tap-highlight-color: transparent;
		touch-action: pan-y;
	}
	.holo-card[data-flippable] {
		cursor: pointer;
	}
	.holo-card[data-flippable]:focus-visible {
		outline: 2px solid #fff;
		outline-offset: 6px;
	}
	.shadow {
		position: absolute;
		inset: 10% 7% -4%;
		border-radius: var(--hc-radius);
		background: rgba(0, 0, 0, 0.42);
		filter: blur(26px);
		pointer-events: none;
		will-change: transform;
	}
	.rotor {
		position: absolute;
		inset: 0;
		transform-style: preserve-3d;
		will-change: transform;
	}
	.face {
		position: absolute;
		inset: 0;
		overflow: hidden;
		border-radius: var(--hc-radius);
		backface-visibility: hidden;
		-webkit-backface-visibility: hidden;
	}
	.back {
		transform: rotateY(180deg);
	}
	.face img,
	.face canvas {
		position: absolute;
		inset: 0;
		display: block;
		width: 100%;
		height: 100%;
	}
	.face img {
		object-fit: cover;
		pointer-events: none;
		-webkit-user-drag: none;
	}
	.face canvas {
		opacity: 0;
		transition: opacity 0.3s ease;
	}
	.holo-card[data-ready] .face canvas {
		opacity: 1;
	}
</style>
