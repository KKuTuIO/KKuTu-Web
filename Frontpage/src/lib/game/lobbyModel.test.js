import { describe, expect, it } from 'vitest';
import { channelName, exactRoomNo, filterRooms, groupModes, prettyTime, recordRow, seatLayout } from './lobbyModel.js';

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
		expect(filterRooms(rooms, { joinableFirst: true }).map((r) => r.no)).toEqual([3, 1, 4, 2]); // joinable rooms first, then newest
		expect(filterRooms(rooms, { joinableFirst: true, sort: 'crowded' }).map((r) => r.no)).toEqual([3, 1, 4, 2]);
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

describe('seatLayout', () => {
	it('uses few big cards for small rooms and more columns for large ones', () => {
		const two = seatLayout(880, 380, 2);
		expect([two.cols, two.rows]).toEqual([2, 1]);
		const four = seatLayout(880, 380, 4);
		expect(four.cols * four.rows).toBeGreaterThanOrEqual(4);
		expect(four.rows).toBeLessThanOrEqual(2);
		const eight = seatLayout(880, 380, 8);
		expect(eight.cols * eight.rows).toBeGreaterThanOrEqual(8);
		expect(eight.cardW).toBeGreaterThanOrEqual(170);
	});
	it('always has room for every seat, and never shrinks cards below the minimum when avoidable', () => {
		for (const limit of [2, 3, 4, 6, 8, 10, 12, 16]) {
			const l = seatLayout(880, 420, limit);
			expect(l.cols * l.rows).toBeGreaterThanOrEqual(limit);
			expect(l.cardW).toBeGreaterThan(100);
		}
	});
	it('adapts to a narrow area', () => {
		const l = seatLayout(360, 600, 6);
		expect(l.cols).toBeLessThanOrEqual(2);
	});
});

describe('recordRow', () => {
	it('computes losses, draws and the win rate the way the legacy profile does', () => {
		expect(recordRow([10, 4, 900, 0, 5])).toEqual({ plays: 10, wins: 4, losses: 5, rate: 45, score: 900 }); // 1 draw counts half
		expect(recordRow([0, 0, 0])).toMatchObject({ plays: 0, rate: 0 });
		expect(recordRow([3, 1, 10, 0, 99]).losses).toBe(2); // losses can't exceed plays - wins
		expect(recordRow(undefined)).toMatchObject({ plays: 0, wins: 0 });
	});
});

describe('pingLevel', () => {
	it('grades the latency and says "unknown" before anything was measured', async () => {
		const { pingLevel } = await import('./lobbyModel.js');
		expect(pingLevel(null)).toBe('unknown');
		expect(pingLevel(undefined)).toBe('unknown');
		expect(pingLevel(NaN)).toBe('unknown');
		expect(pingLevel(0)).toBe('good');
		expect(pingLevel(100)).toBe('good');
		expect(pingLevel(101)).toBe('fair');
		expect(pingLevel(250)).toBe('fair');
		expect(pingLevel(251)).toBe('poor');
	});
});

describe('channelName', () => {
	it('is the channel by its name, and its number only when it has none', () => {
		const labels = { 'server.0': '감자(손님)', 'server.2': '다래' };
		expect(channelName(labels, 2)).toBe('다래');
		expect(channelName(labels, '0')).toBe('감자(손님)');
		expect(channelName(labels, 9)).toBe('9');
		expect(channelName(undefined, 1)).toBe('1');
	});
});
