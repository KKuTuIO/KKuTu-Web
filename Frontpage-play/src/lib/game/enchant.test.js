import { describe, it, expect, vi } from 'vitest';
import { bestCopy, wornCopies, copyToWear, wearPlan, copiesOf, applyCopyState, splitEnchanted, fakeLine, ownedCount, buyTicket, permille, lineValue, lineText, enchantableItems, chanceAfter, tryEnchant, resolveEnchant, fetchEnchant, repairLine, damagePercent, effectiveValue, damageChanceAt, repairCostOf } from './enchant.js';

const units = { ping_pct: 'PERMILLE', ogg_max_stack: 'FLAT', ogg_charge_cut: 'SECONDS' };

describe('line text', () => {
	it('shows permille as percent with at most one decimal', () => {
		expect([10, 15, 2, 60, 20].map(permille)).toEqual(['1', '1.5', '0.2', '6', '2']);
	});
	it('only divides percent effects', () => {
		expect(lineValue({ effect: 'ping_pct', value: 30 }, units)).toBe('3');
		expect(lineValue({ effect: 'ogg_max_stack', value: 3 }, units)).toBe('3');
		expect(lineValue({ effect: 'ogg_charge_cut', value: 45 }, units)).toBe('45');
	});
	it('passes the game type name and the number to the locale', () => {
		const t = vi.fn((k, p) => `${k}|${p?.mode}|${p?.value}`);
		expect(lineText({ effect: 'ping_pct', value: 20, mode: 'KKT' }, units, (k, p) => (k.startsWith('game.mode') ? 'M' : t(k, p)))).toBe('play.enchant.effect.ping_pct|M|2');
	});
});

describe('picking items', () => {
	const mk = (id, group) => ({ item: { _id: id, group }, count: 1 });
	it('keeps only enchantable groups', () => {
		const out = enchantableItems([mk('a', 'Mhead'), mk('b', 'BON'), mk('c', 'Mhand')], ['Mhead', 'Mhand'], (id) => id);
		expect(out.map((e) => e.item._id)).toEqual(['a', 'c']);
	});
});

describe('reel', () => {
	it('makes a line of a known effect with a value of its unit', () => {
		const l = fakeLine(units, () => 0);
		expect(l).toEqual({ effect: 'ping_pct', value: 10, mode: 'KKT' });
		expect(fakeLine(units, () => 0.99).effect).toBe('ogg_charge_cut');
		expect(fakeLine(units, () => 0.99).value).toBe(60);
	});
	it('has nothing to show without effects', () => {
		expect(fakeLine({}, () => 0)).toBeNull();
	});
});

describe('chance', () => {
	it('starts at the base and rises by the step to 100', () => {
		expect([0, 1, 7, 9].map((p) => chanceAfter(p, { baseChance: 30, pityStep: 10 }))).toEqual([30, 40, 100, 100]);
	});
});

describe('calls', () => {
	const ok = (body) => vi.fn(async () => ({ json: async () => body }));
	it('posts the item and the ticket', async () => {
		const f = ok({ success: true });
		await tryEnchant('x', 2, 'enchantTicketRare', false, f);
		expect(f.mock.calls[0][0]).toBe('/enchant');
		expect(String(f.mock.calls[0][1].body)).toBe('item=x&instance=2&ticket=enchantTicketRare');
	});
	it('asks for protection and repairs a line', async () => {
		const f = ok({});
		await tryEnchant('x', 2, 'enchantTicketNormal', true, f);
		expect(String(f.mock.calls[0][1].body)).toBe('item=x&instance=2&ticket=enchantTicketNormal&protect=true');
		const g = ok({});
		await repairLine('x', 2, 1, g);
		expect(g.mock.calls[0][0]).toBe('/enchant/repair');
		expect(String(g.mock.calls[0][1].body)).toBe('item=x&instance=2&slot=1');
	});
	it('buys one ticket through the shop endpoint', async () => {
		const f = ok({ result: 200 });
		await buyTicket('enchantTicketNormal', 3, f);
		expect(f.mock.calls[0][0]).toBe('/buy/enchantTicketNormal');
		expect(String(f.mock.calls[0][1].body)).toBe('count=3');
	});
	it('counts what the box holds in either shape', () => {
		expect([ownedCount({ a: { value: 3 } }, 'a'), ownedCount({ a: 2 }, 'a'), ownedCount({}, 'a'), ownedCount(null, 'a')]).toEqual([3, 2, 0, 0]);
	});
	it('omits the slot to discard', async () => {
		const f = ok({});
		await resolveEnchant('x', 3, null, f);
		expect(String(f.mock.calls[0][1].body)).toBe('item=x&instance=3');
		await resolveEnchant('x', 3, 1, f);
		expect(String(f.mock.calls[1][1].body)).toBe('item=x&instance=3&slot=1');
	});
	it('turns a network failure into an error', async () => {
		expect(await fetchEnchant(vi.fn(async () => { throw new Error('x'); }))).toEqual({ error: 'network' });
	});
});

