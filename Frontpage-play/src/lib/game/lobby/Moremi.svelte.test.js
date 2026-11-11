import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/svelte';
import Moremi from './Moremi.svelte';

const parts = ['back', 'eye', 'mouth', 'shoes', 'clothes', 'head', 'lhand', 'rhand'];
const shop = { balloon: { _id: 'balloon', group: 'Mhand', options: {} } };

describe('Moremi', () => {
	it('mirrors the right hand on a wrapper, so the breathing animation (which uses `scale` too) cannot cancel the mirror', () => {
		const { container } = render(Moremi, { equip: { Mlhand: 'balloon', Mrhand: 'balloon' }, shop, parts, size: 96 });
		const mirrored = container.querySelectorAll('.-scale-x-100');
		expect(mirrored).toHaveLength(1);
		const img = mirrored[0].querySelector('img');
		expect(img?.getAttribute('src')).toContain('/hand/balloon.png');
		// the picture itself carries no mirror, and (being a big, living avatar) it is the one that breathes
		expect(img?.classList.contains('-scale-x-100')).toBe(false);
		expect(img?.classList.contains('breathe')).toBe(true);
	});
});
