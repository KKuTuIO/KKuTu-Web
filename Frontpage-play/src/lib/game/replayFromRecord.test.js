import { describe, expect, it } from 'vitest';
import { replaySupport, recFromRecord, turnSpeed } from './replayFromRecord.js';
import { checkRecording } from './kkio.js';
import { createReplay } from './replayPlayer.svelte.js';

// two players, 2 rounds of 끝말잇기 (60 s): round 1: three words and a timeout, round 2: one word and a timeout
const payload = {
	rm: [7, 3, 'KSH', 'Classic', 'ko', 2, 60, '시험방', ['가', '나'], ['man'], [], 0],
	p: [
		['a', '가나', '', 3, 'J', 0, 0, 0, '', '', '', ['gloves', 'glv1']],
		['b', '다라', '', 1, 'J', 0, 1, 0, '', '', '', []]
	],
	w: ['가방', '방아', '아이', '나비', '비누'],
	x: ['CA,20,5', 'CA,15,0', 'CA,12,0', 'CA,18,0', 'CA,9,0', 'CTO,-10', 'CR,NFD,404'],
	i: [
		// [player, word, turnMs, gameMs, round, turn, extra]
		[0, 0, 3000, 6000, 1, 0, 0],
		[1, 1, 2000, 9500, 1, 1, 1],
		[1, 4, 500, 9800, 1, 1, 6], // a refused word of the player on turn: not a turn of its own
		[0, 2, 2500, 14000, 1, 0, 2],
		[1, 3, 4000, 40000, 2, 1, 3]
	],
	mv: [['CTO', 1, 1, 30000, 5], ['CTO', 0, 2, 70000, 5]], // b runs out of time in round 1, a in round 2
	rs: [[0, 0, 47, 1, 120, 30, 60000, 0], [1, 1, 33, 1, 60, 10, 60000, 0]],
	wn: ['a'],
	s: 1700000000000, e: 1700000100000, d: 100000
};
const detail = { gameId: 'g1', replayView: { payload } };
const optionDefs = { man: { name: 'Manner' } };

describe('replaySupport', () => {
	it('accepts the games it can rebuild and refuses the ones whose puzzle, drawing or letters are not stored', () => {
		expect(replaySupport(detail)).toEqual({ supported: true, family: 'chain' });
		expect(replaySupport({ replayView: { payload: { ...payload, rm: [7, 10, 'KDA', 'Daneo', 'ko', 2, 60] } } })).toEqual({ supported: true, family: 'themed' });
		expect(replaySupport({ replayView: { payload: { ...payload, rm: [7, 9, 'HUN', 'Hunmin', 'ko', 2, 60] } } })).toEqual({ supported: true, family: 'themed' });
		for (const [mode, rule] of [['KWS', 'Wordstack'], ['KCW', 'Crossword'], ['KDG', 'Drawing'], ['CSQ', 'Jaqwi'], ['KTY', 'Typing'], ['KMT', 'Mathquiz'], ['KLT', 'Linkland']]) {
			expect(replaySupport({ replayView: { payload: { ...payload, rm: [7, 3, mode, rule, 'ko', 2, 60] } } }).supported, rule).toBe(false);
		}
		expect(replaySupport({ replayView: { payload: { ...payload, rm: [7, 10, 'KDA', 'Classic', 'ko', 2, 60] } } }).supported).toBe(false); // a mode with the wrong rule
		expect(replaySupport({ replayView: { payload: { ...payload, i: [] } } }).supported).toBe(false);
		expect(replaySupport(null).supported).toBe(false);
		expect(recFromRecord({ replayView: { payload: { ...payload, rm: [7, 3, 'KWS', 'Wordstack', 'ko', 2, 60] } } })).toBeNull();
	});
});

describe('turnSpeed', () => {
	it('is the server clock: shorter round time left, faster turn', () => {
		expect(turnSpeed(60000)).toBe(3);
		expect(turnSpeed(4000)).toBe(10);
		expect(turnSpeed(10000)).toBe(9);
		expect(turnSpeed(100000)).toBe(0);
	});
});

