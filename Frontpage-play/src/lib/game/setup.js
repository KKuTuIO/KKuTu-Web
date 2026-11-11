/**
 * First-time profile setup (nickname), hosted inside the client. Uses the existing /api/setup/nick endpoint,
 * whose validation (length, pattern, banned words, duplicates) is the single source of truth.
 */
export const NICK_MIN = 2;
export const NICK_MAX = 16;

/** Cheap pre-check so the button only enables for plausible input; the server decides for real. */
export const looksValid = (nick) => {
	const n = String(nick ?? '');
	return n.length >= NICK_MIN && n.length <= NICK_MAX && n === n.trim();
};

/** Server result code -> locale key (`play.setup.error.<code>`); unknown codes fall back to a generic message. */
export const errorKey = (code) => (['600', '601', '602', '603', '620', 'BAD_REQUEST'].includes(String(code)) ? `play.setup.error.${code}` : 'play.setup.error.generic');

/** @returns {Promise<{ok:true}|{ok:false, key:string}>} */
export async function submitNickname(nick, fetchFn = fetch) {
	try {
		const res = await fetchFn('/api/setup/nick', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ nick }) });
		const body = await res.json();
		return body.success ? { ok: true } : { ok: false, key: errorKey(body.resultCode) };
	} catch {
		return { ok: false, key: 'play.setup.error.generic' };
	}
}
