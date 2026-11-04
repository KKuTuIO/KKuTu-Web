/**
 * The in-game packets (한국어 끝말잇기), as pure functions over the connection state: no sockets, no DOM.
 * The server speaks the legacy protocol: starting -> roundReady -> (turnStart -> turnEnd)* -> roundEnd.
 */
import { getLevel } from './level.js';

/** `initSy` hands over the key that unlocks the words in `turnEnd` (legacy: round(val[2] / 3 - 1120)). */
export function secretFromInit(val) {
	if (!Array.isArray(val)) return 0;
	const rk = val[2] / 3 - 1120;
	if (!Number.isFinite(rk) || rk < 0 || Math.abs(rk - Math.round(rk)) > 0.01) return 0;
	return Math.round(rk);
}

/**
 * The server hides the played word in `turnEnd`: the first character is plain, the rest are XOR-ed with a list that
 * sits (base64 JSON) under a property named by `sum ^ secretKey`. Returns the packet with the word restored.
 * A packet that is not obfuscated, or that cannot be read, comes back unchanged.
 */
export function decryptTurnEnd(pkt, secretKey) {
	if (pkt.sum === undefined || !secretKey || typeof pkt.value !== 'string') return pkt;
	try {
		const key = String.fromCharCode(pkt.sum ^ secretKey);
		if (pkt[key] === undefined) return pkt;
		const xor = JSON.parse(atob(pkt[key]));
		let word = pkt.value[0];
		for (let i = 0; i < xor.length; i++) word += String.fromCharCode(pkt.value.charCodeAt(i + 1) ^ xor[i]);
		const { sum: _sum, [key]: _gone, ...rest } = pkt;
		return { ...rest, value: word };
	} catch {
		return pkt;
	}
}

/** The letter to start with: "가", or "가(나)" when a second one is also allowed. */
export const charText = (char, subChar) => (char ?? '') + (subChar ? `(${subChar})` : '');

/** The families that are played all at once: everyone answers at the same time, there is no turn order. */
export const QUIZ = ['Jaqwi', 'Typing', 'Mathquiz', 'Sock', 'Linkland', 'Wordstack', 'Crossword', 'Drawing'];
/** Modes drawn on the square board layout (the legacy `cw` layout): the board in the middle, found words on the left, players on the right. */
export const BOARD = ['Sock', 'Linkland', 'Crossword', 'Drawing'];
export const familyOf = (state) => state.meta.rules[state.rooms[state.myRoom]?.modeKey]?.rule;

/** Seat order arrives as ids, or as {id, robot} objects for AI seats. */
export const seatIds = (seq) => (Array.isArray(seq) ? seq.map((s) => (s && typeof s === 'object' ? s.id : s)).filter(Boolean) : []);

