import { afterEach, describe, expect, it, vi } from 'vitest';
import { FAMILY_CREATE_COST, FAMILY_CREATE_LEVEL, FAMILY_INTRO_MAX_BYTES, canKick, canManage, capacityOf, clampIntro, familyLevel, introBytes, nameProblem, sortMembers, whereOf } from './familyModel.js';
import { family } from './familyStore.svelte.js';

describe('familyModel', () => {
	it('counts Korean as two bytes and ASCII as one, and clamps without splitting a character', () => {
		expect(introBytes('abc')).toBe(3);
		expect(introBytes('가나')).toBe(4);
		const long = '가'.repeat(100);
		const cut = clampIntro(long);
		expect(introBytes(cut)).toBeLessThanOrEqual(FAMILY_INTRO_MAX_BYTES);
		expect(cut.length).toBe(FAMILY_INTRO_MAX_BYTES / 2);
		expect(clampIntro('a가', 2)).toBe('a'); // the 2-byte character would overflow
	});

	it('validates names', () => {
		expect(nameProblem('가')).toBe('short');
		expect(nameProblem('가'.repeat(13))).toBe('long');
		expect(nameProblem('끄투!')).toBe('chars');
		expect(nameProblem('끄투  리오')).toBe('chars');
		expect(nameProblem('끄투 리오')).toBe('');
	});

	it('levels up from fame and grows capacity to the cap', () => {
		expect(familyLevel(0)).toMatchObject({ level: 1, into: 0 });
		expect(familyLevel(1000).level).toBe(2);
		expect(familyLevel(999).level).toBe(1);
		expect(capacityOf(1)).toBe(20);
		expect(capacityOf(999)).toBe(200);
	});

	it('lets only master and deputy manage, and nobody kick an equal or higher role', () => {
		expect(canManage('member')).toBe(false);
		expect(canManage('deputy')).toBe(true);
		expect(canKick('master', 'deputy')).toBe(true);
		expect(canKick('deputy', 'deputy')).toBe(false);
		expect(canKick('deputy', 'master')).toBe(false);
		expect(canKick('member', 'member')).toBe(false);
	});

	it('orders online members first, then by role', () => {
		const rows = sortMembers([
			{ name: 'b', online: false, role: 'master' },
			{ name: 'c', online: true, role: 'member' },
			{ name: 'a', online: true, role: 'deputy' }
		]);
		expect(rows.map((r) => r.name)).toEqual(['a', 'c', 'b']);
	});

	it('words where a member is like the friend list does', () => {
		expect(whereOf({ online: false }, 0).key).toBe('play.friend.offline');
		expect(whereOf({ online: true, status: 'dnd' }, 0).key).toBe('play.status.dnd');
		expect(whereOf({ online: true, status: 'online', place: 7 }, 0)).toEqual({ key: 'play.friend.inGame', vars: { room: 7 } });
		expect(whereOf({ online: true, status: 'online', place: 0, server: 0 }, 0).key).toBe('play.friend.lobby');
		expect(whereOf({ online: true, status: 'online', place: 0, server: 2 }, 0).key).toBe('play.friend.channel');
		// the server sends the channel as text, the page holds a number; and a channel is shown by its name
		expect(whereOf({ online: true, status: 'online', place: 0, server: '0' }, 0).key).toBe('play.friend.lobby');
		expect(whereOf({ online: true, status: 'online', place: 0, server: '2' }, 0, (id) => ({ 2: '다래' })[id]).vars).toEqual({ channel: '다래' });
	});
});

