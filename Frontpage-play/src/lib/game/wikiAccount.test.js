import { describe, expect, it, vi } from 'vitest';
import { isWikiAddress, loadBinding, requestChallenge, unbind, verifyChallenge } from './wikiAccount.js';

const reply = (body, ok = true, status = 200) => ({ ok, status, json: async () => body });

describe('wikiAccount', () => {
	it('reads the binding with a plain GET', async () => {
		const f = vi.fn().mockResolvedValue(reply({ bound: false, contributions: { settled: 0, pending: 0 } }));
		expect((await loadBinding(f)).bound).toBe(false);
		expect(f).toHaveBeenCalledTimes(1);
		expect(f.mock.calls[0][0]).toBe('/api/account/wiki');
		expect(f.mock.calls[0][1].method).toBe('GET');
	});
	it('fetches the CSRF cookie before a change and sends the trimmed username', async () => {
		vi.stubGlobal('document', { cookie: 'a=1; XSRF-TOKEN=tok%3D1' });
		const f = vi.fn().mockResolvedValueOnce(reply({})).mockResolvedValueOnce(reply({ code: 'KK-1', expires_at: 'x', page: 'User:A', wiki_url: 'https://w' }));
		const r = await requestChallenge('  가을 ', f);
		expect(r.code).toBe('KK-1');
		expect(f.mock.calls[0][0]).toBe('/api/account/csrf');
		const [url, init] = f.mock.calls[1];
		expect(url).toBe('/api/account/wiki/challenge');
		expect(init.method).toBe('POST');
		expect(init.headers['X-XSRF-TOKEN']).toBe('tok=1');
		expect(JSON.parse(init.body)).toEqual({ username: '가을' });
		vi.unstubAllGlobals();
	});
	it('turns a failure into an error with the server code and message', async () => {
		vi.stubGlobal('document', { cookie: '' });
		const f = vi.fn().mockResolvedValueOnce(reply({})).mockResolvedValueOnce(reply({ error: 'challenge_not_found', error_description: '코드를 찾지 못했습니다.' }, false, 422));
		await expect(verifyChallenge(f)).rejects.toMatchObject({ code: 'challenge_not_found', message: '코드를 찾지 못했습니다.', status: 422 });
		vi.unstubAllGlobals();
	});
	it('reports a network failure as its own code', async () => {
		vi.stubGlobal('document', { cookie: '' });
		const f = vi.fn().mockResolvedValueOnce(reply({})).mockRejectedValueOnce(new Error('offline'));
		await expect(unbind(f)).rejects.toMatchObject({ code: 'network' });
		vi.unstubAllGlobals();
	});
	it('tells the wiki own addresses from others', () => {
		expect(isWikiAddress('https://kkutu.wiki/w/User:A', 'https://kkutu.wiki')).toBe(true);
		expect(isWikiAddress('https://example.com/', 'https://kkutu.wiki')).toBe(false);
		expect(isWikiAddress('not a url', 'https://kkutu.wiki')).toBe(false);
	});
});
