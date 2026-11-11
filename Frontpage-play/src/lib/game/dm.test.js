import { describe, it, expect, beforeEach } from 'vitest';
import { newDm, dmAdd, dmReceive, dmRead, dmRemove, dmUnread, dmList, dmMembers, whisperQuery, peerName, DM_CAP, DM_THREADS } from './dm.js';

const st = (o = {}) => ({ id: 'me', users: {}, friendsData: {}, dm: newDm(), ...o });
const pkt = (id, name, value, extra = {}) => ({ type: 'chat', from: name, value, profile: { id, name, title: name, ...extra } });

describe('threads', () => {
	beforeEach(() => globalThis.sessionStorage?.clear?.());
	it('a whisper opens a thread by the sender id and counts as unread', () => {
		const s = st();
		dmReceive(s, pkt('u1', '민트', '안녕'), '안녕');
		expect(Object.keys(s.dm.threads)).toEqual(['u1']);
		expect(s.dm.threads.u1.name).toBe('민트');
		expect(dmUnread(s)).toBe(1);
		expect(s.dm.last).toMatchObject({ id: 'u1', msg: '안녕', n: 1 });
	});
	it('what I am looking at is not unread, reading clears the rest', () => {
		const s = st();
		s.dm.viewing = 'u1';
		dmReceive(s, pkt('u1', '민트', 'a'));
		expect(dmUnread(s)).toBe(0);
		s.dm.viewing = null;
		dmReceive(s, pkt('u1', '민트', 'b'));
		dmReceive(s, pkt('u2', '하늘', 'c'));
		expect(dmUnread(s)).toBe(2);
		dmRead(s, 'u1');
		expect(dmUnread(s)).toBe(1);
	});
	it('my own lines and system lines are never unread; a nickname change keeps the thread', () => {
		const s = st();
		dmAdd(s, 'u1', { name: '민트', msg: 'hi', mine: true });
		dmAdd(s, 'u1', { msg: 'not here', system: true });
		expect(dmUnread(s)).toBe(0);
		dmReceive(s, pkt('u1', '새이름', 'yo'));
		expect(s.dm.threads.u1.name).toBe('새이름');
		expect(s.dm.threads.u1.messages).toHaveLength(3);
	});
	it('caps the lines of a thread and the number of threads', () => {
		const s = st();
		for (let i = 0; i < DM_CAP + 20; i++) dmAdd(s, 'u1', { msg: `m${i}`, mine: true, at: i });
		expect(s.dm.threads.u1.messages).toHaveLength(DM_CAP);
		expect(s.dm.threads.u1.messages[0].msg).toBe('m20');
		for (let i = 0; i < DM_THREADS + 5; i++) dmAdd(s, `x${i}`, { msg: 'hi', at: 1000 + i });
		expect(Object.keys(s.dm.threads).length).toBeLessThanOrEqual(DM_THREADS);
		expect(s.dm.threads[`x${DM_THREADS + 4}`]).toBeTruthy(); // the newest stays
	});
	it('lists newest first with the last real line as preview; removing a thread closes it', () => {
		const s = st();
		dmAdd(s, 'a', { name: 'A', msg: 'first', at: 10 });
		dmAdd(s, 'b', { name: 'B', msg: 'second', mine: true, at: 20 });
		dmAdd(s, 'a', { msg: 'offline', system: true, at: 30 });
		const list = dmList(s);
		expect(list.map((r) => r.id)).toEqual(['a', 'b']);
		expect(list[0].preview).toBe('first');
		expect(list[1].mine).toBe(true);
		s.dm.active = 'a';
		dmRemove(s, 'a');
		expect(s.dm.active).toBe(null);
		expect(dmList(s).map((r) => r.id)).toEqual(['b']);
	});
	it('a whisper without a sender id is dropped', () => {
		const s = st();
		expect(dmReceive(s, { type: 'chat', from: 'x', value: 'hi' })).toBe(null);
	});
});

describe('members and the slash picker', () => {
	const users = {
		a: { id: 'a', name: '가나', tag: '12' },
		b: { id: 'b', name: '하늘', tag: '' },
		c: { id: 'c', name: '민트', tag: '7' },
		bot: { id: 'bot', name: '봇', robot: true },
		me: { id: 'me', name: '나' }
	};
	it('lists the others online, friends first, and filters by name or tag', () => {
		const s = st({ users, friendsData: { c: {} } });
		expect(dmMembers(s).map((r) => r.id)).toEqual(['c', 'a', 'b']); // friend, then by name
		expect(dmMembers(s, '하').map((r) => r.id)).toEqual(['b']);
		expect(dmMembers(s, '#12').map((r) => r.id)).toEqual(['a']);
		expect(dmMembers(s, '없는')).toEqual([]);
	});
	it('people I talked to come before strangers', () => {
		const s = st({ users });
		dmAdd(s, 'b', { msg: 'hi', mine: true });
		expect(dmMembers(s).map((r) => r.id)).toEqual(['b', 'a', 'c']);
	});
	it('peerName is how people type it after /e', () => {
		expect(peerName({ name: '가 나', tag: '12' })).toBe('가나#12');
	});
	it('whisperQuery finds the target being typed after /e, /귓 or /ㄷ', () => {
		expect(whisperQuery('/e ')).toBe('');
		expect(whisperQuery('/e 하')).toBe('하');
		expect(whisperQuery('/귓 가나#1')).toBe('가나#1');
		expect(whisperQuery('/ㄷ x')).toBe('x');
		expect(whisperQuery('/e 하늘 안녕')).toBe(null);
		expect(whisperQuery('/e')).toBe(null);
		expect(whisperQuery('hello')).toBe(null);
	});
});
