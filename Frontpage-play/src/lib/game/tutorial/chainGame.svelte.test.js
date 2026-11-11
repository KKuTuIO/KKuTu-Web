import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../audio.svelte.js', () => ({ playSound: vi.fn(() => Promise.resolve({ stop() {} })), playGameBgm: vi.fn(), stopBgm: vi.fn() }));

import { createChainGame, INPUT, MISSION, REMI } from './chainGame.svelte.js';
import { MEANING, PLAYER_WORDS, ROUNDS, SCORE, judge, lastChar, nextTurnDelay, penalty, robotDelay, turnSpeed, turnTimeOf } from './chainData.js';
import { initialState } from '../protocol.js';
import { t } from '../i18n.svelte.js';
import { playSound } from '../audio.svelte.js';

describe('chain data', () => {
	it('is a chain in every round: each word begins with the last letter of the one before, and the first with the round letter', () => {
		for (const r of ROUNDS) {
			expect(r.moves[0].word.startsWith(r.char), r.char).toBe(true);
			r.moves.slice(1).forEach((m, i) => expect(m.word.startsWith(lastChar(r.moves[i].word)), m.word).toBe(true));
			for (const m of r.moves) expect(MEANING[m.word], m.word).toBeTruthy();
		}
		expect(PLAYER_WORDS).toEqual(['물레방아', '씨앗', '어린이', '몬스터']);
		expect(ROUNDS.length).toBe(3);
	});
	it('teaches mission, 매너 and 어인정 in the rounds, in that order', () => {
		const [r1, r2, r3] = ROUNDS;
		expect(r1.opts.mission).toBe(true);
		expect(r1.moves.find((m) => m.mission).word).toContain(r1.mission); // the player\'s word has the mission letter
		expect(r2.moves.at(-1)).toMatchObject({ by: 'remi', word: '이리듐', hanbang: [2, 3] }); // a 한방 word: only its last letter
		expect(r2.ends).toMatchObject({ by: 'me', stuck: true });
		expect(r2.then.opts).toEqual({ manner: true });
		expect(r3.opts.manner).toBe(true);
		expect(r3.moves.at(-1).injeong).toBe(true);
		expect(r3.moves.at(-1).then.opts).toEqual({ manner: true, injeong: true });
	});
	it('judges: the word asked for, or what is wrong with another', () => {
		const want = { char: '물', word: '물레방아' };
		expect(judge(want, '물레방아')).toEqual({ ok: true });
		expect(judge(want, '물고기')).toEqual({ ok: false, reason: 'other' });
		expect(judge(want, '사과')).toEqual({ ok: false, reason: 'start' });
	});
	it('counts time and penalties the way the game server does', () => {
		expect(turnSpeed(90000)).toBe(1);
		expect(turnSpeed(100000)).toBe(0);
		expect(turnSpeed(4000)).toBe(10);
		expect(turnTimeOf(1)).toBe(13600);
		expect(turnTimeOf(10)).toBe(1000);
		expect(nextTurnDelay(13600)).toBeCloseTo(2266.67, 1);
		expect(robotDelay('그물')).toBe(800 + 2 * 600);
		expect(penalty(2, 24)).toBe(-18); // 10 + 2 * 2.1 + 24 * 0.15 = 17.8, at most the score
		expect(penalty(0, 0)).toBe(0);
		expect(penalty(9, 5)).toBe(-5); // never more than what the player has
	});
});

