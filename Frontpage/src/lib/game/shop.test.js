import { describe, it, expect } from 'vitest';
import { isConsumable, shelfItems, sortItems, filterItems, isNew, optionRows, slotOf, toggleInCart, setQty, maxQty, cartPreview, cartTotal, itemName, dynItem, inventoryKind, matchesInventory, isEquippable, equipSlot, isEquipped, ownedItems, bonusKind, bonusValue, gachaItems, gachaMeta, gachaPool, drawPrice, tally, inSub, SHOP_TABS, activeAt, resolveHome, sectionItems } from './shop.js';

const shop = {
	a: { _id: 'a', cost: '300', hit: 5, group: 'Mhead', updatedAt: '2026-01-01 00:00:00' },
	b: { _id: 'b', cost: '100', hit: 9, group: 'Meye', updatedAt: '2026-03-01 00:00:00' },
	c: { _id: 'c', cost: '-1', hit: 1, group: 'Meye', updatedAt: '2026-02-01 00:00:00' },
	d: { _id: 'd', cost: '200', hit: 1, group: 'BDG1', updatedAt: 'nonsense' }
};
const names = { a: '가', b: '나', c: '다', d: '라' };
const nameOf = (id) => names[id];

describe('shelf', () => {
	it('drops items with a negative cost and parses numbers', () => {
		const list = shelfItems(shop);
		expect(list.map((g) => g._id)).toEqual(['a', 'b', 'd']);
		expect(list[0].cost).toBe(300);
		expect(list[2].updatedAt).toBe(0);
	});
	it('sorts like the legacy shop', () => {
		const list = shelfItems(shop);
		const ids = (by) => sortItems(list, by, nameOf).map((g) => g._id);
		expect(ids('updatedAt')).toEqual(['b', 'a', 'd']);
		expect(ids(undefined)).toEqual(['b', 'a', 'd']);
		expect(ids('name')).toEqual(['a', 'b', 'd']);
		expect(ids('hit')).toEqual(['b', 'a', 'd']);
		expect(ids('highCost')).toEqual(['a', 'd', 'b']);
		expect(ids('lowCost')).toEqual(['b', 'd', 'a']);
	});
	it('filters by group and name', () => {
		const list = shelfItems(shop);
		expect(filterItems(list, null, '', nameOf)).toHaveLength(3);
		expect(filterItems(list, ['Mhead'], '', nameOf).map((g) => g._id)).toEqual(['a']);
		expect(filterItems(list, null, ' 나 ', nameOf).map((g) => g._id)).toEqual(['b']);
		expect(filterItems(list, ['Mhead'], '나', nameOf)).toEqual([]);
	});
	it('marks items added within 30 days as new', () => {
		const now = Date.parse('2026-03-10');
		expect(isNew({ updatedAt: Date.parse('2026-03-01') }, now)).toBe(true);
		expect(isNew({ updatedAt: Date.parse('2026-01-01') }, now)).toBe(false);
	});
});

describe('optionRows', () => {
	const label = (k) => `L:${k}`;
	it('formats the bonuses', () => {
		expect(optionRows({ gEXP: 0.125, hMNY: 3, gif: true }, label)).toEqual([
			{ label: 'L:gEXP', value: '+12.5%p' },
			{ label: 'L:hMNY', value: '+3' }
		]);
	});
	it('shows gives / event as labels and gacha with its colour', () => {
		expect(optionRows({ gives: {}, event: true, gacha: { expl: 'x' } }, label)).toEqual([{ text: 'L:gives' }, { text: 'L:event' }, { text: 'x', color: '#f3b819' }]);
	});
});

