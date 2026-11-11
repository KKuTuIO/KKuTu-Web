import { describe, expect, it } from 'vitest';
import { buildResultView, TEAM_COLORS } from './resultModel.js';

const user = (score, extra = {}) => ({ game: { score }, ...extra });
const row = (id, team, extra = {}) => ({ id, rank: 0, score: 0, team, ...extra });

describe('buildResultView, a game with teams', () => {
	// D (4): me 1524 + 411, A (1): 804 + 736, C (3): 600 + 502, B (2): 400 + 333
	const ids = { me: [4, 1524], d2: [4, 411], a1: [1, 804], a2: [1, 736], c1: [3, 600], c2: [3, 502], b1: [2, 400], b2: [2, 333] };
	const rows = Object.entries(ids).map(([id, [team]]) => row(id, team, { me: id === 'me', score: 99999 })); // a row's own score is the team's, not used here
	const users = Object.fromEntries(Object.entries(ids).map(([id, [, score]]) => [id, user(score)]));
	const v = buildResultView({ rows, users, meId: 'me', played: [] });

	it('sums each team from its players and ranks them', () => {
		expect(v.layout).toBe('teams');
		expect(v.teams.map((t) => [t.letter, t.score, t.rank])).toEqual([['D', 1935, 1], ['A', 1540, 2], ['C', 1102, 3], ['B', 733, 4]]);
		expect(v.teams.map((t) => t.color)).toEqual([TEAM_COLORS[4], TEAM_COLORS[1], TEAM_COLORS[3], TEAM_COLORS[2]]);
	});
	it('gives the teams widths that follow their points and add up to the whole bar', () => {
		expect(v.teams.reduce((n, t) => n + t.share, 0)).toBeCloseTo(1);
		expect(v.teams[0].share).toBeGreaterThan(v.teams[1].share);
		expect(v.teams[0].share / v.teams[1].share).toBeCloseTo(1935 / 1540, 1);
	});
	it('knows my team, the outcome and my own score (not the team\'s)', () => {
		expect(v.myTeam).toMatchObject({ letter: 'D', rank: 1, mine: true });
		expect(v.outcome).toBe('win');
		expect(v.hero).toEqual({ score: 1524, color: TEAM_COLORS[4] });
		expect(v.me).toMatchObject({ id: 'me', rank: 1, score: 1524 });
	});
	it('lists the players by their own scores', () => {
		expect(v.players.map((p) => [p.id, p.rank, p.letter])).toEqual([['me', 1, 'D'], ['a1', 2, 'A'], ['a2', 3, 'A'], ['c1', 4, 'C'], ['c2', 5, 'C'], ['d2', 6, 'D'], ['b1', 7, 'B'], ['b2', 8, 'B']]);
	});
	it('says lose when my team did not win', () => {
		const lost = buildResultView({ rows, users: { ...users, me: user(10) }, meId: 'me' });
		expect(lost.outcome).toBe('lose');
	});
	it('keeps a small team wide enough to read', () => {
		const tiny = buildResultView({ rows: [row('a', 1, { me: true }), row('b', 2)], users: { a: user(1000), b: user(1) }, meId: 'a' });
		expect(tiny.teams[1].share).toBeGreaterThanOrEqual(0.08);
	});
});

