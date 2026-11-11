/**
 * The player's own 단어장 theme sets ("custom presets"), kept in this browser. Same storage key and shape as the legacy
 * client (`themePresets`: [{id, name, lang, themes}]), so presets made there show up here and the other way round.
 * The official sets come from the server (see protocol.js).
 */
const KEY = 'themePresets';
export const MAX_PRESETS = 10;
export const MAX_NAME = 30;
const MAX_THEMES = 128;

/** One stored entry, or null when it is unusable (same rules as the legacy client). */
export function normalizePreset(p, fallbackId) {
	if (!p || typeof p.name !== 'string' || !Array.isArray(p.themes)) return null;
	const name = p.name.trim().slice(0, MAX_NAME);
	if (!name) return null;
	return {
		id: String(p.id || fallbackId),
		name,
		lang: p.lang === 'en' ? 'en' : 'ko',
		themes: p.themes.filter((x, i, a) => typeof x === 'string' && x.length <= 64 && a.indexOf(x) === i).slice(0, MAX_THEMES)
	};
}

function load() {
	try {
		const stored = JSON.parse(localStorage.getItem(KEY) ?? '[]');
		if (!Array.isArray(stored)) return [];
		return stored.map((p, i) => normalizePreset(p, `legacy-${i}`)).filter(Boolean).slice(0, MAX_PRESETS);
	} catch {
		return []; // private mode or damaged data
	}
}

export const custom = $state({ list: load() });

function save() {
	try {
		localStorage.setItem(KEY, JSON.stringify($state.snapshot(custom.list)));
	} catch {
		/* private mode */
	}
}

const newId = () => `theme-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

/** Adds a preset; returns it, or null when the limit is reached or the name is empty. */
export function addPreset(name, lang, themes) {
	if (custom.list.length >= MAX_PRESETS) return null;
	const p = normalizePreset({ id: newId(), name, lang, themes }, newId());
	if (!p) return null;
	custom.list.push(p);
	save();
	return p;
}

/** Replace the custom presets with a list from a file (unusable ones are dropped, the limit applies). */
export function replacePresets(list) {
	custom.list = (Array.isArray(list) ? list : []).map((p, i) => normalizePreset(p, `imported-${i}`)).filter(Boolean).slice(0, MAX_PRESETS);
	save();
}

export function renamePreset(id, name) {
	const p = custom.list.find((x) => x.id === id);
	const n = (name ?? '').trim().slice(0, MAX_NAME);
	if (!p || !n) return false;
	p.name = n;
	save();
	return true;
}

export function setPresetThemes(id, themes) {
	const p = custom.list.find((x) => x.id === id);
	if (!p) return false;
	p.themes = normalizePreset({ ...p, themes }, id).themes;
	save();
	return true;
}

export function removePreset(id) {
	const i = custom.list.findIndex((x) => x.id === id);
	if (i < 0) return false;
	custom.list.splice(i, 1);
	save();
	return true;
}

/** Moves a preset to another place in the list (drag and drop, or the arrow buttons). */
export function movePreset(id, toIndex) {
	const from = custom.list.findIndex((x) => x.id === id);
	if (from < 0 || toIndex < 0 || toIndex >= custom.list.length || from === toIndex) return false;
	const [p] = custom.list.splice(from, 1);
	custom.list.splice(toIndex, 0, p);
	save();
	return true;
}
