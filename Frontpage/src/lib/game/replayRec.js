/**
 * Recording a game for a `.kkio` file (the legacy "경기 저장"): from the `starting` packet to the final result, every packet that shapes the
 * board is kept with its time, in the legacy recording shape (see kkio.js). Pure: the connection feeds it what it receives.
 */
/** The packets that make up a game on the board; everything else (lobby, mail, friends, whispers ...) stays out. */
export const REC_TYPES = new Set(['starting', 'initSy', 'roundReady', 'turnStart', 'turnEnd', 'turnHint', 'useItem', 'roundEnd', 'chat', 'drawCanvas', 'drawingContent', 'attackBonus']);
export const REC_MAX_EVENTS = 30000;
export const REC_VERSION = 'v3.0.0-client';

const clone = (v) => JSON.parse(JSON.stringify(v ?? null));

/** The recording of the game that starts now (my room as it is), or null when I am in no room. */
export function startRecording(state, now = Date.now(), version = REC_VERSION) {
	const room = state.rooms[state.myRoom];
	if (!room) return null;
	const readies = {};
	const players = room.players.map((id) => {
		const u = state.users[id] ?? { id, name: id, tag: '', score: 0, equip: {} };
		readies[id] = { t: u.game?.team ?? 0 };
		const o = { id, score: 0, data: { score: u.score ?? 0 }, equip: clone(u.equip ?? {}), title: u.name + (u.tag ? `#${u.tag}` : '') };
		if (u.robot) {
			o.robot = true;
			o.level = u.level ?? 0;
		}
		return o;
	});
	return {
		version,
		me: state.id,
		time: now,
		title: room.title,
		roundTime: room.time,
		round: room.rounds,
		mode: room.modeIndex,
		limit: room.limit,
		game: { seq: clone(room.game?.seq ?? []), title: clone(room.game?.title ?? []), turn: 0, round: 0 },
		opts: clone(room.opts ?? {}),
		readies,
		injpick: clone(room.injpick ?? []),
		players,
		events: []
	};
}

/** What of a packet is kept: nobody's whole profile, only who they are. */
function shrink(pkt) {
	const out = clone(pkt);
	if (out.profile) out.profile = { id: out.profile.id, title: out.profile.title ?? out.profile.name };
	delete out.user;
	return out;
}

/** Add `pkt` to the recording if it belongs to it. @returns whether it was kept */
export function recordPacket(rec, pkt, now = Date.now()) {
	if (!rec || !pkt || !REC_TYPES.has(pkt.type)) return false;
	if (pkt.type === 'chat' && (pkt.notice || pkt.from)) return false; // system notices and whispers are not part of the game
	if (rec.events.length >= REC_MAX_EVENTS) return false;
	rec.events.push({ data: shrink(pkt), time: now - rec.time });
	return true;
}

/** The sorted, trimmed copy that is written to a file. */
export const finishRecording = (rec) => ({ ...rec, events: rec.events.slice().sort((a, b) => a.time - b.time) });

/**
 * A small driver for the connection: `feed(state, pkt)` after the packet was applied; `current`/`done` say what there is to save.
 */
export function createRecorder(now = () => Date.now()) {
	let rec = null;
	let done = false;
	return {
		/** the recording to save (the finished one, or the game in progress), null when nothing was recorded */
		get recording() {
			return rec && rec.events.length > 1 ? finishRecording(rec) : null;
		},
		get done() {
			return done;
		},
		/** @returns true when what can be saved changed (a game started or its result came) */
		feed(state, pkt) {
			if (pkt.type === 'starting') {
				rec = startRecording(state, now());
				done = false;
				if (rec) recordPacket(rec, pkt, now());
				return true;
			}
			if (!rec || done) return false;
			recordPacket(rec, pkt, now());
			if (pkt.type === 'roundEnd' && state.game?.result) {
				done = true;
				return true;
			}
			return false;
		},
		/** the game stopped without a result (everyone left) */
		abandon() {
			if (!done) rec = null;
		},
		clear() {
			rec = null;
			done = false;
		}
	};
}
