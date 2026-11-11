import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { answer } from '../ui/confirm.svelte.js';
import ReportDialog from './ReportDialog.svelte';

afterEach(async () => {
	answer(false);
	await new Promise((r) => setTimeout(r, 0));
	cleanup();
});

const labels = {
	'kkutu.dialog.report.category': '신고 사유',
	'kkutu.dialog.report.section.01': '비매너',
	'kkutu.dialog.report.category.01': '욕설 (예시)',
	'kkutu.dialog.report.ok': '신고하기',
	'kkutu.dialog.report.tail': '내용',
	'kkutu.dialog.report.placeholder': '',
	'kkutu.dialog.report.falseReportWarning': '허위 신고 경고'
};
function setup() {
	const store = createLobbyStore(1);
	store.net.status = 'ready';
	store.net.id = 'me';
	store.net.meta = { ...store.net.meta, labels: { ...store.net.meta.labels, ...labels } };
	store.net.users.b = { id: 'b', name: '곰' };
	store.conn = { send: vi.fn() };
	return store;
}

describe('ReportDialog, a chat report', () => {
	it('quotes the picked lines and sends them with the report', async () => {
		const store = setup();
		const { component } = render(ReportDialog, { store });
		component.show({ type: 'chat', id: 'b', name: '곰', chat: '첫째\n셋째' });
		await tick();
		expect(document.querySelector('[data-report-chat]').textContent).toBe('첫째\n셋째');
		await fireEvent.click(screen.getByRole('radio', { name: /욕설/ }));
		await fireEvent.click(screen.getByRole('button', { name: '신고하기' }));
		await tick();
		answer(true);
		await tick();
		await tick();
		expect(store.conn.send).toHaveBeenCalledWith('reportChat', expect.objectContaining({ id: 'b', chat: '첫째\n셋째', categoryCode: '01' }));
	});

	it('shows no quote for a report of a person', async () => {
		const store = setup();
		const { component } = render(ReportDialog, { store });
		component.show({ type: 'user', id: 'b', name: '곰' });
		await tick();
		expect(document.querySelector('[data-report-chat]')).toBeNull();
	});
});
