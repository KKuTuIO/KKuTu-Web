<script>
	import { onMount } from 'svelte';
	import { captureRef } from '$lib/invite.js';
	import '../app.css';
	import '$lib/game/game.css';
	let { children } = $props();
	onMount(() => {
		captureRef(location.href); // a friend's link (`?ref=`) is kept until the player is in the game (see the play page)
	});
</script>

{@render children?.()}
