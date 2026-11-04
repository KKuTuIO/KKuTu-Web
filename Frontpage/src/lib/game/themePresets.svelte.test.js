import { beforeEach, describe, expect, it } from 'vitest';

// The store reads localStorage once at import time, so each test loads a fresh copy.
async function fresh(stored) {
	const data = {};
	if (stored !== undefined) data.themePresets = typeof stored === 'string' ? stored : JSON.stringify(stored);
	globalThis.localStorage = { getItem: (k) => data[k] ?? null, setItem: (k, v) => (data[k] = v) };
	const mod = await import('./themePresets.svelte.js?' + Math.random());
	return { mod, data };
}

describe('theme presets (own 단어장 sets)', () => {
	beforeEach(() => delete globalThis.localStorage);

	it('reads the legacy client\'s stored sets and drops unusable ones', async () => {
		const { mod } = await fresh([{ id: 'a', name: ' 쉬움 ', lang: 'ko', themes: ['x', 'x', 'y', 3] }, { name: '', themes: [] }, { name: 'no themes' }, { name: 'En', lang: 'en', themes: ['z'] }]);
		expect(mod.custom.list.map((p) => [p.id, p.name, p.lang, p.themes])).toEqual([['a', '쉬움', 'ko', ['x', 'y']], ['legacy-3', 'En', 'en', ['z']]]);
	});
	it('survives damaged storage', async () => {
		const { mod } = await fresh('{not json');
		expect(mod.custom.list).toEqual([]);
	});
	it('adds, renames, edits, re-orders and removes, saving every change', async () => {
		const { mod, data } = await fresh();
		const a = mod.addPreset('A', 'ko', ['x']);
		const b = mod.addPreset('B', 'ko', ['y']);
		expect(mod.addPreset('  ', 'ko', [])).toBeNull();
		expect(mod.renamePreset(a.id, ' 에이 ')).toBe(true);
		expect(mod.renamePreset(a.id, '  ')).toBe(false);
		expect(mod.setPresetThemes(b.id, ['y', 'y', 'z'])).toBe(true);
		expect(mod.movePreset(b.id, 0)).toBe(true);
		expect(JSON.parse(data.themePresets).map((p) => [p.name, p.themes])).toEqual([['B', ['y', 'z']], ['에이', ['x']]]);
		expect(mod.removePreset(a.id)).toBe(true);
		expect(JSON.parse(data.themePresets)).toHaveLength(1);
	});
	it('holds at most 10 sets', async () => {
		const { mod } = await fresh();
		for (let i = 0; i < 10; i++) expect(mod.addPreset(`P${i}`, 'ko', ['x'])).not.toBeNull();
		expect(mod.addPreset('11th', 'ko', ['x'])).toBeNull();
		expect(mod.custom.list).toHaveLength(10);
	});
});
