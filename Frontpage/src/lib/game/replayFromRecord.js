/**
 * A game of the server's records, played on the real game screen. The server keeps a summary of a game (who played, every word with the
 * time it was said and its outcome, the timeouts, the result), not the packets the game was made of (see replayPayload.js), so this builds
 * the recording a `.kkio` file would hold: the same `starting`, `roundReady`, `turnStart`, `turnEnd` and `roundEnd` packets, with times. The
 * player (replayPlayer.svelte.js) then plays it like any other recording.
 *
 * What can be rebuilt is what the summary holds:
 *  - the chain modes (끝말잇기, 쿵쿵따, 앞말잇기, 끄투), 단어 대결 and 훈민정음: every accepted word with its time and points, the timeouts;
 *  - 솎솎: every word taken with its points, and when each round ended; the board is rebuilt from those words (the other letters of the
 *    real board are not stored, so they are filled in).
 * The other modes (십자말풀이, 그림퀴즈, 자음퀴즈, 타자 대결, 수학 대결, 잇땅, 워드스택) keep no puzzle, drawing, problem or letter pool in the
 * summary, so there is nothing to show of them but the game sheet; `replaySupport` says so. A turn the summary does not show (a refused
 * word, an item) is left out; the letters of the 두음 rule are read from the words that were accepted.
 */
import { b64json, summarize } from './replayPayload.js';
import { EXP } from './level.js';

const CHAIN_MODES = new Set(['KSH', 'ESH', 'KKT', 'KFT', 'EKT', 'KAP', 'EAP']);
const THEME_MODES = { KDA: 'Daneo', EDA: 'Daneo', HUN: 'Hunmin' };
const SOCK_MODES = new Set(['KSS', 'ESS']);
const REVERSE = new Set(['KAP', 'EAP']);
/** The pause the server keeps before the first turn of a round (the round letters show meanwhile). */
const ROUND_LEAD_MS = 2400;
/** Time before the first round letters, so nothing is ever at a negative time. */
const START_LEAD_MS = 1200;

/**
 * Does the summary hold what is needed to rebuild this game?
 * @returns {{supported:boolean, family?:'chain'|'themed'|'sock'}}
 */
export function replaySupport(detail) {
	const payload = detail?.replayView?.payload ?? detail?.payload;
	if (!payload || !Array.isArray(payload.rm) || !Array.isArray(payload.p)) return { supported: false };
	const mode = String(payload.rm[2]);
	const rule = String(payload.rm[3]);
	const has = (list) => Array.isArray(list) && list.length > 0;
	if (rule === 'Classic' && CHAIN_MODES.has(mode) && has(payload.i)) return { supported: true, family: 'chain' };
	if (THEME_MODES[mode] === rule && has(payload.i)) return { supported: true, family: 'themed' };
	if (rule === 'Sock' && SOCK_MODES.has(mode) && (payload.mv ?? []).some((r) => r?.[0] === 'SOK')) return { supported: true, family: 'sock' };
	return { supported: false };
}

/** The server's turn clock: how fast the turn runs for the round time that is left. */
export function turnSpeed(roundTimeMs) {
	const limits = [5000, 11000, 18000, 26000, 35000, 45000, 56000, 68000, 81000, 95000];
	for (let i = 0; i < limits.length; i++) if (roundTimeMs < limits[i]) return 10 - i;
	return 0;
}

/** The outfit the summary keeps as [slot, item, slot, item ...], as the `equip` object the board draws. */
function equipOf(outfit) {
	const equip = {};
	if (Array.isArray(outfit)) for (let i = 0; i + 1 < outfit.length; i += 2) if (outfit[i] && outfit[i + 1]) equip[String(outfit[i])] = String(outfit[i + 1]);
	return equip;
}

/** The score a player has when they reach `level` (so the level shown is the one they had). */
const scoreOfLevel = (level) => (level > 1 ? EXP[Math.min(level, EXP.length) - 2] : 0);

/** What the next player starts from, after `word` (the legacy rules: the last letter, the first for 앞말잇기, the last three for 끄투). */
function promptAfter(word, mode) {
	if (REVERSE.has(mode)) return word.charAt(0);
	if (mode === 'EKT') return word.slice(-3);
	return word.slice(-1);
}

/** A small repeatable shuffle (so the same game always gives the same board). */
function shuffled(items, seed) {
	const out = items.slice();
	let x = (seed * 2654435761) >>> 0 || 1;
	for (let i = out.length - 1; i > 0; i--) {
		x = (Math.imul(x, 1664525) + 1013904223) >>> 0;
		const j = x % (i + 1);
		[out[i], out[j]] = [out[j], out[i]];
	}
	return out;
}

const FILLER = [...'가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허고노도로모보소오조초코토포호'];

/**
 * @param {object} detail the game with its `replayView` (see replayView.js)
 * @param {{optionDefs?:object, me?:string, now?:number}} [o] `optionDefs`: the option codes of the server (to name the room's rules)
 * @returns the recording, or null when the game cannot be rebuilt
 */
