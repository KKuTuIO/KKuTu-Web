import { beforeEach, describe, expect, it, vi } from 'vitest';
import { _reset, articleUrl, editUrl, loadArticle, wikiIntro, wikiSummary } from './wiki.js';

const q = (pages, extra = {}) => vi.fn().mockResolvedValue({ ok: true, json: async () => ({ query: { pages, ...extra } }) });

describe('wiki', () => {
	const store = new Map();
	beforeEach(() => {
		_reset();
		store.clear();
		vi.stubGlobal('localStorage', { getItem: (k) => store.get(k) ?? null, setItem: (k, v) => store.set(k, String(v)) });
	});
	it('builds article and edit urls', () => {
		expect(articleUrl('사과 파이', 'https://x')).toBe('https://x/w/%EC%82%AC%EA%B3%BC_%ED%8C%8C%EC%9D%B4');
		expect(editUrl('가을감자', 'https://kkutu.wiki')).toBe('https://kkutu.wiki/index.php?title=%EA%B0%80%EC%9D%84%EA%B0%90%EC%9E%90&action=edit');
	});
	it('asks the wiki once for titles asked in the same tick, resolving redirects, and caches the answers', async () => {
		localStorage.setItem('kkutu_wiki_origin', 'https://b/');
		const f = q([{ title: '가을', thumbnail: { source: 'https://i/가을.png' } }, { title: '나', missing: true }], { redirects: [{ from: '가을날', to: '가을' }] });
		const [a, b, c] = await Promise.all([wikiSummary('가을', f), wikiSummary('나', f), wikiSummary('가을날', f)]);
		expect(f).toHaveBeenCalledTimes(1);
		const url = new URL(f.mock.calls[0][0]);
		expect(url.origin + url.pathname).toBe('https://b/api.php');
		expect(url.searchParams.get('titles')).toBe('가을|나|가을날');
		expect(url.searchParams.get('origin')).toBe('*');
		expect(a).toEqual({ title: '가을', exists: true, thumb: 'https://i/가을.png' });
		expect(b).toEqual({ title: '나', exists: false, thumb: null });
		expect(c.title).toBe('가을');
		await wikiSummary('가을', f);
		expect(f).toHaveBeenCalledTimes(1);
	});
	it('resolves null on failure and tries again next time', async () => {
		expect(await wikiSummary('가', vi.fn().mockRejectedValue(new Error('offline')))).toBeNull();
		await Promise.resolve();
		expect((await wikiSummary('가', q([{ title: '가' }]))).exists).toBe(true);
	});
	it('loads an article, and answers null for a missing one', async () => {
		const ok = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ parse: { title: '가을', text: '<p>x</p>', redirects: [{ from: '가을날', to: '가을' }] } }) });
		expect(await loadArticle('가을날', ok)).toEqual({ title: '가을', html: '<p>x</p>', sections: [], redirectedFrom: '가을날' });
		const none = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ error: { code: 'missingtitle', info: 'x' } }) });
		expect(await loadArticle('없음', none)).toBeNull();
		await expect(loadArticle('x', vi.fn().mockRejectedValue(new Error('offline')))).rejects.toThrow();
	});
	it('keeps the headings of an article as its table of contents, without those of templates', async () => {
		const sections = [{ index: '1', number: '1', toclevel: 1, line: '<b>개요</b>', anchor: '개요' }, { index: 'T-1', number: '', toclevel: 1, line: 'x', anchor: 'x' }, { index: '2', number: '1.1', toclevel: 2, line: '특징', anchor: '특징' }];
		const f = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ parse: { title: 'A', text: '', sections } }) });
		expect((await loadArticle('A', f)).sections).toEqual([{ number: '1', text: '개요', level: 1, anchor: '개요' }, { number: '1.1', text: '특징', level: 2, anchor: '특징' }]);
	});
	it('summarises the start of an article, skipping heading lines', async () => {
		const page = { title: '가을', extract: '\n== 개요 ==\n\n가을은 계절이다. 여름 다음이다. 겨울 전이다.', thumbnail: { source: 'https://i/a.png' } };
		const f = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ query: { pages: [page] } }) });
		expect(await wikiIntro('가을', 14, f)).toEqual({ title: '가을', exists: true, extract: '가을은 계절이다. 여름…', thumb: 'https://i/a.png' });
		const none = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ query: { pages: [{ title: '없음', missing: true }] } }) });
		expect((await wikiIntro('없음', 100, none)).exists).toBe(false);
		expect(await wikiIntro('x', 100, vi.fn().mockRejectedValue(new Error('offline')))).toBeNull();
	});
});
