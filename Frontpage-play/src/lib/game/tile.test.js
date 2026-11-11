import { describe, expect, it } from 'vitest';
import { COLUMNS, TILE_COUNT, drawsPossible, gaugeView, ownedTiles, planSweep, sweepTime } from './tile.js';

const seeded = (seed) => () => ((seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648);

describe('planSweep', () => {
	it('is a 5x5 board', () => {
		expect([TILE_COUNT, COLUMNS]).toEqual([25, 5]);
	});
	it('always ends on the tile the server named, and nowhere else', () => {
		for (let seed = 1; seed <= 60; seed++) {
			const final = seed % TILE_COUNT;
			const { path, delays } = planSweep(final, { random: seeded(seed) });
			expect(path.at(-1)).toBe(final);
			expect(delays).toHaveLength(path.length);
			expect(path.every((c) => Number.isInteger(c) && c >= 0 && c < TILE_COUNT)).toBe(true);
		}
	});
	it('never stays on a tile: each hop moves', () => {
		for (let seed = 1; seed <= 60; seed++) {
			const { path } = planSweep(seed % TILE_COUNT, { random: seeded(seed * 7) });
			for (let i = 1; i < path.length; i++) expect(path[i]).not.toBe(path[i - 1]);
		}
	});
	it('goes fast and then suddenly slow, with the last stop a surprise', () => {
		const { path, delays } = planSweep(12, { fast: 20, interval: 50, slow: [200, 350, 600], random: seeded(9) });
		expect(delays.slice(0, 20)).toEqual(Array(20).fill(50));
		expect(delays.slice(20, 23)).toEqual([200, 350, 600]);
		expect(delays.at(-1)).toBe(0);
		expect(path.slice(21, -1)).not.toContain(12); // the slow hops before the end are never the final tile
	});
	it('takes as long as its delays add up to', () => {
		expect(sweepTime(planSweep(3, { fast: 10, interval: 100, slow: [300, 500], random: seeded(2) }))).toBe(10 * 100 + 300 + 500);
	});
	it('still ends on the tile with no slow part, and with a single hop', () => {
		for (let seed = 1; seed <= 30; seed++) {
			const plan = planSweep(seed % TILE_COUNT, { fast: 5, slow: [], random: seeded(seed) });
			expect(plan.path.at(-1)).toBe(seed % TILE_COUNT);
		}
		expect(planSweep(4, { fast: 0, slow: [300], random: seeded(1) }).path.at(-1)).toBe(4);
	});
	it('cannot loop for ever, even on a random source that always says the same', () => {
		for (const value of [0, 0.5, 0.9999999]) {
			const { path } = planSweep(7, { random: () => value });
			expect(path.at(-1)).toBe(7);
			for (let i = 1; i < path.length; i++) expect(path[i]).not.toBe(path[i - 1]);
		}
	});
});

describe('the rest', () => {
	it('counts the draws the tokens pay for', () => {
		expect(drawsPossible(7, 2)).toBe(3);
		expect(drawsPossible(1, 2)).toBe(0);
		expect(drawsPossible(5, 0)).toBe(0);
	});
	it('marks only the unique item tiles the box holds', () => {
		const cells = [
			{ reward: { type: 'item', name: 'leaf', q: 1 }, unique: true },
			{ reward: { type: 'item', name: 'wordToken', q: 1 }, unique: false },
			{ reward: { type: 'ping', q: 5 }, unique: true },
			{ reward: { type: 'item', name: 'fedora', q: 1 }, unique: true }
		];
		const owned = ownedTiles(cells, (id) => id === 'leaf' || id === 'wordToken');
		expect([...owned]).toEqual([0]);
	});
	it('shows the gauge as a share of its last step and which steps are reached', () => {
		const view = gaugeView({ points: 6, steps: [{ points: 5, rewards: [] }, { points: 10, rewards: [] }] });
		expect(view).toMatchObject({ points: 6, last: 10, ratio: 0.6 });
		expect(view.steps.map((s) => [s.reached, s.at])).toEqual([[true, 0.5], [false, 1]]);
		expect(gaugeView({ points: 99, steps: [{ points: 10, rewards: [] }] }).ratio).toBe(1);
		expect(gaugeView(undefined)).toBeNull();
	});
});
