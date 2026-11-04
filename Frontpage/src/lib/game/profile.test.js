import { describe, expect, it } from 'vitest';
import { buildModeStats, historyOutcome, timeAgo, winRate, recommendedMode, needsWorkMode } from './profile.js';

describe('profile numbers', () => {
	it('merges the game record with the record service, newest effort first', () => {
		const rows = buildModeStats(
			{ KSH: [10, 4, 900, 3_600_000, 3], KKT: [0, 0, 0, 0, 0], ESH: [2, 0, 50, 60_000, 2] },
			[{ modeName: 'KSH', acceptedWords: 120, games: 99, wins: 99, exp: 1, playTime: 1 }, { modeName: 'CSQ', games: 5, wins: 1, exp: 70, playTime: 600_000, acceptedWords: 9 }]
		);
		expect(rows.map((r) => r.key)).toEqual(['KSH', 'CSQ', 'ESH']);
		const ksh = rows[0];
		expect(ksh).toMatchObject({ games: 10, wins: 4, losses: 3, halves: 3, acceptedWords: 120, hours: 1 }); // the game's own counters win over the service's
		expect(ksh.winRate).toBe(55); // (4 + 3 * 0.5) / 10
		expect(rows[1]).toMatchObject({ games: 5, wins: 1, acceptedWords: 9 }); // a mode only the service knows
	});
	it('rates draws as half a win', () => {
		expect(winRate(1, 0)).toBe(0);
		expect(winRate(2, 4, 2)).toBe(75);
	});
	it('tells how a game went', () => {
		expect(historyOutcome({ won: true, placement: 1, playerCount: 4 })).toBe('win');
		expect(historyOutcome({ won: false, placement: 4, playerCount: 4 })).toBe('loss');
		expect(historyOutcome({ won: false, placement: 2, playerCount: 4 })).toBe('half');
	});
	it('says how long ago', () => {
		const now = 1_000_000_000_000;
		expect(timeAgo(now - 5_000, now)).toBe('5초 전');
		expect(timeAgo(now - 3 * 3600_000, now)).toBe('3시간 전');
		expect(timeAgo(now - 70 * 86400_000, now)).toBe('2개월 전');
		expect(timeAgo(0, now)).toBe('-');
	});
});

describe('insights of the records page', () => {
	const m = (key, games, winRate) => ({ key, games, winRate });
	it('recommends the best mode with 10+ games, else the first', () => {
		expect(recommendedMode([m('A', 50, 40), m('B', 20, 60), m('C', 5, 100)]).key).toBe('B');
		expect(recommendedMode([m('A', 5, 40), m('B', 2, 60)]).key).toBe('A');
		expect(recommendedMode([])).toBeNull();
	});
	it('names the weakest mode with 10+ games', () => {
		expect(needsWorkMode([m('A', 50, 40), m('B', 20, 60), m('C', 5, 1)]).key).toBe('A');
		expect(needsWorkMode([m('A', 9, 40)])).toBeNull();
	});
});
