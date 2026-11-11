import { describe, it, expect } from 'vitest';
import { analyzeTray, addError, ownedPieces, boxGains, rewardCount, isPiece, MAX_TRAY } from './charFactory.js';

describe('analyzeTray', () => {
	it('adds the letters and the levels', () => {
		expect(analyzeTray(['$WPC간', '$WPB간', '$WPA간'])).toEqual({ word: '간간간', level: 6, event: false, malformed: false });
	});
	it('marks an event word -1 and flags a mix', () => {
		expect(analyzeTray(['$WPE가', '$WPE나'])).toMatchObject({ level: -1, event: true, malformed: false });
		expect(analyzeTray(['$WPE가', '$WPC나']).malformed).toBe(true);
		expect(analyzeTray(['$WPC가', '$WPE나']).malformed).toBe(true);
	});
	it('is empty for an empty tray', () => {
		expect(analyzeTray([])).toEqual({ word: '', level: 0, event: false, malformed: false });
	});
});

describe('addError', () => {
	it('stops at six pieces (435) and at what I own (434)', () => {
		expect(addError(Array(MAX_TRAY).fill('$WPC가'), '$WPC나', 9)).toBe(435);
		expect(addError(['$WPC가'], '$WPC가', 1)).toBe(434);
		expect(addError(['$WPC가'], '$WPC가', 2)).toBe(null);
	});
});

describe('ownedPieces', () => {
	const e = (id, count = 1, expire) => ({ item: { _id: id }, count, expire });
	const list = [e('$WPC다'), e('boxB4'), e('$WPA가'), e('$WPB나'), e('$WPC가', 0), e('$WPC라', 1, 1000)];
	it('keeps usable pieces only, best first', () => {
		expect(ownedPieces(list, 'high', 5_000_000).map((x) => x.item._id)).toEqual(['$WPA가', '$WPB나', '$WPC다']);
	});
	it('can put the common ones first, or order by letter', () => {
		expect(ownedPieces(list, 'low', 5_000_000).map((x) => x.item._id)[0]).toBe('$WPC다');
		expect(ownedPieces(list, 'name', 5_000_000).map((x) => x.item._id)).toEqual(['$WPA가', '$WPB나', '$WPC다']);
	});
	it('skips a malformed piece (more than one character)', () => {
		expect(ownedPieces([e('$WPC\\\\uaf5d'), e('$WPC가')], 'high', 5_000_000).map((x) => x.item._id)).toEqual(['$WPC가']);
	});
	it('knows a piece when it sees one', () => {
		expect(isPiece('$WPC가')).toBe(true);
		expect(isPiece('boxB4')).toBe(false);
	});
});

describe('boxGains', () => {
	it('lists what went up', () => {
		expect(boxGains({ a: { value: 1 }, b: { value: 2 } }, { a: { value: 3 }, b: { value: 1 }, c: { value: 1 } })).toEqual([{ key: 'a', q: 2 }, { key: 'c', q: 1 }]);
	});
});

describe('rewardCount', () => {
	it('shows a range for a fractional rate', () => {
		expect(rewardCount(1)).toBe('x1');
		expect(rewardCount(2.4)).toBe('x2-3');
		expect(rewardCount(0.4)).toBe('x0-1');
		expect(rewardCount(1.2)).toBe('x1-2');
	});
});