export function newGame(room) {
	return {
		phase: 'loading', // loading | ready | turn | between | ended
		round: 0,
		rounds: room?.rounds ?? 0,
		char: '',
		subChar: '',
		mission: '',
		chain: 0,
		seq: seatIds(room?.game?.seq),
		turn: room?.game?.turn ?? 0,
		current: null, // id of whose turn it is
		speed: 0,
		turnTime: 0,
		roundTime: 0,
		turnAt: 0, // when the turn started, on my clock
		wordLength: 0,
		gameId: undefined,
		turnId: undefined,
		inputEpoch: undefined,
		words: [], // [{id, word, mean, theme, score, bonus}] newest last
		last: null, // the latest turn's outcome {ok, id, value, mean, score, bonus, hint}
		fail: null, // {code, text, at} the server refused my word
		deltas: {}, // id -> {seq, text, kind} the score that just changed, to float over the player
		result: null,
		theme: '',
		hints: [], // 자음퀴즈: hints given so far
		list: [], // 타자 / 수학 대결: the words (problems) of the round, repeated once so the end never shows
		history: [], // quiz modes: what I answered {word, mean, mark}
		answer: '', // 자음퀴즈: the answer shown when nobody got it
		winner: null, // 자음퀴즈: who answered last
		gaveUp: {},
		kpm: null, // 타자 대결: {id: kpm} at the end of a round
		board: '', // 솎솎 / 잇땅: the letters of the board
		boardSize: 0,
		owners: [], // 잇땅: who owns each cell
		maps: [], // 솎솎: the words found
		picked: [], // 솎솎: cells that were just taken [{key, index, char}], to animate
		words: [], // 잇땅: the words found, newest first
		pool: [], // 워드스택: my pieces
		subpool: [],
		pieceAges: [],
		poolAt: 0,
		throws: [], // 워드스택: pieces flying from one player to another [{key, from, to, piece, delay}]
		boards: [], // 십자말풀이: per round, the bars [x, y, dir, len, word, owner]
		means: [], // 십자말풀이: per round, {'x,y,dir': {dir, len, mean, theme, type}}
		selectedRound: 1,
		sel: null, // 십자말풀이: the bar being answered [round, x, y, dir]
		cwFlash: null, // 십자말풀이: a word was solved in a round that is not on show {round, key}
		roundsDone: false,
		painter: null, // 그림퀴즈: who is drawing
		answerLength: 0,
		painterWord: '', // what the painter has to draw (only the painter is told)
		canvasImage: '', // what the guessers see: the painter's picture
		lastFail: undefined, // who failed the last turn, for the "two turns in a row" rule
		failCombo: 0,
		combo: null // {action: 'warn' | 'leave', at}: what the rule asks for after the last turn
	};
}

/**
 * The legacy `checkFailCombo`: when I fail two turns in a row the first time is a warning and the second one sends me out.
 * The server judges this itself in a word chain with three or more players, so the client rule only applies to the small ones.
 * @returns {'warn' | 'leave' | null}
 */
export function checkFailCombo(state, g, failId) {
	if (state.admin) {
		g.failCombo = 0;
		g.lastFail = failId;
		return null;
	}
	const room = state.rooms[state.myRoom];
	const family = state.meta.rules[room?.modeKey]?.rule;
	if (family === 'Hunmin') return null; // legacy: 훈민정음 does not count failed turns in a row
	const classic = family === 'Classic';
	const seqLength = g.seq.length;
	let action = null;
	if (!classic || (seqLength > 0 && seqLength < 3)) {
		if (g.lastFail === state.id && state.id === failId) {
			g.failCombo += 1;
			if (g.failCombo === 1) action = 'warn';
			if (g.failCombo > 1) action = 'leave';
		} else g.failCombo = 0;
	} else g.failCombo = 0;
	g.lastFail = failId;
	return action;
}

let deltaSeq = 0;
let historySeq = 0;
function addScore(state, id, n, kind, text) {
	const u = state.users[id];
	if (u) u.game.score = (u.game.score ?? 0) + n;
	if (state.game) state.game.deltas[id] = { seq: ++deltaSeq, text, kind };
}

/** A room's `game` block (turn order, round) is kept on the room so a spectator who arrives mid-game can draw it. */
function ensure(state) {
	if (!state.game) state.game = newGame(state.rooms[state.myRoom]);
	return state.game;
}

/** After the result arrives: who ranked where, and what each got. */
function resultRows(state, pkt) {
	const rows = [];
	for (const r of pkt.result ?? []) {
		const u = state.users[r.id];
		if (!u || !r.reward) continue;
		const gained = Math.round(r.reward.score ?? 0);
		const after = u.score;
		const before = Math.max(0, after - gained);
		rows.push({
			id: r.id,
			rank: Number(r.rank ?? rows.length),
			score: r.score ?? 0,
			team: u.game.team,
			reward: { exp: gained, money: Math.round(r.reward.money ?? 0), ep: Math.round(r.reward.ep ?? 0), baseExp: Math.round(r.reward._score ?? gained), baseMoney: Math.round(r.reward._money ?? r.reward.money ?? 0), log: r.reward._blog ?? [] },
			before,
			after,
			levelBefore: getLevel(before),
			levelAfter: getLevel(after),
			me: r.id === state.id
		});
	}
	return rows;
}

