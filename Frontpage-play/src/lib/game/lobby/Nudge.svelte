<script>
	import { nudgeOpen } from '../nudges.svelte.js';

	/**
	 * A one-time hint bubble pointing at the element it wraps (the wrapper adds no box, like Tooltip).
	 *
	 *   <Nudge id="tutorial" {enabled}>{#snippet content()}…{/snippet}<button>…</button></Nudge>
	 *
	 * It shows while `enabled` and the nudge `id` isn't closed (see nudges.svelte.js), and only if the target is on screen (the
	 * same button exists in the desktop bar and the phone strip; the hidden one has no size and shows nothing).
	 * The bubble is a button: pressing it presses the target. It is a fixed layer under the loading veil and the dialogs (not in the
	 * top layer, which would put it over them), and a fixed layer isn't clipped by the phone strip's scrolling.
	 * @type {{id:string, enabled?:boolean, above?:boolean, content:import('svelte').Snippet, children:import('svelte').Snippet}}
	 */
	let { id, enabled = true, above = false, content, children } = $props();
	const GAP = 10;
	const MARGIN = 8;
	let host = $state();
	let bubble = $state();
	let shown = $state(false);
	let pos = $state({ left: 0, top: 0, arrow: 0 });
	// the first element with a box: wrappers that add none (this one, Tooltip) are looked through
	function target() {
		let el = host?.firstElementChild ?? null;
		while (el && getComputedStyle(el).display === 'contents') el = el.firstElementChild;
		return el;
	}
	const wanted = $derived(enabled && nudgeOpen(id));

	function measure() {
		const el = target();
		const r = el?.getBoundingClientRect();
		const visible = !!r && r.width > 0 && r.height > 0;
		if (visible !== shown) shown = visible;
		if (!visible || !bubble) return;
		const w = bubble.offsetWidth;
		const left = Math.round(Math.min(Math.max(MARGIN, r.left + r.width / 2 - w / 2), Math.max(MARGIN, innerWidth - w - MARGIN)));
		const top = Math.round(above ? r.top - GAP - bubble.offsetHeight : r.bottom + GAP);
		const arrow = Math.round(r.left + r.width / 2 - left);
		if (left !== pos.left || top !== pos.top || arrow !== pos.arrow) pos = { left, top, arrow };
	}

	// Follow the target while the nudge is wanted. Every frame: a header that slides or re-flows moves the button without any
	// resize or scroll event, and a bubble measured once would stay where the button used to be.
	$effect(() => {
		if (!wanted) {
			shown = false;
			return;
		}
		let raf = requestAnimationFrame(function tick() {
			measure();
			raf = requestAnimationFrame(tick);
		});
		return () => cancelAnimationFrame(raf);
	});
</script>

<span bind:this={host} class="contents">{@render children()}</span>
{#if wanted && shown}
	<button
		bind:this={bubble}
		type="button"
		onclick={() => target()?.click()}
		style:left="{pos.left}px"
		style:top="{pos.top}px"
		style:--arrow="{pos.arrow}px"
		class:above class="kk-hint fixed z-40 m-0 flex max-w-[min(20rem,calc(100vw-1rem))] items-center gap-2.5 overflow-visible rounded-2xl border-[1.5px] border-(color:--kk-line-strong) bg-(--kk-surface) py-2 pr-4 pl-2 text-left text-sm font-bold text-balance text-(color:--kk-text) shadow-[0_3px_0_var(--kk-line-strong)]"
	>
		{@render content()}
	</button>
{/if}

<style>
	/* the pointer: a rotated square on the bubble's top edge, under the target */
	.kk-hint::before {
		content: '';
		position: absolute;
		top: -7px;
		left: var(--arrow);
		width: 12px;
		height: 12px;
		margin-left: -6px;
		rotate: 45deg;
		background: var(--kk-surface);
		border-top: 1.5px solid var(--kk-line-strong);
		border-left: 1.5px solid var(--kk-line-strong);
	}
	/* above its target (the phone's tab bar): the pointer is on the bottom edge */
	.kk-hint.above::before {
		top: auto;
		bottom: -7px;
		rotate: 225deg;
	}
	@media (prefers-reduced-motion: no-preference) {
		.kk-hint {
			animation: kk-hint-bob 2.4s ease-in-out infinite;
		}
	}
	@keyframes kk-hint-bob {
		0%,
		100% {
			translate: 0 0;
		}
		50% {
			translate: 0 3px;
		}
	}
</style>