const hat = { _id: 'hat', group: 'Mhead' };
const copy = (instance, level, worn = false) => ({ instance, level, lines: [], pending: null, worn });

describe('copies', () => {
	const nameOf = (id) => id;
	it('lists enchanted copies by number, then each plain one', () => {
		const items = { hat: { pity: 2, copies: [copy(2, 3), copy(5, 1)] } };
		const out = copiesOf([{ item: hat, count: 4 }], ['Mhead'], items, nameOf);
		expect(out.map((c) => c.key)).toEqual(['hat#2', 'hat#5', 'hat#p0', 'hat#p1']);
		expect(out.map((c) => c.level)).toEqual([3, 1, 0, 0]);
	});
	it('never lists more enchanted copies than the box holds, nor items of other groups', () => {
		const items = { hat: { copies: [copy(1, 1), copy(2, 1)] } };
		expect(copiesOf([{ item: hat, count: 1 }, { item: { _id: 'b', group: 'BON' }, count: 3 }], ['Mhead'], items, nameOf).map((c) => c.key)).toEqual(['hat#1']);
	});
	it('folds a new state in: replaces a copy, or adds the one a plain copy became', () => {
		const items = { hat: { pity: 3, copies: [copy(1, 1)] } };
		const after = applyCopyState(items, 'hat', { ...copy(1, 2), pity: 0, chance: 30 });
		expect(after.hat.copies.map((c) => c.level)).toEqual([2]);
		expect(after.hat.pity).toBe(0);
		expect(applyCopyState(after, 'hat', { ...copy(2, 1), pity: 0, chance: 30 }).hat.copies.map((c) => c.instance)).toEqual([1, 2]);
		expect(applyCopyState(after, 'hat', { ...copy(0, 0), pity: 4, chance: 70 }).hat.copies).toHaveLength(1);
	});
});

describe('inventory split', () => {
	it('keeps enchanted copies out of the ×n and leaves the plain remainder', () => {
		const items = { hat: { copies: [copy(1, 2), copy(3, 1)] } };
		const out = splitEnchanted([{ item: hat, count: 5 }], items, () => false);
		expect(out.map((e) => [e.key, e.count, e.level ?? 0])).toEqual([['hat#1', 1, 2], ['hat#3', 1, 1], ['hat', 3, 0]]);
	});
	it('carries the lines of an enchanted copy to its tile', () => {
		const lines = [{ effect: 'ping_pct', value: 20, mode: 'KKT' }];
		const out = splitEnchanted([{ item: hat, count: 2 }], { hat: { copies: [{ ...copy(1, 1), lines }] } }, () => false);
		expect(out[0].lines).toEqual(lines);
		expect(out[1].lines).toBeUndefined();
	});
	it('has no plain tile when every copy is enchanted', () => {
		const items = { hat: { copies: [copy(1, 1), copy(2, 1)] } };
		expect(splitEnchanted([{ item: hat, count: 2 }], items, () => false).map((e) => e.key)).toEqual(['hat#1', 'hat#2']);
	});
	it('marks only the worn copy as worn, or the plain stack when none is', () => {
		const items = { hat: { copies: [copy(1, 1), copy(2, 1, true)] } };
		expect(splitEnchanted([{ item: hat, count: 3 }], items, () => true).map((e) => e.equipped)).toEqual([false, true, false]);
		const none = { hat: { copies: [copy(1, 1)] } };
		expect(splitEnchanted([{ item: hat, count: 2 }], none, () => true).map((e) => e.equipped)).toEqual([false, true]);
		expect(splitEnchanted([{ item: hat, count: 2 }], none, () => false).map((e) => e.equipped)).toEqual([false, false]);
	});
	it('passes items without enchants and counts of zero through', () => {
		const out = splitEnchanted([{ item: hat, count: 0 }, { item: { _id: 'x', group: 'Mhead' }, count: 2 }], { hat: { copies: [copy(1, 1)] } }, () => false);
		expect(out.map((e) => [e.key, e.count])).toEqual([['hat', 0], ['x', 2]]);
	});
});

