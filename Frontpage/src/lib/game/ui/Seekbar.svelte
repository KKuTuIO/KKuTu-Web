<script>
	/** Custom slider: pointer drag with capture, full keyboard support, role="slider". */
	let { value = $bindable(0), min = 0, max = 100, step = 1, label = '', format = (v) => String(v), disabled = false, onchange } = $props();
	let track = $state();
	let dragging = $state(false);
	const pct = $derived(max > min ? ((value - min) / (max - min)) * 100 : 0);
	const clamp = (v) => Math.min(max, Math.max(min, v));
	const snap = (v) => clamp(Math.round((v - min) / step) * step + min);

	function set(v) {
		const next = snap(v);
		if (next === value) return;
		value = next;
		onchange?.(next);
	}
	function fromPointer(e) {
		const r = track.getBoundingClientRect();
		set(min + ((e.clientX - r.left) / r.width) * (max - min));
	}
	function key(e) {
		const big = Math.max(step, Math.round((max - min) / 10 / step) * step);
		const to = { ArrowRight: value + step, ArrowUp: value + step, ArrowLeft: value - step, ArrowDown: value - step, PageUp: value + big, PageDown: value - big, Home: min, End: max }[e.key];
		if (to === undefined) return;
		e.preventDefault();
		set(to);
	}
</script>

<div class="flex items-center gap-3">
	<div
		bind:this={track}
		role="slider"
		tabindex={disabled ? -1 : 0}
		aria-label={label}
		aria-valuemin={min}
		aria-valuemax={max}
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
		class="relative h-9 flex-1 cursor-pointer touch-none select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-(--kk-accent) {disabled ? 'opacity-50' : ''}"
	>
		<span class="absolute inset-x-0 top-1/2 h-2 -translate-y-1/2 rounded-full bg-(--kk-line)"></span>
		<span class="absolute top-1/2 left-0 h-2 -translate-y-1/2 rounded-full bg-(--kk-accent)" style:width="{pct}%"></span>
		<span class="absolute top-1/2 size-6 -translate-x-1/2 -translate-y-1/2 rounded-full border-[3px] border-(color:--kk-fill) bg-(--kk-surface) shadow-[0_1px_0_rgb(0_0_0/0.3)] transition-transform {dragging ? 'scale-125' : 'hover:scale-110'}" style:left="{pct}%"></span>
	</div>
	<output class="w-14 shrink-0 text-right font-extrabold tabular-nums">{format(value)}</output>
</div>