const CIRCLED = '①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳㉑㉒㉓㉔㉕㉖㉗㉘㉙㉚㉛㉜㉝㉞㉟㊱㊲㊳㊴㊵㊶㊷㊸㊹㊺㊻㊼㊽㊾㊿';
function setPool(g, pool, sub, ages, now) {
	g.pool = [...pool];
	g.subpool = [...(sub ?? [])];
	g.pieceAges = [...(ages ?? [])];
	g.poolAt = now;
}

/** 워드스택: the circled number of a player (their place in the order), as the legacy history writes it. */
function seatNumber(g, id) {
	for (let i = 0; i < g.seq.length; i++) {
		if (i >= 50) return '○';
		if (g.seq[i] === id) return CIRCLED[i];
	}
	return '○';
}

let throwSeq = 0;

/** 워드스택 turn ends (legacy `Wordstack.turnEnd`): words, pieces moving between players, penalties. */
function wordstackTurnEnd(state, g, data, now) {
	const id = data.target && typeof data.target === 'object' ? data.target.id : (data.target ?? data.id);
	const me = state.id;
	const score = Number(data.score) || 0;
	const spectating = state.users[me]?.game.form === 'S';
	const throwPiece = (from, to, piece, delay = 0) => {
		if (to && piece) g.throws = [...g.throws, { key: ++throwSeq, from, to, piece, delay }];
	};
	const push = (word) => {
		g.history = [{ key: ++historySeq, word, mean: data.mean ?? '', theme: data.theme ?? '', wc: data.wc ?? '', raw: true }, ...g.history].slice(0, 6);
	};
	if (data.error) return;
	if (data.ok || data.isBonus) {
		if (data.isBonus) {
			if (data.pieceEvent) {
				if (id === me && data.pool) setPool(g, data.pool, data.subpool, data.pieceAges, now);
				else if (data.attack === me && data.otherpool) setPool(g, data.otherpool, data.othersub, data.otherPieceAges, now);
				if (data.pieceEvent === 'leave-transfer') (data.pieces ?? []).forEach((piece, i) => throwPiece(data.from, id, piece, i * 110));
				else if (data.pieceEvent === 'expire-transfer') throwPiece(id, data.attack, data.piece);
				else throwPiece(null, id, data.piece);
			}
		} else if (id === me) {
			setPool(g, data.pool ?? [], data.subpool, data.pieceAges, now);
			push(`▶${seatNumber(g, data.attack)} ${data.value}`);
		} else if (data.attack === me) {
			setPool(g, data.otherpool ?? [], data.othersub, data.otherPieceAges, now);
			push(`${seatNumber(g, data.attack)}▶ ${data.value}`);
		} else if (spectating) {
			push(`${seatNumber(g, data.target)}▶${seatNumber(g, data.attack)} ${data.value}`);
		}
		if (!data.isBonus && data.attack && data.piece) throwPiece(id, data.attack, data.piece);
		if (score) {
			const text = score > 0 ? `+${score}` : String(score);
			if (data.scoreReset) {
				const u = state.users[id];
				if (u) u.game.score = (u.game.score ?? 0) + score;
			} else addScore(state, id, score, data.isBonus && !data.ok ? 'lost' : 'gain', text);
		}
	} else {
		g.phase = 'between'; // the round is over
	}
}

/** 십자말풀이: a solved bar shows its word and belongs to whoever solved it. */
function crosswordTurnEnd(state, g, data) {
	const id = data.target && typeof data.target === 'object' ? data.target.id : (data.target ?? data.id);
	const score = Number(data.score) || 0;
	if (!score || !Array.isArray(data.pos)) {
		g.phase = 'between';
		g.roundsDone = true; // the round is over: the round list is cleared
		return;
	}
	const [round, x, y, dir] = data.pos.map(Number);
	const bar = g.boards[round]?.find((b) => Number(b[0]) === x && Number(b[1]) === y && Number(b[2]) === dir);
	if (bar) {
		bar[4] = data.value;
		bar[5] = id;
	}
	if (id === state.id || (g.sel && g.sel.join(',') === data.pos.join(','))) g.sel = null; // that question is answered
	if (round !== g.selectedRound - 1) g.cwFlash = { round, key: ++throwSeq };
	addScore(state, id, score, 'gain', `+${score}`);
}

