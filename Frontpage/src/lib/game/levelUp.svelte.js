import { playSound } from './audio.svelte.js';
import { entryFor, splitLevels } from './levelUp.js';
import { protoGift } from './levelGifts.proto.js';

/**
 * The one place level-ups are announced from, whatever raised the experience: the result board's gauge, a 백과사전 낱장, a coupon.
 * Sources open a *session* for the levels they pass and tell it when each is reached; the controller queues the popups
 * (three of their own, the rest folded into one summary) and `LevelUpHost` shows them. Nothing here knows about a particular screen.
 */
// raw: the session is compared by identity, which a deep reactive proxy would break
let current = $state.raw(null); // { entry, index, total, session } while a popup is up
let queue = []; // { entry, index, total, session }
let idle = []; // callbacks for when the last popup is gone
let steps = 0; // counts the popups shown, so a screen can tell one step from the next

function show() {
	const item = queue.shift();
	if (!item) {
		current = null;
		const wake = idle;
		idle = [];
		for (const fn of wake) fn();
		return;
	}
	const levels = item.entry.kind === 'level' ? [item.entry.level] : item.entry.levels;
	for (const l of levels) item.session.displayed.add(l);
	current = { ...item, step: ++steps };
	playSound('lvup');
}

function enqueue(session, entry, index, total) {
	queue.push({ entry, index, total, session });
	if (!current) show();
}

/** Whatever of a session's levels has had no popup of its own, as one summary entry at the end of the queue. */
function flushSummary(s) {
	const queued = new Set(queue.filter((i) => i.session === s && i.entry.kind === 'level').map((i) => i.entry.level));
	const rest = s.levels.filter((l) => !s.displayed.has(l) && !queued.has(l));
	if (!rest.length) return;
	enqueue(s, { ...entryFor(rest, s.giftOf), levels: rest }, s.skipped ? 0 : s.total - 1, s.skipped ? 1 : s.total);
}

/**
 * The levels a source is about to pass, in order. `reach(level)` as each is passed, `end()` once the source is done.
 * `giftOf(level)` gives what that level gave (the server's, once it sends them; the prototype table until then).
 */
function session(levels, giftOf = protoGift) {
	const { shown, folded } = splitLevels(levels);
	const s = { levels, giftOf, displayed: new Set(), skipped: false, total: shown.length + (folded.length ? 1 : 0), count: 0 };
	s.reach = (level) => {
		if (s.skipped || !shown.includes(level)) return;
		enqueue(s, entryFor([level], giftOf), s.count++, s.total);
	};
	s.end = () => flushSummary(s);
	return s;
}

export const levelUp = {
	get current() {
		return current;
	},
	get busy() {
		return current !== null || queue.length > 0;
	},
	session,
	/** For a source that has all its levels at once (an item opened, a coupon): every popup and the summary in one go. */
	announce(levels, giftOf) {
		const s = session(levels, giftOf);
		for (const l of levels) s.reach(l);
		s.end();
		return s;
	},
	/** Go on to the next popup, or finish. */
	next: show,
	/** Skip the popups still to come in the current session: one summary of them takes their place. */
	skip() {
		const cur = current;
		if (!cur) return;
		const s = cur.session;
		s.skipped = true;
		queue = queue.filter((i) => i.session !== s);
		flushSummary(s);
		show();
	},
	/** Runs `fn` when no popup is up or waiting (at once if there is none). */
	whenIdle(fn) {
		if (!this.busy) fn();
		else idle.push(fn);
	},
	reset() {
		queue = [];
		idle = [];
		current = null;
	}
};
