import { describe, expect, it } from 'vitest';
import { captureRef, clearRef, inviteUrl, pendingRef } from './invite.js';

const memory = () => {
	const data = new Map();
	return { getItem: (k) => data.get(k) ?? null, setItem: (k, v) => data.set(k, v), removeItem: (k) => data.delete(k) };
};

describe('invite links', () => {
	it('keeps the code of an address and gives it back until it is let go of', () => {
		const storage = memory();
		expect(captureRef('https://kkutu.io/?ref=abcd2345', storage, 1000)).toBe('ABCD2345');
		expect(pendingRef(storage, 2000)).toBe('ABCD2345');
		clearRef(storage);
		expect(pendingRef(storage, 2000)).toBe('');
	});
	it('ignores an address without a plausible code, and forgets a code after thirty days', () => {
		const storage = memory();
		expect(captureRef('https://kkutu.io/', storage)).toBe('');
		expect(captureRef('https://kkutu.io/?ref=<script>', storage)).toBe('');
		expect(pendingRef(storage)).toBe('');
		captureRef('/?ref=ABCD2345', storage, 0);
		expect(pendingRef(storage, 31 * 24 * 3_600_000)).toBe('');
	});
	it('does not break where nothing can be kept', () => {
		const broken = { getItem: () => { throw new Error('no'); }, setItem: () => { throw new Error('no'); }, removeItem: () => { throw new Error('no'); } };
		expect(captureRef('/?ref=ABCD2345', broken)).toBe('ABCD2345');
		expect(pendingRef(broken)).toBe('');
		expect(() => clearRef(broken)).not.toThrow();
	});
	it('makes the link to share', () => {
		expect(inviteUrl('https://kkutu.io', 'ABCD2345')).toBe('https://kkutu.io/?ref=ABCD2345');
	});
});
