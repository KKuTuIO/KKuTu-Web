/**
 * Pure translation of game-server packets into lobby state. No sockets, no DOM, so it
 * can be tested directly. The server speaks the same JSON the legacy client consumes.
 */
import { createSplitFilter } from './badwords.js';
import { isAdminCommand } from './commands.js';
import { getLevel } from './level.js';
import { applyGamePacket } from './gameModel.js';
import { t } from './i18n.svelte.js';
import { newDm, dmReceive, dmLoad } from './dm.js';

/** Server messages are HTML snippets (<br>, links); the UI shows plain text. */
export const plainText = (html = '') => String(html).replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]*>/g, '').trim();

/**
 * The text of a server error packet. A few messages carry a blank for a number the server sends in `message` (446 "최소 {0}단계의 오끄감 버프",
 * 447 "하루에 최대 {0}회까지"): the legacy client fills it (`.replace("{0}", message)`), so it is filled here too.
 */
export function errorText(labels, pkt) {
	const label = labels[`kkutu.js.error.${pkt.code}`];
	if (label === undefined) return plainText(pkt.message ?? '');
	return plainText(String(label).replace('{0}', pkt.message ?? ''));
}

export const initialState = () => ({
	assetsReady: false, // shop catalogue + fonts, so the lobby doesn't pop in half-drawn
	status: 'connecting', // connecting | setup | captcha | ready | reconnecting | closed | error
	outage: null, // while the connection is down: {kind: 'network'|'update'|'maintenance'|'unknown', name?, reason?, until?} (see outage.js)
	reconnect: null, // after a dropped connection: {attempt, max, nextAt (ms timestamp, null while trying), exhausted}
	error: null,
	captchaSiteKey: null,
	meta: { modes: [], options: {}, labels: {}, rules: {}, optionDefs: {}, moremiParts: [], moremiCategories: [], moremiGroups: {}, moremiEquips: [] },
	membership: 0,
	playTime: 0,
	okgCount: 0, // 오끄감 buff stack
	server: 0, // the channel I'm on
	levelGifts: {}, // level -> what reaching it gave ({level, ping, items, mail}), from the `obtain` packets that took me through levels
	levelTable: null, // every level's gift, as the server sent it: {level: [ping, [[item, q]], [[ticket, q]]]} (asked for when the level table opens)
	mails: [], // the mailbox: [{_id, name, content, hasRewards, claimable, claimEndAt}]
	notifications: [], // notices the server keeps until I confirm them: [{id, message, createdAt}]
	mailLoaded: false,
	events: { list: [] }, // 공지·행사: the catalog the server sent ({list: EventInfo[]}); at login without the detail blocks, with them after event.progress
	eventProgress: { missions: [], attendance: [] }, // my missions and attendance books
	eventsLoaded: false, // event.progress has answered (the page shows a skeleton until then)
	exchanges: {}, // the exchange shop of each event: {eventId: Exchange[]}
	eventReward: null, // {eventId, source, rewards, at} what the last claim gave
	invites: {}, // 친구 초대: {eventId: status} (my code, how far I am as the invited one, and my friends)
	inviteLinked: null, // the answer to a link followed: {result, at}
	campaigns: {}, // 캠페인: {eventId: status + `at`} (where each campaign is: the round that is on, the next, the ones over)
	tileResult: null, // {eventId, draws:[{cell, owned}], rewards, gauge?:{points, reached}, at}: the last 타일 룰렛 draw
	pendingInjeongHint: null, // words for the 어인정 nudge, held until the result is closed
	recentFrom: '', // the last player who whispered to me (compact nickname), for /답
	dm: newDm(), // 귓속말 conversations (see dm.js)
	surveys: null, // 설문 조사: [{name, desc, openAt, closeAt, gives, surveyUrl}] once asked for
	surveyToken: null, // {value, date}: what a survey page needs to know it is me
	replayReady: false, // a finished game was recorded and can be saved as a .kkio (see replayRec.js)
	boxSeq: 0, // counts the packets that carried my whole box: the last one of a gacha draw ends it
	box: null, // what I own: {item id: {value, expire?}} (flag items, keys starting with #, are left out)
	expired: [], // item ids that ran out while I was away (the server lists them on connect): shown once, then cleared
	gained: [], // items the server gave (a gacha draw sends one packet per prize): [{key, q}], taken by whoever shows them
	gifts: [], // what an admin gave me (`#give`): [{key, q}], taken by GiftHost, which shows them on the obtain screen
	obtained: null, // {rewards, at} what the last claimed mail gave
	tutorial: null, // {id, done, ping, at} the server's answer to a cleared tutorial lesson: what the account has cleared, and the 핑 this clear paid
	friends: {}, // id -> nickname
	friendsData: {}, // id -> {memo, nick, bf (best friend)}
	friendRequests: [], // [{from, at}] people asking to become my friend, until I answer
	friendPending: [], // [{target, name, at}] requests I sent that nobody has answered yet
	ratingRestrictionUntil: 0, // epoch seconds: until then I can only join 친선전 rooms (rating too low)
	myStatus: 'online', // what I chose to show: online | idle | dnd | invisible (the lobby's copy of me never says invisible)
	friendsStatus: {}, // id -> 'idle' | 'dnd' for the online friends who are not plain online
	friendResult: null, // {target, res, at} the answer to a request I sent
	myRoom: null,
	game: null, // the running (or just finished) game of my room, see gameModel.js
	secretKey: 0, // unlocks the words in turnEnd
	prompt: null, // latest server prompt, e.g. the guest's progress-transfer code
	kickVote: null, // {target, at} while a vote about removing someone is running
	kicked: null, // {at} set when I was removed from a room
	invite: null, // {by, from, at} an invitation waiting for my answer
	jl: { history: {}, mute: {} }, // join/leave notice throttling, per user
	preRoom: null,
	id: null,
	guest: true,
	admin: false,
	users: {},
	rooms: {},
	friendsOnline: {},
	chat: [],
	themePresets: [], // official 단어장 theme sets: {id, name, lang, themes[]}
	pingMs: null
});

