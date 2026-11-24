<script>
	import { reducedMotion } from '../motion.js';
	/**
	 * An event's title as text art: the stage lettering (ONE Mobile POP) filled with a gradient, every letter bobbing a little behind the one before
	 * it, so a wave runs through the name. Each letter is two layers: a dark outline (a thick stroke, behind) and the gradient fill (on top, no stroke),
	 * so the outline stays whole however the fill is clipped. Screen readers get the plain name; with motion reduced the letters stand still.
	 * `accent` colours the outline (`--c`); `fill` (a CSS gradient) replaces the white-to-yellow fill;
	 * `outline` (a colour) and `outlineWidth` (e.g. `0.3em`) replace the outline's dark colour and its thickness.
	 * @type {{text:string, accent?:string, fill?:string, outline?:string, outlineWidth?:string, class?:string}}
	 */
	let { text, accent = '', fill = '', outline = '', outlineWidth = '', class: cls = '' } = $props();
	const letters = $derived([...text]);
	const uid = $props.id();
	const still = $derived(reducedMotion()); // the system's or the player's 움직임 감소: the letters stand still
</script>

<!-- Closes the holes of the outline layer (the inside of ㅇ, ㅂ, ㅁ, where the stroke leaves a gap): the shape is grown, then shrunk by the same amount, so
     whatever is open and narrower than twice that is filled and the outer edge stays. In fractions of each letter's box, so it follows the font size. -->
<h2 class="kk-fire-text leading-tight {cls}" class:still style:--fh="url(#nwt-{uid})" style:--c={accent || 'var(--ac, #7c3aed)'} style:--fill={fill || undefined} style:--o={outline || undefined} style:--ow={outlineWidth || undefined}>
	<svg width="0" height="0" class="absolute" aria-hidden="true" focusable="false" style="color: var(--o, color-mix(in srgb, var(--c) 55%, #1e0538))">
		<filter id="nwt-{uid}" x="-30%" y="-30%" width="160%" height="160%" primitiveUnits="objectBoundingBox" color-interpolation-filters="sRGB">
			<feMorphology in="SourceAlpha" operator="dilate" radius="0.16 0.14" result="grown" />
			<feMorphology in="grown" operator="erode" radius="0.16 0.14" result="closed" />
			<feFlood flood-color="currentColor" />
			<feComposite in2="closed" operator="in" />
		</filter>
	</svg>
	<span class="sr-only">{text}</span>
	<!-- two layers of the same letters, bobbing in step: every outline lies under every fill (with an outline and a fill in each letter, a letter's outline was drawn over the fill of the one before it) -->
	<span aria-hidden="true" class="letters">
		<span class="under">{@render row('o')}</span>
		<span class="over">{@render row('f')}</span>
	</span>
</h2>

{#snippet row(kind)}{#each letters as ch, i (i)}<span class="wave" style:animation-delay="{i * 0.09}s"><span class={kind}>{ch === ' ' ? '\u00a0' : ch}</span></span>{/each}{/snippet}

<style>
	.letters {
		display: block;
		position: relative;
	}
	/* the shadow is cast by the outlines as one shape: one per letter would be drawn over the neighbouring letter's outline */
	.under {
		display: block;
		filter: drop-shadow(0 3px 0 color-mix(in srgb, var(--c) 40%, #1e0538));
	}
	.over {
		position: absolute;
		inset: 0;
		display: block;
	}
	.wave {
		display: inline-block;
		position: relative;
		isolation: isolate;
		animation: kk-wave 1.7s ease-in-out infinite;
	}
	/* the outline: the lower layer, as thick as the lettering wants */
	.o {
		color: var(--o, color-mix(in srgb, var(--c) 55%, #1e0538));
		-webkit-text-stroke: var(--ow, 0.18em) var(--o, color-mix(in srgb, var(--c) 55%, #1e0538));
		text-shadow: none;
		filter: var(--fh);
	}
	/* the fill: a gradient clipped to the letter, no stroke of its own */
	.f {
		color: transparent;
		background-image: var(--fill, linear-gradient(180deg, #fff 15%, #fde047 100%));
		-webkit-background-clip: text;
		background-clip: text;
		-webkit-text-stroke: 0;
		text-shadow: none;
	}
	@keyframes kk-wave {
		0%,
		100% {
			transform: translateY(0);
		}
		50% {
			transform: translateY(-0.14em);
		}
	}
	.still .wave {
		animation: none;
	}
</style>
