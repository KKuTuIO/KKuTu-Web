<script>
	/**
	 * The default Moremi parts redrawn as vectors, traced from the original `moremi.fla` (same 120x120 coordinates as
	 * the PNG layers, so they drop into the stack unchanged). `part` is 'body', 'eye' or 'mouth'.
	 *
	 * Body: the torso is separate from the two legs, and the legs are a shade darker with the torso's shadow falling
	 * on them. `.breathe` / `.blink` are driven by `Moremi.svelte` (only present when the avatar is animated).
	 */
	let { part, class: cls = '' } = $props();
	const id = $props.id();
</script>

<svg viewBox="0 0 120 120" class="absolute inset-0 size-full {cls}" aria-hidden="true" focusable="false">
	{#if part === 'body'}
		<defs>
			<radialGradient id="{id}-ground" cx="60" cy="118.6" r="40" gradientUnits="userSpaceOnUse" gradientTransform="translate(60 118.6) scale(1 .07) translate(-60 -118.6)">
				<stop offset="0" stop-color="#000" stop-opacity=".22" />
				<stop offset=".6" stop-color="#000" stop-opacity=".1" />
				<stop offset="1" stop-color="#000" stop-opacity="0" />
			</radialGradient>
			<linearGradient id="{id}-leg" x1="0" y1="108" x2="0" y2="120" gradientUnits="userSpaceOnUse">
				<stop offset="0" stop-color="#4a3200" stop-opacity=".5" />
				<stop offset=".55" stop-color="#4a3200" stop-opacity=".16" />
				<stop offset="1" stop-color="#4a3200" stop-opacity="0" />
			</linearGradient>
			<path id="{id}-l" d="M22 106H32V110Q32 111.45 31.85 112.75Q31.5 115.2 30.55 117.1Q29.1 120 27 120Q24.95 120 23.45 117.1Q22.5 115.2 22.2 112.75Q22 111.45 22 110Z" />
			<path id="{id}-r" d="M88 106H98V110Q98 111.45 97.85 112.75Q97.5 115.2 96.55 117.1Q95.1 120 93 120Q90.95 120 89.45 117.1Q88.5 115.2 88.2 112.75Q88 111.45 88 110Z" />
		</defs>
		<ellipse cx="60" cy="118.6" rx="40" ry="2.8" fill="url(#{id}-ground)" />
		<g fill="#E4BC52">
			<use href="#{id}-l" />
			<use href="#{id}-r" />
		</g>
		<g fill="url(#{id}-leg)">
			<use href="#{id}-l" />
			<use href="#{id}-r" />
		</g>
		<path class="breathe-body" fill="#F3D368" d="M95.35 34.65Q103.95 43.22 107.5 53.9Q110 61.43 110 70V110H10V70Q10 61.77 12.3 54.5Q15.81 43.46 24.65 34.65Q39.3 20 60 20Q80.7 20 95.35 34.65Z" />
	{:else if part === 'eye'}
		<g class="blink-open" stroke="#000" stroke-width="3" stroke-linecap="round">
			<g fill="#fff">
				<circle cx="29.9" cy="54.9" r="4.9" />
				<circle cx="89.9" cy="54.9" r="4.9" />
			</g>
			<path d="M30 50V45M90 50V45" fill="none" />
		</g>
		<path class="blink-shut" d="M25.2 54.6Q30 58.2 34.8 54.6M85.2 54.6Q90 58.2 94.8 54.6" fill="none" stroke="#000" stroke-width="3" stroke-linecap="round" />
	{:else if part === 'mouth'}
		<path d="M52 70Q60 76 68 70" fill="none" stroke="#000" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
	{/if}
</svg>

<style>
	/* Resting state: eyes open. The animated state below is only switched on by the parent's `.alive` class. */
	.blink-shut {
		opacity: 0;
	}
	.blink-open,
	.blink-shut {
		transform-origin: 60px 55px;
	}
	.breathe-body {
		transform-origin: 60px 110px;
	}
	:global(.alive) .breathe-body {
		animation: breathe-body var(--breath-dur, 3.8s) ease-in-out var(--alive-delay, 0s) infinite;
	}
	:global(.alive) .blink-open {
		animation: blink-open var(--blink-dur, 5s) linear var(--alive-delay, 0s) infinite;
	}
	:global(.alive) .blink-shut {
		animation: blink-shut var(--blink-dur, 5s) linear var(--alive-delay, 0s) infinite;
	}
	@keyframes breathe-body {
		0%,
		100% {
			transform: translateY(0) scaleY(1);
		}
		50% {
			transform: translateY(1.1px) scaleY(0.972);
		}
	}
	@keyframes blink-open {
		0%,
		93% {
			transform: scaleY(1);
			opacity: 1;
		}
		95.5%,
		97% {
			transform: scaleY(0.1);
			opacity: 0;
		}
		100% {
			transform: scaleY(1);
			opacity: 1;
		}
	}
	@keyframes blink-shut {
		0%,
		93% {
			opacity: 0;
		}
		95.5%,
		97% {
			opacity: 1;
		}
		100% {
			opacity: 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		:global(.alive) .breathe-body,
		:global(.alive) .blink-open,
		:global(.alive) .blink-shut {
			animation: none;
		}
	}
</style>
