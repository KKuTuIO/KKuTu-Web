import { getLevel } from './level.js';

/**
 * Level-up gifts, as the result board shows them. A gift is `{ level, ping, items: [{key, q}], mail: [{key, q}] }`:
 * `items` and `ping` went straight into the inventory, `mail` (items whose time limit starts on receipt) waits in the mailbox.
 * The server sends these per level it paid: in the result's reward (`reward.levelGifts`) and in `obtain` (`levelGifts`).
 */

/** How many level-ups get a popup of their own; the rest are folded into one summary, so a big jump never takes dozens of key presses. */
export const POPUP_CAP = 3;

/** The levels passed going from `before` to `after` experience, lowest first. */
export function crossedLevels(before, after) {
	const out = [];
	for (let l = getLevel(before) + 1; l <= getLevel(after); l++) out.push(l);
	return out;
}

const add = (map, list = []) => {
	for (const { key, q } of list) map.set(key, (map.get(key) ?? 0) + q);
};
const toList = (map) => [...map].map(([key, q]) => ({ key, q }));

/** One gift standing for several: pings added up, same items merged. */
export function sumGifts(gifts) {
	const items = new Map();
	const mail = new Map();
	let ping = 0;
	for (const g of gifts) {
		if (!g) continue;
		ping += g.ping ?? 0;
		add(items, g.items);
		add(mail, g.mail);
	}
	return { ping, items: toList(items), mail: toList(mail) };
}

/** A popup entry for one level, or for a run of levels when `levels` has more than one. */
export function entryFor(levels, giftOf) {
	const gift = sumGifts(levels.map(giftOf));
	return levels.length === 1 ? { kind: 'level', level: levels[0], gift } : { kind: 'summary', from: levels[0], to: levels[levels.length - 1], count: levels.length, levels, gift };
}

/** Which levels pop up on their own and which are folded away: `{ shown: [level], folded: [level] }`. */
export function splitLevels(levels, cap = POPUP_CAP) {
	return { shown: levels.slice(0, cap), folded: levels.slice(cap) };
}

/** True when a gift has anything to show beyond the level itself. */
export const hasGift = (g) => !!g && (g.ping > 0 || g.items?.length > 0 || g.mail?.length > 0);
