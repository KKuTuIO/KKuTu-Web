/**
 * Sound for the client: a 3-channel mixer (background music / effects / voice+UI, like the legacy client) over Web Audio.
 * Browsers keep audio locked until the user interacts, so playback requests made earlier are remembered and start on the
 * first click or key press. Every call is a harmless no-op when Web Audio is unavailable.
 */
import { DEFAULT_AUDIO, effectiveVolume, lobbyBgmUrl, sanitizeAudio, shopBgmUrl, soundUrl } from './audioCatalog.js';

const STORAGE_KEY = 'kkutu-play-audio';

function load() {
	try {
		return sanitizeAudio(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null'));
	} catch {
		return { ...DEFAULT_AUDIO };
	}
}

/** Reactive settings. Change them through the functions below so they are applied and saved. */
export const audio = $state(load());

let ctx = null;
let master = null;
const gains = {};
const buffers = new Map(); // url -> Promise<AudioBuffer|null>
let unlocked = false;
let wanted = null; // 'lobby' | 'shop' | 'game:<name>' | null
const fading = new Set(); // tracks on their way out during a crossfade
const FADE_MS = 1200;
let current = null; // {src}
let token = 0;

function save() {
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify({ vb: audio.vb, ve: audio.ve, vu: audio.vu, set: audio.set, lobby: audio.lobby, muted: audio.muted }));
	} catch {
		/* private mode: the settings just last for this visit */
	}
}

function applyGains() {
	if (!ctx) return;
	gains.bgm.gain.value = effectiveVolume(audio, 'vb');
	gains.effect.gain.value = effectiveVolume(audio, 've');
	gains.ui.gain.value = effectiveVolume(audio, 'vu');
}

function ensure() {
	if (ctx) return ctx;
	const AC = globalThis.AudioContext ?? globalThis.webkitAudioContext;
	if (!AC) return null;
	try {
		ctx = new AC();
	} catch {
		return null;
	}
	master = ctx.createGain();
	master.connect(ctx.destination);
	for (const ch of ['bgm', 'effect', 'ui']) {
		gains[ch] = ctx.createGain();
		gains[ch].connect(master);
	}
	applyGains();
	return ctx;
}

/**
 * The decoded sound of `url`. When the browser cannot fetch it for Web Audio (CORS) or cannot decode it (some of the files are
 * bare MPEG streams with no tag, which not every browser accepts), the answer is `{ audioUrl }` and the sound is played through
 * an <audio> element instead, as the legacy client does (its AudioSound fallback). Only a missing file gives null.
 */
function buffer(url) {
	if (!buffers.has(url)) {
		buffers.set(
			url,
			fetch(url)
				.then((r) => (r.ok ? r.arrayBuffer() : Promise.reject(new Error(String(r.status)))))
				.then((data) =>
					ctx.decodeAudioData(data).catch((e) => {
						console.warn(`[audio] ${url.split('/').pop()} could not be decoded (${e?.name ?? e}); it plays through an <audio> element`);
						return { audioUrl: url };
					})
				)
				.catch((e) => {
					if (/^\d+$/.test(e?.message ?? '')) {
						buffers.delete(url); // a real HTTP error: allow a retry later
						return null;
					}
					console.warn(`[audio] ${url.split('/').pop()} could not be fetched for Web Audio (${e?.message ?? e}); it plays through an <audio> element`);
					return { audioUrl: url }; // the fetch itself was refused: <audio> does not need CORS
				})
		);
	}
	return buffers.get(url);
}

async function startBgm() {
	const mine = ++token;
	if (!wanted || !ensure()) return stopCurrent();
	// 'lobby' is the waiting-room music, 'shop' the shop's; 'game:<name>' is a track of the sound set that plays in a game (the quiz modes loop one)
	const url = wanted === 'lobby' ? lobbyBgmUrl(audio.lobby) : wanted === 'shop' ? shopBgmUrl() : soundUrl(wanted.slice(5), audio.set);
	// The track that is playing goes on until the new one has arrived, then they are crossfaded.
	const buf = await buffer(url);
	if (!buf || mine !== token || !wanted) return; // missing, or superseded while loading
	const prev = current;
	current = null;
	const fadeIn = !!prev;
	if (prev) fadeOut(prev);
	if (buf.audioUrl) {
		// the music of a file Web Audio can't use: a looping <audio> element (its volume follows the BGM setting, like the legacy fallback)
		const el = new Audio(buf.audioUrl);
		el.loop = true;
		const level = () => effectiveVolume(audio, 'vb');
		el.volume = fadeIn ? 0 : level();
		el.play().catch(() => {});
		const handle = { stop: () => el.pause(), ramp: (to, done) => rampElement(el, to === 1 ? level : () => 0, done) };
		current = { src: handle };
		if (fadeIn) handle.ramp(1);
		return;
	}
	const src = ctx.createBufferSource();
	src.buffer = buf;
	src.loop = true;
	const node = ctx.createGain();
	node.gain.value = fadeIn ? 0 : 1;
	src.connect(node);
	node.connect(gains.bgm);
	src.start();
	const handle = {
		stop: () => {
			src.stop();
			node.disconnect();
		},
		ramp: (to, done) => {
			const t0 = ctx.currentTime;
			node.gain.cancelScheduledValues(t0);
			node.gain.setValueAtTime(node.gain.value, t0);
			node.gain.linearRampToValueAtTime(to, t0 + FADE_MS / 1000);
			if (done) setTimeout(done, FADE_MS + 50);
		}
	};
	current = { src: handle };
	if (fadeIn) handle.ramp(1);
}

