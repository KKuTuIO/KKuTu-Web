import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import RoomScreen from './RoomScreen.svelte';

afterEach(cleanup);

const user = (id, game) => ({ id, name: id, lv: 1, score: 0, equip: {}, game: { ready: false, form: 'J', team: 0, practice: false, ...game } });
function makeStore({ me = 'me', master = 'me', others = [], playing = false } = {}) {
	const users = { me: user('me'), ...Object.fromEntries(others.map((o) => [o.id, o])) };
	const room = { no: 7, title: '테스트방', locked: false, mode: '끝말잇기', tags: ['매너'], rules: [{ key: 'manner', label: '매너', expl: '한방 단어를 사용할 수 없게 합니다.' }], modeKey: 'X', cur: 1 + others.length, limit: 4, rounds: 3, time: 60, playing, master, modeIndex: 0, players: ['me', ...others.map((o) => o.id)] };
	const calls = [];
	const act = (name) => (...a) => calls.push([name, ...a]);
	return {
		calls,
		store: {
			net: { id: me, guest: false, admin: false, chat: [], users, meta: { moremiParts: [], rules: { X: { ai: false } }, modes: ['X'], labels: {} } },
			shop: {},
			currentRoom: room,
			get roomPlayers() { return room.players.map((id) => users[id]); },
			isMaster: master === me,
			myGame: users[me].game,
			toggleReady: act('ready'), start: act('start'), setSpectate: act('spectate'), setTeam: act('team'), leaveRoom: act('leave'), practice: act('practice'), kick: act('kick'), handover: act('handover'), pushNotice: act('notice'), invite: act('invite'), hooks: { openUserMenu: (id, e, layout) => calls.push(['menu', id, layout]) }
		}
	};
}

describe('RoomScreen', () => {
	it('shows the room, its seats and the empty ones', () => {
		const { store } = makeStore({ others: [user('b', { ready: true })] });
		render(RoomScreen, { store, onsettings: vi.fn() });
		expect(screen.getByRole('heading', { name: /테스트방/ })).toBeTruthy();
		expect(document.querySelectorAll('[data-empty-seat]').length).toBe(2); // limit 4, two seated
		expect(screen.queryByText('빈 자리')).toBeNull(); // open seats carry no text or icon
		expect(screen.getByLabelText(/b, 준비/)).toBeTruthy();
		expect(screen.getByLabelText(/me, 방장/)).toBeTruthy();
	});
	it('the master gets 시작 and 방 설정; clicking a seat opens that player\'s menu', async () => {
		const { store, calls } = makeStore({ others: [user('b')] });
		render(RoomScreen, { store, onsettings: vi.fn() });
		expect(screen.queryByRole('button', { name: /^준비/ })).toBeNull();
		await fireEvent.click(screen.getByRole('button', { name: '시작' }));
		expect(screen.getByRole('button', { name: '방 설정' })).toBeTruthy();
		await fireEvent.click(screen.getByRole('button', { name: /^b, 대기/ }));
		expect(calls).toEqual([['start'], ['menu', 'b', 'roomUser']]);
	});
	it('a regular player gets 준비 and no management buttons', async () => {
		const { store, calls } = makeStore({ master: 'b', others: [user('b')] });
		render(RoomScreen, { store, onsettings: vi.fn() });
		expect(screen.queryByRole('button', { name: '시작' })).toBeNull();
		expect(screen.queryByRole('button', { name: '방 설정' })).toBeNull();
		await fireEvent.click(screen.getByRole('button', { name: '준비' }));
		await fireEvent.click(screen.getByRole('button', { name: '나가기' }));
		expect(calls).toEqual([['ready'], ['leave']]);
	});
	it('locks actions while the game is running', () => {
		const { store } = makeStore({ playing: true });
		render(RoomScreen, { store, onsettings: vi.fn() });
		expect(screen.getByRole('button', { name: '시작' }).disabled).toBe(true);
		expect(screen.queryByText(/준비 중/)).toBeNull(); // the seats stay on screen until the game's veil covers them
	});
});
