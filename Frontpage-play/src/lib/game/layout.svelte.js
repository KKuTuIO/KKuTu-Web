/**
 * Sizes of the two resizable lobby bars, kept in this browser: the side bar's width and the chat panel's height (px).
 * The limits live here so the drag handle, the keyboard and a saved value from an older/larger screen all agree.
 */
const KEY = 'kkutu-play-layout';

export const SIDE = { min: 240, max: 480, def: 288 }; // 15rem .. 30rem, default is the old w-72
export const CHAT = { min: 160, max: 480, def: 240 }; // default is the old h-60

export const clamp = (v, min, max) => Math.min(max, Math.max(min, v));

function load() {
	let o = {};
	try {
		o = JSON.parse(localStorage.getItem(KEY) ?? 'null') ?? {};
	} catch {
		/* private mode or damaged data: use the defaults */
	}
	const num = (v, r) => (Number.isFinite(v) ? clamp(Math.round(v), r.min, r.max) : r.def);
	// `sideOpen`: whether the lobby's side bar is open where it can be folded away (the two columns under 1024 px); null: not chosen, the page decides
	return { side: num(o.side, SIDE), chat: num(o.chat, CHAT), sideOpen: typeof o.sideOpen === 'boolean' ? o.sideOpen : null };
}

export const layout = $state(load());

export function setLayout(name, value) {
	const r = name === 'side' ? SIDE : name === 'chat' ? CHAT : null;
	if (!r || !Number.isFinite(value)) return;
	layout[name] = clamp(Math.round(value), r.min, r.max);
	try {
		localStorage.setItem(KEY, JSON.stringify({ ...layout }));
	} catch {
		/* private mode */
	}
}

/** Fold the side bar away or open it again (kept in this browser). */
export function setSideOpen(open) {
	layout.sideOpen = !!open;
	try {
		localStorage.setItem(KEY, JSON.stringify({ ...layout }));
	} catch {
		/* private mode */
	}
}
