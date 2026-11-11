/**
 * Ranking data for the in-game panel. Same endpoint and conventions as the homepage's /rank page.
 * The server caps a page at PAGE_SIZE rows, so a shorter page means "last page".
 */
export const PAGE_SIZE = 15;

/** @typedef {{id:string,name:string|null,rank:number,score:number|string,delta?:string}} RankRow */

/** @param {{page?:number, me?:boolean, query?:string}} o */
export function rankingUrl({ page, me, query } = {}) {
	if (me) return '/ranking?me=true';
	if (query?.trim()) return `/ranking?query=${encodeURIComponent(query.trim())}`;
	return `/ranking?page=${page ?? 0}`;
}

/**
 * @param {{page?:number, me?:boolean, query?:string}} o
 * @returns {Promise<{page:number, rows:RankRow[]}>}
 */
export async function fetchRanking(o = {}, fetchFn = fetch) {
	const res = await fetchFn(rankingUrl(o));
	if (!res.ok) throw new Error(`ranking ${res.status}`);
	const body = await res.json();
	return { page: Number(body.data?.page ?? 0), rows: body.data?.data ?? [] };
}

/** "닉네임#1234" -> { name, tag } so the tag can be shown smaller. */
export function splitName(name) {
	if (!name) return { name: '', tag: '' };
	const i = name.indexOf('#');
	return i < 0 ? { name, tag: '' } : { name: name.slice(0, i), tag: name.slice(i) };
}

/**
 * Rank change as shown in the in-game leaderboard: "+3" up, "-2" down, "0" unchanged, anything else
 * (e.g. "*" for new) as is. `dir` drives colour; the arrow keeps it readable without colour.
 */
export function displayDelta(delta) {
	const v = String(delta || '*');
	if (v.startsWith('+')) return { text: v.replace('+', '↑'), dir: 'up' };
	if (v.startsWith('-')) return { text: v.replace('-', '↓'), dir: 'down' };
	if (v === '0') return { text: '-', dir: 'none' };
	return { text: v, dir: 'none' };
}

/** A user's equip map from /user/{id}: it may arrive as a JSON string. */
export function equipOf(user) {
	const e = user?.equip;
	if (!e) return {};
	if (typeof e !== 'string') return e;
	try {
		return JSON.parse(e);
	} catch {
		return {};
	}
}
