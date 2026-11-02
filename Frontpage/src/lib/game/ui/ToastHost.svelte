<script>
	import { toasts } from './toast.svelte.js';
	import ToastItem from './ToastItem.svelte';
</script>

<div class="pointer-events-none fixed inset-x-0 top-8 z-[100] flex flex-col items-center gap-2 px-4" aria-live="polite">
	{#each toasts as t (t.id)}
		<ToastItem toast={t} />
	{/each}
</div>
