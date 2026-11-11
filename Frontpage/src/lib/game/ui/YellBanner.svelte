<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { banner, nextYell, scrollDuration } from './yell.svelte.js';

	let host = $state();
	let track = $state();
	let line = $state();
	let reduced = $state(false);
	let travel = $state({ from: 0, to: 0, seconds: 8 });

	// Like <ToastHost/>: a popover sits in the top layer, above modal dialogs, so an announcement is never hidden behind one.
	function promote() {
		if (!host?.showPopover) return;
		try {
			if (host.matches(':popover-open')) host.hidePopover();
			host.showPopover();
		} catch {
			/* no popover support: the z-index below still covers everything that isn't a modal */
		}
	}
	onMount(() => {
		const q = matchMedia('(prefers-reduced-motion: reduce)');
		reduced = q.matches;
		const on = () => (reduced = q.matches);
		q.addEventListener('change', on);
		// A dialog that opens while an announcement is up would be drawn over it (and its backdrop would dim it): come back in front.
		const seen = new WeakSet();
		const mo = new MutationObserver((records) => {
			if (!banner.current) return;
			if (records.some((r) => r.target instanceof HTMLDialogElement && r.target.open && !seen.has(r.target) && seen.add(r.target))) promote();
			for (const r of records) if (r.target instanceof HTMLDialogElement && !r.target.open) seen.delete(r.target);
		});
		mo.observe(document.body, { subtree: true, attributes: true, attributeFilter: ['open'] });
		return () => {
			q.removeEventListener('change', on);
			mo.disconnect();
		};
	});

	// Measured once the line is in the page: it enters at the right edge and leaves past the left one, at a constant speed.
	$effect(() => {
		if (!banner.current) return;
		promote();
		const id = banner.current.id;
		queueMicrotask(() => {
			if (banner.current?.id !== id || !track || !line) return;
			const viewport = track.clientWidth; // the text enters at the right edge of its own lane, not of the page
			const width = line.scrollWidth;
			travel = { from: viewport, to: -width, seconds: scrollDuration(width, viewport) };
		});
	});

	// Reduced motion: no scrolling; the text stays put (wrapped) for a time that grows with its length, then goes.
	$effect(() => {
		if (!banner.current || !reduced) return;
		const timer = setTimeout(nextYell, Math.min(15000, 4000 + banner.current.message.length * 120));
		return () => clearTimeout(timer);
	});
</script>

<div bind:this={host} popover="manual" class="pointer-events-none fixed inset-x-0 top-4 bottom-auto z-[100] m-0 h-auto max-h-none w-auto max-w-none overflow-visible border-0 bg-transparent p-0">
	{#if banner.current}
		{#key banner.current.id}
			<div role="status" aria-live="polite" data-yell class="yell-bar pointer-events-auto relative flex min-h-11 items-center justify-center text-white">
				<div class="yell-row">
					<!-- The (!) mark of the game (warning.svg), inline so it takes the text colour -->
					<svg class="yell-mark" viewBox="0 0 310 280" fill="none" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">
						<path fill-rule="evenodd" clip-rule="evenodd" fill="currentColor" d="M134.284 132.884L167.957 126.359C174.829 125.027 177.599 120.925 176.268 114.053L161.549 0C243.84 2.94488 309.463 58.9414 309.463 127.588C309.463 161.87 293.097 192.997 266.462 215.937C263.594 218.406 262.313 222.291 263.352 225.929L275.343 267.967C277.443 275.331 270.272 281.951 263.1 279.27L192.473 252.88C190.866 252.28 189.127 252.14 187.438 252.439C176.895 254.312 165.953 255.297 154.731 255.297C69.2755 255.297 0 198.12 0 127.588C0 75.5237 37.747 30.7363 91.8855 10.8517L121.978 124.574C123.309 131.446 127.411 134.216 134.284 132.884ZM128.255 188.242C131.651 205.766 148.059 216.847 165.583 213.451C183.107 210.055 194.188 193.648 190.792 176.124C187.396 158.6 170.988 147.519 153.464 150.915C135.94 154.311 124.859 170.719 128.255 188.242Z" />
					</svg>
					{#if reduced}
						<span class="yell-text yell-static">{banner.current.message}</span>
					{:else}
						<span class="sr-only">{banner.current.message}</span>
						<span bind:this={track} class="yell-track" aria-hidden="true">
							<span
								bind:this={line}
								class="yell-text yell-run"
								style="--from:{travel.from}px;--to:{travel.to}px;animation-duration:{travel.seconds}s"
								onanimationend={nextYell}
							>{banner.current.message}</span>
						</span>
					{/if}
				</div>
				<button type="button" class="yell-close" aria-label={t('play.confirm.ok')} onclick={nextYell}>✕</button>
			</div>
		{/key}
	{/if}
</div>

<style>
	/* The bar: transparent at both ends, solid black in the middle. The scrolling text has its own opacity mask of the same colours
	   with short fades, so it is opaque for nearly the whole lane and only eases in and out at the lane's edges. */
	.yell-bar {
		--yell-mask: linear-gradient(90deg, #00000000 0, #000000ff 2rem, #000000ff calc(100% - 2rem), #00000000 100%);
		background: linear-gradient(90deg, #00000000, #000000ff, #00000000);
		animation: yell-in 0.25s ease-out;
	}
	.yell-row {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		width: min(100%, 64rem);
		padding: 0 3rem;
	}
	.yell-mark {
		flex: none;
		width: 1.75rem;
		height: auto;
		aspect-ratio: 310 / 280;
		filter: drop-shadow(0 0 3px #000);
	}
	.yell-track {
		position: relative;
		flex: 1;
		min-width: 0;
		overflow: hidden;
		height: 1.75rem;
		-webkit-mask-image: var(--yell-mask);
		mask-image: var(--yell-mask);
	}
	.yell-text {
		font-weight: 700;
		font-size: 1.0625rem;
		line-height: 1.75rem;
		white-space: nowrap;
	}
	.yell-run {
		position: absolute;
		left: 0;
		top: 0;
		transform: translateX(var(--from));
		animation: yell-scroll linear forwards;
	}
	.yell-static {
		white-space: normal;
		padding: 0.5rem 0;
	}
	.yell-close {
		position: absolute;
		right: 0.5rem;
		top: 50%;
		translate: 0 -50%;
		width: 2rem;
		height: 2rem;
		border-radius: 9999px;
		font-size: 0.875rem;
		color: #fff;
		background: rgb(0 0 0 / 0.55);
	}
	.yell-close:hover,
	.yell-close:focus-visible {
		background: rgb(0 0 0 / 0.85);
		outline: 2px solid #fff;
	}
	@keyframes yell-scroll {
		from {
			transform: translateX(var(--from));
		}
		to {
			transform: translateX(var(--to));
		}
	}
	@keyframes yell-in {
		from {
			transform: translateY(-100%);
		}
		to {
			transform: translateY(0);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.yell-bar {
			animation: none;
		}
	}
</style>
