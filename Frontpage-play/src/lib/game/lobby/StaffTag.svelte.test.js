import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render } from '@testing-library/svelte';
import NickName from './NickName.svelte';

describe('staff tag before a name', () => {
	afterEach(cleanup);
	it('shows the team of the worn staff badge', () => {
		render(NickName, { user: { id: 'u1', name: '운영자님', equip: { BDG1: 'b1_gm' } } });
		expect(document.body.textContent).toContain('운영자');
		expect(document.querySelector('.kk-staff')).toBeTruthy();
	});
	it('shows nothing for other players', () => {
		render(NickName, { user: { id: 'u2', name: '손님', equip: { BDG: '2023_attendanceE9', NIK: 'gm' } } });
		expect(document.querySelector('.kk-staff')).toBeNull();
	});
});
