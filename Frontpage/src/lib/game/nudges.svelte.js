/**
 * Nudges: small one-time hints that point at a feature ("플레이 방법이 궁금하지 않아? 나를 눌러봐!").
 * Each nudge has an id ('tutorial', later 'shop', ...). A signed-in account's closed ids are kept by the server in its account
 * flags (`account.flags.nudgesClosed`, an array), so they follow the account to every device and profile and a new nudge needs
 * no new flag. The ticket brings them (`ticket.nudgesClosed`). A signed-out player always sees every nudge again on the next
 * visit: closing one for them lasts for the current page only.
 */
export const NUDGES = ['tutorial']; // keep in step with AccountService.NUDGE_IDS

function csrfHeaders() {
	const token = document.cookie.split('; ').find((v) => v.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
	return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {};
}

/** Saves one closed nudge on the account; resolves to the account's closed ids. */
async function saveOnAccount(id) {
	await fetch('/api/account/csrf', { credentials: 'same-origin' }); // sets the CSRF cookie
	const res = await fetch(`/api/account/nudges/${encodeURIComponent(id)}/close`, { method: 'POST', credentials: 'same-origin', headers: csrfHeaders() });
	if (!res.ok) throw new Error(`nudge ${res.status}`);
	return (await res.json()).closed;
}
let save = saveOnAccount;
/** (tests) replace the call that saves on the account */
export function setNudgeSaver(fn) {
	save = fn ?? saveOnAccount;
}

const clean = (ids) => (Array.isArray(ids) ? [...new Set(ids.filter((id) => NUDGES.includes(id)))] : []);

export const nudges = $state({
	signedIn: false,
	closed: /** @type {string[]} */ ([]),
	/** closed on this page only (signed-out players) */
	session: /** @type {string[]} */ ([])
});

/** From the ticket: whether I am signed in, and the ids the account closed. */
export function setNudgeState({ signedIn = false, closed = [] } = {}) {
	nudges.signedIn = signedIn;
	nudges.closed = signedIn ? clean(closed) : [];
}

export function nudgeOpen(id) {
	return !nudges.closed.includes(id) && !nudges.session.includes(id);
}

/** Closing is immediate; the save is best effort, so a failed save only means the nudge may show again on the next visit. */
export async function closeNudge(id) {
	if (!NUDGES.includes(id) || !nudgeOpen(id)) return;
	if (!nudges.signedIn) {
		nudges.session = [...nudges.session, id];
		return;
	}
	nudges.closed = [...nudges.closed, id];
	try {
		nudges.closed = [...new Set([...nudges.closed, ...clean(await save(id))])];
	} catch {
		/* see above */
	}
}
