import { describe, expect, it, vi } from 'vitest';
import { createSender, circleDots, sprayDots, hasPressure } from './drawing.svelte.js';

describe('createSender (the picture rate limit)', () => {
	it('sends the first picture at once and only the newest of the rest when the cooldown ends', () => {
		vi.useFakeTimers();
		const sent = [];
		const sender = createSender((d) => sent.push(d), { cooldown: 2200 });
		sender.push('a');
		sender.push('b');
		sender.push('c'); // b is dropped: only the newest waits
		expect(sent).toEqual(['a']);
		vi.advanceTimersByTime(2200);
		expect(sent).toEqual(['a', 'c']);
		vi.advanceTimersByTime(2200); // nothing waiting: quiet again
		sender.push('d');
		expect(sent).toEqual(['a', 'c', 'd']);
		vi.useRealTimers();
	});
});

describe('brushes', () => {
	const seq = (...v) => {
		let i = 0;
		return () => v[i++ % v.length];
	};
	it('spray puts dots around the point, within the brush radius', () => {
		const dots = sprayDots(100, 100, 10, seq(0.3, 0.5, 0.9));
		expect(dots.length).toBeGreaterThanOrEqual(12);
		for (const d of dots) expect(Math.hypot(d.x - 100, d.y - 100)).toBeLessThanOrEqual(10.0001);
	});
	it('circle brush makes three discs near the point', () => {
		const dots = circleDots(50, 50, 20, seq(0.5));
		expect(dots).toHaveLength(3);
		for (const d of dots) expect(d.size).toBeGreaterThanOrEqual(1);
	});
});

describe('hasPressure (a trackpad must not look like a pen)', () => {
	it('is false for the flat 0.5 a mouse or trackpad gets, true once a pen varies it', () => {
		expect(hasPressure([[0, 0, 0.5], [5, 5, 0.5]])).toBe(false);
		expect(hasPressure([[0, 0], [5, 5]])).toBe(false);
		expect(hasPressure([[0, 0, 0.3], [5, 5, 0.8]])).toBe(true);
	});
});
