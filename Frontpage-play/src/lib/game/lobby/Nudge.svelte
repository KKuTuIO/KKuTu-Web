<script>
	import { nudgeOpen, closeNudge } from '../nudges.svelte.js';
	import { t } from '../i18n.svelte.js';

	/**
	 * A one-time hint bubble pointing at the element it wraps (the wrapper adds no box, like Tooltip).
	 *
	 *   <Nudge id="tutorial" {enabled}>{#snippet content()}…{/snippet}<button>…</button></Nudge>
	 *
	 * It shows while `enabled` and the nudge `id` isn't closed (see nudges.svelte.js), and only if the target is on screen (the
	 * same button exists in the desktop bar and the phone strip; the hidden one has no size and shows nothing).
	 * The bubble presses the target when pressed, and has a × that dismisses the nudge for good (closeNudge, kept on the account). It is a fixed layer under the loading veil and the dialogs (not in the
	 * top layer, which would put it over them), and a fixed layer isn't clipped by the phone strip's scrolling.
	 * `onclose`: what the × does when the nudge is not one of nudges.svelte.js's (the announcement bubble is not kept on the account).
	 * `beside` ('left' | 'right'): next to the target, centred on it, instead of above or below (the tab rail of a folding phone stands at the screen's edge).
	 * @type {{id:string, enabled?:boolean, above?:boolean, beside?:'left'|'right'|null, onclose?:() => void, content:import('svelte').Snippet, children:import('svelte').Snippet}}
	 */
	let { id, enabled = true, above = false, beside = null, onclose, content, children } = $props();
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
	// The bubble is `position: fixed`, but a fixed box is placed against the nearest ancestor that has a filter (the glass tab bar's backdrop-filter) or a
	// transform instead of the screen, so inside such a parent it lands far off. It lives under the client's root (`.kk`: the colour tokens are defined
	// there, and that wrapper has no box of its own) instead of next to its target.
	function portal(node) {
		(host?.closest('.kk') ?? document.body).appendChild(node);
		return { destroy: () => node.remove() };
	}

	function measure() {
		const el = target();
		const r = el?.getBoundingClientRect();
		const visible = !!r && r.width > 0 && r.height > 0;
		if (visible !== shown) shown = visible;
		if (!visible || !bubble) return;
		const w = bubble.offsetWidth;
		const h = bubble.offsetHeight;
		let left, top, arrow;
		if (beside) {
			// beside the target, centred on it; the pointer is then a vertical offset
			left = Math.round(Math.max(MARGIN, beside === 'left' ? r.left - GAP - w : r.right + GAP));
			top = Math.round(Math.min(Math.max(MARGIN, r.top + r.height / 2 - h / 2), Math.max(MARGIN, innerHeight - h - MARGIN)));
			arrow = Math.round(r.top + r.height / 2 - top);
		} else {
			left = Math.round(Math.min(Math.max(MARGIN, r.left + r.width / 2 - w / 2), Math.max(MARGIN, innerWidth - w - MARGIN)));
			top = Math.round(above ? r.top - GAP - h : r.bottom + GAP);
			arrow = Math.round(r.left + r.width / 2 - left);
		}
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
	<div
		bind:this={bubble}
		use:portal
		style:left="{pos.left}px"
		style:top="{pos.top}px"
		style:--arrow="{pos.arrow}px"
		class:above class:beside-left={beside === 'left'} class:beside-right={beside === 'right'} class="kk-hint fixed z-40 m-0 flex max-w-[min(20rem,calc(100vw-1rem))] items-stretch overflow-visible rounded-2xl border-[1.5px] border-(color:--kk-line-strong) bg-(--kk-surface) text-left text-sm font-bold text-balance text-(color:--kk-text) shadow-[0_3px_0_var(--kk-line-strong)]"
	>
		<button type="button" onclick={() => target()?.click()} class="flex min-w-0 flex-1 items-center gap-2.5 rounded-l-2xl py-2 pr-2 pl-2 text-left">{@render content()}</button>
		<button type="button" aria-label={t('play.dialog.close')} onclick={() => (onclose ? onclose() : closeNudge(id))} class="grid w-9 shrink-0 place-items-start justify-center rounded-r-2xl pt-1.5 text-(color:--kk-muted) hover:text-(color:--kk-text)"><span class="material-symbols-outlined leading-none select-none" aria-hidden="true" style="font-size:18px">close</span></button>
	</div>
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
	/* beside its target: the pointer is on the edge that faces it, at the target's height */
	.kk-hint.beside-left::before {
		top: var(--arrow);
		left: auto;
		right: -7px;
		margin: -6px 0 0;
		rotate: 135deg;
	}
	.kk-hint.beside-right::before {
		top: var(--arrow);
		left: -7px;
		margin: -6px 0 0;
		rotate: 315deg;
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
