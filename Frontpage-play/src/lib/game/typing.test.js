import { describe, expect, it } from 'vitest';
import { clock, isInjected, live, marks, strokesOf, wrongStrokes } from './typing.js';

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
	it('takes what is typed, syllable by syllable', () => {
		const key = (inputType, data, before, after, composed = '') => isInjected({ inputType, data }, before, after, composed);
		expect(key('insertCompositionText', 'ㅎ', '', 'ㅎ')).toBe(false);
		expect(key('insertCompositionText', '한', '하', '한', '하')).toBe(false);
		expect(key('insertCompositionText', '나', '간', '가나', '간')).toBe(false);
		expect(key('insertCompositionText', '동해', '동', '동해', '동')).toBe(false); // a keyboard that composes the whole word
		expect(key('insertText', ' ', '동해', '동해 ')).toBe(false);
		expect(key('insertText', '. ', '끝 ', '끝. ')).toBe(false);
		expect(key('deleteContentBackward', null, '동해', '동')).toBe(false);
		expect(key('insertCompositionText', '백두산', '백두산', '백두산')).toBe(false); // an existing word composed again
	});
	it('refuses what is put in at once', () => {
		const key = (inputType, data, before, after, composed = '') => isInjected({ inputType, data }, before, after, composed);
		expect(key('insertFromPaste', null, '', '동해물과')).toBe(true);
		expect(key('insertFromPaste', null, '가가가가', '동해물과')).toBe(true); // over a selection of the same length
		expect(key('insertFromDrop', null, '', '동')).toBe(true);
		expect(key('insertReplacementText', null, '동해', '동해물과')).toBe(true);
		expect(key('historyUndo', null, '', '동해물과')).toBe(true);
		expect(key('insertText', '동해물과', '', '동해물과')).toBe(true); // a phone keyboard's clipboard
		expect(key('insertText', '동해물과', '가가가가', '동해물과')).toBe(true);
		expect(key('insertCompositionText', '동해물과', '', '동해물과')).toBe(true);
		expect(key('', null, '', '동해물과')).toBe(true); // a value set by a script
	});
	it('writes minutes and seconds', () => {
		expect(clock(0)).toBe('0:00');
		expect(clock(72_900)).toBe('1:12');
	});
});
