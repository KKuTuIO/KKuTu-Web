import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import GameScreen from './GameScreen.svelte';
import { playSound } from '../audio.svelte.js';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
afterEach(cleanup);

const player = (id, extra = {}) => ({ id, profile: { title: id }, data: { score: 100, record: {} }, game: { score: 0, form: 'J', team: 0, ...extra.game }, ...extra });
function setup({ master = 'me', form = 'J' } = {}) {
	const store = createLobbyStore(1);
	const net = store.net;
	net.status = 'ready';
	net.id = 'me';
	net.meta.modes = ['KSH'];
	net.meta.rules = { KSH: { rule: 'Classic' } };
	net.meta.labels = { 'kkutu.js.turn.error.409': '이미 쓰인 단어', 'word.class.0': '명사', 'kkutu.js.scoreGain': '획득한 경험치', 'kkutu.js.moneyGain': '획득한 핑' };
	applyPacket(net, { type: 'room', room: { id: 5, title: '연습방', mode: 0, round: 3, time: 60, limit: 4, gaming: true, master, players: [player('me', { game: { form } }), player('bot', { robot: true, level: 2 })], game: { seq: ['me', 'bot'], turn: 0, title: ['가', '나', '다'] }, opts: {} } });
	const sent = [];
	store.conn.send = (type, data) => sent.push({ type, ...data });
	return { store, net, sent };
}
const feed = async (net, ...pkts) => {
	for (const p of pkts) applyPacket(net, p);
	await tick();
};
const wait = (n) => new Promise((r) => setTimeout(r, n));
const display = () => document.querySelector('[data-display]');

