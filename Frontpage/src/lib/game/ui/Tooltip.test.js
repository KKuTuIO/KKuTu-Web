import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { createRawSnippet } from 'svelte';
import Tooltip from './Tooltip.svelte';

afterEach(() => {
	cleanup();
	vi.useRealTimers();
});
beforeEach(() => vi.useFakeTimers());

const button = createRawSnippet(() => ({ render: () => '<button>Target</button>' }));
const tooltip = () => screen.queryByRole('tooltip', { hidden: true }); // jsdom has no popover, so it reports it hidden

describe('Tooltip', () => {
	it('appears after the delay on mouse hover and disappears on leave', async () => {
		render(Tooltip, { text: 'Hello', delay: 300, children: button });
		const target = screen.getByRole('button');
		await fireEvent.pointerEnter(target, { pointerType: 'mouse' });
		vi.advanceTimersByTime(200);
		expect(tooltip()).toBeNull();
		vi.advanceTimersByTime(150);
		await Promise.resolve();
		expect(tooltip()?.textContent).toContain('Hello');
		expect(target.getAttribute('aria-describedby')).toBe(tooltip().id);
		await fireEvent.pointerLeave(target);
		await Promise.resolve();
		expect(tooltip()).toBeNull();
		expect(target.hasAttribute('aria-describedby')).toBe(false);
	});

	it('ignores touch and cancels when the pointer leaves before the delay', async () => {
		render(Tooltip, { text: 'Hello', delay: 300, children: button });
		const target = screen.getByRole('button');
		await fireEvent.pointerEnter(target, { pointerType: 'touch' });
		vi.advanceTimersByTime(1000);
		expect(tooltip()).toBeNull();
		await fireEvent.pointerEnter(target, { pointerType: 'mouse' });
		await fireEvent.pointerLeave(target);
		vi.advanceTimersByTime(1000);
		expect(tooltip()).toBeNull();
	});

	it('closes on Escape and on press', async () => {
		render(Tooltip, { text: 'Hello', delay: 0, children: button });
		const target = screen.getByRole('button');
		await fireEvent.pointerEnter(target, { pointerType: 'mouse' });
		vi.advanceTimersByTime(5);
		await Promise.resolve();
		expect(tooltip()).not.toBeNull();
		await fireEvent.keyDown(window, { key: 'Escape' });
		await Promise.resolve();
		expect(tooltip()).toBeNull();
		await fireEvent.pointerEnter(target, { pointerType: 'mouse' });
		vi.advanceTimersByTime(5);
		await Promise.resolve();
		await fireEvent.pointerDown(target);
		await Promise.resolve();
		expect(tooltip()).toBeNull();
	});

	it('renders nothing when there is no text or content', async () => {
		render(Tooltip, { text: '', delay: 0, children: button });
		await fireEvent.pointerEnter(screen.getByRole('button'), { pointerType: 'mouse' });
		vi.advanceTimersByTime(10);
		expect(tooltip()).toBeNull();
	});
});
