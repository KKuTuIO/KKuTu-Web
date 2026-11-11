import { describe, it, expect } from 'vitest';
import { startRecording, recordPacket, createRecorder, REC_MAX_EVENTS } from './replayRec.js';
import { checkRecording } from './kkio.js';

const state = () => ({
	id: 'me',
	myRoom: 7,
	game: null,
	rooms: { 7: { title: '방', time: 60, rounds: 3, modeIndex: 2, limit: 8, opts: { man: true }, injpick: [], players: ['me', 'bot'], game: { seq: ['me', 'bot'], title: ['가', '나', '다'] } } },
	users: {
		me: { id: 'me', name: '나', tag: '12', score: 500, equip: { Mhead: 'hat' }, game: { team: 0 } },
		bot: { id: 'bot', name: '로봇', tag: '', score: 0, equip: {}, robot: true, level: 2, game: { team: 1 } }
	}
});

describe('replay recording', () => {
	it('starts from my room in the legacy shape, with players and who is a robot', () => {
		const r = startRecording(state(), 1000);
		expect(r).toMatchObject({ me: 'me', time: 1000, title: '방', roundTime: 60, round: 3, mode: 2, limit: 8, opts: { man: true } });
		expect(r.players[0]).toMatchObject({ id: 'me', title: '나#12', data: { score: 500 }, equip: { Mhead: 'hat' } });
		expect(r.players[1]).toMatchObject({ id: 'bot', robot: true, level: 2 });
		expect(r.readies.bot).toEqual({ t: 1 });
		expect(r.game.seq).toEqual(['me', 'bot']);
	});
	it('no room, nothing to record', () => {
		expect(startRecording({ ...state(), myRoom: null })).toBe(null);
	});
	it('keeps game packets with their time and only who a profile is', () => {
		const r = startRecording(state(), 1000);
		expect(recordPacket(r, { type: 'turnEnd', value: 'x', profile: { id: 'me', title: '나', image: 'big', data: { a: 1 } }, user: { id: 'me' } }, 1500)).toBe(true);
		expect(r.events[0]).toEqual({ time: 500, data: { type: 'turnEnd', value: 'x', profile: { id: 'me', title: '나' } } });
	});
	it('leaves out the lobby, whispers and notices', () => {
		const r = startRecording(state(), 0);
		for (const p of [{ type: 'room' }, { type: 'obtain' }, { type: 'friends' }, { type: 'chat', from: 'x', value: 'psst' }, { type: 'chat', notice: true, code: 1 }, { type: 'turnError', code: 1 }]) expect(recordPacket(r, p, 1)).toBe(false);
		expect(recordPacket(r, { type: 'chat', value: 'hi', profile: { id: 'a', title: 'A' } }, 1)).toBe(true);
		expect(r.events).toHaveLength(1);
	});
	it('stops at the cap', () => {
		const r = startRecording(state(), 0);
		for (let i = 0; i < REC_MAX_EVENTS + 5; i++) recordPacket(r, { type: 'turnStart' }, i);
		expect(r.events).toHaveLength(REC_MAX_EVENTS);
	});
});

describe('the recorder over a whole game', () => {
	it('starts at `starting`, is done at the final result, and what it gives passes the file checks', () => {
		let t = 1000;
		const rec = createRecorder(() => (t += 100));
		const s = state();
		expect(rec.recording).toBe(null);
		rec.feed(s, { type: 'turnStart' }); // before `starting`: ignored
		expect(rec.recording).toBe(null);
		expect(rec.feed(s, { type: 'starting' })).toBe(true);
		rec.feed(s, { type: 'roundReady', round: 1 });
		rec.feed(s, { type: 'turnStart', turn: 0 });
		expect(rec.done).toBe(false);
		s.game = { result: { rows: [] } };
		expect(rec.feed(s, { type: 'roundEnd', result: [] })).toBe(true);
		expect(rec.done).toBe(true);
		rec.feed(s, { type: 'chat', value: 'gg', profile: { id: 'me', title: '나' } }); // after the end: not part of it
		const out = rec.recording;
		expect(out.events.map((e) => e.data.type)).toEqual(['starting', 'roundReady', 'turnStart', 'roundEnd']);
		expect(out.events.every((e, i, a) => !i || e.time >= a[i - 1].time)).toBe(true);
		expect(() => checkRecording(out)).not.toThrow();
	});
	it('a new game replaces the old recording; abandoning one that did not end drops it', () => {
		const rec = createRecorder();
		const s = state();
		rec.feed(s, { type: 'starting' });
		rec.feed(s, { type: 'turnStart' });
		rec.abandon();
		expect(rec.recording).toBe(null);
	});
});
