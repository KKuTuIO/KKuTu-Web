<script>
	/**
	 * The twinkling stars and soft dots of the obtain screen's ribbon (`ObtainScreen` `ambient="shine"`), shared with the result screen.
	 * Place it in a `relative` box; x and y are percentages of that box (a negative one lies outside it). The colours come from the
	 * CSS variables `--rb-ray` and `--rb-glow` of the parent, so a screen tints them by setting those.
	 * @type {{list:{star:number,x:number,y:number,size:number,tint:string,t:number,d:number}[]}}
	 */
	let { list } = $props();
</script>

<div class="pointer-events-none absolute inset-0" aria-hidden="true">
	{#each list as q, i (i)}
		{#if q.star}
			<svg class="sparkle" viewBox="0 0 100 100" width={q.size} height={q.size} style="left:{q.x}%; top:{q.y}%; color:{q.tint}; animation-duration:{q.t}s; animation-delay:-{q.d}s"><path d="M50 0 C54 34 66 46 100 50 C66 54 54 66 50 100 C46 66 34 54 0 50 C34 46 46 34 50 0 Z" fill="currentColor" /></svg>
		{:else}
			<span class="sparkle dot" style="left:{q.x}%; top:{q.y}%; width:{q.size}px; height:{q.size}px; background:{q.tint}; box-shadow:0 0 {q.size * 2}px {q.size / 2}px {q.tint}; animation-duration:{q.t}s; animation-delay:-{q.d}s"></span>
		{/if}
	{/each}
</div>

<style>
	/* a sparkle swells and turns a little, then goes out; every one has its own pace and offset (negative delay) */
	.sparkle {
		position: absolute;
		opacity: 0;
		transform: translate(-50%, -50%) scale(0.3);
		animation: ob-twinkle ease-in-out infinite;
		filter: drop-shadow(0 0 5px rgb(255 255 255 / 0.9)) drop-shadow(0 0 11px color-mix(in srgb, var(--rb-glow) 80%, transparent));
	}
	.dot {
		border-radius: 50%;
		filter: none;
	}
	@keyframes ob-twinkle {
		0%,
		100% {
			opacity: 0;
			transform: translate(-50%, -50%) scale(0.3) rotate(0deg);
		}
		50% {
			opacity: 1;
			transform: translate(-50%, -50%) scale(1) rotate(35deg);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.sparkle {
			animation: none;
			opacity: 0.7;
			transform: translate(-50%, -50%) scale(0.8);
		}
	}
</style>
