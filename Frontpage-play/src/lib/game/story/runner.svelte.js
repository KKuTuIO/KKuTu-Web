import { kindOf } from './script.js';

/**
 * Runs a dialogue script (see script.js). `state` is what the screen draws; the functions are what it can do.
 * `host` is the page's side: `host.stage(name, node, api)` and `host.do(name, node, api)` (both may return a promise),
 * `host.end({skipped})` when the script is over. `api.tip(...)` shows a line that doesn't wait for 다음, `api.emit(name)` answers a `wait`.
 */
export function createRunner(script, host = {}) {
	const state = $state({
		id: '',
		line: /** @type {null|{who:string,text:string,params?:object,mood?:string,n:number}} */ (null),
		choices: /** @type {null|{text:string,goto:string}[]} */ (null),
		focus: /** @type {null|string} */ (null), // a CSS selector of the part of the page being talked about
		focusLabel: '', // what is said beside the focused part (a stage's pointer: "물레방아 입력!")
		at: '', // where the bubble sits ('top' | 'bottom'); '' is the overlay's own choice
		canAdvance: false, // a line that waits for 다음
		canGoBack: false,
		waiting: '', // the event a `wait` is waiting for
		busy: false, // a stage or an action is running
		tip: /** @type {null|{who:string,text:string,params?:object,n:number,wait?:boolean}} */ (null), // an extra line from a stage, shown instead of the line (`wait`: it waits for 다음)
		done: false,
		skipped: false
	});
	const indexOfId = new Map(script.nodes.flatMap((n, i) => (n.id !== undefined ? [[n.id, i]] : [])));
	const trail = []; // the lines shown so far (node indexes), for 이전
	let pos = -1;
	let run = 0; // a newer run cancels the one before it
	let release = /** @type {null|(()=>void)} */ (null); // the stage's `line` that is waiting for 다음
	let lines = 0;

	const target = (id) => {
		const i = indexOfId.get(id);
		if (i === undefined) throw new Error(`story: no node "${id}"`);
		return i;
	};
	const after = (i, to) => (to !== undefined ? target(to) : i + 1);

	function finish(skipped = false) {
		run++;
		release = null;
		state.done = true;
		state.skipped = skipped;
		state.line = state.choices = state.focus = state.tip = null;
		state.focusLabel = '';
		state.canAdvance = state.canGoBack = state.busy = false;
		state.waiting = '';
		host.end?.({ skipped });
	}

	const api = {
		/** An extra line of the guide (not part of the script's own lines); the next node replaces it. */
		tip(text, { who = state.line?.who ?? script.cast?.[0] ?? '', params } = {}) {
			state.tip = { who, text, params, n: ++lines };
		},
		/**
		 * An extra line that waits for 다음 (a stage that explains something: the game does not go on until it is read). Resolves when it is pressed.
		 * `at` puts the bubble at the top or the bottom for this line.
		 */
		line(text, { who = state.line?.who ?? script.cast?.[0] ?? '', params, at } = {}) {
			return new Promise((resolve) => {
				const before = state.at;
				if (at) state.at = at;
				state.tip = { who, text, params, n: ++lines, wait: true };
				state.canAdvance = true;
				release = () => {
					state.at = before;
					if (state.tip) state.tip = { ...state.tip, wait: false };
					state.canAdvance = false;
					resolve();
				};
			});
		},
		/** Dims the page except the part `selector` finds, with `label` beside it (a stage points at its own controls); `null` ends it. */
		focus(selector, label = '') {
			state.focus = selector;
			state.focusLabel = selector ? label : '';
		},
		emit(name) {
			if (state.waiting && state.waiting === name) {
				state.waiting = '';
				void enter(after(pos, script.nodes[pos]?.next));
			}
		}
	};

	async function enter(i) {
		const mine = ++run;
		while (mine === run) {
			const node = script.nodes[i];
			if (!node) return finish();
			pos = i;
			state.id = node.id ?? `#${i}`;
			state.tip = null;
			state.choices = null;
			state.waiting = '';
			if ('focus' in node) (state.focus = node.focus ?? null), (state.focusLabel = '');
			if ('at' in node) state.at = node.at ?? '';
			const kind = kindOf(node);
			if (node.say) {
				state.line = { ...node.say, n: ++lines };
				if (trail.at(-1) !== i) trail.push(i);
			}
			state.canGoBack = trail.length > 1 && (kind === 'say' || kind === 'choice');
			if (kind === 'say') {
				state.canAdvance = true;
				return;
			}
			state.canAdvance = false;
			if (kind === 'choice') {
				state.choices = node.choice;
				return;
			}
			if (kind === 'wait') {
				state.waiting = node.wait;
				return;
			}
			if (kind === 'end') return finish();
			if (kind === 'goto') {
				i = target(node.goto);
				continue;
			}
			// stage / do: the host works, then the script goes on
			state.busy = true;
			let answer;
			try {
				answer = await (kind === 'stage' ? host.stage?.(node.stage, node, api) : host.do?.(node.do, node, api));
			} finally {
				if (mine === run) state.busy = false;
			}
			if (mine !== run) return;
			if (kind === 'stage') (state.focus = null), (state.focusLabel = ''); // a stage's pointer ends with it
			i = typeof answer === 'string' ? target(answer) : after(i, node.next);
		}
	}

	return {
		state,
		start(id) {
			void enter(id === undefined ? 0 : target(id));
		},
		/** 다음 */
		advance() {
			if (state.done || !state.canAdvance) return;
			if (release) {
				// a waiting line of a stage
				const done = release;
				release = null;
				return done();
			}
			void enter(after(pos, script.nodes[pos].next));
		},
		/** 이전: the line before this one */
		back() {
			if (state.done || !state.canGoBack || trail.length < 2) return;
			trail.pop();
			const i = trail.pop();
			void enter(i);
		},
		choose(n) {
			const c = state.choices?.[n];
			if (c) void enter(target(c.goto));
		},
		emit: api.emit,
		skip: () => finish(true),
		dispose() {
			run++;
		}
	};
}