function safeJson(v) {
	try {
		return JSON.parse(v);
	} catch {
		return {};
	}
}

/** "닉네임#1234" -> the name and the tag (empty when the nickname is fixed). */
export function splitNick(full = '') {
	const i = String(full).indexOf('#');
	return i < 0 ? { name: String(full), tag: '' } : { name: String(full).slice(0, i), tag: String(full).slice(i + 1) };
}

export function normalizeUser(raw) {
	const score = raw.data?.score ?? 0;
	const robot = !!raw.robot;
	const aiLevel = Math.min(5, Math.max(0, Number(raw.level) || 0)); // a practice partner of a mode without AI levels has none
	const full = robot ? `${t(`play.ai.level.${aiLevel}`)} ${t('play.ai.robot')}` : (raw.profile?.title ?? raw.profile?.name ?? raw.id ?? '');
	const { name, tag } = splitNick(full);
	return {
		id: raw.id,
		name,
		tag,
		fixed: !robot && !raw.guest && tag === '', // a fixed nickname has no #tag (legacy shows a check mark for it)
		level: robot ? aiLevel : null, // AI difficulty
		record: raw.data?.record ?? {}, // per mode: [plays, wins, score, ?, losses]
		lv: getLevel(score),
		score,
		money: raw.money ?? 0,
		wins: Object.values(raw.data?.record ?? {}).reduce((n, r) => n + (r?.[1] ?? 0), 0),
		equip: typeof raw.equip === 'string' ? safeJson(raw.equip) : (raw.equip ?? {}),
		place: raw.place ?? 0,
		exordial: raw.exordial ?? '', // the "소개 한마디"
		status: raw.status ?? 'online', // what they show: online | idle | dnd
		statusMessage: typeof raw.statusMessage === 'string' ? raw.statusMessage : '', // the line pinned on their profile
		rating: Number.isInteger(raw.rating) && raw.rating >= 0 && raw.rating <= 4 ? raw.rating : null, // 평점: 0 very low .. 4 very high; null when not known (offline)
		guest: !!raw.guest,
		robot,
		// Room state: ready / form ("J" join, "S" spectate) / team (0 solo, 1-4) / practice
		game: { ready: !!raw.game?.ready, form: raw.game?.form ?? 'J', team: Number(raw.game?.team ?? 0), practice: !!raw.game?.practice, score: Number(raw.game?.score ?? 0) }
	};
}

