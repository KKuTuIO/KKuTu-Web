/**
 * The `identity` packet every connection must send within 15 s of joining (the game server disconnects without it).
 * The server stores it in the connection log, where staff match devices of banned or duplicate accounts.
 *
 * Only hashes leave the browser, never the raw readings. A signal the browser will not give (privacy mode, blocked
 * canvas/WebGL/audio) is 'n/a' (the schema forbids empty strings) and lowers `confidence` to 0, which the server
 * treats as a low-confidence report and does not act on.
 *
 *   pcid        random id kept in localStorage: tells a returning browser
 *   canvasId    a drawn picture, as the graphics stack renders it
 *   webglId     GPU vendor/renderer and limits
 *   audioId     the output of an offline audio graph
 *   mainfpId    browser/screen/timezone traits
 *   fingerjsId  all of the above together
 *   fingerPrint2 same value as fingerjsId (the server keeps both columns; a leading "x" would mark a hard-ban match)
 */
const NA = 'n/a';
const PCID_KEY = 'kkutu-pcid';

async function sha256(text) {
	try {
		const bytes = new TextEncoder().encode(text);
		const digest = await crypto.subtle.digest('SHA-256', bytes);
		return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
	} catch {
		// no SubtleCrypto (insecure context): FNV-1a, 32 bit, still a stable id
		let h = 0x811c9dc5;
		for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 0x01000193) >>> 0;
		return `f${h.toString(16).padStart(8, '0')}`;
	}
}

function withTimeout(promise, ms) {
	return Promise.race([promise, new Promise((resolve) => setTimeout(() => resolve(null), ms))]);
}

function persistentId() {
	try {
		let id = localStorage.getItem(PCID_KEY);
		if (!id || !/^[0-9a-f]{32}$/.test(id)) {
			id = [...crypto.getRandomValues(new Uint8Array(16))].map((b) => b.toString(16).padStart(2, '0')).join('');
			localStorage.setItem(PCID_KEY, id);
		}
		return id;
	} catch {
		return null; // storage blocked: this browser cannot be recognised again
	}
}

function canvasReading() {
	const canvas = document.createElement('canvas');
	canvas.width = 240;
	canvas.height = 60;
	const ctx = canvas.getContext('2d');
	if (!ctx) return null;
	ctx.textBaseline = 'top';
	ctx.font = '16px Arial, sans-serif';
	ctx.fillStyle = '#f60';
	ctx.fillRect(100, 1, 62, 20);
	ctx.fillStyle = '#069';
	ctx.fillText('KKuTu 끄투 <canvas> 1.0, \u{1F600}', 2, 15);
	ctx.fillStyle = 'rgba(102, 204, 0, 0.7)';
	ctx.fillText('KKuTu 끄투 <canvas> 1.0, \u{1F600}', 4, 17);
	const gradient = ctx.createLinearGradient(0, 0, 240, 0);
	gradient.addColorStop(0, '#f0f');
	gradient.addColorStop(1, '#0ff');
	ctx.strokeStyle = gradient;
	ctx.beginPath();
	ctx.arc(190, 40, 14, 0, Math.PI * 2);
	ctx.stroke();
	return canvas.toDataURL();
}

function webglReading() {
	const canvas = document.createElement('canvas');
	const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
	if (!gl) return null;
	const debug = gl.getExtension('WEBGL_debug_renderer_info');
	const parts = [
		debug ? gl.getParameter(debug.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR),
		debug ? gl.getParameter(debug.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER),
		gl.getParameter(gl.VERSION),
		gl.getParameter(gl.MAX_TEXTURE_SIZE),
		gl.getParameter(gl.MAX_VERTEX_ATTRIBS),
		gl.getParameter(gl.MAX_VIEWPORT_DIMS)?.join('x'),
		(gl.getSupportedExtensions() ?? []).join(',')
	];
	return parts.join('|');
}

async function audioReading() {
	const Offline = globalThis.OfflineAudioContext || globalThis.webkitOfflineAudioContext;
	if (!Offline) return null;
	const ctx = new Offline(1, 5000, 44100);
	const osc = ctx.createOscillator();
	osc.type = 'triangle';
	osc.frequency.value = 10000;
	const compressor = ctx.createDynamicsCompressor();
	for (const [name, value] of [['threshold', -50], ['knee', 40], ['ratio', 12], ['attack', 0], ['release', 0.25]]) {
		if (compressor[name]?.setValueAtTime) compressor[name].setValueAtTime(value, ctx.currentTime);
	}
	osc.connect(compressor);
	compressor.connect(ctx.destination);
	osc.start(0);
	const buffer = await ctx.startRendering();
	let sum = 0;
	const data = buffer.getChannelData(0);
	for (let i = 4500; i < data.length; i++) sum += Math.abs(data[i]);
	return sum.toString();
}

function traitsReading() {
	const n = globalThis.navigator ?? {};
	const s = globalThis.screen ?? {};
	return [
		n.userAgent,
		(n.languages ?? [n.language]).join(','),
		n.platform,
		n.hardwareConcurrency,
		n.deviceMemory,
		n.maxTouchPoints,
		s.width,
		s.height,
		s.colorDepth,
		globalThis.devicePixelRatio,
		Intl.DateTimeFormat().resolvedOptions().timeZone,
		new Date().getTimezoneOffset()
	].join('|');
}

async function hashed(read) {
	try {
		const value = await read();
		return value ? await sha256(String(value)) : null;
	} catch {
		return null;
	}
}

/** Collect the identity packet's fields. Never rejects; slow probes are cut off after `timeoutMs`. */
export async function collectIdentity({ timeoutMs = 5000 } = {}) {
	const [canvas, webgl, audio, main] = await Promise.all([
		withTimeout(hashed(canvasReading), timeoutMs),
		withTimeout(hashed(webglReading), timeoutMs),
		withTimeout(hashed(audioReading), timeoutMs),
		withTimeout(hashed(traitsReading), timeoutMs)
	]);
	const pcid = persistentId();
	const complete = !!(canvas && webgl && audio && main && pcid);
	const device = await sha256([main, canvas, webgl, audio].map((v) => v ?? NA).join('|'));
	return {
		fingerPrint2: device,
		fingerjsId: device,
		mainfpId: main ?? NA,
		audioId: audio ?? NA,
		webglId: webgl ?? NA,
		canvasId: canvas ?? NA,
		pcid: pcid ?? NA,
		confidence: complete ? 1 : 0
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
