import { describe, expect, it, vi } from 'vitest';
import { createRunner } from './runner.svelte.js';
import { checkScript, scriptKeys } from './script.js';

const say = (text, extra = {}) => ({ say: { who: 'haksamo', text }, ...extra });
const tick = () => new Promise((r) => setTimeout(r, 0));

describe('script checks', () => {
	it('finds unknown targets, repeated ids, unknown speakers and empty nodes', () => {
		const errors = checkScript({ cast: ['haksamo'], nodes: [say('a', { id: 'x', next: 'nope' }), say('b', { id: 'x' }), { say: { who: 'ghost', text: 'c' } }, {}] });
		expect(errors).toHaveLength(4);
		expect(checkScript({ cast: ['haksamo'], nodes: [say('a', { id: 'x' }), { goto: 'x' }] })).toEqual([]);
	});
	it('lists the locale keys a script uses', () => {
		expect(scriptKeys({ nodes: [say('k.a'), { choice: [{ text: 'k.b', goto: 'x' }] }, say('k.a')] }).sort()).toEqual(['k.a', 'k.b']);
	});
});

describe('runner', () => {
	const script = { cast: ['haksamo'], nodes: [say('one'), say('two', { focus: '#x' }), say('three'), { end: true }] };

	it('goes line by line with 다음 and ends the script', async () => {
		const end = vi.fn();
		const r = createRunner(script, { end });
		r.start();
		expect(r.state.line.text).toBe('one');
		expect(r.state.canGoBack).toBe(false);
		r.advance();
		await tick();
		expect(r.state.line.text).toBe('two');
		expect(r.state.focus).toBe('#x');
		expect(r.state.canGoBack).toBe(true);
		r.advance();
		await tick();
		r.advance();
		await tick();
		expect(r.state.done).toBe(true);
		expect(end).toHaveBeenCalledWith({ skipped: false });
	});
	it('goes back to the line before', async () => {
		const r = createRunner(script);
		r.start();
		r.advance();
		await tick();
		r.advance();
		await tick();
		r.back();
		await tick();
		expect(r.state.line.text).toBe('two');
		r.back();
		await tick();
		expect(r.state.line.text).toBe('one');
	});
	it('can be skipped', async () => {
		const end = vi.fn();
		const r = createRunner(script, { end });
		r.start();
		r.skip();
		expect(r.state.done && r.state.skipped).toBe(true);
		expect(end).toHaveBeenCalledWith({ skipped: true });
		r.advance(); // nothing happens after the end
		expect(r.state.line).toBe(null);
	});
	it('branches on a choice', async () => {
		const r = createRunner({ cast: ['haksamo'], nodes: [{ choice: [{ text: 'a', goto: 'A' }, { text: 'b', goto: 'B' }], say: { who: 'haksamo', text: 'q' } }, say('A!', { id: 'A', next: 'Z' }), say('B!', { id: 'B' }), { id: 'Z', end: true }] });
		r.start();
		expect(r.state.choices).toHaveLength(2);
		expect(r.state.canAdvance).toBe(false);
		r.choose(1);
		await tick();
		expect(r.state.line.text).toBe('B!');
	});
	it('waits for an event, and ignores other events', async () => {
		const r = createRunner({ cast: ['haksamo'], nodes: [{ wait: 'ready', say: { who: 'haksamo', text: 'do it' } }, say('done')] });
		r.start();
		expect(r.state.canAdvance).toBe(false);
		r.emit('other');
		await tick();
		expect(r.state.line.text).toBe('do it');
		r.emit('ready');
		await tick();
		expect(r.state.line.text).toBe('done');
	});
	it('runs a stage and an action, and lets the stage pick where to go', async () => {
		const calls = [];
		const host = {
			do: async (name) => void calls.push(`do:${name}`),
			stage: async (name, node, api) => {
				calls.push(`stage:${name}`);
				api.tip('hint');
				return 'win';
			}
		};
		const r = createRunner({ cast: ['haksamo'], nodes: [{ do: 'open' }, { stage: 'play' }, say('lost', { id: 'lose' }), say('won', { id: 'win' })] }, host);
		r.start();
		await tick();
		expect(calls).toEqual(['do:open', 'stage:play']);
		expect(r.state.line.text).toBe('won');
		expect(r.state.tip).toBe(null); // the next node replaces a tip
	});
	it('shows a tip over the line while a stage runs', async () => {
		let release;
		const r = createRunner({ cast: ['haksamo'], nodes: [say('intro'), { stage: 'play' }, { end: true }] }, { stage: (n, node, api) => new Promise((res) => ((release = res), api.tip('try again', { params: { n: 1 } }))) });
		r.start();
		r.advance();
		await tick();
		expect(r.state.busy).toBe(true);
		expect(r.state.tip).toMatchObject({ text: 'try again', who: 'haksamo', params: { n: 1 } });
		release();
		await tick();
		expect(r.state.done).toBe(true);
	});
	it('lets a stage point at part of the page with a label, and the pointer ends with the stage', async () => {
		let release;
		const r = createRunner({ cast: ['haksamo'], nodes: [say('intro', { focus: '#a' }), { stage: 'play' }, say('after')] }, { stage: (n, node, api) => new Promise((res) => ((release = res), api.focus('#field', 'type this'))) });
		r.start();
		r.advance();
		await tick();
		expect(r.state.focus).toBe('#field');
		expect(r.state.focusLabel).toBe('type this');
		release();
		await tick();
		expect(r.state.focus).toBe(null);
		expect(r.state.focusLabel).toBe('');
		expect(r.state.line.text).toBe('after');
	});
	it('a later run cancels an earlier stage', async () => {
		let release;
		const r = createRunner({ cast: ['haksamo'], nodes: [{ id: 's', stage: 'play' }, say('after')] }, { stage: () => new Promise((res) => (release = res)) });
		r.start();
		r.skip();
		release();
		await tick();
		expect(r.state.line).toBe(null);
	});
});

describe('runner: lines that wait', () => {
	it('a stage line waits for 다음 and the stage goes on when it is pressed, with the bubble put where it asks', async () => {
		let lineDone = false;
		const r = createRunner({ cast: ['haksamo'], nodes: [{ at: 'top', stage: 'play' }, { end: true }] }, {
			stage: async (n, node, api) => {
				await api.line('k.explain', { params: { x: 1 }, at: 'bottom' });
				lineDone = true;
			}
		});
		r.start();
		await tick();
		expect(r.state.tip).toMatchObject({ text: 'k.explain', wait: true, params: { x: 1 } });
		expect(r.state.canAdvance).toBe(true);
		expect(r.state.at).toBe('bottom');
		expect(lineDone).toBe(false);
		r.advance();
		await tick();
		expect(lineDone).toBe(true);
		expect(r.state.at).toBe('top'); // back where it was
		expect(r.state.done).toBe(true);
	});
	it('after it is pressed the line stays on screen without a 다음, until the next one', async () => {
		let api;
		const r = createRunner({ cast: ['haksamo'], nodes: [{ stage: 'play' }] }, { stage: (n, node, a) => new Promise(() => (api = a)) });
		r.start();
		await tick();
		api.line('k.one');
		r.advance();
		expect(r.state.canAdvance).toBe(false);
		expect(r.state.tip).toMatchObject({ text: 'k.one', wait: false });
		r.advance(); // nothing waits: nothing happens
		expect(r.state.done).toBe(false);
	});
});
