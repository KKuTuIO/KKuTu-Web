/**
 * 칭호 (shop group `TTL`): an item whose `options.title` describes a frame, `{ shape, from, to, edge, text, ornament? }`, and whose name
 * (the localized one of the shop) is the words on it. The client draws the frame as SVG (`lobby/TitleBadge.svelte`), so a new title is a row
 * in the shop table: no art and no client change.
 * `shape`: 'pill' (round ends), 'tab' (a folder tab: the left part of the top edge stands a step higher), 'plate' (a squarer banner).
 * `from`/`to`: the frame's gradient (top to bottom); `edge`: its outline; `text`: the colour of the words; `ornament`: 'ring' | 'wings' | absent.
 */

export const TITLE_GROUP = 'TTL';
export const TITLE_SHAPES = ['pill', 'tab', 'plate'];
export const TITLE_ORNAMENTS = ['ring', 'wings'];
const HEX = /^#[0-9a-f]{6}$/i;

/** The frame of a shop item, or null when it has none (or an unusable one: unknown shape, a colour that is not `#rrggbb`). */
export function titleOf(item) {
	const x = item?.options?.title;
	if (!x || typeof x !== 'object' || !TITLE_SHAPES.includes(x.shape)) return null;
	for (const key of ['from', 'to', 'edge', 'text']) if (typeof x[key] !== 'string' || !HEX.test(x[key])) return null;
	const ornament = TITLE_ORNAMENTS.includes(x.ornament) ? x.ornament : null;
	const outline = typeof x.outline === 'string' && HEX.test(x.outline) ? x.outline : null; // an optional outline round the words
	return { shape: x.shape, from: x.from, to: x.to, edge: x.edge, text: x.text, outline, ornament };
}

/** The path of the frame in a `w` x `h` box (`shape` as above). */
export function framePath(shape, w, h) {
	const r = shape === 'plate' ? Math.min(5, h / 3) : shape === 'tab' ? Math.min(4, h / 4) : h / 2;
	if (shape === 'tab') {
		const step = Math.min(4, h / 5);
		const at = Math.max(r * 2, w * 0.24);
		return `M${r},0 H${at - step} L${at},${step} H${w - r} Q${w},${step} ${w},${step + r} V${h - r} Q${w},${h} ${w - r},${h} H${r} Q0,${h} 0,${h - r} V${r} Q0,0 ${r},0 Z`;
	}
	return `M${r},0 H${w - r} Q${w},0 ${w},${r} V${h - r} Q${w},${h} ${w - r},${h} H${r} Q0,${h} 0,${h - r} V${r} Q0,0 ${r},0 Z`;
}
