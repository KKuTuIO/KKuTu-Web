import { beforeEach, describe, expect, it } from 'vitest';
import { DEFAULTS, prefs, setPref } from './prefs.svelte.js';

describe('prefs', () => {
	const store = {};
	globalThis.localStorage = { getItem: (k) => store[k] ?? null, setItem: (k, v) => (store[k] = String(v)) };
	beforeEach(() => Object.assign(prefs, DEFAULTS));
	it('starts from the legacy defaults', () => {
		expect(prefs.filter).toBe('blind');
		expect(prefs.vibration).toBe(true);
		expect(prefs.spectatorTag).toBe(true);
		expect(prefs.whisperFrom).toBe('off');
		expect(prefs.hideUsers).toBe(false);
	});
	it('saves every change and ignores values it does not know', () => {
		setPref('whisperFrom', 'friend');
		setPref('hideRoom', true);
		setPref('filter', 'nonsense');
		setPref('nope', true);
		expect(prefs.whisperFrom).toBe('friend');
		expect(prefs.hideRoom).toBe(true);
		expect(prefs.filter).toBe('blind');
		expect('nope' in prefs).toBe(false);
		expect(JSON.parse(store['kkutu-play-prefs']).whisperFrom).toBe('friend');
	});
});
