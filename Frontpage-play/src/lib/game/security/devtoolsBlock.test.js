import { describe, expect, it, vi } from 'vitest';
import { blockDevtools } from './devtoolsBlock.js';

function fire(target, type, init) {
	const event = new Event(type, { bubbles: true, cancelable: true, ...init });
	Object.assign(event, init);
	target.dispatchEvent(event);
	return event;
}

describe('blockDevtools', () => {
	it('blocks the right-click menu and F12', () => {
		const stop = blockDevtools();
		expect(fire(window, 'contextmenu').defaultPrevented).toBe(true);
		expect(fire(document, 'keydown', { key: 'F12' }).defaultPrevented).toBe(true);
		stop();
	});

	it.each(['I', 'J', 'C', 'i', 'j', 'c'])('blocks Ctrl/Cmd+Shift+%s but not the bare key or other combinations', (key) => {
		const stop = blockDevtools();
		expect(fire(document, 'keydown', { key, ctrlKey: true, shiftKey: true }).defaultPrevented).toBe(true);
		expect(fire(document, 'keydown', { key, metaKey: true, shiftKey: true }).defaultPrevented).toBe(true);
		expect(fire(document, 'keydown', { key, ctrlKey: true, altKey: true }).defaultPrevented).toBe(true);
		expect(fire(document, 'keydown', { key }).defaultPrevented).toBe(false);
		expect(fire(document, 'keydown', { key, ctrlKey: true }).defaultPrevented).toBe(false);
		stop();
	});

	it('leaves other shortcuts alone and stops listening once removed', () => {
		const stop = blockDevtools();
		expect(fire(document, 'keydown', { key: 'R', ctrlKey: true, shiftKey: true }).defaultPrevented).toBe(false);
		expect(fire(document, 'keydown', { key: 'Escape' }).defaultPrevented).toBe(false);
		stop();
		expect(fire(window, 'contextmenu').defaultPrevented).toBe(false);
		expect(fire(document, 'keydown', { key: 'F12' }).defaultPrevented).toBe(false);
	});
});
