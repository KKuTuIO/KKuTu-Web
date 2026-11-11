import { describe, expect, it } from 'vitest';
import { createTelemetry, diffText } from './telemetry.js';

/** The server's reconstruction (InputTrace.ts decodeCompactUpdates): previous text + [prefix, suffix, inserted] = next text. */
function replay(batches, initial = '') {
	let text = Array.from(initial);
	for (const batch of batches) {
		for (const tuple of batch.u) {
			if (tuple.length === 7) {
				const [, , , , prefix, suffix, inserted] = tuple;
				expect(prefix + suffix).toBeLessThanOrEqual(text.length);
				text = [...text.slice(0, prefix), ...Array.from(inserted), ...(suffix ? text.slice(text.length - suffix) : [])];
			}
		}
	}
	return text.join('');
}

/** What the server's Ingress schema accepts for an `inputTrace` (requireCompactInputTraceUpdates). */
function expectValidPacket(p) {
	expect(Object.keys(p).every((k) => ['type', 'v', 'c', 'q', 'p', 'r', 'u'].includes(k))).toBe(true);
	expect(p.type).toBe('inputTrace');
	expect(p.v).toBe(1);
	expect(Number.isInteger(p.c) && p.c >= 0 && p.c <= 0xffffffff).toBe(true);
	expect(Number.isInteger(p.q) && p.q >= 0).toBe(true);
	expect(p.u.length).toBeGreaterThanOrEqual(1);
	expect(p.u.length).toBeLessThanOrEqual(8);
	for (const t of p.u) {
		expect([4, 7]).toContain(t.length);
		const [delta, event, type, flag] = t;
		expect(Number.isInteger(delta) && delta >= 0 && delta <= 3_600_000).toBe(true);
		expect(Number.isInteger(event) && event >= 0 && event <= 4).toBe(true);
		expect(typeof type === 'string' ? type.length <= 48 : Number.isInteger(type) && type >= 0 && type <= 7).toBe(true);
		expect([0, 1]).toContain(flag);
	}
}

/** Dispatch events on a real element through the action, like the browser would (jsdom events are untrusted, hence trustedOnly: false below). */
function dom(options = {}) {
	const sent = [];
	let clock = 1000;
	const timers = [];
	const telemetry = createTelemetry({
		send: (type, data) => sent.push({ type, ...data }),
		now: () => clock,
		trustedOnly: false,
		setTimer: (fn) => (timers.push(fn), timers.length),
		clearTimer: (id) => (timers[id - 1] = null),
		...options
	});
	const input = document.createElement('input');
	let scope = {};
	const action = telemetry.observe(input, () => scope);
	const type = (name, value, init = {}) => {
		input.value = value;
		const event = name === 'input' || name === 'beforeinput' ? new InputEvent(name, { inputType: init.inputType ?? 'insertText', isComposing: !!init.composing }) : new CompositionEvent(name);
		input.dispatchEvent(event);
	};
	return {
		telemetry,
		sent,
		input,
		type,
		setScope: (s) => (scope = s),
		advance: (ms) => (clock += ms),
		runTimers: () => timers.splice(0).forEach((fn) => fn?.()),
		destroy: () => action.destroy()
	};
}
const start = (t, context = 123456) => t.turnStart({ type: 'turnStart', inputTraceContext: context });

describe('diffText', () => {
	it('names the common front and back and what sits between (code points, like the server)', () => {
		expect(diffText([], Array.from('가'))).toEqual([0, 0, '가']);
		expect(diffText(Array.from('위병'), Array.from('위병소'))).toEqual([2, 0, '소']);
		expect(diffText(Array.from('위병소'), Array.from('위병'))).toEqual([2, 0, '']);
		expect(diffText(Array.from('위벼'), Array.from('위병'))).toEqual([1, 0, '병']);
		expect(diffText(Array.from('가나다'), Array.from('가라다'))).toEqual([1, 1, '라']);
		expect(diffText(Array.from('a\u{1F600}b'), Array.from('a\u{1F600}cb'))).toEqual([2, 1, 'c']);
	});
});

