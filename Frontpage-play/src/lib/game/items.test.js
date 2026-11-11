import { describe, it, expect } from 'vitest';
import { itemReady, itemLeft, dockAt } from './items.js';
import { applyGamePacket, newGame } from './gameModel.js';

const g = (o = {}) => ({ items: [2, 2, 1, 2, 0, 2], itemsLeft: 4, itemLock: false, chain: 3, words: [], ...o });
describe('itemReady', () => {
	it('is for my turn only', () => {
		expect(itemReady(g(), 0, true)).toBe(true);
		expect(itemReady(g(), 0, false)).toBe(false);
	});
	it('needs a count left, for the item and overall', () => {
		expect(itemReady(g(), 4, true)).toBe(false);
		expect(itemReady(g({ itemsLeft: 0 }), 0, true)).toBe(false);
	});
	it('waits for the first word and for the next turn after one use', () => {
		expect(itemReady(g({ chain: 0 }), 0, true)).toBe(false);
		expect(itemReady(g({ itemLock: true }), 0, true)).toBe(false);
	});
});
describe('itemLeft', () => {
	it('is the item\'s own count, capped by the round\'s five', () => {
		expect(itemLeft(g(), 0)).toBe(2);
		expect(itemLeft(g({ itemsLeft: 1 }), 0)).toBe(1);
		expect(itemLeft(g({ itemsLeft: 0 }), 0)).toBe(0);
		expect(itemLeft(g(), 4)).toBe(0);
	});
});
describe('dockAt', () => {
	const answer = { x: 300, y: 200, w: 200, h: 80 };
	it('answer wins over the edges', () => expect(dockAt(350, 220, 1000, 500, answer)).toBe('answer'));
	it('picks the nearest edge when close to one', () => {
		expect(dockAt(20, 250, 1000, 500, answer)).toBe('left');
		expect(dockAt(980, 250, 1000, 500, answer)).toBe('right');
		expect(dockAt(500, 10, 1000, 500, answer)).toBe('top');
		expect(dockAt(500, 490, 1000, 500, answer)).toBe('bottom');
	});
	it('none in the middle of nowhere', () => expect(dockAt(600, 120, 1000, 500, answer)).toBe(null));
});
describe('useItem packets', () => {
	const state = () => ({ id: 'me', rooms: {}, myRoom: null, users: {}, meta: { rules: {}, labels: {} }, game: newGame(null) });
	it('keeps my counts, locks until the next turn, resets each round', () => {
		const s = state();
		applyGamePacket(s, { type: 'useItem', target: 'me', item: 1, isEnd: true, remaining: [2, 1, 2, 2, 2, 2], remainingUses: 4 }, 1);
		expect(s.game.items).toEqual([2, 1, 2, 2, 2, 2]);
		expect(s.game.itemsLeft).toBe(4);
		expect(s.game.itemLock).toBe(true);
		applyGamePacket(s, { type: 'turnStart', turn: 0, seq: [] }, 2);
		expect(s.game.itemLock).toBe(false);
		applyGamePacket(s, { type: 'roundReady', round: 1 }, 3);
		expect(s.game.items).toEqual([2, 2, 2, 2, 2, 2]);
	});
	it("someone else's item changes only the notice", () => {
		const s = state();
		applyGamePacket(s, { type: 'useItem', target: 'other', item: 2, isEnd: true, remaining: [0, 0, 0, 0, 0, 0], remainingUses: 0 }, 1);
		expect(s.game.items).toEqual([2, 2, 2, 2, 2, 2]);
		expect(s.game.itemUse.item).toBe(2);
	});
});
