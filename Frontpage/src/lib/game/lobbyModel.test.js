import { describe, expect, it } from 'vitest';
import { exactRoomNo, filterRooms, groupModes, prettyTime } from './lobbyModel.js';

const room = (no, o = {}) => ({ no, title: `방${no}`, locked: false, mode: '한국어 끝말잇기', tags: [], rounds: 3, time: 90, cur: 1, max: 8, playing: false, ...o });

describe('lobbyModel', () => {
	const rooms = [room(1), room(2, { playing: true }), room(3, { locked: true }), room(4, { cur: 8 })];
	it('sorts newest first by default', () => {
		expect(filterRooms(rooms).map((r) => r.no)).toEqual([4, 3, 2, 1]);
	});
	it('applies filters', () => {
		expect(filterRooms(rooms, { waitingOnly: true }).map((r) => r.no)).toEqual([4, 3, 1]);
		expect(filterRooms(rooms, { hasSeat: true, hideLocked: true }).map((r) => r.no)).toEqual([2, 1]);
	});
	it('searches by number or title', () => {
		expect(filterRooms(rooms, { query: '3' }).map((r) => r.no)).toEqual([3]);
	});
	it('formats play time', () => {
		expect(prettyTime(45_000)).toBe('45초');
		expect(prettyTime(3_720_000)).toBe('1시간 2분');
	});
	it('detects exact room numbers', () => {
		expect(exactRoomNo(' 321 ')).toBe(321);
		expect(exactRoomNo('32')).toBeNull();
		expect(exactRoomNo('abc')).toBeNull();
	});
});

describe('groupModes', () => {
	it('groups by language with math as "other", in legacy order, keeping server indexes', () => {
		const modes = ['ESH', 'KKT', 'KMT', 'KSH', 'XYZ'];
		const rules = { ESH: { lang: 'en' }, KKT: { lang: 'ko' }, KMT: { lang: 'ko', rule: 'Mathquiz' }, KSH: { lang: 'ko' }, XYZ: { lang: 'ko' } };
		const g = groupModes(modes, rules);
		expect(g.map((x) => x.id)).toEqual(['ko', 'en', 'other']);
		expect(g[0].modes.map((m) => m.key)).toEqual(['KSH', 'KKT', 'XYZ']); // legacy order, unknown last
		expect(g[0].modes.find((m) => m.key === 'KKT').index).toBe(1);
		expect(groupModes(['ESH'], { ESH: { lang: 'en' } }).map((x) => x.id)).toEqual(['en']);
	});
});
