import { describe, expect, it } from 'vitest';
import { newsAlerts, letterId, howTo, roundsToday, hmsText, representative, stepFill, campaignView, clockText, isCampaign, sectionOf, isStage, attendanceDone, endingSoon, finishedCount, missionEntry, missionStatus, periodsOf, readyCount, trackCount, trackStep, attendanceLength, eventPhase, filterNews, limitList, loadSeen, newsNudge, NUDGE_PLACEHOLDER, orderNews, ratio, rewardOf, rewardsOfMap, safeAccent, safeUrl, saveSeen, timeLeft, unseenNews } from './news.js';

const H = 3_600_000;
const NOW = Date.parse('2026-11-07T12:00:00+09:00');
const ev = (id, start, until, extra = {}) => ({ id, name: id, start: NOW + start * H, until: NOW + until * H, bulletin: false, permanent: false, ...extra });

describe('phase', () => {
	it('is going between start and until, upcoming before, ended after', () => {
		expect(eventPhase(ev('a', -1, 1), NOW)).toBe('ongoing');
		expect(eventPhase(ev('a', 1, 2), NOW)).toBe('upcoming');
		expect(eventPhase(ev('a', -2, -1), NOW)).toBe('ended');
		expect(eventPhase(ev('a', -2, 0), NOW)).toBe('ended'); // the end itself is over
	});
	it('a permanent event is always going', () => {
		expect(eventPhase(ev('a', -9, -8, { permanent: true }), NOW)).toBe('ongoing');
	});
});

describe('endingSoon', () => {
	it('marks only a going, time-limited event that ends within a day', () => {
		expect(endingSoon(ev('a', -1, 5), NOW)).toBe(true);
		expect(endingSoon(ev('a', -1, 24), NOW)).toBe(true);
		expect(endingSoon(ev('a', -1, 25), NOW)).toBe(false);
		expect(endingSoon(ev('a', -1, 5, { permanent: true }), NOW)).toBe(false);
		expect(endingSoon(ev('a', 1, 5), NOW)).toBe(false);
		expect(endingSoon(ev('a', -9, -1), NOW)).toBe(false);
	});
});

describe('timeLeft', () => {
	it('splits into days, hours and minutes, rounded up to the minute', () => {
		expect(timeLeft(NOW + 26 * H + 61_000, NOW)).toEqual({ days: 1, hours: 2, minutes: 2, over: false });
		expect(timeLeft(NOW + 5 * 60_000, NOW)).toMatchObject({ days: 0, hours: 0, minutes: 5 });
	});
	it('never goes negative', () => {
		expect(timeLeft(NOW - H, NOW)).toEqual({ days: 0, hours: 0, minutes: 0, over: true });
	});
});

describe('orderNews', () => {
	it('puts what ends soonest first, permanent ones after, then upcoming, then ended', () => {
		const list = [ev('ended-old', -50, -40), ev('perm', -5, 0, { permanent: true }), ev('later', -1, 50), ev('soon', -1, 2), ev('up2', 10, 20), ev('up1', 3, 9), ev('ended-new', -10, -2)];
		expect(orderNews(list, NOW).map((e) => e.id)).toEqual(['soon', 'later', 'perm', 'up1', 'up2', 'ended-new', 'ended-old']);
	});
	it('keeps the server order for ties and does not change its input', () => {
		const list = [ev('b', -1, 5), ev('a', -1, 5)];
		expect(orderNews(list, NOW).map((e) => e.id)).toEqual(['b', 'a']);
		expect(list.map((e) => e.id)).toEqual(['b', 'a']);
	});
});

describe('filter', () => {
	const list = [ev('e', -1, 1), ev('n', -1, 1, { bulletin: true })];
	it('splits notices (bulletin) from events', () => {
		expect(filterNews(list, 'all')).toHaveLength(2);
		expect(filterNews(list, 'event').map((e) => e.id)).toEqual(['e']);
		expect(filterNews(list, 'notice').map((e) => e.id)).toEqual(['n']);
	});
});

