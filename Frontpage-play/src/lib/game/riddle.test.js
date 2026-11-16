import { describe, expect, it } from 'vitest';
import { countdown, dailyNumber, jamoOfKey, keyMarks, KEY_ROWS, shareText, tilesOf, typeJamo } from './riddle.js';

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
	it('gathers a try into a tile for each syllable, with the number of jamo in place and the number elsewhere', () => {
		// 3 + 3 jamo: the first syllable has two in place and one not in the word, the second one in place and one elsewhere
		const row = { j: 'ㄱㅏㄱㄱㅏㅣ', m: 'ccapac', s: [3, 3], w: '각개' };
		expect(tilesOf(row)).toEqual([
			{ size: 3, start: 0, ch: '각', c: 2, p: 0, state: 'o' },
			{ size: 3, start: 3, ch: '개', c: 1, p: 1, state: 'm' }
		]);
		expect(tilesOf({ j: 'ㄱㅏㄴㅏ', m: 'cccc', s: [2, 2], w: '가나' }).map((t) => t.state)).toEqual(['g', 'g']);
		expect(tilesOf({ j: 'ㄱㅏㄴㅏ', m: 'aaaa', s: [2, 2] }).map((t) => [t.state, t.ch])).toEqual([['a', 'ㄱㅏ'], ['a', 'ㄴㅏ']]);
		expect(tilesOf({ j: 'ㄱㅏㄴㅏ', m: 'pcap', s: [2, 2] }).map((t) => t.state)).toEqual(['m', 'y']);
		expect(tilesOf({ j: 'ㄱㅏㄴㅏ', m: 'ppaa', s: [2, 2] }).map((t) => t.state)).toEqual(['y', 'a']);
		// a try without sizes is a tile for each jamo
		expect(tilesOf({ j: 'ㄱㅏ', m: 'cp' }).map((t) => t.state)).toEqual(['g', 'y']);
	});
	it('colours a key only for what a tile makes certain', () => {
		const rows = [
			{ j: 'ㄱㅏㄱㄱㅏㅣ', m: 'ccapac', s: [3, 3] }, // 각: 2 in place, 1 not in the word -> nothing certain; 개: 1 in place, 1 elsewhere, 1 ... not all in the word
			{ j: 'ㅁㅜㄴㅎㅗㅣ', m: 'aaappp', s: [3, 3] }, // 문: none in the word; 회: all three in the word
			{ j: 'ㄴㅏㅂㅏ', m: 'cccc', s: [2, 2] } // 나바: right
		];
		const m = keyMarks(rows);
		expect(m['ㅁ']).toBe('a');
		expect(m['ㅜ']).toBe('a');
		expect(m['ㅎ']).toBe('p');
		expect(m['ㅗ']).toBe('p');
		expect(m['ㄴ']).toBe('c'); // a right syllable beats "none in the word" of 문
		expect(m['ㅂ']).toBe('c');
		expect(m['ㄱ']).toBeUndefined(); // the first try left it uncertain
	});
	it('does not colour a key grey for a jamo that was only counted out in one tile because another tile had it', () => {
		// 각 has two in place (ㄱ and ㅏ are in the word), so the ㄱ and ㅏ of the grey 개 are the ones the word does not have a second of
		const m = keyMarks([{ j: 'ㄱㅏㄱㄱㅏㅣ', m: 'ccaaaa', s: [3, 3] }]);
		expect(m['ㄱ']).toBeUndefined();
		expect(m['ㅏ']).toBeUndefined();
		expect(m['ㅣ']).toBe('a');
	});
	it('counts down in hours, minutes and seconds', () => {
		expect(countdown(1000 * (6 * 3600 + 12 * 60 + 5), 0)).toBe('06:12:05');
		expect(countdown(0, 5000)).toBe('00:00:00');
	});
	it('shares the marks as squares and says nothing about the word', () => {
		const rows = [{ j: 'ㄱㄴㄷㄹㅁㅂ', m: 'aapaac' }, { j: 'ㅅㅏㄱㅗㅏㅇ', m: 'cccccc' }];
		expect(shareText({ rows, won: true, number: 12, title: '리들' })).toBe('리들 12 2/6\n\n⬜⬜🟨⬜⬜🟩\n🟩🟩🟩🟩🟩🟩');
		// with the syllables: a tile for each, the numbers of the orange and the yellow
		const tiles = [{ j: 'ㄱㅏㄱㄱㅏㅣ', m: 'ccapac', s: [3, 3] }, { j: 'ㄱㅏㄴㅏ', m: 'cccc', s: [2, 2] }];
		expect(shareText({ rows: tiles, won: true, title: '리들' })).toBe('리들 2/6\n\n🟧2 🟧1🟨1\n🟩 🟩');
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
