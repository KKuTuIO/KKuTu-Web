/** The wardrobe's outfits, in this browser (see wardrobe.js for the shape). Same pattern as the theme presets. */
import { MAX_PRESETS, MAX_NAME, normalizePreset } from './wardrobe.js';

const KEY = 'wardrobePresets';

function load() {
	try {
		const stored = JSON.parse(localStorage.getItem(KEY) ?? '[]');
		if (!Array.isArray(stored)) return [];
		return stored.map((p, i) => normalizePreset(p, `legacy-${i}`)).filter(Boolean).slice(0, MAX_PRESETS);
	} catch {
		return []; // private mode or damaged data
	}
}

export const wardrobe = $state({ list: load() });

function save() {
	try {
		localStorage.setItem(KEY, JSON.stringify($state.snapshot(wardrobe.list)));
	} catch {
		/* private mode */
	}
}

const newId = () => `wardrobe-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

/** Replace the outfits with a list from a file (unusable ones are dropped, the limit applies). */
export function replaceOutfits(list) {
	wardrobe.list = (Array.isArray(list) ? list : []).map((p, i) => normalizePreset(p, `imported-${i}`)).filter(Boolean).slice(0, MAX_PRESETS);
	save();
}

/** Saves an outfit; returns it, or null when the list is full or the name is empty. */
export function addOutfit(name, equip, copies) {
	if (wardrobe.list.length >= MAX_PRESETS) return null;
	const p = normalizePreset({ id: newId(), name, equip, copies }, newId());
	if (!p) return null;
	wardrobe.list.push(p);
	save();
	return p;
}

export function overwriteOutfit(id, equip, copies) {
	const p = wardrobe.list.find((x) => x.id === id);
	if (!p) return false;
	const next = normalizePreset({ ...p, equip, copies }, id);
	p.equip = next.equip;
	if (next.copies) p.copies = next.copies;
	else delete p.copies;
	save();
	return true;
}

export function renameOutfit(id, name) {
	const p = wardrobe.list.find((x) => x.id === id);
	const n = (name ?? '').trim().slice(0, MAX_NAME);
	if (!p || !n) return false;
	p.name = n;
	save();
	return true;
}

export function removeOutfit(id) {
	const i = wardrobe.list.findIndex((x) => x.id === id);
	if (i < 0) return false;
	wardrobe.list.splice(i, 1);
	save();
	return true;
}

export function moveOutfit(id, toIndex) {
	const from = wardrobe.list.findIndex((x) => x.id === id);
	if (from < 0 || toIndex < 0 || toIndex >= wardrobe.list.length || from === toIndex) return false;
	const [p] = wardrobe.list.splice(from, 1);
	wardrobe.list.splice(toIndex, 0, p);
	save();
	return true;
}
