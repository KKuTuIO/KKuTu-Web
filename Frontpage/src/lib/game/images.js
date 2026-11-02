/** Image URL rules shared with the legacy client (`iImage`). */
export const IMAGE_CDN = 'https://cdn.kkutu.io/img/';
export const IMAGE_VERSION = '?v=4.0.5f';
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
	const obj = key ? shop[key] : slotGroup ? { _id: 'default', group: slotGroup, options: {} } : undefined;
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
	const layers = [];
	for (const part of parts) {
		const key = 'M' + part;
		if (key === 'Mbody') continue;
		layers.push({ part, src: itemImage(equip[key], shop, slot[key] ?? key), flip: key === 'Mrhand' });
		if (part === 'back') {
			const body = equip.robot
				? IMAGE_CDN + 'kkutu/moremi/robot.png'
				: equip.Mshoes === 'foot_remover'
					? IMAGE_CDN + 'kkutu/moremi/body/body_footless.png'
					: itemImage(equip.Mbody, shop, 'Mbody');
			layers.push({ part: 'body', src: body, flip: false });
		}
	}
	if (equip.BDG) layers.push({ part: 'badge', src: itemImage(equip.BDG, shop), flip: false });
	return layers;
}

/** CSS for the level sprite (`newlv.png`, 25 columns), same maths as legacy `getLevelImage`. */
export function levelSpriteStyle(level) {
	const i = level - 1;
	return `background-image:url('${IMAGE_CDN}kkutu/lv/newlv.png');background-size:2560%;background-position:${(i % 25) * -100}% ${Math.floor(i / 25) * -100}%`;
}