describe('cart', () => {
	const hat = { _id: 'hat', group: 'Mhead', cost: 100 };
	const cap = { _id: 'cap', group: 'Mhead', cost: 50 };
	const glove = (id) => ({ _id: id, group: 'Mhand', cost: 10 });
	const ticket = { _id: 'bonus', group: 'BON', cost: 30 };
	it('finds the slot of an item', () => {
		expect(slotOf(hat)).toBe('Mhead');
		expect(slotOf({ group: 'BDG3' })).toBe('BDG');
		expect(slotOf(ticket)).toBeNull();
		expect(slotOf({ group: 'NIK' })).toBe('NIK');
		expect(slotOf({ group: 'BAN' })).toBe('BAN');
		expect(slotOf(glove('a'), [])).toBe('Mlhand');
		expect(slotOf(glove('b'), [{ slot: 'Mlhand' }])).toBe('Mrhand');
		expect(slotOf(glove('c'), [{ slot: 'Mlhand' }, { slot: 'Mrhand' }])).toBe('Mlhand');
	});
	it('toggles, and swaps an item for the one in its slot', () => {
		let cart = toggleInCart([], hat);
		cart = toggleInCart(cart, ticket);
		expect(cart.map((c) => c.id)).toEqual(['hat', 'bonus']);
		cart = toggleInCart(cart, cap);
		expect(cart.map((c) => c.id)).toEqual(['bonus', 'cap']);
		expect(toggleInCart(cart, cap).map((c) => c.id)).toEqual(['bonus']);
	});
	it('puts the cart on my equipment and adds up the price', () => {
		const cart = toggleInCart(toggleInCart([], hat), ticket);
		expect(cartPreview({ Mhead: 'mine', Meye: 'e' }, cart)).toEqual({ Mhead: 'hat', Meye: 'e' });
		expect(cartTotal(cart, { hat, bonus: ticket })).toBe(130);
		expect(cartTotal(setQty(cart, ticket, 3), { hat, bonus: ticket })).toBe(190);
	});
});

describe('home', () => {
	const now = Date.parse('2026-11-04T00:00:00+09:00');
	it('knows what is on show', () => {
		expect(activeAt({}, now)).toBe(true);
		expect(activeAt({ from: '2026-12-01T00:00:00+09:00' }, now)).toBe(false);
		expect(activeAt({ to: '2026-11-01T00:00:00+09:00' }, now)).toBe(false);
		const home = resolveHome({ season: { to: '2026-01-01' }, hero: [{ id: 1 }, { id: 2, to: '2026-01-01' }], tiles: 'x' }, now);
		expect(home.season).toBeNull();
		expect(home.hero.map((h) => h.id)).toEqual([1]);
		expect(home.tiles).toEqual([]);
	});
	it('fills sections', () => {
		const list = shelfItems(shop);
		const ids = (s) => sectionItems(s, list, Date.parse('2026-03-10')).map((g) => g._id);
		expect(ids({ kind: 'items', items: ['d', 'zzz', 'a'] })).toEqual(['d', 'a']);
		expect(ids({ kind: 'group', groups: ['Mhead', 'BDG1'] })).toEqual(['a', 'd']);
		expect(ids({ kind: 'best', limit: 1 })).toEqual(['b']);
		expect(ids({ kind: 'new' })).toEqual(['b']);
		expect(ids({ kind: 'nope' })).toEqual([]);
	});
});

describe('quantities', () => {
	const ticket = { _id: 'bonus', group: 'BON', cost: 30 };
	it('limits quantities and removes at zero', () => {
		expect(maxQty(ticket)).toBe(99);
		expect(maxQty({ group: 'Mhead' })).toBe(1);
		expect(maxQty({ group: 'BON', term: 86400 })).toBe(1);
		expect(setQty([], ticket, 500)[0].qty).toBe(99);
		expect(setQty([], { _id: 'h', group: 'Mhead' }, 5)[0].qty).toBe(1);
		const cart = setQty([], ticket, 2);
		expect(setQty(cart, ticket, 7)).toEqual([{ id: 'bonus', slot: null, qty: 7 }]);
		expect(setQty(cart, ticket, 0)).toEqual([]);
		expect(setQty(cart, ticket, 'x')).toEqual([]);
	});
});

describe('tabs and names', () => {
	it('finds items of a sub-category', () => {
		const sub = SHOP_TABS.find((t) => t.id === 'func').subs[1];
		expect(inSub({ _id: 'wordToken', group: 'CNS' }, sub)).toBe(true);
		expect(inSub({ _id: 'boxspring', group: 'CNS' }, sub)).toBe(false);
		expect(inSub({ _id: 'x', group: 'Mhead' }, SHOP_TABS.find((t) => t.id === 'moremi').subs[1])).toBe(true);
		expect(inSub({ _id: 'x', group: 'Mhead' }, undefined)).toBe(false);
	});
	it('names dynamic items like legacy', () => {
		const d = { a: { name: 'A' }, $WPC: { name: '글자 조각' }, '$WPC꽝': { name: '꽝 조각' } };
		expect(itemName(d, 'a')).toBe('A');
		expect(itemName(d, '$WPC꽝')).toBe('꽝 조각');
		expect(itemName(d, '$WPC가')).toBe('글자 조각 - 가');
		expect(itemName(d, 'zzz')).toBe('zzz');
		expect(dynItem('$WPB꽝').text).toBe('꽝');
		expect(dynItem('hair')).toBeNull();
	});
});

