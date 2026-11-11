import { cdnImg } from '../cdn.js';
/**
 * The 공지·행사 page's pure rules. The server makes no difference between a notice and an event: both are one catalog entry
 * (`EventInfo`), and a notice is the entry with `bulletin` set. Times are milliseconds.
 */

/** The picture in the new-event bubble for an event that has none of its own (about 518x190, like the real ones). */
/** The picture of a card that has none of its own (16:9, the slogan on the arcade colours; KKuTu-Static). */
export const DEFAULT_BANNER = cdnImg('event/default-card.png');
export const NUDGE_PLACEHOLDER = cdnImg('event/default-nudge.png'); // 518×190 at 2x: the slogan on the arcade colours (KKuTu-Static)

export const PHASES = ['ongoing', 'upcoming', 'ended'];
const RANK = { ongoing: 0, upcoming: 1, ended: 2 };
const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const SEEN_KEY = 'kk.newsSeen';
/** An https address or a path of this site: the same rule as the server's presentation check. Anything else is not drawn as a link or an image. */
const SAFE_URL = /^(https:\/\/\S+|\/(?!\/)\S*)$/;

/** @returns {'ongoing'|'upcoming'|'ended'} a permanent event is always going */
export function eventPhase(event, now = Date.now()) {
	if (event.permanent) return 'ongoing';
	if (now < Number(event.start)) return 'upcoming';
	if (now < Number(event.until)) return 'ongoing';
	return 'ended';
}

/** Going, not permanent, and ending within `hours`: the "곧 종료" mark. */
export function endingSoon(event, now = Date.now(), hours = 24) {
	return eventPhase(event, now) === 'ongoing' && !event.permanent && Number(event.until) - now <= hours * HOUR;
}

/** The time left as whole days, hours and minutes (rounded up to the minute, never negative). */
export function timeLeft(until, now = Date.now()) {
	const left = Math.max(0, Math.ceil((Number(until) - now) / MINUTE) * MINUTE);
	return { days: Math.floor(left / DAY), hours: Math.floor((left % DAY) / HOUR), minutes: Math.floor((left % HOUR) / MINUTE), over: left === 0 };
}

/** Going first (the one that ends soonest first, permanent ones last), then the upcoming (soonest first), then the ended (latest first). Ties keep the server's order. */
export function orderNews(list, now = Date.now()) {
	return list
		.map((event, index) => ({ event, index, phase: eventPhase(event, now) }))
		.sort((a, b) => {
			if (RANK[a.phase] !== RANK[b.phase]) return RANK[a.phase] - RANK[b.phase];
			const key = (x) =>
				x.phase === 'ongoing' ? (x.event.permanent ? Infinity : Number(x.event.until)) : x.phase === 'upcoming' ? Number(x.event.start) : -Number(x.event.until);
			const [ka, kb] = [key(a), key(b)];
			return ka < kb ? -1 : ka > kb ? 1 : a.index - b.index;
		})
		.map((x) => x.event);
}

/** A campaign: everybody's play adds up (the server tells by the entry carrying `campaign`). */
export const isCampaign = (event) => !!event?.campaign;
/** An event for newcomers or returning players (the server only lists it to those it is for). */
export const isAudience = (event) => !!event?.audience;
/** The section an entry is listed in: 새내기/복귀 (an event with an `audience`), 캠페인, 공지 (`bulletin`) or 행사 (the rest). */
export const sectionOf = (event) => (isAudience(event) ? 'audience' : isCampaign(event) ? 'campaign' : event.bulletin ? 'notice' : 'event');

/** @param {'all'|'audience'|'campaign'|'event'|'notice'} kind */
export function filterNews(list, kind) {
	return kind === 'all' ? list : list.filter((e) => sectionOf(e) === kind);
}

export const safeUrl = (url) => (typeof url === 'string' && SAFE_URL.test(url) ? url : '');
export const safeAccent = (value) => (typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value) ? value : '');

/** The picture of a letter piece of a tier whose letter is not drawn yet: the piece's circle in the tier's colour, with a question mark. */
export const letterId = (tier) => `$WP${{ common: 'C', advanced: 'B', rare: 'A' }[tier] ?? 'C'}?`;

