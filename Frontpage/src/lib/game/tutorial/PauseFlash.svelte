<script>
	import Icon from '../lobby/Icon.svelte';

	/**
	 * The practice game's pause, as a video player shows it: a round dark badge with the pause (or, when the game goes on, the play) symbol
	 * pops up in the middle of the game, grows and fades away. It is staging only (nothing to press, nothing that stays on the screen).
	 * `pause`: {on, kind: 'pause' | 'play', n} (see chainGame.svelte.js); every change (`n`) shows the picture once.
	 * @type {{pause:{on:boolean, kind:'pause'|'play', n:number}}}
	 */
	let { pause } = $props();
</script>

{#key pause.n}
	{#if pause.n}
		<div class="kk-flash pointer-events-none absolute top-1/2 left-1/2 z-20 grid size-24 place-items-center rounded-full bg-black/60 text-white" aria-hidden="true">
			<Icon name={pause.kind === 'pause' ? 'pause' : 'play_arrow'} size={56} fill />
		</div>
	{/if}
{/key}

<style>
	.kk-flash {
		translate: -50% -50%;
		opacity: 0;
	}
	@media (prefers-reduced-motion: no-preference) {
		.kk-flash {
			animation: kk-flash 0.75s ease-out both;
		}
	}
	/* the badge swells a little and fades, as a video player's pause / play picture does */
	@keyframes kk-flash {
		0% {
			opacity: 0.95;
			scale: 0.8;
		}
		100% {
			opacity: 0;
			scale: 1.6;
		}
	}
</style>
