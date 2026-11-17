/**
 * Sound for the client: a 3-channel mixer (background music / effects / voice+UI, like the legacy client) over Web Audio.
 * Browsers keep audio locked until the user interacts, so playback requests made earlier are remembered and start on the
 * first click or key press. Every call is a harmless no-op when Web Audio is unavailable.
 */
import { DEFAULT_AUDIO, effectiveVolume, leagueSoundUrl, uiSoundUrl, lobbyBgmUrl, resultBgmUrl, sanitizeAudio, shopBgmUrl, soundUrl, tutorialBgmUrl } from './audioCatalog.js';

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
let wanted = null; // 'lobby' | 'shop' | 'result' | 'tutorial' | 'game:<name>' | null
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

/**
 * 트로피 리그 로비의 음악: 인트로를 한 번 재생하다가 루프 부분으로 넘어가 그 뒤로 계속 되풀이한다(두 파일은 media/league/bgm-intro.mp3, bgm-loop.mp3).
 * 인트로 파일은 루프가 시작하는 지점보다 LEAGUE_BGM_OVERLAP 초 더 길게 잘려 있고, 그 겹치는 구간에서 인트로는 줄고 루프는 커져서 이어진다(MP3 디코드가
 * 몇 ms 어긋나도 이음매가 들리지 않게). 루프 파일은 버퍼 전체가 한 바퀴다. 두 파일을 못 쓰면 로비 음악으로 대신한다.
 */
const LEAGUE_BGM_OVERLAP = 0.12;
async function startLeagueBgm(mine) {
	const [intro, loop] = await Promise.all([buffer(leagueSoundUrl('bgm-intro')), buffer(leagueSoundUrl('bgm-loop'))]);
	if (mine !== token || wanted !== 'league') return;
	if (!intro || !loop || intro.audioUrl || loop.audioUrl) {
		wanted = 'lobby'; // 리그 음악을 쓸 수 없으면 로비 음악
		return startBgm();
	}
	const prev = current;
	current = null;
	const fadeIn = !!prev;
	if (prev) fadeOut(prev);
	const node = ctx.createGain();
	node.gain.value = fadeIn ? 0 : 1;
	node.connect(gains.bgm);
	const t0 = ctx.currentTime + 0.05;
	const join = t0 + intro.duration - LEAGUE_BGM_OVERLAP; // 루프가 시작하는 때
	const gi = ctx.createGain();
	const gl = ctx.createGain();
	gi.connect(node);
	gl.connect(node);
	gi.gain.setValueAtTime(1, t0);
	gi.gain.setValueAtTime(1, join);
	gi.gain.linearRampToValueAtTime(0, join + LEAGUE_BGM_OVERLAP);
	gl.gain.setValueAtTime(0, t0);
	gl.gain.setValueAtTime(0, join);
	gl.gain.linearRampToValueAtTime(1, join + LEAGUE_BGM_OVERLAP);
	const si = ctx.createBufferSource();
	si.buffer = intro;
	si.connect(gi);
	si.start(t0);
	si.stop(join + LEAGUE_BGM_OVERLAP + 0.05);
	const sl = ctx.createBufferSource();
	sl.buffer = loop;
	sl.loop = true;
	sl.loopStart = 0;
	sl.loopEnd = loop.duration;
	sl.connect(gl);
	sl.start(join);
	const handle = {
		stop: () => {
			for (const x of [si, sl]) {
				try {
					x.stop();
				} catch {
					/* already stopped */
				}
			}
			node.disconnect();
		},
		ramp: (to, done) => {
			const now = ctx.currentTime;
			node.gain.cancelScheduledValues(now);
			node.gain.setValueAtTime(node.gain.value, now);
			node.gain.linearRampToValueAtTime(to, now + FADE_MS / 1000);
			if (done) setTimeout(done, FADE_MS + 50);
		}
	};
	current = { src: handle };
	if (fadeIn) handle.ramp(1);
}