export function recFromRecord(detail, { optionDefs = {}, me = '', now = Date.now() } = {}) {
	const support = replaySupport(detail);
	if (!support.supported) return null;
	const payload = detail.replayView?.payload ?? detail.payload;
	const sheet = summarize(payload, detail);
	if (!sheet?.players.length) return null;
	const rm = payload.rm;
	const codes = Array.isArray(rm[9]) ? rm[9].map(String) : [];
	const opts = {};
	for (const code of codes) if (optionDefs[code]?.name) opts[optionDefs[code].name.toLowerCase()] = true;
	const players = sheet.players;
	const ids = players.map((p) => p.id);
	const events = [];
	const push = (time, data) => events.push({ time: Math.max(0, Math.round(time + START_LEAD_MS)), data });
	push(-START_LEAD_MS, { type: 'starting', target: 1 });

	const ctx = {
		payload,
		sheet,
		mode: String(rm[2]),
		codes,
		rounds: Math.max(1, sheet.room.rounds),
		roomTime: Math.max(1000, sheet.room.time * 1000),
		titles: Array.isArray(rm[8]) ? rm[8].map(String) : [...String(rm[8] ?? '')],
		ids,
		gameId: String(detail.gameId ?? sheet.id ?? ''),
		seq: ids.slice(),
		push
	};
	const end = support.family === 'sock' ? sockEvents(ctx) : turnEvents(ctx, support.family === 'themed');

	// the result, a moment after the last turn
	const result = sheet.results.map((r) => ({ id: ids[r.player], score: r.score, dim: 1, rank: r.rank - 1, reward: { score: r.exp, money: r.money, ep: r.ep, _score: r.exp, _money: r.money } }));
	const users = {};
	for (const r of sheet.results) {
		const p = players[r.player];
		users[p.id] = { id: p.id, data: { score: scoreOfLevel(p.level) + r.exp }, money: r.money };
	}
	push(end + 1200, { type: 'roundEnd', result, users, ranks: {}, ...(ctx.gameId ? { gameId: ctx.gameId } : {}) });

	const viewer = ids.includes(me) ? me : (players.find((p) => !p.robot) ?? players[0]).id;
	return {
		version: 'v3.0.0-client',
		me: viewer,
		time: Number(sheet.start) || now,
		title: sheet.room.title,
		roundTime: sheet.room.time,
		round: ctx.rounds,
		mode: sheet.room.modeIndex,
		limit: Math.max(players.length, 2),
		game: { seq: ctx.seq, title: ctx.titles, turn: 0, round: 0 },
		opts,
		readies: Object.fromEntries(players.map((p) => [p.id, { t: p.team }])),
		injpick: Array.isArray(rm[10]) ? rm[10] : [],
		players: players.map((p) => ({ id: p.id, score: 0, data: { score: scoreOfLevel(p.level) }, equip: equipOf(p.outfit), title: p.name, ...(p.robot ? { robot: true, level: 0 } : {}) })),
		events
	};
}

/**
 * The chain modes and the ones that go by a round's theme (단어 대결, 훈민정음): turn after turn, each word with its points, and the
 * timeout that ends a round. @returns when the last turn ended
 */
