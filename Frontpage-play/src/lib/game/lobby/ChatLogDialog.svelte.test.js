import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import ChatLogDialog from './ChatLogDialog.svelte';

afterEach(cleanup);

const line = (who, uid, msg, extra = {}) => ({ who, uid, msg, shown: msg, at: Date.UTC(2026, 10, 10, 1, 0), system: false, whisper: false, mine: false, ...extra });
function setup(chat) {
	const store = createLobbyStore(1);
	store.net.status = 'ready';
	store.net.id = 'me';
	store.net.users.me = { id: 'me', name: '나' };
	store.net.chat.push(...chat);
	store.hooks.openReport = vi.fn();
	return store;
}
const rows = () => [...document.querySelectorAll('[data-line]')];
const boxes = () => screen.queryAllByRole('checkbox');
const reportBtn = () => screen.getByRole('button', { name: '대화 신고' });
const startSelecting = () => fireEvent.click(reportBtn());

describe('ChatLogDialog', () => {
	it('lists what I could read as plain text, leaving out system lines and whispers', () => {
		const store = setup([line('곰', 'b', '안녕'), line('시스템', '', '입장', { system: true }), line('곰', 'b', '비밀', { whisper: true }), line('나', 'me', '네', { mine: true })]);
		render(ChatLogDialog, { open: true, store });
		expect(rows().map((r) => r.textContent)).toEqual([expect.stringContaining('안녕'), expect.stringContaining('네')]);
		expect(boxes()).toHaveLength(0); // reading needs no checkboxes
	});

	it('opens on the latest 100 lines', () => {
		const store = setup(Array.from({ length: 130 }, (_, i) => line('곰', 'b', `m${i}`)));
		render(ChatLogDialog, { open: true, store });
		expect(rows()).toHaveLength(100);
		expect(rows()[0].textContent).toContain('m30');
		expect(rows()[99].textContent).toContain('m129');
	});

	it('opens scrolled to the newest line', async () => {
		const height = vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(4000);
		try {
			const store = setup(Array.from({ length: 130 }, (_, i) => line('곰', 'b', `m${i}`)));
			render(ChatLogDialog, { open: true, store });
			await tick();
			await tick();
			expect(document.querySelector('[role="log"]').scrollTop).toBe(4000);
		} finally {
			height.mockRestore();
		}
	});

	it('brings in 100 older lines each time the top is reached, until there are none left', async () => {
		const store = setup(Array.from({ length: 250 }, (_, i) => line('곰', 'b', `m${i}`)));
		render(ChatLogDialog, { open: true, store });
		const log = document.querySelector('[role="log"]');
		log.scrollTop = 500; // not at the top: nothing more
		await fireEvent.scroll(log);
		expect(rows()).toHaveLength(100);
		log.scrollTop = 0;
		await fireEvent.scroll(log);
		await tick();
		expect(rows()).toHaveLength(200);
		expect(rows()[0].textContent).toContain('m50');
		log.scrollTop = 0;
		await fireEvent.scroll(log);
		await tick();
		expect(rows()).toHaveLength(250);
		log.scrollTop = 0;
		await fireEvent.scroll(log);
		await tick();
		expect(rows()).toHaveLength(250);
	});

	it('keeps what was picked when older lines are brought in', async () => {
		const store = setup(Array.from({ length: 150 }, (_, i) => line('곰', 'b', `m${i}`)));
		render(ChatLogDialog, { open: true, store });
		await startSelecting();
		await fireEvent.click(boxes()[99]); // the newest line
		const log = document.querySelector('[role="log"]');
		log.scrollTop = 0;
		await fireEvent.scroll(log);
		await tick();
		expect(rows()).toHaveLength(150);
		expect(screen.getByText('1개 선택')).toBeTruthy();
		expect(boxes().filter((b) => b.getAttribute('aria-checked') === 'true')).toHaveLength(1);
	});

	it('puts a checkbox on every line once 대화 신고 is pressed, and nothing is reported yet', async () => {
		const store = setup([line('곰', 'b', '하나'), line('나', 'me', '내 말', { mine: true })]);
		render(ChatLogDialog, { open: true, store });
		await startSelecting();
		expect(boxes()).toHaveLength(2);
		expect(store.hooks.openReport).not.toHaveBeenCalled();
		expect(reportBtn().disabled).toBe(true); // nothing picked yet
	});

	it('reports the picked lines of one player, as typed', async () => {
		const store = setup([line('곰', 'b', '첫째'), line('여우', 'f', '다른 사람'), line('곰', 'b', '셋째', { shown: '***' })]);
		render(ChatLogDialog, { open: true, store });
		await startSelecting();
		await fireEvent.click(boxes()[0]);
		await fireEvent.click(boxes()[2]);
		expect(screen.getByText('2개 선택')).toBeTruthy();
		await fireEvent.click(reportBtn());
		expect(store.hooks.openReport).toHaveBeenCalledWith({ type: 'chat', id: 'b', name: '곰', chat: '첫째\n셋째' });
	});

	it('holds back other players once one is picked, and frees them when nothing is picked', async () => {
		const store = setup([line('곰', 'b', '하나'), line('여우', 'f', '둘')]);
		render(ChatLogDialog, { open: true, store });
		await startSelecting();
		await fireEvent.click(boxes()[0]);
		expect(boxes()[1].disabled).toBe(true);
		await fireEvent.click(boxes()[0]);
		expect(boxes()[1].disabled).toBe(false);
	});

	it('does not let me pick my own lines', async () => {
		const store = setup([line('나', 'me', '내 말', { mine: true })]);
		render(ChatLogDialog, { open: true, store });
		await startSelecting();
		expect(boxes()[0].disabled).toBe(true);
	});

	it('goes back to reading when selecting is cancelled, dropping what was picked', async () => {
		const store = setup([line('곰', 'b', '하나')]);
		render(ChatLogDialog, { open: true, store });
		await startSelecting();
		await fireEvent.click(boxes()[0]);
		await fireEvent.click(screen.getByRole('button', { name: '취소' }));
		expect(boxes()).toHaveLength(0);
		await startSelecting();
		expect(boxes()[0].getAttribute('aria-checked')).toBe('false');
	});

	it('says so when there is nothing to show', () => {
		render(ChatLogDialog, { open: true, store: setup([]) });
		expect(screen.getByText('아직 대화가 없어요.')).toBeTruthy();
	});
});
