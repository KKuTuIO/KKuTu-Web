import { describe, expect, it, vi } from 'vitest';
import { HELP, idFromNick, isAdminCommand, parseCommand, runCommand } from './commands.js';
import { applyPacket, initialState } from './protocol.js';

const labels = { 'game.mode.KSH.name': '한국어 끝말잇기', 'game.mode.KKT.name': '한국어 쿵쿵따' };
const meta = { modes: ['KSH', 'KKT'], labels, rules: { KSH: { time: 1, opts: ['man'] }, KKT: { time: 1, opts: [] } }, optionDefs: { man: { name: 'Manner' } }, options: {}, moremiParts: [] };
const room = { no: 7, master: 'me', modeKey: 'KSH', modeIndex: 0, title: '방', limit: 8, rounds: 5, time: 60, opts: { manner: true }, injpick: ['a'] };

function setup(over = {}) {
	const calls = [];
	const c = {
		net: { id: 'me', guest: false, users: { me: { id: 'me', name: '나', tag: '' }, b: { id: 'b', name: '곰', tag: '1234' } }, meta },
		room,
		t: (k, v) => (v ? `${k}:${JSON.stringify(v)}` : k),
		notice: (s) => calls.push(['notice', s]),
		toast: (s) => calls.push(['toast', s]),
		send: (...a) => calls.push(['send', ...a]),
		toggleReady: () => calls.push(['ready']),
		start: () => calls.push(['start']),
		clearChat: () => calls.push(['clear']),
		updateRoom: (f) => calls.push(['update', f]),
		toggleBlock: (kind, id) => calls.push(['block', kind, id]),
		whisper: (to, text) => calls.push(['whisper', to, text]),
		recentFrom: '곰#1234',
		titleAllowed: (s) => !s.includes('욕'),
		fetchId: (n) => calls.push(['fetchId', n]),
		hooks: { openSettings: () => calls.push(['settings']), openProfile: (id) => calls.push(['profile', id]), openRoomSettings: () => calls.push(['roomSettings']), openHelp: () => calls.push(['help']) },
		...over
	};
	return { c, calls, run: (text) => runCommand(text, c) };
}

describe('parseCommand', () => {
	it('knows the Korean-layout and English aliases of each command', () => {
		expect(parseCommand('/ㄱ').name).toBe('ready');
		expect(parseCommand('/R').name).toBe('ready');
		expect(parseCommand('/청소').name).toBe('clear');
		expect(parseCommand('/ㅂㅈ 새 제목').args).toEqual(['새', '제목']);
		expect(parseCommand('/nope').name).toBeNull();
	});
	it('recognises admin commands, which only the server runs', () => {
		expect(isAdminCommand('#yell hello')).toBe(true);
		expect(isAdminCommand(' #delroom 12')).toBe(true);
		expect(isAdminCommand('# not one')).toBe(false);
		expect(isAdminCommand('hello #x')).toBe(false);
	});
});

