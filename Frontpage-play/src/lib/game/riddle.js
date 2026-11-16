/**
 * 리들 (the arcade's word puzzle): the client only draws it. The server holds the answer, judges every try and says the marks (`c` in its place,
 * `p` elsewhere, `a` not in the word); what is here is the keyboard, the typing of jamo and the small texts.
 */
export const LENGTH = 6;
export const MAX_TRIES = 6;

/** The on-screen keyboard: the 24 basic jamo in the Korean layout's places (a compound vowel or a double consonant is typed as its parts). */
export const KEY_ROWS = [['ㅂ', 'ㅈ', 'ㄷ', 'ㄱ', 'ㅅ', 'ㅛ', 'ㅕ', 'ㅑ'], ['ㅁ', 'ㄴ', 'ㅇ', 'ㄹ', 'ㅎ', 'ㅗ', 'ㅓ', 'ㅏ', 'ㅣ'], ['ㅋ', 'ㅌ', 'ㅊ', 'ㅍ', 'ㅠ', 'ㅜ', 'ㅡ']];

const BY_CODE = {
	KeyQ: 'ㅂ', KeyW: 'ㅈ', KeyE: 'ㄷ', KeyR: 'ㄱ', KeyT: 'ㅅ', KeyY: 'ㅛ', KeyU: 'ㅕ', KeyI: 'ㅑ', KeyO: 'ㅏㅣ', KeyP: 'ㅓㅣ',
	KeyA: 'ㅁ', KeyS: 'ㄴ', KeyD: 'ㅇ', KeyF: 'ㄹ', KeyG: 'ㅎ', KeyH: 'ㅗ', KeyJ: 'ㅓ', KeyK: 'ㅏ', KeyL: 'ㅣ',
	KeyZ: 'ㅋ', KeyX: 'ㅌ', KeyC: 'ㅊ', KeyV: 'ㅍ', KeyB: 'ㅠ', KeyN: 'ㅜ', KeyM: 'ㅡ'
};
const BY_CODE_SHIFT = { KeyQ: 'ㅂㅂ', KeyW: 'ㅈㅈ', KeyE: 'ㄷㄷ', KeyR: 'ㄱㄱ', KeyT: 'ㅅㅅ', KeyO: 'ㅑㅣ', KeyP: 'ㅕㅣ' };

/**
 * The jamo a physical key types (null for a key that types none). The key's place is read, not the character, so it works with the Korean
 * input method on, off, or half-way through a syllable. Shift gives the double consonants and ㅒ ㅖ, which are typed as their parts.
 */
export function jamoOfKey(code, shift = false) {
	return (shift && BY_CODE_SHIFT[code]) || BY_CODE[code] || null;
}

/** `add` appended to what is typed, or the same string when it does not all fit. */
export function typeJamo(typed, add) {
	return typed.length + add.length > LENGTH ? typed : typed + add;
}

const RANK = { a: 1, p: 2, c: 3 };

/**
 * A try as the board shows it: one tile for each syllable (`row.s` says how many jamo each is spelled in, `row.w` is the word). A tile says how many of its jamo
 * are in their place (`c`, orange) and how many are in the word elsewhere (`p`, yellow). Its `state`: `g` the syllable is right (every jamo in place), `a` none of its
 * jamo is in the word, `o` some are in place and the rest not in the word, `y` some are in the word elsewhere and none in place, `m` both. A try without sizes is a tile for each jamo.
 */
export function tilesOf(row) {
	const sizes = row.s?.length ? row.s : Array.from({ length: row.j.length }, () => 1);
	const chars = row.w ? [...row.w] : null;
	const out = [];
	let at = 0;
	for (let i = 0; i < sizes.length; i++) {
		const size = sizes[i];
		const part = row.m.slice(at, at + size);
		const c = [...part].filter((x) => x === 'c').length;
		const p = [...part].filter((x) => x === 'p').length;
		const jamo = row.j.slice(at, at + size);
		out.push({ size, start: at, ch: chars && chars.length === sizes.length ? chars[i] : jamo, c, p, state: c === size ? 'g' : c + p === 0 ? 'a' : c && p ? 'm' : c ? 'o' : 'y' });
		at += size;
	}
	return out;
}

