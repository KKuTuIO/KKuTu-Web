import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import NewsScreen from './NewsScreen.svelte';
import { playSound } from '../audio.svelte.js';

const motion = vi.hoisted(() => ({ reduce: true }));
vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), playResultBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
vi.mock('../motion.js', async (original) => ({ ...(await original()), reducedMotion: () => motion.reduce }));
vi.mock('../shop.js', async (original) => ({ ...(await original()), loadGoodDetails: () => Promise.resolve({ leaf: { name: '리프' }, tileToken: { name: '타일 룰렛 티켓' }, b1_dev: { name: '개발자 배지' } }) }));
beforeEach(() => {
	motion.reduce = true;
	vi.mocked(playSound).mockClear();
});
afterEach(() => {
	cleanup();
	vi.useRealTimers();
});

const NOW = Date.now();
const H = 3_600_000;
const ping = (q) => ({ type: 'ping', q });
// 0: leaf (given once) 4%, 1: 핑 100 40%, 2: 핑 500 12%, 3-24: 핑 10 (22 tiles), 2% each: 100% in all
const cells = [
	{ reward: { type: 'item', name: 'leaf', q: 1 }, percent: 4, unique: true },
	{ reward: ping(100), percent: 40, unique: false },
	{ reward: ping(500), percent: 12, unique: false },
	...Array.from({ length: 22 }, () => ({ reward: ping(10), percent: 2, unique: false }))
];
const tileEvent = (extra = {}) => ({
	id: 'tile', name: '타일 룰렛', bulletin: false, permanent: false, start: NOW - H, until: NOW + 100 * H,
	presentation: { blocks: [{ type: 'tileRoulette' }] },
	tile: { token: 'tileToken', cost: 1, multi: [3], cells, gauge: { perDraw: 1, points: 3, steps: [{ points: 5, rewards: [{ type: 'item', name: 'b1_dev', q: 1 }] }, { points: 10, rewards: [ping(20000)] }] }, ...extra }
});

async function setup({ box = { tileToken: { value: 5 } }, tile, presentation } = {}) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	net.users.me = { id: 'me', name: '나', money: 1000 };
	net.box = box;
	const base = tileEvent(tile);
	const event = presentation ? { ...base, presentation: { ...base.presentation, ...presentation } } : base;
	applyPacket(net, { type: 'event.progress', event: { list: [event] }, missions: [], attendance: [] });
	const sent = [];
	store.conn.send = (type, data) => sent.push({ type, ...data });
	render(NewsScreen, { store });
	await fireEvent.click(screen.getByRole('button', { name: /타일 룰렛/ }));
	await tick();
	return { store, net, sent, event };
}
const result = (net, extra = {}) =>
	applyPacket(net, { type: 'event.tileResult', eventId: 'tile', draws: [{ cell: 1, owned: false }], rewards: [ping(100)], gauge: { points: 4, reached: [] }, ...extra }, Date.now() + 1);
const board = () => screen.getByRole('list', { name: '타일 룰렛 판' });

