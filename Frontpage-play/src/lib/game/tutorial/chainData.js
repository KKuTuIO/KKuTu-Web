/**
 * The practice 끝말잇기 of 학사모레미's tutorial: a fixed game of three rounds, so the player is told exactly what to type.
 *
 *  1. 미션: the mission letter 방 is shown; 그물 (guide) → 물레방아 (player, has 방: bonus) → 아저씨 (guide) → 씨앗 (player); the guide's clock runs out.
 *  2. 매너: 영어 (guide) → 어린이 (player) → 이리듐 (guide: a 한방 word, nothing starts with 듐) → the player cannot go on and the clock runs
 *     out; the guide turns 매너 on.
 *  3. 어인정: 레몬 (guide) → 몬스터 (player: refused, it is a 어인정 word) → the guide turns 어인정 on → 몬스터 is accepted; the guide's clock runs out.
 */
export const ROUNDS = [
	{
		char: '그',
		opts: { mission: true },
		mission: '방', // the mission letter of the round
		nextMission: '기', // after a mission word the server gives a new one
		moves: [
			{ by: 'remi', word: '그물' },
			{ by: 'me', word: '물레방아', mission: true },
			{ by: 'remi', word: '아저씨' },
			{ by: 'me', word: '씨앗' }
		],
		ends: { by: 'remi', hint: '아침' } // the round is over when this one's clock runs out
	},
	{
		char: '영',
		opts: {},
		moves: [
			{ by: 'remi', word: '영어' },
			{ by: 'me', word: '어린이' },
			{ by: 'remi', word: '이리듐', hanbang: [2, 3] } // [start, end) of the letters nobody can go on from
		],
		ends: { by: 'me', stuck: true },
		then: { opts: { manner: true } } // the room settings the guide turns on after it
	},
	{
		char: '레',
		opts: { manner: true },
		moves: [
			{ by: 'remi', word: '레몬' },
			{ by: 'me', word: '몬스터', injeong: true, refusedFirst: true, then: { opts: { manner: true, injeong: true } } }
		],
		ends: { by: 'remi', hint: '터널' }
	}
];

/** Every word the player types, in order. */
export const PLAYER_WORDS = ROUNDS.flatMap((r) => r.moves.filter((m) => m.by === 'me').map((m) => m.word));

/** The locale key (`play.tutorial.chain.mean.<name>`) of the meaning of each word of the game. */
export const MEANING = {
	그물: 'net',
	물레방아: 'watermill',
	아저씨: 'mister',
	씨앗: 'seed',
	영어: 'english',
	어린이: 'child',
	이리듐: 'iridium',
	레몬: 'lemon',
	몬스터: 'monster'
};

/** Points of a turn (the practice game shows them like a real one, but nothing is kept). `bonus` is part of a mission word's score. */
export const SCORE = { me: 24, remi: 15, missionBonus: 12 };

export const lastChar = (word) => [...word].at(-1) ?? '';

/**
 * What the server does, as the Classic mode of the game server does it (games/classic.ts, games/Game.ts, kkutu/room.ts):
 * a round of `ROUND_SEC` seconds, the first turn 2.4 s after `roundReady`, `turnTime = 15000 - 1400 * speed` where the speed comes from the
 * time left in the round, the next turn `turnTime / 6` after a word, a robot of level L types for START[L] + letters * COEF[L] ms,
 * a turn that runs out ends the round and the next round is dealt 3 s after it.
 */
export const ROUND_SEC = 90;
export const FIRST_TURN_MS = 2400;
export const NEXT_ROUND_MS = 3000;
export function turnSpeed(roundTime) {
	const edges = [5000, 11000, 18000, 26000, 35000, 45000, 56000, 68000, 81000, 95000]; // speed 10 .. 1; slower than the last: 0
	const i = edges.findIndex((e) => roundTime < e);
	return i === -1 ? 0 : 10 - i;
}
export const turnTimeOf = (speed) => 15000 - 1400 * speed;
export const nextTurnDelay = (turnTime) => turnTime / 6;
export const ROBOT_LEVEL = 1; // slow enough to follow
const ROBOT_START_DELAY = [1200, 800, 500, 300, 150, 10];
const ROBOT_TYPE_COEF = [1000, 600, 300, 200, 100, 5];
export const robotDelay = (word) => ROBOT_START_DELAY[ROBOT_LEVEL] + [...word].length * ROBOT_TYPE_COEF[ROBOT_LEVEL];
/** The time left in the round when its clock runs out: the turn is as short as the server makes the last one (speed 10: one second). */
export const LAST_TURN_ROUND_TIME = 4000;
/** What a turn that runs out costs (games/Game.ts `getPenalty`): a negative number, at most what the player had. */
export const penalty = (chainLength, score) => (score <= 0 ? 0 : -Math.round(Math.min(10 + chainLength * 2.1 + score * 0.15, score)));

/**
 * Is `word` the answer to the turn that needs `expected` (which begins with `char`)? A word that doesn't begin with the letter breaks the
 * rule of the game; any other word is not the one the practice is about.
 * @returns {{ok:true}|{ok:false, reason:'start'|'other'}}
 */
export function judge({ char, word: expected }, word) {
	if (word === expected) return { ok: true };
	return { ok: false, reason: word.startsWith(char) ? 'other' : 'start' };
}
