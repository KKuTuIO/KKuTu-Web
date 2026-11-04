<script>
	import { onMount } from 'svelte';
	import { toasts } from './toast.svelte.js';
	import ToastItem from './ToastItem.svelte';

	let host = $state();
	// A popover lives in the browser's top layer, above even modal dialogs. Whatever was put there last is frontmost, so
	// the host is re-promoted whenever a toast arrives or a dialog opens.
	function promote() {
		if (!host?.showPopover) return;
		try {
			if (host.matches(':popover-open')) host.hidePopover();
			host.showPopover();
		} catch {
			/* no popover support: the z-index below still covers everything that isn't a modal */
		}
	}
	onMount(() => {
		promote();
		const seen = new WeakSet();
		const mo = new MutationObserver((records) => {
			if (records.some((r) => r.target instanceof HTMLDialogElement && r.target.open && !seen.has(r.target) && seen.add(r.target))) promote();
			for (const r of records) if (r.target instanceof HTMLDialogElement && !r.target.open) seen.delete(r.target);
		});
		mo.observe(document.body, { subtree: true, attributes: true, attributeFilter: ['open'] });
		return () => mo.disconnect();
	});
	$effect(() => {
		if (toasts.length) promote();
	});
</script>

<div bind:this={host} popover="manual" class="pointer-events-none fixed inset-x-0 top-8 bottom-auto z-[100] m-0 flex h-auto max-h-none w-auto max-w-none flex-col items-center gap-2 overflow-visible border-0 bg-transparent p-0 px-4" aria-live="polite">
	{#each toasts as t (t.id)}
		<ToastItem toast={t} />
	{/each}
</div>
