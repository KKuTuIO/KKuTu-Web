/** 아이템전: the six items of the legacy item mode, in the server's id order (game.item.<id>.name / .expl are the legacy texts). */
export const ITEMS = [
	{ id: 0, icon: 'arrow_forward', color: '#3b82f6' }, // 넘기기: blue
	{ id: 1, icon: 'fast_forward', color: '#f97316' }, // 건너뛰기: orange
	{ id: 2, icon: 'undo', color: '#ec4899' }, // 뒤로: pink
	{ id: 3, icon: 'shuffle', color: '#14b8a6' }, // 제시어 변경: teal
	{ id: 4, icon: 'add', color: '#eab308' }, // 한번 더: yellow
	{ id: 5, icon: 'casino', color: '#8b5cf6' } // 무작위: violet
];
/** The server keeps two counters per round: each item can be used twice, and all items together five times. */
export const PER_ITEM = 2;
export const PER_ROUND = 5;
export const DOCKS = ['answer', 'top', 'bottom', 'left', 'right'];

/**
 * Can I press this item now? The server decides in the end (turn errors 420-429); this only greys out what it would refuse for sure:
 * not my turn, none of this item or of all items left, the very first move of the game, or one item already used this turn.
 */
/** How many times I can still use this item: what is left of it, but never more than what is left of the round's five. */
export const itemLeft = (g, id) => Math.max(0, Math.min(g?.items?.[id] ?? 0, g?.itemsLeft ?? 0));

export function itemReady(g, id, myTurn) {
	if (!g || !myTurn) return false;
	if (itemLeft(g, id) <= 0) return false;
	if (!g.chain && !g.words?.length) return false; // nothing has been said yet
	return !g.itemLock;
}

/** Which dock a key moves the bar to (arrow keys on the handle). */
export const DOCK_KEYS = { ArrowUp: 'top', ArrowDown: 'bottom', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'answer', ' ': 'answer' };

/** The dock the pointer means: the answer area if it is over it, else the nearest edge once it is within `edge` px of one. */
export function dockAt(x, y, w, h, answer, edge = 90) {
	if (answer && x >= answer.x && x <= answer.x + answer.w && y >= answer.y && y <= answer.y + answer.h) return 'answer';
	const d = { left: x, right: w - x, top: y, bottom: h - y };
	const [name, dist] = Object.entries(d).sort((a, b) => a[1] - b[1])[0];
	return dist <= edge ? name : null;
}