describe('runCommand', () => {
	it('/r starts as the master and readies as a player', () => {
		const a = setup();
		a.run('/r');
		expect(a.calls).toEqual([['start']]);
		const b = setup({ room: { ...room, master: 'b' } });
		b.run('/ㄱ');
		expect(b.calls).toEqual([['ready']]);
	});
	it('room commands are for the master of my room only', () => {
		const a = setup({ room: { ...room, master: 'b' } });
		a.run('/인원 4');
		expect(a.calls).toEqual([['notice', 'play.cmd.onlyMaster']]);
		const b = setup({ room: null });
		b.run('/qw title');
		expect(b.calls).toEqual([['notice', 'play.cmd.onlyMaster']]);
	});
	it('changes one setting and keeps the rest of the room (including the word themes)', () => {
		const a = setup();
		a.run('/방제 새 방 제목');
		a.run('/dd 6');
		a.run('/fde 3');
		a.run('/tr 90');
		a.run('/qq 비번');
		expect(a.calls.map((x) => x[1])).toEqual([
			{ title: '새 방 제목', limit: 8, mode: 0, round: 5, time: 60, opts: { manner: true }, injpick: ['a'] },
			{ title: '방', limit: 6, mode: 0, round: 5, time: 60, opts: { manner: true }, injpick: ['a'] },
			{ title: '방', limit: 8, mode: 0, round: 3, time: 60, opts: { manner: true }, injpick: ['a'] },
			{ title: '방', limit: 8, mode: 0, round: 5, time: 90, opts: { manner: true }, injpick: ['a'] },
			{ title: '방', limit: 8, mode: 0, round: 5, time: 60, opts: { manner: true }, injpick: ['a'], password: '비번' }
		]);
	});
	it('/방제 without a title uses the default one, and refuses titles the game does not allow', () => {
		const a = setup();
		a.run('/방제');
		a.run('/방제 욕설');
		expect(a.calls[0][1].title).toBe('play.cmd.defaultTitle:{"nick":"나"}');
		expect(a.calls[1]).toEqual(['toast', 'play.create.titleBad']);
	});
	it('only accepts the round times the game offers', () => {
		const a = setup();
		a.run('/시간 45');
		expect(a.calls[0][0]).toBe('notice');
		expect(a.calls[0][1]).toContain('play.cmd.invalidTime');
	});
	it('changes the game type by its name and drops options the new type lacks', () => {
		const a = setup();
		a.run('/유형 한국어 쿵쿵따');
		expect(a.calls[0][1]).toMatchObject({ mode: 1, opts: {} });
		a.run('/유형 없는 유형');
		expect(a.calls[1]).toEqual(['notice', 'play.cmd.invalidMode']);
	});
	it('prints the usage when an argument is missing, and opens the help window for /도움말', () => {
		const a = setup();
		a.run('/인원');
		expect(a.calls).toEqual([['notice', 'play.cmd.help.dd']]);
		const b = setup();
		b.run('/help');
		expect(b.calls).toEqual([['help']]);
		expect(HELP).toHaveLength(18);
	});
	it('answers an unknown command', () => {
		const a = setup();
		a.run('/zzz');
		expect(a.calls).toEqual([['notice', 'play.cmd.invalid']]);
	});
	it('/id shows my own id, a known player by nickname, or asks the server', () => {
		const a = setup();
		a.run('/id');
		a.run('/식별번호 곰#1234');
		a.run('/id 모르는 사람');
		expect(a.calls[0]).toEqual(['notice', 'play.cmd.myId:{"id":"me"}']);
		expect(a.calls[1]).toEqual(['notice', '[곰#1234] b']);
		expect(a.calls[2]).toEqual(['fetchId', '모르는 사람']);
		expect(idFromNick(a.c.net.users, '곰')).toBe('b');
	});
	it('/uid asks for the code, and for confirmation before issuing a new one', () => {
		const a = setup();
		a.run('/uid');
		a.run('/uid 재발급');
		a.run('/uid 재발급 확인');
		expect(a.calls).toEqual([['send', 'uid', undefined, true], ['notice', 'play.cmd.uidConfirm'], ['send', 'newUid', undefined, true]]);
		const g = setup({ net: { ...a.c.net, guest: true } });
		g.run('/uid');
		expect(g.calls).toEqual([['notice', 'play.cmd.uidGuest']]);
	});
	it('opens the screens: settings, a profile (mine by default), the room settings', () => {
		const a = setup();
		a.run('/설정');
		a.run('/정보');
		a.run('/info b');
		a.run('/주제');
		a.run('/정보 없음');
		expect(a.calls).toEqual([['settings'], ['profile', 'me'], ['profile', 'b'], ['roomSettings'], ['profile', '없음']]);
	});
	it('/귓 whispers to one name; /답 answers the last one who whispered', () => {
		const a = setup();
		a.run('/귓 곰 안녕 하세요');
		a.run('/ee 반가워');
		expect(a.calls).toEqual([['whisper', '곰', '안녕 하세요'], ['whisper', '곰#1234', '반가워']]);
		const b = setup({ recentFrom: '' });
		b.run('/답 hi');
		expect(b.calls).toEqual([['notice', 'play.cmd.noReply']]);
	});
	it('/차단 flips both kinds of block, /무시 only the whisper one; either needs a target', () => {
		const a = setup();
		a.run('/차단 곰#1234');
		a.run('/무시 b');
		a.run('/차단');
		expect(a.calls).toEqual([['block', 'shut', 'b'], ['block', 'mute', 'b'], ['block', 'mute', 'b'], ['notice', 'play.cmd.help.shut']]);
	});
	it('/청소 empties the chat', () => {
		const a = setup();
		a.run('/cls');
		expect(a.calls).toEqual([['clear']]);
	});
});

describe('"#" lines in the chat', () => {
	it('are never shown, like in the legacy client; normal lines are', () => {
		const s = initialState();
		applyPacket(s, { type: 'chat', profile: { id: 'x', title: 'a' }, value: '#delroom 12' });
		applyPacket(s, { type: 'chat', profile: { id: 'x', title: 'a' }, value: '안녕 #1' });
		expect(s.chat.map((m) => m.msg)).toEqual(['안녕 #1']);
	});
});
