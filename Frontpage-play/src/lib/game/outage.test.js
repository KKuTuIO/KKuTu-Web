import { describe, expect, it } from 'vitest';
import { activeMaintenance, classifyOutage, MAINTENANCE_GRACE } from './outage.js';

describe('classifyOutage', () => {
	it('an offline device is a network problem, whatever else was seen', () => {
		expect(classifyOutage({ online: false, closeCode: 1006 })).toBe('network');
		expect(classifyOutage({ online: false, httpStatus: 502 })).toBe('network');
	});
	it('a proxy error or a restarting server is an update', () => {
		for (const httpStatus of [502, 503, 504]) expect(classifyOutage({ httpStatus })).toBe('update');
		for (const closeCode of [1001, 1012, 1013]) expect(classifyOutage({ closeCode })).toBe('update');
		expect(classifyOutage({ httpStatus: 500, badBody: true })).toBe('update');
	});
	it('anything else is unknown', () => {
		expect(classifyOutage({ closeCode: 1006 })).toBe('unknown');
		expect(classifyOutage({})).toBe('unknown');
		expect(classifyOutage({ httpStatus: 404 })).toBe('unknown');
	});
});

describe('activeMaintenance', () => {
	const at = (s) => Date.parse(s);
	const event = { type: 'maintenance', start: '2026-11-06T00:00:00+09:00', end: '2026-11-06T23:30:00+09:00', name: '정기 점검', reason: '23:30~05:00 이용 불가' };
	it('finds the maintenance whose notice is running', () => {
		expect(activeMaintenance([event], at('2026-11-06T12:00:00+09:00'))).toEqual({ name: '정기 점검', reason: '23:30~05:00 이용 불가', until: null });
	});
	it('still counts for a while after its notice ended (the work runs on)', () => {
		expect(activeMaintenance([event], at('2026-11-07T03:00:00+09:00'))?.name).toBe('정기 점검');
		expect(activeMaintenance([event], at('2026-11-06T23:30:00+09:00') + MAINTENANCE_GRACE + 1000)).toBeNull();
	});
	it('is not there before it starts, and an `until` replaces the grace', () => {
		expect(activeMaintenance([event], at('2026-11-05T12:00:00+09:00'))).toBeNull();
		const withUntil = { ...event, until: '2026-11-07T01:00:00+09:00' };
		expect(activeMaintenance([withUntil], at('2026-11-07T00:30:00+09:00'))?.until).toBe(at('2026-11-07T01:00:00+09:00'));
		expect(activeMaintenance([withUntil], at('2026-11-07T02:00:00+09:00'))).toBeNull();
	});
	it('ignores other kinds of events and a file that is not a list', () => {
		expect(activeMaintenance([{ ...event, type: 'alert' }], at('2026-11-06T12:00:00+09:00'))).toBeNull();
		expect(activeMaintenance({ oops: true })).toBeNull();
		expect(activeMaintenance(null)).toBeNull();
	});
	it('prefers the latest one that applies', () => {
		const later = { ...event, start: '2026-11-06T10:00:00+09:00', name: '긴급 점검' };
		expect(activeMaintenance([event, later], at('2026-11-06T12:00:00+09:00'))?.name).toBe('긴급 점검');
	});
});
