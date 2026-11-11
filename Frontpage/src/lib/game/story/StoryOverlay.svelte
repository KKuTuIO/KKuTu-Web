<script>
	import { onDestroy, onMount } from 'svelte';
	import Spotlight from './Spotlight.svelte';
	import DialogueBox from './DialogueBox.svelte';

	/**
	 * Where a dialogue script plays: a full-screen modal <dialog>, so it is above every other dialog of the page and the page behind it
	 * can't be clicked. It holds, back to front: what the script's stage shows (`children`, e.g. the practice game), the spotlight, the bubble.
	 * 다음: Enter, Space, → or a press on the bubble (not while typing into a field, and not for a key meant for another dialog above it); ←: back;
	 * Esc asks `onexit`. The keys are taken from the window: when the practice game's answer field goes away the focus drops to the page, not into this dialog.
	 * `restack()` puts it above a dialog that was opened after it (a real dialog the script showed).
	 * `dim`: darken the page behind the bubble (off while a stage fills the screen).
	 * @type {{runner:any, store:any, placement?:'bottom'|'top', dim?:boolean, onexit?:()=>void, children?:import('svelte').Snippet}}
	 */
	let { runner, store, placement = 'bottom', dim = true, onexit, children } = $props();
	let el = $state();
	let box = $state();

	export function restack() {
		if (!el?.open) return;
		el.close();
		el.showModal();
	}
	onMount(() => el.showModal());
	// A new line takes the keyboard back: whatever had it (the practice game's field, a result window) may be gone.
	$effect(() => {
		runner.state.line?.n;
		runner.state.tip?.n;
		if (el?.open && !el.contains(document.activeElement)) el.focus({ preventScroll: true });
	});
	onDestroy(() => el?.open && el.close());

	function key(e) {
		if (e.defaultPrevented) return;
		const inDialog = e.target?.closest?.('dialog');
		if (inDialog && inDialog !== el) return; // a key for a dialog above this one (a result window, a question)
		if (e.target?.closest?.('input, textarea, select, [contenteditable]')) return;
		if (e.key === 'ArrowLeft') return runner.back();
		if (['Enter', ' ', 'ArrowRight'].includes(e.key) && !e.target?.closest?.('button')) {
			e.preventDefault();
			box?.press();
		}
	}
</script>

<svelte:window onkeydown={key} />

<dialog
	bind:this={el}
	tabindex="-1"
	oncancel={(e) => {
		e.preventDefault();
		onexit?.();
	}}
	style="background: transparent"
	class="fixed inset-0 m-0 size-full max-h-none max-w-none border-0 p-0 text-inherit outline-none backdrop:bg-transparent"
>
	{#if children}<div class="absolute inset-0 z-0">{@render children()}</div>{/if}
	<Spotlight selector={runner.state.focus} label={runner.state.focusLabel} {dim} />
	<DialogueBox bind:this={box} {runner} {store} placement={runner.state.at || placement} />
</dialog>
