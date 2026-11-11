/**
 * 친구 초대 links. A friend's link is `…/?ref=<code>`: the code is kept in the browser (30 days) until the player is signed in and in the game, where it
 * is sent to the server once; whatever the answer, it is let go of then. The server decides if the link counts (see `Invite.ts`).
 */
const KEY = 'kkRef';
const KEEP = 30 * 24 * 3_600_000;
export const REF_PATTERN = /^[A-Za-z0-9]{6,12}$/;

/** Keeps the code of an address that carries one. Gives it back, or an empty string. */
export function captureRef(url, storage = globalThis.localStorage, now = Date.now()) {
	const code = new URL(url, 'https://x.invalid').searchParams.get('ref') ?? '';
	if (!REF_PATTERN.test(code)) return '';
	try {
		storage?.setItem(KEY, JSON.stringify({ code: code.toUpperCase(), at: now }));
	} catch {
		/* a browser that keeps nothing: the link only works in the page it was opened in */
	}
	return code.toUpperCase();
}

/** The code kept, if there is one that is not too old. */
export function pendingRef(storage = globalThis.localStorage, now = Date.now()) {
	try {
		const saved = JSON.parse(storage?.getItem(KEY) ?? 'null');
		return saved && REF_PATTERN.test(saved.code) && now - saved.at < KEEP ? saved.code : '';
	} catch {
		return '';
	}
}

export function clearRef(storage = globalThis.localStorage) {
	try {
		storage?.removeItem(KEY);
	} catch {
		/* nothing kept */
	}
}

/** The link a player shares. */
export const inviteUrl = (origin, code) => `${origin}/?ref=${code}`;