describe('NewsTile', () => {
	it('shows a 5x5 board with the tickets, the mileage and the way to the chances', async () => {
		await setup();
		expect(within(board()).getAllByRole('listitem')).toHaveLength(25);
		expect(screen.getByLabelText('티켓 5')).toBeTruthy();
		expect(screen.getByRole('progressbar', { name: '마일리지' }).getAttribute('aria-valuenow')).toBe('3');
		expect(screen.getByText('3 / 10')).toBeTruthy();
		expect(screen.getByRole('button', { name: /확률 보기/ })).toBeTruthy();
	});

	it('cannot be drawn without enough tickets, and does not lecture about it', async () => {
		await setup({ box: {} });
		expect(screen.getByRole('button', { name: /1회 뽑기/ }).disabled).toBe(true);
		expect(screen.queryByText(/티켓이 부족합니다/)).toBeNull();
		expect(screen.queryByText(/티켓 .*장을 사용합니다/)).toBeNull();
	});

	it('has no line explaining how the mileage fills', async () => {
		await setup();
		expect(screen.queryByText(/차오르고/)).toBeNull();
		expect(screen.queryByText(/줄어들지 않습니다/)).toBeNull();
	});

	it('uses the shop\'s draw buttons: the label and the price as a pill, here the tickets', async () => {
		await setup({ tile: { cost: 2 } });
		const one = screen.getByRole('button', { name: /1회 뽑기/ });
		expect(one.className).toContain('kk-btn-primary');
		expect(one.textContent).toContain('2'); // 2 tickets for one draw
		expect(screen.getByRole('button', { name: /3회 뽑기/ }).textContent).toContain('6'); // and 6 for three
	});

	it('offers the counts of the event only while the tickets pay for them', async () => {
		await setup({ box: { tileToken: { value: 2 } } });
		expect(screen.getByRole('button', { name: /3회 뽑기/ }).disabled).toBe(true);
		expect(screen.getByRole('button', { name: /1회 뽑기/ }).disabled).toBe(false);
	});

	it('asks the server for one draw, or for the count of the button', async () => {
		const { sent } = await setup();
		await fireEvent.click(screen.getByRole('button', { name: /1회 뽑기/ }));
		expect(sent.at(-1)).toEqual({ type: 'event.tileDraw', eventId: 'tile', count: 1 });
	});

	it('asks for three at once from "3회 뽑기"', async () => {
		const { sent } = await setup();
		await fireEvent.click(screen.getByRole('button', { name: /3회 뽑기/ }));
		expect(sent.at(-1)).toEqual({ type: 'event.tileDraw', eventId: 'tile', count: 3 });
	});

	it('runs the highlight to the tile the server named: a tick for every hop, then the success sound once, and says what it gave', async () => {
		const { net } = await setup();
		await fireEvent.click(screen.getByRole('button', { name: /1회 뽑기/ }));
		net.box = { tileToken: { value: 4 } };
		result(net);
		await vi.waitFor(() => expect(screen.getByRole('status').textContent).toBe('핑 ×100 당첨!'));
		const sounds = vi.mocked(playSound).mock.calls.map((c) => c[0]);
		expect(sounds.filter((s) => s === 'As10').length).toBeGreaterThan(20);
		expect(sounds.filter((s) => s === 'success')).toHaveLength(1);
		expect(sounds.lastIndexOf('As10')).toBeLessThan(sounds.indexOf('success')); // the ticks come before the stop
	});

	it('shows the "you got these" screen with what the draw gave', async () => {
		const { net } = await setup();
		await fireEvent.click(screen.getByRole('button', { name: /1회 뽑기/ }));
		result(net);
		await vi.waitFor(() => expect(screen.getByText('타일 룰렛 결과입니다.')).toBeTruthy());
	});

	it('shows the mileage as the full-width gauge: a mark for each checkpoint at its share of the way, with its reward above', async () => {
		await setup(); // 3 points; the checkpoints are at 5 and 10
		const section = screen.getByLabelText('마일리지', { selector: 'section' });
		const gauge = within(section).getByRole('progressbar', { name: '마일리지' });
		expect(gauge.getAttribute('aria-valuenow')).toBe('3');
		expect(gauge.querySelector('span').style.width).toBe('30%'); // 3 of 10
		const marks = within(section).getAllByRole('listitem').filter((li) => li.style.left);
		expect(marks.map((li) => li.style.left)).toEqual(['50%', '100%']);
		expect(within(section).getByText('3 / 10')).toBeTruthy();
		expect(section.textContent).not.toContain('차오르고'); // and no explaining line
	});

	it('tells that the mileage gave something too', async () => {
		const { net } = await setup();
		await fireEvent.click(screen.getByRole('button', { name: /1회 뽑기/ }));
		result(net, { rewards: [ping(100), { type: 'item', name: 'b1_dev', q: 1 }], gauge: { points: 5, reached: [5] } });
		await vi.waitFor(() => expect(screen.getByText('타일 룰렛 결과와 마일리지 보너스입니다.')).toBeTruthy());
	});

	it('marks a tile whose given-once reward is already owned, and says nothing was given when it comes up', async () => {
		const { net } = await setup({ box: { tileToken: { value: 5 }, leaf: { value: 1 } } });
		const first = within(board()).getAllByRole('listitem')[0];
		expect(first.textContent).toContain('보유 중');
		expect(first.getAttribute('aria-label')).toContain('보유 중');
		await fireEvent.click(screen.getByRole('button', { name: /1회 뽑기/ }));
		result(net, { draws: [{ cell: 0, owned: true }], rewards: [], gauge: { points: 4, reached: [] } });
		await vi.waitFor(() => expect(screen.getByRole('status').textContent).toBe('리프은(는) 이미 보유 중이라 지급되지 않았습니다.'));
		expect(screen.queryByText('타일 룰렛 결과입니다.')).toBeNull(); // nothing was given: no screen of rewards
	});

	it('marks no tile as owned for a consumable or a reward that is not given once', async () => {
		await setup({ box: { tileToken: { value: 5 }, leaf: { value: 0 } } });
		expect(within(board()).queryByText('보유 중')).toBeNull();
	});

	it('shows the chances the way the shop does: a toggle, and the percent over every tile', async () => {
		await setup();
		const toggle = screen.getByRole('button', { name: '확률 보기' });
		expect(toggle.getAttribute('aria-pressed')).toBe('false');
		expect(within(board()).queryByText('40%')).toBeNull();
		await fireEvent.click(toggle);
		expect(screen.getByRole('button', { name: '확률 숨기기' }).getAttribute('aria-pressed')).toBe('true');
		expect(within(board()).getByText('40%')).toBeTruthy(); // 핑 100
		expect(within(board()).getByText('4%')).toBeTruthy(); // leaf
		expect(within(board()).getAllByText('2%')).toHaveLength(22);
		expect(screen.getByText('치장 아이템은 중복 획득이 불가하며, 획득 여부와 무관하게 확률은 고정됩니다.')).toBeTruthy();
		await fireEvent.click(screen.getByRole('button', { name: '확률 숨기기' }));
		expect(within(board()).queryByText('40%')).toBeNull();
	});

	it('writes a small chance with its decimals, as the shop does', async () => {
		const tiny = cells.map((c, i) => (i === 0 ? { ...c, percent: 0.077 } : c));
		await setup({ tile: { cells: tiny } });
		await fireEvent.click(screen.getByRole('button', { name: '확률 보기' }));
		expect(within(board()).getByText('0.077%')).toBeTruthy();
	});

	it('writes every amount in full: 3,000 and 12,000, never 3천 or 1.2만', async () => {
		const big = cells.map((c, i) => (i === 1 ? { ...c, reward: ping(12000) } : i === 3 ? { ...c, reward: { type: 'item', name: 'wordToken', q: 3000 } } : c));
		await setup({ tile: { cells: big } });
		expect(within(board()).getByText('12,000')).toBeTruthy();
		expect(within(board()).getByText('×3,000')).toBeTruthy();
		expect(board().textContent).not.toMatch(/[천만]|\dK/);
	});

	it('frees the board again when no answer comes', async () => {
		vi.useFakeTimers();
		await setup();
		await fireEvent.click(screen.getByRole('button', { name: /1회 뽑기/ }));
		expect(screen.getByRole('button', { name: /1회 뽑기/ }).disabled).toBe(true);
		await vi.advanceTimersByTimeAsync(6100);
		expect(screen.getByRole('button', { name: /1회 뽑기/ }).disabled).toBe(false);
	});
});

