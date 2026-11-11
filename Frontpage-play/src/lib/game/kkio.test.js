import { describe, it, expect } from 'vitest';
import { encodeKkio, decodeKkio, checkRecording, kkioName, sanitizeRecording, KKIO_MAX_BODY } from './kkio.js';

const rec = () => ({
	version: 'v3.0.0',
	me: 'u1',
	time: Date.UTC(2025, 10, 5, 4, 5, 9),
	title: '한글 제목 🙂',
	players: [{ id: 'u1', score: 0, data: { score: 100 }, equip: {}, title: '#u1' }],
	events: [{ time: 0, data: { type: 'starting' } }, { time: 1500, data: { type: 'turnEnd', value: '가나다' } }]
});

describe('kkio container', () => {
	it('round-trips a recording, including non-ASCII text', () => {
		const out = decodeKkio(encodeKkio(rec(), [100, 200]));
		expect(out).toEqual(rec());
	});
	it('has the legacy header: KKIO, key, 3-byte length, a zero', () => {
		const bytes = encodeKkio(rec(), [100, 200]);
		expect([...bytes.slice(0, 6)]).toEqual([75, 75, 73, 79, 100, 200]);
		const len = bytes[6] + (bytes[7] << 8) + (bytes[8] << 16);
		expect(len).toBe(bytes.length - 10);
		expect(bytes[9]).toBe(0);
	});
	it('reads what the legacy client wrote (its algorithm, copied)', () => {
		const r = rec();
		const recBase = btoa(unescape(encodeURIComponent(JSON.stringify(r))));
		const data = new Uint8Array(recBase.length + 10);
		const key = [77, 190];
		data.set([75, 75, 73, 79]);
		data.set(key, 4);
		for (let pad = 0; pad < 3; pad++) data[6 + pad] = (recBase.length & (0xff << (pad * 8))) >> (pad * 8);
		for (let i = 0; i < recBase.length; i++) data[10 + i] = recBase.charCodeAt(i) ^ key[i % 2];
		expect(decodeKkio(data)).toEqual(r);
	});
	it('refuses what is not a replay', () => {
		expect(() => decodeKkio(new Uint8Array([1, 2, 3]))).toThrow();
		const bytes = encodeKkio(rec());
		expect(() => decodeKkio(bytes.slice(0, bytes.length - 3))).toThrow(); // length mismatch
		const bad = bytes.slice();
		bad[9] = 1;
		expect(() => decodeKkio(bad)).toThrow();
		const garbled = bytes.slice();
		garbled[20] ^= 0xff;
		expect(() => decodeKkio(garbled)).toThrow();
	});
	it('refuses a recording with nothing in it', () => {
		expect(() => checkRecording({ time: 1, players: [], events: [{ time: 0, data: { type: 'x' } }] })).toThrow();
		expect(() => checkRecording({ time: 1, players: [{}], events: [] })).toThrow();
		expect(() => checkRecording({ time: 0, players: [{}], events: [{ time: 0, data: { type: 'x' } }] })).toThrow();
		expect(() => checkRecording({ time: 1, players: [{}], events: [{ time: 0, data: {} }] })).toThrow();
	});
	it('cannot write more than the format holds', () => {
		const big = { ...rec(), events: [{ time: 0, data: { type: 'x', pad: 'a'.repeat(KKIO_MAX_BODY) } }] };
		expect(() => encodeKkio(big)).toThrow('too large');
	});
	it('names the file like the legacy client', () => {
		const d = new Date(rec().time);
		expect(kkioName(rec())).toBe(`KKuTuIO${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${d.getHours()}-${d.getMinutes()}-${d.getSeconds()}.kkio`);
	});
	it('makes markup in a file harmless', () => {
		const s = sanitizeRecording({ a: ['<img src=x onerror=1>', 3], b: { c: '<b>' } });
		expect(JSON.stringify(s)).not.toMatch(/[<>]/);
		expect(s.a[1]).toBe(3);
	});
});
