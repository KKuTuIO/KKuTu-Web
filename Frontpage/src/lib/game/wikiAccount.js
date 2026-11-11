/**
 * 리오위키 연동: the account's link to a wiki user, kept by the account service.
 * The player proves the wiki account is theirs by putting a one-time code on the wiki (their user page or an edit summary); the server looks
 * for it there. Requests follow the other /api/account/* calls: the CSRF cookie first, failures as `{error, error_description}`.
 */
const csrf = () => document.cookie.split('; ').find((v) => v.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');

async function call(method, path, body, fetchFn = fetch) {
	const headers = {};
	if (method !== 'GET') {
		await fetchFn('/api/account/csrf', { credentials: 'same-origin' }); // sets the CSRF cookie
		const token = csrf();
		if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
	}
	if (body !== undefined) headers['Content-Type'] = 'application/json';
	let res;
	try {
		res = await fetchFn(`/api/account/wiki${path}`, { method, credentials: 'same-origin', headers, body: body === undefined ? undefined : JSON.stringify(body) });
	} catch {
		throw Object.assign(new Error(''), { code: 'network', status: 0 });
	}
	const payload = await res.json().catch(() => null);
	if (!res.ok) throw Object.assign(new Error(payload?.error_description ?? ''), { code: payload?.error ?? `http_${res.status}`, status: res.status });
	return payload;
}

/** @typedef {{bound:boolean, wiki_username?:string, wiki_user_id?:number, bound_at?:string, contributions:{settled:number, pending:number}}} WikiBinding */

/** @returns {Promise<WikiBinding>} */
export const loadBinding = (fetchFn) => call('GET', '', undefined, fetchFn);

/** Starts a binding: the one-time code to put on the wiki. @returns {Promise<{code:string, expires_at:string, page:string, wiki_url:string}>} */
export const requestChallenge = (username, fetchFn) => call('POST', '/challenge', { username: String(username).trim() }, fetchFn);

/** Looks for the code on the wiki and binds. @returns {Promise<WikiBinding>} */
export const verifyChallenge = (fetchFn) => call('POST', '/verify', undefined, fetchFn);

export const unbind = (fetchFn) => call('DELETE', '', undefined, fetchFn);

/** Whether an address belongs to the wiki itself (opened at once; any other asks first). */
export function isWikiAddress(href, origin) {
	try {
		return new URL(href).origin === new URL(origin).origin;
	} catch {
		return false;
	}
}
