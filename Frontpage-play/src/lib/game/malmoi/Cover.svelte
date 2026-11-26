<script>
	import Icon from '../lobby/Icon.svelte';
	/** The cover of a 말모이: one icon (a Material Symbol or an emoji) on a plain colour. An emoji keeps its own colours. */
	let { cover, size = 56, class: cls = '' } = $props();
</script>

<span aria-hidden="true" class="grid shrink-0 place-items-center rounded-[22%] border border-black/10 {cls}" style:width="{size}px" style:height="{size}px" style:background-color={cover.bg}>
	{#if cover.type === 'emoji'}
		<span class="leading-none" style:font-size="{Math.round(size * 0.52)}px">{cover.icon}</span>
	{:else}
		<span class="grid" style:color={cover.color}><Icon name={cover.icon} size={Math.round(size * 0.58)} fill /></span>
	{/if}
</span>
