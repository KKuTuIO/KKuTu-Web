/**
 * 단축키: one list of what a key can do (`ACTIONS`, ids and default keys follow the other 끄투 client most players come from), the keys
 * the player chose (kept in this browser), and one window listener that runs the handler of the screen that is open.
 *
 * A key is written the way it is shown (`Ctrl+1`, `F2`, `/`, `←`) and is read from `event.code`, never `event.key`: with the Korean layout on,
 * `event.key` for the Q key is `ㅂ`, so a binding on it would never fire. A key pressed while a syllable is still being composed is ignored.
 *
 * Who answers: a screen calls `onAction(id, handler)` while it is on screen (and returns the stop function; use it in an `$effect`).
 * The newest handler runs first; one that returns `false` lets the next one try. A key without Ctrl/Alt/Meta does nothing while a text field
 * has the focus, and nothing runs while a dialog is open, unless the action says otherwise (`always`).
 */

/** Where an action lives. Two actions may share a key only when their contexts differ (F2 starts a match in the lobby and readies in a room). */
export const CONTEXTS = ['any', 'lobby', 'room', 'replay', 'arcade', 'riddle', 'game'];

/**
 * @type {{id:string, group:'chat'|'game'|'lobby'|'replay'|'arcade', ctx:string, key:string, fixed?:boolean, repeat?:boolean, always?:boolean, inField?:boolean}[]}
 * `fixed`: shown, but not changeable (Esc and Enter keep their meaning everywhere). `repeat`: a held key runs it again.
 * `always`: also runs while a dialog is open. `inField`: also runs (without Ctrl/Alt/Meta) while a text field has the focus; its handler then
 * decides whether that field is the right one. The 스킬 of the other client are our 아이템 (Shift+1..6). Actions of features this client does
 * not have (이모티콘, 낱말집) are not listed. `waive` sends the "gg" the server takes as giving up in 초성 퀴즈 and 그림 퀴즈.
 */
export const ACTIONS = [
	{ id: 'chatPanelExpand', group: 'chat', ctx: 'any', key: '/' },
	...Array.from({ length: 10 }, (_, i) => ({ id: `chatRoom${i + 1}`, group: 'chat', ctx: 'any', key: `Ctrl+${(i + 1) % 10}` })),
	{ id: 'chatRoomHide', group: 'chat', ctx: 'any', key: 'Ctrl+Shift+Backspace' },
	{ id: 'inputChatSubmit', group: 'chat', ctx: 'game', key: 'Ctrl+Enter' },
	{ id: 'lobbyStart', group: 'lobby', ctx: 'lobby', key: 'Enter' },
	{ id: 'quickStart', group: 'lobby', ctx: 'lobby', key: 'F2' },
	{ id: 'roomCTA', group: 'game', ctx: 'room', key: 'F2' },
	{ id: 'escape', group: 'game', ctx: 'any', key: 'Esc', fixed: true },
	{ id: 'inputSubmit', group: 'game', ctx: 'game', key: 'Enter', fixed: true },
	...Array.from({ length: 6 }, (_, i) => ({ id: `item${i + 1}`, group: 'game', ctx: 'game', key: `Shift+${i + 1}`, inField: true })),
	{ id: 'waive', group: 'game', ctx: 'game', key: 'Shift+`', inField: true },
	{ id: 'hint1', group: 'game', ctx: 'riddle', key: '1' },
	{ id: 'replayToggle', group: 'replay', ctx: 'replay', key: 'Space' },
	{ id: 'replaySeekBackward', group: 'replay', ctx: 'replay', key: '←', repeat: true },
	{ id: 'replaySeekForward', group: 'replay', ctx: 'replay', key: '→', repeat: true },
	{ id: 'soksokSubmit', group: 'arcade', ctx: 'arcade', key: 'Space' },
	{ id: 'soksokCancel', group: 'arcade', ctx: 'arcade', key: 'Esc', always: true }
];
export const GROUPS = ['lobby', 'chat', 'game', 'replay', 'arcade'];

const BY_ID = Object.fromEntries(ACTIONS.map((a) => [a.id, a]));
const STORE = 'kkutu-play-keys';

/** What `event.code` is called in a key's written form. */
const NAMES = { Slash: '/', Backquote: '`', Backslash: '\\', Minus: '-', Equal: '=', Comma: ',', Period: '.', Semicolon: ';', Quote: "'", BracketLeft: '[', BracketRight: ']', ArrowLeft: '←', ArrowRight: '→', ArrowUp: '↑', ArrowDown: '↓', Escape: 'Esc', Space: 'Space' };
const MODIFIER_CODES = /^(Control|Shift|Alt|Meta|OS)(Left|Right)$/;

