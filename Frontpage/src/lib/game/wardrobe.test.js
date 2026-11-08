import { describe, it, expect } from 'vitest';
import { normalizePreset, currentEquip, itemSlots, canWear, planApply, presetItems, MAX_NAME } from './wardrobe.js';

const shop = {
	hat: { group: 'Mhead' },
	balloon: { group: 'Mhand' },
	badge: { group: 'BDG2' },
	ticket: { group: 'BON' },
	shirt: { group: 'Mclothes' }
};

describe('normalizePreset', () => {
	it('keeps known slots and a trimmed, shortened name', () => {
		const p = normalizePreset({ id: 'a', name: `  ${'x'.repeat(40)} `, equip: { Mhead: 'hat', Mfoo: 'x', Mclothes: 5, BON: 'ticket' } }, 'f');
		expect(p).toEqual({ id: 'a', name: 'x'.repeat(MAX_NAME), equip: { Mhead: 'hat' } });
	});
	it('rejects what has no name or no equip', () => {
		expect(normalizePreset({ name: '  ', equip: {} }, 'f')).toBe(null);
		expect(normalizePreset({ name: 'a', equip: [] }, 'f')).toBe(null);
		expect(normalizePreset(null, 'f')).toBe(null);
	});
	it('falls back to the given id', () => {
		expect(normalizePreset({ name: 'a', equip: {} }, 'legacy-0').id).toBe('legacy-0');
	});
});

describe('currentEquip', () => {
	it('drops the bonus ticket and anything else outside an outfit', () => {
		expect(currentEquip({ Mhead: 'hat', BON: 'ticket', robot: true })).toEqual({ Mhead: 'hat' });
	});
});

describe('itemSlots', () => {
	it('knows hands, badges, plain slots and the unwearable', () => {
		expect(itemSlots(shop.balloon)).toEqual(['Mlhand', 'Mrhand']);
		expect(itemSlots(shop.badge)).toEqual(['BDG']);
		expect(itemSlots(shop.hat)).toEqual(['Mhead']);
		expect(itemSlots(shop.ticket)).toBe(null);
		expect(itemSlots(undefined)).toBe(null);
	});
});

describe('canWear', () => {
	const preset = (equip) => ({ id: 'p', name: 'p', equip });
	it('needs every item owned', () => {
		expect(canWear(preset({ Mhead: 'hat' }), shop, { hat: { value: 1 } })).toBe(true);
		expect(canWear(preset({ Mhead: 'hat' }), shop, {})).toBe(false);
	});
	it('needs two of a hand item worn in both hands', () => {
		const both = preset({ Mlhand: 'balloon', Mrhand: 'balloon' });
		expect(canWear(both, shop, { balloon: { value: 1 } })).toBe(false);
		expect(canWear(both, shop, { balloon: { value: 2 } })).toBe(true);
	});
	it('refuses an item in a slot it does not fit', () => {
		expect(canWear(preset({ Mclothes: 'hat' }), shop, { hat: { value: 1 } })).toBe(false);
	});
});

describe('planApply', () => {
	it('takes the old hands off before the new ones go on, then the other slots', () => {
		const plan = planApply({ Mlhand: 'a', Mrhand: 'b', Mhead: 'hat' }, { Mlhand: 'b', Mrhand: 'a', Mclothes: 'shirt' });
		expect(plan).toEqual([
			{ item: 'a', slot: 'Mlhand' },
			{ item: 'b', slot: 'Mrhand' },
			{ item: 'b', slot: 'Mlhand' },
			{ item: 'a', slot: 'Mrhand' },
			{ item: 'hat', slot: 'Mhead' }, // toggled off: the outfit has no head item (slots go in the legacy order)
			{ item: 'shirt', slot: 'Mclothes' }
		]);
	});
	it('does nothing when the outfit is already on', () => {
		expect(planApply({ Mhead: 'hat' }, { Mhead: 'hat' })).toEqual([]);
	});
});

describe('presetItems', () => {
	it('lists the items in slot order', () => {
		expect(presetItems({ equip: { Mclothes: 's', Mhead: 'h' } })).toEqual(['h', 's']);
	});
});

describe('outfits remember which copy they wear', () => {
	it('keeps copies of worn items only, as whole numbers', () => {
		const p = normalizePreset({ id: 'a', name: 'x', equip: { Mhead: 'hat' }, copies: { hat: 2, other: 1, Mhead: 3 } }, 'f');
		expect(p.copies).toEqual({ hat: 2 });
		expect(normalizePreset({ id: 'a', name: 'x', equip: { Mhead: 'hat' }, copies: { hat: 1.5 } }, 'f').copies).toEqual({});
	});
	it('has none on an outfit from before enchants', () => {
		expect(normalizePreset({ id: 'a', name: 'x', equip: { Mhead: 'hat' } }, 'f').copies).toBeUndefined();
	});
});
