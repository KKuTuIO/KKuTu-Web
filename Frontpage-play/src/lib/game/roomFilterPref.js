/**
 * The room list's sort, filters and view, kept in this browser like the legacy client kept `ow` / `ou` in `kks` (the user list's
 * order, `ul`, is the `userSort` of prefs.svelte.js). Every change is saved at once; damaged or unknown values fall back to the defaults.
 * The query is never kept.
 */
const KEY = 'kkutu-play-rooms';

export const ROOM_FILTER_DEFAULTS = {
	sort: 'recent', // recent | crowded
	joinableFirst: false,
	waitingOnly: false, // off by default: a list of only waiting rooms hides the games running, which looks like an empty lobby
	hasSeat: false,
	hideLocked: false,
	view: 'list', // list | tile
	viewPicked: false // false: the view follows the width of the list
};
const CHOICES = { sort: ['recent', 'crowded'], view: ['list', 'tile'] };

function clean(name, value) {
	const d = ROOM_FILTER_DEFAULTS[name];
	if (typeof d === 'boolean') return typeof value === 'boolean' ? value : d;
	return CHOICES[name].includes(value) ? value : d;
}

export function loadRoomFilter() {
	let o = {};
	try {
		o = JSON.parse(localStorage.getItem(KEY) ?? 'null') ?? {};
	} catch {
		/* private mode or damaged data: the defaults */
	}
	if (typeof o !== 'object' || o === null) o = {};
	const out = Object.fromEntries(Object.keys(ROOM_FILTER_DEFAULTS).map((k) => [k, clean(k, o[k])]));
	if (!out.viewPicked) out.view = ROOM_FILTER_DEFAULTS.view;
	return out;
}

export function saveRoomFilter(ui) {
	try {
		localStorage.setItem(KEY, JSON.stringify(Object.fromEntries(Object.keys(ROOM_FILTER_DEFAULTS).map((k) => [k, clean(k, ui[k])]))));
	} catch {
		/* private mode */
	}
}

export const isRoomOption = (name) => name in ROOM_FILTER_DEFAULTS;
