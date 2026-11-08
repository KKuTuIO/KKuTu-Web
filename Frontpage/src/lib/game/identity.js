/**
 * The `identity` packet every connection sends within 15 s of joining (the game server disconnects without it) and the server
 * stores in the connection log, where staff match devices of banned or duplicate accounts.
 *
 * Port of the legacy `getIdentityData` (`in_game_kkutu_security.js`). The values are computed the way the old client did, to the
 * letter (same drawing, same hashes, same storage key), because the log is matched by value: a returning player on the same
 * browser must give the same ids as before or an old ban no longer finds them.
 *
 *   pcid         `IPI`, a random UUID kept in a cookie, localStorage and sessionStorage together (any one of them restores it)
 *   canvasId     djb2 hash of a drawn picture
 *   webglId      djb2 hash of "unmasked vendor::renderer"
 *   audioId      sum of an offline audio graph's output
 *   fingerjsId   FingerprintJS v5 `visitorId` (MIT licence). The old client downloaded it from openfpcdn.io at run time; that host
 *                is blocked by content blockers and by a content-security-policy, so it is now part of the app's own files (loaded
 *                only when the identity is collected). If it still cannot run the id is the fixed word "no_fingerprintjs" and the
 *                report is LOW confidence. The legacy script called that case fine, so every blocked browser sent the same id with the
 *                "x" prefix, and the server's HARD_ matching saw them all as one device.
 *   mainfpId     MD5 of "canvas|webgl|audio"
 *   fingerPrint2 "x" + fingerjsId, or "b" + fingerjsId when the readings are not trustworthy (the server turns "x" into HARD_...)
 *   confidence   1, or 0 when any reading was cut off, blocked, tampered with (a replaced canvas/WebGL/audio function), taken
 *                by an automation driver (`navigator.webdriver`) or storage was unavailable. The server records but does not act on 0.
 *
 * Only these derived values are sent, never the raw readings. A reading the browser withholds is a fixed word
 * ("no_canvas", ...), since the server's schema forbids empty strings.
 */
import { md5 } from './md5.js';
import { isNative } from './security/native.js';

const KEY = 'IPI';

export function djb2(text) {
	let h = 5381;
	for (let i = 0; i < text.length; i++) h = (((h << 5) + h) + text.charCodeAt(i)) | 0;
	return Math.abs(h).toString(16);
}

function uuid() {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
		const r = (crypto.getRandomValues(new Uint8Array(1))[0] & 15) | 0;
		return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
	});
}

function readCookie() {
	const m = document.cookie.match(new RegExp(`(?:^|; )${KEY}=([^;]*)`));
	return m ? decodeURIComponent(m[1]) : null;
}

/** Throws when storage is not available (the caller then reports low confidence). */
function persistentId() {
	const cookie = readCookie();
	const local = localStorage.getItem(KEY);
	const session = sessionStorage.getItem(KEY);
	const id = cookie || local || session || uuid();
	if (!cookie) document.cookie = `${KEY}=${encodeURIComponent(id)}; max-age=${365 * 86400}; path=/`;
	if (!local) localStorage.setItem(KEY, id);
	if (!session) sessionStorage.setItem(KEY, id);
	return id;
}

/** Each reading resolves to its value or rejects with 'IB' (automation) / 'TD' (a browser function was replaced) / 'NF' (not loadable). */
function canvasReading() {
	if (navigator.webdriver) throw 'IB';
	const canvas = document.createElement('canvas');
	if (!isNative(canvas.toDataURL) || !isNative(canvas.getContext)) throw 'TD';
	const ctx = canvas.getContext('2d');
	canvas.width = 280;
	canvas.height = 60;
	ctx.textBaseline = 'alphabetic';
	ctx.fillStyle = '#f60';
	ctx.fillRect(125, 1, 62, 20);
	ctx.fillStyle = '#069';
	ctx.font = '11pt no-real-font-123';
	ctx.fillText('KKuTuIo_g', 2, 15);
	ctx.fillStyle = 'rgba(102, 204, 0, 0.7)';
	ctx.font = '11pt Arial';
	ctx.fillText('KKuTuIo_g', 4, 17);
	ctx.globalCompositeOperation = 'multiply';
	ctx.fillStyle = 'rgb(255,0,255)';
	ctx.beginPath();
	ctx.arc(50, 50, 50, 0, Math.PI * 2, true);
	ctx.closePath();
	ctx.fill();
	ctx.fillStyle = 'rgb(0,255,255)';
	ctx.beginPath();
	ctx.arc(100, 50, 50, 0, Math.PI * 2, true);
	ctx.closePath();
	ctx.fill();
	const uri = canvas.toDataURL();
	if (!uri.length) throw 'no_canvas';
	return djb2(uri);
}

