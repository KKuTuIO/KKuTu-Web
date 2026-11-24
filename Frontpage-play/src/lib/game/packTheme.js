/**
 * 팩 포장지와 카드의 색: 아이템 그림(RGBA)에서 대표 색상(hue)을 하나 뽑고, 그 hue 둘레의 파스텔 팔레트를 만든다.
 * 그림을 읽지 못하면(CORS, 로드 실패) 아이템 ID로 정한 hue를 쓴다. 상자마다 색이 달라야 하므로 고정 색으로 두지 않는다.
 */

const cache = new Map();

/** @param {string} s */
function hashHue(s) {
	let h = 2166136261;
	for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
	return Math.abs(h) % 360;
}

/** Hue (0~360), saturation and value (0~1) of an RGB pixel. */
export function rgbToHsv(r, g, b) {
	r /= 255;
	g /= 255;
	b /= 255;
	const max = Math.max(r, g, b);
	const min = Math.min(r, g, b);
	const d = max - min;
	let h = 0;
	if (d) {
		if (max === r) h = ((g - b) / d) % 6;
		else if (max === g) h = (b - r) / d + 2;
		else h = (r - g) / d + 4;
		h *= 60;
		if (h < 0) h += 360;
	}
	return { h, s: max ? d / max : 0, v: max };
}

/**
 * The dominant hue of RGBA pixels: transparent and grey pixels count for nothing, vivid ones count most.
 * Returns null when nothing in the picture has a colour (a black, white or grey item).
 * @param {Uint8ClampedArray} data
 */
export function dominantHue(data) {
	const BINS = 24;
	const weight = new Array(BINS).fill(0);
	const sumX = new Array(BINS).fill(0);
	const sumY = new Array(BINS).fill(0);
	for (let i = 0; i < data.length; i += 4) {
		const a = data[i + 3] / 255;
		if (a < 0.5) continue;
		const { h, s, v } = rgbToHsv(data[i], data[i + 1], data[i + 2]);
		if (s < 0.18 || v < 0.2) continue;
		const w = a * s * s * v;
		const bin = Math.floor((h / 360) * BINS) % BINS;
		weight[bin] += w;
		// the mean of angles, not of numbers: 350 and 10 average to 0, not 180
		sumX[bin] += w * Math.cos((h * Math.PI) / 180);
		sumY[bin] += w * Math.sin((h * Math.PI) / 180);
	}
	let best = -1;
	let top = 0;
	for (let b = 0; b < BINS; b++) {
		// a bin counts together with its neighbours, so one hue split across two bins is not beaten by a smaller whole one
		const w = weight[b] + (weight[(b + 1) % BINS] + weight[(b + BINS - 1) % BINS]) * 0.5;
		if (w > top) ((top = w), (best = b));
	}
	if (best < 0 || top < 0.5) return null;
	const deg = (Math.atan2(sumY[best], sumX[best]) * 180) / Math.PI;
	return (deg + 360) % 360;
}

/**
 * Pastel prism around a hue: three bright tints a third of the wheel apart-ish (the foil's colours), a deep accent for
 * lettering and glow, and a pale base.
 * @param {number} h
 */
export function paletteFromHue(h) {
	const hsl = (hue, s, l) => `hsl(${Math.round((hue + 360) % 360)} ${s}% ${l}%)`;
	return {
		hue: h,
		a: hsl(h, 92, 74),
		b: hsl(h + 58, 94, 77),
		c: hsl(h - 62, 94, 78),
		d: hsl(h + 128, 90, 80),
		accent: hsl(h, 78, 56),
		deep: hsl(h, 60, 34),
		pale: hsl(h, 80, 94)
	};
}

/**
 * The palette of an item picture, read once (and remembered). Never rejects.
 *
 * The picture is fetched for its pixels with `cache: 'no-store'`, not drawn from an `<img crossorigin>`: the host sends its CORS headers only to
 * requests that carry an `Origin`, so the copy that a plain `<img>` put in the browser's cache earlier (the same icon in the inventory or the
 * shop) has none, and a CORS-mode request that is answered from that copy fails. `no-store` neither reads that copy nor replaces it. A host
 * that does not allow this page (a local server on http) just gives the id's hue.
 */
export function themeFromImage(key, url) {
	if (cache.has(key)) return cache.get(key);
	const fallback = paletteFromHue(hashHue(String(key)));
	const job = (async () => {
		if (!url || typeof document === 'undefined') return fallback;
		try {
			const res = await fetch(url, { mode: 'cors', cache: 'no-store', credentials: 'omit' });
			if (!res.ok) return fallback;
			const bitmap = await createImageBitmap(await res.blob());
			const N = 40;
			const canvas = document.createElement('canvas');
			canvas.width = canvas.height = N;
			const ctx = canvas.getContext('2d', { willReadFrequently: true });
			ctx.drawImage(bitmap, 0, 0, N, N);
			bitmap.close?.();
			const hue = dominantHue(ctx.getImageData(0, 0, N, N).data);
			return hue == null ? fallback : paletteFromHue(hue);
		} catch {
			return fallback; // not allowed (CORS), offline, or a picture the browser cannot decode
		}
	})();
	cache.set(key, job);
	return job;
}

/** A palette at once, for the first frame (the picture's own one replaces it when it has been read). */
export const themeFor = (key) => paletteFromHue(hashHue(String(key)));

/** The CSS variables of a palette. */
export const themeStyle = (p) => `--pa:${p.a};--pb:${p.b};--pc:${p.c};--pd:${p.d};--pac:${p.accent};--pdeep:${p.deep};--ppale:${p.pale}`;
