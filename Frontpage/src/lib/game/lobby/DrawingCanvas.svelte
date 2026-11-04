<script>
	import { onMount, untrack } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { CANVAS_H, CANVAS_W, DRAW_SCALE as S, circleDots, createSender, pad, pictureOf, sprayDots, strokePath } from '../drawing.svelte.js';

	/** The painter's canvas (298 x 253, white). Every finished stroke sends the picture to the guessers as a JPEG. */
	let { store, clearToken = 0, onpicture } = $props();
	const W = CANVAS_W;
	const H = CANVAS_H;
	let canvas = $state();
	let ctx;
	let base = null; // the picture as it was when the stroke began
	let points = [];
	let drawing = false;
	const sender = createSender((data) => store.sendCanvas(data));

	function whiten() {
		ctx.fillStyle = '#ffffff';
		ctx.fillRect(0, 0, W, H);
	}
	onMount(() => {
		ctx = canvas.getContext('2d', { willReadFrequently: true });
		whiten();
	});
	let lastClear = untrack(() => clearToken);
	// "새 캔버스": a blank picture, sent on at once so the guessers see it too
	$effect(() => {
		if (clearToken === lastClear || !ctx) return;
		lastClear = clearToken;
		whiten();
		finish();
	});

	const at = (e) => {
		const r = canvas.getBoundingClientRect();
		return [((e.clientX - r.left) / r.width) * W, ((e.clientY - r.top) / r.height) * H, e.pressure || 0.5];
	};
	const ink = () => (pad.tool === 'eraser' ? '#ffffff' : pad.color);

	function down(e) {
		if (e.button !== 0) return;
		canvas.setPointerCapture(e.pointerId);
		drawing = true;
		base = ctx.getImageData(0, 0, W, H);
		points = [at(e)];
		paint(true);
	}
	function move(e) {
		if (!drawing) return;
		for (const ev of e.getCoalescedEvents?.() ?? [e]) points.push(at(ev));
		paint(false);
	}
	function paint(first) {
		const last = points.at(-1);
		if (pad.tool === 'spray' || pad.tool === 'circle') {
			// these two lay dots straight onto the picture as the pointer goes
			ctx.fillStyle = pad.color;
			for (const d of (pad.tool === 'spray' ? sprayDots : circleDots)(last[0], last[1], pad.width * S)) {
				ctx.beginPath();
				ctx.arc(d.x, d.y, d.size, 0, Math.PI * 2);
				ctx.fill();
			}
			return;
		}
		ctx.putImageData(base, 0, 0);
		ctx.fillStyle = ink();
		if (first && points.length === 1) {
			ctx.beginPath();
			ctx.arc(last[0], last[1], Math.max(1, (pad.width * S) / 2), 0, Math.PI * 2);
			ctx.fill();
		} else ctx.fill(strokePath(points, pad.width * S));
	}
	function up() {
		if (!drawing) return;
		drawing = false;
		finish();
	}
	function finish() {
		const picture = pictureOf(canvas);
		if (!picture) {
			// too much for one picture: start again (legacy; its notice text was never defined, so there is none)
			whiten();
			return;
		}
		onpicture?.(picture);
		sender.push(picture);
	}
</script>

<canvas
	bind:this={canvas}
	id="canvas"
	class="canvas"
	width={W}
	height={H}
	style="width:100%;height:100%;display:block;touch-action:none;cursor:crosshair"
	aria-label={t('play.drawing.canvas')}
	onpointerdown={down}
	onpointermove={move}
	onpointerup={up}
	onpointercancel={up}
></canvas>
