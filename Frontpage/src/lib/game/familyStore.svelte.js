/**
 * 패밀리 state and actions, over the game server's `family` packet (see KKuTu-Game-Private/server/sub/utils/Family.ts).
 *
 * The server decides everything. An action sends `{type:'family', op, ...}` and resolves with the server's `familyRes` ({ok, reason}); the new
 * state arrives as a `family` snapshot that replaces what is here. Chat lines are added at once for the one who sends them.
 *
 *   FamilySummary { id, name, level, members, capacity, intro, recruiting, master }
 *   Member        { accountId, name (대표 프로필), activeName (the profile in use when it is not the 대표 one, else ''), role, online,
 *                   status, place, server, weekly (contribution this week), equip }
 *   Family        { id, name, intro, recruiting, fame, level, into, need, capacity, members: Member[], attendance: {today, me},
 *                   applicants: {accountId, name, equip}[] (managers only) }
 */
import { t } from './i18n.svelte.js';
import { toast } from './ui/toast.svelte.js';
import { FAMILY_CHAT_MAX, FAMILY_CREATE_COST, FAMILY_CREATE_LEVEL } from './familyModel.js';

const ANSWER_TIMEOUT = 8000;
const LIST_DELAY = 250;

class FamilyStore {
	/** @type {object|null} my family, or null */
	mine = $state(null);
	/** My account's id, as the members are listed by. */
	me = $state('');
	/** @type {object[]} families open to look through (the server's answer to the last search) */
	directory = $state([]);
	/** @type {number[]} ids of the families I asked to join */
	applied = $state([]);
	/** @type {object[]} those families, to show them whether or not they still recruit */
	appliedFamilies = $state([]);
	/** @type {{who:string, rep?:string, accountId?:string, msg:string, at:number, mine?:boolean, system?:boolean}[]} */
	chat = $state([]);
	unread = $state(0);
	/** True once the first snapshot has come: before that "no family" does not mean anything. */
	loaded = $state(false);
	/** The chat tab is on screen, so a line is read as it comes. */
	watching = false;

	_send = null;
	_waiters = new Map(); // op -> [{resolve, timer}]
	_quiet = false; // my own join/leave is coming back as a snapshot: nothing to announce
	_listTimer = 0;

	get applicantCount() {
		return this.mine?.applicants?.length ?? 0;
	}

	/** Hands over the way to the server (the connection's `send`). */
	attach(send) {
		this._send = send;
	}

	/** The connection is gone: nothing here is true any more. */
	detach() {
		this._send = null;
		clearTimeout(this._listTimer);
		for (const list of this._waiters.values()) for (const w of list) (clearTimeout(w.timer), w.resolve({ ok: false, reason: 'timeout' }));
		this._waiters.clear();
		Object.assign(this, { mine: null, me: '', directory: [], applied: [], appliedFamilies: [], chat: [], unread: 0, loaded: false });
	}

	/** My role in my family. */
	role(meId = this.me) {
		return this.mine?.members.find((m) => m.accountId === meId)?.role ?? null;
	}

	/** Why I cannot found a family right now ('' when I can): 'joined' | 'level' | 'money'. The server checks again. */
	createBlocker(me) {
		if (this.mine) return 'joined';
		if ((me.lv ?? 0) < FAMILY_CREATE_LEVEL) return 'level';
		if ((me.money ?? 0) < FAMILY_CREATE_COST) return 'money';
		return '';
	}

	// ---- asking the server ----

	/** Sends one op and resolves with the server's answer to it. A failure is said to the player unless `quiet`. */
	request(op, data = {}, { quiet = false } = {}) {
		if (!this._send) return Promise.resolve({ ok: false, reason: 'offline' });
		return new Promise((resolve) => {
			const list = this._waiters.get(op) ?? [];
			const waiter = {
				resolve: (r) => {
					if (!r.ok && !quiet) toast(t(`play.family.error.${r.reason}`), { kind: 'error' });
					resolve(r);
				},
				timer: setTimeout(() => {
					this._waiters.set(op, (this._waiters.get(op) ?? []).filter((w) => w !== waiter));
					waiter.resolve({ ok: false, reason: 'timeout' });
				}, ANSWER_TIMEOUT)
			};
			list.push(waiter);
			this._waiters.set(op, list);
			this._send('family', { op, ...data });
		});
	}

