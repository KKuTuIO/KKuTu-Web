import lobby from './scripts/lobby.js';
import lobbyMatch from './scripts/lobbyMatch.js';
import { prefs } from '../prefs.svelte.js';
import chain from './scripts/chain.js';
import room from './scripts/room.js';

/**
 * The lessons of 학사모레미's tutorial, in the order of the cards. Titles and descriptions are locale keys
 * (`play.tutorial.lesson.<id>.title` / `.desc`); `preview` is the picture on the card.
 */
export const LESSONS = [
	// the tour follows the lobby the player has (설정 > 로비 선택): the stage and 게임 시작!, or the room list
	{ id: 'lobby', icon: 'home', get script() { return prefs.lobbyStyle === 'match' ? lobbyMatch : lobby; } },
	{ id: 'chain', icon: 'sports_esports', script: chain },
	{ id: 'room', icon: 'add_circle', script: room }
];
/** What a signed-in player gets for the first clear of each lesson (the game server pays it: TutorialReward.ts). */
export const TUTORIAL_PING = 100;
export const lessonById = (id) => LESSONS.find((l) => l.id === id);
