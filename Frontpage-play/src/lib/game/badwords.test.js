import { describe, expect, it } from 'vitest';
import { createSplitFilter, maskBad } from './badwords.js';

describe('badwords (copied from the legacy client)', () => {
	it('masks swearing with hearts of the same length and leaves normal text alone', () => {
		expect(maskBad('안녕하세요')).toBe('안녕하세요');
		expect(maskBad('이 병신 같은')).toBe('이 ♥♥ 같은');
		expect(maskBad('병.신')).toBe('♥♥♥');
	});
	it('catches a word typed over several short messages, per sender', () => {
		const f = createSplitFilter();
		expect(f('a', '씨', 1000)).toBe('씨');
		expect(f('b', '발', 1500)).toBe('발'); // someone else's "발" doesn't complete it
		expect(f('a', '발', 2000)).toBe('♥');
		expect(f('a', '발', 20_000)).toBe('발'); // too long ago
	});
});
