import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import GameOptions from './GameOptions.svelte';

// Shape of the real /api/play/{n}/ticket payload (trimmed).
const net = {
	meta: {
		modes: ['EKT', 'ESH', 'KKT'],
		rules: { KKT: { lang: 'ko', opts: ['man', 'saf', 'inj', 'mis', 'rms', 'etq'], time: 1 }, EKT: { lang: 'en', opts: [], time: 1 } },
		optionDefs: { man: { name: 'Manner' }, saf: { name: 'Safe' }, mis: { name: 'Mission' }, rms: { name: 'RandMission', req: 'mis' }, etq: { name: 'Etiquette', noguest: true } },
		labels: { 'game.mode.KKT.name': '한국어 쿵쿵따', 'game.option.Manner.name': '매너', 'game.option.Safe.name': '안전' }
	}
};

afterEach(cleanup);

describe('GameOptions', () => {
	it('shows the mode picker and that mode\'s rule checkboxes', async () => {
		const opts = {};
		render(GameOptions, { net: { ...net, guest: false, meta: { ...net.meta, rules: { ...net.meta.rules, KKT: { ...net.meta.rules.KKT, opts: ['man', 'saf'] } } } }, mode: 2, opts });
		expect(screen.getByRole('combobox', { name: '게임 모드' }).textContent).toContain('쿵쿵따');
		const boxes = screen.getAllByRole('checkbox');
		expect(boxes.map((b) => b.textContent.trim())).toEqual(['매너', '안전']);
		await fireEvent.click(boxes[0]);
		expect(boxes[0].getAttribute('aria-checked')).toBe('true');
		expect(opts.manner).toBe(true);
	});
	it('hides member-only options for guests and gates dependent ones', async () => {
		const guestNet = { ...net, guest: true, meta: { ...net.meta, labels: { ...net.meta.labels, 'game.option.Mission.name': '미션', 'game.option.RandMission.name': '랜덤미션' } } };
		const opts = $state({});
		render(GameOptions, { net: guestNet, mode: 2, opts });
		const names = screen.getAllByRole('checkbox').map((b) => b.textContent.trim());
		expect(names).toEqual(['매너', '안전', '미션', '랜덤미션']); // no member-only 에티켓, no 단어장 here
		const rand = screen.getByRole('checkbox', { name: '랜덤미션' });
		expect(rand.disabled).toBe(false); // nothing is disabled: the boxes follow each other instead
		await fireEvent.click(rand);
		expect(opts.randmission).toBe(true);
		expect(opts.mission).toBe(true); // switching a rule on also switches on what it needs
		await fireEvent.click(screen.getByRole('checkbox', { name: '미션' }));
		expect(opts.mission).toBe(false);
		expect(opts.randmission).toBe(false); // and switching that off takes the dependent one with it
	});
	it('options of one group exclude each other', async () => {
		const grouped = { ...net, guest: false, meta: { ...net.meta, optionDefs: { ...net.meta.optionDefs, man: { name: 'Manner', group: 'M' }, saf: { name: 'Safe', group: 'M' } } } };
		const opts = $state({});
		render(GameOptions, { net: grouped, mode: 2, opts });
		await fireEvent.click(screen.getByRole('checkbox', { name: '매너' }));
		await fireEvent.click(screen.getByRole('checkbox', { name: '안전' }));
		expect(opts.safe).toBe(true);
		expect(opts.manner).toBe(false);
	});
	describe('단어장', () => {
		const themes = { ko: ['a', 'b', 'c'], en: ['x'] };
		const base = {
			...net,
			guest: false,
			themePresets: [{ id: 'p1', name: '쉬운 주제', lang: 'ko', themes: ['a', 'c', 'zzz'] }],
			meta: {
				...net.meta,
				pickThemes: themes,
				rules: { ...net.meta.rules, KKT: { lang: 'ko', opts: ['ext', 'inj'], time: 1 }, JQ: { lang: 'ko', opts: ['ijp'], time: 1 } },
				modes: [...net.meta.modes, 'JQ'],
				optionDefs: { ...net.meta.optionDefs, ext: { name: 'Injeong' }, inp: { name: 'InjeongPick', req: 'ext' } },
				labels: { ...net.meta.labels, 'game.option.Injeong.name': '인정', 'game.option.InjeongPick.name': '단어장', 'word.theme.a': '가', 'word.theme.b': '나', 'word.theme.c': '다' }
			}
		};
		it('is an optional checkbox (rule code "inj" or "inp") that needs 인정, and only then asks for themes', async () => {
			const opts = $state({});
			const injpick = $state([]);
			render(GameOptions, { net: base, mode: 2, opts, injpick, withThemes: true });
			const pick = screen.getByRole('checkbox', { name: '단어장' });
			expect(pick.disabled).toBe(false);
			expect(screen.queryByRole('button', { name: /단어장/ })).toBeNull();
			await fireEvent.click(pick); // asking for the 단어장 switches 인정 on as well
			expect(opts.injeong).toBe(true);
			expect(screen.getByRole('button', { name: /단어장/ })).toBeTruthy();
		});
		it('modes that always need themes show the picker, and a preset fills it', async () => {
			const injpick = $state([]);
			const opts = $state({});
			render(GameOptions, { net: base, mode: 3, opts, injpick, withThemes: true });
			expect(screen.queryAllByRole('checkbox')).toEqual([]);
			await fireEvent.click(screen.getByRole('combobox', { name: '주제 세트' }));
			await fireEvent.click(screen.getByRole('option', { name: /쉬운 주제/, hidden: true }));
			expect(screen.getByRole('button', { name: /단어장:/ }).getAttribute('aria-label')).toBe('단어장: 가, 다'); // codes the server doesn't offer are dropped
			await fireEvent.click(screen.getByRole('button', { name: '전체 선택' }));
			expect(screen.getByRole('button', { name: /단어장:/ }).getAttribute('aria-label')).toBe('단어장: 가, 나, 다');
		});
	});
	it('shows no rule section for a mode without options', () => {
		render(GameOptions, { net, mode: 0 });
		expect(screen.queryAllByRole('checkbox')).toEqual([]);
	});
});
