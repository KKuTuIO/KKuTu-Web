import { describe, it, expect } from 'vitest';
import { watchIdle } from './autoIdle.js';

function rig(initial = 'online') {
	let status = initial;
	const calls = [];
	let fire = null;
	const target = new EventTarget();
	const stop = watchIdle({
		status: () => status,
		set: (s, auto) => ((status = s), calls.push([s, auto])),
		after: 1000,
		target,
		schedule: (fn) => ((fire = fn), 1),
		cancel: () => (fire = null)
	});
	return { calls, target, stop, tick: () => fire?.(), setStatus: (s) => (status = s) };
}

describe('watchIdle', () => {
	it('goes idle after a quiet spell, and back online on the next input', () => {
		const r = rig();
		r.tick();
		expect(r.calls).toEqual([['idle', true]]);
		r.target.dispatchEvent(new Event('pointermove'));
		expect(r.calls).toEqual([['idle', true], ['online', true]]);
		r.stop();
	});
	it('leaves a status the player chose alone', () => {
		const r = rig('dnd');
		r.tick();
		expect(r.calls).toEqual([]);
		r.target.dispatchEvent(new Event('keydown'));
		expect(r.calls).toEqual([]);
		r.stop();
	});
	it('does not take back an idle the player chose', () => {
		const r = rig('idle');
		r.tick();
		r.target.dispatchEvent(new Event('pointerdown'));
		expect(r.calls).toEqual([]);
		r.stop();
	});
	it('stops watching when stopped', () => {
		const r = rig();
		r.stop();
		r.target.dispatchEvent(new Event('pointermove'));
		expect(r.tick()).toBeUndefined();
		expect(r.calls).toEqual([]);
	});
});
