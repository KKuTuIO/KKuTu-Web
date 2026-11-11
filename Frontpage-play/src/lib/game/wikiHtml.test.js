import { describe, expect, it } from 'vitest';
import { sanitizeWikiHtml } from './wikiHtml.js';

const O = 'https://kkutu.wiki';
const clean = (h) => {
	const d = document.createElement('div');
	d.innerHTML = sanitizeWikiHtml(h, O);
	return d;
};

describe('sanitizeWikiHtml', () => {
	it('removes scripts, frames, forms and event handlers', () => {
		const d = clean('<p onclick="x()">a</p><script>alert(1)</script><iframe src="https://e"></iframe><form><input></form><img src="https://i/a.png" onerror="x()">');
		expect(d.querySelector('script,iframe,form,input')).toBeNull();
		expect(d.querySelector('p').hasAttribute('onclick')).toBe(false);
		expect(d.querySelector('img').hasAttribute('onerror')).toBe(false);
	});
	it('drops javascript: links and non-https images', () => {
		const d = clean('<a href="javascript:alert(1)">x</a><img src="http://i/a.png"><img src="data:image/png;base64,AAAA">');
		expect(d.querySelector('a').hasAttribute('href')).toBe(false);
		expect(d.querySelectorAll('img').length).toBe(0);
	});
	it('keeps harmless inline styles and drops the ones that escape the flow', () => {
		const d = clean('<table style="float:right;border:3px solid #5741a6;position:fixed;top:0;z-index:9"><tr><td style="background:url(https://x/y.png);color:red">c</td></tr></table>');
		expect(d.querySelector('table').getAttribute('style')).toBe('float:right;border:3px solid #5741a6;top:0');
		expect(d.querySelector('td').getAttribute('style')).toBe('color:red');
	});
	it('marks article links for in-place opening and sends the rest to a new tab', () => {
		const d = clean('<a href="/w/%EA%B0%80%EC%9D%84_%ED%8C%8C%EC%9D%B4">a</a><a href="/w/Special:Search">s</a><a class="new" href="/w/%EC%97%86%EC%9D%8C">r</a><a href="https://example.com/">e</a><a href="#cite_note-1">n</a>');
		const [a, s, r, e, n] = d.querySelectorAll('a');
		expect(a.dataset.wiki).toBe('가을 파이');
		expect(a.hasAttribute('target')).toBe(false);
		for (const x of [s, r, e]) {
			expect(x.hasAttribute('data-wiki')).toBe(false);
			expect(x.getAttribute('target')).toBe('_blank');
			expect(x.getAttribute('rel')).toContain('noopener');
		}
		expect(n.getAttribute('href')).toBe('#wk-cite_note-1');
	});
	it('drops the link around a picture and sends namespaced pages to the wiki', () => {
		const d = clean('<a class="mw-file-description" href="/w/%ED%8C%8C%EC%9D%BC:a.png"><img src="https://i/a.png"></a><a href="/w/%EB%B6%84%EB%A5%98:%EC%9D%8C%EC%8B%9D">c</a>');
		expect(d.querySelector('a.mw-file-description')).toBeNull();
		expect(d.querySelector('img')).not.toBeNull();
		expect(d.querySelector('a').hasAttribute('data-wiki')).toBe(false);
	});
	it('prefixes element ids so they cannot collide with the game page', () => {
		const d = clean('<h2 id="content">a</h2><a href="#content">x</a><a name="y">z</a>');
		expect(d.querySelector('h2').id).toBe('wk-content');
		expect(d.querySelector('a[name]')).toBeNull();
	});
	it('makes relative image addresses absolute and lazy', () => {
		const img = clean('<img src="/images/a.png" srcset="/images/a.png 1.5x, /images/b.png 2x">').querySelector('img');
		expect(img.getAttribute('src')).toBe('https://kkutu.wiki/images/a.png');
		expect(img.getAttribute('srcset')).toBe('https://kkutu.wiki/images/a.png 1.5x, https://kkutu.wiki/images/b.png 2x');
		expect(img.getAttribute('loading')).toBe('lazy');
	});
});
