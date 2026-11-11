import { beforeEach, describe, expect, it } from 'vitest';
import { nudges, setNudgeState, nudgeOpen, closeNudge, setNudgeSaver } from './nudges.svelte.js';

describe('nudges', () => {
	let calls;
	beforeEach(() => {
		calls = [];
		setNudgeSaver(async (id) => (calls.push(id), ['tutorial']));
		nudges.session = [];
		setNudgeState({});
	});
	it('shows every nudge to a signed-out player, again on every visit', () => {
		expect(nudgeOpen('tutorial')).toBe(true);
		closeNudge('tutorial');
		expect(nudgeOpen('tutorial')).toBe(false); // this page only
		expect(calls).toEqual([]); // nothing is saved
		nudges.session = []; // a new page
		setNudgeState({ signedIn: false, closed: ['tutorial'] }); // even if a ticket listed some
		expect(nudgeOpen('tutorial')).toBe(true);
	});
	it('takes what the account closed from the ticket and saves a new close on the account', async () => {
		setNudgeState({ signedIn: true, closed: [] });
		expect(nudgeOpen('tutorial')).toBe(true);
		const done = closeNudge('tutorial');
		expect(nudgeOpen('tutorial')).toBe(false); // at once, before the save returns
		await done;
		await closeNudge('tutorial'); // closed already: nothing more is sent
		expect(calls).toEqual(['tutorial']);
		setNudgeState({ signedIn: true, closed: ['tutorial'] });
		expect(nudgeOpen('tutorial')).toBe(false);
	});
	it('ignores ids it does not know, from the ticket and in calls', async () => {
		setNudgeState({ signedIn: true, closed: ['tutorial', 'nope', 'tutorial'] });
		expect(nudges.closed).toEqual(['tutorial']);
		await closeNudge('nope');
		expect(calls).toEqual([]);
	});
	it('keeps the nudge closed on this page when the save fails', async () => {
		setNudgeSaver(() => Promise.reject(new Error('offline')));
		setNudgeState({ signedIn: true, closed: [] });
		await closeNudge('tutorial');
		expect(nudgeOpen('tutorial')).toBe(false);
	});
});
