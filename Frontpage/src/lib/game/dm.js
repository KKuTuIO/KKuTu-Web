/**
 * 귓속말 as conversations: one thread per person (by id, so a nickname change keeps the thread), kept in the lobby state.
 * The server does not store whispers (they reach only whoever is online), so threads live here: in memory, and in sessionStorage so a
 * reload of the page does not lose them. Pure functions on the state; the UI reads `state.dm`.
 */
import { isHidden } from './chatFilter.js';

export const DM_CAP = 200; // messages kept per thread
export const DM_THREADS = 50; // threads kept (the quietest go first)
const KEY = (id) => `kkutu-dm:${id}`;
const compact = (s) => String(s ?? '').replace(/\s/g, '');

export const newDm = () => ({
	threads: {}, // peer id -> {id, name, lastAt, unread, messages: [{mine, msg, shown?, at, system?}]}
	active: null, // the thread open in the 귓속말 tab (null: the list)
	viewing: null, // the thread that is on screen right now (a message for it does not count as unread)
	last: null // {id, name, msg, at, n} the latest message that arrived, for the notice
});

/** A person's name as people type it after /e: no spaces, `#tag` when there is one. */
export const peerName = (u) => compact(`${u?.name ?? ''}${u?.tag ? `#${u.tag}` : ''}`);

function thread(dm, id, name) {
	let th = dm.threads[id];
	if (!th) th = dm.threads[id] = { id, name: name ?? '', lastAt: 0, unread: 0, messages: [] };
	if (name) th.name = name;
	return th;
}

function trim(dm) {
	const ids = Object.keys(dm.threads);
	if (ids.length <= DM_THREADS) return;
	ids.sort((a, b) => dm.threads[a].lastAt - dm.threads[b].lastAt).slice(0, ids.length - DM_THREADS).forEach((id) => delete dm.threads[id]);
}

/** Add a line to a thread (mine, theirs, or a system line such as "not here"). Returns the thread. */
export function dmAdd(state, id, { name, msg, shown, mine = false, system = false, at = Date.now() }) {
	const dm = state.dm;
	const th = thread(dm, id, name);
	th.messages.push({ mine, system, msg, shown: shown ?? msg, at });
	if (th.messages.length > DM_CAP) th.messages.splice(0, th.messages.length - DM_CAP);
	if (!system) th.lastAt = at;
	else th.lastAt = Math.max(th.lastAt, at);
	if (!mine && !system && dm.viewing !== id) th.unread++;
	trim(dm);
	dmSave(state);
	return th;
}

/**
 * A whisper arrived (`chat` packet with `from`). Whoever I filter out (blocked, muted, 귓속말 거부) never gets a thread.
 * @returns the thread, or null when it was hidden
 */
export function dmReceive(state, pkt, shown) {
	const uid = pkt.profile?.id ?? '';
	if (!uid || isHidden({ whisper: true, uid }, state)) return null;
	const name = compact(String(pkt.profile?.title ?? pkt.profile?.name ?? pkt.from ?? '?')).split('#')[0];
	const at = Date.now();
	const th = dmAdd(state, uid, { name, msg: pkt.value, shown, at });
	state.dm.last = { id: uid, name, msg: pkt.value, at, n: (state.dm.last?.n ?? 0) + 1 };
	return th;
}

/** I read a thread (it is on screen): nothing in it is unread. */
export function dmRead(state, id) {
	const th = state.dm.threads[id];
	if (th && th.unread) {
		th.unread = 0;
		dmSave(state);
	}
}

export function dmRemove(state, id) {
	delete state.dm.threads[id];
	if (state.dm.active === id) state.dm.active = null;
	dmSave(state);
}

export const dmUnread = (state) => Object.values(state.dm.threads).reduce((n, th) => n + th.unread, 0);

/** The conversation list, newest first: what the 귓속말 tab shows. */
export function dmList(state) {
	return Object.values(state.dm.threads)
		.filter((th) => th.messages.length)
		.sort((a, b) => b.lastAt - a.lastAt)
		.map((th) => {
			const last = [...th.messages].reverse().find((m) => !m.system) ?? th.messages[th.messages.length - 1];
			return { id: th.id, name: th.name, unread: th.unread, lastAt: th.lastAt, preview: last?.msg ?? '', mine: !!last?.mine };
		});
}

/**
 * The people I can start a conversation with: those online, not me, not robots, matching `query` (name or #tag, spaces ignored);
 * friends first, then people I talked to, then the rest, each by name.
 */
export function dmMembers(state, query = '', limit = 60) {
	const q = compact(query).toLowerCase().replace(/^@/, '');
	const rows = Object.values(state.users)
		.filter((u) => u.id !== state.id && !u.robot && u.name)
		.map((u) => ({ id: u.id, name: u.name, tag: u.tag ?? '', handle: peerName(u), friend: u.id in (state.friendsData ?? {}), talked: !!state.dm.threads[u.id]?.messages.length, user: u }))
		.filter((r) => !q || r.handle.toLowerCase().includes(q));
	const rank = (r) => (r.friend ? 0 : r.talked ? 1 : 2);
	rows.sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name, 'ko'));
	return rows.slice(0, limit);
}

/** Does `text` (the chat box) ask for a whisper target? "/e na" -> "na"; null when not typing one. */
export function whisperQuery(text) {
	const m = /^\/(?:귓|ㄷ|e)\s+([^\s]*)$/.exec(String(text));
	return m ? m[1] : null;
}

// ---- keeping it across a reload of the page ----
export function dmSave(state) {
	if (!state.id || typeof sessionStorage === 'undefined') return;
	try {
		const data = Object.fromEntries(Object.entries(state.dm.threads).map(([id, th]) => [id, { id, name: th.name, lastAt: th.lastAt, unread: th.unread, messages: th.messages }]));
		sessionStorage.setItem(KEY(state.id), JSON.stringify(data));
	} catch {
		/* private mode or full: the threads stay in memory */
	}
}

export function dmLoad(state) {
	if (!state.id || typeof sessionStorage === 'undefined') return;
	try {
		const data = JSON.parse(sessionStorage.getItem(KEY(state.id)) ?? 'null');
		if (data && typeof data === 'object') state.dm.threads = data;
	} catch {
		/* damaged: start empty */
	}
}

/** The time of a list row: today as a clock, another day as month/day. */
export function dmTime(at, now = Date.now(), lang = 'ko') {
	const d = new Date(at);
	const today = new Date(now);
	return d.toDateString() === today.toDateString()
		? d.toLocaleTimeString(lang, { hour: 'numeric', minute: '2-digit' })
		: d.toLocaleDateString(lang, { month: 'numeric', day: 'numeric' });
}
