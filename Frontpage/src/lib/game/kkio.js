/**
 * The `.kkio` replay file, byte for byte the legacy client's: "KKIO", two random key bytes, the length of the body (3 bytes, little endian),
 * a zero byte, then the body = base64(utf-8(JSON of the recording)) with every byte XOR-ed with the key (alternating). The legacy limits
 * apply (a body over ~8 MB cannot be written), so a file made here can be opened by the legacy client's reader, and the other way round.
 *
 * The recording ($rec): {version, me, time, title, roundTime, round, mode, limit, game, opts, readies, injpick, players[], events[]}
 * with `players` = [{id, score, data, equip, title, robot?}] and `events` = [{time (ms after `time`), data (a server packet)}].
 */
import { encodeContainer, decodeContainer, MAX_BODY } from './container.js';

export const KKIO_MAX_BODY = MAX_BODY; // the legacy reader refuses a longer one (byte 8 above 127)

/** @param {object} rec the recording @returns {Uint8Array} @throws if it is too large for the format */
export function encodeKkio(rec, key) {
	return encodeContainer('KKIO', rec, key);
}

/** The legacy reader's checks, then the JSON. @throws on anything that is not a replay of ours */
export function decodeKkio(bytes) {
	return checkRecording(decodeContainer('KKIO', bytes));
}

/** The legacy integrity checks, plus the shape the player relies on. */
export function checkRecording(rec) {
	if (!rec || typeof rec !== 'object') throw new Error('damaged');
	if (!(new Date(rec.time) > 0)) throw new Error('bad date');
	if (!Array.isArray(rec.players) || !rec.players.length) throw new Error('no players');
	if (!Array.isArray(rec.events) || !rec.events.length) throw new Error('no events');
	if (!rec.events.every((e) => e && typeof e === 'object' && Number.isFinite(e.time) && e.data && typeof e.data === 'object' && typeof e.data.type === 'string')) throw new Error('bad events');
	return rec;
}

/** "KKuTuIO2025-11-5 13-5-9.kkio" (the legacy name). */
export function kkioName(rec) {
	const d = new Date(rec.time);
	return `KKuTuIO${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${d.getHours()}-${d.getMinutes()}-${d.getSeconds()}.kkio`;
}

/** Which of the recorded values a file may carry into the page as markup: none. Text with angle brackets is made harmless on opening. */
export function sanitizeRecording(value) {
	if (typeof value === 'string') return value.replace(/</g, '＜').replace(/>/g, '＞');
	if (Array.isArray(value)) return value.map(sanitizeRecording);
	if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, sanitizeRecording(v)]));
	return value;
}
