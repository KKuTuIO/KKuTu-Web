<script>
	/** The game's own currency/stat icons (same art as the legacy client). */
	let { name, size = 22 } = $props();
	const src = { ping: '/img/play/ping.png', xp: '/img/play/xp.png', playtime: '/img/play/playtime.png', win: '/img/play/win.png' };
</script>

<img src={src[name]} alt="" aria-hidden="true" draggable="false" width={size} height={size} class="inline-block shrink-0 object-contain" style:width="{size}px" style:height="{size}px" />
