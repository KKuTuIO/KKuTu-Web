<script>
	import { onMount } from 'svelte';
	import { stageUrl } from './stageArt.js';

	/**
	 * The lobby stage as stacked layers that drift a little with the mouse (a fake 3D parallax).
	 * Every layer is the same square picture, in the same place, so they register by construction; each one is cropped the same way
	 * (object-fit: cover, so a wide stage shows the middle band and a tall phone shows the middle column) and then shifted by its own depth.
	 * Near layers move more. On a touch screen, and after the mouse has rested, the scene sways slowly by itself. Nothing moves for
	 * `prefers-reduced-motion`.
	 *
	 * Time of day: every layer has a night twin (lit windows, lamp halos, a moon). The two are cross-faded by the local hour
	 * (day 7-17, dusk 17-19, night 19-5, dawn 5-7); `?time=22` previews an hour.
	 *
	 * The drift is written as CSS variables (--px, --py, --night) on the nearest `[data-stage]` ancestor, so whatever stands on the
	 * floor (the Moremi) can move with it by reading the same variables.
	 * `still` (움직임 감소) holds the layers where they are: no drift with the mouse, no sway.
	 * @type {{season:string, still?:boolean}}
	 */
	let { season, still = false } = $props();
	let w = $state(0);
	let h = $state(0);
	let root = $state();
	let stageEl = $state();
	// the wide stage keeps the building and the mat in the middle band; the phone zooms in on the clock tower
	const wide = $derived(w / Math.max(h, 1) > 1.4);
	const pos = $derived(wide ? '50% 44%' : '50% 50%');
	const zoom = $derived(wide ? 1.07 : 1.2);
	// far to near (the drawing order), with how many px each layer travels at the edge of the stage.
	// The trees stand right behind the building and its railing (so they show through the railing and never cover it), hence they travel less than the building.
	const layers = $derived([
		{ day: 'sky.webp', night: 'night-sky.webp', d: 3 },
		{ day: `trees-${season}.webp`, night: `night-trees-${season}.webp`, d: 5 },
		{ day: 'floor.webp', night: 'night-floor.webp', d: 12 },
		{ day: 'building.webp', night: 'night-building.webp', d: 7 },
		{ day: 'props.webp', night: 'night-props.webp', d: 16 },
		{ day: 'bushes.webp', night: 'night-bushes.webp', d: 26 }
	]);

	const qs = typeof location !== 'undefined' ? new URLSearchParams(location.search) : new URLSearchParams();
	const fixedHour = qs.has('time') ? Number(qs.get('time')) : null;
	/** 0 (day) .. 1 (night) for an hour of the day */
	function nightAt(hour) {
		const x = ((hour % 24) + 24) % 24;
		if (x >= 7 && x < 17) return 0;
		if (x >= 17 && x < 19) return (x - 17) / 2;
		if (x >= 19 || x < 5) return 1;
		return 1 - (x - 5) / 2;
	}
	const nowHour = () => {
		const d = new Date();
		return d.getHours() + d.getMinutes() / 60;
	};
	let night = $state(nightAt(fixedHour ?? nowHour()));

	$effect(() => {
		stageEl?.style.setProperty('--night', night.toFixed(3));
	});

	// Where the plate (the round mat) is on the stage, whatever the stage's size: the pictures are square, shown with object-fit: cover at
	// `pos` and zoomed about 50% 55%, so the anchor (the Moremi's feet, in picture pixels) is mapped the same way. Written as --mx / --my (px)
	// and --ms (the picture's scale, px per picture pixel) on the stage, for whatever stands on the plate.
	const ANCHOR = { x: 627, y: 790, size: 1254 };
	$effect(() => {
		if (!stageEl || !w || !h) return;
		const posY = wide ? 0.44 : 0.5;
		const s = Math.max(w, h) / ANCHOR.size;
		const bx = (w - ANCHOR.size * s) * 0.5 + ANCHOR.x * s;
		const by = (h - ANCHOR.size * s) * posY + ANCHOR.y * s;
		const ox = w * 0.5;
		const oy = h * 0.55;
		stageEl.style.setProperty('--mx', `${(ox + (bx - ox) * zoom).toFixed(1)}px`);
		stageEl.style.setProperty('--my', `${(oy + (by - oy) * zoom).toFixed(1)}px`);
		stageEl.style.setProperty('--ms', (s * zoom).toFixed(3));
	});

	onMount(() => {
		stageEl = root?.closest('[data-stage]') ?? root;
		const stops = [];
		if (fixedHour === null) {
			const t = setInterval(() => (night = nightAt(nowHour())), 60_000);
			stops.push(() => clearInterval(t));
		}
		if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
			const coarse = matchMedia('(pointer: coarse)').matches;
			let tx = 0, ty = 0, cx = 0, cy = 0, last = -1e9, raf = 0, paused = false;
			const pause = () => (paused = true);
			const resume = () => ((paused = false), (last = performance.now()));
			const move = (e) => {
				if (e.pointerType !== 'mouse') return;
				tx = (e.clientX / innerWidth - 0.5) * 2;
				ty = (e.clientY / innerHeight - 0.5) * 2;
				last = performance.now();
			};
			const tick = (now) => {
				if (paused) return void (raf = requestAnimationFrame(tick));
				if (still) {
					tx = ty = 0;
				} else if (coarse || now - last > 3000) {
					tx = Math.sin(now / 2800) * 0.7;
					ty = Math.cos(now / 3600) * 0.35;
				}
				cx += (tx - cx) * 0.06;
				cy += (ty - cy) * 0.06;
				stageEl?.style.setProperty('--px', cx.toFixed(3));
				stageEl?.style.setProperty('--py', cy.toFixed(3));
				raf = requestAnimationFrame(tick);
			};
			addEventListener('pointermove', move, { passive: true });
			stageEl?.addEventListener('stagepause', pause);
			stageEl?.addEventListener('stageresume', resume);
			raf = requestAnimationFrame(tick);
			stops.push(() => {
				removeEventListener('pointermove', move);
				stageEl?.removeEventListener('stagepause', pause);
				stageEl?.removeEventListener('stageresume', resume);
				cancelAnimationFrame(raf);
			});
		}
		return () => stops.forEach((s) => s());
	});
