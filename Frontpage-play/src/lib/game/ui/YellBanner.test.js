import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render } from '@testing-library/svelte';
import { tick } from 'svelte';
import YellBanner from './YellBanner.svelte';
import { banner, nextYell, scrollDuration, yell } from './yell.svelte.js';

beforeEach(() => {
	banner.current = null;
	banner.queue.length = 0;
	globalThis.matchMedia ??= (q) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {} });
});
afterEach(cleanup);

describe('yell queue', () => {
	it('shows the first announcement now and the others in order, one at a time', () => {
		yell('첫째');
		yell('둘째');
		yell('셋째');
		expect(banner.current.message).toBe('첫째');
		nextYell();
		expect(banner.current.message).toBe('둘째');
		nextYell();
		nextYell();
		expect(banner.current).toBeNull();
	});
	it('collapses whitespace, ignores empty text and keeps only the newest few waiting', () => {
		yell('  가   나\n다 ');
		expect(banner.current.message).toBe('가 나 다');
		yell('   ');
		expect(banner.queue).toHaveLength(0);
		for (let i = 0; i < 9; i++) yell(`m${i}`);
		expect(banner.queue.map((q) => q.message)).toEqual(['m4', 'm5', 'm6', 'm7', 'm8']);
	});
	it('scrolls at a calm, steady pace, never in less than ten seconds', () => {
		expect(scrollDuration(50, 300)).toBe(10);
		expect(scrollDuration(1000, 1000)).toBeCloseTo(2000 / 65);
	});
});

describe('<YellBanner/>', () => {
	it('draws the text as plain text, readable to a screen reader, and closes on the button', async () => {
		render(YellBanner);
		yell('<img src=x onerror="window.__yell=1"><b>굵게</b> 점검 안내');
		await tick();
		const bar = document.querySelector('[data-yell]');
		expect(bar.querySelector('img, b')).toBeNull();
		expect(window.__yell).toBeUndefined();
		expect(bar.textContent).toContain('<img src=x onerror="window.__yell=1"><b>굵게</b> 점검 안내');
		expect(bar.querySelector('.sr-only').textContent).toContain('점검 안내');
		await fireEvent.click(bar.querySelector('button'));
		await tick();
		expect(document.querySelector('[data-yell]')).toBeNull();
	});
	it('moves on to the next announcement when one is closed', async () => {
		render(YellBanner);
		yell('하나');
		yell('둘');
		await tick();
		expect(document.querySelector('[data-yell]').textContent).toContain('하나');
		await fireEvent.click(document.querySelector('[data-yell] button'));
		await tick();
		expect(document.querySelector('[data-yell]').textContent).toContain('둘');
	});
});
