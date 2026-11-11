/**
 * The server-wide "#yell" announcements. They are shown one at a time in <YellBanner/>, scrolling across the top of the page;
 * `yell(text)` queues one. The text is plain (the banner never renders markup).
 */
export const banner = $state({ current: null, queue: [] });
const MAX_QUEUE = 5;
let seq = 0;

export function yell(text) {
	const message = String(text ?? '').replace(/\s+/g, ' ').trim();
	if (!message) return;
	if (banner.current === null) banner.current = { id: ++seq, message };
	else {
		banner.queue.push({ id: ++seq, message });
		if (banner.queue.length > MAX_QUEUE) banner.queue.shift(); // an old announcement is stale by the time it would be shown
	}
}

/** The current announcement is over (it scrolled out, or was closed): the next one, if any, comes up. */
export function nextYell() {
	banner.current = banner.queue.shift() ?? null;
}

/** How long a text takes to cross a lane of `viewport` px: a calm, readable constant speed, and never less than 10 s. */
export function scrollDuration(textWidth, viewport, pxPerSecond = 65) {
	return Math.max(10, (viewport + textWidth) / pxPerSecond);
}