/** Moves an <audio> element's volume to `target()` over the fade time. */
function rampElement(el, target, done) {
	const from = el.volume;
	const t0 = performance.now();
	const step = () => {
		const k = Math.min(1, (performance.now() - t0) / FADE_MS);
		el.volume = Math.min(1, Math.max(0, from + (target() - from) * k));
		if (k < 1 && !el.paused) setTimeout(step, 50);
		else done?.();
	};
	step();
}

/** Lets a track fade out and stops it when it is silent. */
function fadeOut(track) {
	fading.add(track);
	const end = () => {
		fading.delete(track);
		try {
			track.src.stop();
		} catch {
			/* already stopped */
		}
	};
	track.src.ramp(0, end);
}

function stopCurrent() {
	for (const track of fading) {
		try {
			track.src.stop();
		} catch {
			/* already stopped */
		}
	}
	fading.clear();
	try {
		current?.src.stop();
	} catch {
		/* already stopped */
	}
	current = null;
}

/** Call from a user gesture (done automatically by `installUnlock`). */
export function unlock() {
	if (!ensure()) return;
	ctx.resume?.();
	if (!unlocked) {
		unlocked = true;
		if (wanted) startBgm();
	}
}

/** Unlocks audio on the first real interaction. Returns a cleanup function. */
export function installUnlock() {
	if (typeof window === 'undefined') return () => {};
	const go = () => unlock();
	const opts = { capture: true, once: true };
	window.addEventListener('pointerdown', go, opts);
	window.addEventListener('keydown', go, opts);
	return () => {
		window.removeEventListener('pointerdown', go, true);
		window.removeEventListener('keydown', go, true);
	};
}

/** Background music for the lobby and waiting rooms. */
export function playLobbyBgm() {
	if (wanted === 'lobby' && current) return;
	wanted = 'lobby';
	if (unlocked) startBgm();
}
/**
 * Fetches and decodes the effects a game uses before they are needed (the legacy client loads its whole sound list at login).
 * Without it, the first time a sound plays it is only played once its file has arrived, which can be after the word it belongs to.
 */
const GAME_SOUNDS = ['mission', 'success', 'fail', 'timeout', 'horr', 'lvup', 'round_start', 'game_start', 'Al', 'kung', ...Array.from({ length: 11 }, (_, i) => `As${i}`), ...Array.from({ length: 11 }, (_, i) => `K${i}`), ...Array.from({ length: 11 }, (_, i) => `T${i}`)];
export function preloadGameSounds() {
	if (!ensure()) return;
	for (const name of GAME_SOUNDS) buffer(soundUrl(name, audio.set));
}

/** The shop's music; crossfades with whatever is playing. */
export function playShopBgm() {
	if (wanted === 'shop' && current) return;
	wanted = 'shop';
	if (unlocked) startBgm();
}

/** Loops a track of the current sound set (e.g. 'JaqwiBGM') as the music of a game. */
export function playGameBgm(name) {
	const key = `game:${name}`;
	if (wanted === key && current) return;
	wanted = key;
	if (unlocked) startBgm();
}
export function stopBgm() {
	wanted = null;
	token++;
	stopCurrent();
}

/**
 * One-shot sound from the active effect set. `ui` sounds ride the voice/UI channel (dialogs, shop), the rest the effect channel.
 * @param {string} name e.g. 'dialog', 'success', 'fail'
 */
export async function playSound(name, { ui = false } = {}) {
	if (!unlocked || !ensure()) return;
	if (effectiveVolume(audio, ui ? 'vu' : 've') <= 0) return;
	const buf = await buffer(soundUrl(name, audio.set));
	if (!buf || !ctx) return;
	if (buf.audioUrl) {
		const el = new Audio(buf.audioUrl);
		el.volume = effectiveVolume(audio, ui ? 'vu' : 've');
		el.play().catch(() => {});
		return { stop: () => el.pause() };
	}
	const src = ctx.createBufferSource();
	src.buffer = buf;
	src.connect(ui ? gains.ui : gains.effect);
	src.start();
	// A handle for sounds that have to be cut short (the turn clock's ticking).
	return { stop: () => { try { src.stop(); } catch { /* already ended */ } } };
}

/** @param {'vb'|'ve'|'vu'} channel @param {number} value 0..1 */
export function setVolume(channel, value) {
	audio[channel] = Math.min(1, Math.max(0, Number(value) || 0));
	applyGains();
	save();
}
export function setMuted(muted) {
	audio.muted = !!muted;
	applyGains();
	save();
}
export function setSoundSet(set) {
	audio.set = set;
	save();
}
export function setLobbyBgm(name) {
	audio.lobby = name;
	save();
	if (wanted === 'lobby' && unlocked) startBgm();
}
