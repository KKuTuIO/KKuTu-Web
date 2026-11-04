import { describe, it, expect } from 'vitest';
import { nickProblem, nickState, FIXED_COST } from './nick.js';

describe('nickProblem', () => {
	it('checks length and characters', () => {
		expect(nickProblem('a')).toBe('length');
		expect(nickProblem('가'.repeat(16))).toBe('length');
		expect(nickProblem('끄투 리오')).toBe(null);
		expect(nickProblem('-abc')).toBe('chars');
		expect(nickProblem('ab!')).toBe('chars');
		expect(nickProblem('  끄투  ')).toBe(null);
	});
});

const base = { value: '새별명', base: '옛별명', fixed: false, meFixed: false, member: false, money: 50 };

describe('nickState', () => {
	it('lets a plain change through for free', () => {
		expect(nickState(base)).toMatchObject({ canSubmit: true, cost: 0, poor: false });
	});
	it('charges 100 ping to fix it, and refuses when short', () => {
		const s = nickState({ ...base, fixed: true });
		expect(s.cost).toBe(FIXED_COST);
		expect(s.poor).toBe(true);
		expect(s.canSubmit).toBe(false);
		expect(nickState({ ...base, fixed: true, money: 100 }).canSubmit).toBe(true);
	});
	it('is free for members', () => {
		expect(nickState({ ...base, fixed: true, member: true, money: 0 })).toMatchObject({ cost: 0, canSubmit: true });
	});
	it('says nothing changes for the same name, unless it is being fixed now', () => {
		expect(nickState({ ...base, value: '옛별명' }).unchanged).toBe(true);
		expect(nickState({ ...base, value: '옛별명', fixed: true, money: 100 })).toMatchObject({ unchanged: false, canSubmit: true });
		expect(nickState({ ...base, value: '옛별명', fixed: true, meFixed: true, money: 100 }).unchanged).toBe(true);
	});
	it('waits out the 7 days', () => {
		expect(nickState({ ...base, nextChangeAt: 2000, now: 1000 })).toMatchObject({ cooling: true, canSubmit: false });
		expect(nickState({ ...base, nextChangeAt: 500, now: 1000 }).cooling).toBe(false);
	});
	it('rejects a bad name', () => {
		expect(nickState({ ...base, value: 'x' })).toMatchObject({ problem: 'length', canSubmit: false });
	});
});
