import { describe, it, expect } from 'vitest';
import { summarize, inputOutcome, clock } from './replayPayload.js';

// a real payload from the dev database (8 players, 1 round of 쿵쿵따)
const sample = {
	g: '8d5557ab', v: 1, s: 1793687554537, e: 1793687578710, d: 24173, c: 1,
	rm: [131, 3, 'KSH', 'Classic', 'ko', 1, 30, '초보도 환영해요', '서멀', [], [], 0],
	p: [
		['guest__a', '손님3594', '', 1, 'J', 0, 0, 0, '손님3594', '', 'https://x/guest.png', []],
		['131738466679', '131738466679', '', 3, 'J', 0, 1, 0, '', '', '', []]
	],
	w: ['서울', '울트라마이크로미터', '터키그리스전쟁', '쟁그랑쟁그랑'],
	i: [[0, 0, 2055, 6469, 1, 0, 0], [1, 1, 2167, 9738, 1, 1, 1], [0, 2, 1704, 12544, 1, 2, 3], [1, 3, 1585, 14997, 1, 3, 4]],
	rs: [[0, 0, 16, 1, 3, 1, 24169, 0]],
	wn: [0],
	x: ['CA,16,0', 'OUT,eyJzIjoiYWNjZXB0ZWQiLCJyIjoiQUNDRVBURUQiLCJkIjoxNiwibCI6MiwiYiI6MCwidCI6bnVsbCwicCI6WyJjaGFpbiIsMSwwLDBdfQ', 'CA,39,0', 'CR,409,1', 'CTO,0']
};

describe('summarize', () => {
	const s = summarize(sample);
	it('reads the room, the players and who won', () => {
		expect(s.room).toMatchObject({ no: 131, modeKey: 'KSH', rule: 'Classic', lang: 'ko', rounds: 1, time: 30, title: '초보도 환영해요', practice: false });
		expect(s.players.map((p) => [p.name, p.robot])).toEqual([['손님3594', false], ['131738466679', true]]);
		expect(s.results).toHaveLength(1);
		expect(s.results[0]).toMatchObject({ player: 0, rank: 1, score: 16, exp: 3, won: true }); // the payload's rank 0 is first place
		expect(s.duration).toBe(24173);
	});
	it('lists every word in order with its player and time', () => {
		expect(s.inputs.map((i) => [s.players[i.player].name, i.word, i.at])).toEqual([
			['손님3594', '서울', 6469],
			['131738466679', '울트라마이크로미터', 9738],
			['손님3594', '터키그리스전쟁', 12544],
			['131738466679', '쟁그랑쟁그랑', 14997]
		]);
		expect(s.rounds).toEqual([1]);
	});
	it('knows whether a word counted', () => {
		expect(s.inputs[0]).toMatchObject({ ok: true, score: 16 });
		expect(s.inputs[1]).toMatchObject({ ok: true, score: 16 }); // from the OUT outcome
		expect(s.inputs[2]).toMatchObject({ ok: false, reason: '409' });
		expect(s.inputs[3].ok).toBe(null); // a mode event, not a verdict
	});
	it('takes timing and winners from the game record when the payload has none (the public one)', () => {
		const { g, s: _s, e, d, wn, ...pub } = sample;
		const out = summarize(pub, { gameId: g, startedAt: 111, durationMs: 222, winnerIds: ['guest__a'] });
		expect(out).toMatchObject({ id: g, start: 111, duration: 222 });
		expect(out.results[0].won).toBe(true);
	});
	it('is null for what is not a payload', () => {
		expect(summarize(null)).toBe(null);
		expect(summarize({ rm: 1 })).toBe(null);
	});
});

describe('helpers', () => {
	it('inputOutcome', () => {
		expect(inputOutcome('CA,12,3')).toMatchObject({ ok: true, score: 12, bonus: 3 });
		expect(inputOutcome('CR,404,2')).toMatchObject({ ok: false, reason: '404' });
		expect(inputOutcome('TPM,300').ok).toBe(null);
		expect(inputOutcome(undefined).ok).toBe(null);
	});
	it('clock', () => {
		expect(clock(0)).toBe('0:00');
		expect(clock(65000)).toBe('1:05');
		expect(clock(3723000)).toBe('1:02:03');
	});
});