describe('safe values', () => {
	it('lets only https addresses and paths of this site through', () => {
		for (const ok of ['https://kkutu.io/a.png', '/img/event/a.png']) expect(safeUrl(ok)).toBe(ok);
		for (const bad of ['javascript:alert(1)', '//evil.example/a', 'http://kkutu.io/a', 'data:image/png;base64,AA', 'relative.png', '', null, undefined, 5]) expect(safeUrl(bad)).toBe('');
	});
	it('takes only #rrggbb as an accent', () => {
		expect(safeAccent('#22c55e')).toBe('#22c55e');
		for (const bad of ['red', '#fff', '#22c55e;x', 'url(x)', null]) expect(safeAccent(bad)).toBe('');
	});
});

describe('rewards', () => {
	it('maps the server rewards to what a chip draws', () => {
		expect(rewardOf({ type: 'ping', q: 100 })).toEqual({ kind: 'ping', q: 100 });
		expect(rewardOf({ type: 'ep', q: 5 })).toEqual({ kind: 'ep', q: 5 });
		expect(rewardOf({ type: 'item', name: 'boost', q: 2 })).toEqual({ kind: 'item', id: 'boost', q: 2 });
	});
	it('reads an exchange map, where ping is a key', () => {
		expect(rewardsOfMap({ ping: 10, PIECE_A: 3 })).toEqual([{ kind: 'ping', q: 10 }, { kind: 'item', id: 'PIECE_A', q: 3 }]);
		expect(rewardsOfMap(undefined)).toEqual([]);
	});
	it('limits a row and counts what was left out', () => {
		expect(limitList([1, 2, 3, 4], 3)).toEqual({ shown: [1, 2, 3], more: 1 });
		expect(limitList([1], 3)).toEqual({ shown: [1], more: 0 });
	});
});

describe('missions and attendance', () => {
	it('clamps progress to 0..1', () => {
		expect(ratio(1, 4)).toBe(0.25);
		expect(ratio(9, 4)).toBe(1);
		expect(ratio(-1, 4)).toBe(0);
		expect(ratio(1, 0)).toBe(0);
	});
	it('counts the days done in a book and starts over after its length', () => {
		expect(attendanceLength('7')).toBe(7);
		expect(attendanceLength('calendar')).toBe(31);
		expect(attendanceDone('7', 0)).toBe(0);
		expect(attendanceDone('7', 3)).toBe(3);
		expect(attendanceDone('7', 7)).toBe(7);
		expect(attendanceDone('7', 8)).toBe(1);
		expect(attendanceDone('7', 14)).toBe(7);
	});
});

describe('seen', () => {
	const memory = () => {
		const data = {};
		return { getItem: (k) => data[k] ?? null, setItem: (k, v) => (data[k] = v) };
	};
	it('remembers what was seen and counts only going events that are new', () => {
		const storage = memory();
		saveSeen(new Set(['a']), storage);
		const seen = loadSeen(storage);
		expect([...seen]).toEqual(['a']);
		const list = [ev('a', -1, 1), ev('b', -1, 1), ev('c', 2, 3), ev('d', -9, -8)];
		expect(unseenNews(list, seen, NOW).map((e) => e.id)).toEqual(['b']);
	});
	it('survives damaged or blocked storage', () => {
		expect([...loadSeen({ getItem: () => '{not json' })]).toEqual([]);
		expect([...loadSeen({ getItem: () => { throw new Error('blocked'); } })]).toEqual([]);
		expect(() => saveSeen(new Set(['a']), { setItem: () => { throw new Error('blocked'); } })).not.toThrow();
	});
});

