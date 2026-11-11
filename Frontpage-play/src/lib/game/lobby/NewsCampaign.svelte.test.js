import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import NewsScreen from './NewsScreen.svelte';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), playResultBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
vi.mock('../shop.js', async (original) => ({ ...(await original()), loadGoodDetails: () => Promise.resolve({ boxSpaceTravel: { name: '우주 여행 상자' } }) }));
afterEach(cleanup);

const NOW = Date.now();
const H = 3_600_000;
const ping = (q) => ({ type: 'ping', q });
const campaign = {
	id: 'camp', name: '모두의 단어 캠페인', bulletin: false, permanent: false, start: NOW - 24 * H, until: NOW + 100 * H,
	campaign: { timezone: 'Asia/Seoul', schedule: [{ from: '00:00', to: '24:00' }], goal: { metric: 'word.accept', points: 1 }, cap: 200, tiers: [{ points: 20, rewards: [ping(300)] }, { points: 60, rewards: [ping(1000)] }, { points: 150, rewards: [ping(3000)] }] },
	presentation: { blocks: [{ type: 'campaign' }] }
};
const plain = { id: 'ev', name: '일반 행사', bulletin: false, permanent: false, start: NOW - H, until: NOW + 9 * H };
const status = (extra = {}) => ({ type: 'event.campaign', eventId: 'camp', now: Date.now(), current: { key: 'k1', startsAt: NOW - H, endsAt: NOW + H, total: 25, mine: 7, joined: true }, past: [], ...extra });

async function setup(extra) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	applyPacket(net, { type: 'event.progress', event: { list: [campaign, plain] }, missions: [], attendance: [] });
	const sent = [];
	store.conn.send = (type, data) => sent.push({ type, ...data });
	render(NewsScreen, { store });
	applyPacket(net, status(extra));
	await tick();
	return { store, net, sent };
}
const mineSection = () => screen.getAllByLabelText('내 협동 포인트').find((el) => el.tagName === 'SECTION');
const open = async () => {
	await fireEvent.click(screen.getByRole('button', { name: /모두의 단어 캠페인/ }));
	await tick();
};

