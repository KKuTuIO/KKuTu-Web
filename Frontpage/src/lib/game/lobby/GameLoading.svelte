<script>
	import { i18n, t, tList } from '../i18n.svelte.js';
	import { ms } from '../motion.js';
	import { tileCells } from '../gameTile.js';
	import Logo from './Logo.svelte';
	import Rich from '../ui/Rich.svelte';

	/**
	 * The veil over the board while a game loads, with the layout of the first loading screen. The board's tile (both tones)
	 * zooms in square by square from the top-left to the bottom-right until the screen is covered; when loading is over the
	 * squares zoom out, again from the top-left to the bottom-right. It stays a moment even when loading is quick.
	 */
	let { loading = false, label = t('kkutu.js.gameLoading'), message = t('kkutu.js.gameLoading'), progress = 33, minShown = 1200, instant = false, handoff = null, stage = $bindable('none') } = $props();
	// stage: 'in' while the squares are still arriving, 'covered' when the screen is solid, 'out' while they leave, else 'none'
	const SPREAD = 500; // ms for the sweep across the screen
	const ZOOM = 220; // ms for one square
	let shown = $state(false);
	let leaving = $state(false);
	let since = 0;
	let timer;
	let coverTimer;
	$effect(() => () => clearTimeout(coverTimer));
	let tip = $state('');
	$effect(() => {
		clearTimeout(timer);
		if (loading) {
			if (!shown) {
				since = Date.now();
				stage = instant ? 'covered' : 'in';
				if (!instant) {
					clearTimeout(coverTimer);
					coverTimer = setTimeout(() => (stage = 'covered'), ms(SPREAD + ZOOM));
				}
			}
			shown = true;
			leaving = false;
		} else if (shown) {
			timer = setTimeout(
				() => {
					leaving = true;
					stage = 'out';
					timer = setTimeout(() => {
						shown = false;
						stage = 'none';
					}, ms(SPREAD + ZOOM));
				},
				ms(Math.max(0, minShown - (Date.now() - since)))
			);
		}
		return () => clearTimeout(timer);
	});

	// the tip is picked as soon as the messages are there (the first load starts before they arrive)
	$effect(() => {
		if (!shown || tip || !i18n.ready) return;
		const all = tList('play.tip');
		let picked = '';
		try {
			// the page before/after this one shows the same tip: 'write' leaves it behind, 'read' takes it
			if (handoff === 'read') {
				picked = sessionStorage.getItem('kk-load-tip') ?? '';
				sessionStorage.removeItem('kk-load-tip');
			}
		} catch {
			/* no storage: a tip of its own */
		}
		tip = picked || (all[Math.floor(Math.random() * all.length)] ?? '');
		if (handoff === 'write') {
			try {
				sessionStorage.setItem('kk-load-tip', tip);
			} catch {
				/* ignore */
			}
		}
	});
	const uid = `kk-gl-clip-${Math.random().toString(36).slice(2, 8)}`;
	let w = $state(0);
	let h = $state(0);
	const cells = $derived(w && h ? tileCells(w, h, { spread: SPREAD }) : []);
</script>

{#if shown}
	<div class="fixed inset-0 z-[60] overflow-hidden" role="status" aria-live="polite" aria-label={label} bind:clientWidth={w} bind:clientHeight={h}>
		<svg class="absolute inset-0 size-full" aria-hidden="true">
			{#each cells as c (c.key)}
				<polygon class="kk-gl-cell {c.dark ? 'kk-gl-dark' : 'kk-gl-light'} {leaving ? 'kk-gl-out' : instant ? 'kk-gl-now' : ''}" points={c.points} style="--d:{c.delay}ms;--z:{ZOOM}ms;transform-origin:{c.cx}px {c.cy}px" />
			{/each}
		</svg>
		<!-- the components are masked by the same squares: they show only where a square has arrived -->
		<svg class="absolute size-0" aria-hidden="true">
			<clipPath id={uid} clipPathUnits="userSpaceOnUse">
				{#each cells as c (c.key)}
					<polygon class="kk-gl-clip {leaving ? 'kk-gl-out' : instant ? 'kk-gl-now' : ''}" points={c.points} style="--d:{c.delay}ms;--z:{ZOOM}ms;transform-origin:{c.cx}px {c.cy}px;transform-box:view-box" />
				{/each}
			</clipPath>
		</svg>
		{#if true}
			<div class="absolute inset-0 grid place-items-center p-6" style:clip-path="url(#{uid})">
				<div class="flex w-full max-w-lg flex-col items-center gap-7">
					<Logo class="h-14" />
					<div class="kk-card flex w-full flex-col gap-4 p-5">
						{#if tip}
							<div class="flex items-start gap-3">
								<span class="mt-0.5 shrink-0 rounded-lg bg-(--kk-accent-soft) px-2 py-0.5 text-xs font-black tracking-wider text-(color:--kk-accent-strong)">TIP</span>
								<p class="min-h-6 flex-1 font-bold break-keep"><Rich html={tip} /></p>
							</div>
						{/if}
						<div class="flex flex-col gap-1.5">
							<div class="h-3 overflow-hidden rounded-full bg-(--kk-surface-2) shadow-[inset_0_0_0_1.5px_var(--kk-line)]" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={progress}>
								<span class="block h-full rounded-full bg-(--kk-fill) shadow-[0_2px_0_var(--kk-fill-edge)] transition-[width] duration-500 ease-out" style:width="{progress}%"></span>
							</div>
							{#if message}<p class="text-center text-xs font-bold text-(color:--kk-muted)">{message}</p>{/if}
						</div>
					</div>
				</div>
			</div>
		{/if}
	</div>
{/if}

<style>
	.kk-gl-light { fill: color-mix(in srgb, var(--kk-accent) 6%, var(--kk-bg)); stroke: color-mix(in srgb, var(--kk-accent) 6%, var(--kk-bg)); }
	.kk-gl-dark { fill: color-mix(in srgb, var(--kk-accent) 13%, var(--kk-bg)); stroke: color-mix(in srgb, var(--kk-accent) 13%, var(--kk-bg)); }
	.kk-gl-clip { animation: kk-gl-in var(--z) ease-out var(--d) both; }
	.kk-gl-clip.kk-gl-out { animation: kk-gl-out var(--z) ease-in var(--d) both; }
	.kk-gl-cell { stroke-width: 1; animation: kk-gl-in var(--z) ease-out var(--d) both; }
	/* leaving: the squares zoom out, again from the top-left to the bottom-right */
	.kk-gl-cell.kk-gl-out { animation: kk-gl-out var(--z) ease-in var(--d) both; }
	.kk-gl-now { animation: none !important; }
	.kk-gl-now.kk-gl-out { animation: kk-gl-out var(--z) ease-in var(--d) both !important; }
	@keyframes kk-gl-in { from { transform: scale(0); } to { transform: scale(1); } }
	@keyframes kk-gl-out { from { transform: scale(1); } to { transform: scale(0); } }
	@media (prefers-reduced-motion: reduce) {
		.kk-gl-cell, .kk-gl-cell.kk-gl-out, .kk-gl-clip { animation: none; }
	}
</style>
