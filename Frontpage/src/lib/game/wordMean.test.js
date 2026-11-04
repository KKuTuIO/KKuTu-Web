import { describe, expect, it } from 'vitest';
import { formMean, processWord } from './wordMean.js';

const labels = { 'word.theme.320': '지명', 'word.theme.ODW': '우리말샘', 'word.theme.ABC': '전문어' };

describe('processWord (the legacy meaning layout)', () => {
	it('keeps a meaning without numbering as it is', () => {
		expect(processWord('그냥 뜻', '', labels)).toEqual({ plain: '그냥 뜻' });
		expect(processWord('', '', labels)).toEqual({ plain: '' });
	});
	it('splits the three numbering levels and puts each theme on its meaning', () => {
		const m = processWord('＂1＂［1］（1） 첫째 （2） 둘째 ［2］ 셋째 ＂2＂ 넷째', '320,320,ABC,ODW', labels);
		expect(m.numbered).toBe(true);
		expect(m.groups.map((g) => g.head)).toEqual([1, 2]);
		const first = m.groups[0];
		expect(first.subs.map((s) => s.head)).toEqual([1, 2]);
		expect(first.subs[0].items.map((i) => [i.head, i.theme?.name, i.theme?.kind, i.html.trim()])).toEqual([
			[1, '지명', 'plain', '첫째'],
			[2, '지명', 'plain', '둘째']
		]);
		expect(first.subs[1].items[0]).toMatchObject({ head: null, theme: { name: '전문어', kind: 'injeong' } });
		expect(m.groups[1].subs[0].items[0].theme).toMatchObject({ name: '우리말샘', kind: 'special' });
	});
	it('drops a meaning that belongs to a flag theme (한방, 안전, 매너)', () => {
		const m = processWord('＂1＂ 보이는 뜻 ＂2＂ 숨는 뜻', '320,HBW', labels);
		expect(m.groups.map((g) => g.index)).toEqual([0]);
	});
	it('numbers the groups only when there is more than one real theme', () => {
		expect(processWord('＂1＂ 뜻', '320', labels).numbered).toBe(false);
	});
});

describe('formMean', () => {
	it('turns the marks into sub/superscripts and escapes everything else', () => {
		expect(formMean('H*2*O **m**')).toBe('H<sub>2</sub>O <sup>m</sup>');
		expect(formMean('$$x^{2}$$')).toBe('<equ>x<sup>2</sup></equ>');
		expect(formMean('<b>굵게</b>')).toBe('&lt;b&gt;굵게&lt;/b&gt;');
	});
});
