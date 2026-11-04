<script>
	import { cdnImg } from '../../cdn.js';
	/** "Nothing found" placeholder: the searching Moremi plus a message. */
	let { title, hint = '', size = 160, compact = false } = $props();
</script>

<div class="flex flex-col items-center justify-center gap-1 text-center {compact ? 'p-3' : 'p-8'}" role="status">
	<img src={cdnImg('play/not-found.png')} alt="" width={size} style:width="{size}px" class="h-auto max-w-full" draggable="false" />
	<p class="font-extrabold">{title}</p>
	{#if hint}<p class="text-xs text-(color:--kk-muted)">{hint}</p>{/if}
</div>
