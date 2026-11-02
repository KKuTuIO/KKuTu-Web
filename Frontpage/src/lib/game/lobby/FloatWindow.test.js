import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { createRawSnippet } from 'svelte';
import FloatWindow from './FloatWindow.svelte';

afterEach(cleanup);
const body = createRawSnippet(() => ({ render: () => '<input aria-label="field">' }));
const win = () => screen.getByRole('dialog');
const handle = () => screen.getByRole('toolbar');
const pos = () => [parseFloat(win().style.left), parseFloat(win().style.top)];

describe('FloatWindow', () => {
	it('is non-modal and translucent by default', () => {
		render(FloatWindow, { open: true, title: 'T', id: 'a', children: body });
		expect(win().getAttribute('aria-modal')).toBe('false');
		expect(win().dataset.ghost).toBe('true');
		expect(document.querySelector('dialog')).toBeNull(); // no <dialog>, so no backdrop and nothing is blocked
	});
	it('can opt out of the translucency', () => {
		render(FloatWindow, { open: true, title: 'T', id: 'b', ghost: false, children: body });
		expect(win().dataset.ghost).toBe('false');
	});
	it('moves when its title bar is dragged and is fully opaque while dragging', async () => {
		render(FloatWindow, { open: true, title: 'T', id: 'c', children: body });
		const [x0, y0] = pos();
		await fireEvent.pointerDown(handle(), { clientX: 400, clientY: 100, button: 0 });
		expect(win().dataset.dragging).toBe('true');
		await fireEvent.pointerMove(handle(), { clientX: 460, clientY: 160 });
		expect(pos()).toEqual([x0 + 60, y0 + 60]);
		await fireEvent.pointerUp(handle());
		expect(win().dataset.dragging).toBe('false');
	});
	it('never lets the title bar leave the screen', async () => {
		render(FloatWindow, { open: true, title: 'T', id: 'd', children: body });
		await fireEvent.pointerDown(handle(), { clientX: 10, clientY: 10, button: 0 });
		await fireEvent.pointerMove(handle(), { clientX: -9999, clientY: -9999 });
		expect(pos()[1]).toBe(0);
		await fireEvent.pointerMove(handle(), { clientX: 99999, clientY: 99999 });
		expect(pos()[0]).toBeLessThanOrEqual(window.innerWidth - 96);
		expect(pos()[1]).toBeLessThanOrEqual(window.innerHeight - 48);
	});
	it('moves with the arrow keys on the focused title bar', async () => {
		render(FloatWindow, { open: true, title: 'T', id: 'e', children: body });
		const [x0, y0] = pos();
		await fireEvent.keyDown(handle(), { key: 'ArrowRight' });
		await fireEvent.keyDown(handle(), { key: 'ArrowDown', shiftKey: true });
		expect(pos()).toEqual([x0 + 16, y0 + 64]);
	});
	it('does not start a drag from the close button, and closes', async () => {
		render(FloatWindow, { open: true, title: 'T', id: 'f', children: body });
		const close = screen.getByRole('button');
		await fireEvent.pointerDown(close, { clientX: 5, clientY: 5, button: 0 });
		expect(win().dataset.dragging).toBe('false');
		await fireEvent.click(close);
		await Promise.resolve();
		expect(screen.queryByRole('dialog')).toBeNull();
	});
	it('remembers where it was left', async () => {
		const first = render(FloatWindow, { open: true, title: 'T', id: 'g', children: body });
		await fireEvent.keyDown(handle(), { key: 'ArrowRight', shiftKey: true });
		const moved = pos();
		first.unmount();
		render(FloatWindow, { open: true, title: 'T', id: 'g', children: body });
		expect(pos()).toEqual(moved);
	});
});
