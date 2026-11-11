<script>
	import { cdnImg } from '../../cdn.js';
	/** The game's own currency/stat icons (same art as the legacy client). */
	let { name, size = 22 } = $props();
	const src = Object.fromEntries(['ping', 'xp', 'playtime', 'win', 'wordToken'].map((n) => [n, cdnImg(`play/${n}.png`)]));
</script>

<img src={src[name]} alt="" aria-hidden="true" draggable="false" width={size} height={size} class="inline-block shrink-0 object-contain" style:width="{size}px" style:height="{size}px" />
