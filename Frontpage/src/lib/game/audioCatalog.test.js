import { describe, expect, it } from 'vitest';
import { DEFAULT_AUDIO, LOBBY_BGMS, SOUND_SETS, effectiveVolume, lobbyBgmUrl, sanitizeAudio, shopBgmUrl, soundUrl } from './audioCatalog.js';

describe('audio catalog', () => {
	it('builds legacy CDN urls', () => {
		expect(soundUrl('dialog', 'acoustic')).toBe('https://cdn.kkutu.io/media/acoustic/dialog.mp3?v=4.1.3c');
		expect(lobbyBgmUrl('LobbyBGM_NEW')).toBe('https://cdn.kkutu.io/media/LobbyBGM_NEW.mp3?v=4.1.3c');
		expect(shopBgmUrl()).toBe('https://cdn.kkutu.io/media/ShopBGM.mp3?v=4.1.3c');
	});
	it('sanitizes stored settings', () => {
		expect(sanitizeAudio(null)).toEqual(DEFAULT_AUDIO);
		// out-of-range volumes are clamped; non-numbers, unknown names and non-booleans fall back to the defaults
		expect(sanitizeAudio({ vb: 5, ve: -1, vu: 'x', set: 'nope', lobby: 'nope', muted: 'yes' })).toEqual({ ...DEFAULT_AUDIO, vb: 1, ve: 0 });
		const ok = sanitizeAudio({ vb: 0.3, ve: 0, vu: 0.8, set: SOUND_SETS[0], lobby: LOBBY_BGMS[1], muted: true });
		expect(ok).toEqual({ vb: 0.3, ve: 0, vu: 0.8, set: SOUND_SETS[0], lobby: LOBBY_BGMS[1], muted: true });
	});
	it('mutes all channels together', () => {
		expect(effectiveVolume({ vb: 0.5, muted: false }, 'vb')).toBe(0.5);
		expect(effectiveVolume({ vb: 0.5, muted: true }, 'vb')).toBe(0);
	});
});
