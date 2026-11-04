/**
 * Players I don't want to hear from. Two kinds, as in the legacy client:
 *  - shut: all of their chat is hidden, their invitations and friend requests are turned down;
 *  - mute: only their whispers are ignored.
 * Members are remembered in this browser under the legacy keys ("shut" and "block"); guests only for this visit, since
 * a guest's id is gone with the session. The server never sees any of it.
 */
const KEYS = { shut: 'shut', mute: 'block' };

function load(key) {
	try {
		const o = JSON.parse(localStorage.getItem(key) ?? 'null');
		return o && typeof o === 'object' ? o : {};
	} catch {
		return {};
	}
}

/** id -> nickname */
export const blocked = $state({ shut: load(KEYS.shut), mute: load(KEYS.mute) });

const kept = (kind) => Object.fromEntries(Object.entries(blocked[kind]).filter(([id]) => !id.startsWith('guest__')));
function save(kind) {
	try {
		localStorage.setItem(KEYS[kind], JSON.stringify(kept(kind)));
	} catch {
		/* private mode */
	}
}

export const isShut = (id) => id in blocked.shut;
export const isMuted = (id) => id in blocked.mute;

/** Switch one kind on or off for a player. Returns whether it is on now. */
export function toggleBlock(kind, id, nick) {
	if (id in blocked[kind]) delete blocked[kind][id];
	else blocked[kind][id] = nick || id;
	save(kind);
	return id in blocked[kind];
}

/** Everyone blocked in either way, one row per (player, kind). */
export const blockedList = () => [
	...Object.entries(blocked.shut).map(([id, name]) => ({ id, name, kind: 'shut' })),
	...Object.entries(blocked.mute).map(([id, name]) => ({ id, name, kind: 'mute' }))
];
