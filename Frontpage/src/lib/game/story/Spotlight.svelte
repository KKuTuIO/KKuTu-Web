<script>
	import Rich from '../ui/Rich.svelte';
	import { visibleEl } from './focus.js';

	/**
	 * Dims the page except for one part of it: the element `selector` finds (followed every frame, so a part that moves or
	 * appears is still found). Without a selector, or when nothing is on screen for it, the whole page is dimmed (unless `dim` is off: a stage that fills the screen).
	 * The dimmed part cannot be clicked (the part itself can): a stage's controls outside the focus are out of reach while it points.
	 * `label` is said beside the part (above it, or below when there is no room).
	 * @type {{selector?: string|null, pad?: number, dim?: boolean, label?: string}}
	 */
	let { selector = null, pad = 8, dim = true, label = '' } = $props();
	let rect = $state(/** @type {null|{x:number,y:number,w:number,h:number}} */ (null));

	$effect(() => {
		if (!selector) {
			rect = null;
			return;
		}
		let raf = 0;
		let scrolled = false;
		const look = () => {
			const el = visibleEl(selector);
			// a part that is out of the screen (a long sheet, a page that scrolls) is brought in once, so the spotlight has something to show
			if (el && !scrolled) {
				scrolled = true;
				const b = el.getBoundingClientRect();
				if (b.top < 0 || b.bottom > innerHeight) el.scrollIntoView({ block: 'center', inline: 'nearest' });
			}
			const r = el?.getBoundingClientRect();
			const next = r && r.width > 0 && r.height > 0 ? { x: Math.round(r.left - pad), y: Math.round(r.top - pad), w: Math.round(r.width + pad * 2), h: Math.round(r.height + pad * 2) } : null;
			if (next?.x !== rect?.x || next?.y !== rect?.y || next?.w !== rect?.w || next?.h !== rect?.h || (!next && rect)) rect = next;
			raf = requestAnimationFrame(look);
		};
		look();
		return () => cancelAnimationFrame(raf);
	});
</script>

<div class="pointer-events-none fixed inset-0 z-10 overflow-hidden">
	{#if rect}
		<!-- four plain walls around the part: they catch the clicks, the part itself stays open -->
		<div class="pointer-events-auto absolute inset-x-0 top-0" style:height="{Math.max(0, rect.y)}px"></div>
		<div class="pointer-events-auto absolute inset-x-0 bottom-0" style:top="{rect.y + rect.h}px"></div>
		<div class="pointer-events-auto absolute left-0" style:top="{rect.y}px" style:height="{rect.h}px" style:width="{Math.max(0, rect.x)}px"></div>
		<div class="pointer-events-auto absolute right-0" style:top="{rect.y}px" style:height="{rect.h}px" style:left="{rect.x + rect.w}px"></div>
		<div
			class="kk-spot absolute rounded-2xl border-[3px] border-(--kk-accent) shadow-[0_0_0_100vmax_rgb(0_0_0/0.55)]"
			style:left="{rect.x}px"
			style:top="{rect.y}px"
			style:width="{rect.w}px"
			style:height="{rect.h}px"
			aria-hidden="true"
		></div>
		{#if label}
			<div class="kk-spot-label absolute -translate-x-1/2 rounded-xl bg-(--kk-text) px-3.5 py-2 text-sm font-extrabold whitespace-nowrap text-(--kk-surface) shadow-[0_3px_0_rgb(0_0_0/0.35)] {rect.y > 80 ? '-translate-y-full' : ''}" style:left="{rect.x + rect.w / 2}px" style:top="{rect.y > 80 ? rect.y - 10 : rect.y + rect.h + 10}px" role="status"><Rich html={label} /></div>
		{/if}
	{:else if dim}
		<div class="absolute inset-0 bg-black/55"></div>
	{/if}
</div>

<style>
	@media (prefers-reduced-motion: no-preference) {
		.kk-spot {
			transition:
				left 0.28s ease,
				top 0.28s ease,
				width 0.28s ease,
				height 0.28s ease;
			animation: kk-spot-pulse 1.6s ease-in-out infinite;
		}
	}
	@keyframes kk-spot-pulse {
		50% {
			border-color: color-mix(in srgb, var(--kk-accent) 45%, white);
		}
	}
</style>