export function normalizeRoom(raw, meta) {
	const key = meta.modes[raw.mode];
	const label = meta.labels[`game.mode.${key}.name`] ?? key ?? '';
	const tags = Object.entries(raw.opts ?? {})
		.filter(([, on]) => on)
		.map(([k]) => meta.labels[`game.option.${meta.options[k] ?? k}.name`])
		.filter(Boolean);
	// Each switched-on option with its description (shown on hover, like the legacy header).
	const rules = Object.entries(raw.opts ?? {})
		.filter(([, on]) => on)
		.map(([k]) => {
			const name = meta.options[k] ?? k;
			return { key: k, label: meta.labels[`game.option.${name}.name`], expl: meta.labels[`game.option.${name}.expl`] ?? '' };
		})
		.filter((r) => r.label);
	return {
		rules,
		modeKey: key,
		no: raw.id,
		modeIndex: raw.mode,
		opts: raw.opts ?? {},
		title: raw.title ?? '',
		locked: !!raw.password,
		mode: label,
		tags,
		rounds: raw.round,
		time: raw.time,
		cur: raw.players?.length ?? 0,
		max: raw.limit,
		playing: !!raw.gaming,
		limit: raw.limit,
		injpick: raw.injpick ?? [],
		practice: !!raw.practice,
		players: (raw.players ?? []).map((p) => p.id),
		master: raw.master,
		game: { seq: raw.game?.seq ?? [], turn: raw.game?.turn ?? 0, round: raw.game?.round ?? 0, title: raw.game?.title ?? [] }
	};
}

/** Official theme sets from the server: drop malformed ones, like the legacy client (name <= 30 chars, <= 128 themes). */
export function normalizePresets(list) {
	return (Array.isArray(list) ? list : [])
		.filter((p) => p && typeof p.name === 'string' && p.name.trim() && Array.isArray(p.themes))
		.map((p, i) => ({
			id: String(p.id ?? `official-${i}`),
			name: p.name.trim().slice(0, 30),
			lang: p.lang === 'en' ? 'en' : 'ko',
			themes: [...new Set(p.themes.filter((x) => typeof x === 'string' && x.length <= 64))].slice(0, 128)
		}));
}

const mapOf = (obj, fn) => Object.fromEntries(Object.entries(obj ?? {}).map(([k, v]) => [k, fn(v)]));

/** A system line in the chat. */
/** How many chat lines the client keeps (the 대화 기록 reads them back; the chat panel shows the latest of them). */
export const CHAT_BUFFER = 1000;

/** My box as the client keeps it: flag items (`#...`) are not shown, a plain number is `{value}`. */
export function cleanBox(box) {
	const out = {};
	for (const [k, v] of Object.entries(box ?? {})) if (!k.startsWith('#')) out[k] = typeof v === 'number' ? { value: v } : v;
	return out;
}

export function notice(state, text) {
	if (!text) return;
	state.chat.push({ who: t('play.chat.system'), msg: text, at: Date.now(), system: true, mine: false });
	if (state.chat.length > CHAT_BUFFER) state.chat.splice(0, state.chat.length - CHAT_BUFFER);
}

/** The words of the 어인정 nudge waiting to be shown (cleared), or null. */
export function takeInjeongHint(state) {
	const words = state.pendingInjeongHint;
	state.pendingInjeongHint = null;
	return words?.length ? words : null;
}

/**
 * "X joined / left" lines, with the legacy flood guard: a user who comes and goes more than twice in 15 seconds is
 * muted for 60 seconds (once, with a notice about it). Operators are never announced.
 */
const splitFilter = createSplitFilter();

export function joinLeave(state, id, kind, now = Date.now()) {
	const u = state.users[id];
	if (!u || u.name.includes('운영자')) return;
	const jl = state.jl;
	if (now <= (jl.mute[id] ?? 0)) return;
	const recent = (jl.history[id] ?? []).filter((at) => now - at < 15_000);
	recent.push(now);
	jl.history[id] = recent;
	if (recent.length > 2) {
		jl.mute[id] = now + 60_000;
		notice(state, t('play.room.notice.flapping', { nick: u.name }));
	} else notice(state, t(`play.room.notice.${kind}`, { nick: u.name }));
}

/** The friend list arrives as {id: memo} or {id: {memo, nick, bf}}; keep both views in step. */
export function setFriends(state, raw) {
	state.friendsData = mapOf(raw, (f) => (typeof f === 'object' && f ? { memo: f.memo ?? f.nick ?? '', nick: f.nick ?? f.memo ?? '', bf: !!f.bf } : { memo: String(f), nick: String(f), bf: false }));
	state.friends = mapOf(state.friendsData, (f) => f.nick);
}

