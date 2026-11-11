import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import { EXP } from '../level.js';
import { levelUp } from '../levelUp.svelte.js';
import LevelUpHost from './LevelUpHost.svelte';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
afterEach(() => {
	cleanup();
	levelUp.reset();
});

const inside = (level) => (EXP[level - 2] ?? 0) + 1;
function setup(score) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	net.users.me = { id: 'me', name: 'me', score, lv: 1, money: 0, equip: {} };
	return { store, net };
}
const popup = () => screen.queryByRole('dialog', { name: '레벨 업!' });

describe('LevelUpHost', () => {
	it('announces a level reached outside a game, e.g. by opening a 백과사전 낱장, over whatever is open', async () => {
		const { store, net } = setup(inside(9));
		render(LevelUpHost, { store });
		await tick();
		expect(popup()).toBeNull();
		applyPacket(net, { type: 'obtain', exp: 80, money: 0, data: { score: inside(10) } }); // what the server sends after the item is used
		await tick();
		const dialog = popup();
		expect(dialog).toBeTruthy();
		expect(dialog.getAttribute('data-kind')).toBe('level');
		expect(dialog.open).toBe(true);
		expect(dialog.textContent).toContain('레벨 10');
	});

	it('says nothing when the level stays or the experience falls, and nothing on the first sight of my score', async () => {
		const { store, net } = setup(inside(9));
		render(LevelUpHost, { store });
		await tick();
		applyPacket(net, { type: 'obtain', exp: 5, money: 0, data: { score: inside(9) + 5 } });
		await tick();
		applyPacket(net, { type: 'obtain', exp: 0, money: 0, data: { score: 3 } });
		await tick();
		expect(popup()).toBeNull();
	});

	it('a big jump is three popups and a summary; Esc skips to the summary', async () => {
		vi.useFakeTimers();
		const { store, net } = setup(inside(2));
		render(LevelUpHost, { store });
		await tick();
		applyPacket(net, { type: 'obtain', exp: 1, money: 0, data: { score: inside(40) } });
		await tick();
		const kinds = [];
		for (let i = 0; i < 6 && popup(); i++) {
			kinds.push(popup().getAttribute('data-kind'));
			await vi.advanceTimersByTimeAsync(400);
			await fireEvent.keyDown(window, { key: i === 1 ? 'Escape' : ' ' });
			await vi.advanceTimersByTimeAsync(300); // the last one pops out before the dialog closes
		}
		expect(kinds).toEqual(['level', 'level', 'summary']); // Esc on the second popup folded the rest into the summary
		expect(levelUp.busy).toBe(false);
		vi.useRealTimers();
	});
});

describe('LevelUpHost, back to back', () => {
	it('a level-up right after another sequence ended is shown, not left faded out by the exit animation', async () => {
		vi.useFakeTimers();
		const { store, net } = setup(inside(9));
		render(LevelUpHost, { store });
		await tick();
		applyPacket(net, { type: 'obtain', exp: 1, money: 0, data: { score: inside(10) } });
		await tick();
		await vi.advanceTimersByTimeAsync(400);
		await fireEvent.keyDown(window, { key: ' ' }); // done: the screen starts to leave
		await tick();
		applyPacket(net, { type: 'obtain', exp: 1, money: 0, data: { score: inside(11) } }); // before it has gone
		await tick();
		await vi.advanceTimersByTimeAsync(400);
		const dialog = popup();
		expect(dialog).toBeTruthy();
		expect(dialog.querySelector('.stack').classList.contains('closing')).toBe(false);
		expect(dialog.textContent).toContain('레벨 11');
		vi.useRealTimers();
	});
});