describe('GameScreen (a port of the legacy board)', () => {
	beforeEach(() => vi.useRealTimers());

	it('a player card opens that player\'s profile (legacy: a click on .game-user), with the mouse or the keyboard', async () => {
		const { store, net } = setup();
		const opened = [];
		store.hooks.openProfile = (id) => opened.push(id);
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' });
		await fireEvent.click(document.querySelector('[data-player="bot"]'));
		await fireEvent.keyDown(document.querySelector('[data-player="me"]'), { key: 'Enter' });
		expect(opened).toEqual(['bot', 'me']);
	});

	it('veils the board while the game loads, then shows the letter, the round letters and whose turn it is', async () => {
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'starting' });
		expect(screen.getByText('게임 정보를 불러오고 있습니다.')).toBeTruthy();
		expect(display().textContent.trim()).toBe(''); // the display stays empty until the round is ready
		await feed(net, { type: 'roundReady', round: 2, char: '가', subChar: '' });
		expect(display().textContent.trim()).toBe('가');
		await feed(net, { type: 'turnStart', turn: 1, char: '가', subChar: '나', speed: 1, turnTime: 10000, roundTime: 60000, wordLength: 3 });
		expect(display().textContent).toContain('가(나)');
		expect(display().querySelector('.jjo-display-word-length').textContent).toBe('(3)');
		expect(document.querySelector('[data-player="bot"]').getAttribute('aria-current')).toBe('true');
		expect(document.querySelector('.game-input').style.display).toBe('none'); // not my turn
	});

	it('on my turn the answer field appears over the history; Enter sends the word with the ids of the turn', async () => {
		const { store, net, sent } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 0, char: '가', turnTime: 10000, roundTime: 60000, gameId: 'g1', turnId: 't1', inputEpoch: 3 });
		expect(document.querySelector('.game-input').style.display).toBe('block');
		const input = document.querySelector('#game-input');
		expect(input.placeholder).toBe('여기에 입력하세요.');
		await fireEvent.input(input, { target: { value: '가위' } });
		await fireEvent.keyDown(input, { key: 'Enter', isComposing: true }); // still composing a syllable: not yet
		expect(sent).toHaveLength(0);
		await fireEvent.keyDown(input, { key: 'Enter' });
		expect(sent).toHaveLength(1);
		expect(sent[0]).toMatchObject({ type: 'talk', relay: true, value: '가위', gameId: 'g1', turnId: 't1', inputEpoch: 3, lastInputSeq: -1 });
		expect(atob(sent[0].checker)).toBe(String.fromCharCode(...new TextEncoder().encode('가위'))); // utf-8 bytes, like the server decodes
		expect(input.value).toBe('');
		await fireEvent.keyDown(input, { key: 'Enter' }); // empty: nothing
		expect(sent).toHaveLength(1);
	});

	it('does not say a refused word in the chat', async () => {
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 0, char: '가', turnTime: 10000, roundTime: 60000, wordLength: 3 });
		await feed(net, { type: 'turnError', code: 409, value: '가위' });
		expect(net.chat.some((l) => l.system && l.msg.includes('가위'))).toBe(false);
		expect(display().querySelector('.game-fail-text')).toBeNull(); // nothing was typed here, so nothing to strike
		await wait(2100);
		await tick();
		expect(display().querySelector('.game-fail-text')).toBeNull();
	});

	it('strikes out what I typed when it is refused, and the chat box answers too', async () => {
		const { store, net, sent } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 0, char: '가', turnTime: 10000, roundTime: 60000, gameId: 'g', turnId: 't', inputEpoch: 1 });
		store.sendChat('가위가위'); // typed in the chat box on my turn
		expect(sent.at(-1)).toMatchObject({ type: 'talk', relay: true, value: '가위가위' });
		await feed(net, { type: 'turnError', code: 409, value: '가위가위' });
		expect(display().querySelector('.game-fail-text').textContent).toBe('가위가위');
		await wait(2100);
		await tick();
		expect(display().querySelector('.game-fail-text')).toBeNull();
	});

	it('spells the word out, then lists it with its meaning; the score runs up and the change floats out of the card', async () => {
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 0, char: '가', speed: 1, turnTime: 120, roundTime: 60000 }, { type: 'turnEnd', ok: true, value: '가위', mean: '＂1＂［1］（1） 자르는 도구', wc: '0', score: 30, bonus: 10 });
		await wait(100);
		await tick();
		expect(display().textContent).toContain('가위');
		const item = document.querySelector('[data-history] .history-item');
		expect(item.textContent).toContain('가위');
		expect(item.querySelector('.history-class').textContent).toBe('명사');
		expect(item.querySelector('.history-mean').textContent).toContain('자르는 도구');
		expect(item.textContent).not.toContain('＂');
		expect(document.querySelector('[data-player="me"] [data-score]').dataset.value).toBe('30');
		expect(document.querySelector('[data-player="me"] .deltaScore[data-kind="gain"]').textContent).toBe('+20');
		await wait(3000);
		await tick();
		expect(document.querySelector('.deltaScore')).toBeNull(); // the floating score is gone once it has played
		expect(document.querySelector('[data-chain]').textContent).toBe('1');
		await wait(500);
		expect([...document.querySelectorAll('[data-player="me"] .game-user-score-char')].map((e) => e.textContent).join('')).toBe('00030');
	});

	it('shows the hint after a timeout, bright up to the letter and dimmed after it, and marks the card', async () => {
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 1, char: '가', turnTime: 100, roundTime: 60000 }, { type: 'turnEnd', ok: false, target: 'bot', score: -15, hint: { _id: '가방' } });
		expect(display().textContent).toBe('가방');
		expect(display().querySelectorAll('span')[1].getAttribute('style')).toContain('#AAAAAA');
		expect(document.querySelector('[data-player="bot"]').classList.contains('game-user-bomb')).toBe(true);
	});

	it('a spectator has no answer overlay', async () => {
		const { store, net } = setup({ master: 'bot', form: 'S' });
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '나', turn: 1, enter: true });
		expect(document.querySelector('.game-input').style.display).toBe('none');
	});

	it('opens the legacy result board after the game; closing it returns to the waiting room', async () => {
		vi.useFakeTimers();
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, {
			type: 'roundEnd',
			users: { me: { data: { score: 700, record: {} }, money: 5 } },
			result: [{ id: 'me', rank: 0, score: 80, reward: { score: 500, money: 20, _score: 400, _money: 20, _blog: [] } }, { id: 'bot', rank: 1, score: 5, reward: { score: 0, money: 0 } }]
		});
		expect(net.chat.at(-1).msg).toBe('획득한 경험치: 500, 획득한 핑: 20'); // also said in the chat
		expect(display().textContent.trim()).toBe('게임 끝!');
		expect(screen.queryByRole('dialog')).toBeNull();
		await vi.advanceTimersByTimeAsync(2100);
		const dialog = await screen.findByRole('dialog', { name: '게임 결과' });
		expect(dialog.querySelector('[data-row="me"] .result-board-rank').textContent).toBe('1');
		expect(dialog.querySelector('[data-reward="exp"]').textContent).toBe('획득한 경험치 +500(100)');
		expect(dialog.querySelector('[data-row="me"]').textContent).toContain('80점');
		expect(store.inGame).toBe(true);
		await vi.advanceTimersByTimeAsync(3000); // the gauge has run its course
		expect(dialog.querySelector('.result-me-score-text').textContent).toMatch(/^700 \/ /);
		await fireEvent.click(screen.getByRole('button', { name: '확인' }));
		await vi.runAllTimersAsync();
		expect(net.game).toBeNull();
	});

	it('타자 대결: the letters typed wrongly are struck out in red on the word being typed', async () => {
		const { store, net } = setup();
		net.meta.rules = { KSH: { rule: 'Typing' } };
		render(GameScreen, { store });
		const answer = '나는 감정을 느끼지 못하는 인공지능이다';
		await feed(net, { type: 'roundReady', round: 1, list: [answer] }, { type: 'turnStart', turn: 0, roundTime: 60000 });
		expect(display().textContent.trim().startsWith(answer)).toBe(true);
		expect(display().querySelectorAll('.jjo-wrong')).toHaveLength(0);
		store.setTyped('나는 감정을 느끼지 못 하는 인공지능이다'); // an extra space after 못
		await tick();
		const wrong = [...display().querySelectorAll('.jjo-wrong')].map((e) => e.textContent).join('');
		expect(wrong).toBe('하는 인공지능이다'); // from the first letter that differs, as the typed text runs on
		expect(display().querySelectorAll('.jjo-wrong')[0].textContent).toBe('하');
		// a syllable still being composed is not judged yet, a finished one is
		store.setTyped('나는 감정을 느끼지 ㅁ', true);
		await tick();
		expect(display().querySelectorAll('.jjo-wrong')).toHaveLength(0);
		store.setTyped('나는 감정을 느끼지 ㅁ', false);
		await tick();
		expect([...display().querySelectorAll('.jjo-wrong')].map((e) => e.textContent).join('')).toBe('못');
	});

	it('a letter of the mission turns green and plays the mission sound as it comes in', async () => {
		vi.mocked(playSound).mockClear();
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '사', mission: '과' }, { type: 'turnStart', turn: 0, char: '사', speed: 1, turnTime: 120, roundTime: 60000, mission: '과' }, { type: 'turnEnd', ok: true, value: '사과', score: 10, bonus: 0 });
		await wait(150);
		await tick();
		const played = vi.mocked(playSound).mock.calls.map(([name]) => name);
		expect(played).toContain('mission');
		expect(played.filter((n) => n.startsWith('As') || n === 'Al')).toHaveLength(2); // both letters have their beat (legacy pushDisplay), the mission letter adds the mission sound
		expect([...display().querySelectorAll('.jjo-ch')].find((e) => e.textContent === '과').style.color).toBe('rgb(102, 255, 102)');
	});

	it('타자 대결: Space sends the typed word, unless the room plays 속담 or 장문', async () => {
		const { store, net, sent } = setup();
		net.meta.rules = { KSH: { rule: 'Typing' } };
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, list: ['가나', '다라'] }, { type: 'turnStart', turn: 0, roundTime: 60000, gameId: 'g', turnId: 't', inputEpoch: 1 });
		const input = document.querySelector('#game-input');
		await fireEvent.input(input, { target: { value: '가나' } });
		await fireEvent.keyDown(input, { key: ' ', code: 'Space' });
		expect(sent.at(-1)).toMatchObject({ type: 'talk', relay: true, value: '가나' });
		expect(input.value).toBe('');
		const before = sent.length;
		await fireEvent.keyDown(input, { key: ' ', code: 'Space' }); // nothing typed: nothing sent
		expect(sent).toHaveLength(before);
		net.rooms[5].opts = { longtext: true };
		await fireEvent.input(input, { target: { value: '다라' } });
		await fireEvent.keyDown(input, { key: ' ', code: 'Space' });
		expect(sent).toHaveLength(before); // 장문: Space is just a space
	});

	it('퇴장 예약: it is said in the chat, and the room is left when the result is closed', async () => {
		const { store, net, sent } = setup();
		net.meta.labels['kkutu.js.leaveReserved'] = '게임이 끝나면 자동으로 나가지도록 예약되었습니다.';
		net.meta.labels['kkutu.js.leaveReserveDisabled'] = '퇴장 예약이 비활성화되었습니다.';
		store.toggleExitReserve();
		expect(store.ui.exitReserved).toBe(true);
		expect(net.chat.at(-1).msg).toBe('게임이 끝나면 자동으로 나가지도록 예약되었습니다.');
		store.toggleExitReserve();
		expect(net.chat.at(-1).msg).toBe('퇴장 예약이 비활성화되었습니다.');
		store.toggleExitReserve();
		store.closeResult(); // the result was closed
		expect(sent.at(-1)).toMatchObject({ type: 'leave' });
		expect(store.ui.exitReserved).toBe(false);
	});
});
