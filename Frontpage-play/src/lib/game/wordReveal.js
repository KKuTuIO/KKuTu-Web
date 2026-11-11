/**
 * How a played word is put on the display, timed like the legacy client's `pushDisplay`: the letters appear one by one
 * (on a beat for short words), a sound ticks with each, the word pulses three times, then it joins the history.
 * Pure: it returns a list of timed steps and the component runs them.
 */
const BEAT = [null, '10000000', '10001000', '10010010', '10011010', '11011010', '11011110', '11011111', '11111111'];

/**
 * @param {{text: string, turnTime: number, speed: number, mission?: string, kkt?: boolean, reverse?: boolean, en?: boolean}} o
 * @returns {{mode: 'beat'|'run', letterSound: string, steps: {at: number, index: number, char: string, mission: boolean, sound: string}[], pulses: {at: number, sound?: string}[], endSound: string|null, historyAt: number, revealMs: number}}
 */
export function planReveal({ text, turnTime, speed, mission = '', kkt = false, reverse = false, en = false }) {
	const len = text.length;
	const tick = turnTime / 96;
	const sg = turnTime / 12;
	const beat = BEAT[len];
	const letterSound = beat || (en && len < 10) ? `As${speed}` : 'Al';
	const steps = [];
	if (beat) {
		let j = 0;
		for (let i = 0; i < beat.length; i++) {
			if (beat[i] === '0') continue;
			const char = reverse ? text.charAt(len - j - 1) : text.charAt(j);
			steps.push({ at: i * tick, index: j, char, mission: char === mission, sound: letterSound });
			j++;
		}
	} else {
		// A long word comes out evenly over `sg` ms, the whole text redrawn each time.
		for (let i = 0; i < len; i++) {
			const char = reverse ? text[len - i - 1] : text[i];
			steps.push({ at: (i * sg) / len, index: i, char, mission: char === mission, sound: letterSound });
		}
	}
	// Three pulses, then the history entry (the 쿵쿵따 modes make a "kung" on the 2nd and 3rd pulse).
	const pulses = [0, 1, 2].filter((v) => !(kkt && v === 1)).map((v) => ({ at: sg + v * tick * 2, sound: kkt ? 'kung' : undefined }));
	return { mode: beat ? 'beat' : 'run', letterSound, steps, pulses, endSound: kkt ? null : `K${speed}`, historyAt: sg + tick * 4, revealMs: sg };
}

/** The hint after a timeout: the part up to the starting letter (inclusive) is bright, the rest dimmed. */
export function splitHint(hint, chars, reverse = false) {
	if (!hint) return null;
	let hi = hint.indexOf(chars[0]);
	if (hi === -1 && chars[1]) hi = hint.indexOf(chars[1]);
	return reverse ? { dim: hint.slice(0, hi), bright: hint.slice(hi) } : { bright: hint.slice(0, hi + 1), dim: hint.slice(hi + 1) };
}

/** The five digits (or "-0050", "100k") each drawn in its own cell, as the legacy score board does. */
export function scoreChars(score) {
	const n = Math.round(Number(score) || 0);
	const pad = (v, len) => String(v).padStart(len, '0');
	if (n < 0) return `-${pad(Math.abs(n), 4)}`;
	if (n > 99999) return `${pad(Math.round(n * 0.001), 4)}k`;
	return pad(n, 5);
}
