import { describe, it, expect } from 'vitest';
import { searchPlayers } from './friendSearch.js';

const reply = (status, body) => async () => ({ status, ok: status >= 200 && status < 300, json: async () => body });

describe('searchPlayers', () => {
	it('does not ask for fewer than two characters', async () => {
		let asked = false;
		const r = await searchPlayers(' a ', async () => ((asked = true), reply(200, [])()));
		expect(r).toEqual({ ok: true, users: [] });
		expect(asked).toBe(false);
	});
	it('turns the rows into players (name and tag split, level from the score)', async () => {
		let url = '';
		const r = await searchPlayers('끄투#12', async (u) => ((url = u), reply(200, [{ id: 'u1', nickname: '끄투리오#12345', score: 480000, equip: { Mhead: 'hat' } }])()));
		expect(url).toBe('/api/play/users/search?q=%EB%81%84%ED%88%AC%2312');
		expect(r.ok).toBe(true);
		expect(r.users[0]).toMatchObject({ id: 'u1', name: '끄투리오', tag: '12345', fixed: false, equip: { Mhead: 'hat' } });
		expect(r.users[0].lv).toBeGreaterThan(1);
	});
	it('says why when it is refused', async () => {
		expect(await searchPlayers('abc', reply(401, {}))).toEqual({ ok: false, reason: 'login' });
		expect(await searchPlayers('abc', reply(429, {}))).toEqual({ ok: false, reason: 'rate' });
		expect(await searchPlayers('abc', reply(500, {}))).toEqual({ ok: false, reason: 'error' });
		expect(await searchPlayers('abc', async () => { throw new Error('offline'); })).toEqual({ ok: false, reason: 'error' });
	});
});
