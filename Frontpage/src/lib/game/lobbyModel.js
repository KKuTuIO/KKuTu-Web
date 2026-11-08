import { t } from './i18n.svelte.js';

/**
 * Lobby view-model helpers. Pure functions so they can be unit tested and reused
 * once the websocket adapter feeds real server data in.
 *
 * @typedef {{no:number,title:string,locked:boolean,mode:string,tags:string[],rounds:number,time:number,cur:number,max:number,playing:boolean}} Room
 * @typedef {{lv:number,name:string}} LobbyUser
 */

/** @param {Room} r */
export const roomMeta = (r) => t('play.rooms.meta', { rounds: r.rounds, time: r.time });

/** @param {Room} r */
// First character of the last word of the mode name ("한국어 쿵쿵따" -> 쿵), independent of language.
export const modeGlyph = (r) => (r.mode.trim().split(/\s+/).pop() ?? '').charAt(0);

/** How many friends a player may have (the server refuses more: error 452). */
export const FRIEND_LIMIT = 200;

/** @param {Room} r */
export const isJoinable = (r) => !r.playing && r.cur < r.max;

/**
 * @param {Room[]} rooms
 * @param {{query?:string,sort?:'recent'|'crowded',joinableFirst?:boolean,waitingOnly?:boolean,hasSeat?:boolean,hideLocked?:boolean}} o
 */
export function filterRooms(rooms, o = {}) {
	const q = (o.query ?? '').trim();
	let out = rooms.filter((r) => {
		if (o.waitingOnly && r.playing) return false;
		if (o.hasSeat && r.cur >= r.max) return false;
		if (o.hideLocked && r.locked) return false;
		if (q && !(String(r.no).includes(q) || r.title.includes(q))) return false;
		return true;
	});
	// 1st: rooms you can walk into (optional), then the chosen order.
	const order = o.sort === 'crowded' ? (x, y) => y.cur - x.cur || y.no - x.no : (x, y) => y.no - x.no;
	out = [...out].sort((x, y) => (o.joinableFirst ? Number(isJoinable(y)) - Number(isJoinable(x)) : 0) || order(x, y));
	return out;
}

/** A bare 3-digit query is a "jump to room" request. */
export const exactRoomNo = (query) => (/^\d{3}$/.test(query.trim()) ? Number(query.trim()) : null);

/** Minutes of play per 오끄감 buff, by membership tier (same table as the legacy client). */
export const OKG_MINUTES = [12, 12, 10, 10, 8];

/** Milliseconds as e.g. "3시간 12분" / "45초" (localized), like the legacy `prettyTime`. */
export function prettyTime(ms) {
	const h = Math.floor(ms / 3_600_000);
	const m = Math.floor(ms / 60_000) % 60;
	const sec = Math.floor(ms / 1000) % 60;
	const out = [];
	if (h) out.push(t('play.time.hour', { n: h }));
	if (m) out.push(t('play.time.minute', { n: m }));
	if (!h) out.push(t('play.time.second', { n: sec }));
	return out.join(' ');
}

/** Legacy display order of game modes. Modes not listed keep their server order after these. */
const MODE_ORDER = ['KSH', 'KKT', 'KAP', 'KTY', 'KDA', 'KCW', 'KSS', 'KLT', 'KWS', 'CSQ', 'HUN', 'KDG', 'KMT', 'ESH', 'EKT', 'EAP', 'ETY', 'EDA', 'ESS', 'ELT', 'EWS', 'EDG', 'ECW'];

/**
 * Game modes grouped like the legacy room dialog: Korean, English, and "other" (language-less games such
 * as the math quiz). Empty groups are dropped. `index` is the mode's position in the server's list, which is
 * what the server expects when creating a room.
 * @param {string[]} modes server mode keys
 * @param {Record<string, {lang?:string, rule?:string}>} rules
 * @returns {{id:'ko'|'en'|'other', modes:{key:string,index:number}[]}[]}
 */