/** The written form of the key the event is (modifiers first), or '' for a modifier on its own or a key with no name. */
export function comboOf(e) {
	const code = String(e.code ?? '');
	if (!code || MODIFIER_CODES.test(code) || code === 'Unidentified') return '';
	let key = NAMES[code];
	if (!key) {
		const m = /^(?:Key([A-Z])|Digit(\d)|Numpad(\d))$/.exec(code);
		key = m ? (m[1] ?? m[2] ?? m[3]) : /^(F\d{1,2}|Enter|Tab|Backspace|Delete|Insert|Home|End|PageUp|PageDown)$/.test(code) ? code : '';
	}
	if (!key) return '';
	return [e.ctrlKey && 'Ctrl', e.altKey && 'Alt', e.shiftKey && 'Shift', e.metaKey && 'Meta', key].filter(Boolean).join('+');
}

/** A combo as the player reads it ("Meta" is the ⌘ key on a Mac). */
export const label = (combo) => String(combo ?? '').replace('Meta', typeof navigator !== 'undefined' && /Mac/i.test(navigator.platform) ? '⌘' : 'Win');

function load() {
	let saved = {};
	try {
		saved = JSON.parse(localStorage.getItem(STORE) ?? 'null') ?? {};
	} catch {
		/* private mode or damaged data: the defaults */
	}
	return Object.fromEntries(ACTIONS.map((a) => [a.id, a.fixed ? a.key : typeof saved[a.id] === 'string' ? saved[a.id] : a.key]));
}

/** action id -> its key now. */
export const bindings = $state(load());

function save() {
	try {
		const changed = Object.fromEntries(ACTIONS.filter((a) => !a.fixed && bindings[a.id] !== a.key).map((a) => [a.id, bindings[a.id]]));
		if (Object.keys(changed).length) localStorage.setItem(STORE, JSON.stringify(changed));
		else localStorage.removeItem(STORE);
	} catch {
		/* private mode */
	}
}

const overlaps = (a, b) => a === b || a === 'any' || b === 'any';

/** The other action that already has this key where this one is used, or null. */
export function conflictOf(id, combo) {
	const me = BY_ID[id];
	if (!me) return null;
	return ACTIONS.find((a) => a.id !== id && bindings[a.id] === combo && overlaps(a.ctx, me.ctx)) ?? null;
}

/** Gives an action a key. Returns null when done, or the action that has the key already (nothing changes then). */
export function setBinding(id, combo) {
	const a = BY_ID[id];
	if (!a || a.fixed || !combo) return null;
	const other = conflictOf(id, combo);
	if (other) return other;
	bindings[id] = combo;
	save();
	return null;
}
export function resetBinding(id) {
	const a = BY_ID[id];
	if (!a || a.fixed) return;
	bindings[id] = a.key;
	save();
}
export function resetBindings() {
	for (const a of ACTIONS) bindings[a.id] = a.key;
	save();
}

// ---- the listener -----------------------------------------------------------------------------------------------------------------

/** @type {{id:string, fn:(e:KeyboardEvent)=>void|false}[]} */
const handlers = [];
let installed = false;

/** Whether a text field has the focus (a key without Ctrl/Alt/Meta then belongs to the field). */
export function typing(el = typeof document !== 'undefined' ? document.activeElement : null) {
	if (!el) return false;
	const tag = el.tagName;
	if (tag === 'TEXTAREA' || el.isContentEditable || el.getAttribute?.('role') === 'textbox') return true;
	return tag === 'INPUT' && !/^(checkbox|radio|button|submit|range|file|color|reset|image)$/.test(/** @type {HTMLInputElement} */ (el).type);
}

function dispatch(e) {
	if (e.defaultPrevented || e.isComposing || e.keyCode === 229 || capturing) return;
	const combo = comboOf(e);
	if (!combo) return;
	const bare = !e.ctrlKey && !e.altKey && !e.metaKey;
	const inField = typing(/** @type {Element} */ (e.target instanceof Element ? e.target : document.activeElement));
	const dialog = !!document.querySelector('dialog[open]');
	for (let i = handlers.length - 1; i >= 0; i--) {
		const h = handlers[i];
		const a = BY_ID[h.id];
		if (!a || bindings[a.id] !== combo || (e.repeat && !a.repeat)) continue;
		if (inField && bare && !a.inField) continue;
		if (dialog && !a.always) continue;
		if (h.fn(e) === false) continue;
		e.preventDefault();
		return;
	}
}

/**
 * Runs `fn` when the key of action `id` is pressed, for as long as the caller keeps it (call the returned function to stop).
 * Meant for `$effect(() => onAction('replayToggle', toggle))`.
 */
export function onAction(id, fn) {
	if (!installed && typeof window !== 'undefined') {
		window.addEventListener('keydown', dispatch);
		installed = true;
	}
	const h = { id, fn };
	handlers.push(h);
	return () => {
		const i = handlers.indexOf(h);
		if (i >= 0) handlers.splice(i, 1);
	};
}

// ---- choosing a key (the settings tab) --------------------------------------------------------------------------------------------

let capturing = false;
/** While the settings tab waits for a key, nothing else may react to it. */
export function setCapturing(on) {
	capturing = on;
}
