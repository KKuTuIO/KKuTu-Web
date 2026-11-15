<script>
	/**
	 * A line chart of the typing speed over the time of a text ("이번 경기 성적"): `points` are `{t, v}` (seconds from the start, strokes a minute). `goal`
	 * draws a dashed line at the goal's speed. It is drawn in its own box and scales with the width it is given, so a screen, a dialog and the game's result can all use it.
	 * @type {{points:{t:number, v:number}[], goal?:number|null, height?:number, label?:string}}
	 */
	let { points, goal = null, height = 140, label = '' } = $props();

	const W = 300;
	const PAD = { l: 34, r: 8, t: 8, b: 20 };
	const innerW = W - PAD.l - PAD.r;
	const innerH = $derived(height - PAD.t - PAD.b);
	const top = $derived(niceMax(Math.max(100, goal ?? 0, ...points.map((p) => p.v))));
	const span = $derived(Math.max(5, points.at(-1)?.t ?? 0));
	const x = (t) => PAD.l + (t / span) * innerW;
	const y = (v) => PAD.t + innerH - (v / top) * innerH;
	const ticks = $derived([0, 0.5, 1].map((f) => Math.round(top * f)));
	const line = $derived(points.map((p, i) => `${i ? 'L' : 'M'}${x(p.t).toFixed(1)} ${y(p.v).toFixed(1)}`).join(' '));
	const area = $derived(points.length > 1 ? `${line} L${x(points.at(-1).t).toFixed(1)} ${y(0)} L${x(points[0].t).toFixed(1)} ${y(0)} Z` : '');

	/** the next round number above `v` (the chart's top) */
	function niceMax(v) {
		const steps = [100, 200, 400, 600, 800, 1000, 1200, 1500, 2000];
		return steps.find((s) => s >= v) ?? Math.ceil(v / 500) * 500;
	}
</script>

<svg viewBox="0 0 {W} {height}" class="chart" role="img" aria-label={label || undefined} preserveAspectRatio="xMidYMid meet">
	{#each ticks as v (v)}
		<line x1={PAD.l} x2={W - PAD.r} y1={y(v)} y2={y(v)} class="grid" />
		<text x={PAD.l - 5} y={y(v) + 3} class="axis" text-anchor="end">{v}</text>
	{/each}
	<text x={PAD.l} y={height - 5} class="axis">0</text>
	<text x={W - PAD.r} y={height - 5} class="axis" text-anchor="end">{Math.round(span)}s</text>
	{#if goal}
		<line x1={PAD.l} x2={W - PAD.r} y1={y(goal)} y2={y(goal)} class="goal" />
	{/if}
	{#if area}<path d={area} class="area" />{/if}
	{#if points.length > 1}<path d={line} class="line" />{/if}
</svg>

<style>
	.chart {
		display: block;
		width: 100%;
		height: auto;
		color: var(--kk-text);
	}
	.grid {
		stroke: var(--kk-line);
		stroke-width: 1;
	}
	.axis {
		fill: var(--kk-muted);
		font-size: 9px;
		font-weight: 700;
	}
	.goal {
		stroke: var(--kk-accent);
		stroke-width: 1.5;
		stroke-dasharray: 4 3;
	}
	.line {
		fill: none;
		stroke: var(--kk-accent);
		stroke-width: 2;
		stroke-linejoin: round;
		stroke-linecap: round;
	}
	.area {
		fill: var(--kk-accent);
		opacity: 0.14;
	}
</style>