describe('newsNudge', () => {
	const seen = new Set();
	it('announces the first going event nobody has seen, never a notice', () => {
		const list = [ev('note', -1, 5, { bulletin: true }), ev('later', -1, 50), ev('soon', -1, 2)];
		expect(newsNudge(list, seen, NOW)).toMatchObject({ id: 'soon', name: 'soon' });
	});
	it('says nothing when everything is seen, upcoming or ended', () => {
		expect(newsNudge([ev('a', -1, 5)], new Set(['a']), NOW)).toBeNull();
		expect(newsNudge([ev('up', 2, 5), ev('old', -9, -5)], seen, NOW)).toBeNull();
		expect(newsNudge([], seen, NOW)).toBeNull();
	});
	it("uses the event's own picture when it is safe, the placeholder otherwise", () => {
		expect(newsNudge([ev('a', -1, 5, { presentation: { nudge: '/img/event/a.png' } })], seen, NOW).image).toBe('/img/event/a.png');
		expect(newsNudge([ev('a', -1, 5, { presentation: { nudge: 'javascript:alert(1)' } })], seen, NOW).image).toBe(NUDGE_PLACEHOLDER);
		expect(newsNudge([ev('a', -1, 5)], seen, NOW).image).toBe(NUDGE_PLACEHOLDER);
	});
});

describe('missions', () => {
	const m = (id, target, period) => ({ id, target, rewards: [{ type: 'ping', q: 1 }], ...(period ? { period } : {}) });
	const progress = { missions: [
		{ eventId: 'e', missionId: 'a', progress: 3, target: 3, claimed: false },
		{ eventId: 'e', missionId: 'b', progress: 1, target: 2, claimed: false },
		{ eventId: 'e', missionId: 'c', progress: 5, target: 5, claimed: true },
		{ eventId: 'other', missionId: 'a', progress: 9, target: 3, claimed: true }
	], attendance: [{ eventId: 'e', attended: 9, streak: 2, todayClaimed: true }] };
	const missions = [m('a', 3), m('b', 2, 'weekly'), m('c', 5, 'daily'), m('d', 1, 'event')];

	it('knows where a mission is', () => {
		expect(missionStatus(m('a', 3), { progress: 3 })).toBe('ready');
		expect(missionStatus(m('a', 3), { progress: 2 })).toBe('progress');
		expect(missionStatus(m('a', 3), undefined)).toBe('progress');
		expect(missionStatus(m('a', 3), { progress: 3, claimed: true })).toBe('claimed');
	});
	it('counts a finished mission that gives nothing of its own as done, with nothing to take', () => {
		const lesson = { id: 'l', target: 1, rewards: [] };
		expect(missionStatus(lesson, { progress: 1 })).toBe('claimed');
		expect(missionStatus(lesson, { progress: 0 })).toBe('progress');
	});
	it('reads the entry of this event only', () => {
		expect(missionEntry(progress, 'e', 'a').progress).toBe(3);
		expect(missionEntry(progress, 'e', 'zzz')).toBeUndefined();
	});
	it('lists the periods in use in the usual order', () => {
		expect(periodsOf(missions)).toEqual(['daily', 'weekly', 'event']);
		expect(periodsOf([m('x', 1, 'weekly')])).toEqual(['weekly']);
		expect(periodsOf([])).toEqual([]);
	});
	it('counts what waits to be claimed and what is finished', () => {
		expect(readyCount(missions, progress, 'e')).toBe(1); // a
		expect(readyCount(missions, progress, 'e', 'weekly')).toBe(0);
		expect(readyCount(missions, progress, 'e', 'daily')).toBe(1);
		expect(finishedCount(missions, progress, 'e')).toBe(2); // a (ready) and c (claimed)
	});
	it('places the player on a reward track', () => {
		const event = { id: 'e', missions, attendance: { TYPE: '7' } };
		expect(trackCount('attendance', event, progress)).toBe(2); // 9 check-ins on a 7-day book
		expect(trackCount('missions', event, progress)).toBe(2);
		expect(trackCount(undefined, event, progress)).toBe(-1);
		expect([0, 1, 2, 3].map((i) => trackStep(2, i))).toEqual(['done', 'done', 'current', 'todo']);
		expect(trackStep(-1, 0)).toBe('plain');
		expect(trackStep(0, 0)).toBe('current');
	});
});

