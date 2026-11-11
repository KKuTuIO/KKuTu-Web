/**
 * The 타일 룰렛's own rules on the client: the path a highlight takes over the board, and how the chances are listed. Pure: the component
 * adds the clock, the sounds and the screen. The result of a draw is decided by the server before any of this runs; the path only decides
 * how the highlight gets to the tile the server named.
 */

export const TILE_COUNT = 25;
export const COLUMNS = 5;

/**
 * The tiles the highlight visits and how long it stays on each. It starts anywhere and hops from tile to tile (never twice on one) quickly,
 * then, all of a sudden, slowly: the last few hops come one after another more and more slowly and the last of them is `final`. The slow
 * hops never touch `final` before the end, so the stop is a surprise.
 * `delays[i]` is the time spent on `path[i]` before the move to the next one (0 for the last).
 * @param {number} final the tile the server named
 * @param {{fast?:number, interval?:number, slow?:number[], random?:()=>number}} [o] `fast` quick hops of `interval` ms, then one slow hop per entry of `slow` (ms)
 */
export function planSweep(final, { fast = 26, interval = 60, slow = [260, 400, 640], random = Math.random } = {}) {
	// A random tile that is not one of `avoid`. A few draws are enough with a real random source; if it keeps returning the same one (a
	// broken or constant source) the next tile that is allowed is taken, so this can never loop for ever.
	const pick = (...avoid) => {
		let cell = 0;
		for (let attempt = 0; attempt < 32; attempt++) {
			cell = Math.min(TILE_COUNT - 1, Math.floor(random() * TILE_COUNT));
			if (!avoid.includes(cell)) return cell;
		}
		while (avoid.includes(cell)) cell = (cell + 1) % TILE_COUNT;
		return cell;
	};
	const path = [pick()];
	const delays = [];
	for (let i = 0; i < fast; i++) {
		delays.push(interval);
		path.push(pick(path[path.length - 1]));
	}
	for (let i = 0; i < slow.length; i++) {
		delays.push(slow[i]);
		path.push(i === slow.length - 1 ? final : pick(path[path.length - 1], final));
	}
	if (!slow.length) {
		// no slow part: the last hop is simply `final`, and the hop before it moves aside if it was there already
		const n = path.length;
		path[n - 1] = final;
		if (n > 1 && path[n - 2] === final) path[n - 2] = pick(final, n > 2 ? path[n - 3] : -1);
	}
	delays.push(0);
	return { path, delays };
}

/** How long a sweep takes. */
export const sweepTime = (plan) => plan.delays.reduce((sum, ms) => sum + ms, 0);

/** How many draws of `count` the tokens pay for at `cost` each. */
export const drawsPossible = (tokens, cost) => (cost > 0 ? Math.floor(tokens / cost) : 0);

/** The tiles a player already holds the reward of and that are given once: shown as "보유 중". `owns(itemId)` says whether the box has it. */
export const ownedTiles = (cells, owns) => new Set(cells.flatMap((c, i) => (c.unique && c.reward.type === 'item' && c.reward.name && owns(c.reward.name) ? [i] : [])));

/** The gauge as 0..1 of its last step, and which steps are reached. */
export function gaugeView(gauge) {
	if (!gauge) return null;
	const last = gauge.steps[gauge.steps.length - 1]?.points ?? 1;
	return { points: gauge.points, last, ratio: Math.min(1, gauge.points / last), steps: gauge.steps.map((s) => ({ ...s, reached: gauge.points >= s.points, at: Math.min(1, s.points / last) })) };
}
