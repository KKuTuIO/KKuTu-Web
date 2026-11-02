// Compact MD5 (hex). The game server expects room passwords as MD5 hex, like the legacy client.
export function md5(str) {
	const bytes = new TextEncoder().encode(str);
	const K = Array.from({ length: 64 }, (_, i) => Math.floor(Math.abs(Math.sin(i + 1)) * 2 ** 32) >>> 0);
	const S = [7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21];
	const len = bytes.length;
	const padded = new Uint8Array(((len + 8) >> 6 << 6) + 64);
	padded.set(bytes);
	padded[len] = 0x80;
	new DataView(padded.buffer).setUint32(padded.length - 8, (len * 8) >>> 0, true);
	new DataView(padded.buffer).setUint32(padded.length - 4, Math.floor((len * 8) / 2 ** 32), true);
	let a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
	const dv = new DataView(padded.buffer);
	for (let off = 0; off < padded.length; off += 64) {
		const M = Array.from({ length: 16 }, (_, i) => dv.getUint32(off + i * 4, true));
		let A = a0, B = b0, C = c0, D = d0;
		for (let i = 0; i < 64; i++) {
			let F, g;
			if (i < 16) { F = (B & C) | (~B & D); g = i; }
			else if (i < 32) { F = (D & B) | (~D & C); g = (5 * i + 1) % 16; }
			else if (i < 48) { F = B ^ C ^ D; g = (3 * i + 5) % 16; }
			else { F = C ^ (B | ~D); g = (7 * i) % 16; }
			F = (F + A + K[i] + M[g]) >>> 0;
			A = D; D = C; C = B;
			const s = S[(i >> 4) * 4 + (i % 4)];
			B = (B + ((F << s) | (F >>> (32 - s)))) >>> 0;
		}
		a0 = (a0 + A) >>> 0; b0 = (b0 + B) >>> 0; c0 = (c0 + C) >>> 0; d0 = (d0 + D) >>> 0;
	}
	return [a0, b0, c0, d0].map((v) => [0, 8, 16, 24].map((s) => ((v >>> s) & 255).toString(16).padStart(2, '0')).join('')).join('');
}
