/**
 * Input trace for the game screen (the game server's `inputTrace` v1 protocol).
 *
 * While the player types into the answer field, every observable change of the field (beforeinput / input /
 * composition*) is streamed to the room worker as it happens, in small batches. The server keeps its own copy of the
 * field from those changes and, when the answer is submitted, compares it with the word (`lastInputSeq` names the last
 * change the client sent). A word that was never typed, or was pasted or injected as one piece, cannot match.
 *
 * Wire format (one packet per batch, all updates of a batch share the context and scope):
 *   { type:'inputTrace', v:1, c:context, q:firstSeq, r?:scope, u:[[deltaMs, domEvent, inputType, composing(0|1)], ...] }
 *   a tuple has three more entries [prefix, suffix, inserted] when the field's text changed: keep `prefix` characters of
 *   the previous text at the front and `suffix` at the back, put `inserted` between (characters are code points, like
 *   the server's `Array.from`).
 *
 * Rules the server enforces and this module follows:
 *   - `c` is `inputTraceContext` of the last `turnStart`; a batch with another context is refused as stale.
 *   - `q` counts from 0 for every trace; a submit ends the trace, so the counter restarts after `take()`.
 *   - the scope `r` depends on the rule (see `traceScope` in lobbyStore); a new scope is a new trace.
 *   - updates are sent as they happen (micro-batched: at most 8 updates or 50 ms), never replayed at submit time, and
 *     the batch always goes out before the `talk` that carries the word.
 *   - text typed into the chat box before the turn is the player's own (legacy: the chat box is the answer box) is sent as a
 *     "pending" trace (`p:1`, no `r`) while another player's turn runs, and the server carries it into the player's turn: the
 *     numbering and the text simply go on when the turn starts. A chat line that is not an answer ends it with `cleared()`.
 *   - only real user input is traced: events a script dispatches (`isTrusted` false) are left out, so the server's copy
 *     of the field stays behind the word and the submit is flagged.
 */
import { readValue } from './security/native.js';

const DOM_EVENT = { beforeinput: 0, input: 1, compositionstart: 2, compositionupdate: 3, compositionend: 4 };
const INPUT_TYPE = { '': 0, insertText: 1, insertCompositionText: 2, deleteCompositionText: 3, deleteContentBackward: 4, deleteContentForward: 5, insertLineBreak: 6, insertParagraph: 7 };
const MAX_BATCH = 8;
const FLUSH_MS = 50;
const MAX_VALUE = 200;
const MAX_DELTA_MS = 3_600_000;

/** What the screens that never take real input (replay, tutorial) use in place of a trace. */
export const noTelemetry = {
	enabled: false,
	turnStart() {},
	observe: () => ({ destroy() {} }),
	cleared() {},
	take: () => -1
};

/** The change from `previous` to `next` (arrays of characters) as `[prefix, suffix, inserted]`. */
export function diffText(previous, next) {
	const max = Math.min(previous.length, next.length);
	let prefix = 0;
	while (prefix < max && previous[prefix] === next[prefix]) prefix++;
	const maxSuffix = Math.min(previous.length - prefix, next.length - prefix);
	let suffix = 0;
	while (suffix < maxSuffix && previous[previous.length - 1 - suffix] === next[next.length - 1 - suffix]) suffix++;
	return [prefix, suffix, next.slice(prefix, next.length - suffix).join('')];
}

/**
 * @param {object} options
 * @param {(type: string, data: object) => void} options.send  sends to the room worker
 * @param {() => number} [options.now]
 * @param {boolean} [options.trustedOnly]  ignore events a script dispatched (always on outside tests)
 */
