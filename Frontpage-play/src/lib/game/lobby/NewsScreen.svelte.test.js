import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import NewsScreen from './NewsScreen.svelte';

vi.mock('../ui/confirm.svelte.js', () => ({ confirm: vi.fn(() => Promise.resolve(true)) }));
vi.mock('../shop.js', async (original) => ({ ...(await original()), loadGoodDetails: () => Promise.resolve({ boost: { name: '부스트' } }) }));
afterEach(cleanup);

const NOW = Date.now();
const H = 3_600_000;
const events = [
	{
		id: 'daily', name: '데일리 런', bulletin: false, permanent: false, start: NOW - H, until: NOW + 30 * H,
		description: '매일 한 판!',
		presentation: {
			summary: '매일 한 판만 해도 선물!', banner: 'javascript:alert(1)', accent: '#22c55e', rewards: [{ type: 'ping', q: 100 }, { type: 'item', name: 'boost', q: 2 }],
			blocks: [
				{ type: 'text', title: '참여 방법', body: '게임을 완료하세요.' },
				{ type: 'rewardTrack', steps: [{ label: '1', rewards: [{ type: 'ping', q: 3000 }] }, { label: '2', rewards: [{ type: 'item', name: 'boost', q: 1 }] }] },
				{ type: 'missions' }, { type: 'attendance' }, { type: 'exchange' }
			]
		},
		missions: [{ id: 'm1', name: '세 판 하기', metric: 'game.play', target: 3, period: 'daily', rewards: [{ type: 'ping', q: 100 }] }],
		attendance: { TYPE: '7', DAILY_REWARDS: [{ day: 1, rewards: [{ type: 'ping', q: 100 }] }] }
	},
	{ id: 'soon', name: '아직 안 열린 행사', bulletin: false, permanent: false, start: NOW + 48 * H, until: NOW + 96 * H },
	{ id: 'old', name: '지난 행사', bulletin: false, permanent: false, start: NOW - 90 * H, until: NOW - 60 * H },
	{ id: 'maint', name: '점검 안내', bulletin: true, permanent: false, start: NOW - H, until: NOW + 5 * H }
];

// a tab may carry a red count after its name ("출석 체크1"), so a tab is found by how its name begins
const pickTab = (name) => fireEvent.click(screen.getByRole('tab', { name: new RegExp(`^${name}`) }));
function setup({ progress = { missions: [], attendance: [] }, exchange = {} } = {}) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	net.users.me = { id: 'me', name: '나', money: 1000 };
	applyPacket(net, { type: 'event.progress', event: { list: events }, ...progress });
	applyPacket(net, { type: 'event.exchangeData', exchange });
	const sent = [];
	store.conn.send = (type, data) => sent.push({ type, ...data });
	render(NewsScreen, { store });
	return { store, net, sent };
}

