<script>
	/**
	 * Gauge-style slider: end labels, tick marks with numbers above the track, a filled track and a value bubble under the
	 * thumb. Pointer drag with capture, full keyboard support, role="slider".
	 *
	 * - Continuous/stepped:  min / max / step.
	 * - Discrete `stops` (e.g. [5,10,30,60]): the thumb snaps between the stops, spaced evenly; `value` is the stop.
	 * - `tickStep`: put (labelled) ticks only at multiples of this step, e.g. 25 on a 0-100 gauge.
	 * - `ticks`: show tick marks (default on). `tickLabel(v)` formats the numbers above them; `format(v)` the bubble and ends.
	 */
	let {
		value = $bindable(0),
		min = 0,
		max = 100,
		step = 1,
		stops = null,
		label = '',
		format = (v) => String(v),
		tickLabel = (v) => String(v),
		ticks = true,
		tickStep = null,
		disabled = false,
		onchange
	} = $props();
	let track = $state();
	let dragging = $state(false);

	const discrete = $derived(Array.isArray(stops) && stops.length > 1);
	const lo = $derived(discrete ? stops[0] : min);
	const hi = $derived(discrete ? stops[stops.length - 1] : max);
	const index = $derived(discrete ? Math.max(0, stops.indexOf(value)) : 0);
	const pctOf = (v) => (discrete ? (Math.max(0, stops.indexOf(v)) / (stops.length - 1)) * 100 : hi > lo ? ((v - lo) / (hi - lo)) * 100 : 0);
	const pct = $derived(discrete ? (index / (stops.length - 1)) * 100 : pctOf(value));

	// Tick positions. Too many labels would collide, so thin them out (always keeping both ends).
	const marks = $derived.by(() => {
		if (!ticks) return [];
		if (tickStep && !discrete) {
			const out = [];
			for (let v = min; v <= max; v += tickStep) out.push({ v, pct: pctOf(v), label: true });
			return out;
		}
		const values = discrete ? stops : Array.from({ length: Math.floor((max - min) / step) + 1 }, (_, i) => min + i * step);
		const every = Math.max(1, Math.ceil(values.length / 8));
		return values.map((v, i) => ({ v, pct: pctOf(v), label: i % every === 0 || i === values.length - 1 }));
	});

	const clamp = (v) => Math.min(hi, Math.max(lo, v));
	const snap = (v) => clamp(Math.round((v - lo) / step) * step + lo);
	function set(v) {
		const next = discrete ? stops.reduce((a, b) => (Math.abs(b - v) < Math.abs(a - v) ? b : a)) : snap(v);
		if (next === value) return;
		value = next;
		onchange?.(next);
	}
	function fromPointer(e) {
		const r = track.getBoundingClientRect();
		const f = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
		if (discrete) set(stops[Math.round(f * (stops.length - 1))]);
		else set(lo + f * (hi - lo));
	}
	function key(e) {
		const dir = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1 }[e.key];
		if (e.key === 'Home') return (e.preventDefault(), set(lo));
		if (e.key === 'End') return (e.preventDefault(), set(hi));
		if (e.key === 'PageUp' || e.key === 'PageDown') {
			e.preventDefault();
			const sign = e.key === 'PageUp' ? 1 : -1;
			if (discrete) return set(stops[Math.min(stops.length - 1, Math.max(0, index + sign * 2))]);
			return set(value + sign * Math.max(step, Math.round((hi - lo) / 10 / step) * step));
		}
		if (!dir) return;
		e.preventDefault();
		if (discrete) set(stops[Math.min(stops.length - 1, Math.max(0, index + dir))]);
		else set(value + dir * step);
	}
</script>

<div class="flex items-center gap-4 pt-5 pb-8">
	<span class="min-w-10 shrink-0 text-right text-sm whitespace-nowrap font-extrabold text-(color:--kk-muted) tabular-nums" aria-hidden="true">{format(lo)}</span>

	<div
		bind:this={track}
		role="slider"
		tabindex={disabled ? -1 : 0}
		aria-label={label}
		aria-valuemin={lo}
		aria-valuemax={hi}
		aria-valuenow={value}
		aria-valuetext={format(value)}
		aria-disabled={disabled}
		onkeydown={key}
		onpointerdown={(e) => {
			if (disabled) return;
			dragging = true;
			e.currentTarget.setPointerCapture(e.pointerId);
			fromPointer(e);
		}}
		onpointermove={(e) => dragging && fromPointer(e)}
		onpointerup={() => (dragging = false)}
		onpointercancel={() => (dragging = false)}
		class="relative h-7 flex-1 cursor-pointer touch-none select-none focus-visible:outline-2 focus-visible:outline-offset-8 focus-visible:outline-(--kk-accent) {disabled ? 'opacity-50' : ''}"
	>
		<!-- ticks and their numbers -->
		{#each marks as m (m.v)}
			<span class="absolute bottom-3 w-px -translate-x-1/2 bg-(--kk-line-strong) {m.label ? 'h-3.5' : 'h-2'}" style:left="{m.pct}%" aria-hidden="true"></span>
			{#if m.label}<span class="absolute bottom-[1.6rem] -translate-x-1/2 text-[11px] font-bold text-(color:--kk-muted) tabular-nums" style:left="{m.pct}%" aria-hidden="true">{tickLabel(m.v)}</span>{/if}
		{/each}

		<span class="absolute inset-x-0 bottom-[0.6rem] h-2 translate-y-1/2 rounded-full bg-(--kk-line)"></span>
		<span class="absolute bottom-[0.6rem] left-0 h-2 translate-y-1/2 rounded-full bg-(--kk-accent)" style:width="{pct}%"></span>

		<!-- thumb and the bubble that shows the value -->
		<span class="absolute bottom-[0.6rem] size-6 -translate-x-1/2 translate-y-1/2 rounded-full border-[3px] border-(color:--kk-fill) bg-(--kk-surface) shadow-[0_1px_0_rgb(0_0_0/0.3)] transition-transform {dragging ? 'scale-125' : 'hover:scale-110'}" style:left="{pct}%"></span>
		<output
			class="kk-card pointer-events-none absolute top-[2.35rem] -translate-x-1/2 rounded-lg px-2.5 py-0.5 text-sm font-extrabold whitespace-nowrap tabular-nums"
			style:left="{pct}%"
		>{format(value)}</output>
	</div>

	<span class="min-w-10 shrink-0 text-sm whitespace-nowrap font-extrabold text-(color:--kk-muted) tabular-nums" aria-hidden="true">{format(hi)}</span>
</div>
