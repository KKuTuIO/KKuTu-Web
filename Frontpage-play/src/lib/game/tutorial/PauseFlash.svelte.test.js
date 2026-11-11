import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render } from '@testing-library/svelte';
import { tick } from 'svelte';
import PauseFlash from './PauseFlash.svelte';

afterEach(cleanup);
const icons = (c) => [...c.querySelectorAll('.material-symbols-outlined')].map((e) => e.textContent.trim());

describe('PauseFlash', () => {
	it('shows nothing before the first pause', () => {
		const { container } = render(PauseFlash, { pause: { on: false, kind: 'pause', n: 0 } });
		expect(icons(container)).toEqual([]);
	});
	it('shows the pause picture, and nothing else (no label, nothing to press)', async () => {
		const { container } = render(PauseFlash, { pause: { on: true, kind: 'pause', n: 1 } });
		await tick();
		expect(icons(container)).toEqual(['pause']);
		expect(container.querySelector('button, a, input')).toBeNull();
		expect(container.textContent.replace('pause', '').trim()).toBe('');
	});
	it('shows the play picture when the game goes on', async () => {
		const { container } = render(PauseFlash, { pause: { on: false, kind: 'play', n: 2 } });
		await tick();
		expect(icons(container)).toEqual(['play_arrow']);
	});
});
