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

/** The best mark each jamo has got so far (the keyboard's colours): in place beats elsewhere beats not in the word. */
export function keyMarks(rows) {
	const out = {};
	for (const { j, m } of rows) {
		for (let i = 0; i < j.length; i++) if ((RANK[m[i]] ?? 0) > (RANK[out[j[i]]] ?? 0)) out[j[i]] = m[i];
	}
	return out;
}

/** The locale key of the text for each reason a try did not count. */
export const ERROR_KEYS = {
	invalid: 'play.riddle.error.invalid',
	notWord: 'play.riddle.error.notWord',
	dup: 'play.riddle.error.dup',
	done: 'play.riddle.error.done'
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

const EMOJI = { c: '🟩', p: '🟨', a: '⬜' };

/**
 * The text a finished puzzle is shared as, in the way of Wordle: a title with the number of tries, then a line of squares for each try. It says
 * nothing about the word. `title` is the game's name; `number` is the daily's number (a free puzzle has none).
 */
export function shareText({ rows, won, number = null, title }) {
	const head = `${number ? `${title} ${number}` : title} ${won ? rows.length : 'X'}/${MAX_TRIES}`;
	return `${head}\n\n${rows.map((r) => [...r.m].map((m) => EMOJI[m] ?? '⬜').join('')).join('\n')}`;
}