describe('isStage', () => {
	it('is on for a tile roulette unless the event says otherwise, and off for the rest unless it says so', () => {
		expect(isStage({ tile: { cells: [] } })).toBe(true);
		expect(isStage({ tile: {}, presentation: { stage: false } })).toBe(false);
		expect(isStage({})).toBe(false);
		expect(isStage({ presentation: { stage: true } })).toBe(true);
		expect(isStage({ presentation: { summary: 'x' } })).toBe(false);
		expect(isStage(undefined)).toBe(false);
	});
});

describe('campaign sections', () => {
	const camp = ev('c', -1, 9, { campaign: { tiers: [] } });
	const plain = ev('e', -1, 9);
	const note = ev('n', -1, 9, { bulletin: true });
	it('files an entry under 캠페인, 공지 or 행사', () => {
		expect([camp, plain, note].map(sectionOf)).toEqual(['campaign', 'event', 'notice']);
		expect(isCampaign(camp)).toBe(true);
		expect(isCampaign(plain)).toBe(false);
	});
	it('filters by section, and a campaign is not an event', () => {
		const list = [camp, plain, note];
		expect(filterNews(list, 'campaign')).toEqual([camp]);
		expect(filterNews(list, 'event')).toEqual([plain]);
		expect(filterNews(list, 'notice')).toEqual([note]);
		expect(filterNews(list, 'all')).toHaveLength(3);
	});
	it('is a stage unless it says otherwise, and is announced before an event', () => {
		expect(isStage(camp)).toBe(true);
		expect(isStage({ ...camp, presentation: { stage: false } })).toBe(false);
		expect(newsNudge([plain, camp], new Set(), NOW).id).toBe('c');
	});
});

describe('campaignView', () => {
	const info = { tiers: [{ points: 100, rewards: [] }, { points: 500, rewards: [] }] };
	const status = (current, extra = {}) => ({ now: NOW, at: NOW, current, past: [], ...extra });
	it('reads the total, my part, the tier reached and the next to reach', () => {
		const v = campaignView(status({ key: 'k', startsAt: NOW - H, endsAt: NOW + H, total: 120, mine: 7, joined: true }), info, NOW);
		expect([v.total, v.mine, v.reached, v.goal?.points, v.left]).toEqual([120, 7, 0, 500, H]);
		expect(v.bar.steps.map((s) => s.reached)).toEqual([true, false]);
	});
	it('has no goal left once every tier is reached, and none reached at the start', () => {
		expect(campaignView(status({ startsAt: 0, endsAt: NOW + 1, total: 900, mine: 0, joined: false }), info, NOW).goal).toBeNull();
		expect(campaignView(status({ startsAt: 0, endsAt: NOW + 1, total: 5, mine: 0, joined: false }), info, NOW).reached).toBe(-1);
	});
	it('counts down to the next hot time when none is on, by the server clock', () => {
		const v = campaignView({ now: NOW + 60_000, at: NOW, past: [], next: { key: 'n', startsAt: NOW + 3_660_000, endsAt: NOW + 7_000_000 } }, info, NOW);
		expect(v.until).toBe(H); // the server is a minute ahead of me
		expect(v.left).toBe(0);
	});
	it('lists only the finished hot times I joined, reached a tier in and have not taken', () => {
		const past = [
			{ key: 'a', joined: true, tier: 0, claimed: false },
			{ key: 'b', joined: true, tier: 0, claimed: true },
			{ key: 'c', joined: false, tier: 1, claimed: false },
			{ key: 'd', joined: true, tier: -1, claimed: false }
		];
		expect(campaignView(status(undefined, { past }), info, NOW).claimable.map((p) => p.key)).toEqual(['a']);
	});
	it('survives no status at all', () => {
		expect(campaignView(undefined, info, NOW)).toMatchObject({ total: 0, mine: 0, reached: -1, left: 0, claimable: [] });
	});
	it('writes a countdown as hours and minutes, or minutes and seconds', () => {
		expect(clockText(3_725_000)).toEqual([1, 2, 'h']);
		expect(clockText(125_000)).toEqual([2, 5, 'm']);
		expect(clockText(-5)).toEqual([0, 0, 'm']);
	});
});

