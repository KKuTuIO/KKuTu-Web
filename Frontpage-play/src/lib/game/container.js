/**
 * The legacy client's file container, shared by replays (.kkio, magic "KKIO") and settings (.kkis, magic "KKIS"): the magic (4 bytes), two random
 * key bytes, the length of the body (3 bytes, little endian), a zero byte, then the body = base64(utf-8(JSON)) with every byte XOR-ed with the
 * key (alternating). The legacy limit applies (a body over ~8 MB cannot be written).
 */
export const MAX_BODY = 0x7fffff;

const toBase64 = (text) => {
	const bytes = new TextEncoder().encode(text);
	let bin = '';
	for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
	return btoa(bin);
};
const fromBase64 = (b64) => {
	const bin = atob(b64);
	const bytes = new Uint8Array(bin.length);
	for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
	return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
};

/** @param {string} magic four characters @param {any} value anything JSON can hold @returns {Uint8Array} @throws if it is too large */
export function encodeContainer(magic, value, key = [16 + Math.floor(Math.random() * 239), 16 + Math.floor(Math.random() * 239)]) {
	const body = toBase64(JSON.stringify(value));
	if (body.length > MAX_BODY) throw new Error('too large');
	const out = new Uint8Array(body.length + 10);
	for (let i = 0; i < 4; i++) out[i] = magic.charCodeAt(i);
	out.set(key, 4);
	for (let i = 0; i < 3; i++) out[6 + i] = (body.length >> (i * 8)) & 0xff;
	out[9] = 0;
	for (let i = 0; i < body.length; i++) out[10 + i] = body.charCodeAt(i) ^ key[i % 2];
	return out;
}

/** The legacy reader's checks, then the JSON. @throws on anything that is not a file of this kind */
export function decodeContainer(magic, bytes) {
	const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
	if (b.length < 11 || [...magic].some((c, i) => b[i] !== c.charCodeAt(0))) throw new Error('wrong file');
	if (b[9] !== 0 || b[8] > 127) throw new Error('bad header');
	const length = b[6] + (b[7] << 8) + (b[8] << 16);
	if (length <= 0 || length !== b.length - 10) throw new Error('bad length');
	let body = '';
	for (let i = 10; i < b.length; i++) body += String.fromCharCode(b[i] ^ b[4 + (i % 2)]);
	try {
		return JSON.parse(fromBase64(body));
	} catch {
		throw new Error('damaged');
	}
}
