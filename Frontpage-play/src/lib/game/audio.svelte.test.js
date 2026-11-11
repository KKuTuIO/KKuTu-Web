import { describe, expect, it } from 'vitest';
import { isResultBgm, isTutorialBgm, playLobbyBgm, playResultBgm, playTutorialBgm, stopBgm, wantedBgm } from './audio.svelte.js';

// (no Web Audio here: only which track is wanted is looked at)
describe('the result screen music', () => {
	it('holds against the lobby music until the result is closed, then gives way to it', () => {
		stopBgm();
		playResultBgm();
		expect(isResultBgm()).toBe(true);
		playLobbyBgm(); // the page asking for the lobby music while the result is up: ignored
		expect(isResultBgm()).toBe(true);
		playLobbyBgm({ fromResult: true }); // the result was closed: the lobby takes over (a crossfade in the player)
		expect(isResultBgm()).toBe(false);
		stopBgm();
	});
});

describe('the tutorial music', () => {
	it('holds against the lobby music until the tutorial ends, then gives way to it', () => {
		stopBgm();
		playTutorialBgm();
		expect(isTutorialBgm()).toBe(true);
		playLobbyBgm(); // the page asking for the lobby music while the tutorial plays: ignored
		playLobbyBgm({ fromResult: true }); // (even as it does after a result)
		expect(isTutorialBgm()).toBe(true);
		playLobbyBgm({ fromResult: true, fromTutorial: true }); // the tutorial is over
		expect(isTutorialBgm()).toBe(false);
		stopBgm();
	});
	it('gives way to the result screen\'s music of the practice game, and takes over again when the result is closed', () => {
		stopBgm();
		playTutorialBgm();
		playResultBgm();
		expect(isResultBgm()).toBe(true);
		expect(isTutorialBgm()).toBe(false);
		playTutorialBgm(); // something asking for it while the result is up: ignored
		expect(isResultBgm()).toBe(true);
		playTutorialBgm({ fromResult: true });
		expect(isTutorialBgm()).toBe(true);
		expect(isResultBgm()).toBe(false);
		stopBgm();
	});
	it('is stopped for a game, and wanted again afterwards', () => {
		stopBgm();
		playTutorialBgm();
		expect(wantedBgm()).toBe('tutorial');
		stopBgm({ fade: true }); // a game of the practice: no music (its sounds are the turn clock's and the round's)
		expect(wantedBgm()).toBe(null);
		expect(isTutorialBgm()).toBe(false);
		playTutorialBgm({ fromResult: true }); // the game is over
		expect(wantedBgm()).toBe('tutorial');
		stopBgm();
	});
});