export function groupModes(modes, rules) {
	const rank = (k) => (MODE_ORDER.includes(k) ? MODE_ORDER.indexOf(k) : MODE_ORDER.length);
	const groupOf = (k) => (rules[k]?.rule === 'Mathquiz' ? 'other' : rules[k]?.lang === 'en' ? 'en' : 'ko');
	const groups = { ko: [], en: [], other: [] };
	modes.forEach((key, index) => { if (!rules[key]?.retired) groups[groupOf(key)].push({ key, index }); });
	return ['ko', 'en', 'other']
		.map((id) => ({ id, modes: groups[id].sort((a, b) => rank(a.key) - rank(b.key) || a.index - b.index) }))
		.filter((g) => g.modes.length);
}

/**
 * Seat grid for a waiting room: how many columns/rows make the `limit` seats fill the available area with
 * cards close to a comfortable shape, instead of a few huge cards or a lot of dead space.
 * @param {number} width available px   @param {number} height available px   @param {number} limit seats
 * @returns {{cols:number, rows:number, cardW:number, cardH:number}}
 */
export function seatLayout(width, height, limit, { gap = 12, ideal = 1.45, minW = 170, minH = 104 } = {}) {
	const n = Math.max(1, limit);
	let best = null;
	for (let cols = 1; cols <= n; cols++) {
		const rows = Math.ceil(n / cols);
		const cardW = (width - gap * (cols - 1)) / cols;
		const cardH = (height - gap * (rows - 1)) / rows;
		if (cardW <= 0 || cardH <= 0) continue;
		// Penalise too-small cards heavily, then pick the shape closest to `ideal`; wasted cells are a small cost.
		const small = (cardW < minW ? (minW - cardW) / minW : 0) + (cardH < minH ? (minH - cardH) / minH : 0);
		const score = small * 10 + Math.abs(Math.log(cardW / cardH / ideal)) + (cols * rows - n) * 0.3;
		if (!best || score < best.score) best = { cols, rows, cardW, cardH, score };
	}
	return best ? { cols: best.cols, rows: best.rows, cardW: best.cardW, cardH: best.cardH } : { cols: 1, rows: n, cardW: width, cardH: minH };
}

/**
 * One row of a player's record for a mode, as the legacy profile shows it. `r` = [plays, wins, score, ?, losses].
 * Draws ("half" wins) are what is left after wins and losses, and count half in the win rate.
 */
export function recordRow(r) {
	const plays = Number(r?.[0]) || 0;
	const wins = Number(r?.[1]) || 0;
	const losses = Math.min(Math.max(0, Number(r?.[4]) || 0), Math.max(0, plays - wins));
	const halves = Math.max(0, plays - wins - losses);
	const rate = plays === 0 ? 0 : Math.round(((wins + halves * 0.5) / plays) * 100 * 1000) / 1000;
	return { plays, wins, losses, rate, score: Number(r?.[2]) || 0 };
}

/** A rule's option codes. Some settings files call the optional 단어장 "inj" instead of "inp"; both mean the same. */
export const ruleCodes = (rule) => (rule?.opts ?? []).map((c) => (c === 'inj' ? 'inp' : c));
/** Does a room of this rule (with these options) need word themes picked? ("ijp": always, "inp": when its option is on) */
export function needsThemes(rule, opts) {
	const codes = ruleCodes(rule);
	return codes.includes('ijp') || (codes.includes('inp') && !!opts?.injeongpick);
}

/** Round-trip time (ms) up to which the connection counts as good / fair; above that it is poor. */
export const PING_GOOD = 100;
export const PING_FAIR = 250;
/** "good" | "fair" | "poor", or "unknown" while nothing has been measured. */
export function pingLevel(ms) {
	if (typeof ms !== 'number' || !Number.isFinite(ms) || ms < 0) return 'unknown';
	return ms <= PING_GOOD ? 'good' : ms <= PING_FAIR ? 'fair' : 'poor';
}
