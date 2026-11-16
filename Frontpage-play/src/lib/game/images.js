/** Image URL rules shared with the legacy client (`iImage`). */
export const IMAGE_CDN = 'https://cdn.kkutu.io/img/';
export const IMAGE_VERSION = '?v=4.0.5f';
/** The level sheet changes on its own (721~800 were added in place), so it has its own cache key; bump it with the sheet. */
export const LEVEL_SHEET_VERSION = '?lv=800';
const DEFAULT_HEAD = IMAGE_CDN + 'kkutu/moremi/head/default.png?s=fb';

let shopRequest;
/** Item catalogue (`/shop`) keyed by item id. Cached for the page lifetime. */
export function loadShop() {
	shopRequest ??= fetch('/shop')
		.then((r) => (r.ok ? r.json() : { goods: [] }))
		.then((res) => Object.fromEntries((res.goods ?? []).map((g) => [g._id, g])))
		.catch(() => {
			shopRequest = undefined;
			return {};
		});
	return shopRequest;
}

/**
 * @param {string|undefined} key equipped item id, or empty for the slot's default
 * @param {Record<string, any>} shop
 * @param {string} [slotGroup] fallback group when nothing is equipped, e.g. "Mhead"
 */
export function itemImage(key, shop, slotGroup) {
	// An item the catalogue doesn't know (or the literal 'default') shows the slot's own default picture, not the head's.
	const obj = (key && shop[key]) || (slotGroup ? { _id: 'default', group: slotGroup, options: {} } : undefined);
	if (!obj?.group) return DEFAULT_HEAD;
	const opts = obj.options ?? {};
	const ext = 'gif' in opts ? '.gif' : '.png';
	const event = 'event' in opts;
	const group = String(obj.group);
	if (group.startsWith('BDG')) return IMAGE_CDN + (event ? 'kkutu/moremi/event/' : 'kkutu/moremi/badge/') + obj._id + ext + IMAGE_VERSION;
	return group.startsWith('M')
		? IMAGE_CDN + 'kkutu/moremi/' + (event ? 'event' : group.slice(1)) + '/' + obj._id + ext + IMAGE_VERSION
		: IMAGE_CDN + (event ? 'kkutu/moremi/event/' : 'kkutu/shop/') + obj._id + ext + IMAGE_VERSION;
}

/**
 * Ordered layer list for a Moremi, back to front. Mirrors legacy `renderMoremi`:
 * every part except the body is drawn in `parts` order, the body goes right after "back",
 * the right hand is mirrored and an equipped badge sits on top.
 */
export function moremiLayers(equip = {}, shop = {}, parts = []) {
	const slot = { Mlhand: 'Mhand', Mrhand: 'Mhand' };
	const isDefault = (key) => !equip[key] || equip[key] === 'default';
	// The default body, eyes and mouth are redrawn as SVG (see `MoremiVector.svelte`) so they stay sharp on HiDPI
	// screens and can animate. Anything else keeps its PNG. The robot and the foot remover replace the whole body.
	const vectorBody = !equip.robot && equip.Mshoes !== 'foot_remover' && isDefault('Mbody');
	const layers = [];
	for (const part of parts) {
		const key = 'M' + part;
		if (key === 'Mbody') continue;
		const vector = (key === 'Meye' || key === 'Mmouth') && isDefault(key) ? part : undefined;
		layers.push({ part, src: itemImage(equip[key], shop, slot[key] ?? key), flip: key === 'Mrhand', vector });
		if (part === 'back') {
			const body = equip.robot
				? IMAGE_CDN + 'kkutu/moremi/robot.png'
				: equip.Mshoes === 'foot_remover'
					? IMAGE_CDN + 'kkutu/moremi/body/body_footless.png'
					: itemImage(equip.Mbody, shop, 'Mbody');
			layers.push({ part: 'body', src: body, flip: false, vector: vectorBody ? 'body' : undefined });
		}
	}
	if (equip.BDG) layers.push({ part: 'badge', src: itemImage(equip.BDG, shop), flip: false });
	return layers;
}

/** CSS for the level sprite (`newlv.png`, 25 columns), same maths as legacy `getLevelImage`. */
export function levelSpriteStyle(level) {
	const i = level - 1;
	return `background-image:url('${IMAGE_CDN}kkutu/lv/newlv.png${LEVEL_SHEET_VERSION}');background-size:2560%;background-position:${(i % 25) * -100}% ${Math.floor(i / 25) * -100}%`;
}

/** Levels from here to 800 have an animated badge: `fxlv.png` holds one row per level, `LEVEL_FX_FRAMES` frames (7 moving, the last at rest). */
export const LEVEL_FX_FIRST = 751;
export const LEVEL_FX_FRAMES = 8;
const LEVEL_FX_LAST = 800;

/** Whether this level has an animated badge. */
export const hasLevelFx = (level) => level >= LEVEL_FX_FIRST && level <= LEVEL_FX_LAST;

/**
 * CSS for the animated badge of `level` at `size` px. It sets the sheet, the row and `--s` (the badge size); the frame itself is moved by the
 * `lv-fx` animation of `LevelIcon`, which also holds the rest frame so the badge stands still for a while.
 */
export function levelFxStyle(level, size) {
	const rows = LEVEL_FX_LAST - LEVEL_FX_FIRST + 1;
	return `background-image:url('${IMAGE_CDN}kkutu/lv/fxlv.png${LEVEL_SHEET_VERSION}');background-size:${LEVEL_FX_FRAMES * 100}% ${rows * 100}%;--s:${size}px;background-position-y:calc(var(--s) * -${level - LEVEL_FX_FIRST})`;
}

/** The level badge of a staff team (`gmlv.png`, 40x40, drawn like the level sprite: one picture scaled to the badge size). */
export const staffLevelImage = (id) => `${IMAGE_CDN}kkutu/lv/${id}lv.png${LEVEL_SHEET_VERSION}`;