describe('input trace', () => {
	it('streams what is typed as it happens, and the server can rebuild the word from it', () => {
		const d = dom();
		start(d.telemetry);
		// 위병소 on a Korean IME: ㅇ 우 위 위ㅂ 위벼 위병 위병ㅅ 위병소
		d.type('compositionstart', '');
		for (const v of ['ㅇ', '우', '위', '위ㅂ', '위벼', '위병', '위병ㅅ']) {
			d.advance(80);
			d.type('beforeinput', d.input.value, { inputType: 'insertCompositionText', composing: true });
			d.type('input', v, { inputType: 'insertCompositionText', composing: true });
		}
		d.type('input', '위병소', { inputType: 'insertCompositionText', composing: true });
		d.type('compositionend', '위병소');
		const last = d.telemetry.take();
		for (const p of d.sent) expectValidPacket(p);
		expect(replay(d.sent)).toBe('위병소');
		expect(last).toBe(d.sent.reduce((n, p) => n + p.u.length, 0) - 1); // lastInputSeq = the last update sent
		// sequence numbers are exactly consecutive across batches
		let expected = 0;
		for (const p of d.sent) {
			expect(p.q).toBe(expected);
			expected += p.u.length;
		}
		expect(d.sent.every((p) => p.c === 123456)).toBe(true);
	});

	it('codes the DOM events and input types the way the server reads them', () => {
		const d = dom();
		start(d.telemetry);
		d.type('beforeinput', '', { inputType: 'insertText' });
		d.type('input', '가', { inputType: 'insertText' });
		d.type('beforeinput', '가', { inputType: 'deleteContentBackward' });
		d.type('input', '', { inputType: 'deleteContentBackward' });
		d.type('beforeinput', '', { inputType: 'insertFromPaste' });
		d.telemetry.take();
		const tuples = d.sent.flatMap((p) => p.u);
		expect(tuples.map((t) => [t[1], t[2], t[3]])).toEqual([
			[0, 1, 0],
			[1, 1, 0],
			[0, 4, 0],
			[1, 4, 0],
			[0, 'insertFromPaste', 0] // unusual types stay readable for an operator
		]);
		expect(tuples[1].slice(4)).toEqual([0, 0, '가']); // the change rides on the event that first shows it
		expect(tuples[3].slice(4)).toEqual([0, 0, '']);
		expect(tuples[0]).toHaveLength(4); // nothing changed yet: the short form
	});

	it('flags composition, and a composition event has no input type', () => {
		const d = dom();
		start(d.telemetry);
		d.type('compositionstart', '');
		d.type('input', 'ㄱ', { inputType: 'insertCompositionText', composing: true });
		d.type('compositionend', 'ㄱ');
		d.telemetry.take();
		const t = d.sent.flatMap((p) => p.u);
		expect(t.map((x) => [x[1], x[2], x[3]])).toEqual([
			[2, 0, 1],
			[1, 2, 1],
			[4, 0, 0]
		]);
	});

	it('sends at most 8 updates per packet at once, otherwise after 50 ms, and always before the word', () => {
		const d = dom();
		start(d.telemetry);
		for (let i = 0; i < 9; i++) d.type('input', '가'.repeat(i + 1));
		expect(d.sent).toHaveLength(1); // the 8th filled a batch
		expect(d.sent[0].u).toHaveLength(8);
		expect(d.sent[0].q).toBe(0);
		d.runTimers(); // the 9th waits for the timer
		expect(d.sent).toHaveLength(2);
		expect(d.sent[1].q).toBe(8);
		d.type('input', '가'.repeat(10));
		expect(d.sent).toHaveLength(2); // still queued ...
		const last = d.telemetry.take(); // ... and goes out ahead of the talk that follows
		expect(d.sent).toHaveLength(3);
		expect(last).toBe(9);
	});

	it('a submit ends the trace: numbering starts again at 0 and the server is told there was nothing when nothing was typed', () => {
		const d = dom();
		start(d.telemetry);
		expect(d.telemetry.take()).toBe(-1);
		d.type('input', '가');
		expect(d.telemetry.take()).toBe(0);
		d.type('input', '나');
		d.telemetry.take();
		expect(d.sent.map((p) => p.q)).toEqual([0, 0]);
		expect(replay([d.sent[1]])).toBe('나'); // the second word is traced from an empty field, not from the first
		expect(d.telemetry.take()).toBe(-1);
	});

	it('a new turn is a new context: queued updates of the old one are dropped, numbering restarts', () => {
		const d = dom();
		start(d.telemetry, 1);
		d.type('input', '가');
		start(d.telemetry, 2);
		d.runTimers();
		expect(d.sent).toHaveLength(0); // the old context would be refused as stale
		d.type('input', '나');
		d.telemetry.take();
		expect(d.sent).toHaveLength(1);
		expect(d.sent[0]).toMatchObject({ c: 2, q: 0 });
	});

	it('sends nothing before a turn started or when the server gave no context', () => {
		const d = dom();
		d.type('input', '가');
		expect(d.telemetry.take()).toBe(-1);
		d.telemetry.turnStart({ type: 'turnStart' }); // an old server
		d.type('input', '가');
		expect(d.telemetry.take()).toBe(-1);
		expect(d.sent).toHaveLength(0);
	});

	it('names the scope with `r`, leaves it out for a turn scope, and a new scope is a new trace', () => {
		const d = dom();
		start(d.telemetry);
		d.setScope({ r: [1, 4, 2, 0] }); // a crossword bar
		d.type('input', '가');
		d.type('input', '가나');
		d.setScope({ r: [1, 5, 2, 1] }); // another bar
		d.type('input', '다');
		d.telemetry.take();
		expect(d.sent).toHaveLength(2);
		expect(d.sent[0]).toMatchObject({ q: 0, r: [1, 4, 2, 0] });
		expect(d.sent[0].u).toHaveLength(2); // what was typed for the first bar went out under it
		expect(d.sent[1]).toMatchObject({ q: 0, r: [1, 5, 2, 1] }); // numbering restarted for the new scope
		const turn = dom();
		start(turn.telemetry);
		turn.type('input', '가');
		turn.telemetry.take();
		expect('r' in turn.sent[0]).toBe(false);
	});

	it('traces nothing while the screen has no scope for the field (null)', () => {
		const d = dom();
		start(d.telemetry);
		d.setScope(null);
		d.type('input', '가');
		expect(d.telemetry.take()).toBe(-1);
		expect(d.sent).toHaveLength(0);
	});

	it('leaves out events a script dispatched: the word then has no trace the server could match', () => {
		const sent = [];
		const telemetry = createTelemetry({ send: (t, d) => sent.push(d) }); // trustedOnly is the default
		const input = document.createElement('input');
		telemetry.observe(input, () => ({}));
		telemetry.turnStart({ inputTraceContext: 5 });
		input.value = '정답';
		input.dispatchEvent(new InputEvent('input', { inputType: 'insertText' })); // isTrusted false
		expect(telemetry.take()).toBe(-1);
		expect(sent).toHaveLength(0);
	});

	it('stops listening when the field goes away', () => {
		const d = dom();
		start(d.telemetry);
		d.destroy();
		d.type('input', '가');
		expect(d.telemetry.take()).toBe(-1);
	});

	it('reports the time between updates as diagnostics, never negative or beyond the schema', () => {
		const d = dom();
		start(d.telemetry);
		d.advance(250);
		d.type('input', '가');
		d.advance(10_000_000);
		d.type('input', '가나');
		d.telemetry.take();
		const t = d.sent.flatMap((p) => p.u);
		expect(t[0][0]).toBe(250);
		expect(t[1][0]).toBe(3_600_000);
	});

	it('traces chat typed while another player is on turn as pending (p:1, no r) and the turn takes it over without a restart', () => {
		const d = dom();
		d.telemetry.turnStart({ gameId: 'g', inputTraceContext: 11 }); // someone else's turn
		d.setScope({ pending: true });
		d.type('input', '가');
		d.type('input', '가나');
		d.runTimers();
		expect(d.sent).toHaveLength(1);
		expect(d.sent[0]).toMatchObject({ c: 11, q: 0, p: 1 });
		expect('r' in d.sent[0]).toBe(false);
		expectValidPacket(d.sent[0]);
		d.telemetry.turnStart({ gameId: 'g', inputTraceContext: 12 }); // my turn starts: new context, same trace
		d.setScope({}); // the turn scope
		d.type('input', '가나다');
		const last = d.telemetry.take();
		expect(d.sent[1]).toMatchObject({ c: 12, q: 2 }); // numbering went on
		expect('p' in d.sent[1]).toBe(false);
		expect(replay(d.sent)).toBe('가나다'); // and so did the text
		expect(last).toBe(2);
	});

	it('pending updates still queued when the turn starts are sent under the new context, none are lost', () => {
		const d = dom();
		d.telemetry.turnStart({ gameId: 'g', inputTraceContext: 1 });
		d.setScope({ pending: true });
		d.type('input', '가');
		d.telemetry.turnStart({ gameId: 'g', inputTraceContext: 2 }); // before the 50 ms timer fired
		d.setScope({});
		d.type('input', '가나');
		d.telemetry.take();
		expect(d.sent.flatMap((p) => p.u)).toHaveLength(2);
		expect(d.sent.every((p) => p.c === 2)).toBe(true);
		expect(d.sent[0].q).toBe(0);
	});

	it('a different game starts a new trace even if text was pending', () => {
		const d = dom();
		d.telemetry.turnStart({ gameId: 'one', inputTraceContext: 1 });
		d.setScope({ pending: true });
		d.type('input', '가');
		d.telemetry.turnStart({ gameId: 'two', inputTraceContext: 2 });
		d.setScope({});
		d.type('input', '나');
		d.telemetry.take();
		expect(d.sent.at(-1)).toMatchObject({ c: 2, q: 0 });
		expect(replay([d.sent.at(-1)])).toBe('나');
	});

	it('cleared(): a chat line that is not an answer empties the server\'s copy honestly and the numbering goes on', () => {
		const d = dom();
		d.telemetry.turnStart({ gameId: 'g', inputTraceContext: 1 });
		d.setScope({ pending: true });
		d.type('input', '안녕하세요');
		d.telemetry.cleared(); // the chat line was sent and the box emptied by the page
		d.type('input', '다음');
		d.runTimers();
		const tuples = d.sent.flatMap((p) => p.u);
		expect(tuples.map((t) => t[2])).toEqual([1, 'programmaticClear', 1]);
		expect(replay(d.sent)).toBe('다음');
		d.sent.forEach(expectValidPacket);
		// nothing typed, nothing to say
		const e = dom();
		start(e.telemetry);
		e.telemetry.cleared();
		expect(e.telemetry.take()).toBe(-1);
	});

	it('leaves out an event that was cancelled: the field did not change', () => {
		const d = dom();
		start(d.telemetry);
		d.input.addEventListener('beforeinput', (e) => e.preventDefault(), { capture: true });
		d.input.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertFromPaste', cancelable: true }));
		expect(d.telemetry.take()).toBe(-1);
	});
});