describe('the 캠페인 section', () => {
	it("lists today's hot times by number beside the clock", async () => {
		await setup();
		await open();
		const list = screen.getByLabelText('오늘의 핫타임');
		expect(list.textContent).toContain('1회차');
		expect(list.textContent).toContain('00:00~24:00');
		expect(list.textContent).toContain('진행 중');
	});

	it('explains how the points are earned in the event info', async () => {
		await setup();
		await open();
		await fireEvent.click(screen.getByRole('button', { name: '행사 정보' }));
		await tick();
		const how = screen.getByLabelText('이렇게 진행돼요');
		expect(how.textContent).toContain('협동 포인트');
		expect(how.textContent).toContain('단어를 인정받을 때마다 1점');
		expect(how.textContent).toContain('최대 200점');
	});

	it('has a tab of its own, apart from the events', async () => {
		await setup();
		expect(screen.getByRole('tab', { name: /^캠페인/ }).textContent).toBe('캠페인1');
		expect(screen.getByRole('tab', { name: /^행사/ }).textContent).toBe('행사1');
		await fireEvent.click(screen.getByRole('tab', { name: /^캠페인/ }));
		expect(screen.getByText('모두의 단어 캠페인')).toBeTruthy();
		expect(screen.queryByText('일반 행사')).toBeNull();
	});

	it('opens in the pane with the list tabs still there, and asks the server where it is, again every few seconds', async () => {
		vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] });
		const { sent } = await setup();
		await open();
		expect(screen.getByRole('tab', { name: /^전체/ })).toBeTruthy(); // the page's tabs, rail and chat are not touched
		const asked = () => sent.filter((s) => s.type === 'event.campaign').length;
		const first = asked();
		expect(first).toBeGreaterThan(0);
		await vi.advanceTimersByTimeAsync(10_500);
		expect(asked()).toBeGreaterThanOrEqual(first + 2);
		vi.useRealTimers();
	});

	it("shows everybody's total, my part and how far the next tier is", async () => {
		await setup();
		await open();
		expect(screen.getAllByText('협동 포인트').length).toBeGreaterThan(0);
		expect(screen.queryByText('모두의 누적')).toBeNull();
		expect(mineSection().textContent).toContain('7점');
		expect(screen.getByText('다음 단계까지 35점')).toBeTruthy(); // 60 - 25
		expect(screen.getByText('1단계 보상이 보장됩니다.')).toBeTruthy();
		expect(mineSection().textContent).toContain('200'); // the cap
		expect(screen.getByRole('progressbar', { name: '목표 단계' }).getAttribute('aria-valuenow')).toBe('25');
	});

	it('follows the total as new numbers arrive', async () => {
		const { net } = await setup();
		await open();
		applyPacket(net, status({ current: { key: 'k1', startsAt: NOW - H, endsAt: NOW + H, total: 160, mine: 9, joined: true } }));
		await tick();
		expect(screen.getByText('모든 단계를 달성했습니다!')).toBeTruthy();
	});

	it('shows a green chip when I am eligible and a red one, with the way to become so, when I am not', async () => {
		await setup();
		await open();
		const green = screen.getByText('보상 대상');
		expect(green.className).toContain('bg-(--kk-accent-strong)');
		expect(green.className).toContain('text-white');
		expect(screen.queryByText('보상 대상 아님')).toBeNull();
		cleanup();
		await setup({ current: { key: 'k1', startsAt: NOW - H, endsAt: NOW + H, total: 3, mine: 0, joined: false } });
		await open();
		const red = screen.getByText('보상 대상 아님');
		expect(red.className).toContain('bg-(--kk-danger)');
		expect(red.className).toContain('text-white');
		expect(red.parentElement.textContent).toContain('달성 조건: 게임 1판 이상 플레이'); // the chip and the condition in one line
		expect(screen.queryByText(/핫타임에 게임을 한 판/)).toBeNull();
	});

	it('shows the hot time left in an orange-red plate at the top while it is on', async () => {
		await setup(); // the hot time ends in one hour
		await open();
		const timer = screen.getByRole('timer', { name: '핫타임 진행 중' });
		expect(timer.textContent).toContain('핫타임 진행 중');
		expect(timer.textContent).toMatch(/00:(59|00):\d{2}|01:00:00/);
		expect(timer.querySelector('span').className).toContain('bg-[#f97316]'); // the orange label on the rim
	});

	it('shows a grey plate that counts down to the next hot time when none is on', async () => {
		await setup({ current: undefined, next: { key: 'n', startsAt: NOW + 2 * H, endsAt: NOW + 3 * H } });
		await open();
		const timer = screen.getByRole('timer', { name: '핫타임까지' });
		expect(timer.textContent).toMatch(/0[12]:\d{2}:\d{2}/);
		expect(timer.querySelector('span').className).toContain('bg-[#4f6bed]'); // a cool colour while it is waited for
	});

	it('shows no timer once the campaign is over', async () => {
		await setup({ current: undefined, next: undefined });
		await open();
		expect(screen.queryByRole('timer', { name: /핫타임/ })).toBeNull(); // the header's own clock of the event is another thing
		expect(screen.getByText('캠페인이 끝났습니다.')).toBeTruthy();
	});

	it('puts the cooperative points and my part side by side', async () => {
		await setup();
		await open();
		const total = screen.getByRole('region', { name: '협동 포인트' });
		const mine = mineSection();
		expect(total.parentElement).toBe(mine.parentElement);
		expect(total.parentElement.className).toContain('flex-wrap');
		expect(total.className).toContain('flex-1');
		expect(mine.className).toContain('flex-1');
	});

	it('lists every tier with its rewards right on the page, and marks the ones reached', async () => {
		await setup();
		await open();
		expect(screen.queryByRole('button', { name: '보상' })).toBeNull(); // no window to open
		const list = screen.getByLabelText('보상');
		expect(within(list).getAllByText(/^\d단계$/)).toHaveLength(3);
		expect(within(list).getAllByText('달성')).toHaveLength(1); // 25 points: the first tier only
		expect(within(list).queryByText(/가장 높은 단계의 보상/)).toBeNull(); // no explaining text under the gauge
	});

	it('draws the gauge full width with every checkpoint on it at its own share of the way, its reward above and its points below', async () => {
		await setup(); // 25 points; the checkpoints are at 20, 60 and 150
		await open();
		const gauge = screen.getByRole('progressbar', { name: '목표 단계' });
		expect(gauge.querySelector('span').style.width).toBe('16.666666666666664%'); // 25 of 150
		const marks = within(screen.getByLabelText('보상')).getAllByRole('listitem').filter((li) => li.style.left);
		expect(marks.map((li) => li.style.left)).toEqual(['13.333333333333334%', '40%', '100%']); // 20, 60, 150 of 150
		expect(within(screen.getByLabelText('보상')).getAllByText('달성')).toHaveLength(1); // only the first is passed
		for (const n of ['20', '60', '150']) expect(screen.getAllByText(n).length).toBeGreaterThan(0);
	});

	it('has no text explaining the guarantee when nothing is reached yet, and none about the rule', async () => {
		await setup({ current: { key: 'k1', startsAt: NOW - H, endsAt: NOW + H, total: 3, mine: 0, joined: false } });
		await open();
		expect(screen.queryByText(/첫 단계를 달성하면/)).toBeNull();
		expect(screen.queryByText(/핫타임이 끝난 뒤/)).toBeNull();
	});

	it('offers a finished hot time I took part in once, and only the ones with a tier', async () => {
		const past = [
			{ key: 'p1', startsAt: NOW - 30 * H, endsAt: NOW - 28 * H, total: 70, mine: 5, joined: true, claimed: false, tier: 1 },
			{ key: 'p2', startsAt: NOW - 54 * H, endsAt: NOW - 52 * H, total: 90, mine: 5, joined: true, claimed: true, tier: 1 },
			{ key: 'p3', startsAt: NOW - 78 * H, endsAt: NOW - 76 * H, total: 4, mine: 0, joined: true, claimed: false, tier: -1 }
		];
		const { sent } = await setup({ past });
		await open();
		const section = screen.getByLabelText('받을 수 있는 보상');
		expect(within(section).getAllByRole('button', { name: '보상 받기' })).toHaveLength(1);
		await fireEvent.click(within(section).getByRole('button', { name: '보상 받기' }));
		expect(sent.at(-1)).toEqual({ type: 'event.campaignClaim', eventId: 'camp', round: 'p1' });
		expect(within(section).getByRole('button', { name: '보상 받기' }).disabled).toBe(true); // waits for the answer
	});

	it('opens the record of the earlier hot times in a dialog, with how each went', async () => {
		await setup({ past: [{ key: 'p1', startsAt: NOW - 30 * H, endsAt: NOW - 28 * H, total: 70, mine: 5, joined: true, claimed: true, tier: 1 }, { key: 'p3', startsAt: NOW - 78 * H, endsAt: NOW - 76 * H, total: 4, mine: 0, joined: false, claimed: false, tier: -1 }] });
		await open();
		expect(screen.queryByRole('dialog')).toBeNull(); // the record waits behind a button at the top
		await fireEvent.click(screen.getByRole('button', { name: '핫타임 기록' }));
		await tick();
		const history = screen.getByRole('dialog');
		expect(within(history).getByText('2단계')).toBeTruthy();
		expect(within(history).getByText('미달성')).toBeTruthy();
	});

	it('goes back to the list from its title bar when the rail is not shown', async () => {
		await setup();
		await open();
		await fireEvent.click(screen.getByRole('button', { name: '목록으로' }));
		await tick();
		expect(screen.queryByText('모두의 누적')).toBeNull();
	});
});
