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
	it('lists 단어장 once as a disabled TBD entry, hides member-only options for guests and gates dependent ones', async () => {
		const guestNet = { ...net, guest: true, meta: { ...net.meta, labels: { ...net.meta.labels, 'game.option.Mission.name': '미션', 'game.option.RandMission.name': '랜덤미션', 'game.option.InjeongPick.name': '단어장' } } };
		let opts = $state({});
		render(GameOptions, { net: guestNet, mode: 2, opts });
		const names = screen.getAllByRole('checkbox').map((b) => b.textContent.trim());
		expect(names.map((n) => n.replace('준비 중', '').trim())).toEqual(['매너', '안전', '단어장', '미션', '랜덤미션']); // 단어장 once, no member-only 에티켓
		expect(screen.getByRole('checkbox', { name: /단어장/ }).disabled).toBe(true);
		const rand = screen.getByRole('checkbox', { name: '랜덤미션' });
		expect(rand.disabled).toBe(true);
		await fireEvent.click(screen.getByRole('checkbox', { name: '미션' }));
		expect(rand.disabled).toBe(false);
	});
	it('shows no rule section for a mode without options', () => {
		render(GameOptions, { net, mode: 0 });
		expect(screen.queryAllByRole('checkbox')).toEqual([]);
	});
});
