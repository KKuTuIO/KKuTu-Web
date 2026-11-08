import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { applyPacket } from '../protocol.js';
import GameScreen from './GameScreen.svelte';
import LevelUpHost from './LevelUpHost.svelte';
import { levelUp } from '../levelUp.svelte.js';
import { playResultBgm, playSound } from '../audio.svelte.js';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(), playLobbyBgm: vi.fn(), playResultBgm: vi.fn(), stopBgm: vi.fn(), audio: {} }));
afterEach(() => {
	cleanup();
	levelUp.reset();
});

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

/** The result flow is the full ranking, then (다음) my own result with the rewards and the gauge. */
async function toMine(dialog) {
	dialog.querySelector('[data-step-next]').click();
	await vi.advanceTimersByTimeAsync(700); // the podium and the table slide out first
	expect(dialog.querySelector('[data-step="mine"] .verdict-text')).not.toBeNull();
}

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

	it('sends an administrator\'s "#" line to the server as it is, even on my turn, and never as my answer', async () => {
		const { store, net, sent } = setup();
		net.admin = true;
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 0, char: '가', turnTime: 10000, roundTime: 60000, gameId: 'g', turnId: 't', inputEpoch: 1 });
		store.sendChat('#roominfo 5');
		store.sendChat('#notice hi');
		expect(sent).toEqual([{ type: 'talk', value: '#roominfo 5' }, { type: 'talk', value: '#notice hi' }]);
		expect(net.game.submitted).toBeUndefined();
	});

	it('treats "#" as an ordinary answer when I am not an administrator', async () => {
		const { store, net, sent } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 0, char: '가', turnTime: 10000, roundTime: 60000, gameId: 'g', turnId: 't', inputEpoch: 1 });
		store.sendChat('#notice hi');
		expect(sent.at(-1)).toMatchObject({ type: 'talk', relay: true, value: '#notice hi' });
	});

	it('shows the questions solved on a player\'s card in place of the speed, and the speed when there is no such count', async () => {
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 0, char: '가', turnTime: 10000, roundTime: 60000, gameId: 'g', turnId: 't', inputEpoch: 1 });
		net.game.kpm = { me: 52, bot: 40 };
		net.game.solved = { me: 314, bot: 120 };
		await tick();
		expect(document.querySelector('[data-player="me"] [data-solved]').textContent).toContain('314');
		expect(document.querySelector('[data-player="bot"] [data-solved]').textContent).toContain('120');
		net.game.solved = null;
		await tick();
		expect(document.querySelector('[data-solved]')).toBeNull();
		expect(document.querySelector('[data-player="me"]').textContent).toContain('52');
	});

	it('does not bring back my earlier word when somebody else is refused', async () => {
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '이' }, { type: 'turnStart', turn: 0, char: '이', turnTime: 10000, roundTime: 60000, gameId: 'g', turnId: 't', inputEpoch: 1 });
		store.sendChat('이리듐'); // my word, accepted
		await feed(net, { type: 'turnEnd', ok: true, value: '이리듐', score: 10, bonus: 0 }, { type: 'turnStart', turn: 1, char: '듐', turnTime: 10000, roundTime: 60000, gameId: 'g', turnId: 't2', inputEpoch: 1 });
		await feed(net, { type: 'turnError', code: 404, value: '듐프' }); // the next player is refused
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
		render(LevelUpHost, { store });
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
		expect(playResultBgm).toHaveBeenCalled(); // the result's own music starts with the screen
		// two players and no teams: a duel, my score in the flaps and the outcome at the top, no table
		expect(dialog.querySelector('.frame').dataset.layout).toBe('duel');
		expect([...dialog.querySelectorAll('.pcol')].map((e) => e.dataset.place)).toEqual(['2', '1']); // a duel: the winner in the middle of two steps
		await toMine(dialog);
		expect(dialog.querySelector('.verdict-text').textContent).toBe('승리');
		expect(dialog.querySelector('[data-reward="exp"]').textContent).toBe('+500');
		expect(dialog.querySelector('[data-say]').textContent).toMatch(/\S/); // my moremi says something about the game
		expect(dialog.querySelector('button.fixed')).not.toBeNull(); // and the chat button is inside the modal
		expect(store.inGame).toBe(true);
		// 200 -> 700 experience passes level 3 on the way: the gauge stops for a level-up popup, Space goes on
		await vi.advanceTimersByTimeAsync(3000);
		expect(dialog.querySelector('[data-gauge-text]').textContent).not.toMatch(/^700 \//);
		for (let i = 0; i < 5 && screen.queryByRole('dialog', { name: '레벨 업!' }); i++) {
			await fireEvent.keyDown(window, { key: ' ' }); // too soon after the popup came up: ignored
			await vi.advanceTimersByTimeAsync(400);
			await fireEvent.keyDown(window, { key: ' ' });
			await vi.advanceTimersByTimeAsync(3000);
		}
		expect(screen.queryByRole('dialog', { name: '레벨 업!' })).toBeNull();
		expect(dialog.querySelector('[data-gauge-text]').textContent).toMatch(/^700 \/ /); // the gauge has run its course
		await fireEvent.click(screen.getByRole('button', { name: '확인' }));
		await vi.runAllTimersAsync();
		expect(net.game).toBeNull();
	});

	/** A game of `n` players (me first) that has ended, the board open. `teams[i]` is player i's team. */
	async function endedGame(n, teams = []) {
		vi.useFakeTimers();
		const { store, net } = setup();
		const ids = ['me', ...Array.from({ length: n - 1 }, (_, i) => `p${i + 2}`)];
		applyPacket(net, { type: 'room', room: { id: 5, title: '결과', mode: 0, round: 1, time: 60, limit: 8, gaming: true, master: 'me', players: ids.map((id, i) => player(id, { game: { team: teams[i] ?? 0 } })), game: { seq: ids, turn: 0, title: ['가'] } } });
		render(GameScreen, { store });
		render(LevelUpHost, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' });
		ids.forEach((id, i) => { net.users[id].game.score = [1524, 804, 736, 600, 411][i] ?? 100; });
		net.game.played = [{ id: 'me', word: '칠면조', score: 61, bonus: 0, round: 1 }, { id: 'me', word: '가방', score: 12, bonus: 0, round: 1 }];
		await feed(net, { type: 'roundEnd', users: {}, result: ids.map((id, i) => ({ id, rank: i, score: net.users[id].game.score, reward: { score: 6, money: 21, _score: 6, _money: 21, _blog: [] } })) });
		await vi.advanceTimersByTimeAsync(2100);
		const dialog = await screen.findByRole('dialog', { name: '게임 결과' });
		return { dialog, net, store };
	}

	it('a game with teams: the team bar in rank order with my team marked, my own score as the hero, the list with team letters', async () => {
		const { dialog } = await endedGame(5, [4, 4, 1, 3, 2]);
		expect(dialog.querySelector('.frame').dataset.layout).toBe('teams');
		expect([...dialog.querySelectorAll('.seg')].map((e) => e.dataset.team)).toEqual(['D', 'A', 'C', 'B']); // 2328, 736, 600, 411 by team
		expect(dialog.querySelector('.seg[data-team="D"]').textContent).toContain('1위');
		expect(dialog.querySelector('.mymark-text').textContent).toBe('내 팀');
		expect([...dialog.querySelectorAll('.pcol')].map((e) => e.dataset.place)).toEqual(['2', '1', '3']); // the podium: the first three of the ranking
		expect(dialog.querySelectorAll('[data-row]')).toHaveLength(5);
		expect(dialog.querySelector('[data-row="me"]').className).toContain('tint-4');
		await toMine(dialog);
		expect(dialog.querySelector('.headline-text').textContent).toBe('개인 1위, D팀 1위');
		expect(dialog.querySelector('.w-tray').textContent).toContain('가방');
	});

	it('three players and no teams: the list and my score, no team bar and no duel', async () => {
		const { dialog } = await endedGame(3);
		expect(dialog.querySelector('.frame').dataset.layout).toBe('list');
		expect(dialog.querySelector('.teambar')).toBeNull();
		expect(dialog.querySelectorAll('[data-row]')).toHaveLength(3);
		await toMine(dialog);
		expect(dialog.querySelector('.verdict-text').textContent).toBe('1위');
	});

	/** A result for me going from 200 to `after` experience, opened and with the gauge started. */
	async function bigResult(after) {
		vi.useFakeTimers();
		vi.mocked(playSound).mockClear();
		const { store, net } = setup();
		render(GameScreen, { store });
		render(LevelUpHost, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' }, {
			type: 'roundEnd',
			users: { me: { data: { score: after, record: {} }, money: 5 } },
			result: [{ id: 'me', rank: 0, score: 80, reward: { score: after - 200, money: 20, _score: after - 200, _money: 20, _blog: [] } }, { id: 'bot', rank: 1, score: 5, reward: { score: 0, money: 0 } }]
		});
		await vi.advanceTimersByTimeAsync(2100);
		const dialog = await screen.findByRole('dialog', { name: '게임 결과' });
		await toMine(dialog);
		await vi.advanceTimersByTimeAsync(3000);
		return { dialog, store, net };
	}
	const press = async (key) => {
		await vi.advanceTimersByTimeAsync(400); // a key that comes too soon after a popup is ignored
		await fireEvent.keyDown(window, { key });
		await vi.advanceTimersByTimeAsync(6000); // long enough for the gauge to run to the end
	};

	it('level-up: a jump of many levels gets three popups and one summary, with the lvup sound for each', async () => {
		const { dialog } = await bigResult(60000); // from level 2 to well past level 5
		const seen = [];
		for (let i = 0; i < 6; i++) {
			const pop = screen.queryByRole('dialog', { name: '레벨 업!' });
			if (!pop) break;
			seen.push(pop.getAttribute('data-kind'));
			await press(' ');
		}
		expect(seen).toEqual(['level', 'level', 'level', 'summary']); // never more than cap + 1 presses
		expect(vi.mocked(playSound).mock.calls.filter((c) => c[0] === 'lvup')).toHaveLength(4);
		expect(dialog.querySelector('[data-gauge-text]').textContent).toMatch(/^60,000 \/ /);
	});

	it('level-up: Esc skips the rest to one summary and the gauge goes on; it does not close the board', async () => {
		const { dialog } = await bigResult(60000);
		expect(screen.getByRole('dialog', { name: '레벨 업!' })).toBeTruthy();
		await press('Escape'); // skip: the remaining levels fold into the closing summary
		const summary = screen.getByRole('dialog', { name: '레벨 업!' });
		expect(summary.getAttribute('data-kind')).toBe('summary');
		expect(dialog.open).toBe(true);
		await press(' ');
		expect(screen.queryByRole('dialog', { name: '레벨 업!' })).toBeNull();
		expect(dialog.querySelector('[data-gauge-text]').textContent).toMatch(/^60,000 \/ /);
	});

	it('선두: the badge is on the card of whoever has the highest score, on both on a tie, and on none while the scores are zero', async () => {
		const { store, net } = setup();
		render(GameScreen, { store });
		await feed(net, { type: 'roundReady', round: 1, char: '가' });
		const leaders = () => [...document.querySelectorAll('[data-leader]')].map((e) => e.closest('[data-player]').getAttribute('data-player'));
		expect(leaders()).toEqual([]); // 0 to 0
		net.users.me.game.score = 300;
		net.users.bot.game.score = 100;
		await tick();
		expect(leaders()).toEqual(['me']);
		expect(document.querySelector('[data-leader]').textContent).toBe('선두');
		net.users.bot.game.score = 300;
		await tick();
		expect(leaders().sort()).toEqual(['bot', 'me']);
		net.users.bot.game.score = 450;
		await tick();
		expect(leaders()).toEqual(['bot']);
	});

	describe('the wrong keyboard', () => {
		/** My turn in a room of `lang`; the text goes into the answer field like typing does. */
		async function myTurn(lang) {
			const { store, net } = setup();
			net.meta.rules = { KSH: { rule: 'Classic', lang } };
			render(GameScreen, { store });
			await feed(net, { type: 'roundReady', round: 1, char: '가' }, { type: 'turnStart', turn: 0, char: '가', turnTime: 10000, roundTime: 60000, wordLength: 3 });
			const type = async (text) => {
				const input = document.getElementById('game-input');
				input.value = text;
				await fireEvent.input(input);
			};
			const warn = () => document.querySelector('[data-lang-warn]');
			return { type, warn };
		}

		it('says so above the field when English letters are typed in a Korean room, and not for Korean ones', async () => {
			const { type, warn } = await myTurn('ko');
			await type('가위');
			expect(warn()).toBeNull();
			await type('dkssud'); // 안녕 with the English layout
			expect(warn().getAttribute('data-lang-warn')).toBe('ko');
			expect(warn().querySelector('[data-lang-warn-text]').textContent).toBe('영문 입력 상태');
			expect(warn().closest('.game-input')).toBeTruthy(); // it sits with the answer field
			await type('');
			expect(warn()).toBeNull();
		});

		it('says so when Korean is typed in an English room, and not for English words', async () => {
			const { type, warn } = await myTurn('en');
			await type('apple');
			expect(warn()).toBeNull();
			await type('apple가');
			expect(warn().getAttribute('data-lang-warn')).toBe('en');
			expect(warn().querySelector('[data-lang-warn-text]').textContent).toBe('한글 입력 상태');
		});

		it('only warns, it never changes what was typed', async () => {
			const { type } = await myTurn('ko');
			await type('dkssud');
			expect(document.getElementById('game-input').value).toBe('dkssud');
		});
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
