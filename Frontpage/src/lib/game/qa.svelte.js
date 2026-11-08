import { tick } from 'svelte';
import { EXP, getLevel } from './level.js';
import { applyPacket, normalizeUser } from './protocol.js';
import { levelUp } from './levelUp.svelte.js';
import { loadGoodDetails } from './shop.js';

/**
 * PROTOTYPE QA: shows the result board, a 백과사전 낱장 and the item-acquire screen with made-up data, without playing a game.
 * Only wakes up when the page address has `?qa=...`, so a normal visit never sees it. Remove with the prototype.
 *
 *   /play/0?qa=dict:9-10      the same without a game: a 백과사전 낱장 opened, my experience rises, the popup comes over the lobby
 *   /play/0?qa=item           the item-acquire screen (what a gacha, a box or the lab shows) with sample items
 *   /play/0?qa=result:teams   the result screen of a game with four teams (also result:duel and result:list; add -lose for a game I lost, e.g. result:teams-lose)
 * or from the console: `kkQa.dict(9, 10)`, `kkQa.obtain()`.
 */
const state = $state({ result: null, store: null, obtain: { open: false, details: null } });

const start = (level) => (level > 1 ? EXP[level - 2] : 0);
/** A point 40% of the way into a level. */
const mid = (level) => start(level) + (Number.isFinite(EXP[level - 1]) ? (EXP[level - 1] - start(level)) * 0.4 : 1); // level 720 has no upper bound

export const qa = {
	get store() {
		return state.store;
	},
	/** The item-acquire sample: bound to an `ObtainScreen` on the page. */
	obtain: state.obtain,
	obtainItems: [
		{ key: 'boxB4', q: 1 },
		{ key: 'dictPage', q: 3 },
		{ key: 'bonusEXP_15', q: 2 },
		{ key: '2023_attendanceE9', q: 1 },
		{ key: 'boxB3', q: 1 }
	]
};

export async function showObtain() {
	state.obtain.details = await loadGoodDetails();
	state.obtain.open = true;
}

/** The result screen of a made-up game: `teams` (eight players in four teams), `duel` (two players) or `list` (five players, no teams). */
export function showResult(store, kind) {
	const lose = kind.endsWith('-lose'); // I am last, and in a game with teams my team is the last
	kind = kind.replace('-lose', '');
	const names = ['플레이어', '이도울', '끝말잇기왕', '초록이', '단어수집가', '푸른하늘', '노랑병아리', '초보입니다'];
	const scores = [1524, 804, 736, 600, 502, 411, 400, 333];
	const teamOf = kind === 'teams' ? (lose ? [4, 4, 4, 1, 1, 3, 3, 2] : [4, 1, 1, 3, 3, 4, 4, 4]) : names.map(() => 0);
	const n = kind === 'duel' ? 2 : kind === 'list' ? 5 : 8;
	const meIdx = lose ? n - 1 : 0;
	const meId = store.net.id || 'qa';
	const users = {};
	const rows = [];
	for (let i = 0; i < n; i++) {
		const id = i === meIdx ? meId : `qa${i}`;
		users[id] = normalizeUser({
			id,
			profile: { title: i === meIdx ? (store.net.users[meId]?.name ?? names[0]) : names[i] },
			data: { score: i === meIdx ? start(91) + 20 : start(70 - i * 6) + 10 },
			equip: i === meIdx ? (store.net.users[meId]?.equip ?? {}) : {},
			game: { ready: true, form: 'J', team: teamOf[i], score: scores[i] }
		});
		rows.push({
			id,
			rank: i,
			score: scores[i],
			team: teamOf[i],
			reward: { exp: i === meIdx ? 20 : 6, money: i === meIdx ? 23 : 21, ep: i === meIdx ? 3 : 0, baseExp: i === meIdx ? 16 : 6, baseMoney: 21, log: i === meIdx ? ['kgEXP0.25', 'kgMNY0.1'] : [], sources: i === meIdx ? ['', ''] : [], levelGifts: null },
			before: Math.round(start(91) + 20),
			after: Math.round(start(91) + 26),
			levelBefore: 91,
			levelAfter: 91,
			me: i === meIdx
		});
	}
	state.result = { practice: false, rows };
	const played = [
		...[['칠면조', 61], ['가방', 12], ['방아쇠', 22], ['쇠고기', 19], ['기러기', 17], ['기차표', 20], ['표범', 9], ['이리듐', 38, [2, 3]]].map(([word, score, hanbang]) => ({ id: meId, word, score, bonus: 0, round: 1, hanbang: hanbang ?? null })),
		{ id: 'qa1', word: '고슴도치', score: 44, bonus: 0, round: 1, hanbang: null }
	];
	const net = { id: meId, guest: false, users, meta: store.net.meta, chat: store.net.chat ?? [], myRoom: store.net.myRoom ?? null };
	state.store = {
		get net() {
			return net;
		},
		get shop() {
			return store.shop;
		},
		get game() {
			return state.result ? { result: state.result, played, solved: null, kpm: null } : null;
		},
		replayReady: true, // so the 경기 저장 button can be looked at
		// what the chat button inside the result needs
		draft: { nonce: 0, text: '' },
		dmRequest: { nonce: 0 },
		dmUnread: 0,
		replayView: null,
		inGame: false,
		myTurn: false,
		hooks: {},
		dmMembers: () => [],
		spaceSubmits: () => {},
		saveReplay() {},
		closeResult() {
			state.result = null;
			state.store = null;
		}
	};
}

/** What opening an item does: the server says my new experience in an `obtain`, and the popup comes from the score watcher. */
export async function showDict(store, from, to) {
	from = Math.max(1, Math.min(719, Math.round(from)));
	to = Math.max(from + 1, Math.min(720, Math.round(to)));
	const me = store.net.users[store.net.id];
	if (!me) return;
	me.score = Math.round(mid(from));
	await tick();
	levelUp.reset(); // placing myself at `from` counts as a level-up to the watcher: drop it, the item is what we want to see
	await tick();
	applyPacket(store.net, { type: 'obtain', exp: 0, money: 0, data: { score: Math.round(mid(to)) } });
}

export function installQa(store) {
	if (typeof window === 'undefined') return;
	const param = new URLSearchParams(window.location.search).get('qa');
	if (!param) return;
	window.kkQa = { dict: (from, to) => showDict(store, from, to), obtain: () => showObtain(), result: (kind) => showResult(store, kind) };
	if (param === 'item') return void setTimeout(showObtain, 1500);
	const shape = param.match(/^result:(teams|duel|list)(-lose)?$/);
	if (shape) {
		const timer = setInterval(() => {
			if (store.net.status === 'ready' && store.net.users[store.net.id]) (clearInterval(timer), setTimeout(() => showResult(store, shape[1] + (shape[2] ?? '')), 800));
		}, 300);
		return;
	}
	const m = param.match(/^dict:(\d+)-(\d+)$/);
	if (!m) return;
	// it needs me in the lobby first
	const timer = setInterval(() => {
		if (store.net.status === 'ready' && store.net.users[store.net.id]) (clearInterval(timer), setTimeout(() => window.kkQa.dict(Number(m[1]), Number(m[2])), 800));
	}, 300);
}
