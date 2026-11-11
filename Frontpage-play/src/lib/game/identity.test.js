import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { collectIdentity, djb2, identity, resetIdentityForTests } from './identity.js';
import { md5 } from './md5.js';

const fpjs = vi.hoisted(() => ({ get: async () => ({ visitorId: 'bundled-visitor-id' }), fail: false }));
vi.mock('@fingerprintjs/fingerprintjs', () => ({
	load: async () => {
		if (fpjs.fail) throw new Error('could not run');
		return { get: fpjs.get };
	}
}));

let local;
let session;
const storage = (data) => ({ getItem: (k) => data[k] ?? null, setItem: (k, v) => (data[k] = String(v)) });
beforeEach(() => {
	local = {};
	session = {};
	vi.stubGlobal('localStorage', storage(local));
	vi.stubGlobal('sessionStorage', storage(session));
	document.cookie = 'IPI=; max-age=0; path=/';
});
afterEach(() => {
	vi.unstubAllGlobals();
	resetIdentityForTests();
});

const good = { canvas: () => 'c4nv4s', webgl: () => 'w3bgl', audio: () => '124.04', fingerprint: () => 'visitor1' };
// the server schema (Ingress.ts): non-empty strings up to 2048 (pcid 256), confidence 0 or 1
function expectSchema(r) {
	for (const k of ['fingerPrint2', 'fingerjsId', 'mainfpId', 'audioId', 'webglId', 'canvasId']) {
		expect(typeof r[k]).toBe('string');
		expect(r[k].length).toBeGreaterThan(0);
		expect(r[k].length).toBeLessThanOrEqual(2048);
	}
	expect(r.pcid.length).toBeGreaterThan(0);
	expect(r.pcid.length).toBeLessThanOrEqual(256);
	expect([0, 1]).toContain(r.confidence);
}

describe('identity (port of the legacy getIdentityData)', () => {
	it('hashes like the legacy client, so a returning browser matches its old log entries', () => {
		// the legacy implementation, copied from in_game_kkutu_security.js
		const legacy = (raw) => {
			let h = 5381;
			for (let i = 0; i < raw.length; i++) {
				const chr = raw.charCodeAt(i);
				h = (h << 5) + h + chr;
				h |= 0;
			}
			return Math.abs(h).toString(16);
		};
		for (const text of ['', 'abc', 'Google Inc. (NVIDIA)::ANGLE (NVIDIA, GeForce RTX 3060)', 'data:image/png;base64,' + 'iVBORw0KGgo'.repeat(400)]) expect(djb2(text)).toBe(legacy(text));
	});

	it('a trustworthy device: confidence 1, fingerPrint2 is "x" + the visitor id, mainfpId the MD5 of canvas|webgl|audio', async () => {
		const r = await collectIdentity({ readers: good });
		expectSchema(r);
		expect(r).toMatchObject({ confidence: 1, fingerjsId: 'visitor1', fingerPrint2: 'xvisitor1', canvasId: 'c4nv4s', webglId: 'w3bgl', audioId: '124.04' });
		expect(r.mainfpId).toBe(md5('c4nv4s|w3bgl|124.04'));
	});

	it('a reading that was cut off, tampered with or taken by automation makes it low confidence ("b")', async () => {
		for (const [name, reader] of [
			['tampered function', () => { throw 'TD'; }],
			['automation', () => { throw 'IB'; }],
			['blocked loader', () => { throw 'NF'; }],
			['too slow', () => new Promise(() => {})]
		]) {
			const r = await collectIdentity({ timeoutMs: 30, readers: { ...good, canvas: reader } });
			expectSchema(r);
			expect(r.confidence, name).toBe(0);
			expect(r.fingerPrint2, name).toBe('bvisitor1');
			expect(r.canvasId, name).toBe('no_canvas');
		}
		// an ordinary failure (no WebGL here) is not suspicious by itself, as in the legacy script
		const plain = await collectIdentity({ readers: { ...good, webgl: () => { throw new Error('no gl'); } } });
		expect(plain.confidence).toBe(1);
		expect(plain.webglId).toBe('no_webgl');
	});

	it('FingerprintJS blocked (uBlock Origin and the like): low confidence and a "b" id, never an "x" id shared by every blocked browser', async () => {
		const r = await collectIdentity({ readers: { ...good, fingerprint: () => { throw 'NF'; } } });
		expectSchema(r);
		expect(r.fingerJsId ?? r.fingerjsId).toBe('no_fingerprintjs');
		expect(r.fingerPrint2).toBe('bno_fingerprintjs');
		expect(r.confidence).toBe(0);
	});

	it('uses the FingerprintJS that ships with the app (no download from a third-party host)', async () => {
		const fetched = [];
		vi.stubGlobal('fetch', (u) => (fetched.push(String(u)), Promise.reject(new Error('no network'))));
		const { fingerprint, ...rest } = good;
		const r = await collectIdentity({ readers: rest });
		expect(r.fingerjsId).toBe('bundled-visitor-id');
		expect(r.fingerPrint2).toBe('xbundled-visitor-id');
		expect(r.confidence).toBe(1);
		expect(fetched).toEqual([]);
	});

	it('the bundled library failing to run is low confidence too', async () => {
		fpjs.fail = true;
		try {
			const { fingerprint, ...rest } = good;
			const r = await collectIdentity({ readers: rest });
			expect(r.fingerjsId).toBe('no_fingerprintjs');
			expect(r.fingerPrint2).toBe('bno_fingerprintjs');
			expect(r.confidence).toBe(0);
		} finally {
			fpjs.fail = false;
		}
	});

	it('still fits the schema where nothing can be read (jsdom has no canvas, WebGL or audio)', async () => {
		const r = await collectIdentity({ timeoutMs: 100, readers: { fingerprint: () => 'visitor1' } });
		expectSchema(r);
		expect(JSON.stringify(r)).not.toContain(navigator.userAgent); // only derived values go out
	});

	it('keeps one UUID in cookie, localStorage and sessionStorage, and any one of them brings it back', async () => {
		const a = await collectIdentity({ readers: good });
		expect(a.pcid).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
		expect(local.IPI).toBe(a.pcid);
		expect(session.IPI).toBe(a.pcid);
		expect(document.cookie).toContain(`IPI=${a.pcid}`);
		// storage wiped, the cookie remains
		delete local.IPI;
		delete session.IPI;
		expect((await collectIdentity({ readers: good })).pcid).toBe(a.pcid);
		expect(local.IPI).toBe(a.pcid); // and is written back
		// the cookie gone as well, only localStorage left
		document.cookie = 'IPI=; max-age=0; path=/';
		delete session.IPI;
		expect((await collectIdentity({ readers: good })).pcid).toBe(a.pcid);
	});

	it('answers with an ephemeral id and low confidence when storage is blocked', async () => {
		const blocked = { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); } };
		vi.stubGlobal('localStorage', blocked);
		const r = await collectIdentity({ readers: good });
		expectSchema(r);
		expect(r.pcid).toMatch(/^ephemeral_[0-9a-f]{24}$/);
		expect(r.confidence).toBe(0);
	});

	it('is collected once per page (a reconnect reuses it)', async () => {
		expect(identity({ readers: good })).toBe(identity({ readers: good }));
	});
});
