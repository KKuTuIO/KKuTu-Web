<script module>
	// Shared across windows: where each one was last left (by id), and which one is on top.
	const remembered = new Map();
	let topZ = 40;
</script>

<script>
	import { fade } from 'svelte/transition';
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';

	/**
	 * A movable, non-modal window for tools you use while playing (dictionary, ...).
	 * - Drag it by the title bar (mouse, touch, pen), or focus the title bar and use the arrow keys
	 *   (Shift = bigger steps). It can't be dragged out of reach: the title bar always stays on screen.
	 * - No dimmed backdrop and nothing behind it is blocked, so the game keeps running.
	 * - It fades to 50% while the pointer is away (`ghost`), and is fully opaque on hover, while it has
	 *   keyboard focus (so typing in it stays readable) and while being dragged.
	 * - It remembers where you left it (per `id`) and comes to the front when pressed.
	 * @type {{open?:boolean, title:string, id:string, width?:string, ghost?:boolean, children?:import('svelte').Snippet, onclose?:()=>void}}
	 */
	let { open = $bindable(false), title, id, width = '26rem', ghost = true, children, onclose } = $props();

	const KEEP = 96; // px of the window that must stay inside the screen horizontally
	const STEP = 16;
	let el = $state();
	let x = $state(0);
	let y = $state(0);
	let z = $state(topZ);
	let dragging = $state(false);
	let grab = { dx: 0, dy: 0 };

	const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), Math.max(lo, hi));
	function place(nx, ny) {
		const w = el?.offsetWidth ?? 0;
		x = clamp(nx, KEEP - w, window.innerWidth - KEEP);
		y = clamp(ny, 0, window.innerHeight - 48);
		remembered.set(id, { x, y });
	}
	const raise = () => (z = ++topZ);
	function close() {
		open = false;
		onclose?.();
	}

	// Opening: restore the last position, or start roughly centred and a little high.
	$effect(() => {
		if (!open || !el) return;
		raise();
		const was = remembered.get(id);
		if (was) place(was.x, was.y);
		else place((window.innerWidth - el.offsetWidth) / 2, Math.max(72, (window.innerHeight - el.offsetHeight) / 3));
		const onResize = () => place(x, y);
		window.addEventListener('resize', onResize);
		return () => window.removeEventListener('resize', onResize);
	});

	function down(e) {
		raise();
		if (e.button > 0 || e.target.closest('button')) return;
		dragging = true;
		grab = { dx: e.clientX - x, dy: e.clientY - y };
		e.currentTarget.setPointerCapture?.(e.pointerId);
	}
	function move(e) {
		if (dragging) place(e.clientX - grab.dx, e.clientY - grab.dy);
	}
	const up = () => (dragging = false);
	function key(e) {
		const step = e.shiftKey ? STEP * 4 : STEP;
		const d = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }[e.key];
		if (!d) return;
		e.preventDefault();
		place(x + d[0], y + d[1]);
	}
</script>

{#if open}
	<!-- Escape is a convenience; the close button is the accessible way out. -->
	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<div
		bind:this={el}
		role="dialog"
		aria-modal="false"
		tabindex="-1"
		aria-label={title}
		data-ghost={ghost}
		data-dragging={dragging}
		transition:fade={{ duration: 120 }}
		onpointerdown={raise}
		onkeydown={(e) => e.key === 'Escape' && close()}
		class="kk-card kk-float fixed flex max-h-[calc(100dvh-1rem)] flex-col overflow-hidden"
		style="left:{x}px;top:{y}px;width:min({width},calc(100vw - 1rem));z-index:{z}"
	>
		<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
		<div
			role="toolbar"
			aria-label={title}
			tabindex="0"
			onpointerdown={down}
			onpointermove={move}
			onpointerup={up}
			onpointercancel={up}
			onkeydown={key}
			class="flex min-h-12 touch-none items-center border-b border-(color:--kk-line) pr-1 pl-4 select-none focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-(--kk-accent) {dragging ? 'cursor-grabbing' : 'cursor-grab'}"
		>
			<Icon name="drag_indicator" size={18} class="mr-1.5 -ml-2 text-(color:--kk-muted)" />
			<strong>{title}</strong>
			<button type="button" aria-label={t('play.dialog.close')} onclick={close} class="kk-close ml-auto">
				<span aria-hidden="true"><Icon name="close" size={11} /></span>
			</button>
		</div>
		<div class="min-h-0 flex-1 overflow-y-auto p-4">{@render children?.()}</div>
	</div>
{/if}
