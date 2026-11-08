/**
 * Anti-cheat facade for one connection: turns the detections of `environment.js` and `fieldGuard.js` into `securityTelemetry`
 * packets (master socket), answers the server's `test` challenges, and sends the socket debug events.
 * Port of the legacy `in_game_kkutu_security.js` + the security parts of `in_game_kkutu.js`; see those two files for what is
 * deliberately different.
 */
import { EVENT, SOCKET_DEBUG, toMetric } from './report.js';
import { createEnvironment } from './environment.js';
import { createFieldGuard } from './fieldGuard.js';
import { defaultNative } from './native.js';

// answers to the server's own requests are never held back; a detection repeats at most once a second
const ALWAYS = new Set(['Chartreux', 'LaPerm', 'Siamese']);
const COOLDOWN_MS = 1000;

export const noSecurity = {
	report() {},
	sendInit() {},
	answerTest: () => false,
	start() {},
	stop() {},
	guard: () => ({ destroy() {} }),
	debug() {}
};

/**
 * @param {object} o
 * @param {(type: string, data: object) => void} o.send  sends to the master socket
 * @param {() => string} [o.lastType]
 * @param {() => string} [o.lastRelay]
 * @param {() => boolean} [o.isGaming]
 * @param {() => boolean} [o.isAdmin]
 * @param {() => object} [o.debugContext]  extra readings for the socket debug events (room id, socket states, last type)
 */
export function createSecurity({ send, lastType = () => '', lastRelay = () => '', isGaming = () => false, isAdmin = () => false, debugContext = () => ({}), native = defaultNative, random = Math.random, now = () => Date.now() } = {}) {
	const last = {};
	function report(action, data = {}) {
		const eventCode = EVENT[action];
		if (!eventCode) return;
		const t = now();
		if (!ALWAYS.has(action) && last[action] !== undefined && t - last[action] < COOLDOWN_MS) return;
		last[action] = t;
		send('securityTelemetry', { eventCode, metric: toMetric(data) });
	}
	const environment = createEnvironment({ report, lastType, lastRelay, native, random });
	const fieldGuard = createFieldGuard({ report, native, random, now });
	return {
		report,
		sendInit: environment.sendInit,
		answerTest: environment.answerTest,
		start: environment.start,
		stop: environment.stop,
		/** Watch an answer field: `{ decoys }` for the answer fields (not the chat box). Returns `{ destroy }`. */
		guard: (el, options = {}) => fieldGuard(el, { isGaming, admin: isAdmin(), ...options }),
		/** Socket trouble for the server's log (it only records these). */
		debug(event, detail = {}) {
			const visible = typeof document === 'undefined' ? 'unknown' : document.visibilityState || 'unknown';
			send('securityTelemetry', {
				eventCode: SOCKET_DEBUG,
				metric: toMetric({ event, visibility: visible, online: typeof navigator === 'undefined' ? true : navigator.onLine !== false, focused: typeof document === 'undefined' ? false : document.hasFocus(), lastType: lastType(), ...debugContext(), ...detail })
			});
		}
	};
}