/** A reward of the catalog as the chip shows it: a currency or an item (the exchange maps use item ids and `ping` as keys). */
export function rewardOf(reward) {
	if (reward.type === 'ping') return { kind: 'ping', q: reward.q };
	if (reward.type === 'ep') return { kind: 'ep', q: reward.q };
	if (reward.type === 'letter') return { kind: 'letter', tier: reward.tier, id: letterId(reward.tier), q: reward.q };
	return { kind: 'item', id: reward.name, q: reward.q };
}
export const rewardsOfMap = (map = {}) => Object.entries(map).map(([key, q]) => (key === 'ping' ? { kind: 'ping', q } : { kind: 'item', id: key, q }));

/** `{shown, more}`: the first `max` of a list and how many were left out. */
export function limitList(list, max) {
	return { shown: list.slice(0, max), more: Math.max(0, list.length - max) };
}

/** How far a mission is, as 0..1. */
export const ratio = (progress, target) => (target > 0 ? Math.min(1, Math.max(0, progress / target)) : 0);

/** The days of an attendance book that are done: `attended` counts every check-in, a book of N days starts over after N. */
export function attendanceDone(type, attended) {
	const length = type === 'calendar' ? 31 : Number(type) || 7;
	if (attended <= 0) return 0;
	return attended % length === 0 ? length : attended % length;
}
export const attendanceLength = (type) => (type === 'calendar' ? 31 : Number(type) || 7);

// ---- what the player has seen (a dot on the menu until the page is opened) ----
export function loadSeen(storage = globalThis.localStorage) {
	try {
		const list = JSON.parse(storage?.getItem(SEEN_KEY) ?? '[]');
		return new Set(Array.isArray(list) ? list.filter((x) => typeof x === 'string') : []);
	} catch {
		return new Set();
	}
}
export function saveSeen(seen, storage = globalThis.localStorage) {
	try {
		storage?.setItem(SEEN_KEY, JSON.stringify([...seen].slice(-200)));
	} catch {
		/* storage can be blocked: the dot just shows again next time */
	}
}
/** The going events the player has not seen yet. */
export const unseenNews = (list, seen, now = Date.now()) => list.filter((e) => eventPhase(e, now) === 'ongoing' && !seen.has(e.id));

/** The event the bubble announces: the first going event (not a notice) the player has not seen, in the page's own order. */
export function newsNudge(list, seen, now = Date.now()) {
	const fresh = orderNews(unseenNews(list, seen, now), now).filter((e) => !e.bulletin);
	const event = fresh.find(isCampaign) ?? fresh[0]; // a campaign is announced before an event
	if (!event) return null;
	return { id: event.id, name: event.name, image: safeUrl(event.presentation?.nudge) || NUDGE_PLACEHOLDER };
}

/**
 * What waits for the player in the going events, as a list of `{id, kind}` (the red dot on the menu): a mission done and not taken, today's
 * check-in not made, a campaign's hot time on (and I have not played in it yet) or a hot time's reward not taken, an invitation's stage done and not taken.
 * Unlike the bubble, which announces only an event that is new, this stays as long as there is something to do.
 */
export function newsAlerts(net, now = Date.now()) {
	if (net.guest || !net.eventsLoaded) return [];
	const alerts = [];
	for (const event of net.events.list) {
		if (eventPhase(event, now) !== 'ongoing') continue;
		if (event.missions?.length && readyCount(event.missions, net.eventProgress, event.id) > 0) alerts.push({ id: event.id, kind: 'missions' });
		if (event.attendance && !net.eventProgress.attendance.find((a) => a.eventId === event.id)?.todayClaimed) alerts.push({ id: event.id, kind: 'attendance' });
		if (event.campaign) {
			const status = net.campaigns?.[event.id];
			const view = campaignView(status, event.campaign, now);
			const live = status ? !!view.current && !view.current.joined : roundsToday(event.campaign, now).some((r) => r.state === 'live');
			if (live) alerts.push({ id: event.id, kind: 'hotTime' });
			if (view.claimable.length) alerts.push({ id: event.id, kind: 'campaignReward' });
		}
		const invite = net.invites?.[event.id];
		if (invite) {
			const waiting = (stages) => stages.some((s) => !s.claimed && s.progress >= s.target);
			if ((invite.invitee && waiting(invite.invitee.stages)) || invite.friends.some((f) => waiting(f.stages))) alerts.push({ id: event.id, kind: 'invite' });
		}
	}
	return alerts;
}

