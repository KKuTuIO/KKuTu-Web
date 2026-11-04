/**
 * What there is to play and where it lives (same CDN layout as the legacy client). Pure, so it is easy to test.
 */
export const AUDIO_CDN = 'https://cdn.kkutu.io/media/';
export const MEDIA_VERSION = '?v=4.1.3c';

/** Lobby (waiting) music choices, in the order the legacy settings dialog listed them. `label` is a locale key. */
export const LOBBY_BGMS = ['LobbyBGM', 'LobbyBGM_NEW', 'LobbyBGM_2024_Fall', 'LobbyBGM_2024_Winter', 'LobbyBGM_2025_8th', 'LobbyBGM_2025_Winter', 'LobbyBGM_2026_Spring', 'LobbyBGM_2026_10th'];
/** Sound-effect sets: each is a folder with the same file names. */
export const SOUND_SETS = ['kkutu', 'kkutuio', 'new_start', 'acoustic'];

export const DEFAULT_AUDIO = { vb: 1, ve: 1, vu: 1, set: 'kkutuio', lobby: 'LobbyBGM_2026_Spring', muted: false };

/** @param {string} name file name without extension, e.g. "dialog" */
export const soundUrl = (name, set = DEFAULT_AUDIO.set) => `${AUDIO_CDN}${set}/${name}.mp3${MEDIA_VERSION}`;
export const lobbyBgmUrl = (lobby = DEFAULT_AUDIO.lobby) => `${AUDIO_CDN}${lobby}.mp3${MEDIA_VERSION}`;
/** The shop's music (`ShopBGM.mp3`). */
export const shopBgmUrl = () => `${AUDIO_CDN}ShopBGM.mp3${MEDIA_VERSION}`;

const clamp01 = (v) => (Number.isFinite(v) ? Math.min(1, Math.max(0, v)) : null);

/** Reads stored settings defensively: unknown keys, wrong types and out-of-range values fall back to the defaults. */
export function sanitizeAudio(raw) {
	const o = raw && typeof raw === 'object' ? raw : {};
	const vol = (k) => clamp01(Number(o[k])) ?? DEFAULT_AUDIO[k];
	return {
		vb: vol('vb'),
		ve: vol('ve'),
		vu: vol('vu'),
		set: SOUND_SETS.includes(o.set) ? o.set : DEFAULT_AUDIO.set,
		lobby: LOBBY_BGMS.includes(o.lobby) ? o.lobby : DEFAULT_AUDIO.lobby,
		muted: o.muted === true
	};
}

/** Effective gain of a channel (0 while muted). */
export const effectiveVolume = (settings, channel) => (settings.muted ? 0 : settings[channel]);
