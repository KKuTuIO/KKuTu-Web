<script>
	import { prefs } from '../prefs.svelte.js';
	import StageParallax from './StageParallax.svelte';

	/**
	 * The lobby stage's backdrop: the school plaza as stacked pictures (StageParallax) in the season the web server picks for the loading
	 * picture (MainController: 3-5 spring, 6-8 summer, 9-11 fall, else winter), with petals, motes, leaves or snow falling over it.
	 * `?season=spring|summer|fall|winter` previews another season. 움직임 감소 (설정) stops both the parallax and the falling.
	 * The stage's element can blur and zoom it (--stage-blur, --stage-zoom) and pause the falling (--play).
	 */
	const SEASONS = ['spring', 'summer', 'fall', 'winter'];
	const asked = typeof location !== 'undefined' ? new URLSearchParams(location.search).get('season') : null;
	const month = new Date().getMonth() + 1;
	const auto = month >= 3 && month <= 5 ? 'spring' : month >= 6 && month <= 8 ? 'summer' : month >= 9 && month <= 11 ? 'fall' : 'winter';
	const season = SEASONS.includes(asked) ? asked : auto;

	function rng(seed) {
		let s = seed;
		return () => ((s = (s * 16807) % 2147483647) - 1) / 2147483646;
	}
	// Falling things over the whole stage, as many as the season wants. Fixed seed: the same ones every time.
	const q = rng(23);
	const count = season === 'summer' ? 16 : 26;
	const fall = Array.from({ length: count }, () => ({ x: q() * 100, d: 9 + q() * 10, delay: -q() * 18, s: 7 + q() * 8, sway: 20 + q() * 40 }));
</script>

<div class="stage-art pointer-events-none absolute inset-0 overflow-hidden" data-season={season} aria-hidden="true" style="filter: blur(var(--stage-blur, 0px)); transform: scale(var(--stage-zoom, 1)); transition: filter 0.5s ease, transform 0.5s ease;">
	<StageParallax {season} still={prefs.reduceMotion} />
	{#if !prefs.reduceMotion}
		{#each fall as p}
			<span class="fall fall-{season}" style="left:{p.x}%;width:{p.s}px;height:{season === 'winter' || season === 'summer' ? p.s : p.s * 1.25}px;animation-duration:{p.d}s;animation-delay:{p.delay}s;--sway:{p.sway}px"></span>
		{/each}
	{/if}
</div>

<style>
	.stage-art[data-season='spring'] { --petal: #f6a9c0; }
	.stage-art[data-season='summer'] { --petal: #fff6b8; }
	.stage-art[data-season='fall'] { --petal: #e8743b; }
	.stage-art[data-season='winter'] { --petal: #ffffff; }
	.fall {
		position: absolute;
		top: -6%;
		opacity: 0.85;
		animation: sa-fall linear infinite;
		animation-play-state: var(--play, running);
	}
	.fall-spring { background: var(--petal); border-radius: 60% 0 60% 60%; }
	.fall-summer { background: var(--petal); border-radius: 50%; filter: blur(1px); opacity: 0.55; }
	.fall-fall { background: var(--petal); border-radius: 0 70% 0 70%; }
	.fall-winter { background: #fff; border-radius: 50%; opacity: 0.9; }
	@keyframes sa-fall {
		0% { transform: translate3d(0, 0, 0) rotate(0deg); }
		50% { transform: translate3d(var(--sway), 55vh, 0) rotate(160deg); }
		100% { transform: translate3d(calc(var(--sway) * -0.4), 112vh, 0) rotate(330deg); }
	}
	@media (prefers-reduced-motion: reduce) {
		.fall { animation: none; display: none; }
	}
</style>
