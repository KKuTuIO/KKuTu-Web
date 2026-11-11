/**
 * Profile data: who a player is (the game's /user/{id}), what they have played (the record service) and the helpers
 * that turn it into what the profile window shows. Ported from the legacy in-game profile and the homepage's 전적 page.
 */
import { normalizeUser } from './protocol.js';
import { t } from './i18n.svelte.js';

export const PAGE_SIZES = [10, 30, 50];

const toEpochMs = (v) => {
	const n = Number(v);
	if (!Number.isFinite(n) || n <= 0) return 0;
	return n > 1_000_000_000_000 ? Math.floor(n) : Math.floor(n * 1000);
};

async function getJson(url) {
	const res = await fetch(url);
	let body = {};
	try {
		body = await res.json();
	} catch {
		/* not JSON */
	}
	return { ok: res.ok, body };
}

/** The legacy "offline guest" stand-in: a guest nobody can look up. */
export const guestStub = (id) => ({ ...normalizeUser({ id, guest: true, profile: { title: t('play.profile.offlineGuest') }, data: { score: 0 } }), offline: true, lastLogin: 0, rank: null });

/** A member who is not (or no longer) on the server: the game's own record of them. Null when there is none. */
export async function fetchProfile(id) {
	if (id.startsWith('guest__')) return guestStub(id);
	try {
		const { body } = await getJson(`/user/${encodeURIComponent(id)}`);
		if (!body || body.error || body.result !== 200) return null;
		return { ...normalizeUser(body), offline: true, rank: Number(body.rank) || null, lastLogin: toEpochMs(body.profile?.lastLogin) };
	} catch {
		return null;
	}
}

export async function fetchModeStats(id) {
	const { body } = await getJson(`/api/replay/user/${encodeURIComponent(id)}/mode-stats`);
	if (!body?.ok) throw new Error(String(body?.code ?? 'stats'));
	return Array.isArray(body.stats) ? body.stats : [];
}

export async function fetchHistory(id, page, pageSize) {
	const { body } = await getJson(`/api/replay/user/${encodeURIComponent(id)}?page=${page}&pageSize=${pageSize}`);
	if (!body?.ok) throw new Error(String(body?.code ?? 'history'));
	return { rows: body.history ?? [], hasNext: !!body.pagination?.hasNext };
}

/** Win rate in percent; a draw ("반타작") counts half. */
export function winRate(wins, games, halves = 0) {
	if (!games) return 0;
	return Math.round(((wins + halves * 0.5) / games) * 10000) / 100;
}

/**
 * One row per mode: the game's own counters (plays, wins, score, play time, losses) completed with the record
 * service's numbers (accepted words) - the same merge the homepage's 전적 page did.
 * @param {Record<string, number[]>} record
 * @param {{modeName:string, games:number, wins:number, exp:number, playTime:number, acceptedWords:number}[]} replayStats
 */
export function buildModeStats(record, replayStats) {
	const byMode = {};
	for (const [key, v] of Object.entries(record && typeof record === 'object' ? record : {})) {
		const values = Array.isArray(v) ? v : [];
		const games = Math.max(0, Number(values[0] || 0));
		const wins = Math.min(games, Math.max(0, Number(values[1] || 0)));
		const losses = Math.min(Math.max(0, games - wins), Math.max(0, Number(values[4] || 0)));
		byMode[key] = { key, games, wins, losses, exp: Number(values[2] || 0), playTimeMs: Math.max(0, Number(values[3] || 0)), acceptedWords: 0 };
	}
	for (const row of Array.isArray(replayStats) ? replayStats : []) {
		const key = row?.modeName || 'UNKNOWN';
		if (!byMode[key]) {
			const games = Math.max(0, Number(row?.games || 0));
			byMode[key] = { key, games, wins: Math.min(games, Math.max(0, Number(row?.wins || 0))), losses: 0, exp: Number(row?.exp || 0), playTimeMs: Math.max(0, Number(row?.playTime || 0)), acceptedWords: 0 };
		}
		byMode[key].acceptedWords = Number(row?.acceptedWords || 0);
	}
	return Object.values(byMode)
		.map((r) => {
			const halves = Math.max(0, r.games - r.wins - r.losses);
			return { ...r, halves, hours: r.playTimeMs / 3_600_000, winRate: winRate(r.wins, r.games, halves) };
		})
		.filter((r) => r.playTimeMs > 0 || r.wins > 0 || r.games > 0 || r.acceptedWords > 0 || r.exp > 0)
		.sort((a, b) => b.exp - a.exp || b.playTimeMs - a.playTimeMs || b.games - a.games);
}

/** 오늘의 추천 게임 유형 (records page): the best win rate among modes with at least 10 games, else the first mode. */
export function recommendedMode(stats) {
	return stats.filter((s) => s.games >= 10).sort((a, b) => b.winRate - a.winRate || b.games - a.games)[0] ?? stats[0] ?? null;
}

/** 보완할 게임 유형 (records page): the worst win rate among modes with at least 10 games. */
export function needsWorkMode(stats) {
	return stats.filter((s) => s.games >= 10).sort((a, b) => a.winRate - b.winRate || b.games - a.games)[0] ?? null;
}

/** How a finished game went for the player: 'win', 'loss' (bottom half) or 'half'. */
export function historyOutcome(row) {
	if (row?.won) return 'win';
	const place = Number(row?.placement || 0) - 1;
	const players = Number(row?.playerCount || 0);
	return place >= 0 && players > 0 && place >= Math.ceil(players / 2) ? 'loss' : 'half';
}

/** "3분 전", "2개월 전" ... */
export function timeAgo(ms, now = Date.now()) {
	if (!ms) return '-';
	const sec = Math.max(0, Math.floor((now - ms) / 1000));
	if (sec < 60) return t('play.ago.second', { n: sec });
	const min = Math.floor(sec / 60);
	if (min < 60) return t('play.ago.minute', { n: min });
	const hour = Math.floor(min / 60);
	if (hour < 24) return t('play.ago.hour', { n: hour });
	const day = Math.floor(hour / 24);
	if (day < 30) return t('play.ago.day', { n: day });
	if (day < 365) return t('play.ago.month', { n: Math.floor(day / 30) });
	return t('play.ago.year', { n: Math.floor(day / 365) });
}

/** The people I played against in my latest game (members only, not me), for "친구 추가". Empty when there's nothing to suggest. */
export async function fetchRecentOpponents(me, limit = 6) {
	const { rows } = await fetchHistory(me, 1, 1);
	const gameId = rows[0]?.gameId;
	if (!gameId) return [];
	const { body } = await getJson(`/api/replay/game/${encodeURIComponent(gameId)}`);
	const ids = (body?.game?.userIds ?? []).filter((id) => id && id !== me && !String(id).startsWith('guest__') && !/^robot/i.test(String(id)));
	const people = await Promise.all([...new Set(ids)].slice(0, limit).map((id) => fetchProfile(id)));
	return people.filter(Boolean);
}
