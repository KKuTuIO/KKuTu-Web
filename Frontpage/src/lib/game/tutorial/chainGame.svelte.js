/**
 * The practice 끝말잇기 of the tutorial, on the real game screen: three rounds (chainData.js has the story of each). Like a replay
 * (replayPlayer.svelte.js) it runs the real packet reducer into a state of its own and offers the screen the small part of the lobby store that the
 * screen reads; unlike a replay the player takes part: what they type is judged here and the guide's moves are packets sent on the server's own
 * timing (chainData.js says which). A round ends the way a real one does: a turn runs out (`turnEnd` with `ok: false`, the penalty, a hint) and the
 * next round is dealt 3 s later. The game's sounds (the ticking of the turn clock, the round's start) are the real ones, through `createGameAudio`.
 * Nothing goes to a server.
 */
import { noTelemetry } from '../telemetry.js';
import { initialState, applyPacket, normalizeUser, normalizeRoom } from '../protocol.js';
import { t } from '../i18n.svelte.js';
import { castEquip } from '../story/cast.js';
import { createGameAudio } from '../gameAudio.js';
import { FIRST_TURN_MS, LAST_TURN_ROUND_TIME, MEANING, NEXT_ROUND_MS, ROUNDS, ROUND_SEC, SCORE, judge, lastChar, nextTurnDelay, penalty, robotDelay, turnSpeed, turnTimeOf } from './chainData.js';

export const REMI = 'tutorial-remi';
const ROOM = 1;
const MODE = 'KSH'; // 한국어 끝말잇기
const PAUSE_LINE_MS = 6000; // how long the line about the pause stays before the turn's hint takes its place
const LOADING_MS = 1000; // between `starting` and the first `roundReady` (the real client's loading veil)
export const INPUT = '[data-tour=game-input]';
export const MISSION = '[data-tour=game-mission]';

/**
 * @param {{real:any, tip:(key:string, o?:{params?:object})=>void, line:(key:string, o?:{params?:object, at?:string})=>Promise<void>, focus:(selector:string|null, label?:string)=>void, onEnd:()=>void, onExit:()=>void}} o
 *   real: the page's lobby store (who I am, the game's metadata, the item catalogue); tip: what the guide says on the side; line: what she explains
 *   (waits for 다음); focus: dims the page except the part (the answer field, the mission letter) with a label; onEnd: the player closed the
 *   result; onExit: they left.
 */
