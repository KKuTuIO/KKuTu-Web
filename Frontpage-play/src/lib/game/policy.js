/**
 * Terms-of-service gate. The three documents are versioned; people are asked again only when one of them changes
 * (the legacy client asked again every calendar month).
 *
 * When a document is updated: change its `id` here and its label in the locale files (`play.policy.*Version`).
 */
export const POLICY_DOCS = [
	{ key: 'terms', id: '3', url: 'https://static.kkutu.io/terms.html' },
	{ key: 'operation', id: '10', url: 'https://static.kkutu.io/operation.html' },
	{ key: 'privacy', id: '260820', url: 'https://static.kkutu.io/privacy-policy.html' }
];
export const PORTAL_URL = 'https://cs.kkutu.io';

const KEY = 'kkutu-play-policy';
const LEGACY_KEY = 'isPolicyChecked'; // the legacy client's flag (month of the year)

export const currentPolicy = () => POLICY_DOCS.map((d) => `${d.key}:${d.id}`).join('|');

/** True when this browser already agreed to the current versions. Storage failures mean "ask". */
export function hasAgreed(storage = globalThis.localStorage) {
	try {
		return storage.getItem(KEY) === currentPolicy();
	} catch {
		return false;
	}
}

/** Remembers the agreement. Also sets the legacy flag so the old client doesn't ask again this month. */
export function recordAgreement(storage = globalThis.localStorage, now = new Date()) {
	try {
		storage.setItem(KEY, currentPolicy());
		storage.setItem(LEGACY_KEY, String(now.getMonth()));
	} catch {
		/* private mode: it will simply be asked again next visit */
	}
}
