import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import GameHeader from './GameHeader.svelte';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), stopBgm: vi.fn(), playGameBgm: vi.fn(), preloadGameSounds: vi.fn(), audio: {} }));
afterEach(cleanup);

const player = (id, form = 'J') => ({ id, profile: { title: id }, data: { score: 0 }, game: { form } });
function setup({ practice = false, form = 'J' } = {}) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	net.meta.modes = ['KSH'];
	net.meta.rules = { KSH: { rule: 'Classic' } };
	net.meta.labels = { 'kkutu.menu.reserveLeave': '<i class="fa"></i></i>&nbsp;퇴장 예약' };
	applyPacket(net, { type: 'room', room: { id: 5, title: 't', mode: 0, round: 3, time: 60, limit: 4, gaming: true, practice, master: 'me', players: [player('me', form), player('b')], game: { seq: ['me', 'b'], turn: 0, title: [] }, opts: {} } });
	applyPacket(net, { type: 'roundReady', round: 1, char: '가' });
	return store;
}

describe('GameHeader 퇴장 예약', () => {
	it('is there for a player of a real game, and toggles', async () => {
		const store = setup();
		render(GameHeader, { store, ondict: () => {} });
		const btn = screen.getByRole('button', { name: '퇴장 예약' });
		expect(btn.getAttribute('aria-pressed')).toBe('false');
		await fireEvent.click(btn);
		expect(btn.getAttribute('aria-pressed')).toBe('true');
	});
	it('is not offered to a spectator or in a practice game', async () => {
		for (const opts of [{ form: 'S' }, { practice: true }]) {
			const store = setup(opts);
			render(GameHeader, { store, ondict: () => {} });
			await tick();
			expect(screen.queryByRole('button', { name: '퇴장 예약' })).toBeNull();
			cleanup();
		}
	});
});
