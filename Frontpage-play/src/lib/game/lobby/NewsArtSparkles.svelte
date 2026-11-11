<script>
	import Sparkles from './Sparkles.svelte';

	/**
	 * The checkerboard layer and a few twinkling stars and dots of an `.kk-arcade` backdrop (a direct child of it) (the same particles as the obtain screen's ribbon). Fixed positions: the pattern is
	 * decoration, not data. They are left out when motion is reduced.
	 */
	const LIST = [
		{ star: 1, x: 6, y: 30, size: 22, tint: '#fff', t: 2.6, d: 0.2 },
		{ star: 1, x: 22, y: 78, size: 14, tint: '#fde68a', t: 3.1, d: 1.1 },
		{ star: 0, x: 38, y: 24, size: 5, tint: '#fff', t: 2.2, d: 0.6 },
		{ star: 1, x: 56, y: 70, size: 18, tint: '#fff', t: 2.9, d: 1.7 },
		{ star: 0, x: 71, y: 20, size: 6, tint: '#fef08a', t: 2.4, d: 0.9 },
		{ star: 1, x: 84, y: 62, size: 24, tint: '#fff', t: 3.4, d: 0.4 },
		{ star: 0, x: 93, y: 28, size: 5, tint: '#fff', t: 2.1, d: 1.4 }
	];
</script>

<div class="kk-checker-clip" aria-hidden="true"><div class="kk-checker"></div></div>
<div class="motion-reduce:hidden" style="--rb-ray:#fff;--rb-glow:#f0abfc" aria-hidden="true"><Sparkles list={LIST} /></div>
