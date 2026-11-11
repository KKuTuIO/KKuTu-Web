import { describe, expect, it, vi } from 'vitest';
import { createLobbyStore } from './lobbyStore.svelte.js';
import { applyPacket } from './protocol.js';

vi.mock('./audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), playResultBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));

const player = (id) => ({ id, profile: { title: id }, data: { score: 0, record: {} }, game: { score: 0, form: 'J', team: 0 } });
function setup(rule, opts = {}) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	net.meta.modes = ['X'];
	net.meta.rules = { X: { rule } };
	applyPacket(net, { type: 'room', room: { id: 5, title: 't', mode: 0, round: 3, time: 60, limit: 4, gaming: true, master: 'me', players: [player('me'), player('you')], game: { seq: ['me', 'you'], turn: 0 }, opts } });
	return { store, net };
}
const turn = (net, extra = {}) => applyPacket(net, { type: 'turnStart', turn: 0, roundTime: 60000, turnTime: 10000, gameId: 'g', turnId: 't', inputEpoch: 'e', inputTraceContext: 7, ...extra });

describe('traceScope: the scope each rule names for the answer field (what the server accepts as its own)', () => {
	it('is the turn (no ref) for the word chain rules, and only on my turn', () => {
		const { store, net } = setup('Classic');
		applyPacket(net, { type: 'roundReady', round: 1, char: '가' });
		turn(net, { turn: 1 }); // the other player's
		expect(store.traceScope()).toBeNull();
		turn(net, { turn: 0 });
		expect(store.traceScope()).toEqual({});
	});

	it('is the round for 자음퀴즈 and 그림퀴즈', () => {
		const { store, net } = setup('Jaqwi');
		applyPacket(net, { type: 'roundReady', round: 2, char: 'ㄱㄴ' });
		turn(net);
		expect(store.traceScope()).toEqual({ r: 2 });
	});

	it('is one fixed scope for 솎솎 and 워드스택', () => {
		for (const rule of ['Sock', 'Wordstack']) {
			const { store, net } = setup(rule);
			applyPacket(net, { type: 'roundReady', round: 1, pool: { me: [] }, subpool: { me: [] } });
			turn(net);
			expect(store.traceScope()).toEqual({ r: 0 });
		}
	});

	it('is the selected bar for 십자말풀이, and nothing while none is selected', () => {
		const { store, net } = setup('Crossword');
		applyPacket(net, { type: 'roundReady', round: 1, seq: ['me', 'you'] });
		turn(net);
		expect(store.traceScope()).toBeNull();
		store.selectBar([1, 4, 2, 0]);
		expect(store.traceScope()).toEqual({ r: [1, 4, 2, 0] });
	});

	it('is nothing outside a running turn', () => {
		const { store } = setup('Classic');
		expect(store.traceScope()).toBeNull();
	});

	it('is the problem index for 타자 대결: a right answer counts at once, a refusal does not, and it wraps with the list', () => {
		const { store, net } = setup('Typing');
		applyPacket(net, { type: 'roundReady', round: 1, list: ['가나', '다라', '마바'] });
		turn(net);
		const sent = [];
		store.conn.send = (type, data) => sent.push({ type, ...data });
		expect(store.traceScope()).toEqual({ r: 0 });
		store.submitWord('가나'); // right, typed ahead of the server's answer
		expect(store.traceScope()).toEqual({ r: 1 });
		applyPacket(net, { type: 'turnEnd', ok: true, target: 'me', value: '가나', score: 1 }); // confirmed: the same index, not 2
		expect(store.traceScope()).toEqual({ r: 1 });
		store.submitWord('틀림'); // wrong: the server stays on the index
		expect(store.traceScope()).toEqual({ r: 1 });
		store.submitWord('다라');
		store.submitWord('마바');
		expect(store.traceScope()).toEqual({ r: 0 }); // wrapped round to the first text again
	});

	it('follows the confirmed count for 수학 대결 (the next problem is only shown after the answer)', () => {
		const { store, net } = setup('Mathquiz');
		applyPacket(net, { type: 'roundReady', round: 1, list: ['1+1', '2+2'] });
		turn(net);
		expect(store.traceScope()).toEqual({ r: 0 });
		applyPacket(net, { type: 'turnEnd', ok: true, target: 'me', value: '2', score: 1 });
		expect(store.traceScope()).toEqual({ r: 1 });
	});

	it('the chat box: pending input while another player is on turn in a turn-by-turn rule, the turn\'s own scope on my turn, nothing otherwise', () => {
		const { store, net } = setup('Classic');
		applyPacket(net, { type: 'roundReady', round: 1, char: '가' });
		expect(store.traceScope('chat')).toBeNull(); // no turn yet
		turn(net, { turn: 1 }); // the other player's
		expect(store.traceScope('chat')).toEqual({ pending: true });
		expect(store.traceScope('answer')).toBeNull(); // the answer field is not there yet
		turn(net, { turn: 0 });
		expect(store.traceScope('chat')).toEqual({});
		net.users.me.game.form = 'S'; // watching: nothing to trace
		turn(net, { turn: 1 });
		expect(store.traceScope('chat')).toBeNull();
	});

	it('the chat box in the quiz rules has the round\'s scope while the round runs (everyone answers at once) and no pending input', () => {
		const { store, net } = setup('Jaqwi');
		applyPacket(net, { type: 'roundReady', round: 1, char: 'ㄱㄴ' });
		expect(store.traceScope('chat')).toBeNull();
		turn(net);
		expect(store.traceScope('chat')).toEqual({ r: 1 });
	});

	it('watchField guards the field with the real store: a paste is cancelled while the room plays, not before, never for an administrator', () => {
		const paste = (input) => {
			const ev = new InputEvent('beforeinput', { inputType: 'insertFromPaste', cancelable: true, bubbles: true });
			input.dispatchEvent(ev);
			return ev.defaultPrevented;
		};
		const watched = (store) => {
			const input = document.createElement('input');
			document.body.append(input);
			return { input, handle: store.watchField(input, 'chat') };
		};
		const { store, net } = setup('Classic');
		store.conn.send = () => {};
		net.rooms[5].playing = false;
		const a = watched(store);
		expect(paste(a.input)).toBe(false); // waiting room
		net.rooms[5].playing = true;
		expect(paste(a.input)).toBe(true); // the game runs
		a.handle.destroy();
		const admin = setup('Classic');
		admin.net.admin = true;
		const b = watched(admin.store);
		expect(paste(b.input)).toBe(false);
		b.handle.destroy();
		document.body.innerHTML = '';
	});
});

