import lobby from './scripts/lobby.js';
import chain from './scripts/chain.js';
import room from './scripts/room.js';

/**
 * The lessons of 학사모레미's tutorial, in the order of the cards. Titles and descriptions are locale keys
 * (`play.tutorial.lesson.<id>.title` / `.desc`); `preview` is the picture on the card.
 */
export const LESSONS = [
	{ id: 'lobby', icon: 'home', script: lobby },
	{ id: 'chain', icon: 'sports_esports', script: chain },
	{ id: 'room', icon: 'add_circle', script: room }
];
/** What a signed-in player gets for the first clear of each lesson (the game server pays it: TutorialReward.ts). */
export const TUTORIAL_PING = 100;
export const lessonById = (id) => LESSONS.find((l) => l.id === id);
