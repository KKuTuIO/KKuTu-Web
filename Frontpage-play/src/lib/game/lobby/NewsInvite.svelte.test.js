import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import NewsScreen from './NewsScreen.svelte';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), playResultBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
vi.mock('../shop.js', async (original) => ({ ...(await original()), loadGoodDetails: () => Promise.resolve({}) }));
afterEach(cleanup);

const NOW = Date.now();
const H = 3_600_000;
const event = {
	id: 'friends', name: '우리 같이, 글자로 놀자!', bulletin: false, permanent: false, start: NOW - H, until: NOW + 300 * 24 * H,
	presentation: { blocks: [{ type: 'invite' }] },
	invite: {
		max: 2, days: 7,
		stages: [
			{ id: 'play-1', name: '첫 게임 완료', target: 1, inviteeRewards: [{ type: 'ping', q: 500 }, { type: 'letter', tier: 'common', q: 5 }], inviterRewards: [{ type: 'ping', q: 500 }] },
			{ id: 'play-5', name: '게임 5판 완료', target: 5, inviteeRewards: [{ type: 'ping', q: 1000 }], inviterRewards: [{ type: 'ping', q: 1000 }] }
		]
	}
};
const status = (extra = {}) => ({ type: 'event.invite', eventId: 'friends', code: 'ABCD2345', friends: [], ...extra });
const stage = (id, progress, target, claimed = false) => ({ id, progress, target, claimed });

async function setup(packet = status()) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	applyPacket(net, { type: 'event.progress', event: { list: [event] }, missions: [], attendance: [] });
	const sent = [];
	store.conn.send = (type, data) => sent.push({ type, ...data });
	render(NewsScreen, { store });
	applyPacket(net, packet);
	await tick();
	await fireEvent.click(screen.getByRole('button', { name: /우리 같이, 글자로 놀자!/ }));
	await tick();
	return { store, net, sent };
}

describe('친구 초대', () => {
	it('asks where I stand and shows my code and the places for friends', async () => {
		const { sent } = await setup();
		expect(sent.some((s) => s.type === 'event.invite' && s.eventId === 'friends')).toBe(true);
		expect(screen.getByLabelText('내 초대 코드').textContent).toBe('ABCD2345');
		expect(screen.getByRole('button', { name: /초대 링크 공유/ }).disabled).toBe(false);
		expect(screen.getAllByText('아직 비어 있어요. 초대 링크를 친구에게 보내 보세요.')).toHaveLength(2); // two places, none taken
		expect(screen.queryByText('친구 덕분에 받는 보상')).toBeNull(); // I was not invited
	});

	it('opens the shared dialog with my link to pass on', async () => {
		await setup();
		await fireEvent.click(screen.getByRole('button', { name: /초대 링크 공유/ }));
		await tick();
		expect(screen.getByRole('dialog', { name: '친구 초대 링크' })).toBeTruthy();
		expect(screen.getByDisplayValue(`${location.origin}/?ref=ABCD2345`)).toBeTruthy();
	});

	it("shows how far an invited friend is and lets me take a stage when it is done, once, for that friend", async () => {
		const { sent } = await setup(status({ friends: [{ n: 1, stages: [stage('play-1', 1, 1), stage('play-5', 2, 5)] }] }));
		const card = screen.getByLabelText('친구 1');
		const buttons = within(card).getAllByRole('button', { name: /보상 받기/ });
		expect(buttons.map((b) => b.disabled)).toEqual([false, true]); // the first stage is done, the second is not yet
		await fireEvent.click(buttons[0]);
		expect(sent.at(-1)).toEqual({ type: 'event.inviteClaim', eventId: 'friends', stage: 'play-1', friend: 1 });
		expect(within(card).getAllByRole('button', { name: /보상 받기/ })[0].disabled).toBe(true); // waits for the answer
	});

	it('shows my own stages when I was invited, and claims them without a friend', async () => {
		const { sent } = await setup(status({ invitee: { stages: [stage('play-1', 1, 1), stage('play-5', 1, 5)] } }));
		expect(screen.getByText('친구 덕분에 받는 보상')).toBeTruthy();
		await fireEvent.click(screen.getAllByRole('button', { name: /보상 받기/ })[0]);
		expect(sent.at(-1)).toEqual({ type: 'event.inviteClaim', eventId: 'friends', stage: 'play-1' });
	});

	it('shows a taken stage as taken', async () => {
		await setup(status({ friends: [{ n: 1, stages: [stage('play-1', 1, 1, true), stage('play-5', 5, 5)] }] }));
		const card = screen.getByLabelText('친구 1');
		expect(within(card).getByRole('button', { name: '받음' }).disabled).toBe(true);
	});
});
