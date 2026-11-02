/**
 * Pure translation of game-server packets into lobby state. No sockets, no DOM, so it
 * can be tested directly. The server speaks the same JSON the legacy client consumes.
 */
import { getLevel } from './level.js';
import { t } from './i18n.svelte.js';

export const initialState = () => ({
	assetsReady: false, // shop catalogue + fonts, so the lobby doesn't pop in half-drawn
	status: 'connecting', // connecting | captcha | ready | closed | error
	error: null,
	captchaSiteKey: null,
	meta: { modes: [], options: {}, labels: {}, rules: {}, optionDefs: {}, moremiParts: [] },
	membership: 0,
	playTime: 0,
	okgCount: 0, // 오끄감 buff stack
	friends: {},
	myRoom: null,
	preRoom: null,
	id: null,
	guest: true,
	admin: false,
	users: {},
	rooms: {},
	friendsOnline: {},
	chat: [],
	pingMs: null
});

function safeJson(v) {
	try {
		return JSON.parse(v);
	} catch {
		return {};
	}
}

export function normalizeUser(raw) {
	const score = raw.data?.score ?? 0;
	return {
		id: raw.id,
		name: (raw.profile?.title ?? raw.profile?.name ?? raw.id ?? '').split('#')[0],
		lv: getLevel(score),
		score,
		money: raw.money ?? 0,
		wins: Object.values(raw.data?.record ?? {}).reduce((n, r) => n + (r?.[1] ?? 0), 0),
		equip: typeof raw.equip === 'string' ? safeJson(raw.equip) : (raw.equip ?? {}),
		place: raw.place ?? 0,
		guest: !!raw.guest
	};
}

export function normalizeRoom(raw, meta) {
	const key = meta.modes[raw.mode];
	const label = meta.labels[`game.mode.${key}.name`] ?? key ?? '';
	const tags = Object.entries(raw.opts ?? {})
		.filter(([, on]) => on)
		.map(([k]) => meta.labels[`game.option.${meta.options[k] ?? k}.name`])
		.filter(Boolean);
	return {
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
		players: (raw.players ?? []).map((p) => p.id),
		master: raw.master
	};
}

const mapOf = (obj, fn) => Object.fromEntries(Object.entries(obj ?? {}).map(([k, v]) => [k, fn(v)]));

export function applyPacket(state, pkt) {
	switch (pkt.type) {
		case 'captcha':
			state.status = 'captcha';
			state.captchaSiteKey = pkt.siteKey;
			break;
		case 'welcome':
			state.status = 'ready';
			state.id = pkt.id;
			state.guest = !!pkt.guest;
			state.admin = !!pkt.admin;
			state.membership = pkt.membership ?? 0;
			state.playTime = pkt.playTime ?? 0;
			state.okgCount = pkt.okg ?? 0;
			state.friends = mapOf(pkt.friends, (f) => (typeof f === 'object' ? f.nick : f));
			state.users = mapOf(pkt.users, normalizeUser);
			state.rooms = mapOf(pkt.rooms, (r) => normalizeRoom(r, state.meta));
			break;
		case 'conn':
		case 'user':
		case 'nickUpdate':
			state.users[pkt.user?.id ?? pkt.id] = normalizeUser(pkt.user ?? pkt);
			break;
		case 'disconn':
			delete state.users[pkt.id];
			break;
		case 'room': {
			const room = pkt.room;
			if (!room) break;
			if (!room.players?.length) delete state.rooms[room.id];
			else state.rooms[room.id] = normalizeRoom(room, state.meta);
			if (room.players?.some((p) => p.id === state.id)) state.myRoom = room.id;
			else if (state.myRoom === room.id) state.myRoom = null;
			break;
		}
		case 'refreshRoom':
			state.rooms = mapOf(pkt.rooms, (r) => normalizeRoom(r, state.meta));
			break;
		case 'okg':
			state.playTime = pkt.time ?? state.playTime;
			state.okgCount = pkt.count ?? state.okgCount;
			break;
		case 'preRoom':
			state.preRoom = { channel: pkt.channel, id: pkt.id, token: pkt.handoffToken };
			break;
		case 'friends': {
			const online = {};
			for (const [server, ids] of Object.entries(pkt.list ?? {})) for (const id of ids) online[id] = Number(server);
			state.friendsOnline = online;
			break;
		}
		case 'chat':
			if (pkt.notice) state.chat.push({ who: t('play.chat.system'), msg: pkt.message ?? '', at: Date.now(), system: true, mine: false });
			else if (pkt.value)
				state.chat.push({ who: (pkt.profile?.title ?? pkt.profile?.name ?? '?').split('#')[0], msg: pkt.value, at: Date.now(), system: false, mine: !!state.id && pkt.profile?.id === state.id });
			if (state.chat.length > 200) state.chat.splice(0, state.chat.length - 200);
			break;
		case 'error':
			state.error = { code: pkt.code, message: pkt.message ?? '' };
			break;
	}
	return state;
}
