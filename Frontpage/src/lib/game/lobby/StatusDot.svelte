<script>
	/**
	 * A status as a small solid mark, all drawn here so they share one look (the icon font has no filled moon or minus):
	 * online = a green dot, idle = an amber crescent, dnd = a red dot with a bar, invisible / offline = a grey ring.
	 */
	let { status = 'online', size = 12, class: cls = '' } = $props();
	const uid = $props.id();
</script>

<svg viewBox="0 0 16 16" width={size} height={size} class="shrink-0 {cls}" role="img" aria-hidden="true" focusable="false">
	{#if status === 'online'}
		<circle cx="8" cy="8" r="8" fill="#22c55e" />
	{:else if status === 'idle'}
		<!-- a crescent: the full disc with a second disc cut out of its upper right -->
		<mask id="{uid}-m"><rect width="16" height="16" fill="#fff" /><circle cx="12.4" cy="3.6" r="6" fill="#000" /></mask>
		<circle cx="8" cy="8" r="8" fill="#f59e0b" mask="url(#{uid}-m)" />
	{:else if status === 'dnd'}
		<circle cx="8" cy="8" r="8" fill="#ef4444" />
		<rect x="3.6" y="6.6" width="8.8" height="2.8" rx="1.4" fill="#fff" />
	{:else}
		<circle cx="8" cy="8" r="5.6" fill="none" stroke="#9ca3af" stroke-width="3.2" />
	{/if}
</svg>
