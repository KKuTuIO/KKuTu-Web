/**
 * 타자 연습 (the arcade's typing game): the client draws the lines and the input and sends each line the player types; the server holds the clock and
 * judges. What is counted here (the strokes, the live speed and accuracy) is the same reading as the server's, to show how it is going meanwhile.
 */
const DOUBLE_VOWELS = new Set([9, 10, 11, 14, 15, 16, 19]);
const DOUBLE_TAILS = new Set([3, 5, 6, 9, 10, 11, 12, 13, 14, 15, 18]);

/** The strokes of one character (a syllable is its jamo; a final consonant, a double vowel and a double final count as one more each). */
export function strokesOfChar(ch) {
	const s = ch.charCodeAt(0);
	if (s < 44032 || s > 55203) return 1;
	const b = s - 44032;
	const t = b % 28;
	const v = ((b - t) / 28) % 21;
	return (t ? 3 : 2) + (DOUBLE_VOWELS.has(v) ? 1 : 0) + (DOUBLE_TAILS.has(t) ? 1 : 0);
}

export const strokesOf = (text) => [...text].reduce((n, ch) => n + strokesOfChar(ch), 0);

/** The strokes of `expected` that `typed` got wrong (a place that differs counts the strokes of the character that should be there; what is missing counts). */
export function wrongStrokes(expected, typed) {
	const a = [...expected];
	const b = [...typed];
	let wrong = 0;
	for (let i = 0; i < a.length; i++) if (b[i] !== a[i]) wrong += strokesOfChar(a[i]);
	return wrong;
}

/** Each character of the line with whether the typed one so far is right (`null`: not typed yet). The one being composed by the IME is not judged yet. */
export function marks(expected, typed, composing = false) {
	const b = [...typed];
	return [...expected].map((ch, i) => {
		if (i >= b.length) return { ch, ok: null };
		if (composing && i === b.length - 1) return { ch, ok: null };
		return { ch, ok: b[i] === ch };
	});
}

/** The speed and the accuracy (per cent) so far: the lines done (`right` of `total` strokes) and the line being typed. A wrong stroke takes a right one away from the speed. */
export function live({ right, total, expected, typed, elapsed, composing = false }) {
	const m = marks(expected ?? '', typed ?? '', composing);
	let r = right;
	let n = total;
	for (const { ch, ok } of m) {
		if (ok === null) continue;
		const s = strokesOfChar(ch);
		n += s;
		if (ok) r += s;
	}
	return { speed: elapsed > 0 ? Math.round((Math.max(0, r - (n - r)) * 60000) / elapsed) : 0, accuracy: n ? Math.floor((r / n) * 1000) / 10 : 100, wrong: n - r };
}

/** "0:12": minutes and seconds. */
export function clock(ms) {
	const s = Math.floor(ms / 1000);
	return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** The locale key of the text for each reason a send did not count. */
export const ERROR_KEYS = {
	fast: 'play.typing.error.fast',
	long: 'play.typing.error.long',
	over: 'play.typing.error.over',
	none: 'play.typing.error.none',
	poor: 'play.typing.error.poor',
	same: 'play.typing.error.same',
	cap: 'play.typing.error.cap',
	text: 'play.typing.error.text'
};
export const UNPAID_KEYS = {
	goal: 'play.typing.unpaid.goal',
	speed: 'play.typing.unpaid.speed',
	accuracy: 'play.typing.unpaid.accuracy'
};