describe('stepFill', () => {
	const tiers = [{ points: 20 }, { points: 60 }, { points: 150 }];
	it('fills each stretch from the step before it, every step as wide as the others', () => {
		expect(stepFill(25, tiers).map((s) => [s.fill, s.reached])).toEqual([[1, true], [0.125, false], [0, false]]);
		expect(stepFill(0, tiers).map((s) => s.fill)).toEqual([0, 0, 0]);
		expect(stepFill(10, tiers)[0].fill).toBe(0.5);
	});
	it('is full and reached everywhere once every step is passed, and never past full', () => {
		expect(stepFill(999, tiers).every((s) => s.fill === 1 && s.reached)).toBe(true);
	});
	it('counts a step as reached on its exact points', () => {
		expect(stepFill(60, tiers).map((s) => s.reached)).toEqual([true, true, false]);
	});
	it('survives no steps and a step as low as the one before', () => {
		expect(stepFill(5, [])).toEqual([]);
		expect(stepFill(5, [{ points: 5 }, { points: 5 }]).map((s) => s.fill)).toEqual([1, 1]);
	});
});

describe('hmsText', () => {
	it('writes hours, minutes and seconds with two digits each, hours past a day included', () => {
		expect(hmsText(11_400_000)).toBe('03:10:00');
		expect(hmsText(35_999_000)).toBe('09:59:59');
		expect(hmsText(90 * 3_600_000)).toBe('90:00:00');
		expect(hmsText(-1)).toBe('00:00:00');
		expect(hmsText(999)).toBe('00:00:00');
	});
});

describe('representative', () => {
	const ping = { kind: 'ping', q: 300 };
	const ep = { kind: 'ep', q: 5 };
	const a = { kind: 'item', id: 'a', q: 3 };
	const b = { kind: 'item', id: 'b', q: 1 };
	it('shows an item, never 핑, while the bundle has one', () => {
		expect(representative([ping, a, b])).toEqual({ main: a, more: 2 });
		expect(representative([b, ping])).toEqual({ main: b, more: 1 });
	});
	it('falls back to event points, and 핑 only when it is all there is', () => {
		expect(representative([ping, ep])).toEqual({ main: ep, more: 1 });
		expect(representative([ping])).toEqual({ main: ping, more: 0 });
	});
	it('has nothing for an empty bundle', () => {
		expect(representative([])).toBeNull();
		expect(representative(undefined)).toBeNull();
	});
});