describe('recFromRecord', () => {
	const rec = recFromRecord(detail, { optionDefs, me: 'b' });
	const types = rec.events.map((e) => e.data.type);

	it('makes a recording the player accepts', () => {
		expect(() => checkRecording(rec)).not.toThrow();
		expect(rec.me).toBe('b');
		expect(rec).toMatchObject({ title: '시험방', mode: 3, round: 2, roundTime: 60, opts: { manner: true } });
		expect(rec.game.title).toEqual(['가', '나']);
		expect(rec.players[0]).toMatchObject({ id: 'a', equip: { gloves: 'glv1' } });
		expect(rec.players[1]).toMatchObject({ id: 'b', robot: true });
		expect(rec.players[0].data.score).toBeGreaterThan(rec.players[1].data.score); // level 3 against level 1
	});
	it('puts the game together in order: start, round letters, turns, result', () => {
		expect(types).toEqual([
			'starting',
			'roundReady', 'turnStart', 'turnEnd', 'turnStart', 'turnEnd', 'turnStart', 'turnEnd', 'turnStart', 'turnEnd', // round 1: three words, then the timeout
			'roundReady', 'turnStart', 'turnEnd', 'turnStart', 'turnEnd', // round 2: one word, then the timeout
			'roundEnd'
		]);
		const times = rec.events.map((e) => e.time);
		expect(times).toEqual([...times].sort((a, b) => a - b));
		expect(times.every((t) => t >= 0)).toBe(true);
	});
	it('chains the letters: the round letter first, then the last letter of the word before', () => {
		const starts = rec.events.map((e) => e.data).filter((d) => d.type === 'turnStart');
		expect(starts.map((d) => d.char)).toEqual(['가', '방', '아', '이', '나', '비']);
		expect(starts[0]).toMatchObject({ speed: 3, turnTime: 15000 - 1400 * 3, roundTime: 60000 });
		expect(starts[0].turn).toBe(0);
		expect(starts[1].turn).toBe(1);
	});
	it('says each word with its points, and a timeout with its penalty', () => {
		const ends = rec.events.map((e) => e.data).filter((d) => d.type === 'turnEnd');
		expect(ends.slice(0, 3).map((d) => [d.ok, d.value, d.score, d.bonus])).toEqual([[true, '가방', 20, 5], [true, '방아', 15, 0], [true, '아이', 12, 0]]);
		expect(ends[3]).toMatchObject({ ok: false, target: 'b', score: -10 });
		expect(ends.at(-1)).toMatchObject({ ok: false, target: 'a' });
	});
	it('ends with the result: ranks from the top, what was earned, the level the players reach', () => {
		const end = rec.events.at(-1).data;
		expect(end.type).toBe('roundEnd');
		expect(end.result.map((r) => [r.id, r.rank, r.score, r.reward.score, r.reward.money])).toEqual([['a', 0, 47, 120, 30], ['b', 1, 33, 60, 10]]);
		expect(end.users.a.data.score).toBeGreaterThan(rec.players[0].data.score);
	});
	it('plays on the game screen\'s player: the board shows the words and the final result', () => {
		const meta = { modes: ['EKT', 'ESH', 'KKT', 'KSH'], options: {}, labels: {}, rules: { KSH: { rule: 'Classic', lang: 'ko' } }, optionDefs };
		const replay = createReplay(rec, { meta, shop: {}, clock: () => 0 });
		replay.seek(replay.duration);
		const g = replay.store.net.game;
		expect(g.phase).toBe('ended');
		expect(g.result.rows.map((r) => r.id)).toEqual(['a', 'b']);
		expect(replay.store.net.users.a.score).toBeGreaterThan(replay.store.net.users.b.score);
		replay.dispose();
	});
});

