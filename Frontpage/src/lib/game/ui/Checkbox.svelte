<script>
	import Icon from '../lobby/Icon.svelte';
	/** Custom checkbox: a real <button role="checkbox">, so Space/Enter, focus and a11y work without native form controls. */
	let { checked = $bindable(false), disabled = false, dense = false, onchange, children } = $props();
	function toggle() {
		if (disabled) return;
		checked = !checked;
		onchange?.(checked);
	}
</script>

<button type="button" role="checkbox" aria-checked={checked} {disabled} onclick={toggle} class="group inline-flex {dense ? 'min-h-8' : 'min-h-10'} items-center gap-2.5 text-left disabled:opacity-50">
	<span class="grid size-[22px] shrink-0 place-items-center rounded-md border-2 transition-colors group-focus-visible:outline-2 group-focus-visible:outline-offset-2 group-focus-visible:outline-(--kk-accent) {checked ? 'border-(color:--kk-fill) bg-(--kk-fill) text-white' : 'border-(color:--kk-line-strong) bg-(--kk-surface) group-hover:border-(color:--kk-accent)'}">
		{#if checked}<span class="kk-check-pop grid"><Icon name="check" size={17} /></span>{/if}
	</span>
	{@render children?.()}
</button>
