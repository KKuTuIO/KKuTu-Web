/**
 * Why the game connection is down, so the gate can say the right thing and the client can retry the right way. Pure functions
 * (unit-tested); connection.svelte.js feeds them what it saw.
 *
 *  - `network`: this device is offline. Nothing to do but wait for the network; the browser's `online` event retries at once.
 *  - `update`: the web or game server is being restarted (the proxy answers 502/503/504 or a page instead of JSON, or the game socket
 *    closes "going away" / "restarting"). It comes back within minutes: retry by ourselves.
 *  - `maintenance`: the operators announced it in the schedule file (`static.kkutu.io/schedule.json`, the same file the legacy client read
 *    for its notices) with a name and a reason: shown as written, and we keep waiting without giving up.
 *  - `unknown`: anything else (the server is unreachable while this device is online, an abnormal close).
 */
export const SCHEDULE_URL = 'https://static.kkutu.io/schedule.json';
/** An event's `end` is when its notice stops; the work itself can run on after it, so a maintenance event counts for this long past it (unless it says `until`). */
export const MAINTENANCE_GRACE = 12 * 3600 * 1000;
/** How often the schedule file may be read while the connection stays down. */
export const SCHEDULE_EVERY = 60 * 1000;

/**
 * @param {{online?: boolean, closeCode?: number, httpStatus?: number, badBody?: boolean}} seen
 * @returns {'network'|'update'|'unknown'}
 */
export function classifyOutage({ online = true, closeCode, httpStatus, badBody = false } = {}) {
	if (online === false) return 'network';
	if ([502, 503, 504].includes(httpStatus) || (badBody && httpStatus !== undefined && httpStatus >= 500)) return 'update';
	if ([1001, 1012, 1013].includes(closeCode)) return 'update';
	return 'unknown';
}

/**
 * The maintenance the operators announced that applies now, from the schedule file's events.
 * @param {any} events
 * @param {number} now
 * @returns {{name: string, reason: string, until: number|null}|null}
 */
export function activeMaintenance(events, now = Date.now()) {
	if (!Array.isArray(events)) return null;
	let best = null;
	for (const e of events) {
		if (!e || e.type !== 'maintenance') continue;
		const start = Date.parse(e.start);
		const end = Date.parse(e.until ?? e.end);
		if (!Number.isFinite(start)) continue;
		const stop = Number.isFinite(end) ? end + (e.until ? 0 : MAINTENANCE_GRACE) : Infinity;
		if (now < start || now > stop) continue;
		if (!best || start > best.start) best = { start, name: String(e.name ?? ''), reason: String(e.reason ?? ''), until: e.until && Number.isFinite(end) ? end : null };
	}
	return best ? { name: best.name, reason: best.reason, until: best.until } : null;
}
