import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render } from '@testing-library/svelte';
import { tick } from 'svelte';
import ReportFeedbackDialog from './ReportFeedbackDialog.svelte';
import { policyGate } from '../policyGate.svelte.js';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
afterEach(cleanup);
beforeEach(() => {
	policyGate.agreed = true;
	HTMLDialogElement.prototype.showModal ??= function () { this.open = true; };
	HTMLDialogElement.prototype.close ??= function () { this.open = false; };
});

const THANKS = '앞으로도 적극적인 신고로 건전한 게임 문화 양성에 많은 참여를 부탁드립니다. 감사합니다.';
const para = (reason, target) => `최근 '${reason}'(으)로 신고한 사용자(${target})를 확인한 결과 이용 제한 조치가 완료되었습니다.\n${THANKS}`;
const note = (id, createdAt, ...paras) => ({ id, kind: 'REPORT_RESOLVED', createdAt, message: paras.join('\n\n') });
const store = (notifications, extra = {}) => ({ net: { status: 'ready', notifications }, inGame: false, ackNotification: vi.fn(), ...extra });

describe('ReportFeedbackDialog', () => {
	it('lists every waiting result in one table, newest first, with one confirm for all of them', async () => {
		const s = store([note('n1', 100, para('욕설/비하 발언', '아****'), para('도배', '김****')), note('n2', 200, para('부적절한 닉네임', '사****')), { id: 'm', kind: 'OTHER', message: '다른 알림' }]);
		render(ReportFeedbackDialog, { store: s });
		await tick();
		const dialog = document.querySelector('dialog');
		expect(dialog.open).toBe(true);
		const rows = [...dialog.querySelectorAll('tbody tr')].map((tr) => [...tr.children].map((td) => td.textContent.trim()));
		expect(rows).toEqual([['사****', '부적절한 닉네임'], ['아****', '욕설/비하 발언'], ['김****', '도배']]); // n2 is the newest; name first, then the reason
		expect([...dialog.querySelectorAll('thead th')].map((th) => th.textContent.trim())).toHaveLength(2);
		expect(dialog.textContent.split('감사합니다').length - 1).toBe(1); // the thanks line is said once, not once per player
		expect(dialog.querySelector('h3').tagName).toBe('H3');
		await fireEvent.click([...dialog.querySelectorAll('button')].find((b) => b.classList.contains('kk-btn-primary')));
		expect(s.ackNotification.mock.calls.map((c) => c[0]).sort()).toEqual(['n1', 'n2']);
	});
	it('says nothing while a game runs or before the terms are agreed to, and then shows up', async () => {
		policyGate.agreed = false;
		const s = store([note('n1', 100, para('도배', '김****'))]);
		render(ReportFeedbackDialog, { store: s });
		await tick();
		expect(document.querySelector('dialog[open]')).toBeNull(); // not open (a closed dialog is not in the page at all)
		policyGate.agreed = true;
		await tick();
		expect(document.querySelector('dialog').open).toBe(true);
	});
	it('does not open for a game in progress', async () => {
		render(ReportFeedbackDialog, { store: store([note('n1', 100, para('도배', '김****'))], { inGame: true }) });
		await tick();
		expect(document.querySelector('dialog[open]')).toBeNull(); // not open (a closed dialog is not in the page at all)
	});
	it('shows an unrecognised message as a plain line instead of dropping it', async () => {
		render(ReportFeedbackDialog, { store: store([{ id: 'x', kind: 'REPORT_RESOLVED', createdAt: 5, message: '처리가 완료되었습니다.\n감사합니다.' }]) });
		await tick();
		const cell = document.querySelector('tbody td[colspan="2"]');
		expect(cell.textContent.trim()).toBe('처리가 완료되었습니다.');
	});
});