describe('buildResultView, no teams', () => {
	it('is a duel for two players, with the gap between them', () => {
		const v = buildResultView({ rows: [row('me', 0, { me: true, score: 1524 }), row('x', 0, { score: 804 })], users: {}, meId: 'me' });
		expect(v.layout).toBe('duel');
		expect(v.teams).toEqual([]);
		expect(v.opponent.id).toBe('x');
		expect(v.outcome).toBe('win');
		expect(v.gap).toMatchObject({ diff: 720 });
		expect(v.gap.share).toBeCloseTo(1524 / 2328);
		expect(v.hero.score).toBe(1524);
	});
	it('says lose when the other one scored more', () => {
		const v = buildResultView({ rows: [row('me', 0, { me: true, score: 10 }), row('x', 0, { score: 30 })], meId: 'me' });
		expect(v.outcome).toBe('lose');
	});
	it('is a list for three or more, with ties sharing a place and no win or loss', () => {
		const v = buildResultView({ rows: [row('a', 0, { score: 30 }), row('me', 0, { me: true, score: 30 }), row('c', 0, { score: 20 })], meId: 'me' });
		expect(v.layout).toBe('list');
		expect(v.outcome).toBeNull();
		expect(v.players.map((p) => p.rank)).toEqual([1, 1, 3]);
	});
	it('has no one to show when I am not in it (a spectator)', () => {
		const v = buildResultView({ rows: [row('a', 0, { score: 5 }), row('b', 0, { score: 4 })], meId: 'me' });
		expect(v.me).toBeNull();
		expect(v.hero.score).toBe(0);
		expect(v.words.count).toBe(0);
	});
});

describe('buildResultView, my words', () => {
	const played = [
		{ id: 'me', word: '가방', score: 12 }, { id: 'x', word: '고슴도치', score: 44 }, { id: 'me', word: '방아쇠', score: 22 }, { id: 'me', word: '칠면조', score: 61 },
		{ id: 'me', word: '쇠고기', score: 19 }, { id: 'me', word: '기러기', score: 17 }, { id: 'me', word: '기차표', score: 20 }, { id: 'me', word: '표범', score: 9 }
	];
	const v = buildResultView({ rows: [row('me', 0, { me: true, score: 200 }), row('x', 0, { score: 100 })], users: {}, meId: 'me', played });
	it('counts them, finds the best and lists the top few by points', () => {
		expect(v.words.count).toBe(7);
		expect(v.words.best).toMatchObject({ word: '칠면조', score: 61 });
		expect(v.words.top.map((w) => w.word)).toEqual(['칠면조', '방아쇠', '기차표', '쇠고기', '기러기']);
		expect(v.words.more).toBe(2);
		expect(v.opponentBest).toMatchObject({ word: '고슴도치' });
	});
	it('shows the solved questions or the speed when no words were played', () => {
		const rows = [row('me', 0, { me: true, score: 5 }), row('x', 0, { score: 4 })];
		expect(buildResultView({ rows, meId: 'me', quiz: { solved: { me: 314 }, kpm: { me: 52 } } }).stat).toEqual({ kind: 'solved', value: 314 });
		expect(buildResultView({ rows, meId: 'me', quiz: { kpm: { me: 52 } } }).stat).toEqual({ kind: 'speed', value: 52 });
		expect(buildResultView({ rows, meId: 'me', quiz: {}, played }).stat).toBeNull();
	});

describe('buildResultView, my best move', () => {
	const rows = [row('me', 0, { me: true, score: 200 }), row('x', 0, { score: 100 })];
	const w = (id, word, score, hanbang = null) => ({ id, word, score, hanbang });
	it('is the dead-end word with the most points, not the word with the most points', () => {
		const v = buildResultView({ rows, meId: 'me', played: [w('me', '칠면조', 61), w('me', '이리듐', 38, [2, 3]), w('me', '소듐', 30, [1, 2]), w('x', '고슴도치', 44, [3, 4])] });
		expect(v.words.kind).toBe('deadend');
		expect(v.words.best).toMatchObject({ word: '이리듐', score: 38, hanbang: [2, 3] });
		expect(v.opponentBest).toMatchObject({ word: '고슴도치', kind: 'deadend' });
	});
	it('falls back to the word with the most points when none was a dead end, and says so', () => {
		const v = buildResultView({ rows, meId: 'me', played: [w('me', '칠면조', 61), w('me', '가방', 12)] });
		expect(v.words.kind).toBe('top');
		expect(v.words.best.word).toBe('칠면조');
	});
});
});
