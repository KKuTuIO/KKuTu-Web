/**
 * Page-wide anti-cheat: the init token, the answers to the server's two challenges, and the watchers that look at the page
 * itself (the legacy `in_game_kkutu_security.js` `init` / `startWatch` and the `test` packet handling of `in_game_kkutu.js`).
 *
 *   Chartreux        init token a moment after joining (the server marks the client "initialised"; a token that fails its
 *                    check means `WebSocket.send` was renamed before this code ran, and the server removes the player)
 *   LaPerm           heartbeat: the server asks every 15 min (`test` reqType 127) and removes a client that does not answer 15 times
 *   Siamese          the server asks for the last word sent (`test` reqType 0) when a word's `checker` did not match
 *   Bengal           `WebSocket.prototype.send` / `window.WebSocket` is no longer the browser's own (modified = 1), or
 *                    `Element.prototype.attachShadow` is not (modified = 2: someone wants into the closed shadow root of the answer field)
 *   TurkishAngora    properties appeared on / vanished from `window` (user scripts, injected libraries)
 *   Ocicat           a `fetch` the page's own code did not make (called from an extension, a user script, the console)
 *   Birman           a content-security-policy violation
 *   ScottishFold     a dialog opened by code that is not the page's (the legacy script compared the set of `.dialog` elements, a
 *                    jQuery-era DOM; this page's dialogs come and go by design, so the opener is checked instead)
 *
 * Differences from the legacy script, on purpose: it replaced `window.WebSocket` and `setTimeout` with dummies and patched
 * every iframe's `contentWindow` so other code could not use them. That breaks the libraries a modern page loads (Turnstile,
 * ads, the framework itself) and the extensions of players who did nothing wrong. Here the page keeps the real functions in
 * `native.js` before anything else runs and compares them instead, which finds the same wrapping without breaking anything.
 * The legacy `fetch` watcher reported every request after load (its jQuery client made none); this page makes many, so
 * only calls from code that is not the page's are reported.
 */
import { fitJson, foreignStack } from './report.js';
import { defaultNative } from './native.js';

const MONITOR_DELAY_MS = 3000; // legacy: the watchers wake up 3 s after the page is up
const WINDOW_EVERY_MS = 10_000;
const FUNCTIONS_EVERY_MS = 2000;
// Properties that appear on `window` for ordinary reasons: ads, analytics, the framework, the captcha.
// (measured on the live page: Google ad scripts add google_*, Google*, goog*, ggeac, AFMA_*, ampInabox*, Sentry adds Sentry/__SENTRY__, Svelte adds __svelte*)
const IGNORE_PROPS = ['google', 'Google', 'goog', 'ggeac', 'AFMA_', 'ampInabox', 'adsbygoogle', 'adfit', 'kakao', 'yt_', 'Sentry', '__SENTRY__', '__svelte', '__vite', 'turnstile', 'cf_', 'dataLayer', 'gtag', 'webkit', 'onYouTube'];

const windowProps = () => Object.keys(window).filter((k) => Number.isNaN(Number(k)));
const ignored = (name) => IGNORE_PROPS.some((p) => name.includes(p));

/**
 * @param {object} o
 * @param {(action: string, data?: object) => void} o.report
 * @param {() => string} o.lastType  type of the last message the page sent (the heartbeat tells the server whether anything but heartbeats was)
 * @param {() => string} o.lastRelay  the last word sent
 * @param {object} [o.native]
 */
