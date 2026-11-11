/**
 * Guards for the fields an answer is typed into (the answer field, the crossword field and the chat box, which doubles as the
 * answer field): the legacy `bindInputMonitor`, `startWatch` and `applyDomObfuscation` of `in_game_kkutu_security.js`.
 *
 *   Lykoi              paste / undo / redo / replacement, or a whole word inserted in one piece, while a game runs: the input
 *                      is cancelled and reported (the server only records Lykoi)
 *   MaineCoon          more than three Hangul/digit characters appeared within 0.1 s (checked every 0.1 s)
 *   Persian            a stray input made like the decoys (maxlength 180, autocomplete off) that has text in it  [removes the player]
 *   FakeFocus          a decoy took the focus, or received any input / key / paste event (no person does that to a hidden field)
 *   ProtectionBypassed a decoy was made visible
 *   DOMManipulation    the real field is no longer in the page  [removes the player]
 *   CriticalDOM        the real field was removed from the page  [removes the player]
 *   AttributeModified  id / style / class of the real field changed
 *   SuspiciousAccess   the real field's value is set more than ten times by code from outside the page  [removes the player]
 *
 * Decoys: 10-30 invisible inputs on each side of the answer field, made like the legacy ones (a script that "finds the chat input"
 * by shape meets dozens). Legacy refilled some of them with random text every 3 s so that "has text" says nothing about them.
 * The legacy script also randomised the real input's id and filled the page with decoy display elements; neither is ported
 * (the id is how this page's CSS, tests and the item shortcut find the field; decoy displays would break queries of the page's own
 * display element) and a bot written for this page finds the field by shape, not by id.
 *
 * What the legacy script did differently on purpose: it stopped the value from being read back through `el.value` by keeping a
 * shadow copy (`_rlValue`), and counted every `value` assignment whose stack was not jQuery's. Here the real value is read
 * through the browser's own getter, and only assignments from code that is not the page's are counted (a framework sets the
 * value all the time).
 */
import { fitJson, foreignStack } from './report.js';
import { defaultNative } from './native.js';

const DECOY_ATTRS = { maxlength: '180', autocomplete: 'off', autocorrect: 'off', autocapitalize: 'off', spellcheck: 'false', tabindex: '-1', 'aria-hidden': 'true' };
const DECOY_STYLE = 'position:fixed;left:-9999px;top:-9999px;width:1px;height:1px;opacity:0;pointer-events:none;';
const PREFIXES = ['input', 'field', 'dialog', 'form', 'chat', 'room', 'kkutuIo'];
const KOREAN_DIGIT = /[^가-힣0-9]+/g;
const GRACE_MS = 1000; // a field removed by the page's own teardown is gone from the guard before this runs out

const pick = (rand, list) => list[Math.floor(rand() * list.length)];
const between = (rand, min, max) => Math.floor(rand() * (max - min + 1)) + min;
const makeId = (rand) => `ClientMessage${Date.now().toString(36)}${Array.from({ length: 16 }, () => 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'[Math.floor(rand() * 62)]).join('')}${rand().toString(36).slice(2, 8)}`;
const randomText = (rand) => rand().toString(36).slice(2, 10);

/** What a script would see as an invisible element: zero sizes, an empty rectangle. (`native.sizeOf` still measures the truth.) */
function hideFromMeasuring(el) {
	for (const prop of ['clientWidth', 'clientHeight', 'offsetWidth', 'offsetHeight', 'scrollWidth', 'scrollHeight']) {
		try {
			Object.defineProperty(el, prop, { get: () => 0, configurable: false, enumerable: true });
		} catch {
			/* already defined */
		}
	}
	try {
		Object.defineProperty(el, 'getBoundingClientRect', { value: () => ({ top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0, x: 0, y: 0 }), configurable: false, writable: false });
		Object.defineProperty(el, 'getClientRects', { value: () => [], configurable: false });
		Object.defineProperty(el, 'offsetParent', { get: () => null, configurable: false });
	} catch {
		/* already defined */
	}
}

/**
 * @param {object} o
 * @param {(action: string, data?: object) => void} o.report
 * @param {object} [o.native]
 * @param {() => number} [o.random]
 */
