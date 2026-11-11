import { describe, expect, it } from 'vitest';
import { EXP } from './level.js';
import { crossedLevels, entryFor, hasGift, POPUP_CAP, splitLevels, sumGifts } from './levelUp.js';

// a point inside a level; the last level has no upper bound, so it is just past its start
const at = (level, frac = 0.5) => (EXP[level - 2] ?? 0) + (Number.isFinite(EXP[level - 1]) ? (EXP[level - 1] - (EXP[level - 2] ?? 0)) * frac : 1);

describe('crossedLevels', () => {
	it('is empty when the level does not change', () => expect(crossedLevels(at(10, 0.1), at(10, 0.9))).toEqual([]));
	it('lists every level passed, lowest first', () => expect(crossedLevels(at(9), at(12))).toEqual([10, 11, 12]));
	it('handles the first level and a single step', () => {
		expect(crossedLevels(0, at(2))).toEqual([2]);
		expect(crossedLevels(at(719), at(720))).toEqual([720]);
	});
});

describe('splitLevels', () => {
	it('keeps up to the cap and folds the rest', () => {
		expect(POPUP_CAP).toBe(3);
		expect(splitLevels([2, 3])).toEqual({ shown: [2, 3], folded: [] });
		expect(splitLevels([2, 3, 4, 5, 6])).toEqual({ shown: [2, 3, 4], folded: [5, 6] });
	});
	it('never needs more than cap + 1 key presses for a jump of any size', () => {
		const levels = Array.from({ length: 719 }, (_, i) => i + 2);
		const { shown, folded } = splitLevels(levels);
		expect(shown.length + (folded.length ? 1 : 0)).toBe(POPUP_CAP + 1);
	});
});

describe('sumGifts / entryFor', () => {
	const gifts = { 5: { ping: 80, items: [{ key: 'a', q: 1 }], mail: [] }, 6: { ping: 60, items: [{ key: 'a', q: 2 }, { key: 'b', q: 1 }], mail: [{ key: 'm', q: 1 }] }, 7: null };
	const giftOf = (l) => gifts[l];
	it('adds pings and merges items', () => {
		expect(sumGifts([gifts[5], gifts[6], gifts[7]])).toEqual({ ping: 140, items: [{ key: 'a', q: 3 }, { key: 'b', q: 1 }], mail: [{ key: 'm', q: 1 }] });
	});
	it('one level is a level entry, several are a summary', () => {
		expect(entryFor([5], giftOf)).toMatchObject({ kind: 'level', level: 5 });
		expect(entryFor([5, 6, 7], giftOf)).toMatchObject({ kind: 'summary', from: 5, to: 7, count: 3, gift: { ping: 140 } });
	});
	it('hasGift ignores empty gifts', () => {
		expect(hasGift(null)).toBe(false);
		expect(hasGift({ ping: 0, items: [], mail: [] })).toBe(false);
		expect(hasGift({ ping: 10, items: [], mail: [] })).toBe(true);
	});
});
