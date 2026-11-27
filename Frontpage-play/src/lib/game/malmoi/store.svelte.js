/**
 * 말모이: what the screen shows, kept as the server last told it (`/api/malmoi`). Every change goes to the server first and then the book is read
 * again, so the screen never shows more than the server accepted; a refused change throws an Error with the server's own message.
 */
import { t } from '../i18n.svelte.js';
import { LIMITS } from './model.js';

const DAY = 24 * 3600000;

/** The look of the one using the screen (and the catalogue and layer order every Moremi needs), and their nickname; the screen fills it in. */
export const look = $state({ name: '', equip: {}, shop: {}, parts: [] });

/** The player: the level the server counts, Ping (the game's own number), the license and today's edits. */
export const me = $state({ level: 0, ping: 0, licenseUntil: 0, licenseIssued: 0, licenseNo: '', licenseCode: '', editsToday: 0, daily: LIMITS.dailyEdits, ready: false });
/** The license card on screen; `fresh` when it has just been issued. */
export const card = $state({ open: false, fresh: false });
export const showLicense = (fresh = false) => Object.assign(card, { open: true, fresh });
export const nav = $state({ view: 'list', id: '', tab: 'words', list: 'browse' });
/** The list's 정렬 및 필터: `only` is '' | 'ext' (확장팩만) | 'new' (새 주제만). */
export const filter = $state({ sort: 'popular', only: '', unregistered: false });

/** `list` every book seen, `scopes` which ids each tab holds (in the server's order), `registered` the ids I registered. */
export const book = $state({ list: [], scopes: { browse: [], registered: [], mine: [] }, registered: [], loading: false });

// ---- talking to the server ----

