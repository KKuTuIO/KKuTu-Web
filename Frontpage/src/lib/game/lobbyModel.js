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

/** @param {Room} r */
export const isJoinable = (r) => !r.playing && r.cur < r.max;

/**
 * @param {Room[]} rooms
 * @param {{query?:string,sort?:'recent'|'crowded'|'joinable',waitingOnly?:boolean,hasSeat?:boolean,hideLocked?:boolean}} o
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
	if (o.sort === 'crowded') out = [...out].sort((a, b) => b.cur - a.cur);
	else if (o.sort === 'joinable')
		out = [...out].sort((a, b) => Number(isJoinable(b)) - Number(isJoinable(a)) || b.no - a.no);
	else out = [...out].sort((a, b) => b.no - a.no);
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
const MODE_ORDER = ['KSH', 'KKT', 'KAP', 'KTY', 'KDA', 'KCW', 'KSS', 'KLT', 'KWS', 'CSQ', 'HUN', 'KDG', 'KFT', 'KMT', 'ESH', 'EKT', 'EAP', 'ETY', 'EDA', 'ESS', 'ELT', 'EWS', 'EDG', 'ECW'];

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
	modes.forEach((key, index) => groups[groupOf(key)].push({ key, index }));
	return ['ko', 'en', 'other']
		.map((id) => ({ id, modes: groups[id].sort((a, b) => rank(a.key) - rank(b.key) || a.index - b.index) }))
		.filter((g) => g.modes.length);
}
