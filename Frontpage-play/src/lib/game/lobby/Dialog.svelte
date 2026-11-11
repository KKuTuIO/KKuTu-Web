<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import { playSound } from '../audio.svelte.js';
	import { haptic } from '../haptics.js';
	/**
	 * Modal built on <dialog>: focus trap, Esc and backdrop come from the platform. The dialog and everything in it exist only while it is open
	 * (and while it animates out): a closed dialog leaves nothing in the page for a script to read or hook.
	 */
	let { open = $bindable(false), title, children, onclose, bouncy = true, size = 'md', ghost = false } = $props();
	let el = $state();
	let closing = false;
	// A phone gets a bottom sheet instead of a centred pop-up: it slides up, and is pulled down by its handle to dismiss it.
	let winW = $state(1280);
	const sheet = $derived(winW < 768);
	const animated = $derived(bouncy || sheet);
	let pull = $state(0);
	let pulling = $state(false);
	let grab = { y: 0, t: 0, v: 0, last: 0 };
	function pullStart(e) {
		if (e.button > 0 || e.target.closest('button')) return;
		pulling = true;
		grab = { y: e.clientY, t: performance.now(), v: 0, last: e.clientY };
		e.currentTarget.setPointerCapture?.(e.pointerId);
	}
	function pullMove(e) {
		if (!pulling) return;
		const now = performance.now();
		grab.v = (e.clientY - grab.last) / Math.max(1, now - grab.t);
		grab.t = now;
		grab.last = e.clientY;
		pull = Math.max(0, e.clientY - grab.y);
	}
	function pullEnd() {
		if (!pulling) return;
		pulling = false;
		// far enough, or flicked down: dismiss; otherwise spring back
		if (pull > Math.min(160, (el?.offsetHeight ?? 400) * 0.3) || grab.v > 0.6) {
			haptic(8);
			open = false;
		} else if (pull) {
			haptic(4);
			pull = 0;
		}
	}
	let keep = $state(false); // closed, but still animating out
	const mounted = $derived(open || keep);
	// closing while still on screen: stay mounted until the exit animation is over (finishClose / onclose unload it)
	$effect.pre(() => {
		if (!open && el?.open) keep = true;
	});

	function finishClose() {
		closing = false;
		el.classList.remove('kk-closing');
		el.close();
		keep = false;
	}

	$effect(() => {
		if (!el) return;
		if (open && !el.open) {
			pull = 0;
			playSound('dialog', { ui: true });
			closing = false;
			el.classList.remove('kk-closing');
			el.showModal();
			// showModal() focuses the first control, which is the close button; a dialog with a field of its own keeps
			// that field, the others focus the dialog itself so no button looks pressed.
			if (document.activeElement?.classList.contains('kk-close')) el.focus();
		} else if (!open && el.open && !closing) {
			if (!animated) return el.close();
			closing = true;
			el.classList.add('kk-closing');
			el.addEventListener('animationend', function done(e) {
				if (e.target !== el) return; // ignore the backdrop's own animation
				el.removeEventListener('animationend', done);
				finishClose();
			});
		}
	});
</script>

<svelte:window bind:innerWidth={winW} />

{#if mounted}
<dialog
	bind:this={el}
	tabindex="-1"
	aria-label={title}
	oncancel={(e) => {
		if (!animated) return;
		e.preventDefault(); // let the exit animation run instead of closing instantly
		open = false;
	}}
	onclose={() => {
		open = false;
		keep = false;
		onclose?.();
	}}
	onclick={(e) => e.target === el && (open = false)}
	style={sheet ? `translate:0 ${pull}px;${pulling ? '' : 'transition:translate 0.2s ease-out'}` : undefined}
	class="kk-card max-w-none p-0 outline-none backdrop:bg-black/40 {sheet ? 'kk-sheet m-0 mt-auto w-full overflow-x-hidden rounded-b-none border-b-0 pb-[env(safe-area-inset-bottom)]' : `m-auto ${size === 'lg' ? 'w-[min(56rem,calc(100vw-2rem))]' : 'w-[min(32rem,calc(100vw-2rem))]'} ${bouncy ? 'kk-bouncy' : ''}`} {ghost ? 'kk-ghost' : ''}"
>
	<div class="flex {sheet ? 'max-h-[90dvh] min-w-0' : 'max-h-[85dvh]'} flex-col">
		<!-- On a phone the title bar is the handle: pull it down to close the sheet. -->
		<div
			onpointerdown={sheet ? pullStart : undefined}
			onpointermove={sheet ? pullMove : undefined}
			onpointerup={sheet ? pullEnd : undefined}
			onpointercancel={sheet ? pullEnd : undefined}
			class="flex min-h-12 items-center border-b border-(color:--kk-line) pr-1 pl-4 {sheet ? 'relative touch-none pt-3 select-none' : ''}"
		>
			{#if sheet}<span class="absolute top-2 left-1/2 h-1 w-10 -translate-x-1/2 rounded-full bg-(--kk-line-strong)" aria-hidden="true"></span>{/if}
			<strong>{title}</strong>
			<button type="button" aria-label={t('play.dialog.close')} onclick={() => (open = false)} class="kk-close ml-auto">
				<span aria-hidden="true"><Icon name="close" size={11} /></span>
			</button>
		</div>
		<div class="min-h-0 min-w-0 flex-1 overflow-x-hidden overflow-y-auto p-4">{@render children?.()}</div>
	</div>
</dialog>
{/if}