describe('roundsToday', () => {
	// 2026-11-07 is a Saturday; 13:00 UTC is 22:00 in Seoul
	const at = (h, m = 0) => Date.UTC(2026, 10, 7, h, m);
	const info = { timezone: 'Asia/Seoul', schedule: [{ from: '21:00', to: '22:00' }, { from: '16:00', to: '17:00' }] };
	it('numbers the hot times of the day in order and says where each one is', () => {
		expect(roundsToday(info, at(5)).map((r) => [r.n, r.from, r.state])).toEqual([[1, '16:00', 'next'], [2, '21:00', 'next']]); // 14:00 in Seoul
		expect(roundsToday(info, at(7, 30)).map((r) => r.state)).toEqual(['live', 'next']); // 16:30
		expect(roundsToday(info, at(12, 30)).map((r) => r.state)).toEqual(['past', 'live']); // 21:30
		expect(roundsToday(info, at(13, 0)).map((r) => r.state)).toEqual(['past', 'past']); // 22:00 sharp: over
	});
	it('reads the day in the campaign time zone and keeps a weekday window to its days', () => {
		const days = { timezone: 'Asia/Seoul', schedule: [{ from: '00:00', to: '12:00', days: [1] }, { from: '23:00', to: '24:00' }] };
		expect(roundsToday(days, at(5)).map((r) => r.from)).toEqual(['23:00']); // Saturday there
		expect(roundsToday(days, at(16)).length).toBe(1); // 01:00 on Sunday there
		expect(roundsToday(days, Date.UTC(2026, 10, 8, 16)).length).toBe(2); // 01:00 on Monday there
	});
	it('is empty without a schedule', () => {
		expect(roundsToday({ timezone: 'Asia/Seoul', schedule: [] }, at(5))).toEqual([]);
		expect(roundsToday(undefined, at(5))).toEqual([]);
	});
});

describe('howTo', () => {
	const campaign = { timezone: 'Asia/Seoul', schedule: [], tiers: [], goal: { metric: 'word.accept', points: 1 }, cap: 200 };
	it('explains a campaign by its goal, its cap and who gets the rewards', () => {
		const lines = howTo({ campaign });
		expect(lines.map((l) => l.key)).toEqual(['play.news.how.campaign.pool', 'play.news.how.campaign.earn', 'play.news.how.campaign.cap', 'play.news.how.campaign.tiers', 'play.news.how.campaign.claim']);
		expect(lines[1].args).toEqual({ metric: 'word.accept', n: 1 });
		expect(howTo({ campaign: { ...campaign, cap: undefined, goal: { ...campaign.goal, filtered: true } } }).map((l) => l.key)).toContain('play.news.how.campaign.filtered');
	});
	it('explains a tile roulette by its cost and mileage, and nothing for the rest', () => {
		expect(howTo({ tile: { cost: 1, gauge: { perDraw: 1 } } }).map((l) => l.key)).toEqual(['play.news.how.tile.spend', 'play.news.how.tile.mileage', 'play.news.how.tile.owned']);
		expect(howTo({ tile: { cost: 1 } }).map((l) => l.key)).not.toContain('play.news.how.tile.mileage');
		expect(howTo({})).toEqual([]);
	});
});

describe('events for newcomers and returning players', () => {
	const camp = { id: 'c', campaign: {} };
	const newbie = { id: 'n', audience: { kind: 'newcomer', days: 7 } };
	it('have a section of their own, ahead of the campaign even when they are one', () => {
		expect([newbie, { ...newbie, campaign: {} }, camp].map(sectionOf)).toEqual(['audience', 'audience', 'campaign']);
		expect(filterNews([newbie, camp, { id: 'e' }], 'audience').map((e) => e.id)).toEqual(['n']);
		expect(filterNews([newbie, camp, { id: 'e' }], 'event').map((e) => e.id)).toEqual(['e']);
	});
});

describe('letter pieces as a reward', () => {
	it('are shown by the piece of their tier, whose letter is drawn only when it is given', () => {
		expect(rewardOf({ type: 'letter', tier: 'rare', q: 2 })).toEqual({ kind: 'letter', tier: 'rare', id: '$WPA?', q: 2 });
		expect([letterId('common'), letterId('advanced'), letterId('rare')]).toEqual(['$WPC?', '$WPB?', '$WPA?']);
		expect(representative([{ kind: 'ping', q: 5 }, { kind: 'letter', tier: 'common', id: '$WPC?', q: 3 }]).main.kind).toBe('letter'); // a piece is shown before 핑
	});
	it('explains an invitation', () => {
		expect(howTo({ invite: { days: 7, max: 5, stages: [] } }).map((l) => l.key)).toEqual(['play.news.how.invite.share', 'play.news.how.invite.who', 'play.news.how.invite.stages', 'play.news.how.invite.max']);
	});
});