/**
 * What the keyboard shows. A tile does not say which of its jamo are in the word, so a key is coloured only for what a try makes certain: the jamo of a right
 * syllable are in place (`c`); the jamo of a syllable whose jamo are all in the word (in place or not) are in the word (`p`); and a jamo is not in the word (`a`) when
 * every place it has in the try is in a tile with none in the word (a jamo that the word has would have been counted in one of its places, so one counted out in
 * a tile because it was already counted in another is not taken for absent). The best of what a key got stays: in place beats in the word beats not in it.
 */
export function keyMarks(rows) {
	const out = {};
	const give = (key, mark) => {
		if ((RANK[mark] ?? 0) > (RANK[out[key]] ?? 0)) out[key] = mark;
	};
	for (const row of rows) {
		const tiles = tilesOf(row);
		const onlyGray = {};
		for (const tile of tiles) {
			for (const key of row.j.slice(tile.start, tile.start + tile.size)) {
				onlyGray[key] = (onlyGray[key] ?? true) && tile.state === 'a';
			}
			const mark = tile.state === 'g' ? 'c' : tile.c + tile.p === tile.size && tile.state !== 'a' ? 'p' : null;
			if (mark) for (const key of row.j.slice(tile.start, tile.start + tile.size)) give(key, mark);
		}
		for (const key of Object.keys(onlyGray)) if (onlyGray[key]) give(key, 'a');
	}
	return out;
}

/** The locale key of the text for each reason a try did not count. */
export const ERROR_KEYS = {
	invalid: 'play.riddle.error.invalid',
	notWord: 'play.riddle.error.notWord',
	dup: 'play.riddle.error.dup',
	done: 'play.riddle.error.done',
	nohint: 'play.riddle.error.nohint',
	poor: 'play.riddle.error.poor'
};

/** "06:12:05": what is left until `at` (ms since the epoch). */
export function countdown(at, now = Date.now()) {
	const s = Math.max(0, Math.ceil((at - now) / 1000));
	const p = (n) => String(n).padStart(2, '0');
	return `${p(Math.floor(s / 3600))}:${p(Math.floor((s % 3600) / 60))}:${p(s % 60)}`;
}

/** The day the daily began (2026-11-15 is number 1), as a day number since the epoch. */
const FIRST_DAY = Date.UTC(2026, 10, 15) / 86_400_000;
const KST = 9 * 3_600_000;

/** The number of the daily whose puzzle is open until `resetAt` (the server says when the next one opens). */
export const dailyNumber = (resetAt) => Math.floor((resetAt - 1 + KST) / 86_400_000) - FIRST_DAY + 1;

/** A tile as squares: 🟩 a right syllable, ⬜ one with no jamo in the word, 🟧 the jamo in place and 🟨 the ones in the word elsewhere, each with its number (a one-jamo tile has none). */
function tileEmoji(t) {
	if (t.state === 'g') return '🟩';
	if (t.state === 'a') return '⬜';
	const n = (k) => (t.size > 1 ? String(k) : '');
	return `${t.c ? `🟧${n(t.c)}` : ''}${t.p ? `🟨${n(t.p)}` : ''}`;
}

/**
 * The text a finished puzzle is shared as, in the way of Wordle: a title with the number of tries, then a line of squares for each try. It says
 * nothing about the word. `title` is the game's name; `number` is the daily's number (a free puzzle has none); `hint` marks a puzzle that used its free hint, `paid` one that bought the paid hint.
 */
export function shareText({ rows, won, number = null, title, hint = false, paid = false }) {
	const head = `${number ? `${title} ${number}` : title} ${won ? rows.length : 'X'}/${MAX_TRIES}${hint ? ' 💡' : ''}${paid ? ' 💰' : ''}`;
	return `${head}\n\n${rows.map((r) => { const tiles = tilesOf(r); return tiles.map(tileEmoji).join(tiles.some((t) => t.size > 1) ? ' ' : ''); }).join('\n')}`;
}