describe('gacha', () => {
	const box = { _id: 'box', cost: 300, options: { gacha: { enabled: true }, gives: [{ type: 'item', from: 'wei', pool: { a: 0.7, b: 0.2, c: 0.1 } }, { type: 'money', from: 'fix', value: 5 }] } };
	it('lists gachas and their pool, rarest first', () => {
		expect(gachaItems([box, { _id: 'x', options: {} }]).map((g) => g._id)).toEqual(['box']);
		const b2 = { ...box, _id: 'b2' };
		const placement = { shop: { gacha: [{ _id: 'b2', name: 'B2', start: '2020-01-01T00:00:00+09:00' }, { _id: 'box', name: 'Box' }, { _id: 'later', start: '2999-01-01T00:00:00+09:00' }] } };
		expect(gachaItems([box, b2, { ...box, _id: 'later' }], placement).map((g) => g._id)).toEqual(['b2', 'box']);
		expect(gachaMeta(placement).box.name).toBe('Box');
		expect(gachaMeta(undefined)).toEqual({});
		const pool = gachaPool(box);
		expect(pool.map((e) => e.key)).toEqual(['c', 'b', 'a']);
		expect(pool[0].p).toBeCloseTo(10);
		expect(pool.reduce((s, e) => s + e.p, 0)).toBeCloseTo(100);
		expect(gachaPool({ options: {} })).toEqual([]);
		const old = gachaPool({ options: { gives: [{ type: 'item', from: 'wei', pool: ['x', 'y'], weight: [1, 3] }] } });
		expect(old.map((e) => [e.key, e.p])).toEqual([['x', 25], ['y', 75]]);
	});
	it('prices draws and counts prizes', () => {
		expect(drawPrice(300, 1)).toBe(300);
		expect(drawPrice(300, 10)).toBe(2700);
		expect(drawPrice(333, 10)).toBe(2990);
		expect(tally([{ key: 'a', q: 1 }, { key: 'b', q: 1 }, { key: 'a', q: 2 }])).toEqual([{ key: 'a', q: 3 }, { key: 'b', q: 1 }]);
	});
});

describe('bonus tickets', () => {
	it('sorts tickets into kinds and orders them', () => {
		expect(bonusKind('bonusEXPMNY_10')).toBe('both');
		expect(bonusKind('bonusEXP_5')).toBe('exp');
		expect(bonusKind('bonusMNY_15')).toBe('mny');
		expect(bonusKind('bonusSomething')).toBe('both');
		expect(['bonusEXP_15', 'bonusEXP_5', 'bonusEXP_10'].sort((a, b) => bonusValue(a) - bonusValue(b))).toEqual(['bonusEXP_5', 'bonusEXP_10', 'bonusEXP_15']);
	});
});

