import { describe, expect, it, vi } from 'vitest';
import { displayDelta, equipOf, fetchRanking, rankingUrl, splitName } from './ranking.js';

describe('ranking', () => {
	it('builds the same URLs as the homepage', () => {
		expect(rankingUrl({ page: 3 })).toBe('/ranking?page=3');
		expect(rankingUrl({})).toBe('/ranking?page=0');
		expect(rankingUrl({ me: true, page: 2 })).toBe('/ranking?me=true');
		expect(rankingUrl({ query: ' a b#1 ' })).toBe('/ranking?query=a%20b%231');
		expect(rankingUrl({ query: '   ', page: 1 })).toBe('/ranking?page=1');
	});
	it('maps the response and rejects on HTTP errors', async () => {
		const ok = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: { page: 2, data: [{ id: 'a', rank: 30, score: 5 }] } }) });
		expect(await fetchRanking({ page: 2 }, ok)).toEqual({ page: 2, rows: [{ id: 'a', rank: 30, score: 5 }] });
		await expect(fetchRanking({}, vi.fn().mockResolvedValue({ ok: false, status: 403 }))).rejects.toThrow('403');
	});
	it('splits nickname and tag, and formats rank changes', () => {
		expect(splitName('곰#1234')).toEqual({ name: '곰', tag: '#1234' });
		expect(splitName('곰')).toEqual({ name: '곰', tag: '' });
		expect(splitName(null)).toEqual({ name: '', tag: '' });
		expect(displayDelta('+3')).toEqual({ text: '↑3', dir: 'up' });
		expect(displayDelta('-2')).toEqual({ text: '↓2', dir: 'down' });
		expect(displayDelta('0')).toEqual({ text: '-', dir: 'none' });
		expect(displayDelta(undefined)).toEqual({ text: '*', dir: 'none' });
	});
	it('reads equip from an object or a JSON string', () => {
		expect(equipOf({ equip: { Mhead: 'x' } })).toEqual({ Mhead: 'x' });
		expect(equipOf({ equip: '{"Mhead":"y"}' })).toEqual({ Mhead: 'y' });
		expect(equipOf({ equip: '{bad' })).toEqual({});
		expect(equipOf(null)).toEqual({});
	});
});
