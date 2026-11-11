import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import NewsScreen from './NewsScreen.svelte';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), playResultBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
vi.mock('../shop.js', async (original) => ({ ...(await original()), loadGoodDetails: () => Promise.resolve({}) }));
afterEach(cleanup);

const NOW = Date.now();
const H = 3_600_000;
const newbie = {
	id: 'newbie', name: '새내기 길잡이', bulletin: false, permanent: true, start: NOW - H, until: NOW + 300 * 24 * H,
	audience: { kind: 'newcomer', days: 7 },
	presentation: { blocks: [{ type: 'missions' }] },
	missions: [
		{ id: 'lesson', name: '로비 둘러보기', metric: 'tutorial.done', target: 1, period: 'event', goto: 'tutorial:lobby', rewards: [] },
		{ id: 'gacha', name: '첫 뽑기', metric: 'gacha.draw', target: 1, period: 'event', goto: 'gacha', rewards: [{ type: 'ping', q: 300 }] },
		{ id: 'play', name: '한 판 하기', metric: 'game.play', target: 1, period: 'event', rewards: [{ type: 'ping', q: 50 }] },
		{ id: 'win', name: '이겨 보기', metric: 'game.win', target: 1, period: 'event', rewards: [{ type: 'ping', q: 50 }] }
	]
};
const rows = (...done) => ({
	type: 'event.progress', event: { list: [newbie] }, attendance: [],
	missions: newbie.missions.map((m) => ({ eventId: 'newbie', missionId: m.id, periodKey: 'event', progress: done.includes(m.id) ? 1 : 0, target: 1, claimed: false }))
});

async function setup(...done) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	applyPacket(net, rows(...done));
	const sent = [];
	store.conn.send = (type, data) => sent.push({ type, ...data });
	const went = [];
	store.hooks.goto = (dest) => went.push(dest);
	render(NewsScreen, { store });
	await tick();
	await fireEvent.click(screen.getByRole('button', { name: /새내기 길잡이/ }));
	await tick();
	return { store, net, sent, went };
}

describe('the missions as cards', () => {
	it('has 학사모레미 say a line for newcomers', async () => {
		await setup();
		expect(screen.getByText('튜토리얼을 마치고 첫 뽑기까지 함께 해 보자레미!')).toBeTruthy();
	});

	it('sends a mission that is not done yet where it can be done, and shows one without a destination no button for it', async () => {
		const { went } = await setup();
		const buttons = screen.getAllByRole('button', { name: '이동' });
		expect(buttons).toHaveLength(2); // the lesson and the first draw; "한 판 하기" says nowhere
		await fireEvent.click(buttons[0]);
		await fireEvent.click(buttons[1]);
		expect(went).toEqual(['tutorial:lobby', 'gacha']);
	});

	it('counts a lesson that pays nothing of its own as done, not as something to claim', async () => {
		await setup('lesson');
		expect(screen.getAllByRole('button', { name: '이동' })).toHaveLength(1);
		expect(screen.getByText('완료')).toBeTruthy();
		expect(screen.getByRole('button', { name: /모두 받기/ }).disabled).toBe(true);
	});

	it('takes everything that is done with 모두 받기, and waits for the answer', async () => {
		const { sent } = await setup('play', 'win');
		const all = screen.getByRole('button', { name: /모두 받기/ });
		expect(all.disabled).toBe(false);
		await fireEvent.click(all);
		expect(sent.at(-1)).toEqual({ type: 'event.claimMissions', eventId: 'newbie' });
		expect(screen.getByRole('button', { name: /모두 받기/ }).disabled).toBe(true);
	});
});

describe('the tab of an event for newcomers', () => {
	it('comes right after 전체, named for who the event is for, and lists only those events', async () => {
		await setup();
		cleanup();
		const store = createLobbyStore(1);
		store.net.status = 'ready';
		store.net.id = 'me';
		const back = { ...newbie, id: 'back', name: '복귀 출석판', audience: { kind: 'returning', days: 30, window: 14 }, missions: [] };
		applyPacket(store.net, { type: 'event.progress', event: { list: [back, { id: 'plain', name: '일반 행사', bulletin: false, permanent: false, start: NOW - H, until: NOW + H }] }, missions: [], attendance: [] });
		store.conn.send = () => {};
		render(NewsScreen, { store });
		await tick();
		const names = screen.getAllByRole('tab').map((tab) => tab.textContent.replace(/\d+$/, '').trim());
		expect(names.slice(0, 2)).toEqual(['전체', '복귀']);
		await fireEvent.click(screen.getByRole('tab', { name: /^복귀/ }));
		await tick();
		expect(screen.getByRole('button', { name: /복귀 출석판/ })).toBeTruthy();
		expect(screen.queryByText('일반 행사')).toBeNull();
	});
});