async function call(method, path, body) {
	let res;
	try {
		res = await fetch(`/api/malmoi${path}`, { method, headers: body === undefined ? undefined : { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
	} catch {
		throw new Error(t('play.malmoi.error.network'));
	}
	let data = null;
	try {
		data = await res.json();
	} catch {
		/* no body */
	}
	if (!res.ok) throw new Error(data?.message ?? data?.error_description ?? (res.status === 401 ? t('play.malmoi.error.login') : t('play.malmoi.error.failed')));
	return data;
}

/** Runs a change and says what went wrong in a toast; resolves to the result or null. */
export async function attempt(work, toast) {
	try {
		return await work();
	} catch (e) {
		toast?.(e.message, { kind: 'error' });
		return null;
	}
}

// ---- books ----

const sid = (id) => String(id);
const dayAt = (day) => new Date(`${day}T12:00:00`).getTime();

/** What the list and a detail share. A summary read later never takes away what a detail brought. */
function take(raw) {
	const id = sid(raw.id);
	const have = book.list.find((d) => d.id === id);
	const next = {
		id,
		kind: raw.kind,
		base: raw.base ?? '',
		title: raw.title,
		desc: raw.desc ?? '',
		cover: raw.cover ?? {},
		state: raw.state,
		owner: raw.owner ?? '',
		wordCount: raw.words ?? 0,
		liveCount: raw.live ?? 0,
		registrants: raw.registrants ?? 0,
		preview: raw.preview ?? [],
		updatedAt: raw.updatedAt ?? 0,
		mine: !!raw.mine
	};
	if (have) return Object.assign(have, next);
	const fresh = { ...next, words: [], more: false, changes: [], blocks: [], tops: [], people: [], activity: [], criteria: { detail: '', include: '', source: '' }, manages: false, editor: !!raw.mine, invite: null, locked: false, baseWords: [], editors: [], review: '', promotion: '', loaded: false };
	book.list.push(fresh);
	return book.list.find((d) => d.id === id);
}

export const find = (id) => book.list.find((d) => d.id === sid(id));
export const registeredBooks = () => book.registered.map(find).filter(Boolean);

export async function loadMe() {
	const r = await call('GET', '/license');
	Object.assign(me, {
		level: r.level,
		editsToday: r.edits,
		daily: r.daily,
		licenseUntil: r.active ? r.until : 0,
		licenseIssued: r.active ? r.issuedAt : 0,
		licenseNo: r.active ? r.number : '',
		licenseCode: r.active ? codeOf(r.number) : '',
		ready: true
	});
	return r;
}

/** A short code printed on the card, made from its number (the same number always gives the same code). */
function codeOf(number) {
	let h = 7;
	for (const c of number) h = (h * 31 + c.charCodeAt(0)) >>> 0;
	return Array.from({ length: 6 }, (_, i) => '0123456789ABCDEFGHJKLMNPQRSTUVWXYZ'[Math.floor(h / 34 ** i) % 34]).join('');
}

/** One tab's books (and, for 내 등록, which ones I registered). */
export async function loadList(scope, query = '') {
	book.loading = true;
	try {
		const r = await call('GET', `?scope=${scope}&q=${encodeURIComponent(query)}`);
		book.scopes[scope] = r.items.map((raw) => take(raw).id);
		if (scope === 'registered') book.registered = book.scopes.registered.slice();
		for (const raw of r.items) if (raw.registered && !book.registered.includes(sid(raw.id))) book.registered.push(sid(raw.id));
	} finally {
		book.loading = false;
	}
}

/** Everything a book's screen shows besides its words. */
export async function openBook(id) {
	const [detail, changes, contrib] = await Promise.all([call('GET', `/${id}`), call('GET', `/${id}/changes`), call('GET', `/${id}/contrib?days=182`)]);
	const d = take(detail);
	Object.assign(d, {
		criteria: { detail: '', include: '', source: '', ...(detail.criteria ?? {}) },
		tops: detail.tops ?? [],
		manages: !!detail.manages,
		editor: !!detail.editor,
		invite: detail.invite ?? null,
		locked: !!detail.locked,
		editors: detail.editors ?? [],
		review: detail.review ?? '',
		blocks: detail.blocks ?? [],
		changes: changes.items.map((c) => ({ id: c.id, at: c.at, who: c.who ?? '', type: c.type, text: c.word ?? '', reason: c.reason ?? '', reverted: !!c.reverted })),
		people: contrib.people,
		activity: contrib.activity.map((a) => ({ at: dayAt(a.day), who: a.who, n: a.n })),
		loaded: true
	});
	return d;
}

/** The words of a book, 50 at a time: `page` 0 starts over (a new search), the next pages are added. */
export async function loadWords(d, query = '', page = 0) {
	const r = await call('GET', `/${d.id}/words?q=${encodeURIComponent(query)}&page=${page}`);
	const rows = r.items.map((w) => ({
		id: w.id,
		text: w.text,
		meanings: w.meanings ?? [],
		source: w.source ?? '',
		by: w.by ?? '',
		appliesAt: w.appliesAt,
		removeAt: w.removeAt ?? 0,
		next: w.next ? { meanings: w.next.meanings ?? [], source: w.next.source ?? '', appliesAt: Date.parse(w.next.applies_at) } : null
	}));
	d.words = page === 0 ? rows : [...d.words, ...rows];
	d.more = rows.length === 50;
	return rows.length;
}

const refreshing = async (d, work) => {
	const result = await work();
	await Promise.all([openBook(d.id), loadMe().catch(() => {})]);
	return result;
};

export async function register(id, on) {
	const r = await call(on ? 'PUT' : 'DELETE', `/${id}/registration`);
	book.registered = on ? [...new Set([...book.registered, sid(id)])] : book.registered.filter((x) => x !== sid(id));
	const d = find(id);
	if (d) d.registrants = r.registrants;
}

export const joinByLink = async (token) => {
	const r = await call('POST', `/join/${encodeURIComponent(token)}`, {});
	await loadList('registered');
	return r.id;
};

// ---- who may do what ----

export const licensed = () => me.licenseUntil > Date.now();
export const isOwner = (d) => !!d.mine;
export const isManager = (d) => !!d.manages;
export const isEditorOf = (d) => !!d.editor || !!d.mine;
export const topOf = (d) => d.tops ?? [];

/** May I add and change words here, and if not, why. (The server decides again.) */
export function editing(d) {
	if (d.locked) return { ok: false, why: t('play.malmoi.edit.locked') };
	if (d.state === 'unlisted') return isEditorOf(d) ? { ok: true } : { ok: false, why: t('play.malmoi.edit.editorsOnly') };
	if (!licensed()) return { ok: false, why: t('play.malmoi.edit.needLicense'), license: true };
	return { ok: true };
}

/** Edits left today (unlimited for an unlisted one). */
export const editsLeft = (d) => (d.state === 'unlisted' ? Infinity : Math.max(0, me.daily - me.editsToday));

// ---- words ----

export const addWord = (d, { text, meanings, source }) => refreshing(d, () => call('POST', `/${d.id}/words`, { text, meanings, source }));
export const changeWord = (d, w, { meanings, source }) => refreshing(d, () => call('PUT', `/${d.id}/words/${w.id}`, { meanings, source }));
export const removeWord = (d, w, reason) => refreshing(d, () => call('DELETE', `/${d.id}/words/${w.id}?reason=${encodeURIComponent(reason ?? '')}`));

/** Saves the rows a preview marked `add` or `meaning`, 200 at a time; resolves to the numbers the server counted. */
export async function addRows(d, rows) {
	const todo = rows.filter((r) => r.status === 'add' || r.status === 'meaning').map((r) => ({ text: r.word, mean: r.mean ?? '', source: r.source ?? '' }));
	const total = { added: 0, meaning: 0, dup: 0, failed: 0, stopped: false };
	for (let i = 0; i < todo.length && !total.stopped; i += 200) {
		const r = await call('POST', `/${d.id}/words/bulk`, { rows: todo.slice(i, i + 200) });
		for (const k of ['added', 'meaning', 'dup', 'failed']) total[k] += r[k];
		total.stopped = r.stopped;
	}
	await Promise.all([openBook(d.id), loadMe().catch(() => {})]);
	return total;
}

export const canRevert = (c) => !c.reverted && (c.type === 'add' || c.type === 'remove');
export const revert = (d, c) => refreshing(d, () => call('POST', `/${d.id}/changes/${c.id}/revert`, {}));
export const block = (d, c, days, reason) => refreshing(d, () => call('POST', `/${d.id}/changes/${c.id}/block`, { days, reason }));
export const unblock = (d, name) => refreshing(d, () => call('DELETE', `/${d.id}/blocks?name=${encodeURIComponent(name)}`));
export const report = (d, { word, reason, detail }) => call('POST', `/${d.id}/reports`, { word, reason, detail });

// ---- editors, delegation, review ----

export const invite = (d, name) => refreshing(d, () => call('POST', `/${d.id}/editors`, { name }));
export const delegate = (d, name) => refreshing(d, () => call('POST', `/${d.id}/delegate`, { name }));
export const reissueInvite = (d) => refreshing(d, () => call('POST', `/${d.id}/invite/reissue`, {}));
export const requestReview = (d) => refreshing(d, () => call('POST', `/${d.id}/review`, {}));

/** The editor invitations waiting for my answer. */
export const invitations = $state({ items: [] });
export async function loadInvitations() {
	invitations.items = (await call('GET', '/invitations')).items;
}
export async function answerInvitation(id, accept) {
	await call('POST', `/${id}/invitation/${accept ? 'accept' : 'decline'}`, {});
	await Promise.all([loadInvitations(), loadList('mine'), loadList('registered')]);
}

// ---- license ----

/** Swears the oath and pays. Resolves to the license (and the Ping left, for the game's own number). */
export async function takeLicense(oath) {
	const r = await call('POST', '/license', { oath });
	await loadMe();
	return r;
}

/** What is printed on my license. */
export function licenseInfo() {
	return { issued: me.licenseIssued, until: me.licenseUntil, no: me.licenseNo, code: me.licenseCode };
}

// ---- making ----

export const myUnlisted = () => book.scopes.mine.map(find).filter((d) => d && d.mine && d.state === 'unlisted').length;

export async function create(form) {
	const r = await call('POST', '', form);
	await Promise.all([loadList('mine'), loadList('registered')]);
	return r.id;
}

export const update = (d, form) => refreshing(d, () => call('PUT', `/${d.id}`, form));

// ---- a room's 말모이 ----

/** The 말모이 on in a room: all I registered, when I made the room (the server option says it is on). Others are not told which. */
export function booksIn(room, myId) {
	return room?.opts?.malmoi && room.master === myId ? registeredBooks() : [];
}

/** What the game's own screen needs told after Ping was paid here (the page fills it in). */
export const bridge = { setMoney: (_) => {} };
