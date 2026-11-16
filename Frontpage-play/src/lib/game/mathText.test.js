import { describe, expect, it } from 'vitest';
import { CIRCLED, clock, formulaHtml, mathHtml, mathHtmlPts, numberHtml } from './mathText.js';

describe('mathText', () => {
	it('draws the formulas and escapes the text around them', () => {
		const html = mathHtml('<b>두 수</b> $x^{2}+1$ 의 값은?\n\n$\\frac{1}{2}$');
		expect(html).toContain('&lt;b&gt;');
		expect(html).toContain('katex');
		expect(html).toContain('<br><br>');
		expect(html).not.toContain('<b>');
	});
	it('does not let a formula bring in markup or an error', () => {
		const html = mathHtml('$\\href{javascript:alert(1)}{x}$ $\\unknowncommand$');
		expect(html).not.toContain('javascript:');
		expect(html).toContain('katex');
	});
	it('draws a case-defined function and a limit', () => {
		expect(mathHtml('$f(x)=\\begin{cases}1 & (x<0)\\\\ 2 & (x\\ge 0)\\end{cases}$')).toContain('katex');
		expect(mathHtml('$\\displaystyle\\lim_{x\\to 1}\\dfrac{x^{2}-1}{x-1}$')).toContain('katex');
		expect(formulaHtml('-\\frac{5}{13}')).toContain('katex');
	});
	it('puts the lines that begin with > in a box, and a choice made of words is text, not a formula', () => {
		const html = mathHtml('옳은 것은?\n\n> ㄱ. $x^{2}$이다.\n> ㄴ. 참이다.');
		expect(html).toContain('<div class="mq">');
		expect(html).toContain('ㄱ. ');
		expect(html).not.toContain('&gt;');
		expect(html.match(/<div class="mq">/g)).toHaveLength(1);
		expect(html).toContain('<br>ㄴ.');
		expect(formulaHtml('ㄱ, ㄴ')).toBe('ㄱ, ㄴ');
		expect(formulaHtml('ㄱ, ㄴ')).not.toContain('katex');
		expect(formulaHtml('x^{2}')).toContain('katex');
	});
	it('sets the number of a question as an exam does: bold, in the LaTeX face', () => {
		const html = numberHtml(12);
		expect(html).toContain('katex');
		expect(html).toContain('mathbf');
		expect(html).toContain('12');
		expect(numberHtml('abc')).toContain('0');
	});
	it('sets the points right after the question, before the <보기> box when there is one', () => {
		const plain = mathHtmlPts('$x+1$의 값은?', 3);
		expect(plain).toContain('<span class="pts">[3점]</span>');
		expect(plain.indexOf('katex')).toBeLessThan(plain.indexOf('[3점]'));
		const withBox = mathHtmlPts('옳은 것은?\n\n> ㄱ. 참이다.\n> ㄴ. 거짓이다.', 4);
		expect(withBox.indexOf('[4점]')).toBeLessThan(withBox.indexOf('class="mq"'));
		expect(withBox.match(/\[4점\]/g)).toHaveLength(1);
		expect(mathHtmlPts('', 2)).toBe('');
	});
	it('gives nothing for nothing, and writes minutes and seconds', () => {
		expect(mathHtml('')).toBe('');
		expect(clock(0)).toBe('0:00');
		expect(clock(899_100)).toBe('15:00');
		expect(clock(61_000)).toBe('1:01');
		expect(CIRCLED).toHaveLength(5);
	});
});
