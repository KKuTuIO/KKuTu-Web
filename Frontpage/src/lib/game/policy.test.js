import { describe, expect, it } from 'vitest';
import { currentPolicy, hasAgreed, recordAgreement } from './policy.js';

const memory = () => {
	const m = new Map();
	return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, String(v)), m };
};

describe('policy consent', () => {
	it('asks until the current versions are agreed to, then stops', () => {
		const s = memory();
		expect(hasAgreed(s)).toBe(false);
		recordAgreement(s, new Date('2026-11-03'));
		expect(hasAgreed(s)).toBe(true);
		expect(s.m.get('isPolicyChecked')).toBe('10'); // legacy flag: month index (November = 10)
	});
	it('asks again when a document version changes', () => {
		const s = memory();
		s.setItem('kkutu-play-policy', currentPolicy().replace('terms:3', 'terms:2'));
		expect(hasAgreed(s)).toBe(false);
	});
	it('treats unavailable storage as not agreed and never throws', () => {
		const broken = { getItem() { throw new Error('denied'); }, setItem() { throw new Error('denied'); } };
		expect(hasAgreed(broken)).toBe(false);
		expect(() => recordAgreement(broken)).not.toThrow();
	});
});
