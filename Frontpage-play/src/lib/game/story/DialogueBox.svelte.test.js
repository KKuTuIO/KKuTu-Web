import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/svelte';
import { createRunner } from './runner.svelte.js';
import DialogueBox from './DialogueBox.svelte';
import { setMessages } from '../i18n.svelte.js';

const store = { shop: {}, net: { meta: { moremiParts: [] } } };
const script = {
	cast: ['haksamo'],
	nodes: [
		{ id: 'a', say: { who: 'haksamo', text: 'k.one', params: { n: 7 } } },
		{ choice: [{ text: 'k.yes', goto: 'z' }], say: { who: 'haksamo', text: 'k.ask' } },
		{ id: 'z', end: true }
	]
};

describe('DialogueBox', () => {
	beforeEach(() => {
		vi.useFakeTimers();
		setMessages({ 'k.one': '하나 <b>{n}</b>레미', 'k.ask': '고르라레미', 'k.yes': '응', 'play.mascot.haksamo': '학사모레미', 'play.story.ui.next': '다음', 'play.story.ui.skip': '건너뛰기', 'play.story.ui.back': '이전', 'play.story.ui.skipTyping': '바로 보기' }, 'ko');
	});
	afterEach(() => {
		cleanup();
		vi.useRealTimers();
	});

	it('types the line out, shows it at once when pressed, and goes on when pressed again', async () => {
		const runner = createRunner(script);
		runner.start();
		const { container } = render(DialogueBox, { runner, store });
		expect(screen.getByText('학사모레미')).toBeTruthy();
		// the typed part is the one layer of the line's button that is not the invisible copy (laid over it to keep the bubble's size)
		const typed = () => [...container.querySelectorAll('button[aria-live] .grid > span')].filter((n) => !n.classList.contains('invisible')).map((n) => n.textContent).join('').replace(/\s+/g, '');
		await vi.advanceTimersByTimeAsync(60);
		expect(typed().length).toBeGreaterThan(0);
		expect(typed().length).toBeLessThan('하나7레미'.length); // still typing
		screen.getByRole('button', { name: /바로 보기/ }).click(); // the next button reads 바로 보기 while typing
		await vi.advanceTimersByTimeAsync(0);
		expect(typed()).toBe('하나7레미');
		screen.getByRole('button', { name: /^다음/ }).click();
		await vi.advanceTimersByTimeAsync(0);
		expect(container.textContent).toContain('고르라레미');
	});
	it('shows the choices once the line is typed, and an answer goes on', async () => {
		const runner = createRunner(script);
		runner.start();
		runner.advance();
		await vi.advanceTimersByTimeAsync(0);
		render(DialogueBox, { runner, store });
		expect(screen.queryByRole('button', { name: '응' })).toBeNull(); // not while typing
		await vi.advanceTimersByTimeAsync(2000);
		screen.getByRole('button', { name: '응' }).click();
		await vi.advanceTimersByTimeAsync(0);
		expect(runner.state.done).toBe(true);
	});
	it('shows a tip instead of the line while a stage runs, without a 다음 button', async () => {
		const runner = createRunner({ cast: ['haksamo'], nodes: [{ stage: 'x' }] }, { stage: (n, node, api) => new Promise(() => api.tip('k.one', { params: { n: 1 } })) });
		runner.start();
		render(DialogueBox, { runner, store });
		await vi.advanceTimersByTimeAsync(2000);
		expect(document.body.textContent).toContain('하나 1레미');
		expect(screen.queryByRole('button', { name: /^다음/ })).toBeNull();
		expect(screen.getByRole('button', { name: /건너뛰기/ })).toBeTruthy();
	});
});