describe('which copy is worn', () => {
	const items = { hat: { copies: [copy(1, 1), copy(2, 4, true), copy(3, 4)] }, cap: { copies: [copy(1, 2)] } };
	it('the best copy is the highest level, the lowest number on a tie, a plain one when none is enchanted', () => {
		expect(bestCopy(items, 'hat', 5)).toBe(2);
		expect(bestCopy(items, 'hat', 1)).toBe(1); // only the first copy is in the box
		expect(bestCopy(items, 'none', 3)).toBe(0);
	});
	it('records the worn copy of each enchanted item, 0 for a plain one, and skips items with no enchants', () => {
		expect(wornCopies(items, ['hat', 'cap', 'cap', 'shoe'])).toEqual({ hat: 2, cap: 0 });
	});
	it('wears what the outfit remembers when it still exists, a plain one when it says so, else the best', () => {
		expect(copyToWear(items, 'hat', 5, 3)).toBe(3);
		expect(copyToWear(items, 'hat', 5, 0)).toBe(0);
		expect(copyToWear(items, 'hat', 5, 9)).toBe(2); // that copy is gone
		expect(copyToWear(items, 'hat', 5, undefined)).toBe(2); // an outfit from before enchants
		expect(copyToWear(items, 'shoe', 5, 4)).toBe(0);
	});
	it('plans wear calls only for items that have enchanted copies', () => {
		expect(wearPlan(items, ['hat', 'shoe', 'hat', 'cap'], { hat: 3, cap: 1 }, { hat: 3 })).toEqual([{ item: 'hat', instance: 3 }, { item: 'cap', instance: 1 }]);
	});
});

describe('damage', () => {
	it('a damaged line shows what is left, rounded down to 0.05%', () => {
		const units = { ping_pct: 'PERMILLE', ogg_charge_cut: 'SECONDS' };
		expect(lineValue({ effect: 'ping_pct', value: 40, damage: 1000 }, units)).toBe('2');
		expect(lineValue({ effect: 'ping_pct', value: 2, damage: 200 }, units)).toBe('0.15'); // 1.8 permille
		expect(lineValue({ effect: 'ping_pct', value: 40 }, units)).toBe('4');
		expect(lineValue({ effect: 'ogg_charge_cut', value: 60, damage: 500 }, units)).toBe('45');
		expect(effectiveValue({ value: 8, damage: 2000 })).toBe(0);
	});
	it('damage reads as percent of the line', () => {
		expect([0, 1, 300, 2000].map((d) => damagePercent({ damage: d }))).toEqual([0, 0.1, 15, 100]);
	});
	it('the damage chance and repair price mirror the server', () => {
		expect([0, 2, 3, 4, 5, 6, 9].map((l) => damageChanceAt(l))).toEqual([0, 0, 20, 40, 60, 80, 80]);
		expect([0, 1, 200, 201, 2000].map((d) => repairCostOf(d))).toEqual([0, 500, 500, 1000, 5000]);
	});
});
