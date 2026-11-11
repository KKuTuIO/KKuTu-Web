/**
 * 옷장 (wardrobe presets): named outfits, kept in this browser under the legacy client's own key and shape (`wardrobePresets`:
 * [{id, name, equip:{slot: itemId}}]), so outfits made there show up here and the other way round. Pure rules, ported from the
 * legacy client: what a preset may hold, whether it can be put on with what I own, and the order of equip toggles that gets me there.
 * (The bonus ticket is not part of an outfit: the legacy slots do not include it, and an old outfit must not take my ticket off.)
 */
export const MAX_PRESETS = 10;
export const MAX_NAME = 30;
export const SLOTS = ['Mhead', 'Meye', 'Mmouth', 'Mlhand', 'Mrhand', 'Mclothes', 'Mshoes', 'Mback', 'MheadDeco', 'MfaceDeco', 'MeyeDeco', 'MdressDeco', 'BDG', 'NIK', 'BAN', 'BAD'];

/** One stored entry, or null when it is unusable. */
export function normalizePreset(p, fallbackId) {
	if (!p || typeof p.name !== 'string' || !p.equip || typeof p.equip !== 'object' || Array.isArray(p.equip)) return null;
	const name = p.name.trim().slice(0, MAX_NAME);
	if (!name) return null;
	const equip = {};
	for (const slot of SLOTS) {
		const id = p.equip[slot];
		if (typeof id === 'string' && id.length && id.length <= 128) equip[slot] = id;
	}
	// which copy of an enchanted item the outfit wears ({item: instance}, 0 = a plain one); absent on outfits from before enchants (and from the legacy client)
	const preset = { id: String(p.id || fallbackId), name, equip };
	if (p.copies && typeof p.copies === 'object' && !Array.isArray(p.copies)) {
		const worn = new Set(Object.values(equip));
		const copies = {};
		for (const [id, n] of Object.entries(p.copies)) if (worn.has(id) && Number.isInteger(n) && n >= 0 && n < 1e6) copies[id] = n;
		preset.copies = copies;
	}
	return preset;
}

/** What I wear now, reduced to the slots an outfit holds. */
export function currentEquip(equip) {
	const out = {};
	for (const slot of SLOTS) if (typeof equip?.[slot] === 'string' && equip[slot]) out[slot] = equip[slot];
	return out;
}

/** The slots an item can go to (a hand item either hand, any badge the badge slot), or null when it cannot be worn in an outfit. */
export function itemSlots(item) {
	if (!item) return null;
	const g = String(item.group ?? '');
	if (g === 'Mhand') return ['Mlhand', 'Mrhand'];
	if (g.startsWith('BDG')) return ['BDG'];
	return SLOTS.includes(g) ? [g] : null;
}

export const countOf = (box, id) => {
	const v = box?.[id];
	return typeof v === 'number' ? v : typeof v?.value === 'number' ? v.value : 0;
};

/** Can I put the outfit on: every item known, fitting its slot, and owned as many times as it is used (a pair of the same hand item needs two). */
export function canWear(preset, shop, box) {
	const used = {};
	for (const slot of SLOTS) {
		const id = preset.equip[slot];
		if (!id) continue;
		const slots = itemSlots(shop[id]);
		if (!slots || !slots.includes(slot) || countOf(box, id) < 1) return false;
		used[id] = (used[id] ?? 0) + 1;
	}
	return Object.entries(used).every(([id, n]) => n <= countOf(box, id));
};

/**
 * The equip messages (`{item, slot}`; the server toggles an item in its slot) that turn what I wear into the outfit, in the legacy
 * order: hands first (the old one off, then the new one on, so the same item is never in both hands at once), then every other slot.
 */
export function planApply(current, desired) {
	const changes = [];
	const hands = ['Mlhand', 'Mrhand'];
	for (const slot of hands) if (current[slot] && current[slot] !== desired[slot]) changes.push({ item: current[slot], slot });
	for (const slot of hands) if (desired[slot] && current[slot] !== desired[slot]) changes.push({ item: desired[slot], slot });
	for (const slot of SLOTS) {
		if (hands.includes(slot) || current[slot] === desired[slot]) continue;
		changes.push({ item: desired[slot] || current[slot], slot });
	}
	return changes;
}

/** The items of an outfit in slot order. */
export const presetItems = (preset) => SLOTS.map((s) => preset.equip[s]).filter(Boolean);