const uid_ = (pkt) => pkt.profile?.id ?? '';

/**
 * A notice written as raw HTML (the legacy client's format: `<h3>`, `<ul>`, `<a>` ...). This client writes descriptions in Markdown and would print
 * the tags as text, so such an entry is left to the legacy client.
 */
export const isRawHtmlNotice = (e) => typeof e?.description === 'string' && /<\/?[a-z][a-z0-9]*(\s[^>]*)?>/i.test(e.description);

/** The server's event catalog; a missing or damaged one is an empty list, never a crash. */
export function normalizeEvents(raw) {
	return { list: Array.isArray(raw?.list) ? raw.list.filter((e) => e && typeof e.id === 'string' && !isRawHtmlNotice(e)) : [] };
}

/** @param {number} [now] the time the packet is applied at (the live connection: now; a replay: its own clock) */
export function applyPacket(state, pkt, now = Date.now()) {
	switch (pkt.type) {
		case 'captcha':
			state.status = 'captcha';
			state.captchaSiteKey = pkt.siteKey;
			break;
		case 'welcome':
			state.status = 'ready';
			state.events = normalizeEvents(pkt.event);
			state.id = pkt.id;
			state.guest = !!pkt.guest;
			state.admin = !!pkt.admin;
			state.membership = pkt.membership ?? 0;
			state.playTime = pkt.playTime ?? 0;
			state.okgCount = pkt.okg ?? 0;
			setFriends(state, pkt.friends);
			state.users = mapOf(pkt.users, normalizeUser);
			state.rooms = mapOf(pkt.rooms, (r) => normalizeRoom(r, state.meta));
			state.themePresets = normalizePresets(pkt.themePresets);
			state.box = cleanBox(pkt.box);
			state.myStatus = pkt.status ?? 'online';
			state.ratingRestrictionUntil = Number(pkt.ratingRestrictionUntil) || 0;
			dmLoad(state); // my whisper threads from before a reload of this page
			// legacy: a low 평점 is said once, right after connecting
			if ((state.users[state.id]?.rating ?? 2) < 2) notice(state, t('play.rating.info'));
			break;
		case 'themePresets':
			state.themePresets = normalizePresets(pkt.presets);
			break;
		case 'conn':
		case 'user':
		case 'nickUpdate':
			state.users[pkt.user?.id ?? pkt.id] = normalizeUser(pkt.user ?? pkt);
			break;
		case 'disconn':
			delete state.users[pkt.id];
			state.friendPending = state.friendPending.filter((p) => p.target !== pkt.id); // a request to someone who left is moot
			break;
		case 'room': {
			const room = pkt.room;
			if (!room) break;
			const prev = state.rooms[room.id];
			const wasMine = state.myRoom === room.id;
			if (!room.players?.length) delete state.rooms[room.id];
			else state.rooms[room.id] = normalizeRoom(room, state.meta);
			// The room packet carries every player's full data (profile, equipment, ready state). AI seats too.
			for (const p of room.players ?? []) if (p?.id) state.users[p.id] = normalizeUser(p);
			const mine = !!room.players?.some((p) => p.id === state.id);
			// Anyone who was in this room and isn't any more is back in the lobby (their disconnect would arrive as `disconn`).
			for (const id of prev?.players ?? []) if (!room.players?.some((p) => p.id === id) && state.users[id]) state.users[id].place = 0;
			if (mine) state.myRoom = room.id;
			else if (wasMine) state.myRoom = null;
			// A game that stopped without a result (everyone left, the room broke) leaves nothing to show.
			if (state.game && (mine || wasMine) && (!mine || (!room.gaming && !state.game.result))) state.game = null;
			if (mine || wasMine) {
				// What the legacy client says in the chat about my room.
				if (pkt.kickVote) {
					const who = state.users[pkt.target]?.name ?? '';
					const key = pkt.kickVote.Y >= pkt.kickVote.N ? 'play.room.notice.kickResult' : 'play.room.notice.kickDenied';
					notice(state, t(key, { yes: pkt.kickVote.Y, no: pkt.kickVote.N, nick: who }));
					state.kickVote = null;
					if (pkt.target === state.id) state.kicked = { at: Date.now() };
				} else if (mine) {
					if (pkt.modify) notice(state, t('play.room.notice.modified'));
					if (prev && prev.master !== room.master && state.users[room.master]) notice(state, t('play.room.notice.master', { nick: state.users[room.master].name }));
				}
			}
			break;
		}
		case 'connRoom': {
			if (pkt.user?.id) state.users[pkt.user.id] = normalizeUser(pkt.user);
			if (state.myRoom) joinLeave(state, pkt.user?.id, 'joined'); // only my own room's comings and goings are my business
			break;
		}
		case 'disconnRoom':
			if (state.myRoom) joinLeave(state, pkt.id, 'left');
			break;
		case 'roomStuck':
			// The server says my room is broken: drop back to the lobby.
			if (state.myRoom) delete state.rooms[state.myRoom];
			state.myRoom = null;
			state.game = null;
			break;
		case 'kickVote': {
			state.kickVote = { target: pkt.target, at: Date.now() };
			const who = state.users[pkt.target]?.name ?? '';
			notice(state, t('play.room.notice.kickVoting', { nick: who }));
			break;
		}
		case 'kickDeny': {
			const who = state.users[state.kickVote?.target]?.name ?? '';
			notice(state, t('play.room.notice.kickDenied', { yes: pkt.Y, no: pkt.N, nick: who }));
			state.kickVote = null;
			break;
		}
		case 'invited':
			state.invite = { by: pkt.by, from: pkt.from, at: Date.now() };
			break;
		case 'inviteNo':
			notice(state, t('play.room.notice.inviteDenied', { nick: state.users[pkt.target]?.name ?? '' }));
			break;
		case 'refreshRoom':
			state.rooms = mapOf(pkt.rooms, (r) => normalizeRoom(r, state.meta));
			break;
		case 'okg':
			state.playTime = pkt.time ?? state.playTime;
			state.okgCount = pkt.count ?? state.okgCount;
			break;
		case 'surveyList':
			state.surveys = Object.values(pkt.surveys ?? {});
			break;
		case 'surveyToken':
			state.surveyToken = { value: pkt.value, date: pkt.date };
			break;
		case 'prompt':
			state.prompt = { lang: pkt.lang, type: pkt.promptType, value: pkt.value };
			break;
		case 'preRoom':
			state.preRoom = { channel: pkt.channel, id: pkt.id, token: pkt.handoffToken };
			break;
		case 'friend': {
			// A friend came online or left (legacy: also said in the chat), or changed what they show: `stat` is on / idle / dnd / off.
			const on = pkt.stat !== 'off';
			const was = pkt.id in state.friendsOnline;
			if (on) {
				state.friendsOnline[pkt.id] = Number(pkt.s);
				if (pkt.stat === 'idle' || pkt.stat === 'dnd') state.friendsStatus[pkt.id] = pkt.stat;
				else delete state.friendsStatus[pkt.id];
			} else {
				delete state.friendsOnline[pkt.id];
				delete state.friendsStatus[pkt.id];
			}
			const nick = state.friends[pkt.id];
			if (nick && on !== was) notice(state, on ? t('play.friend.notice.on', { server: state.meta.labels[`server.${pkt.s}`] ?? pkt.s, nick }) : t('play.friend.notice.off', { nick }));
			break;
		}
		case 'friendAdd':
			// `nick`: who asked, for when they are not here to be looked up (offline, or on another channel)
			if (!state.friendRequests.some((r) => r.from === pkt.from)) state.friendRequests.push({ from: pkt.from, nick: pkt.nick ?? '', at: Date.now() });
			break;
		case 'event.progress':
			state.events = normalizeEvents(pkt.event);
			state.eventProgress = { missions: Array.isArray(pkt.missions) ? pkt.missions : [], attendance: Array.isArray(pkt.attendance) ? pkt.attendance : [] };
			state.eventsLoaded = true;
			break;
		case 'event.exchangeData':
			state.exchanges = pkt.exchange && typeof pkt.exchange === 'object' ? pkt.exchange : {};
			break;
		case 'event.invite':
			if (typeof pkt.eventId === 'string') state.invites = { ...state.invites, [pkt.eventId]: { eventId: pkt.eventId, code: String(pkt.code ?? ''), invitee: pkt.invitee, friends: Array.isArray(pkt.friends) ? pkt.friends : [] } };
			break;
		case 'event.inviteLinked':
			state.inviteLinked = { result: String(pkt.result ?? ''), at: now };
			break;
		case 'event.campaign':
			if (typeof pkt.eventId === 'string') state.campaigns = { ...state.campaigns, [pkt.eventId]: { eventId: pkt.eventId, now: Number(pkt.now) || now, at: now, current: pkt.current, next: pkt.next, past: Array.isArray(pkt.past) ? pkt.past : [] } };
			break;
		case 'event.tileResult': {
			state.tileResult = { eventId: pkt.eventId, draws: Array.isArray(pkt.draws) ? pkt.draws : [], rewards: Array.isArray(pkt.rewards) ? pkt.rewards : [], gauge: pkt.gauge, at: now };
			// the catalog keeps where I am on the gauge, so the page shows it correctly after a reload of the board too
			const tile = state.events.list.find((e) => e.id === pkt.eventId)?.tile;
			if (tile?.gauge && pkt.gauge) tile.gauge.points = pkt.gauge.points;
			break;
		}
		case 'event.reward':
			state.eventReward = { eventId: pkt.eventId, source: pkt.source, rewards: Array.isArray(pkt.rewards) ? pkt.rewards : [], at: now };
			break;
		case 'levelRewards':
			state.levelTable = pkt.table && typeof pkt.table === 'object' ? pkt.table : null;
			break;
		case 'mailBox':
			state.mails = pkt.mails ?? [];
			if (pkt.notifications) state.notifications = pkt.notifications;
			state.mailLoaded = true;
			break;
		case 'notifications':
			state.notifications = pkt.notifications ?? [];
			break;
		case 'familyRes':
			// 패밀리: what the server took for it (founding one) is off my balance at once, without a "획득" notice.
			if (pkt.ok && pkt.paid && state.users[state.id]) state.users[state.id].money -= Number(pkt.paid);
			break;
		case 'obtain':
			for (const g of Array.isArray(pkt.levelGifts) ? pkt.levelGifts : []) state.levelGifts[g.level] = g;
			// Ping given by the server (a mail reward): my balance goes up at once.
			if (pkt.money && state.users[state.id]) state.users[state.id].money += Number(pkt.money);
			if (Array.isArray(pkt.gift)) state.gifts.push(...pkt.gift.map((g) => ({ key: String(g.key), q: Number(g.q) || 1 })));
			if (pkt.gain) state.gained.push(...pkt.gain.map((g) => ({ key: g.key, q: g.q })));
			// What the server says I now have: my equipment and my box (legacy sets both from every `obtain`).
			if (pkt.equip && state.users[state.id]) state.users[state.id].equip = pkt.equip;
			if (pkt.data && state.users[state.id] && typeof pkt.data.score === 'number') state.users[state.id].score = pkt.data.score;
			if (pkt.box) {
				state.box = cleanBox(pkt.box);
				state.boxSeq++;
			}
			// What a box or a coupon gave besides items (legacy notices).
			if (pkt.exp) notice(state, `${state.meta.labels['kkutu.js.obtainExp'] ?? ''}: ${Math.round(pkt.exp).toLocaleString()}`);
			if (pkt.money) notice(state, `${state.meta.labels['kkutu.js.obtainMoney'] ?? ''}: ${Math.round(pkt.money).toLocaleString()}`);
			break;
		case 'tutorialDone':
			state.tutorial = { id: String(pkt.id ?? ''), done: Array.isArray(pkt.done) ? pkt.done.map(String) : [], ping: Number(pkt.ping) || 0, at: now };
			break;
		case 'expired':
			state.expired.push(...(pkt.list ?? []));
			break;
		case 'obtainMail':
			state.obtained = { rewards: pkt.rewards ?? [], at: Date.now() };
			break;
		case 'friendAddRes':
			state.friendPending = state.friendPending.filter((p) => p.target !== pkt.target);
			state.friendResult = { target: pkt.target, res: !!pkt.res, at: Date.now() };
			if (pkt.res) {
				const nick = state.users[pkt.target]?.name ?? pkt.nick ?? pkt.target;
				setFriends(state, { ...state.friendsData, [pkt.target]: { memo: nick, nick, bf: false } });
				if (state.users[pkt.target]) state.friendsOnline[pkt.target] = state.server ?? 0;
			}
			break;
		case 'friendEdit':
			setFriends(state, pkt.friends);
			// Friends who are here on this channel right now are online.
			for (const id of Object.keys(state.friends)) if (state.users[id] && !(id in state.friendsOnline)) state.friendsOnline[id] = state.server;
			break;
		case 'friends': {
			const online = {};
			for (const [server, ids] of Object.entries(pkt.list ?? {})) for (const id of ids) online[id] = Number(server);
			state.friendsOnline = online;
			state.friendsStatus = { ...(pkt.status ?? {}) };
			break;
		}
		// What the "#…" admin commands send back (the server sends these only for an administrator's own command, or to everyone for a broadcast)
		case 'yell':
		case 'notice':
			if (typeof pkt.value === 'string' && pkt.value) state.chat.push({ who: t(pkt.type === 'yell' ? 'play.admin.yell' : 'play.admin.notice'), msg: plainText(pkt.value), at: Date.now(), system: true, mine: false });
			break;
		case 'tail':
			// Legacy: "a|rid@id: <json>", the raw answer of #ip, #tailroom, #tailuser and #roominfo
			if (state.admin) state.chat.push({ who: t('play.admin.tail'), msg: `${pkt.a}|${pkt.rid}@${pkt.id}: ${typeof pkt.msg === 'string' ? pkt.msg : JSON.stringify(pkt.msg)}`, at: Date.now(), system: true, mine: false });
			break;
		case 'chat':
			if (pkt.notice) {
				// Like the legacy client: an explicit message, or the localized text for the notice's code.
				const text = plainText(pkt.message !== undefined ? pkt.message : (state.meta.labels[`kkutu.js.error.${pkt.code}`] ?? ''));
				if (text) state.chat.push({ who: t('play.chat.system'), msg: text, at: Date.now(), system: true, mine: false });
			}
			else if (pkt.value && !isAdminCommand(pkt.value)) {
				const uid = pkt.profile?.id ?? '';
				const shown = splitFilter(uid || pkt.profile?.title || pkt.profile?.name, pkt.value); // what the word filter makes of it
				// A whisper is a line of a conversation (the 귓속말 tab), not of the room's chat.
				if (pkt.from) dmReceive(state, pkt, shown);
				else
					state.chat.push({
						who: (pkt.profile?.title ?? pkt.profile?.name ?? '?').split('#')[0],
						msg: pkt.value,
						shown,
						at: Date.now(),
						system: false,
						uid,
						whisper: false,
						spectator: state.users[uid]?.game?.form === 'S',
						mine: !!state.id && uid === state.id
					});
			}
			if (pkt.from && uid_(pkt) !== state.id) state.recentFrom = String(pkt.from).replace(/\s/g, ''); // /답 answers the last one who whispered
			if (state.chat.length > CHAT_BUFFER) state.chat.splice(0, state.chat.length - CHAT_BUFFER);
			break;
		case 'error':
			state.error = { code: pkt.code, message: pkt.message ?? '' };
			break;
		case 'initSy':
		case 'starting':
		case 'roundReady':
		case 'turnStart':
		case 'useItem':
		case 'turnError':
			// A refused word is shown on the board (struck out); it is not said in the chat.
			applyGamePacket(state, pkt, now);
			break;
		case 'turnHint':
		case 'drawingContent':
		case 'drawCanvas':
		case 'attackBonus':
		case 'turnEnd':
			applyGamePacket(state, pkt, now);
			if (state.game?.combo?.action === 'warn') notice(state, state.meta.labels['kkutu.js.trollWarning']);
			break;
		case 'injeongHint':
			// shown once the result is closed and the waiting room is back (see lobbyStore.closeResult)
			if (Array.isArray(pkt.words) && pkt.words.length) state.pendingInjeongHint = pkt.words.map(String); // shown by showInjeongHint, once the result is closed
			break;
		case 'roundEnd': {
			applyGamePacket(state, pkt, now);
			// Legacy: what I got is also said in the chat.
			const mine = state.game?.result?.rows.find((r) => r.me);
			if (mine) {
				const L = state.meta.labels;
				const n = (v) => Math.round(v).toLocaleString();
				let msg = `${L['kkutu.js.scoreGain']}: ${n(mine.reward.exp)}, ${L['kkutu.js.moneyGain']}: ${n(mine.reward.money)}`;
				if (mine.reward.ep) msg += `, ${L['kkutu.js.epGain']}: ${n(mine.reward.ep)}`;
				notice(state, msg);
			}
			break;
		}
	}
	return state;
}