export function createEnvironment({ report, lastType, lastRelay, native = defaultNative, random = Math.random }) {
	let active = false;
	const timers = [];
	let stops = [];

	return {
		/** Chartreux. `forceInvalid` is for tests of the server's check. */
		sendInit(forceInvalid = false) {
			// legacy: the token is "bad" when send was renamed while close was not (a wrapper made by hand)
			const tampered = !!native.nativeSend && native.nativeSend.name !== 'send' && native.NativeWebSocket?.prototype?.close?.name === 'close';
			let v = Math.floor(random() * 4294967295);
			v = forceInvalid || tampered ? v - (v % 1051) : v + Number(!(v % 1051)); // a multiple of 1051 is the failing mark
			report('Chartreux', { init: v.toString(16).padStart(8, '0') });
		},

		/** A `test` packet from the server. Returns true when it was one. */
		answerTest(pkt) {
			if (pkt?.type !== 'test') return false;
			if (pkt.reqType === 0) report('Siamese', { lastRelay: lastRelay() || '' });
			else if (pkt.reqType === 127) report('LaPerm', { lastType: lastType() });
			return true;
		},

		/** Start the watchers (once per page). */
		start() {
			if (active || typeof window === 'undefined') return;
			active = true;
			const wake = setTimeout(() => {
				let baseline = new Set(windowProps());
				const nativeFetch = native.nativeFetch;
				let monitoring = true;

				// TurkishAngora
				timers.push(
					setInterval(() => {
						const now = windowProps();
						const nowSet = new Set(now);
						const added = now.filter((p) => !baseline.has(p) && !ignored(p));
						const removed = [...baseline].filter((p) => !nowSet.has(p) && !ignored(p));
						baseline = nowSet;
						if (monitoring && added.length + removed.length > 0) {
							report('TurkishAngora', { added: fitJson(added, 1000), removed: fitJson(removed, 1000), modCount: added.length + removed.length });
						}
					}, WINDOW_EVERY_MS)
				);

				// Bengal
				timers.push(
					setInterval(() => {
						const sendChanged = native.NativeWebSocket && (window.WebSocket !== native.NativeWebSocket || window.WebSocket.prototype.send !== native.nativeSend);
						if (sendChanged || (native.nativeSend && !native.isNative(native.nativeSend))) report('Bengal', { modified: 1 });
						const shadowChanged = native.nativeAttachShadow && (Element.prototype.attachShadow !== native.nativeAttachShadow || !native.isNative(native.nativeAttachShadow));
						if (shadowChanged) report('Bengal', { modified: 2 });
					}, FUNCTIONS_EVERY_MS)
				);

				// Ocicat: wrap fetch, report calls that come from code that is not the page's (see foreignStack)
				if (typeof nativeFetch === 'function' && window.fetch === nativeFetch) {
					const wrapped = function (...args) {
						try {
							const stack = new Error().stack;
							if (monitoring && foreignStack(stack)) {
								const input = args[0];
								report('Ocicat', { uri: typeof input === 'string' ? input : (input?.url ?? String(input)) });
							}
						} catch {
							/* never get in the way of a request */
						}
						return nativeFetch.apply(this, args);
					};
					window.fetch = wrapped;
					stops.push(() => {
						monitoring = false;
						if (window.fetch === wrapped) window.fetch = nativeFetch;
					});
				}

				// ScottishFold: show()/showModal() called from code that is not the page's
				if (typeof HTMLDialogElement !== 'undefined') {
					for (const name of ['show', 'showModal']) {
						const original = HTMLDialogElement.prototype[name];
						if (typeof original !== 'function') continue;
						const wrapper = function (...args) {
							try {
								if (monitoring && foreignStack(new Error().stack)) report('ScottishFold', { elementId: JSON.stringify([this.id || this.className || this.tagName]) });
							} catch {
								/* never get in the way */
							}
							return original.apply(this, args);
						};
						HTMLDialogElement.prototype[name] = wrapper;
						stops.push(() => {
							monitoring = false;
							if (HTMLDialogElement.prototype[name] === wrapper) HTMLDialogElement.prototype[name] = original;
						});
					}
				}
			}, MONITOR_DELAY_MS);
			timers.push(wake);

			// Birman
			const csp = (e) => report('Birman', { cspType: String(e.violatedDirective || e.effectiveDirective || 'unknown') });
			document.addEventListener('securitypolicyviolation', csp);
			stops.push(() => document.removeEventListener('securitypolicyviolation', csp));
		},

		stop() {
			active = false;
			for (const id of timers.splice(0)) {
				clearTimeout(id);
				clearInterval(id);
			}
			for (const fn of stops.splice(0)) fn();
			stops = [];
		}
	};
}
