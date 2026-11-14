import { t } from './i18n.svelte.js';
import { confirm } from './ui/confirm.svelte.js';
import { eventPhase, timeLeft } from './news.js';
import { itemName } from './shop.js';

/** "2일 3시간", "3시간 12분" or "5분": the two biggest units that are not zero. */
export function spanText(until, now = Date.now()) {
	const { days, hours, minutes } = timeLeft(until, now);
	if (days) return t('play.news.span.days', { days, hours });
	if (hours) return t('play.news.span.hours', { hours, minutes });
	return t('play.news.span.minutes', { minutes: Math.max(1, minutes) });
}

/** The line under an entry's title: how long it still goes, when it begins, or that it is over. */
export function whenText(event, now = Date.now()) {
	const phase = eventPhase(event, now);
	if (phase === 'ongoing') return event.permanent ? t('play.news.permanent') : t('play.news.leftIn', { span: spanText(event.until, now) });
	if (phase === 'upcoming') return t('play.news.startIn', { span: spanText(event.start, now) });
	return t('play.news.phase.ended');
}

/** The clock of the page header: "18일 02:19:09", just "02:19:09" under a day; "상시 진행" for a permanent event and "종료" once it is over. */
export function clockText(event, now = Date.now()) {
	const phase = eventPhase(event, now);
	if (event.permanent) return t('play.news.permanent');
	if (phase === 'ended') return t('play.news.phase.ended');
	const left = Math.max(0, Math.floor(((phase === 'upcoming' ? Number(event.start) : Number(event.until)) - now) / 1000));
	const two = (n) => String(n).padStart(2, '0');
	const days = Math.floor(left / 86_400);
	return `${days ? `${t('play.news.clock.days', { days })} ` : ''}${two(Math.floor((left % 86_400) / 3600))}:${two(Math.floor((left % 3600) / 60))}:${two(left % 60)}`;
}

const dateText = (ms) => new Date(ms).toLocaleString(undefined, { year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
/** "2026. 11. 7. 오전 00:00 ~ 2026. 11. 14. ...", nothing for a permanent event. */
export const periodText = (event) => (event.permanent ? '' : t('play.news.period', { from: dateText(event.start), to: dateText(event.until) }));

/** A link from event content: an address of this site opens at once, any other asks first (the same question as a link in a profile). */
export async function openLink(href) {
	let same = false;
	try {
		same = new URL(href, location.origin).origin === location.origin;
	} catch {
		/* not an address */
	}
	if (!same && !(await confirm({ message: `${t('play.link.confirm')}\n${href}`, title: t('play.confirm.ok'), ok: t('play.link.yes'), cancel: t('play.link.no') }))) return;
	window.open(href, '_blank', 'noopener,noreferrer');
}

/** What a reward is called ("핑", "행사 포인트" or the item's name). */
export function rewardLabel(reward, details) {
	if (reward.kind === 'ping') return t('play.news.reward.ping');
	if (reward.kind === 'ep') return t('play.news.reward.ep');
	if (reward.kind === 'xp') return t('play.obtain.exp');
	if (reward.kind === 'letter') return t(`play.news.reward.letter.${reward.tier}`);
	return itemName(details, reward.id ?? '');
}

/** "핑 500, 단어 토큰 2": what an exchange asks for, as a sentence part. */
export const costText = (costs, details) => costs.map((c) => `${rewardLabel(c, details)} ${Number(c.q).toLocaleString()}`).join(', ');
