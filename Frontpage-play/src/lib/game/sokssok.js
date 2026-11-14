/**
 * 미니 솎솎 (the arcade's word game): the client draws the board and the input and sends the places of the tapped tiles; the server judges. The
 * composition below is only to show what is being spelled while the player taps (it is the same reading as the server's `compose`).
 */
export const SIZE = 6;
export const CELLS = SIZE * SIZE;
export const MAX_PICK = 24;
/** The board is drawn at the frog's size: 44 px tiles and 6 px between them (294 px of his 298 px mouth). */
export const TILE = 44;
export const GAP = 6;
export const PITCH = TILE + GAP;

const CHO = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
const JUNG = 'ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ';
const JONG = ['', ...'ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ'];
const JOIN_FINAL = { ㄱㅅ: 'ㄳ', ㄴㅈ: 'ㄵ', ㄴㅎ: 'ㄶ', ㄹㄱ: 'ㄺ', ㄹㅁ: 'ㄻ', ㄹㅂ: 'ㄼ', ㄹㅅ: 'ㄽ', ㄹㅌ: 'ㄾ', ㄹㅍ: 'ㄿ', ㄹㅎ: 'ㅀ', ㅂㅅ: 'ㅄ' };
const SIMPLE_FINAL = new Set('ㄱㄲㄴㄷㄹㅁㅂㅅㅆㅇㅈㅊㅋㅌㅍㅎ');
const isVowel = (t) => t.length === 1 && JUNG.includes(t);
const isConsonant = (t) => t.length === 1 && CHO.includes(t);

/** The word a sequence of tiles spells, or null when it spells none. A consonant after a vowel closes the syllable only when no vowel follows it. */
export function compose(tiles) {
	let out = '';
	let i = 0;
	while (i < tiles.length) {
		const cho = tiles[i++];
		const jung = tiles[i++];
		if (!isConsonant(cho) || jung === undefined || !isVowel(jung)) return null;
		let jong = '';
		const a = tiles[i];
		const b = tiles[i + 1];
		const c = tiles[i + 2];
		if (a !== undefined && isConsonant(a) && !(b !== undefined && isVowel(b))) {
			if (!SIMPLE_FINAL.has(a)) return null;
			const pair = b !== undefined && isConsonant(b) ? JOIN_FINAL[a + b] : undefined;
			if (pair && (c === undefined || !isVowel(c))) {
				jong = pair;
				i += 2;
			} else {
				jong = a;
				i += 1;
			}
		}
		out += String.fromCodePoint(0xac00 + (CHO.indexOf(cho) * 21 + JUNG.indexOf(jung)) * 28 + JONG.indexOf(jong));
	}
	return out;
}

/** What the input shows: the longest part that already spells a word, then the tiles that do not yet (a lone consonant waiting for its vowel). */
export function preview(tiles) {
	for (let k = tiles.length; k >= 0; k--) {
		const word = compose(tiles.slice(0, k));
		if (word !== null) return word + tiles.slice(k).join('');
	}
	return tiles.join('');
}

/** The locale key of the text for each reason a send did not count. */
export const ERROR_KEYS = {
	invalid: 'play.sok.error.invalid',
	short: 'play.sok.error.short',
	dup: 'play.sok.error.dup',
	notWord: 'play.sok.error.notWord',
	over: 'play.sok.error.over',
	slow: 'play.sok.error.slow'
};

/** Milliseconds left of the round, counted from when the server said how many were left. */
export const msLeft = (session, now = Date.now()) => (session ? Math.max(0, session.left - (now - session.at)) : 0);

/** "1:23": minutes and seconds (the seconds round up, so 0:00 means the time is up). */
export function clock(ms) {
	const s = Math.ceil(ms / 1000);
	return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
