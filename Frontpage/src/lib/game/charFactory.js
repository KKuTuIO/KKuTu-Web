/**
 * 글자 조합소 (the legacy "char factory"): up to six letter pieces ($WPC꽝 = a common piece of 꽝, $WPB uncommon, $WPA rare, $WPE an
 * event piece) are put together into a word. A word in the dictionary gives rewards; two pieces that make no word can be fused into a
 * new piece. The server (`/cf`) is the judge of everything; this only mirrors its rules to show a preview. Pure helpers + the two requests.
 */
export const MAX_TRAY = 6;
/** What a piece adds to the combination's level (the server: 68 - the type's letter, so C=1, B=2, A=3). */
export const PIECE_LEVEL = { WPC: 1, WPB: 2, WPA: 3 };
/** Piece kinds, best first. */
export const PIECE_KINDS = ['WPA', 'WPB', 'WPC', 'WPE'];

export const pieceKind = (key) => String(key).slice(1, 4);
export const pieceChar = (key) => String(key).slice(4);
export const isPiece = (key) => /^\$WP[ABCE]./.test(String(key));

/**
 * The word, level and validity of a tray (an array of piece keys). Event pieces cannot be mixed with the others (412);
 * an event combination has level -1.
 * @returns {{word:string, level:number, event:boolean, malformed:boolean}}
 */
export function analyzeTray(tray) {
	const event = tray.length > 0 && pieceKind(tray[0]) === 'WPE';
	let malformed = false;
	let level = 0;
	let word = '';
	for (const key of tray) {
		if ((pieceKind(key) === 'WPE') !== event) malformed = true;
		word += pieceChar(key);
		level += PIECE_LEVEL[pieceKind(key)] ?? 0;
	}
	return { word, level: event ? -1 : level, event, malformed };
}

/**
 * Whether one more piece fits: 435 when the tray is full, 434 when I have no more of it.
 * @returns {null|435|434}
 */
export function addError(tray, key, have) {
	if (tray.length >= MAX_TRAY) return 435;
	return tray.filter((k) => k === key).length >= have ? 434 : null;
}

/** My pieces to choose from: the box entries that are pieces and still usable (count left, not expired), best kind first or by name. */
export function ownedPieces(entries, sort = 'high', now = Date.now()) {
	// the server reads one character of a piece (index 4), so a piece whose key carries more than that cannot be combined
	const list = entries.filter((e) => isPiece(e.item._id) && [...pieceChar(e.item._id)].length === 1 && e.count > 0 && !(e.expire && e.expire * 1000 < now));
	const rank = (e) => PIECE_KINDS.indexOf(pieceKind(e.item._id));
	const byChar = (a, b) => pieceChar(a.item._id).localeCompare(pieceChar(b.item._id), 'ko');
	return list.sort((a, b) => (sort === 'name' ? byChar(a, b) : sort === 'low' ? rank(b) - rank(a) || byChar(a, b) : rank(a) - rank(b) || byChar(a, b)));
}

/** What a combination can give: `{cost, data:[{key, rate}]}` or `{error}`. */
export async function fetchPreview(word, level, blend, fetchFn = fetch) {
	try {
		const res = await fetchFn(`/cf/${encodeURIComponent(word)}?l=${level}&b=${blend ? '1' : ''}`);
		return await res.json();
	} catch {
		return { error: 400 };
	}
}

/** Does the combination: `{result, box, money, gain}` or `{error}`. */
export async function composeTray(tray, fetchFn = fetch) {
	try {
		const res = await fetchFn('/cf', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ tray: tray.join('|') }) });
		return await res.json();
	} catch {
		return { error: 400 };
	}
}

/** What a combination added to my box: the items whose count went up between two boxes (`{key, q}`). */
export function boxGains(before, after) {
	const n = (v) => Number(v?.value ?? v ?? 0);
	return Object.keys(after ?? {})
		.map((key) => ({ key, q: n(after[key]) - n(before?.[key]) }))
		.filter((g) => g.q > 0);
}

/** "x1" or "x0-1": how many a reward gives (the legacy text: the sure amount up to the amount if the chance hits). */
export function rewardCount(rate) {
	const lo = Math.floor(rate);
	const hi = Math.ceil(rate);
	return lo === hi ? `x${lo}` : `x${lo}-${hi}`;
}
