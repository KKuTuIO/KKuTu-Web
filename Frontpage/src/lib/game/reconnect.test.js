import { describe, expect, it } from 'vitest';
import { MAX_ATTEMPTS, retryDelay, shouldReconnect } from './reconnect.js';

describe('shouldReconnect', () => {
	it('retries dropped networks and restarting servers', () => {
		for (const code of [1006, 1001, 1011, 1012, 1013, 1015]) expect(shouldReconnect(code, undefined)).toBe(true);
	});
	it('does not retry a close the server meant (normal close, no status, custom codes)', () => {
		for (const code of [1000, 1005, 4000, 4401, undefined]) expect(shouldReconnect(code, undefined)).toBe(false);
	});
	it('does not retry when the server refused the guest first', () => {
		for (const err of [449, 402, 479, 457, 440]) expect(shouldReconnect(1006, err)).toBe(false);
		expect(shouldReconnect(1006, 400)).toBe(true); // an ordinary request error says nothing about the connection
	});
});

describe('retryDelay', () => {
	it('backs off 1, 2, 4, 8, 15, 30 s and stays at 30 s', () => {
		const secs = [0, 1, 2, 3, 4, 5, 6, 11].map((n) => retryDelay(n, 0.5) / 1000);
		expect(secs).toEqual([1, 2, 4, 8, 15, 30, 30, 30]);
	});
	it('spreads +-20 %', () => {
		expect(retryDelay(3, 0)).toBe(6400);
		expect(retryDelay(3, 1)).toBe(9600);
	});
	it('gives up after a bounded number of attempts', () => {
		expect(MAX_ATTEMPTS).toBeGreaterThan(5);
		const total = Array.from({ length: MAX_ATTEMPTS }, (_, i) => retryDelay(i, 0.5)).reduce((a, b) => a + b, 0);
		expect(total).toBeLessThan(10 * 60_000);
	});
});