function webglReading() {
	const canvas = document.createElement('canvas');
	if (!isNative(canvas.getContext)) throw 'TD';
	const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
	if (!gl) return 'no_webgl';
	if (!isNative(gl.getParameter)) throw 'TD';
	const debug = gl.getExtension('WEBGL_debug_renderer_info');
	if (!debug) return 'no_webgl';
	return djb2(`${gl.getParameter(debug.UNMASKED_VENDOR_WEBGL)}::${gl.getParameter(debug.UNMASKED_RENDERER_WEBGL)}`);
}

async function audioReading() {
	const Offline = globalThis.OfflineAudioContext || globalThis.webkitOfflineAudioContext;
	if (!Offline) return 'no_audio';
	const ctx = new Offline(1, 44100, 44100);
	if (!isNative(ctx.startRendering)) throw 'TD';
	const osc = ctx.createOscillator();
	const compressor = ctx.createDynamicsCompressor();
	osc.type = 'triangle';
	osc.frequency.value = 10000;
	compressor.threshold.value = -50;
	compressor.knee.value = 40;
	compressor.ratio.value = 12;
	compressor.attack.value = 0;
	compressor.release.value = 0.25;
	osc.connect(compressor);
	compressor.connect(ctx.destination);
	osc.start(0);
	try {
		const data = (await ctx.startRendering()).getChannelData(0);
		let sum = 0;
		for (let i = 0; i < data.length; i++) sum += Math.abs(data[i]);
		return sum.toString();
	} catch {
		return 'audio_error';
	}
}

async function fingerprintReading() {
	if (navigator.webdriver) throw 'TD';
	try {
		const FingerprintJS = await import('@fingerprintjs/fingerprintjs');
		const load = FingerprintJS.load ?? FingerprintJS.default?.load;
		const result = await (await load()).get();
		if (!result?.visitorId) throw 'TD';
		return result.visitorId;
	} catch (e) {
		if (e === 'TD') throw e;
		throw 'NF'; // could not be loaded (blocked, offline): the fixed word below, and low confidence
	}
}

/** A reading with a time limit: `{ val, B }`, B meaning "not trustworthy" (cut off, tampered, automated). */
function task(read, fallback, ms) {
	return new Promise((resolve) => {
		let done = false;
		const timer = setTimeout(() => !done && ((done = true), resolve({ val: fallback, B: true })), ms);
		Promise.resolve()
			.then(read)
			.then((val) => !done && ((done = true), clearTimeout(timer), resolve({ val, B: false })))
			.catch((err) => !done && ((done = true), clearTimeout(timer), resolve({ val: fallback, B: err === 'IB' || err === 'TD' || err === 'NF' })));
	});
}

/** Collect the identity packet's fields. Never rejects. `readers` is for tests. */
export async function collectIdentity({ timeoutMs = 2000, readers = {} } = {}) {
	let pcid = 'no_pcid';
	let storageUnavailable = false;
	try {
		pcid = persistentId();
	} catch {
		storageUnavailable = true;
		try {
			pcid = `ephemeral_${[...crypto.getRandomValues(new Uint8Array(12))].map((v) => v.toString(16).padStart(2, '0')).join('')}`;
		} catch {
			pcid = `ephemeral_${Math.random().toString(36).slice(2)}`;
		}
	}
	const [canvas, webgl, audio, fp] = await Promise.all([
		task(readers.canvas ?? canvasReading, 'no_canvas', timeoutMs),
		task(readers.webgl ?? webglReading, 'no_webgl', timeoutMs),
		task(readers.audio ?? audioReading, 'no_audio', timeoutMs),
		task(readers.fingerprint ?? fingerprintReading, 'no_fingerprintjs', timeoutMs)
	]);
	let low = storageUnavailable || canvas.B || webgl.B || audio.B || fp.B;
	let mainfpId;
	try {
		mainfpId = md5(`${canvas.val}|${webgl.val}|${audio.val}`);
	} catch {
		mainfpId = 'no_mainfp';
		low = true;
	}
	return {
		fingerPrint2: (low ? 'b' : 'x') + fp.val,
		fingerjsId: fp.val,
		mainfpId,
		audioId: audio.val,
		webglId: webgl.val,
		canvasId: canvas.val,
		pcid,
		confidence: low ? 0 : 1
	};
}

let cached;
/** One collection per page: a reconnect reuses it. */
export function identity(options) {
	return (cached ??= collectIdentity(options));
}

export function resetIdentityForTests() {
	cached = undefined;
}