export function createFieldGuard({ report, native = defaultNative, random = Math.random, now = () => Date.now() }) {
	const known = new WeakSet(); // fields and decoys the page made itself
	const guards = new Set(); // records of the fields being watched
	let onHistory = []; // what the input events said (100)
	let intervalHistory = []; // what the field held every 0.1 s (50)
	let lastOn = 0;
	let lastInterval = 0;
	let observer;
	let shared = [];
	let reportedClone = false;
	const platform = typeof navigator === 'undefined' ? '' : navigator.platform || '';
	const isApple = /iPhone|iPod|iPad|Macintosh|MacIntel/.test(platform);

	const histories = () => ({ intervalInputHistory: fitJson(intervalHistory), onInputHistory: fitJson(onHistory) });

	function start() {
		if (observer || typeof document === 'undefined') return;
		observer = new MutationObserver((mutations) => {
			for (const m of mutations) {
				if (m.type === 'childList') {
					for (const node of m.removedNodes) {
						if (node.nodeType !== 1) continue;
						for (const g of guards) if (node === g.el || node === g.home || node.contains(g.home) || node.contains(g.el)) later(g, 'CriticalDOM', { type: 'real_input_removed' });
					}
				} else if (m.type === 'attributes') {
					for (const g of guards) if (m.target === g.el) report('AttributeModified', { attr: m.attributeName, newVal: g.el.getAttribute(m.attributeName) ?? 'null' });
				}
			}
		});
		observer.observe(document.body, { childList: true, subtree: true });

		const focus = (e) => {
			const t = e.target;
			if (t?.tagName === 'INPUT' && t.getAttribute('maxlength') === '180' && ![...guards].some((g) => g.el === t)) {
				report('FakeFocus', { fakeId: t.id, realId: [...guards][0]?.el.id ?? '' });
			}
		};
		document.addEventListener('focusin', focus, true);

		const sweep = setInterval(() => {
			for (const g of guards) {
				// DOMManipulation: the real field left the page and the page did not take it away
				if (g.alive && !(g.el.isConnected && g.home.isConnected)) report('DOMManipulation', { type: 'input_removed' });
				// ProtectionBypassed: a decoy that is no longer hidden
				for (const d of g.decoyEls) {
					const { width, height } = native.sizeOf(d);
					if (width > 1 || height > 1) report('ProtectionBypassed', { elementId: d.id });
				}
			}
			// Persian: an input like the decoys (so one that was cloned from them) with text in it
			if (!reportedClone && guards.size) {
				for (const input of document.querySelectorAll('input')) {
					if (known.has(input) || input.maxLength !== 180 || input.autocomplete !== 'off') continue;
					const value = native.readValue(input);
					if (value.length) {
						reportedClone = true;
						report('Persian', { inputId: input.id, inputVal: value, ...histories() });
						break;
					}
				}
			}
		}, 2000);

		// the decoys' own text keeps changing (legacy: every 3 s, half of them)
		const refill = setInterval(() => {
			for (const g of guards) for (const d of g.decoyEls) if (random() > 0.5) native.writeValue(d, randomText(random));
		}, 3000);
		shared = [() => document.removeEventListener('focusin', focus, true), () => clearInterval(sweep), () => clearInterval(refill)];
	}

	function later(g, action, data) {
		setTimeout(() => {
			if (g.alive && !(g.el.isConnected && g.home.isConnected)) report(action, data);
		}, GRACE_MS);
	}

	function stopIfIdle() {
		if (guards.size || !observer) return;
		observer.disconnect();
		observer = undefined;
		for (const fn of shared) fn();
		shared = [];
	}

	/**
	 * Watch one field. `isGaming()` says whether a game runs (the input rules only apply then); `decoys` surrounds it with decoys
	 * (the answer fields, not the chat box); `admin` turns the input rules off. A field inside a shadow root passes the `anchor` it
	 * stands in the page as (its host: the decoys go around that, in the page where a script looks) and the `root` to keep watching.
	 * Returns `{ destroy }`.
	 */
	return function guard(el, { isGaming = () => false, admin = false, decoys = false, anchor, root } = {}) {
		const home = anchor ?? el; // where the field stands in the page
		const g = { el, home, alive: true, decoys, decoyEls: [] };
		known.add(el);
		guards.add(g);
		start();
		if (root) observer?.observe(root, { childList: true, subtree: true }); // removal of the field from inside its own shadow root
		el.setAttribute?.('data-guarded', '');
		const removers = [];
		const listen = (name, fn, opts) => {
			el.addEventListener(name, fn, opts);
			removers.push(() => el.removeEventListener(name, fn, opts));
		};

		// ---- Lykoi: what is typed (legacy handleInputEvent) ----
		let previous = { val: '', type: '', data: '' };
		let lastLykoi = 0;
		let resyncing = false;
		const lykoi = (extra) => {
			const t = now();
			if (t - lastLykoi < 2000) return; // legacy meant a 2 s limit (its flag was reset on every call, so it never held)
			lastLykoi = t;
			report('Lykoi', { data: g.eventData ?? '', inputType: g.eventType ?? '', ...extra });
		};
		function inspect(event) {
			if (resyncing) return;
			let value = native.readValue(el);
			const beforeInput = event.type === 'beforeinput';
			const inputType = event.inputType;
			const data = event.data;
			g.eventData = typeof data === 'string' ? data : '';
			g.eventType = inputType ?? '';
			let skipPrevious = false;
			const grew = typeof data === 'string' && typeof previous.data === 'string' && previous.type === inputType && data.length - previous.data.length > 1;
			if (isGaming()) {
				if (beforeInput) {
					const forbidden = ['insertFromPaste', 'historyUndo', 'historyRedo', 'insertReplacementText'].includes(inputType);
					const oneShot = inputType === 'insertText' && !event.isComposing && typeof data === 'string' && data.length > (isApple ? 2 : 1);
					const jump = inputType === 'insertCompositionText' && grew;
					if (forbidden || oneShot || jump) {
						lykoi(jump ? { pre: previous.data } : undefined);
						event.preventDefault();
						return;
					}
				} else {
					if (inputType === 'insertFromPaste' || inputType === 'insertReplacementText') lykoi({ val: value, pre: previous.val });
					else if (inputType === 'insertCompositionText' && !isApple && grew) {
						lykoi({ pre: previous.data });
						// take the jump back out of the field and tell the page's own binding about it
						native.writeValue(el, previous.val);
						value = previous.val;
						skipPrevious = true;
						resyncing = true;
						el.dispatchEvent(new Event('input', { bubbles: true }));
						resyncing = false;
					} else if (inputType === 'deleteContentBackward' || inputType === 'insertText') skipPrevious = true;
				}
			}
			if (!beforeInput && !skipPrevious) previous = { val: value, type: inputType, data };
		}
		// the input history an operator reads next to a Persian / MaineCoon report (trusted events only: a script's own are the story)
		function remember(event) {
			if (!event.isTrusted) return;
			const t = now();
			const term = lastOn === 0 ? '-' : t - lastOn;
			lastOn = t;
			onHistory.push({ time: `${t} (${term}ms)`, type: event.inputType, data: event.data, result: native.readValue(el) });
			if (onHistory.length > 100) onHistory = onHistory.slice(-100);
		}
		if (!admin) {
			listen('beforeinput', inspect);
			listen('input', inspect);
			listen('input', remember);
		}

		// ---- MaineCoon: a Hangul/digit burst within 0.1 s ----
		let lastValue = '';
		const poll = setInterval(() => {
			const current = native.readValue(el).replace(KOREAN_DIGIT, '');
			const before = lastValue;
			if (current === before) return;
			const t = now();
			intervalHistory.push(`${t} (${lastInterval === 0 ? '-' : t - lastInterval}ms) | ${current}`);
			lastInterval = t;
			if (intervalHistory.length > 50) intervalHistory = intervalHistory.slice(-50);
			lastValue = current;
			if (!admin && current.length > before.length + 3 && isGaming()) {
				intervalHistory.push('>>DETECTED<<');
				onHistory.push({ time: t, type: '>>DETECTED<<', data: '>>DETECTED<<', result: '>>DETECTED<<' });
				report('MaineCoon', histories());
				onHistory = [];
				intervalHistory = [];
			}
		}, 100);

		if (decoys && home.parentNode) {
			// ---- decoys ----
			const make = () => {
				const d = document.createElement('input');
				d.className = `${pick(random, PREFIXES)}-${random().toString(36).slice(2, 11)}`;
				d.id = makeId(random);
				for (const [k, v] of Object.entries(DECOY_ATTRS)) d.setAttribute(k, v);
				d.setAttribute('style', DECOY_STYLE);
				hideFromMeasuring(d);
				// the real field's placeholder, so a bot that looks the field up by it finds decoys too (hidden from assistive technology,
				// so nobody else is affected)
				if (el.placeholder) d.setAttribute('placeholder', el.placeholder);
				if (random() > 0.7) native.writeValue(d, randomText(random));
				// a decoy is never touched by a person: any event on it is a script's (a script can fill one without focusing it)
				for (const name of ['input', 'beforeinput', 'keydown', 'paste', 'change']) d.addEventListener(name, () => report('FakeFocus', { fakeId: d.id, realId: el.id }));
				known.add(d);
				g.decoyEls.push(d);
				return d;
			};
			for (let i = between(random, 10, 30); i > 0; i--) home.parentNode.insertBefore(make(), home);
			let after = home;
			for (let i = between(random, 10, 30); i > 0; i--) {
				const d = make();
				after.after(d);
				after = d;
			}
		}

		// ---- AttributeModified, SuspiciousAccess ----
		{
			observer?.observe(el, { attributes: true, attributeFilter: ['id', 'style', 'class'] });
			let sets = 0;
			let lastSet = 0;
			try {
				const proto = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
				Object.defineProperty(el, 'value', {
					configurable: true,
					enumerable: true,
					get() {
						return proto.get.call(this);
					},
					set(v) {
						try {
							const t = now();
							if (t - lastSet >= 100) {
								const stack = new Error().stack;
								if (foreignStack(stack) && ++sets > 10) report('SuspiciousAccess', { count: sets, stack: String(stack).split('\n').slice(0, 5).join('|') });
							}
							lastSet = t;
						} catch {
							/* never get in the way of typing */
						}
						proto.set.call(this, v);
					}
				});
				removers.push(() => delete el.value);
			} catch {
				/* the field already has its own property */
			}
		}

		return {
			destroy() {
				g.alive = false;
				clearInterval(poll);
				for (const fn of removers) fn();
				for (const d of g.decoyEls) d.remove();
				guards.delete(g);
				known.delete(el);
				stopIfIdle();
			}
		};
	};
}
