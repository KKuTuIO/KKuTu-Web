<script>
	import '$lib/game/game.css';
	import '$lib/game/nickSkins.css';
	import { wornStyle } from '$lib/game/theme.svelte.js';
	let { children } = $props();
</script>

<!-- A worn 테마 overrides the game's theme tokens right here, so every screen and dialog below takes its colours. -->
<div class="kk contents" style={wornStyle()}>{@render children?.()}</div>
