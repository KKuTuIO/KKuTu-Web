import { beforeEach, describe, expect, it } from 'vitest';
import { progress, setProgress, lessonDone, completeLesson, acceptDone } from './progress.svelte.js';

describe('tutorial progress', () => {
	beforeEach(() => setProgress({}));
	it('takes the cleared lessons from the ticket, ignoring unknown ids', () => {
		setProgress({ signedIn: true, done: ['chain', 'nope', 'chain'] });
		expect(progress.done).toEqual(['chain']);
		expect(lessonDone('chain')).toBe(true);
		expect(lessonDone('room')).toBe(false);
	});
	it('a cleared lesson shows as cleared at once, once', () => {
		completeLesson('room');
		completeLesson('room');
		completeLesson('nope');
		expect(progress.done).toEqual(['room']);
	});
	it('adds what the server says is cleared', () => {
		setProgress({ signedIn: true, done: ['lobby'] });
		acceptDone(['chain', 'ghost', 'lobby']);
		expect(progress.done.sort()).toEqual(['chain', 'lobby']);
	});
});
