import { describe, expect, it } from 'vitest';
import { reportRows } from './reportFeedback.js';

const THANKS = '앞으로도 적극적인 신고로 건전한 게임 문화 양성에 많은 참여를 부탁드립니다. 감사합니다.';
const para = (reason, target) => `최근 '${reason}'(으)로 신고한 사용자(${target})를 확인한 결과 이용 제한 조치가 완료되었습니다.\n${THANKS}`;

describe('reportRows', () => {
	it('makes a row per reported player, also from one notification that holds several', () => {
		const rows = reportRows([{ id: 'a', kind: 'REPORT_RESOLVED', createdAt: 100, message: `${para('욕설/비하 발언', '아****')}\n\n${para('도배', '김****')}` }]);
		expect(rows.map((r) => [r.reason, r.target])).toEqual([['욕설/비하 발언', '아****'], ['도배', '김****']]);
		expect(rows.every((r) => r.at === 100000 && r.id === 'a')).toBe(true);
	});
	it('puts the newest result first across notifications, keeping reasons that contain brackets', () => {
		const rows = reportRows([
			{ id: 'old', kind: 'REPORT_RESOLVED', createdAt: 10, message: para('도배', '김****') },
			{ id: 'new', kind: 'REPORT_RESOLVED', createdAt: 20, message: para('매크로 사용 의심(자동완성·붙여넣기 등)', '손****') }
		]);
		expect(rows.map((r) => r.id)).toEqual(['new', 'old']);
		expect(rows[0].reason).toBe('매크로 사용 의심(자동완성·붙여넣기 등)');
	});
	it('keeps a paragraph in another wording as a plain line, and skips other kinds of notification', () => {
		const rows = reportRows([
			{ id: 'x', kind: 'REPORT_RESOLVED', createdAt: 5, message: '처리가 완료되었습니다.\n감사합니다.' },
			{ id: 'y', kind: 'SOMETHING', createdAt: 6, message: para('도배', '김****') },
			{ id: 'z', createdAt: 7, message: para('도배', '김****') }
		]);
		expect(rows).toHaveLength(1);
		expect(rows[0]).toMatchObject({ id: 'x', raw: '처리가 완료되었습니다.' });
		expect(rows[0].reason).toBeUndefined();
	});
	it('copes with nothing at all', () => {
		expect(reportRows(undefined)).toEqual([]);
		expect(reportRows([{ id: 'e', kind: 'REPORT_RESOLVED', message: '' }])).toEqual([]);
	});
});
