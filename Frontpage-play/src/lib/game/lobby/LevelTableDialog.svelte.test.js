import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render } from '@testing-library/svelte';
import LevelTableDialog from './LevelTableDialog.svelte';
import { EXP } from '../level.js';

const table = { 5: [80, [['2023_attendanceE9', 1]], []], 10: [80, [['dictPage', 1]], []], 35: [100, [], [['bonusMNY_5', 1]]] };

describe('LevelTableDialog', () => {
	afterEach(cleanup);
	it('lists every level with its cumulative experience and the highlighted current level', async () => {
		render(LevelTableDialog, { open: true, level: 5, table, nameOf: (k) => `name:${k}` });
		const rows = document.querySelectorAll('[data-table] tbody tr');
		expect(rows).toHaveLength(720);
		expect(rows[0].textContent).toContain('0');
		expect(rows[4].className).toContain('accent-soft');
		expect(rows[4].textContent).toContain(EXP[3].toLocaleString('en-US'));
		expect(rows[4].querySelector('[aria-label="name:2023_attendanceE9"]')).toBeTruthy(); // level 5 gift
	});
	it('says it is loading until the server has sent the table', () => {
		render(LevelTableDialog, { open: true, level: 5, table: null });
		expect(document.querySelectorAll('[data-table] tbody tr')).toHaveLength(0);
		expect(document.body.textContent).toContain('불러오는 중');
	});
	it('marks timed gifts as coming by mail', () => {
		render(LevelTableDialog, { open: true, level: 35, table });
		expect(document.querySelector('[data-row="35"] .material-symbols-outlined')?.textContent).toBe('mail');
	});
});
