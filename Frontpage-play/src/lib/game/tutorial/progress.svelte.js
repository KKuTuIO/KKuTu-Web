/**
 * Which tutorial lessons I have cleared. A signed-in account keeps them on the server (`account.flags.tutorialDone`, an array of lesson ids,
 * brought by the ticket); a signed-out player's last for this page only. Only the game server writes the flag, because a lesson cleared for the
 * first time pays 100 핑: the client sends `tutorialClear` (see TutorialSession) and the answer, `tutorialDone`, says what is cleared.
 */
export const LESSON_IDS = ['lobby', 'chain', 'room']; // keep in step with AccountService.LESSON_IDS and the game server's TutorialReward.ts

const clean = (ids) => (Array.isArray(ids) ? [...new Set(ids.filter((id) => LESSON_IDS.includes(id)))] : []);

export const progress = $state({ signedIn: false, done: /** @type {string[]} */ ([]) });

/** From the ticket. */
export function setProgress({ signedIn = false, done = [] } = {}) {
	progress.signedIn = signedIn;
	progress.done = clean(done);
}
export const lessonDone = (id) => progress.done.includes(id);

/** A lesson was cleared: it shows as cleared at once (a signed-in player's clear is also sent to the server by the caller). */
export function completeLesson(id) {
	if (LESSON_IDS.includes(id) && !lessonDone(id)) progress.done = [...progress.done, id];
}

/** The server's list of what the account has cleared (`tutorialDone`). */
export function acceptDone(ids) {
	progress.done = [...new Set([...progress.done, ...clean(ids)])];
}