function turnEvents({ sheet, payload, mode, codes, rounds, roomTime, titles, ids, gameId, seq, push }, themed) {
	const k32 = codes.includes('k32');
	const k4 = codes.includes('k4') || mode === 'KFT';
	const seatOf = (id) => Math.max(0, seq.indexOf(id));
	// Who played at each place of the order: the `turn` of a word is the place; a place nobody shows keeps the players' own order.
	const placeOf = new Map();
	for (const word of sheet.inputs) if (word.ok === true && word.turn >= 0 && word.turn < ids.length && ids[word.player]) placeOf.set(word.turn, ids[word.player]);
	if (placeOf.size === ids.length && new Set(placeOf.values()).size === ids.length) for (const [place, id] of placeOf) seq[place] = id;

	const timeouts = (Array.isArray(payload.mv) ? payload.mv : [])
		.filter((r) => Array.isArray(r) && r[0] === 'CTO')
		.map((r) => ({ player: Number(r[1]) || 0, round: Number(r[2]) || 1, at: Number(r[3]) || 0, score: Number(String(payload.x?.[Number(r[4])] ?? '').split(',')[1]) || 0 }));

	let end = 0;
	for (let round = 1; round <= rounds; round++) {
		const accepted = sheet.inputs.filter((w) => w.round === round && w.ok === true).sort((a, b) => a.at - b.at);
		const out = timeouts.filter((t) => t.round === round).sort((a, b) => a.at - b.at).at(-1);
		if (!accepted.length && !out) continue;
		const turns = accepted.map((w) => ({ player: ids[w.player], word: w, at: w.at }));
		if (out) turns.push({ player: ids[out.player], timeout: out, at: out.at });

		let remaining = roomTime;
		let prompt = themed ? '' : (titles[round - 1] ?? '');
		let previousEnd = null;
		let previousTurnTime = 0;
		let readyAt = 0;
		turns.forEach((turn, k) => {
			remaining = Math.min(remaining, Math.max(10000, 150000 - k * 1500));
			const speed = turnSpeed(remaining);
			const turnTime = 15000 - 1400 * speed;
			// a turn begins a sixth of a turn after the last word; the first one is placed by how long its word took
			const start = k === 0 ? Math.max(0, (turn.word ? turn.at - turn.word.turnMs : turn.at - turnTime)) : previousEnd + previousTurnTime / 6;
			const at = Math.max(start + 1, turn.at);
			if (k === 0) {
				readyAt = Math.max(Math.max(0, start - ROUND_LEAD_MS), end);
				push(readyAt, themed ? { type: 'roundReady', round, theme: titles[round - 1] ?? '' } : { type: 'roundReady', round, char: prompt });
			}
			const sub = !themed && turn.word && prompt && !REVERSE.has(mode) && !turn.word.word.startsWith(prompt) ? turn.word.word.charAt(0) : '';
			const subReverse = !themed && turn.word && prompt && REVERSE.has(mode) && !turn.word.word.endsWith(prompt) ? turn.word.word.slice(-1) : '';
			const wordLength = mode === 'KKT' || mode === 'KFT' ? (k4 ? 4 : k32 ? (k % 2 === 0 ? 3 : 2) : 3) : undefined;
			push(Math.max(start, readyAt + 1), {
				type: 'turnStart',
				turn: seatOf(turn.player),
				...(themed ? {} : { char: prompt }),
				...(sub || subReverse ? { subChar: sub || subReverse } : {}),
				speed,
				turnTime,
				roundTime: remaining,
				...(wordLength ? { wordLength } : {}),
				gameId,
				turnId: `r${round}t${k}`,
				inputEpoch: '0'
			});
			if (turn.word) {
				push(at, { type: 'turnEnd', ok: true, value: turn.word.word, mean: '', theme: '', wc: '', score: turn.word.score ?? 0, bonus: turn.word.bonus ?? 0 });
				remaining = Math.max(0, remaining - turn.word.turnMs);
				if (!themed) prompt = promptAfter(turn.word.word, mode);
			} else {
				push(at, { type: 'turnEnd', ok: false, target: turn.player, score: turn.timeout.score });
			}
			previousEnd = at;
			previousTurnTime = turnTime;
			end = Math.max(end, at);
		});
	}
	return end;
}

/**
 * 솎솎: everyone takes words from one board at the same time. The summary has each word taken (and the points in the outcome row next to
 * it), and when each round ended; the board is rebuilt from the words taken, filled up to the size the outcome rows say it had.
 * @returns when the last round ended
 */
function sockEvents({ payload, rounds, roomTime, titles, ids, push }) {
	const extras = Array.isArray(payload.x) ? payload.x : [];
	const rows = (Array.isArray(payload.mv) ? payload.mv : []).filter(Array.isArray).map((r) => {
		const tokens = String(extras[Number(r[4])] ?? '').split(',');
		return { type: r[0], player: Number(r[1]) || 0, round: Number(r[2]) || 1, at: Number(r[3]) || 0, tokens };
	});
	const outcomes = rows.filter((r) => r.type === 'OUT').map((r) => ({ ...r, body: b64json(r.tokens[1]) }));
	const taken = rows.filter((r) => r.type === 'SOK');
	const everything = taken.flatMap((r) => [...(r.tokens[1] ?? '')]);
	const boardSize = outcomes.map((o) => Number(o.body?.p?.[2]) || 0).find((n) => n > 0) || 64;

	const points = (row) => {
		const o = outcomes.find((c) => c.player === row.player && c.round === row.round && Math.abs(c.at - row.at) <= 50);
		return Number.isFinite(o?.body?.d) ? o.body.d : Math.round(Math.pow(Math.max(0, [...(row.tokens[1] ?? '')].length - 1), 1.6) * 8);
	};

	let end = 0;
	for (let round = 1; round <= rounds; round++) {
		const words = taken.filter((r) => r.round === round).sort((a, b) => a.at - b.at);
		const closing = rows.filter((r) => r.type === 'TMO' && r.round === round).map((r) => r.at);
		if (!words.length && !closing.length) continue;
		const roundEnd = closing.length ? Math.min(...closing) : words.at(-1).at + 1000;
		const startAt = Math.max(0, roundEnd - roomTime);
		const readyAt = Math.max(Math.max(0, startAt - ROUND_LEAD_MS), end);
		const letters = words.flatMap((r) => [...(r.tokens[1] ?? '')]);
		const filler = everything.length ? everything : FILLER;
		const pad = Array.from({ length: Math.max(0, boardSize - letters.length) }, (_, i) => filler[(i + round) % filler.length]);
		push(readyAt, { type: 'roundReady', round, board: shuffled([...letters, ...pad], round + (titles.length || 1)).slice(0, Math.max(boardSize, letters.length)).join('') });
		push(Math.max(startAt, readyAt + 1), { type: 'turnStart', roundTime: roomTime });
		for (const row of words) push(Math.min(Math.max(row.at, startAt + 1), roundEnd - 1), { type: 'turnEnd', target: ids[row.player], value: row.tokens[1] ?? '', score: points(row) });
		push(roundEnd, { type: 'turnEnd' });
		end = roundEnd;
	}
	return end;
}
