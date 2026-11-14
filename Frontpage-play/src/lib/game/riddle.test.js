import { describe, expect, it } from 'vitest';
import { countdown, dailyNumber, jamoOfKey, keyMarks, KEY_ROWS, shareText, typeJamo } from './riddle.js';

describe('riddle', () => {
	it('reads the key by its place, and shift gives the double consonants', () => {
		expect(jamoOfKey('KeyK')).toBe('ㅏ');
		expect(jamoOfKey('KeyO')).toBe('ㅏㅣ');
		expect(jamoOfKey('KeyR', true)).toBe('ㄱㄱ');
		expect(jamoOfKey('KeyK', true)).toBe('ㅏ');
		expect(jamoOfKey('Digit1')).toBe(null);
	});
	it('the keyboard has each of the 24 basic jamo once', () => {
		const all = KEY_ROWS.flat();
		expect(all.length).toBe(24);
		expect(new Set(all).size).toBe(24);
	});
	it('refuses a key that does not all fit', () => {
		expect(typeJamo('ㄱㄴㄷㄹㅁ', 'ㅏㅣ')).toBe('ㄱㄴㄷㄹㅁ');
		expect(typeJamo('ㄱㄴㄷㄹㅁ', 'ㅏ')).toBe('ㄱㄴㄷㄹㅁㅏ');
	});
	it('colours a key by the best mark it got', () => {
		const m = keyMarks([{ j: 'ㄱㄱㅏㅏㅏㅏ', m: 'apcaaa' }, { j: 'ㄱㄴㄷㄹㅁㅂ', m: 'caaaaa' }]);
		expect(m['ㄱ']).toBe('c');
		expect(m['ㅏ']).toBe('c');
		expect(m['ㄴ']).toBe('a');
	});
	it('counts down in hours, minutes and seconds', () => {
		expect(countdown(1000 * (6 * 3600 + 12 * 60 + 5), 0)).toBe('06:12:05');
		expect(countdown(0, 5000)).toBe('00:00:00');
	});
	it('shares the marks as squares and says nothing about the word', () => {
		const rows = [{ j: 'ㄱㄴㄷㄹㅁㅂ', m: 'aapaac' }, { j: 'ㅅㅏㄱㅗㅏㅇ', m: 'cccccc' }];
		expect(shareText({ rows, won: true, number: 12, title: '리들' })).toBe('리들 12 2/6\n\n⬜⬜🟨⬜⬜🟩\n🟩🟩🟩🟩🟩🟩');
		expect(shareText({ rows, won: false, title: '리들' })).toBe('리들 X/6\n\n⬜⬜🟨⬜⬜🟩\n🟩🟩🟩🟩🟩🟩');
		expect(shareText({ rows, won: true, number: 3, title: '리들', hint: true }).split('\n')[0]).toBe('리들 3 2/6 💡');
		expect(shareText({ rows, won: true, title: '리들', hint: true, paid: true }).split('\n')[0]).toBe('리들 2/6 💡 💰');
	});
	it('numbers the daily from the first day, by Korean midnight', () => {
		const open = Date.UTC(2026, 10, 15, 15, 0, 0); // the 16th 00:00 in Seoul: the next puzzle opens, so the one now open is the 15th
		expect(dailyNumber(open)).toBe(1);
		expect(dailyNumber(open + 86_400_000)).toBe(2);
	});
});
