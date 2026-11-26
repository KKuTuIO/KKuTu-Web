/**
 * The server's own record of a game (`game_replay`, payload version 1; see RecordGame.ts): a compact summary, not a packet stream, so it
 * is shown as a game sheet (who, how it ended, every word in order with its time) and not played on the board.
 *   rm [roomNo, modeIndex, modeKey, rule, lang, rounds, roundTime, title, gameTitle, opts[], injpick[], practice]
 *   p  [[id, nickname, exordial, level, form, team, robot, createdAt, profileTitle, profileName, profileImage, outfit[]]]
 *   w  words;  i [[player, word, turnMs, gameMs, round, turn, extra]];  x extras ("CA,score,bonus" accepted, "CR,reason,code" refused ...)
 *   rs [[player, rank, score, dim, exp, money, playTime, ep]];  wn winners;  s/e/d start, end, duration (ms)
 */
export const b64json = (s) => {
	try {
		const bin = atob(String(s).replace(/-/g, '+').replace(/_/g, '/'));
		return JSON.parse(new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0))));
	} catch {
		return null;
	}
};

/** What an input row's extra says: did the word count, for how many points. */
export function inputOutcome(extra) {
	if (typeof extra !== 'string' || !extra) return { ok: null, score: null, reason: '' };
	const [kind, a, b] = extra.split(',');
	if (kind === 'CA') return { ok: true, score: Number(a) || 0, bonus: Number(b) || 0, reason: '' };
	if (kind === 'CR') return { ok: false, score: null, reason: a ?? '', code: b ?? '' };
	if (kind === 'OUT') {
		const o = b64json(a);
		if (o) return { ok: o.s === 'accepted', score: Number.isFinite(o.d) ? o.d : null, reason: o.s === 'accepted' ? '' : String(o.r ?? o.s ?? '') };
	}
	return { ok: null, score: null, reason: '' };
}

/**
 * What a game is reviewed by: the words that counted (`a`: the game's one chain; `ap`: [[player, words]] for each player's own).
 * @returns null when the record keeps none (a ranked game seen by someone who did not play it, or a mode without words)
 */
export function reviewOf(payload) {
	const dict = Array.isArray(payload?.w) ? payload.w : [];
	const wordsAt = (list) => (Array.isArray(list) ? list.map((i) => dict[i]).filter(Boolean) : []);
	const chains = (Array.isArray(payload?.ap) ? payload.ap : [])
		.map((row) => ({ player: row?.[0], words: wordsAt(row?.[1]) }))
		.filter((c) => c.words.length);
	const chain = wordsAt(payload?.a);
	const accepted = chain.length ? chain : chains.flatMap((c) => c.words);
	if (!accepted.length) return null;
	const unique = [...new Set(accepted)];
	return {
		ordered: chain.length > 0, // false: no single chain was kept, the players' chains are put together
		accepted,
		unique,
		longest: unique.reduce((best, w) => (w.length > best.length ? w : best), ''),
		typed: new Set(dict).size,
		events: Array.isArray(payload.i) ? payload.i.length : 0,
		chains
	};
}

/** The chain modes the academy's engine can follow (it links words by one letter, so not 영어 끄투's three). */
const ANALYSABLE = new Set(['KSH', 'ESH', 'KAP', 'EAP', 'KKT', 'KFT']);

/**
 * The dictionary rules of a finished game, for the engine that looks for other moves.
 * @param {{modeKey:string, lang:string, opts:string[]}} room the sheet's room (`opts`: the names of the options that were on)
 * @returns the engine's config, or null when the mode cannot be analysed
 */
