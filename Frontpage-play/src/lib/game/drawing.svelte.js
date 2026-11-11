/**
 * 그림퀴즈 drawing: the painter's tool state and the stroke maths, with no DOM except the canvas that is passed in.
 * The strokes are drawn with perfect-freehand (variable-width, smoothed outlines) on a plain 2D canvas, instead of Fabric.js
 * the legacy client used: nothing here needs an object model, only pixels that are sent as a JPEG.
 */
import { getStroke } from 'perfect-freehand';

/** The picture's size, as sent: the server takes any JPEG up to 4096 px a side and 256 KB. It is shown 600 px wide on the page. */
export const CANVAS_W = 960;
export const CANVAS_H = 480;
/** A phone paints on a taller sheet (4:3): a 2:1 strip is too little to draw on at that width. Everyone shows a picture at its own shape (contain). */
export const CANVAS_H_TALL = 720;
/** Picture pixels per page pixel, so a brush of N page pixels is N * DRAW_SCALE here. */
export const DRAW_SCALE = 1.6;
/** The most a picture may weigh as a data URL (the server's cap is 256 KB of JPEG; the legacy client kept to 160000 characters). */
export const MAX_IMAGE = 340000;
/** One picture at most every 2.2 s, the newest one waiting its turn (legacy). */
export const SEND_COOLDOWN = 2200;

/** The palette: a column of greys and five hues, dark to light. */
const HUES = [0, 38, 135, 210, 280];
const LIGHTS = [20, 34, 48, 62, 76, 88];
const GREYS = ['#000000', '#2b2b2b', '#555555', '#808080', '#aaaaaa', '#d4d4d4'];
export const HUE_NAMES = ['red', 'orange', 'green', 'blue', 'purple'];
export const PALETTE = LIGHTS.flatMap((l, row) => [GREYS[row], ...HUES.map((h) => `hsl(${h} 80% ${l}%)`)]);
/** Locale keys that name palette entry `i` (grey ones have a name of their own; the rest are a shade and a hue). */
export function colorKeys(i) {
	const row = Math.floor(i / 6);
	const col = i % 6;
	return col === 0 ? [`play.drawing.grey.${row}`] : [`play.drawing.shade.${row}`, `play.drawing.hue.${HUE_NAMES[col - 1]}`];
}
/** Brush sizes of the five presets. */
export const SIZES = [3, 8, 14, 22, 32];
export const TOOLS = ['pencil', 'spray', 'circle', 'eraser'];

export const pad = $state({ tool: 'pencil', color: '#000000', width: 8 });

/** True when a pen really reported pressure (it varies along the stroke). The canvas hands every other pointer a flat 0.5. */
export const hasPressure = (points) => points.some((p) => p[2] !== undefined && p[2] !== 0.5);

/** The outline of a stroke through `points` ([x, y, pressure]), as a Path2D polygon to fill. */
export function strokePath(points, width) {
	const outline = getStroke(points, { size: Math.max(1, width), thinning: hasPressure(points) ? 0.5 : 0, smoothing: 0.55, streamline: 0.5, simulatePressure: false }); // the width follows real pressure only; it is never faked from speed
	const path = new Path2D();
	if (outline.length < 2) return path;
	path.moveTo(outline[0][0], outline[0][1]);
	for (let i = 1; i < outline.length; i++) {
		const [x0, y0] = outline[i];
		const [x1, y1] = outline[(i + 1) % outline.length];
		path.quadraticCurveTo(x0, y0, (x0 + x1) / 2, (y0 + y1) / 2);
	}
	path.closePath();
	return path;
}

/** The picture as a JPEG data URL under the limit, or null when even the lowest quality is too big. */
export function pictureOf(canvas) {
	if (canvas.width !== CANVAS_W) {
		const small = document.createElement('canvas');
		const height = Math.round((canvas.height * CANVAS_W) / canvas.width); // the shape of the sheet is kept
		small.width = CANVAS_W;
		small.height = height;
		small.getContext('2d').drawImage(canvas, 0, 0, CANVAS_W, height);
		canvas = small;
	}
	for (const quality of [0.4, 0.1]) {
		const url = canvas.toDataURL('image/jpeg', quality);
		if (url.length <= MAX_IMAGE) return url;
	}
	return null;
}

/** Spray: a handful of dots scattered around a point, as many as the brush is wide. */
export function sprayDots(x, y, width, random = Math.random) {
	const count = Math.max(4, Math.round(width * 1.2));
	const radius = Math.max(2, width);
	return Array.from({ length: count }, () => {
		const a = random() * Math.PI * 2;
		const r = Math.sqrt(random()) * radius;
		return { x: x + Math.cos(a) * r, y: y + Math.sin(a) * r, size: 0.5 + random() * 1.5 };
	});
}

/** Circle brush: a few small discs of random size around a point. */
export function circleDots(x, y, width, random = Math.random) {
	return Array.from({ length: 3 }, () => ({ x: x + (random() - 0.5) * width * 1.5, y: y + (random() - 0.5) * width * 1.5, size: 1 + random() * (width / 2) }));
}

/**
 * The sender of the pictures: sends at once when it has been quiet for the cooldown, otherwise keeps only the newest picture
 * and sends it when the cooldown ends (legacy `sendCooldownClear` / `pendingDrawingData`).
 */
export function createSender(send, { cooldown = SEND_COOLDOWN, setTimer = setTimeout } = {}) {
	let clear = true;
	let pending = null;
	function arm() {
		setTimer(() => {
			if (pending) {
				send(pending);
				pending = null;
				arm();
			} else clear = true;
		}, cooldown);
	}
	return {
		push(data) {
			if (clear) {
				send(data);
				clear = false;
				arm();
			} else pending = data;
		}
	};
}