describe('while the highlight runs', () => {
	it('keeps the tickets, the gauge and the owned marks as they were, and catches up at the end', async () => {
		motion.reduce = false;
		vi.useFakeTimers();
		const { net } = await setup({ box: { tileToken: { value: 5 }, leaf: { value: 0 } } });
		await fireEvent.click(screen.getByRole('button', { name: /1회 뽑기/ }));
		// what the server has done by now: a ticket gone, the gauge up, the cosmetic in the box
		net.box = { tileToken: { value: 4 }, leaf: { value: 1 } };
		result(net, { draws: [{ cell: 0, owned: false }], rewards: [{ type: 'item', name: 'leaf', q: 1 }], gauge: { points: 4, reached: [] } });
		await vi.advanceTimersByTimeAsync(500); // in the middle of the run
		expect(screen.getByLabelText('티켓 5')).toBeTruthy(); // not 4
		expect(screen.getByText('3 / 10')).toBeTruthy(); // not 4
		expect(within(board()).queryByText('보유 중')).toBeNull(); // the prize is not given away
		expect(screen.getByRole('button', { name: /건너뛰기/ })).toBeTruthy();
		await vi.advanceTimersByTimeAsync(20_000);
		expect(screen.getByLabelText('티켓 4')).toBeTruthy();
		expect(screen.getByText('4 / 10')).toBeTruthy();
		expect(within(board()).getAllByText('보유 중')).toHaveLength(1);
	});

	it('can be skipped to the end', async () => {
		motion.reduce = false;
		vi.useFakeTimers();
		const { net } = await setup();
		await fireEvent.click(screen.getByRole('button', { name: /1회 뽑기/ }));
		result(net);
		await vi.advanceTimersByTimeAsync(300);
		await fireEvent.click(screen.getByRole('button', { name: /건너뛰기/ }));
		await vi.advanceTimersByTimeAsync(50);
		expect(screen.getByRole('status').textContent).toBe('핑 ×100 당첨!');
	});

	it('takes several results one after another for a multi draw, and shows one screen for all of them', async () => {
		const { net } = await setup();
		await fireEvent.click(screen.getByRole('button', { name: /3회 뽑기/ }));
		result(net, { draws: [{ cell: 1, owned: false }, { cell: 2, owned: false }, { cell: 1, owned: false }], rewards: [ping(700)], gauge: { points: 6, reached: [5] } });
		await vi.waitFor(() => expect(screen.getByText('타일 룰렛 결과와 마일리지 보너스입니다.')).toBeTruthy());
		const sounds = vi.mocked(playSound).mock.calls.map((c) => c[0]);
		expect(sounds.filter((s) => s === 'success')).toHaveLength(3); // a stop for each draw
	});
});