describe('NewsScreen', () => {
	it('asks for the catalog and lists events and notices as cards in one list, going ones first, each saying what kind it is', async () => {
		const { sent } = setup();
		expect(sent.map((s) => s.type)).toEqual(['event.getProgress', 'event.getExchanges']);
		const names = screen.getAllByRole('button', { pressed: false }).map((b) => b.textContent);
		expect(names.findIndex((n) => n.includes('데일리 런'))).toBeLessThan(names.findIndex((n) => n.includes('지난 행사')));
		expect(screen.getByText('점검 안내')).toBeTruthy();
		expect(screen.getAllByRole('list', { name: '공지·행사' })).toHaveLength(1); // not cut up by kind
		const notice = screen.getByRole('button', { name: /점검 안내/ });
		expect(notice.textContent).toContain('공지');
		expect(screen.getByRole('button', { name: /데일리 런/ }).textContent).toContain('행사');
		expect(screen.getByText('매일 한 판만 해도 선물!')).toBeTruthy();
	});

	it('gives a card with no picture of its own the default one, never an empty plate', () => {
		setup();
		const picture = screen.getByRole('button', { name: /점검 안내/ }).querySelector('img');
		expect(picture?.getAttribute('src')).toContain('event/default-card.png');
	});

	it('never draws an address that is not https or a path of this site', () => {
		setup();
		expect(document.querySelector('img[src^="javascript"]')).toBeNull();
	});

	it('filters between events and notices', async () => {
		setup();
		await fireEvent.click(screen.getByRole('tab', { name: /^공지/ }));
		expect(screen.queryByText('데일리 런')).toBeNull();
		expect(screen.getByText('점검 안내')).toBeTruthy();
		await fireEvent.click(screen.getByRole('tab', { name: /^행사/ }));
		expect(screen.getByText('데일리 런')).toBeTruthy();
		expect(screen.queryByText('점검 안내')).toBeNull();
	});

	it('keeps the list tabs and the usual header for an ordinary event: not a stage', async () => {
		setup();
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		expect(screen.getByRole('tab', { name: /^전체/ })).toBeTruthy();
		expect(screen.getAllByRole('tab', { name: /^안내|^미션/ }).length).toBeGreaterThan(0); // the usual sub-tabs of an event with several parts
	});

	it('puts the clock, the counters and the buttons of an ordinary event in one row of one size', async () => {
		setup();
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		const header = document.querySelector('article > header');
		expect(header).toBeTruthy();
		const row = screen.getByRole('timer', { name: '남은 시간' }).parentElement;
		expect(row.closest('header')).toBe(header);
		const kids = [...row.querySelectorAll('.kk-hdr')];
		expect(kids.length).toBeGreaterThanOrEqual(4); // the clock, counters, 행사 정보 and 교환소
		expect(kids.every((el) => el.className.includes('kk-hdr'))).toBe(true);
		expect(screen.getByRole('button', { name: '행사 정보' }).className).toContain('kk-hdr');
		expect(screen.getByRole('button', { name: '교환소' }).className).toContain('kk-hdr');
	});

	it('opens the detail with its blocks and goes back to the list', async () => {
		setup();
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		expect(screen.getByRole('heading', { level: 2, name: '데일리 런' })).toBeTruthy();
		expect(screen.getByText('세 판 하기')).toBeTruthy(); // the first main part, the missions
		expect(screen.queryByText('참여 방법')).toBeNull(); // the text waits behind 행사 정보
		await fireEvent.click(screen.getByRole('button', { name: '행사 정보' }));
		expect(within(screen.getByRole('dialog', { name: '행사 정보', hidden: true })).getByText('참여 방법')).toBeTruthy();
		await fireEvent.click(screen.getByRole('button', { name: '목록으로' }));
		expect(screen.queryByRole('heading', { level: 2, name: '데일리 런' })).toBeNull();
	});

	it('lets a finished mission be claimed once, and only a finished one', async () => {
		const { sent, net } = setup({ progress: { missions: [{ eventId: 'daily', missionId: 'm1', periodKey: 'd', progress: 1, target: 3, claimed: false }], attendance: [] } });
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		await pickTab('미션');
		const claim = screen.getByRole('button', { name: '보상 받기' });
		expect(claim.disabled).toBe(true);
		applyPacket(net, { type: 'event.progress', event: { list: events }, missions: [{ eventId: 'daily', missionId: 'm1', periodKey: 'd', progress: 3, target: 3, claimed: false }], attendance: [] });
		await tick();
		const ready = screen.getByRole('button', { name: '보상 받기' });
		expect(ready.disabled).toBe(false);
		await fireEvent.click(ready);
		expect(sent.at(-1)).toEqual({ type: 'event.claimMission', eventId: 'daily', missionId: 'm1' });
		expect(screen.getByRole('button', { name: '보상 받기' }).disabled).toBe(true); // waits for the answer
		applyPacket(net, { type: 'event.progress', event: { list: events }, missions: [{ eventId: 'daily', missionId: 'm1', periodKey: 'd', progress: 3, target: 3, claimed: true }], attendance: [] });
		await tick();
		expect(screen.getByRole('button', { name: '받음' }).disabled).toBe(true);
	});

	it('checks in once a day', async () => {
		const { sent, net } = setup({ progress: { missions: [], attendance: [{ eventId: 'daily', todayClaimed: false, attended: 2, streak: 2 }] } });
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		await pickTab('출석 체크');
		expect(screen.getByText('누적 2일')).toBeTruthy();
		await fireEvent.click(screen.getByRole('button', { name: '출석하기' }));
		expect(sent.at(-1)).toEqual({ type: 'event.claimAttendance', eventId: 'daily' });
		applyPacket(net, { type: 'event.progress', event: { list: events }, missions: [], attendance: [{ eventId: 'daily', todayClaimed: true, attended: 3, streak: 3 }] });
		await tick();
		expect(screen.getByRole('button', { name: '오늘 출석 완료' }).disabled).toBe(true);
	});

	it('shows the attendance as a board of day tiles and reads what a picked day gives', async () => {
		setup({ progress: { missions: [], attendance: [{ eventId: 'daily', todayClaimed: false, attended: 0, streak: 0 }] } });
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		await pickTab('출석 체크');
		expect(screen.getAllByRole('button', { name: /^\d+일차$/ })).toHaveLength(7);
		expect(screen.getByLabelText('1일차 보상 구성')).toBeTruthy(); // today's day is the one read at first
		await fireEvent.click(screen.getByRole('button', { name: '3일차' }));
		expect(screen.getByLabelText('3일차 보상 구성')).toBeTruthy();
		expect(screen.getByText('이 날은 보상이 없습니다.')).toBeTruthy(); // the sample book gives something on day 1 only
	});

	it('asks before it exchanges, showing what is given, what is got and what is left; only what I can pay', async () => {
		const { sent } = setup({ exchange: { daily: [{ id: 'x1', name: '부스트 교환', cost: { ping: 10 }, gives: { boost: 1 }, buyLimit: 1, stockLimit: 0 }, { id: 'x2', name: '비싼 교환', cost: { ping: 99999 }, gives: { boost: 1 }, buyLimit: 0, stockLimit: 0 }] } });
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		await fireEvent.click(screen.getByRole('button', { name: '교환소' }));
		const shopWindow = screen.getByRole('dialog', { name: '교환소', hidden: true });
		expect(within(shopWindow).getByRole('button', { name: /비싼 교환/, hidden: true }).disabled).toBe(true);
		await fireEvent.click(within(shopWindow).getByRole('button', { name: /부스트 교환/, hidden: true }));
		await tick();
		expect(sent.at(-1).type).not.toBe('event.exchange'); // nothing is spent before the answer
		const dialog = screen.getByRole('dialog', { name: '교환하기', hidden: true });
		expect(within(dialog).getByLabelText('교환 후 남는 양').textContent).toContain('1,000'); // I have 1,000 핑
		expect(within(dialog).getByLabelText('교환 후 남는 양').textContent).toContain('990'); // and 990 stay after paying 10
		await fireEvent.click(within(dialog).getByRole('button', { name: '교환', hidden: true }));
		expect(sent.at(-1)).toEqual({ type: 'event.exchange', eid: 'daily', id: 'x1' });
	});

	it('gives the missions a tab per period with the count of what can be claimed, and ready ones first', async () => {
		const withPeriods = events.map((e) => (e.id === 'daily' ? { ...e, missions: [
			{ id: 'm1', name: '세 판 하기', metric: 'game.play', target: 3, period: 'daily', rewards: [{ type: 'ping', q: 100 }] },
			{ id: 'm2', name: '한 판 이기기', metric: 'game.win', target: 1, period: 'daily', rewards: [{ type: 'ping', q: 50 }] },
			{ id: 'm3', name: '주간 백 판', metric: 'game.play', target: 100, period: 'weekly', rewards: [{ type: 'ping', q: 900 }] }
		] } : e));
		const { net } = setup();
		applyPacket(net, { type: 'event.progress', event: { list: withPeriods }, missions: [{ eventId: 'daily', missionId: 'm2', progress: 1, target: 1, claimed: false }, { eventId: 'daily', missionId: 'm1', progress: 1, target: 3, claimed: false }], attendance: [] });
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		await pickTab('미션');
		expect(screen.getByRole('tab', { name: /^일일/ }).textContent).toContain('1'); // m2 is ready
		expect(screen.getByRole('tab', { name: /^주간/ }).textContent).toBe('주간');
		const names = screen.getAllByRole('progressbar').map((b) => b.getAttribute('aria-label'));
		expect(names).toEqual(['한 판 이기기', '세 판 하기']); // the ready one first
		expect(screen.queryByText('주간 백 판')).toBeNull();
		await fireEvent.click(screen.getByRole('tab', { name: /^주간/ }));
		expect(screen.getByText('주간 백 판')).toBeTruthy();
	});

	it('puts a red count on the sub-tabs that wait for me and shows my progress in the header counters', async () => {
		setup({ progress: { missions: [{ eventId: 'daily', missionId: 'm1', progress: 3, target: 3, claimed: false }], attendance: [{ eventId: 'daily', todayClaimed: false, attended: 2, streak: 2 }] } });
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		expect(screen.getByRole('tab', { name: /^미션/ }).textContent).toBe('미션1');
		expect(screen.getByRole('tab', { name: /^출석 체크/ }).textContent).toBe('출석 체크1');
		const counters = screen.getByLabelText('내 진행 상황'); // the counters at the top right of the header
		expect(counters.textContent).toContain('1/1');
		expect(counters.textContent).toContain('출석 2일');
		expect(counters.textContent).toContain('받을 수 있는 보상 1');
		expect(screen.getByRole('timer', { name: '남은 시간' }).textContent).toMatch(/\d{2}:\d{2}:\d{2}/);
	});

	it('marks the reward steps reached when the track says where its progress comes from', async () => {
		const withTrack = events.map((e) => (e.id === 'daily' ? { ...e, presentation: { ...e.presentation, blocks: [{ type: 'rewardTrack', source: 'attendance', steps: [1, 2, 3].map((n) => ({ label: String(n), rewards: [{ type: 'ping', q: n * 100 }] })) }] } } : e));
		const { net } = setup();
		applyPacket(net, { type: 'event.progress', event: { list: withTrack }, missions: [], attendance: [{ eventId: 'daily', todayClaimed: true, attended: 2, streak: 2 }] });
		await fireEvent.click(screen.getByRole('button', { name: /데일리 런/ }));
		expect(screen.getAllByText('완료')).toHaveLength(2);
		const current = document.querySelector('[aria-current="step"]');
		expect(current?.textContent).toContain('3');
	});

	it('shows what a claim or an exchange gave on the game\'s own "you got these" screen', async () => {
		const { net } = setup();
		await tick();
		applyPacket(net, { type: 'event.reward', eventId: 'daily', source: 'exchange', rewards: [{ type: 'ping', q: 100 }, { type: 'item', name: 'boost', q: 2 }] }, Date.now() + 1);
		await tick();
		await tick();
		expect(screen.getByText('교환을 마쳤습니다.', { exact: false, ignore: 'script' })).toBeTruthy();
	});

	it('does not show an event that has not begun', () => {
		setup();
		expect(screen.queryByText('아직 안 열린 행사')).toBeNull();
		expect(screen.getByRole('tab', { name: /^행사/ }).textContent).toBe('행사2'); // 데일리 런 and 지난 행사; with the hidden one it would be 3
	});

	it('marks the menu entry as read when opened', () => {
		const { store } = setup();
		expect(store.newsUnseen).toBe(0);
	});
});

