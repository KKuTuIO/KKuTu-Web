import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from '@testing-library/svelte';
import { tick } from 'svelte';
import ScoreNumber from './ScoreNumber.svelte';

afterEach(() => {
	cleanup();
	vi.useRealTimers();
	vi.unstubAllGlobals();
});
const reduced = () => vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} })); // no counting
const text = (c) => [c.querySelector('.pad').textContent, c.querySelector('.lit').textContent];

describe('ScoreNumber', () => {
	it('pads to six digits, the padding zeros apart from the digits of the score, with a comma between the groups', async () => {
		reduced();
		const { container } = render(ScoreNumber, { value: 1524, color: 'red' });
		await tick();
		expect(text(container)).toEqual(['00', '1,524']);
		expect(container.querySelector('[data-flaps]').getAttribute('aria-label')).toBe('1,524');
	});
	it('keeps the comma with the padding when the score has fewer than four digits', async () => {
		reduced();
		const { container } = render(ScoreNumber, { value: 804, color: 'red' });
		await tick();
		expect(text(container)).toEqual(['000,', '804']);
	});
	it('grows past six digits for a bigger score, and still lights the last zero of a score of nothing', async () => {
		reduced();
		const big = render(ScoreNumber, { value: 12345678, color: 'red' });
		await tick();
		expect(text(big.container)).toEqual(['', '12,345,678']);
		cleanup();
		const zero = render(ScoreNumber, { value: 0, color: 'red' });
		await tick();
		expect(text(zero.container)).toEqual(['000,00', '0']); // nothing is not unreadable
	});
	it('counts up from nothing, and `skipped` shows the end at once', async () => {
		vi.useFakeTimers();
		const { container, rerender } = render(ScoreNumber, { value: 1000, color: 'red' });
		await tick();
		await vi.advanceTimersByTimeAsync(100);
		const digits = (c) => Number(text(c).join('').replace(/,/g, ''));
		expect(digits(container)).toBeLessThan(1000);
		await rerender({ value: 1000, color: 'red', skipped: 1 });
		await tick();
		expect(digits(container)).toBe(1000);
	});
});