	/** Asks for my family (and attends, once a day). */
	fetch() {
		this._send?.('family', { op: 'get' });
	}

	/** Asks for the families that recruit, by (part of) a name; the answer replaces `directory`. Waits a moment while the player types. */
	fetchList(query = '') {
		clearTimeout(this._listTimer);
		this._listTimer = setTimeout(() => this._send?.('family', { op: 'list', query: String(query).trim().slice(0, 40) }), LIST_DELAY);
	}

	create({ name, intro, recruiting }) {
		return this.request('create', { name: name.trim(), intro: intro.trim(), recruiting: !!recruiting }, { quiet: true }).then((r) => ((this._quiet = r.ok), r));
	}
	apply(id) {
		return this.request('apply', { id });
	}
	cancelApply(id) {
		return this.request('cancel', { id });
	}
	leave() {
		return this.request('leave').then((r) => ((this._quiet = r.ok), r));
	}
	update(patch) {
		return this.request('update', patch);
	}
	answer(account, ok) {
		return this.request('answer', { account, ok });
	}
	kick(account) {
		return this.request('kick', { account });
	}
	setRole(account, role) {
		return this.request('role', { account, role });
	}
	/** The profile I am using now stands for me in the family. */
	setRepresentative() {
		return this.request('rep');
	}

	// ---- chat ----

	/** Sends a line; it shows at once for me, and the server relays it to the others. */
	send(text, who, rep = '') {
		const msg = String(text).trim();
		if (!this.mine || !msg || !this._send) return false;
		this.chat = [...this.chat, { who, rep, accountId: this.me, msg, at: Date.now(), mine: true }].slice(-FAMILY_CHAT_MAX);
		this._send('family', { op: 'chat', value: msg.slice(0, 200) });
		return true;
	}

	/** The chat tab opens or closes. */
	watch(on) {
		this.watching = on;
		if (on) this.unread = 0;
	}
	markRead() {
		this.unread = 0;
	}

	// ---- what the server says ----

	receive(pkt) {
		switch (pkt.type) {
			case 'family': {
				const had = this.mine;
				this.me = pkt.me ?? this.me;
				this.applied = Array.isArray(pkt.applied) ? pkt.applied : [];
				this.appliedFamilies = Array.isArray(pkt.appliedFamilies) ? pkt.appliedFamilies : [];
				this.mine = pkt.mine ?? null;
				const first = !this.loaded;
				this.loaded = true;
				if (!this.mine) this.chat = []; // the chat belongs to the family
				else if (had?.id !== this.mine.id) this.chat = [{ system: true, msg: had ? 'joined' : 'welcome', at: Date.now() }];
				if (!first && !this._quiet) {
					if (!had && this.mine) toast(t('play.family.accepted', { name: this.mine.name }), { kind: 'success' });
					else if (had && !this.mine) toast(t('play.family.removed', { name: had.name }), { kind: 'info' });
				}
				this._quiet = false;
				break;
			}
			case 'familyList':
				this.directory = Array.isArray(pkt.families) ? pkt.families : [];
				break;
			case 'familyChat':
				if (!this.mine || pkt.familyId !== this.mine.id) break;
				this.chat = [...this.chat, { who: pkt.who, rep: pkt.rep, accountId: pkt.account, msg: pkt.msg, at: pkt.at ?? Date.now() }].slice(-FAMILY_CHAT_MAX);
				if (!this.watching) this.unread++;
				break;
			case 'familyRes': {
				const list = this._waiters.get(pkt.op);
				const waiter = list?.shift();
				if (waiter) {
					clearTimeout(waiter.timer);
					waiter.resolve({ ok: !!pkt.ok, reason: pkt.reason });
				} else if (!pkt.ok && pkt.op !== 'get' && pkt.op !== 'list') toast(t(`play.family.error.${pkt.reason}`), { kind: 'error' }); // e.g. a chat line that was not allowed; never the background refresh (a guest is told 'guest' by every one of those)
				break;
			}
		}
	}
}

export const family = new FamilyStore();
