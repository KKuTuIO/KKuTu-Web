import { normalizeUser } from './protocol.js';

/**
 * Looks players up for 친구 추가: by nickname (any part of it, with or without the #tag) or by 식별번호. The server answers members only,
 * a few times a minute.
 * @returns {Promise<{ok:true, users:any[]}|{ok:false, reason:'login'|'rate'|'error'}>}
 */
export async function searchPlayers(query, fetchFn = fetch) {
	const q = String(query ?? '').trim();
	if (q.length < 2) return { ok: true, users: [] };
	try {
		const res = await fetchFn(`/api/play/users/search?q=${encodeURIComponent(q)}`);
		if (res.status === 401) return { ok: false, reason: 'login' };
		if (res.status === 429) return { ok: false, reason: 'rate' };
		if (!res.ok) return { ok: false, reason: 'error' };
		const rows = await res.json();
		return { ok: true, users: (Array.isArray(rows) ? rows : []).map((r) => normalizeUser({ id: r.id, profile: { title: r.nickname }, data: { score: Number(r.score) || 0 }, equip: r.equip })) };
	} catch {
		return { ok: false, reason: 'error' };
	}
}

/**
 * Players by (part of) a nickname or their 식별번호, a page at a time (the replay page's player lookup). Members only.
 * @returns {Promise<{ok:true, users:any[], hasNext:boolean}|{ok:false, reason:'login'|'rate'|'error'}>}
 */
export async function lookupUsers(query, page = 1, fetchFn = fetch) {
	const q = String(query ?? '').trim();
	if (q.length < 2) return { ok: true, users: [], hasNext: false };
	try {
		const res = await fetchFn(`/api/play/users/lookup?q=${encodeURIComponent(q)}&page=${page}`);
		if (res.status === 401) return { ok: false, reason: 'login' };
		if (res.status === 429) return { ok: false, reason: 'rate' };
		if (!res.ok) return { ok: false, reason: 'error' };
		const body = await res.json();
		const rows = Array.isArray(body?.users) ? body.users : [];
		return { ok: true, hasNext: !!body?.hasNext, users: rows.map((r) => normalizeUser({ id: r.id, profile: { title: r.nickname }, data: { score: Number(r.score) || 0 }, equip: r.equip })) };
	} catch {
		return { ok: false, reason: 'error' };
	}
}