/** 솎솎: a found word takes its letters off the board. */
function sockTurnEnd(state, g, data) {
	const id = data.target && typeof data.target === 'object' ? data.target.id : (data.target ?? data.id);
	const score = Number(data.score) || 0;
	if (!score) {
		g.phase = 'between';
		return;
	}
	const key = String(data.value ?? '');
	g.maps = [...g.maps, key];
	const cells = [...g.board];
	const taken = [];
	for (const ch of key) {
		const at = cells.indexOf(ch);
		if (at >= 0) {
			taken.push({ key: ++throwSeq, index: at, char: ch });
			cells[at] = '　';
		}
	}
	g.board = cells.join('');
	g.picked = taken;
	addScore(state, id, score, 'gain', `+${score}`);
}

/** 잇땅: words claim cells; a settlement at the end gives bonus points. */
function linklandTurnEnd(state, g, data) {
	const id = data.target && typeof data.target === 'object' ? data.target.id : (data.target ?? data.id);
	const score = Number(data.score) || 0;
	if (data.settlement) {
		if (score) addScore(state, id, score, 'bonus', `+${score}`);
		return;
	}
	if (data.board) g.board = String(data.board);
	if (Array.isArray(data.owners)) g.owners = [...data.owners];
	if (data.value) {
		const suffix = data.replacement ? ' ↻' : ` ×${data.multiplier || (data.path ? data.path.length : 0)}`;
		g.words = [`${data.value}${suffix}`, ...g.words];
	}
	if (score) addScore(state, id, score, 'gain', `+${score}`);
}

/** 자음퀴즈 / 타자 대결 / 수학 대결: a packet is about one player's answer, not about whose turn it is. */
function quizTurnEnd(state, g, data, now) {
	const fam = familyOf(state);
	const id = data.target && typeof data.target === 'object' ? data.target.id : (data.target ?? data.id);
	const score = Number(data.score) || 0;
	const mine = id === state.id;
	if (fam === 'Wordstack') return wordstackTurnEnd(state, g, data, now);
	if (fam === 'Sock') return sockTurnEnd(state, g, data);
	if (fam === 'Crossword') return crosswordTurnEnd(state, g, data);
	if (fam === 'Linkland') return linklandTurnEnd(state, g, data);
	const mark = (word, mean, kind) => {
		g.history = [{ key: ++historySeq, word, mean, mark: kind }, ...g.history].slice(0, 6); // a stable key: only the new entry animates
	};
	if (fam === 'Jaqwi' || fam === 'Drawing') {
		if (data.giveup) g.gaveUp = { ...g.gaveUp, [id]: true };
		else if (data.answer) {
			g.answer = data.answer;
			g.phase = 'between';
		} else {
			addScore(state, id, score, 'gain', `+${score}`);
			g.winner = id;
			// legacy: once somebody has it, ten seconds are left at most
			const used = now - g.turnAt;
			if (g.roundTime - used > 10000) g.roundTime = used + 10000;
		}
		return;
	}
	if (data.error) {
		g.chain += 1;
		addScore(state, id, score, 'lost', String(score));
		if (fam === 'Mathquiz') mark(String(data.correctAnswer ?? ''), data.problem ?? '', 'bad');
	} else if (data.ok) {
		if (mine) {
			g.chain += 1;
			mark(String(data.value ?? ''), fam === 'Mathquiz' ? (data.problem ?? '') : '', fam === 'Mathquiz' ? 'ok' : null);
		}
		addScore(state, id, score, 'gain', `+${score}`);
	} else {
		// the round is over; 타자 대결 tells everyone's speed
		g.kpm = data.speed && typeof data.speed === 'object' ? { ...data.speed } : null;
		g.phase = 'between';
	}
}

