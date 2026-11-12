/**
 * The squares of the game board's tile (the CDN's img/kkutu/game_tile.svg), as polygons of one 200x200 period of the
 * infinite lattice: the light squares of a tilted checker. Repeating them at every multiple of 200 reproduces the board
 * background, so a loading screen can fill the squares in one at a time.
 */
export const TILE = 200;
export const TILE_SQUARES = [
	[[8, -38], [46, -31], [38, 8], [0, 0]],
	[[85, -23], [123, -15], [116, 23], [77, 16]],
	[[161, -7], [200, 0], [192, 38], [154, 31]],
	[[38, 8], [77, 16], [69, 54], [31, 46]],
	[[116, 23], [154, 31], [146, 69], [108, 62]],
	[[192, 39], [231, 46], [223, 84], [185, 77]],
	[[69, 54], [107, 61], [100, 100], [62, 92]],
	[[146, 70], [184, 77], [177, 115], [139, 108]],
	[[23, 85], [62, 92], [54, 131], [16, 123]],
	[[100, 100], [138, 108], [131, 146], [92, 139]],
	[[177, 116], [215, 123], [208, 161], [169, 154]],
	[[54, 131], [92, 139], [85, 177], [47, 170]],
	[[131, 147], [169, 154], [162, 192], [123, 185]]
];

/** Moving a light square along one of its edges gives the dark one next to it. */
const DARK_SHIFT = [38, 7];

/**
 * Every square (both tones) that touches a `width` x `height` area, with its centre and a delay (ms) that grows
 * straight from the top-left corner to the bottom-right one, `spread` ms for the whole screen.
 */
export function tileCells(width, height, { spread = 500, size = 2 } = {}) {
	const cells = [];
	const period = TILE * size;
	const diag = Math.max(1, width + height);
	for (let i = -1; i * period < width + period; i++) {
		for (let j = -1; j * period < height + period; j++) {
			TILE_SQUARES.forEach((sq, k) => {
				for (const dark of [false, true]) {
					const base = sq.map(([x, y]) => [(x + (dark ? DARK_SHIFT[0] : 0)) * size + i * period, (y + (dark ? DARK_SHIFT[1] : 0)) * size + j * period]);
					const cx = base.reduce((n, p) => n + p[0], 0) / 4;
					const cy = base.reduce((n, p) => n + p[1], 0) / 4;
					// a little larger than its place, so neighbours overlap and no gap shows between them
					const pts = base.map(([x, y]) => [x + (x - cx) * 0.04, y + (y - cy) * 0.04]);
					if (cx < -period || cy < -period || cx > width + period || cy > height + period) continue;
					cells.push({ key: `${i}:${j}:${k}:${dark ? 'd' : 'l'}`, dark, points: pts.map((p) => p.map((n) => n.toFixed(1)).join(',')).join(' '), cx, cy, delay: Math.round(Math.max(0, ((cx + cy) / diag) * spread)) });
				}
			});
		}
	}
	return cells;
}

/** The tile as one repeating cell of `200 * size` px: every square (both tones) that touches it, for an SVG pattern. */
export function patternPolygons(size = 2) {
	const P = TILE * size;
	const out = [];
	for (const dark of [false, true]) {
		for (const sq of TILE_SQUARES) {
			for (const dx of [-P, 0, P]) {
				for (const dy of [-P, 0, P]) {
					const pts = sq.map(([x, y]) => [(x + (dark ? DARK_SHIFT[0] : 0)) * size + dx, (y + (dark ? DARK_SHIFT[1] : 0)) * size + dy]);
					const xs = pts.map((p) => p[0]);
					const ys = pts.map((p) => p[1]);
					if (Math.max(...xs) <= 0 || Math.min(...xs) >= P || Math.max(...ys) <= 0 || Math.min(...ys) >= P) continue;
					out.push({ dark, points: pts.map((p) => p.join(',')).join(' ') });
				}
			}
		}
	}
	return out;
}