describe('an event shown as one stage', () => {
	it('keeps the page around it: the list tabs stay, and the pane has the common header with the wave title and the counters', async () => {
		await setup();
		expect(screen.getByRole('tab', { name: /^전체/ })).toBeTruthy();
		expect(screen.getByRole('heading', { level: 2, name: '타일 룰렛' })).toBeTruthy();
		expect(screen.getByRole('timer', { name: '남은 시간' })).toBeTruthy(); // how long is left, always at the top right
		expect(within(screen.getByLabelText('내 진행 상황')).getByLabelText('티켓 5')).toBeTruthy(); // the tickets as the event's currency counter
		expect(screen.getByRole('button', { name: '목록으로' })).toBeTruthy();
	});

	it('has no header of its own: the clock, the counters and the buttons are laid over the pane, not in a bar', async () => {
		await setup();
		expect(document.querySelector('article > header')).toBeNull();
		const timer = screen.getByRole('timer', { name: '남은 시간' });
		expect(timer.closest('header')).toBeNull();
		expect(timer.parentElement.className).toContain('flex-wrap'); // one row that wraps, not a row for each
	});

	it('gives the clock, the counters and the buttons one size and shape, apart from their colours', async () => {
		await setup();
		const items = [screen.getByRole('timer', { name: '남은 시간' }), screen.getByLabelText('티켓 5'), screen.getByRole('button', { name: '목록으로' })];
		for (const el of items) expect(el.className).toContain('kk-hdr');
		expect(items[0].className).toContain('kk-hdr-time'); // the dark one with a fixed width
		expect(items[1].className).toContain('kk-hdr-count');
	});

	it('draws the title as a gradient fill over a separate outline, so the outline stays whole', async () => {
		await setup();
		const title = screen.getByRole('heading', { level: 2, name: '타일 룰렛' });
		const letter = title.querySelector('.wave');
		expect(letter.querySelector('.o')).toBeTruthy(); // the outline layer
		expect(letter.querySelector('.f')).toBeTruthy(); // the fill layer
		expect(title.querySelectorAll('.wave')).toHaveLength([...'타일 룰렛'].length);
	});

	it('is one page: no sub-tabs of its own, only the tabs of the list', async () => {
		await setup();
		expect(screen.queryByRole('tab', { name: /^안내/ })).toBeNull();
		expect(screen.getByRole('list', { name: '타일 룰렛 판' })).toBeTruthy();
	});

	it('goes back to the list from the title bar', async () => {
		await setup();
		await fireEvent.click(screen.getByRole('button', { name: '목록으로' }));
		await tick();
		expect(screen.queryByRole('list', { name: '타일 룰렛 판' })).toBeNull();
	});

	it('can opt out of the stage: the board then sits in the page like any other block', async () => {
		await setup({ presentation: { stage: false } });
		expect(screen.getByRole('list', { name: '타일 룰렛 판' })).toBeTruthy();
		expect(screen.getByRole('timer', { name: '남은 시간' })).toBeTruthy(); // the header is the same for every event
	});

	it('has the text art title on the arcade backdrop', async () => {
		await setup();
		expect(document.querySelector('.kk-arcade')).toBeTruthy();
		expect(screen.getByRole('heading', { level: 2, name: '타일 룰렛' }).className).toContain('kk-fire-text');
	});

	it('puts the text and the exchange behind the header buttons, each in a window of its own', async () => {
		const withParts = { description: '이벤트 설명입니다.', presentation: { blocks: [{ type: 'text', title: '룰렛 안내', body: '티켓 한 장으로 한 번 돌립니다.' }, { type: 'tileRoulette' }, { type: 'exchange' }] } };
		const { net } = await setup();
		applyPacket(net, { type: 'event.progress', event: { list: [{ ...tileEvent(), ...withParts }] }, missions: [], attendance: [] });
		applyPacket(net, { type: 'event.exchangeData', exchange: { tile: [{ id: 'x1', name: '티켓 한 장', cost: { ping: 100 }, gives: { tileToken: 1 }, buyLimit: 0, stockLimit: 0 }] } });
		await tick();
		expect(screen.queryByText('티켓 한 장으로 한 번 돌립니다.')).toBeNull();
		await fireEvent.click(screen.getByRole('button', { name: '행사 정보' }));
		const info = screen.getByRole('dialog', { name: '행사 정보', hidden: true });
		expect(within(info).getByText('티켓 한 장으로 한 번 돌립니다.')).toBeTruthy();
		await fireEvent.keyDown(info, { key: 'Escape' });
		await fireEvent.click(screen.getByRole('button', { name: '교환소' }));
		expect(within(screen.getByRole('dialog', { name: '교환소', hidden: true })).getByText('티켓 한 장')).toBeTruthy();
	});
});
