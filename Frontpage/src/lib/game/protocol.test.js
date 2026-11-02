import { describe, expect, it } from 'vitest';
import { applyPacket, initialState, normalizeRoom } from './protocol.js';
import { getLevel } from './level.js';

const meta = { modes: ['KKT'], options: { manner: 'Manner' }, labels: { 'game.mode.KKT.name': '끝말잇기', 'game.option.Manner.name': '매너' } };

describe('protocol', () => {
	it('normalizes rooms with localized labels', () => {
		const r = normalizeRoom({ id: 5, title: 't', password: true, limit: 8, mode: 0, round: 3, time: 90, players: [{}, {}], gaming: false, opts: { manner: true, other: false } }, meta);
		expect(r).toMatchObject({ no: 5, locked: true, mode: '끝말잇기', tags: ['매너'], cur: 2, max: 8, playing: false });
	});
	it('welcome fills state and room packets update/remove', () => {
		const s = initialState();
		s.meta = meta;
		applyPacket(s, { type: 'welcome', id: 'u1', guest: false, users: { u1: { id: 'u1', profile: { title: 'a#1' }, data: { score: 0 } } }, rooms: {} });
		expect(s.status).toBe('ready');
		expect(s.users.u1.name).toBe('a');
		applyPacket(s, { type: 'room', room: { id: 9, players: [{}], limit: 2, mode: 0, opts: {} } });
		expect(s.rooms[9].cur).toBe(1);
		applyPacket(s, { type: 'room', room: { id: 9, players: [] } });
		expect(s.rooms[9]).toBeUndefined();
	});
	it('tracks my room, friends, money and wins', () => {
		const s = initialState();
		s.meta = meta;
		applyPacket(s, { type: 'welcome', id: 'me', membership: 1, friends: { a: 'Ann', b: { nick: 'Bob' } }, users: { me: { id: 'me', money: 507, data: { score: 0, record: { KKT: [3, 38], EKT: [1, 2] } }, equip: { Mhead: 'hat' } } }, rooms: {} });
		expect(s.friends).toEqual({ a: 'Ann', b: 'Bob' });
		expect(s.users.me).toMatchObject({ money: 507, wins: 40, equip: { Mhead: 'hat' } });
		applyPacket(s, { type: 'room', room: { id: 7, players: [{ id: 'me' }], limit: 2, mode: 0, opts: {} } });
		expect(s.myRoom).toBe(7);
		applyPacket(s, { type: 'room', room: { id: 7, players: [] } });
		expect(s.myRoom).toBeNull();
	});
	it('tracks play time and the 오끄감 stack from welcome and okg packets', () => {
		const s = initialState();
		applyPacket(s, { type: 'welcome', id: 'me', playTime: 3_600_000, okg: 2, users: {}, rooms: {}, friends: {} });
		expect([s.playTime, s.okgCount]).toEqual([3_600_000, 2]);
		applyPacket(s, { type: 'okg', time: 4_320_000, count: 3 });
		expect([s.playTime, s.okgCount]).toEqual([4_320_000, 3]);
	});
	it('captcha and chat', () => {
		const s = initialState();
		applyPacket(s, { type: 'captcha', siteKey: 'k' });
		expect(s.status).toBe('captcha');
		applyPacket(s, { type: 'chat', value: 'hi', profile: { title: 'x#1', id: 'u2' } });
		expect(s.chat[0]).toMatchObject({ who: 'x', msg: 'hi', mine: false, system: false });
		expect(typeof s.chat[0].at).toBe('number');
		s.id = 'u2';
		applyPacket(s, { type: 'chat', value: 'mine', profile: { title: 'x', id: 'u2' } });
		applyPacket(s, { type: 'chat', notice: true, message: 'sys' });
		expect(s.chat[1].mine).toBe(true);
		expect(s.chat[2]).toMatchObject({ msg: 'sys', system: true });
	});
	it('levels grow with score', () => {
		expect(getLevel(0)).toBe(1);
		expect(getLevel(1_000_000)).toBeGreaterThan(100);
	});
});
