import { describe, expect, it } from 'vitest';
import { evaluate, format } from './calc.js';

describe('calc', () => {
	it('follows the order of operations and brackets', () => {
		expect(evaluate('2+3×4')).toBe(14);
		expect(evaluate('(2+3)×4')).toBe(20);
		expect(evaluate('20÷4÷5')).toBe(1);
		expect(evaluate('-3+5')).toBe(2);
		expect(evaluate('2×-3')).toBe(-6);
		expect(evaluate('1.5×4')).toBe(6);
	});
	it('says null for anything unfinished or impossible', () => {
		for (const bad of ['', '2+', '(2+3', '2+3)', '1÷0', '×3', '1..2+1', 'abc', '2++']) expect([bad, evaluate(bad)]).toEqual([bad, null]);
	});
	it('writes results without floating-point noise', () => {
		expect(format(evaluate('0.1+0.2'))).toBe('0.3');
		expect(format(evaluate('1÷3'))).toBe('0.333333333333');
		expect(format(evaluate('7×6'))).toBe('42');
	});
});
