<script>
	/**
	 * 평점 as a small solid mark (0 very low, 1 low, 2 fine, 3 high, 4 very high), drawn here like the status marks so the set looks
	 * alike. Lists only show the first two (a warning); the profile shows all of them.
	 */
	let { rating = 2, size = 16, class: cls = '' } = $props();
</script>

<svg viewBox="0 0 16 16" width={size} height={size} class="shrink-0 {cls}" role="img" aria-hidden="true" focusable="false">
	{#if rating === 0}
		<circle cx="8" cy="8" r="8" fill="#ef4444" />
		<rect x="7" y="3.4" width="2" height="6" rx="1" fill="#fff" />
		<circle cx="8" cy="11.9" r="1.2" fill="#fff" />
	{:else if rating === 1}
		<path d="M8 1.2c.6 0 1.1.3 1.4.8l5.8 10.2c.6 1-.1 2.3-1.4 2.3H2.2C.9 14.5.2 13.2.8 12.2L6.6 2c.3-.5.8-.8 1.4-.8Z" fill="#f59e0b" />
		<rect x="7" y="5.4" width="2" height="4.6" rx="1" fill="#fff" />
		<circle cx="8" cy="12" r="1.15" fill="#fff" />
	{:else if rating === 2}
		<circle cx="8" cy="8" r="8" fill="#22c55e" />
		<circle cx="8" cy="8" r="3.1" fill="#fff" />
	{:else if rating === 3}
		<circle cx="8" cy="8" r="8" fill="#3b82f6" />
		<path d="M4.4 8.3l2.5 2.5 4.7-5" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
	{:else}
		<path d="M8 .6l2.2 4.7 5.1.6-3.8 3.5 1 5.1L8 12l-4.5 2.5 1-5.1L.7 5.9l5.1-.6L8 .6Z" fill="#7c6cf2" stroke="#7c6cf2" stroke-width="1" stroke-linejoin="round" />
	{/if}
</svg>