export function analysisConfig({ modeKey, lang, opts }) {
	if (!ANALYSABLE.has(modeKey)) return null;
	const on = (name) => opts.includes(name);
	const letters = modeKey === 'KFT' || (modeKey === 'KKT' && on('four')) ? 4 : modeKey === 'KKT' ? 3 : 0; // 0: any length
	const strict = on('strict');
	return {
		lang,
		dictionary: on('injeong') ? 'COMBINED' : 'STANDARD',
		direction: modeKey === 'KAP' || modeKey === 'EAP' ? 'REVERSE' : 'FORWARD',
		duum: !on('antidoum'),
		minLength: letters ? (on('sami') ? 2 : letters) : on('no2') ? 3 : 2,
		maxLength: letters || 64,
		includeLoanword: !on('loanword'),
		includeSpaced: !strict,
		includeDialect: !strict,
		includeOld: !strict,
		includeCultural: !strict,
		includeKung: modeKey === 'KKT' && letters === 3
	};
}

/**
 * @param {object} payload the decoded payload (`detailPayload`; the public one has no start/end/winners, and its ranks start at 0)
 * @param {{startedAt?:number, endedAt?:number, durationMs?:number, winnerIds?:string[], gameId?:string}} [game] the game record around it
 * @returns the game sheet, or null when it is not one
 */
export function summarize(payload, game = {}) {
	if (!payload || typeof payload !== 'object' || !Array.isArray(payload.rm) || !Array.isArray(payload.p)) return null;
	const rm = payload.rm;
	const dict = Array.isArray(payload.w) ? payload.w : [];
	const extras = Array.isArray(payload.x) ? payload.x : [];
	const winners = new Set(Array.isArray(payload.wn) ? payload.wn : []);
	const winnerIds = new Set(Array.isArray(game?.winnerIds) ? game.winnerIds : []);
	const players = payload.p.map((row, index) => ({
		index,
		id: String(row?.[0] ?? ''),
		name: String(row?.[1] || row?.[9] || row?.[0] || ''),
		level: Number(row?.[3]) || 0,
		team: Number(row?.[5]) || 0,
		robot: !!row?.[6],
		outfit: Array.isArray(row?.[11]) ? row[11] : []
	}));
	const results = (Array.isArray(payload.rs) ? payload.rs : [])
		.map((r) => ({ player: r?.[0], rank: (Number(r?.[1]) || 0) + 1, score: Number(r?.[2]) || 0, exp: Number(r?.[4]) || 0, money: Number(r?.[5]) || 0, ep: Number(r?.[7]) || 0, won: winners.has(r?.[0]) || winnerIds.has(players[r?.[0]]?.id) }))
		.filter((r) => players[r.player])
		.sort((a, b) => a.rank - b.rank || b.score - a.score);
	const inputs = (Array.isArray(payload.i) ? payload.i : []).map((r) => ({
		player: r?.[0],
		word: dict[r?.[1]] ?? '',
		turnMs: Number(r?.[2]) || 0,
		at: Number(r?.[3]) || 0,
		round: Number(r?.[4]) || 0,
		turn: Number(r?.[5]) || 0,
		...inputOutcome(extras[r?.[6]])
	}));
	return {
		id: String(game?.gameId ?? payload.g ?? ''),
		start: Number(game?.startedAt ?? payload.s) || 0,
		end: Number(game?.endedAt ?? payload.e) || 0,
		duration: Number(game?.durationMs ?? payload.d) || 0,
		room: { no: rm[0], modeIndex: rm[1], modeKey: rm[2], rule: rm[3], lang: rm[4], rounds: Number(rm[5]) || 0, time: Number(rm[6]) || 0, title: String(rm[7] ?? ''), opts: Array.isArray(rm[9]) ? rm[9] : [], practice: !!rm[11] },
		players,
		results,
		inputs,
		review: reviewOf(payload),
		rounds:[...new Set(inputs.map((i) => i.round))].sort((a, b) => a - b)
	};
}

/** "3:05" / "1:02:09" */
export function clock(ms) {
	const total = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
	const h = Math.floor(total / 3600);
	const m = Math.floor((total % 3600) / 60);
	const s = total % 60;
	return h ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
}