</script>

{#snippet pic(src, d, opacity)}
	<img
		src={stageUrl(src)}
		alt=""
		draggable="false"
		class="absolute inset-0 size-full object-cover will-change-transform select-none"
		style:object-position={pos}
		style:transform-origin="50% 55%"
		style:transform="translate3d(calc(var(--px, 0) * {d}px), calc(var(--py, 0) * {d * 0.5}px), 0) scale({zoom})"
		style:opacity={opacity}
	/>
{/snippet}

<div bind:this={root} bind:clientWidth={w} bind:clientHeight={h} class="absolute inset-0 overflow-hidden">
	{#each layers as l (l.day)}
		{#if night < 1}{@render pic(l.day, l.d, 1)}{/if}
		{#if night > 0}{@render pic(l.night, l.d, night)}{/if}
	{/each}
	<!-- dusk and dawn: a little warm light low on the horizon while the two pictures cross-fade -->
	<div class="absolute inset-0" style:background="radial-gradient(ellipse 95% 50% at 50% 60%, rgb(255 150 70 / {0.3 * Math.sin(Math.PI * night)}), transparent 72%)"></div>
	<!-- a soft vignette: it settles the edges (and the seams of a generated picture) and darkens the bottom a little under the buttons -->
	<div class="vig absolute inset-0" style:--vig={0.26 + 0.16 * night}></div>
</div>

<style>
	.vig {
		background:
			radial-gradient(ellipse 85% 80% at 50% 52%, transparent 58%, rgb(24 28 70 / var(--vig)) 100%),
			linear-gradient(to top, rgb(24 28 70 / calc(var(--vig) * 0.55)) 0%, transparent 28%);
	}
</style>
