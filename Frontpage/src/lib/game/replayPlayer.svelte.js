/**
 * Playing a recording (.kkio) on the real game screen. The recorded packets go through the same pure reducer (`applyPacket`) that the live
 * connection uses, into a state of their own, and `store` below is the small part of the lobby store that the game screen reads, so the
 * board, the players, the balloons and the result are the ones of a live game. Nothing is sent anywhere.
 * The board's clocks run on the replay's own time (`store.now`), so pausing, jumping and the speed keep the turn bars right.
 */
import { noTelemetry } from './telemetry.js';
import { initialState, applyPacket, normalizeUser, normalizeRoom } from './protocol.js';
import { REC_TYPES } from './replayRec.js';

export const REPLAY_ROOM = 1;
export const SPEEDS = [0.5, 1, 1.5, 2];

// (a nickname cannot start with '#': that would read as an empty name with a tag)
const clip = (id) => String(id).slice(0, 8);

/**
 * What the state is before the first packet: my room with its players, as the lobby would hold them when the game starts.
 * Reads recordings of this client and of the legacy one (robots listed in `game.robots`, round titles as one string, players known by "#id").
 */
export function replayBase(rec, meta) {
	const s = initialState();
	s.meta = meta;
	s.status = 'ready';
	s.id = rec.me;
	const robots = Array.isArray(rec.game?.robots) ? rec.game.robots : [];
	for (const p of rec.players) {
		const robot = !!p.robot;
		const title = String(p.title ?? p.id);
		s.users[p.id] = normalizeUser({
			id: p.id,
			profile: { title: title.startsWith('#') && title.length > 9 ? clip(title.slice(1)) : title },
			data: { score: p.data?.score ?? 0 },
			equip: p.equip ?? {},
			robot,
			level: p.level ?? robots.find((r) => r.id === p.id)?.level ?? 0,
			game: { ready: true, form: 'J', team: rec.readies?.[p.id]?.t ?? 0, score: 0 }
		});
	}
	const game = { ...(rec.game ?? {}) };
	if (typeof game.title === 'string') game.title = [...game.title]; // the legacy client kept the round letters as one string
	s.rooms[REPLAY_ROOM] = normalizeRoom(
		{ id: REPLAY_ROOM, title: rec.title ?? '', limit: rec.limit, mode: rec.mode, round: rec.round, time: rec.roundTime, opts: rec.opts ?? {}, injpick: rec.injpick ?? [], gaming: true, master: rec.me, players: rec.players.map((p) => ({ id: p.id })), game },
		meta
	);
	s.myRoom = REPLAY_ROOM;
	return s;
}

/**
 * @param {object} rec a checked recording
 * @param {{meta:object, shop:object, onexit?:()=>void, clock?:()=>number, epoch?:number, audio?:(net:object)=>{onPacket:(pkt:object)=>void, stopTurnSound:()=>void}}} o
 *   `audio`: makes the game's sounds (turn clock, effects, music) for what is applied while playing; not while jumping
 */
export function createReplay(rec, { meta, shop, onexit = () => {}, clock = () => performance.now(), epoch = Date.now(), audio = null }) {
	const events = rec.events.filter((e) => REC_TYPES.has(e.data.type)).sort((a, b) => a.time - b.time);
	const duration = events.at(-1)?.time ?? 0;
	const chapters = events.filter((e) => e.data.type === 'roundReady').map((e) => ({ at: e.time, round: Number(e.data.round) || 0 }));
	const net = $state(replayBase(rec, meta));
	const ui = $state({ gameCovered: false, exitReserved: false });
	const view = $state({ playing: false, position: 0, ended: false, speed: 1 });
	let idx = 0;
	let timer = 0;
	let from = 0; // the position when the clock last started
	let since = 0; // clock() then

	/** Where the recording is now (while playing it moves with the clock and the speed). */
	const position = () => (view.playing ? Math.min(duration, from + (clock() - since) * view.speed) : view.position);
	const sounds = audio?.(net) ?? null;
	const apply = (e, live = false) => {
		applyPacket(net, e.data, epoch + e.time);
		if (live) sounds?.onPacket(e.data);
	};

	function reset() {
		Object.assign(net, replayBase(rec, meta));
		idx = 0;
		view.position = 0;
		view.ended = false;
	}
	function tick() {
		clearTimeout(timer);
		if (!view.playing) return;
		const at = position();
		while (idx < events.length && events[idx].time <= at) apply(events[idx++], true);
		view.position = at;
		if (idx >= events.length) {
			sounds?.stopTurnSound();
			view.playing = false;
			view.ended = true;
			view.position = duration;
			return;
		}
		timer = setTimeout(tick, Math.max(0, Math.min((events[idx].time - at) / view.speed, 250))); // short steps: the clock on screen moves smoothly
	}
	function start(at) {
		view.playing = true;
		view.ended = false;
		from = at;
		since = clock();
		tick();
	}
	function play() {
		if (view.ended) reset();
		start(view.position);
	}
	function pause() {
		if (!view.playing) return;
		view.position = position();
		view.playing = false;
		clearTimeout(timer);
		sounds?.stopTurnSound();
	}
	/** Jump to `ms`: the game is rebuilt from its start up to there (the board shows what it showed then, its clocks included). */
	function seek(ms) {
		const to = Math.max(0, Math.min(duration, ms));
		const was = view.playing;
		clearTimeout(timer);
		view.playing = false;
		sounds?.stopTurnSound();
		reset();
		while (idx < events.length && events[idx].time <= to) apply(events[idx++]);
		view.position = to;
		view.ended = idx >= events.length && to >= duration;
		if (was && !view.ended) start(to);
	}
	function setSpeed(x) {
		const at = position();
		view.speed = SPEEDS.includes(x) ? x : 1;
		if (view.playing) start(at);
	}

	/** What the game screen, its header and the result read from the lobby store (and does with it). */
	const store = {
		net,
		ui,
		shop,
		replay: true, // the game screen shows it without the loading veil
		now: () => epoch + position(), // the clocks of the board
		speed: () => view.speed, // the board's own animations (the mouth) keep up with it
		hooks: { openProfile() {} },
		telemetry: noTelemetry,
		traceScope: () => null,
		conn: { send() {} },
		get game() {
			return net.game;
		},
		get currentRoom() {
			return net.rooms[net.myRoom];
		},
		get inGame() {
			return true;
		},
		get myTurn() {
			return false; // a replay is only watched
		},
		get typed() {
			return '';
		},
		get composing() {
			return false;
		},
		setTyped() {},
		submitWord: () => false,
		spaceSubmits: () => false,
		toggleExitReserve() {},
		replayReady: false,
		saveReplay: () => false,
		dressLocked: true,
		selectRound(n) {
			if (net.game) {
				net.game.selectedRound = n;
				net.game.sel = null;
			}
		},
		selectBar(sel) {
			if (net.game) net.game.sel = sel;
		},
		closeResult() {}, // closing the result leaves the final board on screen
		leaveGame: async () => onexit()
	};

	return {
		store,
		view,
		duration,
		chapters,
		events: events.length,
		play,
		pause,
		seek,
		setSpeed,
		position,
		dispose() {
			clearTimeout(timer);
			view.playing = false;
			sounds?.stopTurnSound();
		}
	};
}
