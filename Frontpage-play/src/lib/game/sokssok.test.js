import { describe, expect, it } from 'vitest';
import { clock, compose, msLeft, preview } from './sokssok.js';

describe('sokssok', () => {
	it('reads tiles as the server does', () => {
		expect(compose(['ㅎ', 'ㅏ', 'ㄱ', 'ㄱ', 'ㅛ'])).toBe('학교');
		expect(compose(['ㄱ', 'ㅘ', 'ㄴ', 'ㅕ', 'ㄱ'])).toBe('과녁');
		expect(compose(['ㄱ', 'ㅏ', 'ㅂ', 'ㅅ', 'ㅇ', 'ㅓ', 'ㅊ', 'ㅣ'])).toBe('값어치');
		expect(compose(['ㄱ', 'ㅏ', 'ㄴ', 'ㅏ'])).toBe('가나');
		expect(compose(['ㄱ', 'ㅏ', 'ㅂ', 'ㅅ', 'ㅏ'])).toBe('갑사');
		expect(compose(['ㅏ'])).toBe(null);
		expect(compose(['ㄱ', 'ㅏ', 'ㄸ'])).toBe(null);
	});
	it('shows the part that spells a word and then the waiting tiles', () => {
		expect(preview([])).toBe('');
		expect(preview(['ㄱ'])).toBe('ㄱ');
		expect(preview(['ㄱ', 'ㅏ', 'ㄴ'])).toBe('간');
		expect(preview(['ㄱ', 'ㅏ', 'ㄴ', 'ㄴ'])).toBe('간ㄴ');
		expect(preview(['ㄱ', 'ㅏ', 'ㄴ', 'ㅏ'])).toBe('가나');
	});
	it('counts the time left from when the server said it', () => {
		expect(msLeft({ left: 10000, at: 1000 }, 4000)).toBe(7000);
		expect(msLeft({ left: 1000, at: 0 }, 5000)).toBe(0);
		expect(msLeft(null)).toBe(0);
		expect(clock(83000)).toBe('1:23');
		expect(clock(0)).toBe('0:00');
		expect(clock(500)).toBe('0:01');
	});
});
