import { describe, expect, it } from 'vitest';
import { BASE_MAX_LEVEL, EXP, MAX_LEVEL, getLevel, requiredScore } from './level.js';

// The numbers are the game server's (`KKuTuLevel.ts`, checked against its own table).
describe('level curve', () => {
	it('keeps every level up to 720 as it always was', () => {
		expect(EXP[718]).toBe(150_380_154); // experience that reaches level 720
		expect(getLevel(120_820_318)).toBe(672);
		expect(getLevel(103_653_445)).toBe(641);
		expect(getLevel(55_157_666)).toBe(535);
		expect(getLevel(53_085_808)).toBe(529);
	});
	it('goes on to 800 with a ramp of 1 + 0.2 * (level - 720) on what a level costs', () => {
		expect([BASE_MAX_LEVEL, MAX_LEVEL]).toEqual([720, 800]);
		expect([requiredScore(720), requiredScore(721), requiredScore(722)]).toEqual([1_648_811, 726_336, 849_660]); // 720 keeps the 45th-multiple spike; 721 is x1.2, 722 x1.4
		expect([EXP[738], EXP[758], EXP[798]]).toEqual([190_563_655, 288_415_237, 696_366_390]); // reach 740 / 760 / 800
		expect(EXP[799]).toBe(Infinity);
		expect(getLevel(1e12)).toBe(800);
	});
	it('puts today\'s top four at 769, 747, 746 and 746', () => {
		expect([364_383_701, 219_192_146, 217_533_731, 214_469_142].map(getLevel)).toEqual([769, 747, 746, 746]);
	});
});
