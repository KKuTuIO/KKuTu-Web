<script>
	import { ms } from '../motion.js';

	/**
	 * A score as big plain numerals, the way the game screen draws one: no boxes. The zeros that only pad it are faded, the digits of the
	 * score are in `color` with a hard edge under them (the same edge the client's buttons and cards have). It counts up from zero;
	 * `skipped` (any value but 0) shows the final number at once, and with reduced motion there is no counting at all. Screen readers get
	 * the final number, not the count.
	 * @type {{value:number, color:string, size?:'lg'|'sm', minDigits?:number, skipped?:number, label?:string}}
	 */
	let { value = 0, color, size = 'lg', minDigits = 6, skipped = 0, label = '' } = $props();
	const target = $derived(Math.max(0, Math.round(Number(value) || 0)));
	let shown = $state(0);

	$effect(() => {
		const to = target;
		const duration = ms(1200);
		if (skipped || !duration) {
			shown = to;
			return;
		}
		shown = 0;
		const start = performance.now();
		let raf;
		const tick = (now) => {
			const p = Math.min(1, (now - start) / duration);
			shown = Math.round(to * (1 - Math.pow(1 - p, 3)));
			if (p < 1) raf = requestAnimationFrame(tick);
		};
		raf = requestAnimationFrame(tick);
		return () => cancelAnimationFrame(raf);
	});

	// "001,524": the padding zeros (with the comma that may sit among them) apart from the digits of the score
	const parts = $derived.by(() => {
		const digits = String(shown).padStart(Math.max(minDigits, String(target).length), '0');
		const full = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
		// a score of nothing still shows its last zero lit, so it can be read
		const first = Math.max(0, digits.search(/[1-9]/) === -1 ? digits.length - 1 : digits.search(/[1-9]/));
		let n = 0;
		for (let i = 0; i < full.length; i++) {
			if (full[i] === ',') continue;
			if (n === first) return { pad: full.slice(0, i), lit: full.slice(i) };
			n++;
		}
		return { pad: '', lit: full };
	});
</script>

<div class="num {size}" style:--c={color} role="img" aria-label={`${label} ${target.toLocaleString('en-US')}`.trim()} data-flaps={target}>
	<span class="pad" aria-hidden="true">{parts.pad}</span><span class="lit" aria-hidden="true">{parts.lit}</span>
</div>

<style>
	.num {
		font-weight: 800;
		font-variant-numeric: tabular-nums;
		line-height: 1;
		white-space: nowrap;
	}
	.lg {
		font-size: 150px;
	}
	.sm {
		font-size: 72px;
	}
	.pad {
		opacity: 0.2;
	}
	.lit {
		color: var(--c);
		text-shadow: 0 6px 0 color-mix(in srgb, var(--c) 40%, black);
	}
	.sm .lit {
		text-shadow: 0 3px 0 color-mix(in srgb, var(--c) 40%, black);
	}
	@media (max-width: 900px) {
		.lg {
			font-size: 84px;
		}
		.sm {
			font-size: 44px;
		}
	}
</style>