export function createTelemetry({ send, now = () => performance.now(), trustedOnly = true, setTimer = setTimeout, clearTimer = clearTimeout } = {}) {
	let gameId; // the game the context belongs to
	let context; // inputTraceContext of the running turn/round; undefined = the server does not take a trace
	let scopeKey; // JSON of the scope the open trace belongs to
	let scopeRef; // the `r` of that scope (undefined: turn scope, left out)
	let seq = 0; // sequence number of the next update
	let mirror = []; // the field's text as the server has it
	let lastAt = now();
	let composing = false;
	let queue = []; // updates not sent yet
	let queueFrom = 0; // sequence number of queue[0]
	let queueScope; // { pending, r } of the queued updates
	let timer = 0;

	function flush() {
		if (timer) {
			clearTimer(timer);
			timer = 0;
		}
		if (!queue.length) return;
		const packet = { v: 1, c: context, q: queueFrom, ...(queueScope?.pending ? { p: 1 } : queueScope?.r !== undefined ? { r: queueScope.r } : {}), u: queue };
		queue = [];
		send('inputTrace', packet);
	}

	function restart() {
		seq = 0;
		mirror = [];
		composing = false;
	}

	function enqueue(tuple) {
		if (!queue.length) {
			queueFrom = seq;
			queueScope = { pending: scopeKey === 'pending', r: scopeRef };
		}
		queue.push(tuple);
		seq++;
		if (queue.length >= MAX_BATCH) flush();
		else if (!timer) timer = setTimer(flush, FLUSH_MS);
	}

	/** One observed event of the field. `scope` is `{ r }` (r undefined: turn scope), `{ pending: true }` or null while nothing can be traced. */
	function record(name, event, el, scope) {
		if (context === undefined || !scope) return;
		if (event.defaultPrevented) return; // a cancelled input changed nothing
		if (trustedOnly && !event.isTrusted) return;
		const key = scope.pending ? 'pending' : JSON.stringify(scope.r ?? null);
		if (key !== scopeKey) {
			flush(); // what was typed for the old scope still goes out under it
			if (scopeKey === 'pending' && !scope.pending) {
				// the pending text becomes the turn's: the server carries it over, numbering and text go on
				scopeKey = key;
				scopeRef = scope.r;
			} else {
				scopeKey = key;
				scopeRef = scope.r;
				restart();
			}
		}
		if (name === 'compositionstart') composing = true;
		else if (name === 'compositionend') composing = false;
		const flag = composing || event.isComposing ? 1 : 0;
		const code = DOM_EVENT[name];
		const rawType = name.startsWith('composition') ? '' : (event.inputType ?? '');
		const type = rawType in INPUT_TYPE ? INPUT_TYPE[rawType] : String(rawType).slice(0, 48); // odd types stay readable for an operator
		const t = now();
		const delta = Math.min(MAX_DELTA_MS, Math.max(0, Math.round(t - lastAt)));
		lastAt = t;
		const next = Array.from(readValue(el)).slice(0, MAX_VALUE);
		const tuple = [delta, code, type, flag];
		if (next.length !== mirror.length || next.some((ch, i) => ch !== mirror[i])) {
			const [prefix, suffix, inserted] = diffText(mirror, next);
			tuple.push(prefix, suffix, inserted);
			mirror = next;
		}
		enqueue(tuple);
	}

	return {
		enabled: true,
		/** A turn (or a round of the quiz rules) begins: a new context, so a new trace. What was queued belongs to the old one. */
		turnStart(pkt) {
			const next = Number.isInteger(pkt?.inputTraceContext) ? pkt.inputTraceContext : undefined;
			const sameGame = pkt?.gameId !== undefined && pkt.gameId === gameId;
			gameId = pkt?.gameId;
			if (scopeKey === 'pending' && sameGame && next !== undefined) {
				// pending text goes on across turns; whatever is queued is sent under the new context
				context = next;
				lastAt = now();
				return;
			}
			if (timer) clearTimer(timer);
			timer = 0;
			queue = [];
			context = next;
			scopeKey = undefined;
			scopeRef = undefined;
			restart();
			lastAt = now();
		},
		/**
		 * Svelte action for the answer field: `use:telemetry.observe={() => scope}`. The scope is asked at every event, because
		 * it moves with the game (the problem being typed, the crossword bar that is selected).
		 */
		observe(el, getScope) {
			let scopeOf = getScope;
			const handlers = Object.keys(DOM_EVENT).map((name) => {
				const fn = (event) => record(name, event, el, scopeOf?.());
				el.addEventListener(name, fn);
				return [name, fn];
			});
			return {
				update(next) {
					scopeOf = next;
				},
				destroy() {
					for (const [name, fn] of handlers) el.removeEventListener(name, fn);
				}
			};
		},
		/**
		 * The field was emptied by the page, not by typing (a chat line was sent, a command run). Say so, so the server's copy of
		 * the text is empty too and the numbering goes on; it reads 'programmaticClear', not a made-up keystroke.
		 */
		cleared() {
			if (context === undefined || scopeKey === undefined || !mirror.length) return;
			const t = now();
			const delta = Math.min(MAX_DELTA_MS, Math.max(0, Math.round(t - lastAt)));
			lastAt = t;
			mirror = [];
			enqueue([delta, 1, 'programmaticClear', 0, 0, 0, '']);
		},
		/**
		 * A word is being submitted: send what is queued, then return the `lastInputSeq` to put in the `talk`. The trace ends
		 * here (the server drops its copy when it takes the word), so the next one starts again at 0. -1 = nothing was typed.
		 */
		take() {
			flush();
			const last = seq - 1;
			restart();
			return last;
		}
	};
}
