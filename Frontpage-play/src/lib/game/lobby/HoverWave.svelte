<script>
	/**
	 * A label whose letters run a wave while its button is hovered: the event title's wave (NewsWaveTitle: every letter bobs a little behind the
	 * one before it), without that title's gradient. The button carries the class `hw`; at rest the letters stand still.
	 * Screen readers get the plain text. With motion reduced the letters stand still.
	 * @type {{text:string}}
	 */
	let { text } = $props();
	const letters = $derived([...text]);
</script>

<span class="sr-only">{text}</span><span aria-hidden="true">{#each letters as ch, i (i)}<span class="wv" style:--i={i}>{ch === ' ' ? ' ' : ch}</span>{/each}</span>

<style>
	.wv {
		display: inline-block;
	}
	@media (prefers-reduced-motion: no-preference) {
		:global(.hw:hover:not(:disabled)) .wv {
			animation: hw-wave 0.95s ease-in-out infinite;
			animation-delay: calc(var(--i) * 0.08s);
		}
	}
	@keyframes hw-wave {
		0%,
		100% {
			transform: translateY(0);
		}
		50% {
			transform: translateY(-0.16em);
		}
	}
</style>