describe('themed modes (단어 대결, 훈민정음)', () => {
	const themed = { ...payload, rm: [7, 10, 'KDA', 'Daneo', 'ko', 2, 60, '단어방', ['0', '0'], [], [], 0] };
	const rec = recFromRecord({ gameId: 'g2', replayView: { payload: themed } }, { me: 'a' });
	const datas = rec.events.map((e) => e.data);
	it('says the round\'s theme instead of a letter, and the turns carry none', () => {
		expect(datas.filter((d) => d.type === 'roundReady').map((d) => [d.round, d.theme, d.char])).toEqual([[1, '0', undefined], [2, '0', undefined]]);
		expect(datas.filter((d) => d.type === 'turnStart').every((d) => d.char === undefined && typeof d.turn === 'number')).toBe(true);
		expect(datas.filter((d) => d.type === 'turnEnd' && d.ok).map((d) => d.value)).toEqual(['가방', '방아', '아이', '나비']);
	});
	it('plays on the player to the result', () => {
		const meta = { modes: Array.from({ length: 11 }, (_, i) => (i === 10 ? 'KDA' : `M${i}`)), options: {}, labels: {}, rules: { KDA: { rule: 'Daneo', lang: 'ko' } }, optionDefs };
		const replay = createReplay(rec, { meta, shop: {}, clock: () => 0 });
		replay.seek(replay.duration);
		expect(replay.store.net.game.phase).toBe('ended');
		replay.dispose();
	});
});

describe('솎솎', () => {
	const out = (d, board = 12) => btoa(JSON.stringify({ s: 'accepted', r: 'ACCEPTED', d, l: 2, b: 0, t: null, p: ['sock', 0, board, 0] }));
	const sock = {
		rm: [9, 12, 'KSS', 'Sock', 'ko', 2, 60, '솎솎방', ['', ''], [], [], 0],
		p: payload.p,
		x: ['SOK,가방', `OUT,${out(8)}`, 'SOK,나비야', `OUT,${out(12)}`, 'TMO,e30=', 'SOK,바다', `OUT,${out(8)}`],
		i: [],
		// [type, player, round, time, extra]
		mv: [['OUT', 0, 1, 10000, 1], ['SOK', 0, 1, 10004, 0], ['OUT', 1, 1, 20000, 3], ['SOK', 1, 1, 20003, 2], ['TMO', 0, 1, 61000, 4], ['TMO', 1, 1, 61000, 4], ['OUT', 0, 2, 80000, 6], ['SOK', 0, 2, 80004, 5], ['TMO', 0, 2, 130000, 4]],
		rs: [[0, 0, 28, 1, 40, 5, 60000, 0], [1, 1, 12, 1, 20, 2, 60000, 0]],
		wn: ['a'], s: 1700000000000, e: 1700000200000, d: 200000
	};
	const rec = recFromRecord({ gameId: 'g3', replayView: { payload: sock } }, { me: 'a' });
	const datas = rec.events.map((e) => e.data);
	it('is supported when words were taken, and builds a board with the letters of those words', () => {
		expect(replaySupport({ replayView: { payload: sock } })).toEqual({ supported: true, family: 'sock' });
		const boards = datas.filter((d) => d.type === 'roundReady').map((d) => d.board);
		expect(boards).toHaveLength(2);
		expect(boards[0]).toHaveLength(12); // the size the outcome rows give
		for (const letter of '가방나비야') expect(boards[0]).toContain(letter);
		for (const letter of '바다') expect(boards[1]).toContain(letter);
	});
	it('takes each word with its points and closes each round when the server did', () => {
		const taken = datas.filter((d) => d.type === 'turnEnd' && d.value);
		expect(taken.map((d) => [d.target, d.value, d.score])).toEqual([['a', '가방', 8], ['b', '나비야', 12], ['a', '바다', 8]]);
		expect(datas.filter((d) => d.type === 'turnStart').map((d) => d.roundTime)).toEqual([60000, 60000]);
		const times = rec.events.map((e) => e.time);
		expect(times).toEqual([...times].sort((a, b) => a - b));
	});
	it('plays on the player: the words leave the board and the scores add up', () => {
		const meta = { modes: Array.from({ length: 13 }, (_, i) => (i === 12 ? 'KSS' : `M${i}`)), options: {}, labels: {}, rules: { KSS: { rule: 'Sock', lang: 'ko' } }, optionDefs };
		const replay = createReplay(rec, { meta, shop: {}, clock: () => 0 });
		replay.seek(replay.duration);
		const net = replay.store.net;
		expect(net.game.phase).toBe('ended');
		expect(net.users.a.game.score).toBe(16);
		expect(net.users.b.game.score).toBe(12);
		replay.dispose();
	});
});