// ---- missions and reward tracks ----
export const MISSION_PERIODS = ['daily', 'weekly', 'event'];

/** The periods the missions of an event use, in the usual order (a mission without one is daily, as on the server). */
export const periodsOf = (missions = []) => MISSION_PERIODS.filter((p) => missions.some((m) => (m.period ?? 'daily') === p));

/** @returns {'claimed'|'ready'|'progress'} where a mission is, from my progress entry (none yet counts as no progress) */
export function missionStatus(mission, entry) {
	if (entry?.claimed) return 'claimed';
	if ((entry?.progress ?? 0) < mission.target) return 'progress';
	return mission.rewards?.length ? 'ready' : 'claimed'; // a mission that gives nothing of its own (a lesson, paid where it is done) has nothing to take
}

/** The latest progress entry of a mission of an event, from `eventProgress.missions`. */
export const missionEntry = (progress, eventId, missionId) => progress.missions.findLast((m) => m.eventId === eventId && m.missionId === missionId);

/** How many missions (all, or of one period) wait to be claimed. */
export function readyCount(missions = [], progress, eventId, period) {
	return missions.filter((m) => (!period || (m.period ?? 'daily') === period) && missionStatus(m, missionEntry(progress, eventId, m.id)) === 'ready').length;
}

/** How many missions are finished (claimed or waiting to be claimed). */
export const finishedCount = (missions = [], progress, eventId) => missions.filter((m) => missionStatus(m, missionEntry(progress, eventId, m.id)) !== 'progress').length;

/** Where I am on a reward track: how many steps are done, from the days attended or the missions finished. Without a source: nowhere. */
export function trackCount(source, event, progress) {
	if (source === 'attendance') return attendanceDone(event.attendance?.TYPE, progress.attendance.find((a) => a.eventId === event.id)?.attended ?? 0);
	if (source === 'missions') return finishedCount(event.missions, progress, event.id);
	return -1;
}

/** @returns {'done'|'current'|'todo'|'plain'} a step of a track: `count` steps are done, the next one is where I am (-1: the track shows no progress) */
export const trackStep = (count, index) => (count < 0 ? 'plain' : index < count ? 'done' : index === count ? 'current' : 'todo');

/** Whether the event is one stage (no big header, no tabs, its main part as large as the container allows): its own `stage` setting, or else yes for a tile roulette (it needs the room) and no for the rest. */
export const isStage = (event) => event?.presentation?.stage ?? (!!event?.tile || !!event?.campaign);

// ---- 캠페인 ----
/**
 * What the campaign page shows of a status. `skew` is how far the server's clock is from mine, so the countdowns follow the server's.
 * Tiers are drawn as a bar like the tile roulette's mileage: `gaugeView` reads `points` and `steps`.
 */
export function campaignView(status, info, now = Date.now()) {
	const skew = status ? status.now - (status.at ?? status.now) : 0;
	const server = now + skew;
	const current = status?.current;
	const total = current?.total ?? 0;
	const tiers = info?.tiers ?? [];
	const reached = tiers.reduce((n, t, i) => (total >= t.points ? i : n), -1);
	return {
		server,
		current,
		next: status?.next,
		total,
		mine: current?.mine ?? 0,
		reached,
		goal: tiers[reached + 1] ?? null, // the next tier to reach, null when all are
		bar: gaugeViewOf(total, tiers),
		left: current ? Math.max(0, current.endsAt - server) : 0,
		until: !current && status?.next ? Math.max(0, status.next.startsAt - server) : 0,
		claimable: (status?.past ?? []).filter((p) => p.joined && p.tier >= 0 && !p.claimed)
	};
}
const gaugeViewOf = (points, steps) => {
	const last = steps[steps.length - 1]?.points ?? 1;
	return { points, last, ratio: Math.min(1, points / last), steps: steps.map((s) => ({ ...s, reached: points >= s.points, at: Math.min(1, s.points / last) })) };
};

const clockMinutes = (text) => {
	const m = /^(\d{2}):(\d{2})$/.exec(text ?? '');
	return m ? Number(m[1]) * 60 + Number(m[2]) : NaN;
};
/**
 * The hot times of today, in the campaign's time zone, numbered in the order of the day (1회차, 2회차...), each with where it is: `past`, `live` or `next`
 * (a later one). A window that is only on some weekdays is left out on the others.
 */
