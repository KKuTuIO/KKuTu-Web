import { describe, it, expect } from 'vitest';
import { parseInline, parseMarkdown, safeUrl } from './markdown.js';

const text = (v) => ({ t: 'text', v });

describe('safeUrl', () => {
	it('accepts http(s) only', () => {
		expect(safeUrl('https://kkutu.io/a?b=1')).toBe('https://kkutu.io/a?b=1');
		expect(safeUrl('http://example.com')).toBe('http://example.com/');
		expect(safeUrl('javascript:alert(1)')).toBe(null);
		expect(safeUrl('data:text/html,hi')).toBe(null);
		expect(safeUrl('//evil.example')).toBe(null);
		expect(safeUrl('https://a b')).toBe(null);
	});
});

describe('parseInline', () => {
	it('reads bold, italic, strike and code', () => {
		expect(parseInline('a **b** *c* ~~d~~ `e`')).toEqual([text('a '), { t: 'strong', c: [text('b')] }, text(' '), { t: 'em', c: [text('c')] }, text(' '), { t: 'del', c: [text('d')] }, text(' '), { t: 'code', v: 'e' }]);
	});
	it('nests marks', () => {
		expect(parseInline('**a *b* c**')).toEqual([{ t: 'strong', c: [text('a '), { t: 'em', c: [text('b')] }, text(' c')] }]);
	});
	it('leaves a lone star, snake_case and spaced marks alone', () => {
		expect(parseInline('2 * 3 * 4')).toEqual([text('2 * 3 * 4')]);
		expect(parseInline('snake_case_name')).toEqual([text('snake_case_name')]);
		expect(parseInline('** nope **')).toEqual([text('** nope **')]);
	});
	it('makes [text](url) a link and keeps a bad address as text', () => {
		expect(parseInline('[끄투](https://kkutu.io)')).toEqual([{ t: 'link', href: 'https://kkutu.io/', c: [text('끄투')] }]);
		expect(parseInline('[x](javascript:alert(1))')).toEqual([text('[x](javascript:alert(1))')]);
	});
	it('does not load images: an image is a link with its description', () => {
		expect(parseInline('![고양이](https://img.example/c.png)')).toEqual([{ t: 'link', href: 'https://img.example/c.png', c: [text('고양이')] }]);
	});
	it('links a bare address without its closing punctuation', () => {
		expect(parseInline('see https://kkutu.io/x.')).toEqual([text('see '), { t: 'link', href: 'https://kkutu.io/x', c: [text('https://kkutu.io/x')] }, text('.')]);
	});
	it('honours backslash escapes', () => {
		expect(parseInline('\\*not bold\\*')).toEqual([text('*not bold*')]);
	});
	it('keeps raw HTML as text', () => {
		expect(parseInline('<img src=x onerror=alert(1)>')).toEqual([text('<img src=x onerror=alert(1)>')]);
	});
	it('keeps a line break', () => {
		expect(parseInline('a\nb')).toEqual([text('a'), { t: 'br' }, text('b')]);
	});
});

describe('parseMarkdown', () => {
	it('splits paragraphs on blank lines', () => {
		expect(parseMarkdown('a\n\nb').map((b) => b.t)).toEqual(['p', 'p']);
	});
	it('reads headings, rules and lists', () => {
		const b = parseMarkdown('# 제목\n---\n- a\n- b\n\n1. x\n2. y');
		expect(b.map((x) => x.t)).toEqual(['h', 'hr', 'ul', 'ol']);
		expect(b[0].l).toBe(1);
		expect(b[2].items).toHaveLength(2);
		expect(b[3].items).toHaveLength(2);
	});
	it('reads quotes (with blocks inside) and code blocks', () => {
		const b = parseMarkdown('> 인용\n> **굵게**\n\n```\n**not bold**\n```');
		expect(b[0].t).toBe('quote');
		expect(b[0].c[0].t).toBe('p');
		expect(b[1]).toEqual({ t: 'pre', v: '**not bold**' });
	});
	it('ends a paragraph where a list starts', () => {
		expect(parseMarkdown('intro\n- a').map((b) => b.t)).toEqual(['p', 'ul']);
	});
	it('is empty for nothing', () => {
		expect(parseMarkdown('')).toEqual([]);
		expect(parseMarkdown(null)).toEqual([]);
	});
});
