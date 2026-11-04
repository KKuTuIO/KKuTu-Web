<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import { playSound } from '../audio.svelte.js';
	/** Modal built on <dialog>: focus trap, Esc and backdrop come from the platform. */
	let { open = $bindable(false), title, children, onclose, bouncy = true, size = 'md', ghost = false } = $props();
	let el = $state();
	let closing = false;

	function finishClose() {
		closing = false;
		el.classList.remove('kk-closing');
		el.close();
	}

	$effect(() => {
		if (!el) return;
		if (open && !el.open) {
			playSound('dialog', { ui: true });
			closing = false;
			el.classList.remove('kk-closing');
			el.showModal();
			// showModal() focuses the first control, which is the close button; a dialog with a field of its own keeps
			// that field, the others focus the dialog itself so no button looks pressed.
			if (document.activeElement?.classList.contains('kk-close')) el.focus();
		} else if (!open && el.open && !closing) {
			if (!bouncy) return el.close();
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

<dialog
	bind:this={el}
	tabindex="-1"
	aria-label={title}
	oncancel={(e) => {
		if (!bouncy) return;
		e.preventDefault(); // let the exit animation run instead of closing instantly
		open = false;
	}}
	onclose={() => {
		open = false;
		onclose?.();
	}}
	onclick={(e) => e.target === el && (open = false)}
	class="kk-card m-auto max-w-none p-0 outline-none {size === 'lg' ? 'w-[min(56rem,calc(100vw-2rem))]' : 'w-[min(32rem,calc(100vw-2rem))]'} backdrop:bg-black/40 {bouncy ? 'kk-bouncy' : ''} {ghost ? 'kk-ghost' : ''}"
>
	<div class="flex max-h-[85dvh] flex-col">
		<div class="flex min-h-12 items-center border-b border-(color:--kk-line) pr-1 pl-4">
			<strong>{title}</strong>
			<button type="button" aria-label={t('play.dialog.close')} onclick={() => (open = false)} class="kk-close ml-auto">
				<span aria-hidden="true"><Icon name="close" size={11} /></span>
			</button>
		</div>
		<div class="min-h-0 flex-1 overflow-y-auto p-4">{@render children?.()}</div>
	</div>
</dialog>
