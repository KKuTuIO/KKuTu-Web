import { describe, expect, it } from 'vitest';
import { itemImage, levelSpriteStyle, moremiLayers } from './images.js';
import { md5 } from './md5.js';

const shop = { hat: { _id: 'hat', group: 'Mhead', options: {} }, wing: { _id: 'wing', group: 'Mback', options: { gif: 1 } }, b1: { _id: 'b1', group: 'BDG1', options: {} } };

describe('images', () => {
	it('builds item urls like the legacy client', () => {
		expect(itemImage('hat', shop)).toBe('https://cdn.kkutu.io/img/kkutu/moremi/head/hat.png?v=4.0.5f');
		expect(itemImage('wing', shop)).toContain('/back/wing.gif');
		expect(itemImage(undefined, shop, 'Mhand')).toContain('/hand/default.png');
		expect(itemImage('nope', shop)).toContain('head/default.png?s=fb');
		// an unknown or 'default' item in a slot shows that slot's default, not the head's
		expect(itemImage('nope', shop, 'Mclothes')).toContain('/clothes/default.png');
		expect(itemImage('default', shop, 'Mshoes')).toContain('/shoes/default.png');
	});
	it('orders layers with body after back and a flipped right hand', () => {
		const layers = moremiLayers({ BDG: 'b1' }, shop, ['back', 'eye', 'lhand', 'rhand', 'body']);
		expect(layers.map((l) => l.part)).toEqual(['back', 'body', 'eye', 'lhand', 'rhand', 'badge']);
		expect(layers.find((l) => l.part === 'rhand').flip).toBe(true);
	});
	it('positions level sprite', () => {
		expect(levelSpriteStyle(26)).toContain('background-position:0% -100%');
	});
	it('md5', () => {
		expect(md5('')).toBe('d41d8cd98f00b204e9800998ecf8427e');
		expect(md5('abc')).toBe('900150983cd24fb0d6963f7d28e17f72');
	});
});

describe('moremiLayers vectors', () => {
	const parts = ['back', 'eye', 'mouth', 'shoes', 'body'];
	const v = (equip) => Object.fromEntries(moremiLayers(equip, shop, parts).map((l) => [l.part, l.vector]));
	it('draws the default body, eyes and mouth as vectors', () => {
		expect(v({})).toMatchObject({ body: 'body', eye: 'eye', mouth: 'mouth', shoes: undefined, back: undefined });
	});
	it('keeps PNGs for non-default items, robot and foot remover', () => {
		expect(v({ Meye: 'sunglasses', Mmouth: 'x' })).toMatchObject({ eye: undefined, mouth: undefined, body: 'body' });
		expect(v({ Mbody: 'carrot' }).body).toBeUndefined();
		expect(v({ robot: 1 }).body).toBeUndefined();
		expect(v({ Mshoes: 'foot_remover' }).body).toBeUndefined();
	});
});
