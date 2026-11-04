<script>
	/** On/off switch: a real <button role="switch">, so Space/Enter, focus and a11y work without native form controls. */
	let { checked = $bindable(false), disabled = false, onchange, children } = $props();
	function toggle() {
		if (disabled) return;
		checked = !checked;
		onchange?.(checked);
	}
</script>

<button type="button" role="switch" aria-checked={checked} {disabled} onclick={toggle} class="group inline-flex min-h-10 w-full items-center justify-between gap-3 text-left disabled:opacity-50">
	<span class="min-w-0">{@render children?.()}</span>
	<span class="relative h-6 w-11 shrink-0 rounded-full transition-colors group-focus-visible:outline-2 group-focus-visible:outline-offset-2 group-focus-visible:outline-(--kk-accent) {checked ? 'bg-(--kk-fill)' : 'bg-(--kk-line-strong) group-hover:bg-(--kk-accent-soft)'}">
		<span class="absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow-[0_1px_0_rgba(0,0,0,.18)] transition-transform duration-200 ease-out {checked ? 'translate-x-5' : ''}"></span>
	</span>
</button>
