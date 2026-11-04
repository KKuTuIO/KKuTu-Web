import { describe, expect, it } from 'vitest';
import { applyPacket, initialState, joinLeave, normalizeRoom, splitNick } from './protocol.js';
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
	it('keeps each players ready, form and team state from room and user packets', () => {
		const s = initialState();
		s.meta = meta;
		s.id = 'me';
		const player = (id, game) => ({ id, profile: { title: id }, data: { score: 0 }, equip: {}, game });
		applyPacket(s, { type: 'room', room: { id: 4, master: 'me', limit: 4, mode: 0, opts: {}, players: [player('me', { ready: false, form: 'J', team: 0 }), player('b', { ready: true, form: 'J', team: 2 })] } });
		expect(s.users.b.game).toEqual({ ready: true, form: 'J', team: 2, practice: false, score: 0 });
		expect(s.rooms[4]).toMatchObject({ master: 'me', limit: 4, players: ['me', 'b'] });
		applyPacket(s, { type: 'user', ...player('b', { ready: false, form: 'S', team: 0, practice: true }) });
		expect(s.users.b.game).toEqual({ ready: false, form: 'S', team: 0, practice: true, score: 0 });
	});
	it('keeps the latest prompt (progress transfer code)', () => {
		const s = initialState();
		applyPacket(s, { type: 'prompt', lang: 'gidInfo', promptType: 'gid', value: 'AB12CD34' });
		expect(s.prompt).toEqual({ lang: 'gidInfo', type: 'gid', value: 'AB12CD34' });
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
		// notices without a message use the localized text of their code; empty ones are dropped
		s.meta = { ...meta, labels: { 'kkutu.js.error.711': '방장이 <b>바뀌었습니다</b>.<br>확인하세요' } };
		applyPacket(s, { type: 'chat', notice: true, code: 711 });
		applyPacket(s, { type: 'chat', notice: true, code: 999 });
		expect(s.chat.slice(3)).toHaveLength(1);
		expect(s.chat[3].msg).toBe('방장이 바뀌었습니다. 확인하세요');
	});
	it('levels grow with score', () => {
		expect(getLevel(0)).toBe(1);
		expect(getLevel(1_000_000)).toBeGreaterThan(100);
	});
});

describe('friends', () => {
	const mk = () => {
		const s = initialState();
		s.meta = { ...meta, labels: { ...meta.labels } };
		s.id = 'me';
		return s;
	};
	it('keeps the friend list in both shapes the server uses, and puts the memo and best-friend flag aside', () => {
		const s = mk();
		applyPacket(s, { type: 'welcome', id: 'me', friends: { a: '메모A', b: { memo: '메모B', nick: '비', bf: true } }, users: {}, rooms: {} });
		expect(s.friends).toEqual({ a: '메모A', b: '비' });
		expect(s.friendsData.b).toEqual({ memo: '메모B', nick: '비', bf: true });
		applyPacket(s, { type: 'friendEdit', friends: { b: { memo: 'x', nick: '비', bf: false } } });
		expect(Object.keys(s.friends)).toEqual(['b']);
	});
	it('notes who comes online or leaves, only for friends', () => {
		const s = mk();
		applyPacket(s, { type: 'welcome', id: 'me', friends: { a: '가' }, users: {}, rooms: {} });
		applyPacket(s, { type: 'friend', id: 'a', stat: 'on', s: 2 });
		expect(s.friendsOnline.a).toBe(2);
		applyPacket(s, { type: 'friend', id: 'a', stat: 'off' });
		expect('a' in s.friendsOnline).toBe(false);
		applyPacket(s, { type: 'friend', id: 'zz', stat: 'on', s: 1 });
		expect(s.chat.filter((c) => c.system)).toHaveLength(2);
	});
	it('takes a request, and adds the friend when my own request is accepted', () => {
		const s = mk();
		applyPacket(s, { type: 'friendAdd', from: 'x' });
		expect(s.friendRequests.map((r) => r.from)).toEqual(['x']);
		applyPacket(s, { type: 'friendAdd', from: 'x' });
		expect(s.friendRequests).toHaveLength(1); // asking twice is still one request
		applyPacket(s, { type: 'friendAddRes', target: 'y', res: false });
		expect(s.friendsData.y).toBeUndefined();
		applyPacket(s, { type: 'friendAddRes', target: 'y', res: true });
		expect(s.friendsData.y.bf).toBe(false);
		expect(s.friendResult).toMatchObject({ target: 'y', res: true });
	});
});