describe('family store (over the server packets)', () => {
	const sent = [];
	const wire = () => {
		sent.length = 0;
		family.detach();
		family.attach((type, data) => sent.push({ type, ...data }));
	};
	afterEach(() => {
		family.detach();
		vi.useRealTimers();
	});
	const mineOf = (extra = {}) => ({ id: 7, name: '끄투팸', intro: '', recruiting: true, fame: 0, level: 1, into: 0, need: 1000, capacity: 20, attendance: { today: 1, me: true }, applicants: [], members: [{ accountId: 'me', name: '나', activeName: '', role: 'master', online: true, status: 'online', server: '0', place: 0, weekly: 0, equip: {} }], ...extra });

	it('the founding conditions are checked here too, the server decides', () => {
		expect(family.createBlocker({ lv: 1, money: 1e6 })).toBe('level');
		expect(family.createBlocker({ lv: FAMILY_CREATE_LEVEL, money: FAMILY_CREATE_COST - 1 })).toBe('money');
		expect(family.createBlocker({ lv: FAMILY_CREATE_LEVEL, money: FAMILY_CREATE_COST })).toBe('');
	});

	it('an action sends one packet and resolves with the server answer', async () => {
		wire();
		const asked = family.apply(3);
		expect(sent).toEqual([{ type: 'family', op: 'apply', id: 3 }]);
		family.receive({ type: 'familyRes', op: 'apply', ok: true });
		expect(await asked).toEqual({ ok: true, reason: undefined });
		const failed = family.apply(4);
		family.receive({ type: 'familyRes', op: 'apply', ok: false, reason: 'full' });
		expect(await failed).toEqual({ ok: false, reason: 'full' });
	});

	it('the background refresh is never announced as an error (a guest is refused by every one of them)', async () => {
		const { toasts } = await import('./ui/toast.svelte.js');
		const before = toasts.length;
		family.receive({ type: 'familyRes', op: 'get', ok: false, reason: 'guest' });
		family.receive({ type: 'familyRes', op: 'list', ok: false, reason: 'guest' });
		expect(toasts.length).toBe(before);
		family.receive({ type: 'familyRes', op: 'chat', ok: false, reason: 'guest' }); // a line the player sent still is
		expect(toasts.length).toBe(before + 1);
	});

	it('answers go to the action they belong to, in order', async () => {
		wire();
		const a = family.kick('x');
		const b = family.answer('y', true);
		family.receive({ type: 'familyRes', op: 'answer', ok: false, reason: 'missing' });
		family.receive({ type: 'familyRes', op: 'kick', ok: true });
		expect((await a).ok).toBe(true);
		expect(await b).toEqual({ ok: false, reason: 'missing' });
	});

	it('gives up on an answer that never comes', async () => {
		vi.useFakeTimers();
		wire();
		const asked = family.leave();
		vi.advanceTimersByTime(8001);
		expect(await asked).toEqual({ ok: false, reason: 'timeout' });
	});

	it('does nothing without a connection', async () => {
		family.detach();
		expect(await family.apply(1)).toEqual({ ok: false, reason: 'offline' });
		expect(family.send('hi', '나')).toBe(false);
	});

	it('a snapshot replaces my family, who I am, and my applications', () => {
		wire();
		family.receive({ type: 'family', me: 'me', mine: mineOf(), applied: [], appliedFamilies: [], role: 'master' });
		expect(family.loaded).toBe(true);
		expect(family.mine.name).toBe('끄투팸');
		expect(family.role()).toBe('master');
		expect(family.chat[0]).toMatchObject({ system: true, msg: 'welcome' });
		family.receive({ type: 'family', me: 'me', mine: null, applied: [9], appliedFamilies: [{ id: 9, name: '신청한곳' }], role: null });
		expect(family.mine).toBeNull();
		expect(family.applied).toEqual([9]);
		expect(family.chat).toEqual([]); // the chat belongs to the family
	});

	it('chat: mine shows at once and goes out; theirs counts as unread unless the tab is open', () => {
		wire();
		family.receive({ type: 'family', me: 'me', mine: mineOf(), applied: [], appliedFamilies: [], role: 'master' });
		expect(family.send('  안녕  ', '나', '대표')).toBe(true);
		expect(sent.at(-1)).toEqual({ type: 'family', op: 'chat', value: '안녕' });
		expect(family.chat.at(-1)).toMatchObject({ msg: '안녕', mine: true, rep: '대표' });
		family.receive({ type: 'familyChat', familyId: 7, account: 'x', who: '남', rep: '', msg: '반가워', at: 1 });
		expect(family.unread).toBe(1);
		family.watch(true);
		expect(family.unread).toBe(0);
		family.receive({ type: 'familyChat', familyId: 7, account: 'x', who: '남', rep: '', msg: '또', at: 2 });
		expect(family.unread).toBe(0);
		family.receive({ type: 'familyChat', familyId: 99, account: 'x', who: '남', rep: '', msg: '다른 패밀리', at: 3 });
		expect(family.chat.some((m) => m.msg === '다른 패밀리')).toBe(false);
		expect(family.send('   ', '나')).toBe(false);
	});

	it('the search result replaces the directory, asked for once the typing pauses', () => {
		vi.useFakeTimers();
		wire();
		family.fetchList('끄');
		family.fetchList('끄투');
		vi.advanceTimersByTime(300);
		expect(sent).toEqual([{ type: 'family', op: 'list', query: '끄투' }]);
		family.receive({ type: 'familyList', families: [{ id: 1, name: '끄투' }] });
		expect(family.directory).toHaveLength(1);
	});
});
