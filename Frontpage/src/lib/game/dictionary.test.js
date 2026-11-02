import { describe, expect, it, vi } from 'vitest';
import { formatMean, lookupWord, normalizeQuery, parseMean, themeKind } from './dictionary.js';

describe('dictionary', () => {
	it('normalizes the query and picks the language', () => {
		expect(normalizeQuery('  사과! ')).toEqual({ q: '  사과 ', lang: 'ko' });
		expect(normalizeQuery('ApPle?')).toEqual({ q: 'apple', lang: 'en' });
		expect(normalizeQuery('###').q).toBe('');
	});
	it('looks words up, repairs stray backslashes and maps failures to 404', async () => {
		const ok = vi.fn().mockResolvedValue({ text: async () => '{"word":"a","mean":"x\\ y"}' });
		const found = await lookupWord('apple', ok);
		expect(found.word).toBe('a');
		expect(found.mean).toBe('x\\ y'); // the lone backslash survives as a character
		expect(ok).toHaveBeenCalledWith('/dictionary/en/apple');
		const nl = await lookupWord('apple', vi.fn().mockResolvedValue({ text: async () => '{"word":"a","mean":"x\ny"}' }));
		expect(nl.mean).toBe('x\ny'); // raw line breaks inside the string are tolerated
		expect(await lookupWord('!!', ok)).toEqual({ error: 404 });
		expect(await lookupWord('apple', vi.fn().mockRejectedValue(new Error('x')))).toEqual({ error: 404 });
		expect(await lookupWord('apple', vi.fn().mockResolvedValue({ text: async () => 'not json' }))).toEqual({ error: 404 });
	});
	it('formats sub/superscripts and equations', () => {
		expect(formatMean('H*2*O and x**2**')).toEqual([
			{ kind: 'text', text: 'H', equ: false },
			{ kind: 'sub', text: '2', equ: false },
			{ kind: 'text', text: 'O and x', equ: false },
			{ kind: 'sup', text: '2', equ: false }
		]);
		const eq = formatMean('a $$x^{2}\\geq 1$$ b');
		expect(eq.map((s) => s.text).join('')).toBe('a x2≥ 1 b');
		expect(eq.find((s) => s.kind === 'sup')).toMatchObject({ text: '2', equ: true });
	});
	it('classifies theme badges', () => {
		expect(themeKind('12')).toBe('topic');
		expect(themeKind('e5')).toBe('topic');
		expect(themeKind('ODW')).toBe('special');
		expect(themeKind('FRU')).toBe('injeong');
	});
	it('returns plain text for unstructured meanings', () => {
		expect(parseMean('과일')).toEqual({ plain: '과일', senses: [] });
		expect(parseMean(undefined)).toEqual({ plain: '', senses: [] });
	});
	it('parses numbered senses and consumes themes in order', () => {
		const label = (c) => ({ 1: '일반', FRU: '과일' })[c];
		const r = parseMean('＂1＂［1］（1）가（2）나［2］다＂2＂라', '1,FRU,1,1', label);
		expect(r.senses.length).toBe(2);
		expect(r.senses[0].n).toBe(1); // >1 non-flag themes: senses are numbered
		const g0 = r.senses[0].groups[0];
		expect(g0.n).toBe(1);
		expect(g0.items.map((i) => [i.n, i.text, i.theme?.label])).toEqual([[1, '가', '일반'], [2, '나', '과일']]);
		expect(r.senses[0].groups[1].items[0]).toMatchObject({ n: null, text: '다' });
	});
	it('stops at a rule flag theme', () => {
		const r = parseMean('＂1＂첫째＂2＂둘째', '1,HBW', () => 'x');
		expect(r.senses.length).toBe(1);
		expect(r.senses[0].n).toBeNull(); // only one non-flag theme
	});
});
