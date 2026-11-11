import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tick } from 'svelte';
import { shadowField } from './shadowField.js';
import { createFieldGuard } from './security/fieldGuard.js';
import { createEnvironment } from './security/environment.js';
import { defaultNative } from './security/native.js';

afterEach(() => {
	vi.useRealTimers();
	document.body.innerHTML = '';
});

function mountField(extra = {}) {
	const host = document.createElement('div');
	const box = document.createElement('div');
	box.append(host);
	document.body.append(box);
	const state = { value: '' };
	let input;
	const params = {
		get value() {
			return state.value;
		},
		set value(v) {
			state.value = v;
		},
		variant: 'answer',
		placeholder: '여기에 입력하세요.',
		label: '답',
		onready: (node) => (input = node),
		...extra
	};
	const handle = shadowField(host, params);
	return { host, box, state, handle, input: () => input };
}

describe('the answer field in a shadow root', () => {
	it('is not reachable through the page: querySelector, querySelectorAll, getElementById and the focus all see only the host', async () => {
		const f = mountField();
		await tick();
		expect(f.input()).toBeInstanceOf(HTMLInputElement);
		expect(document.querySelector('input')).toBeNull();
		expect(document.querySelectorAll('input').length).toBe(0);
		expect(f.host.shadowRoot.querySelector('input')).toBe(f.input()); // (an open root only because this is a test)
		f.input().focus();
		expect(document.activeElement).toBe(f.host); // the page is told the host has the focus, not the input
		expect(f.input().placeholder).toBe('여기에 입력하세요.');
		expect(f.input().getAttribute('aria-label')).toBe('답'); // assistive technology still gets a name
	});

	it('shares its value with the page in both directions', async () => {
		const f = mountField();
		await tick();
		f.input().value = '가나다';
		f.input().dispatchEvent(new Event('input', { bubbles: true }));
		expect(f.state.value).toBe('가나다');
		f.state.value = ''; // the page empties it after a submit
		await tick();
		expect(f.input().value).toBe('');
	});

	it('hands the key, input and blur events to the page', async () => {
		const seen = [];
		const f = mountField({ onkeydown: (e) => seen.push(['key', e.key]), oninput: () => seen.push(['input']), onblur: () => seen.push(['blur']), oncompositionend: () => seen.push(['compositionend']) });
		await tick();
		f.input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
		f.input().dispatchEvent(new Event('input', { bubbles: true }));
		f.input().dispatchEvent(new Event('compositionend'));
		f.input().dispatchEvent(new Event('blur'));
		expect(seen).toEqual([['key', 'Enter'], ['input'], ['compositionend'], ['blur']]);
	});

	it('styles itself (the page\'s own styles do not reach into a shadow root) and takes the crossword look on request', async () => {
		const f = mountField({ variant: 'crossword' });
		await tick();
		expect(f.host.shadowRoot.querySelector('style')).not.toBeNull(); // injected into the root, not the page
		expect(f.input().classList.contains('crossword')).toBe(true);
	});

	it('goes away with the page: the field and its guard', async () => {
		const destroyed = vi.fn();
		const f = mountField({ watch: () => ({ destroy: destroyed }) });
		await tick();
		f.handle.destroy();
		expect(destroyed).toHaveBeenCalled();
		expect(f.host.shadowRoot.querySelector('input')).toBeNull();
	});
});

describe('guarding a field in a shadow root', () => {
	beforeEach(() => vi.useFakeTimers());
	function guarded() {
		const calls = [];
		const guard = createFieldGuard({ report: (a, d) => calls.push([a, d]), now: () => Date.now() });
		const f = mountField({ watch: (node, where) => guard(node, { isGaming: () => true, decoys: true, ...where }) });
		return { f, calls, actions: () => calls.map(([a]) => a) };
	}

	it('puts the decoys around the HOST in the page, where a script looks, and none inside the root', async () => {
		const { f } = guarded();
		await vi.advanceTimersByTimeAsync(0);
		const light = [...f.box.querySelectorAll('input')];
		expect(light.length).toBeGreaterThanOrEqual(20);
		expect(light.every((d) => d.getAttribute('maxlength') === '180' && d.getAttribute('aria-hidden') === 'true')).toBe(true);
		expect([...f.box.children].indexOf(f.host)).toBeGreaterThanOrEqual(10); // decoys on both sides of the host
		expect(f.host.shadowRoot.querySelectorAll('input').length).toBe(1); // only the real one
		expect(light).not.toContain(f.input());
	});

	it('a script that goes for "the input" in the page meets a decoy, and is noticed', async () => {
		const { f, calls } = guarded();
		await vi.advanceTimersByTimeAsync(0);
		const target = document.querySelector('input'); // what a naive script would pick
		expect(target).not.toBe(f.input());
		target.dispatchEvent(new Event('input'));
		expect(calls.find(([a]) => a === 'FakeFocus')).toBeTruthy();
	});

	it('removing the host, or the field inside its root, is noticed after the grace period; the page\'s own teardown is not', async () => {
		const a = guarded();
		await vi.advanceTimersByTimeAsync(0);
		a.f.host.remove();
		await vi.advanceTimersByTimeAsync(0);
		await vi.advanceTimersByTimeAsync(2100);
		expect(a.actions()).toEqual(expect.arrayContaining(['CriticalDOM', 'DOMManipulation']));
		a.f.handle.destroy();
		document.body.innerHTML = '';

		const b = guarded();
		await vi.advanceTimersByTimeAsync(0);
		b.f.input().remove(); // from inside the root
		await vi.advanceTimersByTimeAsync(0);
		await vi.advanceTimersByTimeAsync(2100);
		expect(b.actions()).toContain('CriticalDOM');
		b.f.handle.destroy();
		document.body.innerHTML = '';

		const c = guarded();
		await vi.advanceTimersByTimeAsync(0);
		c.f.handle.destroy(); // teardown first, like Svelte
		c.f.box.remove();
		await vi.advanceTimersByTimeAsync(3000);
		expect(c.actions()).toEqual([]);
	});

	it('Bengal (modified 2): attachShadow replaced by a hook', async () => {
		const calls = [];
		const real = Element.prototype.attachShadow;
		const env = createEnvironment({ report: (a, d) => calls.push([a, d]), lastType: () => '', lastRelay: () => '', native: { ...defaultNative, isNative: () => true, nativeAttachShadow: real } });
		env.start();
		await vi.advanceTimersByTimeAsync(6000);
		expect(calls.filter(([a, d]) => a === 'Bengal' && d.modified === 2)).toEqual([]); // untouched
		Element.prototype.attachShadow = function (init) {
			return real.call(this, { ...init, mode: 'open' }); // what a script that wants in would do
		};
		await vi.advanceTimersByTimeAsync(2100);
		Element.prototype.attachShadow = real;
		env.stop();
		expect(calls.some(([a, d]) => a === 'Bengal' && d.modified === 2)).toBe(true);
	});
});
