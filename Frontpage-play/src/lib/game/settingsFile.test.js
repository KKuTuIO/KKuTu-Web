import { describe, it, expect } from 'vitest';
import { buildKks, readKks, buildSettingsFile, readSettingsFile, partsIn, applySettingsFile } from './settingsFile.js';
import { decodeContainer } from './container.js';
import { DEFAULTS } from './prefs.svelte.js';

const prefs = { ...DEFAULTS, denyInvite: true, whisperFrom: 'friend', userSort: 'score', dmAlerts: false, itemDock: 'top' };
const audio = { vb: 0.3, ve: 0.5, vu: 1 };

describe('options (kks)', () => {
	it('round-trips with the legacy short keys', () => {
		const kks = JSON.parse(buildKks({ prefs, audio }));
		expect(kks).toMatchObject({ di: true, dwt: 'friend', ul: 'score', vb: 0.3, ve: 0.5, vu: 1, kkio_dmAlerts: false, kkio_itemDock: 'top' });
		const back = readKks(JSON.stringify(kks), { clean: (n, v) => v });
		expect(back.prefs).toMatchObject({ denyInvite: true, whisperFrom: 'friend', userSort: 'score', dmAlerts: false, itemDock: 'top' });
		expect(back.audio).toEqual({ vb: 0.3, ve: 0.5, vu: 1 });
	});
	it('reads what the legacy client wrote (only its keys)', () => {
		const legacy = JSON.stringify({ vb: 0.7, ve: 0.7, vu: 0.7, di: false, dl: true, ev: true, ul: 'entry', abt: 'blind', dwt: 'off', sb: 'x', so: 'y', ow: true });
		const r = readKks(legacy, { clean: (n, v) => v });
		expect(r.prefs).toMatchObject({ denyGuestChat: true, vibration: true, filter: 'blind' });
		expect(Object.keys(r.prefs)).not.toContain('sb');
		expect(r.audio.vb).toBe(0.7);
	});
	it('does not trust the types of a stranger\'s file', () => {
		const r = readKks(JSON.stringify({ di: 'yes', vb: 7, ve: 'loud', hr: 1 }), { clean: (n, v) => v });
		expect(r.prefs).toEqual({});
		expect(r.audio).toEqual({});
	});
	it('a text that is not JSON says nothing', () => {
		expect(readKks('{{')).toEqual({ prefs: {}, audio: {} });
		expect(readKks('5')).toEqual({ prefs: {}, audio: {} });
	});
});

describe('the file', () => {
	const source = { kks: () => buildKks({ prefs, audio }), block: () => '{"a":"A"}', shut: () => '{}', notes: () => '{"b":"memo"}', themePresets: () => [{ id: 't', name: 'n', lang: 'ko', themes: [] }], wardrobePresets: () => [] };
	it('has the legacy container (KKIS) and only the chosen parts', () => {
		const bytes = buildSettingsFile({ kks: true, notes: true, shut: false }, source);
		expect(String.fromCharCode(...bytes.slice(0, 4))).toBe('KKIS');
		const data = readSettingsFile(bytes);
		expect(Object.keys(data).sort()).toEqual(['kks', 'notes']);
		expect(partsIn(data)).toEqual(['kks', 'notes']);
	});
	it('reads a file written by the legacy algorithm', () => {
		const obj = { notes: '{"x":"y"}', themePresets: [] };
		const recBase = btoa(unescape(encodeURIComponent(JSON.stringify(obj))));
		const out = new Uint8Array(recBase.length + 10);
		const key = [33, 99];
		out.set([75, 75, 73, 83]);
		out.set(key, 4);
		for (let i = 0; i < 3; i++) out[6 + i] = (recBase.length >> (i * 8)) & 0xff;
		for (let i = 0; i < recBase.length; i++) out[10 + i] = recBase.charCodeAt(i) ^ key[i % 2];
		expect(readSettingsFile(out)).toEqual(obj);
	});
	it('refuses a replay file, a damaged one, and a list', () => {
		expect(() => decodeContainer('KKIS', new Uint8Array([75, 75, 73, 79, 1, 2, 3, 4, 5, 6, 7]))).toThrow();
		const bytes = buildSettingsFile({ notes: true }, source);
		const bad = bytes.slice();
		bad[12] ^= 0xff;
		expect(() => readSettingsFile(bad)).toThrow();
		expect(() => readSettingsFile(bytes.slice(0, 20))).toThrow();
	});
});

describe('applying', () => {
	const calls = [];
	const target = Object.fromEntries(['kks', 'block', 'shut', 'notes', 'themePresets', 'wardrobePresets'].map((k) => [k, (v) => calls.push([k, v])]));
	it('applies only the ticked parts that are in the file and usable', () => {
		calls.length = 0;
		const data = { kks: JSON.stringify({ di: true }), notes: '{"a":"<b>x</b>","n":5}', block: 'not json', themePresets: 'nope', shut: '{"s":"S"}' };
		const done = applySettingsFile(data, { kks: true, notes: true, block: true, themePresets: true, shut: false, wardrobePresets: true }, target, { clean: (n, v) => v });
		expect(done).toEqual(['kks', 'notes']);
		expect(calls.find((c) => c[0] === 'notes')[1]).toEqual({ a: '<b>x</b>', n: '5' }); // text stays text
		expect(calls.map((c) => c[0])).toEqual(['kks', 'notes']);
	});
});
