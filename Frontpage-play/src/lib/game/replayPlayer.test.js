import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createReplay, replayBase, REPLAY_ROOM } from './replayPlayer.svelte.js';
import { startRecording, recordPacket } from './replayRec.js';
import { applyPacket, initialState } from './protocol.js';

const meta = () => {
	const m = initialState().meta;
	m.modes = ['ESH', 'KRH', 'KSH', 'KKT'];
	m.rules = { KKT: { rule: 'Classic', lang: 'ko', opts: [], time: 1 } };
	m.labels = { 'game.mode.KKT.name': '한국어 쿵쿵따' };
	return m;
};
const live = () => {
	const s = initialState();
	s.meta = meta();
	s.id = 'me';
	s.users.me = { id: 'me', name: '나', tag: '', score: 100, equip: {}, game: { team: 0 } };
	s.users.other = { id: 'other', name: '너', tag: '', score: 50, equip: {}, game: { team: 0 } };
	s.rooms[5] = { no: 5, title: '방', time: 60, rounds: 1, modeIndex: 3, limit: 8, opts: {}, injpick: [], players: ['me', 'other'], game: { seq: ['me', 'other'], title: ['가'] } };
	s.myRoom = 5;
	return s;
};
const recording = () => {
	const s = live();
	const rec = startRecording(s, 0);
	const evs = [
		{ type: 'starting', mode: 3 },
		{ type: 'roundReady', round: 1, char: '가' },
		{ type: 'turnStart', turn: 0, char: '가', speed: 1, turnTime: 10000, roundTime: 60000, seq: ['me', 'other'] },
		{ type: 'chat', value: '안녕', profile: { id: 'other', title: '너' } }
	];
	evs.forEach((p, i) => recordPacket(rec, p, i * 1000));
	return rec;
};

describe('replayBase', () => {
	it('rebuilds my room and its players from the recording', () => {
		const base = replayBase(recording(), meta());
		expect(base.myRoom).toBe(REPLAY_ROOM);
		expect(base.id).toBe('me');
		expect(Object.keys(base.users).sort()).toEqual(['me', 'other']);
		expect(base.rooms[REPLAY_ROOM]).toMatchObject({ title: '방', rounds: 1, modeKey: 'KKT', playing: true });
	});
});

describe('playing a recording', () => {
	let now;
	beforeEach(() => {
		vi.useFakeTimers();
		now = 0;
	});
	afterEach(() => vi.useRealTimers());
	const clock = () => now;
	const advance = (ms) => {
		now += ms;
		vi.advanceTimersByTime(ms);
	};

	it('applies the packets at their times and reaches the end', () => {
		const r = createReplay(recording(), { meta: meta(), shop: {}, clock });
		expect(r.duration).toBe(3000);
		expect(r.chapters).toEqual([{ at: 1000, round: 1 }]);
		r.play();
		expect(r.store.game?.phase).toBe('loading'); // `starting` is at time 0
		advance(1000);
		expect(r.store.game?.round).toBe(1);
		expect(r.store.game.phase).toBe('ready');
		advance(1000);
		expect(r.store.game.phase).toBe('turn');
		expect(r.store.net.chat).toHaveLength(0);
		advance(1500);
		expect(r.store.net.chat.map((c) => c.msg)).toEqual(['안녕']);
		expect(r.view.ended).toBe(true);
		expect(r.view.playing).toBe(false);
	});
	it('pauses and goes on from where it was', () => {
		const r = createReplay(recording(), { meta: meta(), shop: {}, clock });
		r.play();
		advance(1500);
		r.pause();
		const at = r.view.position;
		advance(5000);
		expect(r.view.position).toBe(at);
		expect(r.store.game.phase).toBe('ready');
		r.play();
		advance(600);
		expect(r.store.game.phase).toBe('turn');
	});
	it('seeks both ways by rebuilding the game up to that point', () => {
		const r = createReplay(recording(), { meta: meta(), shop: {}, clock });
		r.seek(2500);
		expect(r.store.game.phase).toBe('turn');
		expect(r.view.position).toBe(2500);
		r.seek(500);
		expect(r.store.game.phase).toBe('loading'); // before `roundReady` the game has only just started
		r.seek(3000);
		expect(r.view.ended).toBe(true);
		expect(r.store.net.chat).toHaveLength(1);
	});
	it('playing again after the end starts over', () => {
		const r = createReplay(recording(), { meta: meta(), shop: {}, clock });
		r.play();
		advance(4000);
		expect(r.view.ended).toBe(true);
		r.play();
		expect(r.view.ended).toBe(false);
		expect(r.view.position).toBe(0);
	});
	it('runs at the chosen speed, and the board clock follows it', () => {
		const r = createReplay(recording(), { meta: meta(), shop: {}, clock, epoch: 1_000_000 });
		r.setSpeed(2);
		r.play();
		advance(1000); // 2000 ms of the recording
		expect(r.store.game.phase).toBe('turn');
		expect(r.store.now()).toBe(1_000_000 + 2000);
		r.setSpeed(0.5);
		advance(1000);
		expect(r.store.now()).toBe(1_000_000 + 2500);
		r.pause();
		advance(5000);
		expect(r.store.now()).toBe(1_000_000 + 2500); // paused: the board's clocks stand still
	});
	it('applies each packet at its own time, so a turn started at 2 s counts from there', () => {
		const r = createReplay(recording(), { meta: meta(), shop: {}, clock, epoch: 1_000_000 });
		r.seek(2500);
		expect(r.store.game.turnAt).toBe(1_000_000 + 2000);
	});
	it('reads a legacy recording: robots from game.robots, round titles as a string, players known by id', () => {
		const rec = recording();
		rec.players = [{ id: '46227a6d-a0e8-43c1-a152', score: 0, data: { score: 5 }, equip: {}, title: '#46227a6d-a0e8-43c1-a152' }, { id: '7837', robot: true, data: { score: 0 }, title: '#7837' }];
		rec.me = '46227a6d-a0e8-43c1-a152';
		rec.game = { seq: ['7837', '46227a6d-a0e8-43c1-a152'], title: '카르', robots: [{ id: '7837', robot: true, level: 3 }] };
		rec.readies = { '46227a6d-a0e8-43c1-a152': { r: false, f: 'J', t: 0 } };
		const base = replayBase(rec, meta());
		expect(base.users['7837'].robot).toBe(true);
		expect(base.users['7837'].level).toBe(3);
		expect(base.users['46227a6d-a0e8-43c1-a152'].name).toBe('46227a6d');
		expect(base.rooms[REPLAY_ROOM].game.title).toEqual(['카', '르']);
	});
	it('offers the game screen a store that cannot play', () => {
		const exit = vi.fn();
		const r = createReplay(recording(), { meta: meta(), shop: {}, onexit: exit, clock });
		expect(r.store.myTurn).toBe(false);
		expect(r.store.submitWord('가나')).toBe(false);
		expect(r.store.currentRoom.title).toBe('방');
		r.store.leaveGame();
		expect(exit).toHaveBeenCalled();
	});
});

void applyPacket;
