import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render } from '@testing-library/svelte';
import LevelIcon from './LevelIcon.svelte';
import { prefs } from '../prefs.svelte.js';

const badge = () => document.querySelector('[role="img"]');

describe('LevelIcon', () => {
	afterEach(() => {
		cleanup();
		prefs.reduceMotion = false;
	});
	it('draws levels up to 750 from the plain sheet', () => {
		render(LevelIcon, { level: 750, size: 24 });
		expect(badge().style.backgroundImage).toContain('newlv.png');
		expect(badge().classList.contains('lv-fx')).toBe(false);
	});
	it('animates levels 751 to 800 from their own row of the fx sheet', () => {
		render(LevelIcon, { level: 751, size: 24 });
		expect(badge().classList.contains('lv-fx')).toBe(true);
		expect(badge().style.backgroundImage).toContain('fxlv.png');
		expect(badge().getAttribute('style')).toMatch(/background-position-y:\s*calc\(var\(--s\) \* -0\)/);
		cleanup();
		render(LevelIcon, { level: 800, size: 30 });
		expect(badge().getAttribute('style')).toMatch(/--s:\s*30px/);
		expect(badge().getAttribute('style')).toMatch(/\* -49\)/);
	});
	it('stands still on the plain sheet when the player asked for less motion', () => {
		prefs.reduceMotion = true;
		render(LevelIcon, { level: 777, size: 24 });
		expect(badge().classList.contains('lv-fx')).toBe(false);
		expect(badge().style.backgroundImage).toContain('newlv.png');
	});
	it('takes the level from a score', () => {
		render(LevelIcon, { score: 364_383_701, size: 24 }); // 769 on the extended curve
		expect(badge().getAttribute('aria-label')).toContain('769');
		expect(badge().classList.contains('lv-fx')).toBe(true);
	});
	describe('staff badge', () => {
		it('swaps the number for the staff level badge while a staff badge is worn, under either badge key', () => {
			render(LevelIcon, { level: 300, equip: { BDG: 'b1_gm' }, size: 24 });
			expect(badge().style.background).toContain('gmlv.png');
			expect(badge().getAttribute('aria-label')).toBe('운영자');
			cleanup();
			render(LevelIcon, { level: 300, equip: { BDG1: 'b1_gm' }, size: 24 }); // what the legacy client writes
			expect(badge().style.background).toContain('gmlv.png');
		});
		it('is the same picture for every team, labelled with the team', () => {
			for (const [item, label] of [['b1_mod', '운영팀'], ['b1_word', '단어팀'], ['b1_minwon', '민원팀'], ['b1_dev', '개발팀'], ['b1_media', '미디어팀'], ['b1_plan', '기획팀']]) {
				render(LevelIcon, { level: 300, equip: { BDG: item }, size: 24 });
				expect(badge().style.background).toContain('gmlv.png');
				expect(badge().getAttribute('aria-label')).toBe(label);
				cleanup();
			}
		});
		it('beats the animated badge of levels 751 and up', () => {
			render(LevelIcon, { level: 780, equip: { BDG: 'b1_dev' }, size: 24 });
			expect(badge().classList.contains('lv-fx')).toBe(false);
			expect(badge().style.background).toContain('gmlv.png');
		});
		it('leaves every other badge and a missing equip alone', () => {
			for (const equip of [{ BDG: 'b1_seek' }, { BDG: '2023_attendanceE9' }, { NIK: 'gm' }, {}, null, undefined]) {
				render(LevelIcon, { level: 300, equip, size: 24 });
				expect(badge().style.backgroundImage).toContain('newlv.png');
				cleanup();
			}
		});
		it('is still hidden when the players are hidden', () => {
			prefs.hideUsers = true;
			render(LevelIcon, { level: 300, equip: { BDG: 'b1_gm' }, size: 24, maskable: true });
			expect(badge().style.background).toContain('hidelv.png');
			prefs.hideUsers = false;
		});
	});
});
