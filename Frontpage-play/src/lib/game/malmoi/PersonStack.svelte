<script>
	import Person from './Person.svelte';

	/** Several people as overlapping Moremis (the first on top), then how many more. */
	let { names, size = 28, max = 4, ring = 'var(--kk-surface)' } = $props();
	const shown = $derived(names.slice(0, max));
</script>

<span class="inline-flex items-center" title={names.join(', ')}>
	{#each shown as n, i (n)}<span class={i ? '-ml-2' : ''} style:z-index={shown.length - i}><Person name={n} {size} {ring} /></span>{/each}
	{#if names.length > max}<span class="-ml-2 grid place-items-center rounded-full bg-(--kk-surface-2) text-[11px] font-black text-(color:--kk-muted)" style:width="{size}px" style:height="{size}px" style:box-shadow="0 0 0 2px {ring}">+{names.length - max}</span>{/if}
</span>