describe('my box', () => {
	const shop = { hat: { _id: 'hat', group: 'Mhead', cost: '10' }, glove: { _id: 'glove', group: 'Mhand', cost: '0' }, ticket: { _id: 'ticket', group: 'BON', cost: '5' }, $WPC: { _id: '$WPC', group: 'PIX', cost: '0' }, skin: { _id: 'skin', group: 'NIK', cost: '0' } };
	it('knows kinds, slots and what is worn', () => {
		expect(['Mhead', 'BDG2', 'NIK', 'BAN', 'BON', 'CNS'].map((group) => inventoryKind({ group }))).toEqual(['moremi', 'moremi', 'deco', 'deco', 'func', 'func']);
		expect(isEquippable({ group: 'BON' }, ['BON', 'NIK'])).toBe(true);
		expect(isEquippable({ group: 'CNS' }, ['BON', 'NIK'])).toBe(false);
		expect(equipSlot({ group: 'Mhand' }, 'left')).toBe('Mlhand');
		expect(equipSlot({ group: 'Mhand' }, 'right')).toBe('Mrhand');
		expect(equipSlot({ group: 'BDG3' })).toBe('BDG');
		expect(equipSlot({ group: 'Mhead' })).toBe('Mhead');
		expect(isEquipped({ _id: 'glove', group: 'Mhand' }, { Mrhand: 'glove' })).toBe(true);
		expect(isEquipped({ _id: 'hat', group: 'Mhead' }, { Mhead: 'hat' })).toBe(true);
		expect(isEquipped({ _id: 'hat', group: 'Mhead' }, { Mhead: 'other' })).toBe(false);
	});
	it('lists what I own, what I wear without a count, and dynamic items', () => {
		const names = { hat: 'b', ticket: 'a', $WPC가: 'c', skin: 'd' };
		const list = ownedItems({ hat: { value: 2 }, ticket: { value: 1, expire: 99 }, '$WPC가': { value: 3 }, gone: { value: 1 } }, { NIK: 'skin', Mbody: 'default' }, shop, (id) => names[id] ?? id);
		expect(list.map((e) => [e.item._id, e.count])).toEqual([['ticket', 1], ['hat', 2], ['$WPC가', 3], ['skin', 0]]);
		expect(list[0].expire).toBe(99);
	});
});

describe('box filter', () => {
	const mk = (group, id = group) => ({ _id: id, group });
	const box = (id) => ({ _id: id, group: 'CNS', options: { gives: [{ type: 'item', from: 'wei', pool: { a: 1 } }] } });
	it('only an item that gives something can be consumed', () => {
		expect(isConsumable(box('boxspring'))).toBe(true);
		expect(isConsumable(mk('CNS', 'wordToken'))).toBe(false);
		expect(isConsumable({ _id: 'x', group: 'CNS', options: {} })).toBe(false);
		expect(isConsumable({ _id: 'x', group: 'CNS', options: { gives: [] } })).toBe(false);
		expect(isConsumable({ _id: 'x', group: 'Mhead', options: { gives: [{}] } })).toBe(false);
	});
	it('filters by kind, sub-category and worn', () => {
		expect(matchesInventory(mk('Mhead'), false, 'all')).toBe(true);
		expect(matchesInventory(mk('Mhead'), false, 'worn')).toBe(false);
		expect(matchesInventory(mk('Mhead'), true, 'worn')).toBe(true);
		expect(matchesInventory(mk('Mhead'), false, 'moremi')).toBe(true);
		expect(matchesInventory(mk('Mhead'), false, 'moremi', 'head')).toBe(true);
		expect(matchesInventory(mk('MheadDeco'), false, 'moremi', 'head')).toBe(true);
		expect(matchesInventory(mk('Meye'), false, 'moremi', 'head')).toBe(false);
		expect(matchesInventory(mk('Mhead'), false, 'deco')).toBe(false);
		expect(matchesInventory(mk('NIK'), false, 'deco', 'name')).toBe(true);
		expect(matchesInventory(mk('BAN'), false, 'deco', 'name')).toBe(false);
		expect(matchesInventory(mk('BAD'), false, 'deco', 'all')).toBe(true);
	});
	it('splits 기능성 into tickets, tokens, boxes and the rest', () => {
		expect(matchesInventory(mk('BON'), false, 'func', 'bonus')).toBe(true);
		expect(matchesInventory(mk('CNS', 'wordToken'), false, 'func', 'coin')).toBe(true);
		expect(matchesInventory(mk('CNS', 'wordToken'), false, 'func', 'box')).toBe(false);
		expect(matchesInventory(box('boxspring'), false, 'func', 'box')).toBe(true);
		// a CNS item that gives nothing is not something to open: it is among the rest
		expect(matchesInventory(mk('CNS', 'vip_token'), false, 'func', 'box')).toBe(false);
		expect(matchesInventory(mk('CNS', 'vip_token'), false, 'func', 'etc')).toBe(true);
		expect(matchesInventory(mk('CNS', 'wordToken'), false, 'func', 'etc')).toBe(false);
		expect(matchesInventory(mk('PIX'), false, 'func', 'etc')).toBe(true);
		expect(matchesInventory(mk('BON'), false, 'func', 'etc')).toBe(false);
	});
});
