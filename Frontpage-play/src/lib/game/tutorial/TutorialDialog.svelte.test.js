import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render } from '@testing-library/svelte';
import { tick } from 'svelte';
import TutorialDialog from './TutorialDialog.svelte';
import { setProgress } from './progress.svelte.js';
import { question, answer } from '../ui/confirm.svelte.js';
import { t } from '../i18n.svelte.js';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
afterEach(cleanup);
beforeEach(() => setProgress({}));

const store = (guest) => ({ shop: {}, net: { guest, meta: { moremiParts: [] } } });
const cards = () => [...document.querySelectorAll('dialog li')];
const settle = async () => {
	await tick();
	await new Promise((r) => setTimeout(r, 0));
};

describe('TutorialDialog', () => {
	it('has one card per lesson, the subtitle with the 핑 icon instead of (P), and the reward on the cards not cleared yet', async () => {
		render(TutorialDialog, { store: store(false), open: true, onstart() {}, oncommands() {} });
		await tick();
		expect(cards()).toHaveLength(3);
		const sub = document.querySelector('dialog p');
		expect(sub.textContent).not.toContain('(P)');
		expect(sub.textContent).toContain('100');
		expect(sub.querySelector('img')).toBeTruthy(); // the 핑 icon
		expect(sub.innerHTML).toContain('<br>'); // two lines
		for (const c of cards()) expect(c.textContent).toContain('+100');
	});
	it('stamps a cleared lesson, dims its picture, and no longer offers the reward on it', async () => {
		setProgress({ signedIn: true, done: ['chain'] });
		render(TutorialDialog, { store: store(false), open: true, onstart() {}, oncommands() {} });
		await tick();
		const [lobby, chain, room] = cards();
		expect(chain.querySelector('.kk-stamp')).toBeTruthy();
		expect(chain.querySelector('.opacity-30')).toBeTruthy();
		expect(chain.textContent).not.toContain('+100');
		expect(chain.textContent).toContain(t('play.tutorial.again'));
		for (const c of [lobby, room]) {
			expect(c.querySelector('.kk-stamp')).toBeNull();
			expect(c.textContent).toContain(t('play.tutorial.start'));
		}
	});
	it('starts a lesson at once for a signed-in player', async () => {
		const onstart = vi.fn();
		render(TutorialDialog, { store: store(false), open: true, onstart, oncommands() {} });
		await tick();
		await fireEvent.click(cards()[2].querySelector('button'));
		await settle();
		expect(onstart).toHaveBeenCalledWith('room');
		expect(question.current).toBe(null);
	});
	it('asks a guest first, naming the 100 핑 they would get by signing in, and starts only when they go on', async () => {
		const onstart = vi.fn();
		render(TutorialDialog, { store: store(true), open: true, onstart, oncommands() {} });
		await tick();
		await fireEvent.click(cards()[0].querySelector('button'));
		await settle();
		expect(onstart).not.toHaveBeenCalled();
		expect(question.current.message).toBe(t('play.tutorial.guestConfirm'));
		expect(question.current.message).toContain('100핑');
		answer(false); // 취소
		await settle();
		expect(onstart).not.toHaveBeenCalled();
		await fireEvent.click(cards()[0].querySelector('button'));
		await settle();
		answer(true); // 손님으로 진행하기
		await settle();
		expect(onstart).toHaveBeenCalledWith('lobby');
	});
	it('asks a guest again before replaying a cleared lesson', async () => {
		setProgress({ signedIn: false, done: [] });
		render(TutorialDialog, { store: store(true), open: true, onstart() {}, oncommands() {} });
		await tick();
		await fireEvent.click(cards()[1].querySelector('button'));
		await settle();
		expect(question.current?.message).toBe(t('play.tutorial.guestConfirm'));
		answer(false);
	});
});