export function createChainGame({ real, tip, line, focus, onEnd, onExit }) {
	const meta = real.net.meta;
	const me = real.net.id;
	const mine = real.net.users[me];
	const guideEquip = castEquip('haksamo');
	const shop = { ...Object.fromEntries(Object.entries(guideEquip).map(([slot, id]) => [id, { _id: id, group: slot, options: {} }])), ...real.shop };

	const base = initialState();
	base.meta = meta;
	base.status = 'ready';
	base.id = me;
	base.guest = !!real.net.guest;
	base.users[me] = normalizeUser({
		id: me,
		profile: { title: mine ? (mine.tag ? `${mine.name}#${mine.tag}` : mine.name) : me },
		data: { score: mine?.score ?? 0 },
		equip: mine?.equip ?? {},
		guest: !!real.net.guest,
		game: { ready: true, form: 'J', team: 0, score: 0 }
	});
	base.users[REMI] = normalizeUser({ id: REMI, profile: { title: t('play.mascot.haksamo') }, data: { score: 0 }, equip: guideEquip, game: { ready: true, form: 'J', team: 0, score: 0 } });
	const rawRoom = (opts) => ({
		id: ROOM,
		title: t('play.tutorial.chain.room'),
		limit: 2,
		mode: meta.modes.indexOf(MODE),
		round: ROUNDS.length,
		time: ROUND_SEC,
		opts,
		injpick: [],
		gaming: true,
		master: me,
		players: [{ id: me }, { id: REMI }],
		game: { seq: [REMI, me], round: 1 }
	});
	base.rooms[ROOM] = normalizeRoom(rawRoom(ROUNDS[0].opts), meta);
	base.myRoom = ROOM;
	const net = $state(base);
	// `pause`: the practice game's pause, for the picture over the game: {on: it is paused now, kind: 'pause' | 'play' (the last change), n: counts the changes}
	const ui = $state({ gameCovered: false, exitReserved: false, pause: { on: false, kind: 'pause', n: 0 } });
	const live = $state({ value: '', composing: false });
	const flags = $state({ paused: false }); // she is explaining: the game waits, and the answer field is away (so Enter presses 다음)
	const sounds = createGameAudio(net); // the turn clock's ticking and the round's start, as in a real game

	const timers = new Set();
	// The game's own clock, which can be stopped: what the board reads as "now". A real game cannot be paused; the practice is paused for the
	// player while they type (their turn does not run out, the turn clock does not tick).
	const clock = { paused: false, since: 0, skew: 0 };
	const vnow = () => Date.now() - clock.skew - (clock.paused ? Date.now() - clock.since : 0);
	let disposed = false;
	let turnId = 0;
	let roundTime = ROUND_SEC * 1000; // what is left of the round (the server counts it down by the time each turn took)
	let turnTime = 0;
	let turnAt = 0;
	let chain = 0; // words of this round
	let pauseExplained = false; // she has told that the game is paused for them
	let mission = '';
	/** What the player has to type now: {char, word, refuse?, resolve}; or, in a turn they cannot answer, {stuck: char}. */
	let want = /** @type {null|{char?:string, word?:string, refuse?:boolean, stuck?:string, resolve?:(r:'ok'|'refused')=>void}} */ (null);
	const sleep = (ms) =>
		new Promise((resolve) => {
			const id = setTimeout(() => {
				timers.delete(id);
				if (!disposed) resolve();
			}, ms);
			timers.add(id);
		});
	const push = (pkt, { silent = false } = {}) => {
		applyPacket(net, pkt, vnow());
		if (!silent) sounds.onPacket(pkt);
	};
	const mean = (word) => (MEANING[word] ? t(`play.tutorial.chain.mean.${MEANING[word]}`) : '');
	/** She explains, line after line, and the game waits for each (the answer field is gone meanwhile). */
	async function explain(...lines) {
		flags.paused = true;
		for (const [key, o] of lines) await line(key, o);
		flags.paused = false;
	}
	const setOpts = (opts) => (net.rooms[ROOM] = normalizeRoom(rawRoom(opts), meta)); // the guide changes the room's settings

	function startTurn(who, char, rt = roundTime, opts = {}) {
		turnId += 1;
		const speed = turnSpeed(rt);
		turnTime = turnTimeOf(speed);
		turnAt = vnow();
		push({ type: 'turnStart', turn: who === REMI ? 0 : 1, seq: [REMI, me], char, subChar: '', speed, turnTime, roundTime: rt, mission, wordLength: 0, gameId: 'tutorial', turnId, inputEpoch: 1 }, opts);
	}
	/** Stops the game's clock (and the turn clock's ticking) and shows the pause picture. */
	function pauseGame() {
		if (clock.paused) return;
		clock.paused = true;
		clock.since = Date.now();
		sounds.stopTurnSound();
		ui.pause = { on: true, kind: 'pause', n: ui.pause.n + 1 };
	}
	/** The game goes on: the clock runs again and the play picture is shown. */
	function resumeGame() {
		if (!clock.paused) return;
		clock.skew += Date.now() - clock.since;
		clock.paused = false;
		ui.pause = { on: false, kind: 'play', n: ui.pause.n + 1 };
	}
	/** A word is accepted: the server tells everybody at once and counts the turn off the round. */
	function played(who, word, { bonus = 0, hanbang } = {}) {
		roundTime = Math.max(0, roundTime - Math.min(vnow() - turnAt, turnTime));
		push({ type: 'turnEnd', ok: true, value: word, mean: mean(word), theme: '', wc: '', score: (who === REMI ? SCORE.remi : SCORE.me) + bonus, bonus, ...(hanbang ? { hanbang } : {}) });
		chain += 1;
	}
	/** A turn runs out: it ends the round (`hint`: a word that could have been used). */
	function ranOut(who, hint) {
		const score = net.users[who].game.score;
		push({ type: 'turnEnd', ok: false, target: who, score: penalty(chain, score), ...(hint ? { hint } : {}) });
	}

	/** The guide types `word`; `after` runs the moment it is on the board, then the next turn comes a sixth of a turn later. */
	async function remiTurn(char, word, { hanbang, after } = {}) {
		startTurn(REMI, char);
		await sleep(robotDelay(word));
		played(REMI, word, { hanbang });
		(after ?? (() => tip('play.story.chain.guide', { params: { word, char: lastChar(word) } })))();
		await sleep(nextTurnDelay(turnTime));
	}
	/** The player's turn: dim the page except the answer field and say what to type, until they type it. Resolves 'refused' when it is a word the room refuses. */
	async function myTurn(char, move, { hint, after } = {}) {
		// The turn starts, and the game is paused at once for the player to type (a real game cannot be): the turn clock stands still and does not
		// tick, so nothing times out. This is staging, nothing the player does or presses: the first time she says so while the answer field is
		// already there, and her hint for the turn follows a little later.
		startTurn(me, char, roundTime, { silent: true });
		pauseGame();
		const showHint = () => hint && tip(hint, { params: { word: move.word, char, mission } });
		const outcome = await new Promise((resolve) => {
			want = { char, word: move.word, refuse: !!move.refuse, resolve };
			focus(INPUT, t('play.tutorial.chain.type', { word: move.word }));
			if (pauseExplained) showHint();
			else {
				pauseExplained = true;
				tip('play.story.chain.pause');
				sleep(PAUSE_LINE_MS).then(() => want && showHint());
			}
		});
		// The game goes on once the word is accepted (the turn is over at once: the ticking does not start again). A refused word leaves it paused
		// while she explains.
		if (outcome === 'ok') resumeGame();
		focus(null);
		want = null;
		if (outcome === 'ok') {
			played('me', move.word, move.mission ? { bonus: SCORE.missionBonus } : {});
			after?.();
			await sleep(nextTurnDelay(turnTime));
		}
		return outcome;
	}
	/** The guide's clock runs out as a round's last turn does: a very short, fast turn. */
	async function remiRunsOut(char, hint) {
		startTurn(REMI, char, LAST_TURN_ROUND_TIME);
		await sleep(turnTime + 100);
		ranOut(REMI, hint);
	}
	/** The player has nothing to answer with: the clock runs the whole turn out (what they type is only said not to work). */
	async function playerStuck(char) {
		want = { stuck: char };
		startTurn(me, char);
		await sleep(turnTime + 100);
		want = null;
		ranOut(me);
	}
	const nextRound = async (n, char, opts, missionChar = '') => {
		await sleep(NEXT_ROUND_MS);
		roundTime = ROUND_SEC * 1000;
		chain = 0;
		mission = missionChar;
		setOpts(opts);
		push({ type: 'roundReady', round: n, char, subChar: '', mission });
	};

	async function play() {
		const [r1, r2, r3] = ROUNDS;

		// 1. 미션
		push({ type: 'starting' });
		await sleep(LOADING_MS);
		mission = r1.mission;
		roundTime = ROUND_SEC * 1000;
		push({ type: 'roundReady', round: 1, char: r1.char, subChar: '', mission });
		focus(MISSION);
		await explain(['play.story.chain.r1.mission', { params: { mission }, at: 'bottom' }]);
		focus(null);
		await sleep(FIRST_TURN_MS);
		await remiTurn(r1.char, r1.moves[0].word);
		await myTurn(lastChar(r1.moves[0].word), r1.moves[1], {
			hint: 'play.story.chain.r1.hint',
			after: () => {
				tip('play.story.chain.r1.done', { params: { mission, bonus: SCORE.missionBonus } });
				mission = r1.nextMission; // the next turn brings a new mission letter, as the server's does
			}
		});
		await remiTurn(lastChar(r1.moves[1].word), r1.moves[2].word);
		await myTurn(lastChar(r1.moves[2].word), r1.moves[3], { after: () => tip('play.story.chain.good', { params: { word: r1.moves[3].word } }) });
		tip('play.story.chain.r1.out');
		await remiRunsOut(lastChar(r1.moves[3].word), r1.ends.hint);

		// 2. 매너: the guide plays a 한방 word, the player cannot go on
		await nextRound(2, r2.char, r2.opts);
		await explain(['play.story.chain.r2.intro']);
		await sleep(FIRST_TURN_MS);
		await remiTurn(r2.char, r2.moves[0].word);
		await myTurn(lastChar(r2.moves[0].word), r2.moves[1]);
		await remiTurn(lastChar(r2.moves[1].word), r2.moves[2].word, { hanbang: r2.moves[2].hanbang, after: () => tip('play.story.chain.r2.hanbang', { params: { word: r2.moves[2].word } }) });
		await playerStuck(lastChar(r2.moves[2].word));
		await explain(['play.story.chain.r2.lose', { params: { word: r2.moves[2].word } }], ['play.story.chain.r2.on']);
		setOpts(r2.then.opts); // the guide turns 매너 on

		// 3. 어인정: a word that is refused until the room accepts them
		await nextRound(3, r3.char, r3.opts);
		await explain(['play.story.chain.r3.intro']);
		await sleep(FIRST_TURN_MS);
		await remiTurn(r3.char, r3.moves[0].word);
		const word = r3.moves[1];
		const first = await myTurn(lastChar(r3.moves[0].word), { ...word, refuse: true }, { hint: 'play.story.chain.r3.hint' }); // refused: 어인정 is off
		if (first === 'refused') {
			await explain(['play.story.chain.r3.refused', { params: { word: word.word } }], ['play.story.chain.r3.why'], ['play.story.chain.r3.on']);
			setOpts(word.then.opts); // the guide turns 어인정 on
			await myTurn(lastChar(r3.moves[0].word), word, { hint: 'play.story.chain.r3.again', after: () => tip('play.story.chain.r3.ok', { params: { word: word.word } }) });
		}
		tip('play.story.chain.r3.out');
		await remiRunsOut(lastChar(word.word), r3.ends.hint);

		// the last round is over: the result
		await sleep(NEXT_ROUND_MS);
		const score = (id) => net.users[id].game.score;
		const result = [me, REMI].sort((a, b) => score(b) - score(a)).map((id, rank) => ({ id, rank, score: score(id), reward: { score: 0, money: 0, ep: 0 } }));
		push({ type: 'roundEnd', result });
	}

	function submit(value) {
		const g = net.game;
		const word = value.trim();
		if (!g || !word || !store.myTurn || !want) return false;
		live.value = '';
		live.composing = false;
		g.submitted = value;
		if (want.stuck) {
			tip('play.story.chain.stuck', { params: { char: want.stuck } });
			return true;
		}
		const verdict = judge(want, word);
		if (verdict.ok) {
			// a word the room refuses (a 어인정 word while 어인정 is off) is refused as the server does: a `turnError` 404 with the word
			if (want.refuse) {
				push({ type: 'turnError', code: 404, value: word });
				want.resolve?.('refused');
			} else want.resolve?.('ok');
			return true;
		}
		push({ type: 'turnError', code: 404, value: t(`play.tutorial.chain.err.${verdict.reason}`, { char: want.char, word: want.word }) });
		tip(`play.story.chain.err.${verdict.reason}`, { params: { char: want.char, word: want.word } });
		return true;
	}

	/** What the game screen, its header and the result read from the lobby store (and do with it). */
	const store = {
		net,
		ui,
		shop,
		replay: true, // the screen shows it without the loading veil
		/** The result screen's lines for players: the guide congratulates the player on the podium. */
		resultSay: { [REMI]: 'play.story.chain.win' },
		now: vnow, // the game's own clock: it stands still while the game is paused
		speed: () => 1,
		hooks: { openProfile() {} },
		telemetry: noTelemetry,
		traceScope: () => null,
		watchField: () => ({ destroy() {} }),
		conn: { send() {} },
		get game() {
			return net.game;
		},
		get currentRoom() {
			return net.rooms[net.myRoom];
		},
		get inGame() {
			return true;
		},
		get myTurn() {
			return net.game?.phase === 'turn' && net.game.current === me && !flags.paused;
		},
		get typed() {
			return live.value;
		},
		get composing() {
			return live.composing;
		},
		setTyped(v, composing = false) {
			live.value = v;
			live.composing = composing;
		},
		submitWord: submit,
		spaceSubmits: () => false,
		toggleExitReserve() {},
		replayReady: false,
		saveReplay: () => false,
		dressLocked: true,
		selectRound(n) {
			if (net.game) net.game.selectedRound = n;
		},
		selectBar(sel) {
			if (net.game) net.game.sel = sel;
		},
		closeResult: () => onEnd(),
		leaveGame: async () => onExit()
	};

	return {
		store,
		/** Plays the three rounds as the server would deal them. */
		start() {
			void play();
		},
		dispose() {
			disposed = true;
			for (const id of timers) clearTimeout(id);
			timers.clear();
			sounds.stopTurnSound();
		}
	};
}
