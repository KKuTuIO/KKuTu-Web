import { describe, it, expect } from 'vitest';
import { itemEffect, totals, perGame, baseGame, bestFormula } from './formula.js';

const shop = {
	t5: { _id: 't5', group: 'BON', options: { gEXP: 0.05 } },
	t15: { _id: 't15', group: 'BON', options: { gEXP: 0.15 } },
	m10: { _id: 'm10', group: 'BON', options: { gMNY: 0.1 } },
	hatA: { _id: 'hatA', group: 'Mhead', options: { gEXP: 0.5 } },
	hatB: { _id: 'hatB', group: 'Mhead', options: { gEXP: 0.2, gMNY: 0.4 } },
	hatC: { _id: 'hatC', group: 'Mhead', options: { hEXP: 10 } },
	glove: { _id: 'glove', group: 'Mhand', options: { gEXP: 1 } },
	glove2: { _id: 'glove2', group: 'Mhand', options: { gEXP: 0.3 } },
	plain: { _id: 'plain', group: 'Mhead', options: {} }
};
const own = (id, count = 1, expire) => ({ item: shop[id], count, expire });
const equips = ['BON', 'Mhead', 'Mhand', 'NIK'];
const base = { exp: 100, money: 100, minutes: 2 };
const best = (goal, owned, equip = {}) => bestFormula({ owned, equip, goal, base, equips, shop });

describe('해커레미의 조합', () => {
	it('reads what an item adds and adds up what is worn', () => {
		expect(itemEffect(shop.hatB)).toEqual({ gx: 0.2, hx: 0, gm: 0.4, hm: 0 });
		expect(itemEffect(undefined)).toEqual({ gx: 0, hx: 0, gm: 0, hm: 0 });
		expect(totals({ BON: 't5', Mhead: 'hatA' }, shop).gx).toBeCloseTo(0.55);
	});
	it('values a game like the server: fractions of the base, flat per minute', () => {
		expect(perGame({ gx: 3, hx: 10, gm: 0, hm: 0 }, base)).toEqual({ exp: 420, money: 100 });
	});
	it('takes my recent games as the reference, without what I wear', () => {
		expect(baseGame([])).toEqual({ exp: 300, money: 100, minutes: 5 });
		const b = baseGame([{ exp: 300, money: 150, playTime: 120000 }], { gx: 0.5, hx: 0, gm: 0, hm: 0 });
		expect(b.exp).toBeCloseTo(200);
		expect(b.money).toBeCloseTo(150);
		expect(b.minutes).toBeCloseTo(2);
	});
	it('picks the best owned item of each slot for the goal', () => {
		const owned = [own('t5'), own('t15'), own('hatA'), own('hatB'), own('m10')];
		const xp = best('xp', owned);
		expect(xp.picks.map((p) => [p.slot, p.id])).toEqual(expect.arrayContaining([['BON', 't15'], ['Mhead', 'hatA']]));
		const ping = best('ping', owned);
		expect(ping.picks.map((p) => [p.slot, p.id])).toEqual(expect.arrayContaining([['BON', 'm10'], ['Mhead', 'hatB']]));
		// both: hatB (0.2 + 0.4 = 0.6) beats hatA (0.5); the ticket worth most is t15 (0.15 vs 0.1)
		const both = best('both', owned);
		expect(both.picks.find((p) => p.slot === 'Mhead').id).toBe('hatB');
		expect(both.picks.find((p) => p.slot === 'BON').id).toBe('t15');
		expect(xp.then.gx).toBeCloseTo(0.65);
		expect(xp.now.gx).toBe(0);
	});
	it('keeps what I wear when nothing owned is better, and ignores items with no effect, expired or not wearable', () => {
		expect(best('xp', [own('hatA')], { Mhead: 'hatA' }).picks).toEqual([]);
		expect(best('xp', [own('plain')]).picks).toEqual([]);
		expect(best('xp', [own('hatA', 1, 1)]).picks).toEqual([]); // expired long ago
		expect(best('xp', [own('hatA', 1, 4_102_444_800)]).picks.map((p) => p.id)).toEqual(['hatA']); // ends in 2100
		expect(best('xp', [{ item: { _id: 'box', group: 'CNS', options: { gEXP: 9 } }, count: 1 }]).picks).toEqual([]);
	});
	it('puts the best two items in the hands, one item in both only if I own two', () => {
		const one = best('xp', [own('glove'), own('glove2')]);
		expect(one.picks.map((p) => [p.slot, p.id]).sort()).toEqual([['Mlhand', 'glove'], ['Mrhand', 'glove2']]);
		const two = best('xp', [own('glove', 2), own('glove2')]);
		expect(two.picks.map((p) => p.id)).toEqual(['glove', 'glove']);
		expect(two.then.gx).toBeCloseTo(2);
	});
	it('does not move an item that is already in a hand', () => {
		const r = best('xp', [own('glove'), own('glove2')], { Mrhand: 'glove' });
		expect(r.picks).toEqual([{ slot: 'Mlhand', id: 'glove2', from: undefined }]);
	});
});
