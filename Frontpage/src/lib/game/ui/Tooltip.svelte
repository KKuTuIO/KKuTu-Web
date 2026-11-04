<script>
	import { cubicOut } from 'svelte/easing';
	/**
	 * Tooltip for the element it wraps. Replaces `title="..."`.
	 *
	 *   <Tooltip text="Plain text"><button>…</button></Tooltip>
	 *   <Tooltip placement="bottom">{#snippet content()}<b>Rich</b> content{/snippet}<span>…</span></Tooltip>
	 *
	 * - Wraps without adding a box (display: contents); it attaches to the first child element.
	 * - Shows immediately on mouse hover (optionally after `delay` ms) and on keyboard focus; never on touch. Hides on leave, blur,
	 *   press, Escape, scroll and resize. The target gets aria-describedby while it is visible.
	 * - Lives in the browser top layer (popover), so dialogs and overflow can't clip or offset it, and it
	 *   flips to the other side when there is no room.
	 * @type {{text?:string, content?:import('svelte').Snippet, placement?:'top'|'bottom'|'left'|'right', delay?:number, children:import('svelte').Snippet}}
	 */
	let { text = '', content, placement = 'top', delay = 0, children } = $props();

	// Short slide-and-scale from the target's side; leaving is faster than arriving.
	function pop(node, { side = 'top', duration = 140 } = {}) {
		const [x, y] = { top: [0, 4], bottom: [0, -4], left: [4, 0], right: [-4, 0] }[side];
		return { duration, easing: cubicOut, css: (t, u) => `opacity:${t};transform:translate(${x * u}px,${y * u}px) scale(${0.94 + 0.06 * t})` };
	}

	const uid = `kk-tip-${Math.random().toString(36).slice(2, 8)}`;
	const GAP = 8;
	const MARGIN = 8; // keep this far from the viewport edge
	let host = $state();
	let tip = $state();
	let open = $state(false);
	let pos = $state({ left: 0, top: 0, side: 'top', arrowX: 0, arrowY: 0 });
	let timer;
	const enabled = $derived(Boolean(text) || Boolean(content));
	const target = () => host?.firstElementChild ?? null;

	function show() {
		if (!enabled || !target()) return;
		clearTimeout(timer);
		if (delay > 0) timer = setTimeout(() => (open = true), delay);
		else open = true;
	}
	// A focused target that an update removes fires `focusout` while Svelte is still tearing the page down, and writing state then is
	// refused (state_unsafe_mutation): closing a moment later is invisible and always allowed.
	function hide() {
		clearTimeout(timer);
		if (open) queueMicrotask(() => (open = false));
	}

	$effect(() => {
		const el = target();
		if (!el) return;
		const onEnter = (e) => e.pointerType !== 'touch' && show();
		const events = [
			['pointerenter', onEnter],
			['pointerleave', hide],
			['pointerdown', hide],
			['focusin', () => el.matches(':focus-visible') && show()],
			['focusout', hide]
		];
		for (const [n, h] of events) el.addEventListener(n, h);
		return () => {
			for (const [n, h] of events) el.removeEventListener(n, h);
			hide();
		};
	});

	// Once rendered, move to the top layer and place it next to the target.
	$effect(() => {
		if (!open || !tip) return;
		const el = target();
		if (!el) return;
		try {
			if (!tip.matches(':popover-open')) tip.showPopover?.();
		} catch {
			/* no popover support: fixed positioning below still works */
		}
		el.setAttribute('aria-describedby', uid);
		place(el);

		const away = () => hide();
		const esc = (e) => e.key === 'Escape' && hide();
		window.addEventListener('scroll', away, true);
		window.addEventListener('resize', away);
		window.addEventListener('keydown', esc);
		return () => {
			el.removeAttribute('aria-describedby');
			window.removeEventListener('scroll', away, true);
			window.removeEventListener('resize', away);
			window.removeEventListener('keydown', esc);
		};
	});

	function place(el) {
		const r = el.getBoundingClientRect();
		const w = tip.offsetWidth;
		const h = tip.offsetHeight;
		const vw = window.innerWidth;
		const vh = window.innerHeight;
		const room = { top: r.top, bottom: vh - r.bottom, left: r.left, right: vw - r.right };
		const need = { top: h + GAP, bottom: h + GAP, left: w + GAP, right: w + GAP };
		const opposite = { top: 'bottom', bottom: 'top', left: 'right', right: 'left' };
		const side = room[placement] >= need[placement] || room[opposite[placement]] < need[opposite[placement]] ? placement : opposite[placement];
		const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
		let left, top;
		if (side === 'top' || side === 'bottom') {
			left = clamp(r.left + r.width / 2 - w / 2, MARGIN, vw - w - MARGIN);
			top = side === 'top' ? r.top - h - GAP : r.bottom + GAP;
		} else {
			top = clamp(r.top + r.height / 2 - h / 2, MARGIN, vh - h - MARGIN);
			left = side === 'left' ? r.left - w - GAP : r.right + GAP;
		}
		pos = { left, top, side, arrowX: r.left + r.width / 2 - left, arrowY: r.top + r.height / 2 - top };
	}
</script>

<span bind:this={host} class="contents">{@render children()}</span>

{#if open && enabled}
	<div
		bind:this={tip}
		id={uid}
		role="tooltip"
		popover="manual"
		in:pop={{ side: placement }}
		out:pop={{ side: placement, duration: 90 }}
		class="kk-tooltip pointer-events-none m-0 w-max max-w-[min(20rem,calc(100vw-1rem))] rounded-lg border-0 px-2.5 py-1.5 text-left text-xs leading-snug font-bold break-keep"
		style="inset:auto;left:{pos.left}px;top:{pos.top}px"
	>
		{#if content}{@render content()}{:else}{text}{/if}
		<span
			class="kk-tooltip-arrow"
			data-side={pos.side}
			style:left={pos.side === 'top' || pos.side === 'bottom' ? `${pos.arrowX}px` : undefined}
			style:top={pos.side === 'left' || pos.side === 'right' ? `${pos.arrowY}px` : undefined}
		></span>
	</div>
{/if}

<style>
	.kk-tooltip {
		background: var(--kk-text);
		color: var(--kk-surface);
		box-shadow: 0 2px 0 rgb(0 0 0 / 0.35);
		overflow: visible;
	}
	.kk-tooltip-arrow {
		position: absolute;
		width: 8px;
		height: 8px;
		background: inherit;
		transform: translate(-50%, -50%) rotate(45deg);
	}
	.kk-tooltip-arrow[data-side='top'] { top: 100%; margin-top: -1px; }
	.kk-tooltip-arrow[data-side='bottom'] { top: 0; margin-top: 1px; }
	.kk-tooltip-arrow[data-side='left'] { left: 100%; margin-left: -1px; }
	.kk-tooltip-arrow[data-side='right'] { left: 0; margin-left: 1px; }
</style>