/**
 * @param {*} state  connection state (users, rooms, myRoom, id, game, secretKey)
 * @param {*} pkt
 * @param {number} now  Date.now(), a parameter so tests are exact
 */
export function applyGamePacket(state, pkt, now = Date.now()) {
	switch (pkt.type) {
		case 'initSy':
			state.secretKey = secretFromInit(pkt.val);
			break;
		case 'starting':
			state.game = newGame(state.rooms[state.myRoom]);
			break;
		case 'roundReady': {
			const g = ensure(state);
			const room = state.rooms[state.myRoom];
			g.rounds = room?.rounds ?? g.rounds;
			g.round = Number(pkt.round ?? g.round);
			g.char = pkt.char ?? '';
			g.subChar = pkt.subChar ?? '';
			g.theme = pkt.theme ?? ''; // 훈민정음 / 단어 대결 / 자음퀴즈: the round's theme
			g.hints = [];
			g.history = [];
			g.answer = '';
			g.winner = null;
			g.gaveUp = {};
			g.kpm = null;
			g.list = Array.isArray(pkt.list) ? pkt.list.concat(pkt.list) : [];
			g.painter = pkt.painter && typeof pkt.painter === 'object' ? pkt.painter.id : (pkt.painter ?? null);
			g.answerLength = Number(pkt.length) || 0;
			g.painterWord = '';
			g.canvasImage = '';
			g.board = typeof pkt.board === 'string' ? pkt.board : '';
			g.boardSize = Number(pkt.boardSize) || (g.board.length === 100 ? 10 : 8);
			g.owners = Array.isArray(pkt.owners) ? [...pkt.owners] : [];
			g.maps = [];
			g.picked = [];
			g.words = [];
			g.throws = [];
			{
				// 십자말풀이: everyone starts on the board of their own place in the order (legacy selectedRound)
				const seq = Array.isArray(pkt.seq) ? seatIds(pkt.seq) : [];
				const turn = seq.indexOf(state.id);
				g.selectedRound = turn === -1 ? 1 : (turn % Math.max(1, room?.rounds ?? 1)) + 1;
				g.sel = null;
				g.roundsDone = false;
			}
			setPool(g, pkt.pool?.[state.id] ?? [], pkt.subpool?.[state.id] ?? [], pkt.pieceAges?.[state.id] ?? [], now);
			g.mission = pkt.mission ?? '';
			g.chain = 0;
			g.fail = null;
			g.last = null;
			g.current = null;
			g.phase = 'ready';
			if (room?.game?.seq) g.seq = seatIds(room.game.seq);
			if (pkt.enter && pkt.turn !== undefined) {
				// Walked in on a running game: the turn we're told about is whose it is.
				g.turn = pkt.turn;
				g.current = g.seq[pkt.turn] ?? null;
			}
			break;
		}
		case 'turnStart': {
			const g = ensure(state);
			if (pkt.seq) g.seq = seatIds(pkt.seq);
			g.turn = pkt.turn;
			g.current = QUIZ.includes(familyOf(state)) ? null : (g.seq[pkt.turn] ?? null); // the quiz modes have no turn order
			g.char = pkt.char ?? g.char;
			g.subChar = pkt.subChar ?? '';
			g.speed = pkt.speed ?? 0;
			g.turnTime = pkt.turnTime ?? 0;
			g.roundTime = pkt.roundTime ?? 0;
			g.mission = pkt.mission ?? g.mission;
			g.wordLength = pkt.wordLength ?? 0;
			g.gameId = pkt.gameId;
			g.turnId = pkt.turnId;
			g.inputEpoch = pkt.inputEpoch;
			if (Array.isArray(pkt.boards)) {
				g.boards = pkt.boards.map((round) => round.map((bar) => [...bar]));
				g.means = pkt.means ?? [];
			}
			g.turnAt = now;
			g.fail = null;
			g.last = null;
			g.phase = 'turn';
			break;
		}
		case 'turnError': {
			const g = ensure(state);
			// Like the legacy client: 404 is the server's own text; the others are the message of the code, plus ": detail".
			let text;
			if (pkt.code === 404) text = pkt.value;
			else {
				text = state.meta.labels[`kkutu.js.turn.error.${pkt.code}`] ?? '';
				if (pkt.value) text = `${text}: ${pkt.value}`;
			}
			g.fail = { code: pkt.code, text, at: now };
			break;
		}
		case 'turnHint': {
			const g = ensure(state);
			const fam = familyOf(state);
			if (fam === 'Jaqwi' && pkt.hint !== undefined) g.hints = [...g.hints, pkt.hint];
			// 그림퀴즈: a hint is a theme code (shown by its name) or a text
			else if (fam === 'Drawing' && pkt.hint !== undefined) g.hints = [...g.hints, Array.isArray(pkt.hint) ? (state.meta.labels[`word.theme.${pkt.hint[0]}`] ?? '') : pkt.hint];
			break;
		}
		case 'drawingContent': {
			const g = ensure(state);
			g.painterWord = pkt.word ?? '';
			g.painterTheme = pkt.theme ?? g.theme;
			break;
		}
		case 'drawCanvas': {
			const g = ensure(state);
			if (g.painter !== state.id && pkt.image) g.canvasImage = String(pkt.data ?? '');
			break;
		}
		case 'attackBonus': {
			const n = Number(pkt.value);
			if (Number.isFinite(n)) addScore(state, pkt.id, n, 'bonus', `+${n}`);
			break;
		}
		case 'turnEnd': {
			const g = ensure(state);
			const data = decryptTurnEnd(pkt, state.secretKey ?? 0);
			if (QUIZ.includes(familyOf(state))) {
				quizTurnEnd(state, g, data, now);
				break;
			}
			const score = Number(data.score) || 0;
			const bonus = Number(data.bonus) || 0;
			const target = data.target && typeof data.target === 'object' ? data.target.id : data.target;
			const id = target ?? g.current;
			if (id) addScore(state, id, score, data.ok ? 'gain' : 'lost', score > 0 ? `+${score - bonus}` : String(score));
			if (id && bonus) state.game.deltas[`${id}:bonus`] = { seq: ++deltaSeq, text: `+${bonus}`, kind: 'bonus' };
			const hint = data.hint && typeof data.hint === 'object' ? data.hint._id : data.hint;
			g.last = { ok: !!data.ok, id, value: data.value ?? '', mean: data.mean ?? '', theme: data.theme ?? '', wc: data.wc ?? '', score, bonus, hint: hint ?? '' };
			if (data.ok) {
				g.chain += 1;
				g.words.push({ id, word: data.value, mean: data.mean ?? '', theme: data.theme ?? '', score, bonus });
				if (g.words.length > 60) g.words.shift();
			}
			g.fail = null;
			// legacy: a good word calls the check without an id, a failed turn with the one who failed
			const action = checkFailCombo(state, g, data.ok ? undefined : id);
			g.combo = action ? { action, at: now } : null;
			g.phase = 'between';
			break;
		}
		case 'roundEnd': {
			const g = ensure(state);
			// Fresh totals (score, money, record) for everyone who played, before the rows are made from them.
			for (const [id, raw] of Object.entries(pkt.users ?? {})) {
				const u = state.users[id];
				if (!u || raw?.data?.score === undefined) continue;
				u.score = raw.data.score;
				u.lv = getLevel(raw.data.score);
				u.money = raw.money ?? u.money;
				u.record = raw.data.record ?? u.record;
				u.wins = Object.values(u.record).reduce((n, r) => n + (r?.[1] ?? 0), 0);
			}
			g.result = { rows: resultRows(state, pkt), practice: !!state.rooms[state.myRoom]?.practice };
			g.phase = 'ended';
			g.current = null;
			break;
		}
	}
}
