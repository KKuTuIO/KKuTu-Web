/**
 * The settings file (.kkis, "KKIS"): the legacy client's file for moving settings between browsers. What it can carry, with the legacy keys:
 *   kks (the options, a JSON string of the legacy short keys), block / shut / notes (JSON strings, the legacy storage), themePresets and
 *   wardrobePresets (arrays). A file of the legacy client opens here and the other way round; options this client has and the legacy one
 *   has not are kept under `kkio_...` keys (the legacy client ignores them).
 */
import { encodeContainer, decodeContainer } from './container.js';
import { DEFAULTS } from './prefs.svelte.js';

export const SETTING_PARTS = ['kks', 'block', 'shut', 'notes', 'themePresets', 'wardrobePresets'];

/** legacy short key -> this client's pref */
const PREF_OF = { di: 'denyInvite', dl: 'denyGuestChat', df: 'denyFriend', ar: 'autoReady', ev: 'vibration', es: 'spectatorTag', ul: 'userSort', hr: 'hideRoom', hu: 'hideUsers', ho: 'ignoreSpectators', he: 'hideRecords', abt: 'filter', dwt: 'whisperFrom' };
/** what only this client has */
const OWN = ['dmAlerts', 'itemDock', 'hand'];
const AUDIO = ['vb', 've', 'vu'];

/** The options as the legacy `kks` string. @param {{prefs:object, audio:object}} s */
export function buildKks({ prefs, audio }) {
	const o = {};
	for (const [short, pref] of Object.entries(PREF_OF)) o[short] = prefs[pref];
	for (const k of AUDIO) o[k] = audio[k];
	for (const k of OWN) o[`kkio_${k}`] = prefs[k];
	return JSON.stringify(o);
}

/**
 * What a `kks` string says, as this client's settings: `{prefs, audio}` with only what is there and valid (a stranger's value for a
 * checkbox is not trusted: a boolean pref takes booleans, a choice takes one of its values).
 */
export function readKks(text, { clean } = {}) {
	let o;
	try {
		o = JSON.parse(text);
	} catch {
		return { prefs: {}, audio: {} };
	}
	if (!o || typeof o !== 'object') return { prefs: {}, audio: {} };
	const prefs = {};
	const take = (pref, v) => {
		if (v === undefined) return;
		const cleaned = clean ? clean(pref, v) : v;
		if (cleaned !== undefined && (typeof DEFAULTS[pref] !== 'boolean' || typeof v === 'boolean')) prefs[pref] = cleaned;
	};
	for (const [short, pref] of Object.entries(PREF_OF)) take(pref, o[short]);
	for (const k of OWN) take(k, o[`kkio_${k}`]);
	const audio = {};
	for (const k of AUDIO) if (typeof o[k] === 'number' && o[k] >= 0 && o[k] <= 1) audio[k] = o[k];
	return { prefs, audio };
}

/**
 * The file's bytes for the chosen parts.
 * @param {Record<string, boolean>} selected part -> chosen
 * @param {{kks:()=>string, block:()=>string, shut:()=>string, notes:()=>string, themePresets:()=>any[], wardrobePresets:()=>any[]}} source
 */
export function buildSettingsFile(selected, source) {
	const out = {};
	for (const part of SETTING_PARTS) if (selected[part]) out[part] = source[part]();
	return encodeContainer('KKIS', out);
}

/** @returns {object} the file's content, @throws when it is not a settings file */
export function readSettingsFile(bytes) {
	const data = decodeContainer('KKIS', bytes);
	if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('damaged');
	return data;
}

/** The parts a file has (a part of the list the user may untick). */
export const partsIn = (data) => SETTING_PARTS.filter((p) => data[p] !== undefined && data[p] !== null);

const asObject = (text) => {
	try {
		const o = typeof text === 'string' ? JSON.parse(text) : text;
		return o && typeof o === 'object' && !Array.isArray(o) ? o : null;
	} catch {
		return null;
	}
};

/**
 * Applies the chosen parts. Returns what was applied (a part that is not in the file, or is unusable, is left as it is).
 * @param {object} data the file's content @param {Record<string, boolean>} selected
 * @param {{kks:(s:{prefs:object,audio:object})=>void, block:(o:object)=>void, shut:(o:object)=>void, notes:(o:object)=>void, themePresets:(l:any[])=>void, wardrobePresets:(l:any[])=>void}} target
 */
export function applySettingsFile(data, selected, target, { clean } = {}) {
	const done = [];
	for (const part of SETTING_PARTS) {
		if (!selected[part] || data[part] == null) continue;
		if (part === 'kks') {
			const s = readKks(data.kks, { clean });
			if (!Object.keys(s.prefs).length && !Object.keys(s.audio).length) continue;
			target.kks(s);
		} else if (part === 'themePresets' || part === 'wardrobePresets') {
			if (!Array.isArray(data[part])) continue;
			target[part](data[part]);
		} else {
			const o = asObject(data[part]);
			if (!o) continue;
			// ids and names only: a nickname with markup is plain text everywhere it is shown
			target[part](Object.fromEntries(Object.entries(o).map(([k, v]) => [String(k), String(v)])));
		}
		done.push(part);
	}
	return done;
}
