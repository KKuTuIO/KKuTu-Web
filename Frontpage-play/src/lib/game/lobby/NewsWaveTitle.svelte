<script>
	/**
	 * An event's title as text art: the stage lettering (ONE Mobile POP) filled with a gradient, every letter bobbing a little behind the one before
	 * it, so a wave runs through the name. Each letter is two layers: a dark outline (a thick stroke, behind) and the gradient fill (on top, no stroke),
	 * so the outline stays whole however the fill is clipped. Screen readers get the plain name; with motion reduced the letters stand still.
	 * `accent` colours the outline (`--c`).
	 * @type {{text:string, accent?:string, class?:string}}
	 */
	let { text, accent = '', class: cls = '' } = $props();
	const letters = $derived([...text]);
</script>

<h2 class="kk-fire-text leading-tight {cls}" style:--c={accent || 'var(--ac, #7c3aed)'}>
	<span class="sr-only">{text}</span>
	<span aria-hidden="true">{#each letters as ch, i (i)}<span class="wave" style:animation-delay="{i * 0.09}s"><span class="o">{ch === ' ' ? '\u00a0' : ch}</span><span class="f">{ch === ' ' ? '\u00a0' : ch}</span></span>{/each}</span>
</h2>

<style>
	.wave {
		display: inline-block;
		position: relative;
		isolation: isolate;
		filter: drop-shadow(0 3px 0 color-mix(in srgb, var(--c) 40%, #1e0538));
		animation: kk-wave 1.7s ease-in-out infinite;
	}
	/* the outline: behind the fill, as thick as the lettering wants */
	.o {
		position: absolute;
		inset: 0;
		z-index: 0;
		color: color-mix(in srgb, var(--c) 55%, #1e0538);
		-webkit-text-stroke: 0.18em color-mix(in srgb, var(--c) 55%, #1e0538);
		text-shadow: none;
	}
	/* the fill: a gradient clipped to the letter, no stroke of its own */
	.f {
		position: relative;
		z-index: 1;
		color: transparent;
		background-image: linear-gradient(180deg, #fff 15%, #fde047 100%);
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
	@media (prefers-reduced-motion: reduce) {
		.wave {
			animation: none;
		}
	}
</style>