describe('the practice game', () => {
	let game, tips, focuses, ended, waiting, said;
	const real = () => {
		const net = initialState();
		net.id = 'me';
		net.meta = { modes: ['EKT', 'KSH'], rules: { KSH: { rule: 'Classic', lang: 'ko' } }, labels: { 'game.option.Manner.name': '매너', 'game.option.Injeong.name': '어인정', 'game.option.Mission.name': '미션' }, options: { manner: 'Manner', injeong: 'Injeong', mission: 'Mission' }, optionDefs: {}, moremiParts: [] };
		net.users.me = { id: 'me', name: '나', tag: '', score: 0, equip: {}, guest: false };
		return { net, shop: {} };
	};
	const go = (ms) => vi.advanceTimersByTimeAsync(ms);
	/** She explains something and waits: the next line of hers is pressed (다음). */
	const next = async () => {
		const press = waiting.shift();
		expect(press, 'a line waiting for 다음').toBeTruthy();
		press();
		await go(0);
	};
	/** The player types a word: what follows (the word played, the next lines) happens once the game has gone on. */
	const type = async (word) => {
		game.store.submitWord(word);
		await go(0);
	};
	const T = turnTimeOf(1);
	const GAP = Math.ceil(nextTurnDelay(T));
	const g = () => game.store.game;
	/** Plays round 1 up to the player's first turn. */
	const toFirstTurn = async () => {
		await go(1000); // loading
		await next(); // the mission line
		await go(2400 + robotDelay('그물') + GAP);
	};
	beforeEach(() => {
		vi.useFakeTimers();
		tips = [];
		focuses = [];
		said = [];
		waiting = [];
		ended = 0;
		game = createChainGame({
			real: real(),
			tip: (key, o) => tips.push([key, o?.params]),
			line: (key, o) => new Promise((resolve) => (said.push([key, o?.params]), waiting.push(resolve))),
			focus: (sel, label) => focuses.push([sel, label]),
			onEnd: () => ended++,
			onExit: () => {}
		});
		game.start();
	});
	afterEach(() => {
		game.dispose();
		vi.useRealTimers();
		vi.clearAllMocks();
	});

	describe('round 1: 미션', () => {
		it('deals round 1 with the mission letter, points at it and waits until she is understood', async () => {
			await go(999);
			expect(g().phase).toBe('loading');
			await go(1);
			expect(g().phase).toBe('ready');
			expect(g().char).toBe('그');
			expect(g().mission).toBe('방');
			expect(game.store.currentRoom.opts.mission).toBe(true);
			expect(focuses.at(-1)).toEqual([MISSION, undefined]);
			expect(said.at(-1)).toEqual(['play.story.chain.r1.mission', { mission: '방' }]);
			await go(20000); // nothing happens while it is not read
			expect(g().phase).toBe('ready');
			await next();
			expect(focuses.at(-1)).toEqual([null, undefined]);
			await go(2399);
			expect(g().phase).toBe('ready');
			await go(1);
			expect(g().phase).toBe('turn'); // the first turn comes 2.4 s after she is done, as the server's does after roundReady
			expect(g().current).toBe(REMI);
			expect(g().turnTime).toBe(13600);
		});
		it('plays the real game sounds: the round start and the turn clock', async () => {
			await go(1000);
			expect(playSound).toHaveBeenCalledWith('round_start');
			await next();
			await go(2400);
			expect(playSound).toHaveBeenCalledWith('T1');
		});
		it('the guide types after the robot\'s delay; then the player points at the answer field with the word', async () => {
			await go(1000);
			await next();
			await go(2400 + robotDelay('그물') - 1);
			expect(g().words).toHaveLength(0);
			await go(1);
			expect(g().words.map((w) => w.word)).toEqual(['그물']);
			await go(GAP);
			expect(g().char).toBe('물');
			expect(game.store.myTurn).toBe(true); // paused, and the answer field is there at once: nothing to press first
			expect(said.some(([k]) => k === 'play.story.chain.pause')).toBe(false); // (it is a plain line, not one she waits on)
			expect(tips.at(-1)[0]).toBe('play.story.chain.pause'); // she says the game is paused for them
			expect(focuses.at(-1)).toEqual([INPUT, t('play.tutorial.chain.type', { word: '물레방아' })]);
			expect(tips.at(-1)[0]).toBe('play.story.chain.pause'); // (the mission hint follows it: see the test below)
		});
		it('a mission word scores its bonus, the guide says so and the next turn has a new mission letter', async () => {
			await toFirstTurn();
			await type('물레방아');
			expect(g().last).toMatchObject({ ok: true, value: '물레방아', score: SCORE.me + SCORE.missionBonus, bonus: SCORE.missionBonus });
			expect(tips.at(-1)).toEqual(['play.story.chain.r1.done', { mission: '방', bonus: 12 }]);
			expect(focuses.at(-1)).toEqual([null, undefined]);
			await go(GAP);
			expect(g().current).toBe(REMI);
			expect(g().mission).toBe('기');
		});
		it('refuses another word with the board message and the guide telling what to type', async () => {
			await toFirstTurn();
			await type('사과');
			expect(g().fail.text).toBe(t('play.tutorial.chain.err.start', { char: '물', word: '물레방아' }));
			expect(tips.at(-1)).toEqual(['play.story.chain.err.start', { char: '물', word: '물레방아' }]);
			await type('물고기');
			expect(tips.at(-1)[0]).toBe('play.story.chain.err.other');
			expect(game.store.myTurn).toBe(true);
			expect(g().words).toHaveLength(1);
		});
		it('pauses the game for the player: the clock stands still, nothing ticks and nothing runs out, however long they take', async () => {
			await toFirstTurn();
			expect(game.store.ui.pause).toMatchObject({ on: true, kind: 'pause' });
			const id = g().turnId;
			const now = game.store.now();
			await go(120000);
			expect(game.store.now()).toBe(now); // the board reads this clock: the turn bar stands still
			expect(g().turnId).toBe(id);
			expect(game.store.myTurn).toBe(true);
			expect(g().words).toHaveLength(1);
			expect(playSound.mock.calls.filter(([n]) => n === 'timeout')).toHaveLength(0);
			expect(playSound.mock.calls.filter(([n]) => n === 'T1')).toHaveLength(1); // the guide's turn ticked; the player's did not
			expect(tips.some(([k]) => k === 'play.story.chain.again')).toBe(false);
		});
		it('the line about the pause is followed by the turn\'s hint, after a while', async () => {
			await toFirstTurn();
			expect(tips.at(-1)[0]).toBe('play.story.chain.pause');
			expect(tips.some(([k]) => k === 'play.story.chain.r1.hint')).toBe(false);
			await go(6100);
			expect(tips.at(-1)[0]).toBe('play.story.chain.r1.hint'); // the mission hint
		});
		it('a player who types before that never sees the old hint come back', async () => {
			await toFirstTurn();
			await type('물레방아');
			await go(7000);
			expect(tips.some(([k]) => k === 'play.story.chain.r1.hint')).toBe(false);
		});
		it('says it is paused only the first time; later turns only show the pause picture', async () => {
			await toFirstTurn();
			const n = game.store.ui.pause.n;
			await type('물레방아');
			expect(game.store.ui.pause).toMatchObject({ on: false, kind: 'play' }); // the game goes on: the play picture
			expect(game.store.ui.pause.n).toBe(n + 1);
			await go(GAP + robotDelay('아저씨') + GAP);
			expect(game.store.ui.pause).toMatchObject({ on: true, kind: 'pause' }); // paused again for 씨앗 ...
			expect(game.store.myTurn).toBe(true); // ... without the line about it again
			expect(tips.filter(([k]) => k === 'play.story.chain.pause')).toHaveLength(1);
		});
		it('the clock runs again once the word is in, and the guide\'s turns are never paused', async () => {
			await toFirstTurn();
			await type('물레방아');
			const now = game.store.now();
			await go(1000);
			expect(game.store.now() - now).toBeGreaterThanOrEqual(1000);
			await go(GAP - 1000 + 1);
			expect(g().current).toBe(REMI);
			expect(game.store.ui.pause.on).toBe(false);
			await go(robotDelay('아저씨') - 100);
			expect(game.store.ui.pause.on).toBe(false);
		});
		it('ends when the guide\'s clock runs out: a one-second turn, the penalty and a hint', async () => {
			await toFirstTurn();
			await type('물레방아');
			await go(GAP + robotDelay('아저씨') + GAP);
			await type('씨앗');
			expect(g().words.map((w) => w.word)).toEqual(['그물', '물레방아', '아저씨', '씨앗']);
			await go(GAP);
			expect(tips.at(-1)[0]).toBe('play.story.chain.r1.out');
			expect(g().turnTime).toBe(1000); // the server's last turn of a round: speed 10
			expect(g().speed).toBe(10);
			const before = game.store.net.users[REMI].game.score;
			await go(1100);
			expect(g().last).toMatchObject({ ok: false, id: REMI, hint: '아침' });
			expect(g().last.score).toBeLessThan(0);
			expect(game.store.net.users[REMI].game.score).toBeLessThan(before);
			expect(playSound).toHaveBeenCalledWith('timeout');
		});
	});

	/** Plays rounds 1 up to its end and round 2 up to the player's first turn. */
	const toRound2 = async () => {
		await toFirstTurn();
		await type('물레방아');
		await go(GAP + robotDelay('아저씨') + GAP);
		await type('씨앗');
		await go(GAP + 1100 + 3000);
		expect(g().round).toBe(2);
		await next(); // the intro
		await go(2400 + robotDelay('영어') + GAP);
	};

	describe('round 2: 매너', () => {
		it('deals round 2 three seconds after the last turn, without a mission', async () => {
			await toFirstTurn();
			await type('물레방아');
			await go(GAP + robotDelay('아저씨') + GAP);
			await type('씨앗');
			await go(GAP + 1100);
			expect(g().round).toBe(1);
			await go(2900); // (the next-turn delay is 2266.67 ms: assert with a little room)
			expect(g().round).toBe(1);
			await go(200);
			expect(g().round).toBe(2);
			expect(g().char).toBe('영');
			expect(g().mission).toBe('');
			expect(game.store.currentRoom.opts.mission).toBeUndefined();
			expect(g().chain).toBe(0); // a round starts its chain again
		});
		it('the guide plays the 한방 word 이리듐 and says what happened, as the red letters the board marks', async () => {
			await toRound2();
			expect(game.store.myTurn).toBe(true);
			expect(g().char).toBe('어');
			await type('어린이');
			await go(GAP + robotDelay('이리듐'));
			expect(g().words.at(-1).word).toBe('이리듐');
			expect(g().last.hanbang).toEqual([2, 3]);
			expect(tips.at(-1)).toEqual(['play.story.chain.r2.hanbang', { word: '이리듐' }]);
		});
		it('the player cannot go on: the whole clock runs out, whatever they type, and costs points', async () => {
			await toRound2();
			await type('어린이');
			await go(GAP + robotDelay('이리듐') + GAP);
			expect(game.store.myTurn).toBe(true);
			expect(g().char).toBe('듐');
			expect(focuses.at(-1)).toEqual([null, undefined]); // nothing to point at
			expect(game.store.ui.pause.on).toBe(false); // the clock runs: it is meant to run out
			await type('아무말');
			expect(tips.at(-1)).toEqual(['play.story.chain.stuck', { char: '듐' }]);
			expect(g().words.at(-1).word).toBe('이리듐'); // nothing was added
			const before = game.store.net.users.me.game.score;
			await go(T + 100);
			expect(g().last).toMatchObject({ ok: false, id: 'me' });
			expect(game.store.net.users.me.game.score).toBe(before + g().last.score);
			expect(g().last.score).toBeLessThan(0);
			expect(g().last.hint).toBe(''); // a 한방: there is no word to hint at
		});
		it('then she explains 매너 in three lines, turns it on, and round 3 comes with it', async () => {
			await toRound2();
			await type('어린이');
			await go(GAP + robotDelay('이리듐') + GAP + T + 100);
			expect(said.at(-1)[0]).toBe('play.story.chain.r2.lose');
			expect(game.store.currentRoom.opts.manner).toBeUndefined(); // not yet
			await next();
			expect(said.at(-1)[0]).toBe('play.story.chain.r2.on');
			await go(10000);
			expect(g().round).toBe(2); // the next round waits for her to be understood
			await next();
			expect(game.store.currentRoom.opts.manner).toBe(true); // she turned it on
			expect(game.store.currentRoom.tags).toContain('매너');
			await go(3000);
			expect(g().round).toBe(3);
			expect(g().char).toBe('레');
		});
	});

	/** Plays up to the player's turn in round 3. */
	const toRound3 = async () => {
		await toRound2();
		await type('어린이');
		await go(GAP + robotDelay('이리듐') + GAP + T + 100);
		await next();
		await next();
		await go(3000);
		await next(); // the intro
		await go(2400 + robotDelay('레몬') + GAP);
	};

	describe('round 3: 어인정', () => {
		it('the word is refused as a 어인정 word is while 어인정 is off: a plain 404 with the word', async () => {
			await toRound3();
			expect(g().char).toBe('몬');
			expect(focuses.at(-1)).toEqual([INPUT, t('play.tutorial.chain.type', { word: '몬스터' })]);
			expect(game.store.currentRoom.opts.injeong).toBeUndefined();
			await type('몬스터');
			expect(g().fail).toMatchObject({ code: 404, word: '몬스터', text: '몬스터' });
			expect(g().words.map((w) => w.word)).toEqual(['레몬']); // not played
			expect(playSound).toHaveBeenCalledWith('fail');
			expect(focuses.at(-1)).toEqual([null, undefined]);
			expect(game.store.ui.pause.on).toBe(true); // it stays paused while she explains
			const now = game.store.now();
			await go(10000);
			expect(game.store.now()).toBe(now);
		});
		it('she explains in three lines, turns 어인정 on, and the same word is accepted', async () => {
			await toRound3();
			await type('몬스터');
			await go(0);
			expect(said.at(-1)).toEqual(['play.story.chain.r3.refused', { word: '몬스터' }]);
			expect(game.store.myTurn).toBe(false); // the answer field is away while she explains, so Enter presses 다음 and nothing can be typed
			await next();
			expect(said.at(-1)[0]).toBe('play.story.chain.r3.why');
			await next();
			expect(said.at(-1)[0]).toBe('play.story.chain.r3.on');
			expect(game.store.currentRoom.opts.injeong).toBeUndefined();
			await next();
			expect(game.store.currentRoom.opts).toMatchObject({ manner: true, injeong: true });
			expect(game.store.currentRoom.tags.length).toBe(2);
			expect(game.store.myTurn).toBe(true);
			expect(g().fail).toBe(null); // a new turn
			expect(focuses.at(-1)).toEqual([INPUT, t('play.tutorial.chain.type', { word: '몬스터' })]);
			await type('몬스터');
			expect(g().words.map((w) => w.word)).toEqual(['레몬', '몬스터']);
			expect(tips.at(-1)).toEqual(['play.story.chain.r3.ok', { word: '몬스터' }]);
		});
		it('the last round ends as the others do, then the result, where the player wins', async () => {
			await toRound3();
			await type('몬스터');
			await go(0);
			await next();
			await next();
			await next();
			await type('몬스터');
			await go(GAP);
			expect(tips.at(-1)[0]).toBe('play.story.chain.r3.out');
			await go(1100);
			expect(g().last).toMatchObject({ ok: false, id: REMI, hint: '터널' });
			expect(g().phase).not.toBe('ended');
			await go(2900);
			expect(g().phase).not.toBe('ended');
			await go(200);
			expect(g().phase).toBe('ended');
			const rows = g().result.rows;
			expect(rows.find((r) => r.me).rank).toBe(0);
			expect(game.store.resultSay[REMI]).toBe('play.story.chain.win');
			expect(ended).toBe(0);
			game.store.closeResult();
			expect(ended).toBe(1);
		});
	});

	it('stops when it is disposed', async () => {
		game.dispose();
		await go(30000);
		expect(game.store.game.phase).toBe('loading');
	});
});
