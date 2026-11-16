import { describe, expect, it } from 'vitest';
import { CIRCLED, clock, formulaHtml, mathHtml } from './mathText.js';

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
	it('gives nothing for nothing, and writes minutes and seconds', () => {
		expect(mathHtml('')).toBe('');
		expect(clock(0)).toBe('0:00');
		expect(clock(899_100)).toBe('15:00');
		expect(clock(61_000)).toBe('1:01');
		expect(CIRCLED).toHaveLength(5);
	});
});
