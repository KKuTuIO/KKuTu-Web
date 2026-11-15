import { describe, expect, it } from 'vitest';
import { clock, live, marks, strokesOf, wrongStrokes } from './typing.js';

describe('typing', () => {
	it('counts strokes as the server does', () => {
		expect(strokesOf('가')).toBe(2);
		expect(strokesOf('각')).toBe(3);
		expect(strokesOf('과')).toBe(3);
		expect(strokesOf('닭')).toBe(4);
		expect(strokesOf('a b')).toBe(3);
	});
	it('counts the wrong strokes by the place', () => {
		expect(wrongStrokes('동해물', '동해물')).toBe(0);
		expect(wrongStrokes('동해물', '동해몰')).toBe(strokesOf('물'));
		expect(wrongStrokes('동해물', '동해')).toBe(strokesOf('물'));
		expect(wrongStrokes('동해물', '동해물과')).toBe(0);
	});
	it('marks the typed characters, and leaves the one being composed', () => {
		expect(marks('가나다', '가라').map((m) => m.ok)).toEqual([true, false, null]);
		expect(marks('가나다', '가ㄴ', true).map((m) => m.ok)).toEqual([true, null, null]);
	});
	it('gives the speed and the accuracy so far', () => {
		const r = live({ right: 10, total: 10, expected: '가나', typed: '가라', elapsed: 6000 });
		// 가 right (2), 나 wrong (2): 12 right of 14, and the 2 wrong ones take 2 right ones away: 10 net in 6 s
		expect(r.speed).toBe(100);
		expect(r.accuracy).toBe(85.7);
		expect(r.wrong).toBe(2);
		expect(live({ right: 0, total: 0, expected: '', typed: '', elapsed: 0 })).toEqual({ speed: 0, accuracy: 100, wrong: 0 });
	});
	it('writes minutes and seconds', () => {
		expect(clock(0)).toBe('0:00');
		expect(clock(72_900)).toBe('1:12');
	});
});
