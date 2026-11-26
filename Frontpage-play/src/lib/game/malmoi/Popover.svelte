<script>
	/** A button with a card that opens under it (the room list's 정렬 및 필터). A press outside or Esc closes it. */
	let { label, button, children, class: cls = '', align = 'right', buttonClass = '' } = $props();
	let open = $state(false);
	let el = $state();
	/** A button in the middle of a narrow screen: the card slides over so that none of it is cut off. */
	function keepInside(node) {
		const { left, right } = node.getBoundingClientRect();
		const dx = left < 8 ? 8 - left : Math.min(0, window.innerWidth - 8 - right);
		if (dx) node.style.translate = `${dx}px 0`;
	}
	const outside = (e) => open && !el?.contains(e.target) && (open = false);
	// before everything else on the page: the stage would take the same Esc as 뒤로 and leave the page
	function escape(e) {
		if (!open || e.key !== 'Escape') return;
		e.preventDefault();
		e.stopImmediatePropagation();
		open = false;
	}
</script>

<svelte:window onpointerdown={outside} onkeydowncapture={escape} />

<div bind:this={el} class="relative shrink-0 {cls}">
	<button type="button" aria-expanded={open} aria-label={label} onclick={() => (open = !open)} class="kk-btn inline-flex items-center gap-2 text-sm {buttonClass}">{@render button()}</button>
	{#if open}
		<div use:keepInside role="group" aria-label={label} class="kk-card absolute top-12 z-30 w-64 max-w-[calc(100vw-2rem)] p-3 {align === 'right' ? 'right-0' : 'left-0'}">{@render children()}</div>
	{/if}
</div>