describe('waiting-room behaviours', () => {
	const mk = () => {
		const s = initialState();
		s.meta = { ...meta, labels: { ...meta.labels } };
		s.id = 'me';
		return s;
	};
	const player = (id, game = {}, extra = {}) => ({ id, profile: { title: id + '#1' }, data: { score: 0 }, equip: {}, game: { ready: false, form: 'J', team: 0, ...game }, ...extra });
	const lines = (s) => s.chat.filter((c) => c.system).map((c) => c.msg);

	it('a player who leaves or is removed from a room is back in the lobby', () => {
		const s = mk();
		const room = (players) => ({ type: 'room', room: { id: 7, title: 'r', master: 'me', players, opts: {}, mode: 0, round: 3, time: 60, limit: 8 } });
		applyPacket(s, room([player('me', {}, { place: 7 }), player('b', {}, { place: 7 })]));
		expect(s.users.b.place).toBe(7);
		applyPacket(s, room([player('me', {}, { place: 7 })]));
		expect(s.users.b.place).toBe(0);
		applyPacket(s, room([]));
		expect(s.users.me.place).toBe(0);
	});

	it('splits nicknames into name and tag; fixed ones have no tag', () => {
		expect(splitNick('곰#1234')).toEqual({ name: '곰', tag: '1234' });
		expect(splitNick('곰')).toEqual({ name: '곰', tag: '' });
	});

	it('builds AI seats from their level', () => {
		const s = mk();
		applyPacket(s, { type: 'room', room: { id: 1, master: 'me', limit: 4, mode: 0, opts: {}, players: [player('me'), { id: 'robot1', robot: true, level: 3, data: { score: 0 }, game: { ready: true, form: 'J', team: 0 } }] } });
		expect(s.users.robot1).toMatchObject({ robot: true, level: 3, name: '중수 끄투 봇', tag: '' });
	});

	it('announces joins and leaves, and mutes someone who flaps (3+ times in 15s)', () => {
		const s = mk();
		s.users.b = { id: 'b', name: 'B' };
		joinLeave(s, 'b', 'joined', 1000);
		joinLeave(s, 'b', 'left', 2000);
		joinLeave(s, 'b', 'joined', 3000); // third event: muted, once
		joinLeave(s, 'b', 'left', 4000); // silent
		expect(lines(s)).toEqual(['B님이 입장했습니다.', 'B님이 퇴장했습니다.', 'B 님의 입퇴장이 너무 잦아 60초간 알림을 숨깁니다.']);
		joinLeave(s, 'b', 'joined', 70_000); // mute is over
		expect(lines(s).length).toBe(4);
		s.users.op = { id: 'op', name: '운영자' };
		joinLeave(s, 'op', 'joined', 80_000);
		expect(lines(s).length).toBe(4); // operators are never announced
	});

	it('says when the master changes and when the settings change', () => {
		const s = mk();
		applyPacket(s, { type: 'room', room: { id: 1, master: 'me', limit: 4, mode: 0, opts: {}, players: [player('me'), player('b')] } });
		applyPacket(s, { type: 'room', modify: true, target: 'me', room: { id: 1, master: 'b', limit: 4, mode: 0, opts: {}, players: [player('me'), player('b')] } });
		expect(lines(s)).toEqual(['방 설정이 바뀌었습니다.', 'b님으로 방장이 바뀌었습니다.']);
	});

	it('tracks a kick vote and its result, and flags me when I was the one removed', () => {
		const s = mk();
		applyPacket(s, { type: 'room', room: { id: 1, master: 'm', limit: 8, mode: 0, opts: {}, players: [player('me'), player('m'), player('b')] } });
		applyPacket(s, { type: 'kickVote', target: 'b', Y: 1, N: 0 });
		expect(s.kickVote).toMatchObject({ target: 'b' });
		applyPacket(s, { type: 'room', target: 'b', kickVote: { target: 'b', Y: 2, N: 1 }, room: { id: 1, master: 'm', limit: 8, mode: 0, opts: {}, players: [player('me'), player('m')] } });
		expect(s.kickVote).toBeNull();
		expect(s.kicked).toBeNull();
		expect(lines(s).at(-1)).toBe('찬성 2, 반대 1표로 b님이 강제 퇴장되었습니다.');
		applyPacket(s, { type: 'kickVote', target: 'm' });
		applyPacket(s, { type: 'kickDeny', Y: 0, N: 2 });
		expect(lines(s).at(-1)).toBe('찬성 0, 반대 2표로 m님의 강제 퇴장에 대한 투표가 부결되었습니다.');
		applyPacket(s, { type: 'room', target: 'me', kickVote: { target: 'me', Y: 3, N: 0 }, room: { id: 1, master: 'm', limit: 8, mode: 0, opts: {}, players: [player('m')] } });
		expect(s.kicked).not.toBeNull();
		expect(s.myRoom).toBeNull();
	});

	it('handles invitations, roomStuck', () => {
		const s = mk();
		s.users.b = { id: 'b', name: 'B' };
		applyPacket(s, { type: 'invited', by: 'b', from: 321 });
		expect(s.invite).toMatchObject({ by: 'b', from: 321 });
		applyPacket(s, { type: 'inviteNo', target: 'b' });
		expect(lines(s)).toEqual(['B님이 초대를 거절했습니다.']);
		applyPacket(s, { type: 'room', room: { id: 5, master: 'me', limit: 4, mode: 0, opts: {}, players: [player('me')] } });
		expect(s.myRoom).toBe(5);
		applyPacket(s, { type: 'roomStuck' });
		expect(s.myRoom).toBeNull();
		expect(s.rooms[5]).toBeUndefined();
	});
});