describe('newsAlerts: what waits for the player', () => {
	const NOWMS = Date.now();
	const HOUR = 3_600_000;
	const ev = (extra) => ({ id: 'e', name: 'E', bulletin: false, permanent: false, start: NOWMS - HOUR, until: NOWMS + 5 * HOUR, ...extra });
	const net = (list, extra = {}) => ({ guest: false, eventsLoaded: true, events: { list }, eventProgress: { missions: [], attendance: [] }, campaigns: {}, invites: {}, ...extra });
	const kinds = (n) => newsAlerts(n, NOWMS).map((a) => a.kind);
	const mission = { id: 'm', target: 1, rewards: [{ type: 'ping', q: 1 }] };

	it('has nothing to say when nothing waits, for a guest, and before the events are known', () => {
		expect(kinds(net([ev({})]))).toEqual([]);
		expect(kinds(net([ev({ missions: [mission] })], { guest: true }))).toEqual([]);
		expect(kinds(net([ev({ attendance: {} })], { eventsLoaded: false }))).toEqual([]);
	});
	it('says a mission is done and not taken, and that today is not checked in', () => {
		const done = { missions: [{ eventId: 'e', missionId: 'm', progress: 1, target: 1, claimed: false }], attendance: [{ eventId: 'e', todayClaimed: false }] };
		expect(kinds(net([ev({ missions: [mission], attendance: {} })], { eventProgress: done }))).toEqual(['missions', 'attendance']);
		const taken = { missions: [{ eventId: 'e', missionId: 'm', progress: 1, target: 1, claimed: true }], attendance: [{ eventId: 'e', todayClaimed: true }] };
		expect(kinds(net([ev({ missions: [mission], attendance: {} })], { eventProgress: taken }))).toEqual([]);
	});
	it('says a hot time is on until I have played in it, and when a hot time has a reward to take', () => {
		const campaign = { timezone: 'Asia/Seoul', schedule: [{ from: '00:00', to: '24:00' }], tiers: [{ points: 1, rewards: [] }] };
		expect(kinds(net([ev({ campaign })]))).toEqual(['hotTime']); // nothing asked yet: the schedule says it is on
		const status = (joined) => ({ now: NOWMS, at: NOWMS, current: { key: 'k', startsAt: NOWMS - HOUR, endsAt: NOWMS + HOUR, total: 0, mine: 0, joined }, past: [] });
		expect(kinds(net([ev({ campaign })], { campaigns: { e: status(false) } }))).toEqual(['hotTime']);
		expect(kinds(net([ev({ campaign })], { campaigns: { e: status(true) } }))).toEqual([]);
		const over = { now: NOWMS, at: NOWMS, past: [{ key: 'p', startsAt: 0, endsAt: 1, joined: true, tier: 0, claimed: false }] };
		expect(kinds(net([ev({ campaign: { ...campaign, schedule: [] } })], { campaigns: { e: over } }))).toEqual(['campaignReward']);
	});
	it('says an invitation stage is done and not taken, by either side', () => {
		const stage = (claimed) => ({ id: 's', progress: 1, target: 1, claimed });
		expect(kinds(net([ev({ invite: {} })], { invites: { e: { friends: [{ n: 1, stages: [stage(false)] }] } } }))).toEqual(['invite']);
		expect(kinds(net([ev({ invite: {} })], { invites: { e: { invitee: { stages: [stage(false)] }, friends: [] } } }))).toEqual(['invite']);
		expect(kinds(net([ev({ invite: {} })], { invites: { e: { invitee: { stages: [stage(true)] }, friends: [{ n: 1, stages: [stage(true)] }] } } }))).toEqual([]);
	});
	it('leaves out an event that is not going', () => {
		expect(kinds(net([ev({ attendance: {}, until: NOWMS - 1 })]))).toEqual([]);
	});
});
