/** The wire side of the security events: codes, the metric shape the server's Ingress accepts, and a few shared helpers. */

/** `securityTelemetry` event codes (server `SecurityTelemetryPolicy.ts`). The names are the server's; they say little on purpose. */
export const EVENT = {
	Bengal: 1, // a function that carries the traffic was replaced (WebSocket.send)
	ScottishFold: 2, // a dialog that is not the page's own appeared
	Persian: 3, // text in an input element the page did not make
	Birman: 4, // a content-security-policy violation
	MaineCoon: 5, // more than three characters in 0.1 s
	Lykoi: 6, // paste / undo / one-piece insertion into the answer
	Ocicat: 7, // a request the page's own code did not make
	TurkishAngora: 8, // properties added to or removed from `window`
	DOMManipulation: 9, // the real input is gone from the page
	ProtectionBypassed: 10, // a hidden decoy was made visible
	SuspiciousAccess: 11, // the real input's value is being set by a script from outside
	CriticalDOM: 12, // the real input was removed
	AttributeModified: 13, // id / style / class of the real input changed
	FakeFocus: 14, // a decoy took the focus
	Chartreux: 15, // the init token
	LaPerm: 16, // answer to the server's heartbeat
	Siamese: 17, // answer to the server's request for the last sent word
	NorwegianForest: 18 // (server side only: the request was not answered)
};
/** Debug channel for socket problems; the server only logs it. */
export const SOCKET_DEBUG = 32001;

const MAX_STRING = 2048;
const MAX_KEYS = 16;

/** `{ ... }` -> the `metric` of a securityTelemetry packet: at most 16 keys, strings <= 2048, finite numbers, booleans. `type` is not allowed as a key. */
export function toMetric(data) {
	const metric = {};
	for (const key of Object.keys(data ?? {})) {
		if (Object.keys(metric).length >= MAX_KEYS) break;
		const name = key === 'type' || key === 'action' ? (key === 'type' ? 'inputType' : null) : key;
		if (!name || !/^[A-Za-z][A-Za-z0-9_]{0,31}$/.test(name)) continue;
		const value = data[key];
		if (typeof value === 'string') metric[name] = value.slice(0, MAX_STRING);
		else if (typeof value === 'boolean') metric[name] = value;
		else if (typeof value === 'number' && Number.isFinite(value)) metric[name] = Math.max(-1_000_000_000, Math.min(1_000_000_000, value));
	}
	return metric;
}

/** JSON of `items` that fits in a metric string: the oldest entries go first. */
export function fitJson(items, limit = MAX_STRING - 48) {
	let list = [...items];
	let text = JSON.stringify(list);
	while (text.length > limit && list.length) {
		list = list.slice(Math.max(1, Math.ceil(list.length * 0.1)));
		text = JSON.stringify(list);
	}
	return text;
}

/** Code that is not the page's: a browser extension, a user script, the console or `eval`. (`(<anonymous>)` alone is a built-in frame like Promise.) */
const FOREIGN = /(?:chrome|moz|safari|safari-web|ms-browser)-extension:|<anonymous>:\d+:\d+|\beval at\b|\(eval |userscript|tampermonkey|violentmonkey|greasemonkey/i;
export function foreignStack(stack) {
	return FOREIGN.test(String(stack ?? ''));
}
