import { describe, expect, it } from 'vitest';
import { STAFF_LEVEL_BADGE, staffIdOf, staffOf } from './staffTag.js';

describe('staff badges', () => {
	it('finds the team of a worn staff badge, under the new client\'s key and the legacy one', () => {
		for (const team of ['gm', 'mod', 'word', 'minwon', 'dev', 'media', 'plan']) {
			expect(staffIdOf({ BDG: `b1_${team}` })).toBe(team);
			expect(staffIdOf({ BDG1: `b1_${team}` })).toBe(team);
			expect(staffOf(team)?.id).toBe(team);
		}
	});
	it('gives nothing for anything else', () => {
		for (const equip of [{ BDG: 'b1_seek' }, { BDG: '2023_attendanceE9' }, { NIK: 'gm' }, { BDG: 7 }, {}, null, undefined]) expect(staffIdOf(equip)).toBeNull();
	});
	it('uses one level badge for every team', () => {
		expect(STAFF_LEVEL_BADGE).toBe('gm');
	});
});