describe('NewsScreen on a wide page', () => {
	beforeEach(() => {
		// jsdom has no layout: say that the page is 1000px wide
		vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(1000);
		vi.stubGlobal('ResizeObserver', class { constructor(cb) { this.cb = cb; } observe() { queueMicrotask(() => this.cb([])); } unobserve() {} disconnect() {} });
	});
	afterEach(() => {
		vi.restoreAllMocks();
		vi.unstubAllGlobals();
	});

	const open = async (name) => {
		await tick();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name }));
	};
	const rail = () => screen.getByRole('navigation', { name: '공지·행사' });

	it('starts on the overview, and opening an entry folds the list into a rail beside the detail', async () => {
		setup();
		await tick();
		await tick();
		expect(screen.queryByRole('navigation', { name: '공지·행사' })).toBeNull(); // nothing open: the cards fill the page
		expect(screen.getByText('매일 한 판만 해도 선물!')).toBeTruthy();
		await open(/데일리 런/);
		expect(screen.getByRole('heading', { level: 2, name: '데일리 런' })).toBeTruthy();
		expect(within(rail()).getByRole('button', { name: /데일리 런/ }).getAttribute('aria-pressed')).toBe('true');
		expect(within(rail()).getByText('점검 안내')).toBeTruthy();
		expect(screen.queryByRole('button', { name: '목록으로' })).toBeNull(); // the list is already there
		expect(screen.queryAllByText('매일 한 판만 해도 선물!')).toHaveLength(1); // the card is gone; only the header's line is left
	});

	it('goes back to the overview from the rail', async () => {
		setup();
		await open(/데일리 런/);
		await fireEvent.click(within(rail()).getByRole('button', { name: '전체 보기' }));
		expect(screen.queryByRole('navigation', { name: '공지·행사' })).toBeNull();
		expect(screen.getByText('매일 한 판만 해도 선물!')).toBeTruthy();
	});

	it('keeps the rail free of pictures: a bar of the event colour, the name and the state', async () => {
		setup();
		await open(/데일리 런/);
		expect(rail().querySelector('img')).toBeNull();
	});

	it('shows what is over in a group that starts folded', async () => {
		setup();
		await open(/데일리 런/);
		expect(within(rail()).queryByText('지난 행사')).toBeNull();
		await fireEvent.click(within(rail()).getByRole('button', { name: '종료 목록 펼치기·접기' }));
		expect(within(rail()).getByText('지난 행사')).toBeTruthy();
	});

	it('switches the detail from the rail, and marks an entry that ends within a day', async () => {
		setup();
		await open(/데일리 런/);
		expect(within(rail()).getAllByText('곧 종료').length).toBeGreaterThan(0); // the 점검 안내 notice ends in 5 hours
		await fireEvent.click(within(rail()).getByRole('button', { name: /점검 안내/ }));
		expect(screen.getByRole('heading', { level: 2, name: '점검 안내' })).toBeTruthy();
	});

	it('uses the same tabs as the shop; a tab that does not list the open entry goes back to the overview', async () => {
		setup();
		await open(/데일리 런/);
		await fireEvent.click(screen.getByRole('tab', { name: /^공지/ }));
		await tick();
		expect(screen.queryByRole('navigation', { name: '공지·행사' })).toBeNull();
		expect(screen.queryByText('데일리 런')).toBeNull();
		expect(screen.getByText('점검 안내')).toBeTruthy();
	});
});

describe('responsive rules of the news page', () => {
	const sources = import.meta.glob(['./NewsScreen.svelte', './NewsCard.svelte', './NewsRow.svelte', './NewsAttendance.svelte', './NewsExchange.svelte', './NewsExchangeDialog.svelte', './NewsMissions.svelte', './NewsTrack.svelte', './NewsTile.svelte', './NewsGauge.svelte', './NewsCampaign.svelte', './RewardBundle.svelte', './NewsDetail.svelte', './RewardChip.svelte'], { query: '?raw', import: 'default', eager: true });
	const files = Object.entries(sources);
	it('follows the width of its own container, never the window (the side bar and the chat change it)', () => {
		for (const [name, source] of files) {
			expect(source.match(/(^|[\s"'`{])(sm|md|lg|xl|2xl):[a-z[]/g), `${name} uses a window breakpoint`).toBeNull();
		}
	});
	it('lets the browser pick the number of card columns', () => {
		expect(sources['./NewsScreen.svelte']).toContain('grid-cols-[repeat(auto-fill,minmax(min(100%,17rem),1fr))]');
		expect(sources['./NewsScreen.svelte']).toContain('@container');
	});
});