async function startBgm() {
	const mine = ++token;
	if (!wanted || !ensure()) return stopCurrent();
	if (wanted === 'league') return startLeagueBgm(mine);
	// 'lobby' is the waiting-room music, 'shop' the shop's; 'game:<name>' is a track of the sound set that plays in a game (the quiz modes loop one)
	const url = wanted === 'lobby' ? lobbyBgmUrl(audio.lobby) : wanted === 'shop' ? shopBgmUrl() : wanted === 'result' ? resultBgmUrl() : wanted === 'tutorial' ? tutorialBgmUrl() : soundUrl(wanted.slice(5), audio.set);
	// The track that is playing goes on until the new one has arrived, then they are crossfaded.
	const buf = await buffer(url);
	if (!buf || mine !== token || !wanted) return; // missing, or superseded while loading
	const prev = current;
	current = null;
	const fadeIn = !!prev || wanted === 'result' || wanted === 'tutorial'; // the result's and the tutorial's music always come in softly, over the previous track or out of silence
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
export function playLobbyBgm({ fromResult = false, fromTutorial = false } = {}) {
	if (wanted === 'result' && !fromResult) return; // the result screen's music goes on until it is closed
	if (wanted === 'tutorial' && !fromTutorial) return; // and so does the tutorial's, until the tutorial ends (the page asks for the lobby's whenever its state changes)
	if (wanted === 'result') wanted = null; // closed: the lobby music takes over from it by a crossfade
	if (wanted === 'lobby' && current) return;
	wanted = 'lobby';
	if (unlocked) startBgm();
}
/**
 * Fetching and decoding happens once per page load (the files themselves sit in the browser's HTTP cache for two weeks), so the first
 * time a sound plays it would have to be fetched and decoded first, a hitch of up to half a second for the music. The functions here
 * do that ahead of time, a few files at a time so they do not crowd out the page's own requests. They never reject: a file that is
 * missing is just played when it is asked for (and fails then).
 */
function loadAll(urls, limit = 4) {
	const queue = [...new Set(urls)];
	const worker = async () => {
		while (queue.length) await buffer(queue.shift());
	};
	return Promise.all(Array.from({ length: Math.min(limit, queue.length) }, worker)).then(() => {});
}

/** The small sounds of the interface: a dialog opening, the shop, the chat's tick, a level up. The loading screen waits for these. */
const UI_SOUNDS = ['dialog', 'shop', 'k', 'lvup'];
export function preloadUiSounds() {
	if (!ensure()) return Promise.resolve();
	return loadAll(UI_SOUNDS.map((n) => soundUrl(n, audio.set)));
}

/** The music that starts on a screen change: the result's and the shop's, and a quiz mode's two tracks when the room plays one. */
export function preloadBgms({ quiz = false } = {}) {
	if (!ensure()) return Promise.resolve();
	return loadAll([resultBgmUrl(), shopBgmUrl(), ...(quiz ? ['JaqwiBGM', 'JaqwiFastBGM'].map((n) => soundUrl(n, audio.set)) : [])], 2);
}

/**
 * Fetches and decodes the effects a game uses before they are needed (the legacy client loads its whole sound list at login).
 * Without it, the first time a sound plays it is only played once its file has arrived, which can be after the word it belongs to.
 * Called when I am in a room, so a game that starts finds everything in memory. Resolves when the effects and the music are ready.
 */
const GAME_SOUNDS = ['mission', 'success', 'fail', 'timeout', 'horr', 'lvup', 'round_start', 'game_start', 'Al', 'kung', ...Array.from({ length: 11 }, (_, i) => `As${i}`), ...Array.from({ length: 11 }, (_, i) => `K${i}`), ...Array.from({ length: 11 }, (_, i) => `T${i}`)];
const gamePreloads = new Map(); // `${set}:${quiz}` -> Promise: one pass per sound set, not one per room packet
export function preloadGameSounds({ quiz = false } = {}) {
	if (!ensure()) return Promise.resolve();
	const key = `${audio.set}:${quiz}`;
	if (!gamePreloads.has(key)) gamePreloads.set(key, loadAll(GAME_SOUNDS.map((name) => soundUrl(name, audio.set))).then(() => preloadBgms({ quiz })));
	return gamePreloads.get(key);
}

/** True while the result screen's music is the one wanted. */
export const isResultBgm = () => wanted === 'result';
/** True while the tutorial's music is the one wanted. */
export const isTutorialBgm = () => wanted === 'tutorial';

/**
 * The tutorial's music, looped from the first lesson to the end of the tutorial (it crossfades with what played before). The result screen's
 * music of the practice game takes over while that is up (`fromResult`: it is closed, and this one comes back).
 */
export function playTutorialBgm({ fromResult = false } = {}) {
	if (wanted === 'result' && !fromResult) return;
	if (wanted === 'tutorial' && current) return;
	wanted = 'tutorial';
	if (unlocked) startBgm();
}

/** The result screen's music, looped; it crossfades with the game's and holds until `stopBgm`. */
export function playResultBgm() {
	if (wanted === 'result' && current) return;
	wanted = 'result';
	if (unlocked) startBgm();
}

/** 트로피 리그 로비의 음악(인트로 한 번, 그다음 루프); 재생 중인 곡과 교차한다. */
export function playLeagueBgm() {
	if (wanted === 'league' && current) return;
	wanted = 'league';
	if (unlocked) startBgm();
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
/** Stops the music; with `fade` the track that plays fades out instead of being cut off. */
export function stopBgm({ fade = false } = {}) {
	wanted = null;
	token++;
	if (fade && current) {
		const prev = current;
		current = null;
		fadeOut(prev);
		return;
	}
	stopCurrent();
}
/** Which track is wanted now: 'lobby' | 'shop' | 'league' | 'result' | 'tutorial' | 'game:<name>' | null. */
export const wantedBgm = () => wanted;

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

/**
 * A fixed file (not part of a sound set), played once or `loop`ed seamlessly until `stop()`; the handle's `stop(fade)` fades it out (seconds) and ends it.
 * The file must start and end on the same beat to loop without a seam: the buffer loops over its whole length. Resolves to null while the audio is locked,
 * muted, or the file is missing.
 * @param {string} url
 * @param {{ui?:boolean, loop?:boolean, gain?:number}} [opts] `gain` scales the file's own loudness (1 = as it is)
 * @returns {Promise<{stop:(fade?:number)=>void}|null>}
 */
const missingFiles = new Map(); // url -> when it was found missing (a file that is not on the CDN yet is not asked for again every time, e.g. on every hover)
const MISSING_RETRY_MS = 5 * 60_000;
export async function playFile(url, { ui = false, loop = false, gain = 1 } = {}) {
	if (!unlocked || !ensure()) return null;
	const vol = effectiveVolume(audio, ui ? 'vu' : 've');
	if (vol <= 0) return null;
	const gone = missingFiles.get(url);
	if (gone && performance.now() - gone < MISSING_RETRY_MS) return null;
	const buf = await buffer(url);
	if (!buf) missingFiles.set(url, performance.now());
	if (!buf || !ctx) return null;
	if (buf.audioUrl) {
		const el = new Audio(buf.audioUrl);
		el.loop = loop;
		el.volume = Math.min(1, vol * gain);
		el.play().catch(() => {});
		return { stop: () => el.pause() };
	}
	const src = ctx.createBufferSource();
	src.buffer = buf;
	src.loop = loop;
	const g = ctx.createGain();
	g.gain.value = gain;
	src.connect(g);
	g.connect(ui ? gains.ui : gains.effect);
	src.start();
	return {
		stop(fade = 0.12) {
			const now = ctx.currentTime;
			g.gain.cancelScheduledValues(now);
			g.gain.setValueAtTime(g.gain.value, now);
			g.gain.linearRampToValueAtTime(0.0001, now + fade);
			try { src.stop(now + fade + 0.02); } catch { /* already ended */ }
		}
	};
}

/** The sound every button makes ('click' when pressed, 'hover' when the mouse comes over it). Quiet and short; follows the interface volume. */
export const playUi = (name) => playFile(uiSoundUrl(name), { ui: true });
/** Loads the button sounds ahead, so the first press is not late. */
export function preloadUiFiles() {
	if (!unlocked || !ensure()) return Promise.resolve();
	return loadAll([uiSoundUrl('click'), uiSoundUrl('hover')]);
}

/** Loads the league's sounds ahead (the match jingle, the game's own `mission` sound, the reel's spin and lock; the lobby music loads when it is asked for), so the spin starts the instant the reel opens (call from a click: the audio is unlocked then). */
export function preloadLeagueSounds() {
	if (!unlocked || !ensure()) return Promise.resolve();
	return loadAll([leagueSoundUrl('spin'), leagueSoundUrl('lock'), leagueSoundUrl('matched'), soundUrl('mission', audio.set)]);
}

/**
 * A short sound made on the spot (no file): `notes` is a list of `{ at, hz, to?, dur, type?, vol? }` (seconds from now, a start and an optional end
 * pitch that it glides to, a length, a wave shape, a loudness 0..1). It goes through the same channel mixer as the recorded sounds, so the volume
 * and the mute switch of the settings apply. A no-op while the audio is locked or unavailable.
 * @param {{at?:number, hz:number, to?:number, dur:number, type?:OscillatorType, vol?:number}[]} notes
 */
export function playTone(notes, { ui = false } = {}) {
	if (!unlocked || !ensure()) return;
	if (effectiveVolume(audio, ui ? 'vu' : 've') <= 0) return;
	const t0 = ctx.currentTime + 0.01;
	for (const n of notes) {
		const start = t0 + (n.at ?? 0);
		const end = start + n.dur;
		const osc = ctx.createOscillator();
		const g = ctx.createGain();
		osc.type = n.type ?? 'sine';
		osc.frequency.setValueAtTime(n.hz, start);
		if (n.to) osc.frequency.exponentialRampToValueAtTime(n.to, end);
		// a quick attack and an exponential fade, so a note never clicks at its edges
		const vol = Math.max(0.0002, (n.vol ?? 0.5) * 0.6);
		g.gain.setValueAtTime(0.0001, start);
		g.gain.exponentialRampToValueAtTime(vol, start + Math.min(0.008, n.dur / 4));
		g.gain.exponentialRampToValueAtTime(0.0001, end);
		osc.connect(g);
		g.connect(ui ? gains.ui : gains.effect);
		osc.start(start);
		osc.stop(end + 0.02);
	}
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
	preloadUiSounds(); // the new set's sounds are fetched now, not at the first click
}
export function setLobbyBgm(name) {
	audio.lobby = name;
	save();
	if (wanted === 'lobby' && unlocked) startBgm();
}