export function roundsToday(info, now = Date.now()) {
	const zone = info?.timezone || 'Asia/Seoul';
	let parts;
	try {
		parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone: zone, hourCycle: 'h23', weekday: 'short', hour: '2-digit', minute: '2-digit' }).formatToParts(new Date(now)).map((p) => [p.type, p.value]));
	} catch {
		return [];
	}
	const weekday = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(parts.weekday);
	const minute = Number(parts.hour) * 60 + Number(parts.minute);
	return (info?.schedule ?? [])
		.filter((w) => !w.days || w.days.includes(weekday))
		.map((w) => ({ from: w.from, to: w.to, a: clockMinutes(w.from), b: clockMinutes(w.to) }))
		.filter((w) => w.a < w.b)
		.sort((x, y) => x.a - y.a)
		.map((w, i) => ({ n: i + 1, from: w.from, to: w.to, state: minute >= w.b ? 'past' : minute >= w.a ? 'live' : 'next' }));
}

/**
 * What an event of its own kind needs explained, as locale keys with their arguments: the words are written once in the client (not by whoever sets the
 * event up), so every event of a kind is explained the same way. The way points are earned follows the goal's signal (`play.news.how.metric.<metric>`).
 * @returns {{key:string, args?:Record<string,string|number>}[]}
 */
export function howTo(event) {
	const lines = [];
	const c = event?.campaign;
	if (c) {
		lines.push({ key: 'play.news.how.campaign.pool' });
		if (c.goal) lines.push({ key: 'play.news.how.campaign.earn', args: { metric: c.goal.metric, n: c.goal.points } });
		if (c.goal?.filtered) lines.push({ key: 'play.news.how.campaign.filtered' });
		if (c.cap) lines.push({ key: 'play.news.how.campaign.cap', args: { n: c.cap } });
		lines.push({ key: 'play.news.how.campaign.tiers' }, { key: 'play.news.how.campaign.claim' });
	}
	const invite = event?.invite;
	if (invite) lines.push({ key: 'play.news.how.invite.share' }, { key: 'play.news.how.invite.who', args: { days: invite.days } }, { key: 'play.news.how.invite.stages' }, { key: 'play.news.how.invite.max', args: { n: invite.max } });
	const tile = event?.tile;
	if (tile) {
		lines.push({ key: 'play.news.how.tile.spend', args: { n: tile.cost } });
		if (tile.gauge?.perDraw) lines.push({ key: 'play.news.how.tile.mileage', args: { n: tile.gauge.perDraw } });
		lines.push({ key: 'play.news.how.tile.owned' });
	}
	return lines;
}

/** "1시간 5분", "12분 3초": the two biggest units that are not zero, for a countdown of a hot time. */
export function clockText(ms) {
	const s = Math.max(0, Math.floor(ms / 1000));
	const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
	return h ? [h, m, 'h'] : [m, sec, 'm'];
}

/**
 * The steps of a stepper (every step is as wide as the others, whatever its points are): how full the stretch up to each step is, and whether it is
 * reached. A step's stretch runs from the step before it (0 for the first) to its own points.
 */
export function stepFill(total, tiers) {
	let before = 0;
	return tiers.map((tier) => {
		const span = Math.max(1, tier.points - before);
		const reached = total >= tier.points;
		const fill = reached ? 1 : Math.min(1, Math.max(0, (total - before) / span));
		before = tier.points;
		return { tier, fill, reached };
	});
}

/** "03:10:00": a time left as hours (as many as there are, at least two digits), minutes and seconds. */
export function hmsText(ms) {
	const s = Math.max(0, Math.floor(ms / 1000));
	const two = (n) => String(n).padStart(2, '0');
	return `${two(Math.floor(s / 3600))}:${two(Math.floor((s % 3600) / 60))}:${two(s % 60)}`;
}

/**
 * Which reward of a bundle stands for it: an item first (it is what the player is after, and has a picture to show), then event points, and 핑 only
 * when there is nothing else, since it is the one reward every bundle can carry. `rewards` are the chips' shape (`rewardOf`). The rest is `more`.
 * @returns {{main:any, more:number}|null}
 */
export function representative(rewards) {
	if (!rewards?.length) return null;
	const main = rewards.find((r) => r.kind === 'item' || r.kind === 'letter') ?? rewards.find((r) => r.kind === 'ep') ?? rewards[0];
	return { main, more: rewards.length - 1 };
}
