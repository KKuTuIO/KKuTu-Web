import { describe, expect, it } from 'vitest';
import { applyGamePacket, charText, decryptTurnEnd, secretFromInit, seatIds } from './gameModel.js';
import { applyPacket, initialState, normalizeUser } from './protocol.js';

const user = (id, score = 0) => normalizeUser({ id, profile: { title: id }, data: { score, record: {} }, game: { score: 0, team: 0 } });
function world() {
	const s = initialState();
	s.id = 'me';
	s.myRoom = 1;
	s.rooms[1] = { no: 1, rounds: 3, game: { seq: ['me', 'bot'], turn: 0 }, practice: false, playing: true };
	s.users = { me: user('me', 100), bot: user('bot', 50) };
	return s;
}
const obfuscate = (word, secret, sumKey) => {
	const xor = [...word.slice(1)].map((_, i) => 7 + i);
	const value = word[0] + [...word.slice(1)].map((c, i) => String.fromCharCode(c.charCodeAt(0) ^ xor[i])).join('');
	const key = String.fromCharCode(sumKey ^ secret);
	return { value, sum: sumKey, [key]: btoa(JSON.stringify(xor)) };
};

describe('initSy and turnEnd obfuscation', () => {
	it('reads the secret key like the legacy client', () => {
		expect(secretFromInit([0, 0, (1120 + 55) * 3])).toBe(55);
		expect(secretFromInit([0, 0, 1])).toBe(0); // negative: refused
		expect(secretFromInit([0, 0, 3 * 1120 + 1])).toBe(0); // not a whole number: refused
		expect(secretFromInit(null)).toBe(0);
	});
	it('restores the word', () => {
		const pkt = { type: 'turnEnd', ok: true, ...obfuscate('사과나무', 55, 300) };
		const out = decryptTurnEnd(pkt, 55);
		expect(out.value).toBe('사과나무');
		expect(out.sum).toBeUndefined();
		expect(Object.keys(out).filter((k) => k.length === 1)).toEqual([]);
	});
	it('leaves a plain or unreadable packet alone', () => {
		const plain = { ok: true, value: '사과' };
		expect(decryptTurnEnd(plain, 55)).toBe(plain);
		const locked = { ok: true, ...obfuscate('사과나무', 55, 300) };
		expect(decryptTurnEnd(locked, 0)).toBe(locked); // no key yet
		expect(decryptTurnEnd(locked, 56).value).toBe(locked.value); // wrong key: nothing found under that name
	});
});

describe('helpers', () => {
	it('shows a second allowed letter in brackets', () => {
		expect(charText('가', '')).toBe('가');
		expect(charText('라', '나')).toBe('라(나)');
	});
	it('reads the seat order as ids', () => {
		expect(seatIds(['a', { id: 'b', robot: true }])).toEqual(['a', 'b']);
		expect(seatIds(undefined)).toEqual([]);
	});
});

describe('a game', () => {
	it('starts loading, then shows the first letter', () => {
		const s = world();
		applyGamePacket(s, { type: 'starting' });
		expect(s.game.phase).toBe('loading');
		applyGamePacket(s, { type: 'roundReady', round: 0, char: '가', subChar: '' });
		expect(s.game).toMatchObject({ phase: 'ready', char: '가', round: 0, rounds: 3, seq: ['me', 'bot'] });
	});
	it('follows a turn: whose it is, the clock, the ids the answer must carry', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 0, char: '가' });
		applyGamePacket(s, { type: 'turnStart', turn: 1, char: '가', subChar: '나', speed: 2, turnTime: 12000, roundTime: 60000, wordLength: 3, gameId: 'g', turnId: 't', inputEpoch: 4 }, 1000);
		expect(s.game).toMatchObject({ phase: 'turn', current: 'bot', turnAt: 1000, turnTime: 12000, wordLength: 3, gameId: 'g', turnId: 't', inputEpoch: 4, subChar: '나' });
	});
	it('scores a good word and keeps the chain', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 0, char: '가' });
		applyGamePacket(s, { type: 'turnStart', turn: 0, char: '가' });
		applyGamePacket(s, { type: 'turnEnd', ok: true, value: '가위', mean: 'scissors', score: 25, bonus: 5 });
		expect(s.game.chain).toBe(1);
		expect(s.game.words).toEqual([{ id: 'me', word: '가위', mean: 'scissors', theme: '', score: 25, bonus: 5 }]);
		expect(s.users.me.game.score).toBe(25);
		expect(s.game.deltas.me.text).toBe('+20');
		expect(s.game.deltas['me:bonus'].text).toBe('+5');
		expect(s.game.phase).toBe('between');
	});
	it('reads an obfuscated word once the key is known', () => {
		const s = world();
		applyGamePacket(s, { type: 'initSy', val: [0, 0, (1120 + 55) * 3] });
		applyGamePacket(s, { type: 'roundReady', round: 0, char: '사' });
		applyGamePacket(s, { type: 'turnStart', turn: 0, char: '사' });
		applyGamePacket(s, { type: 'turnEnd', ok: true, score: 10, bonus: 0, ...obfuscate('사과나무', 55, 300) });
		expect(s.game.words[0].word).toBe('사과나무');
	});
	it('charges a timed-out turn to whoever it was, and names the answer that was left', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 0, char: '가' });
		applyGamePacket(s, { type: 'turnStart', turn: 1, char: '가' });
		applyGamePacket(s, { type: 'turnEnd', ok: false, target: 'bot', score: -15, hint: { _id: '가방' } });
		expect(s.users.bot.game.score).toBe(-15);
		expect(s.game.chain).toBe(0);
		expect(s.game.last).toMatchObject({ ok: false, id: 'bot', hint: '가방' });
		expect(s.game.deltas.bot).toMatchObject({ kind: 'lost', text: '-15' });
	});
	it('says why a word was refused', () => {
		const s = world();
		s.meta.labels['kkutu.js.turn.error.409'] = '이미 쓰인 단어';
		applyGamePacket(s, { type: 'turnError', code: 409, value: '가위' }, 5);
		expect(s.game.fail).toEqual({ code: 409, text: '이미 쓰인 단어: 가위', at: 5 });
		applyGamePacket(s, { type: 'turnError', code: 404, value: '없는 말' }, 6);
		expect(s.game.fail.text).toBe('없는 말');
	});
	it('adds a stack-kill bonus to the player', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 0, char: '가' });
		applyGamePacket(s, { type: 'attackBonus', id: 'me', value: 30 });
		expect(s.users.me.game.score).toBe(30);
		expect(s.game.deltas.me).toMatchObject({ kind: 'bonus', text: '+30' });
	});
	it('joining halfway: the turn we are told about is whose it is', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 1, char: '나', turn: 1, enter: true });
		expect(s.game.current).toBe('bot');
		expect(s.game.round).toBe(1);
	});
	it('makes the ranking with before/after levels from the new totals', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 0, char: '가' });
		applyGamePacket(s, {
			type: 'roundEnd',
			users: { me: { id: 'me', data: { score: 400, record: { KKT: [3, 1, 0, 0, 2] } }, money: 1234 } },
			result: [
				{ id: 'me', rank: 0, score: 90, reward: { score: 300, money: 40, ep: 2, _score: 250, _money: 30, _blog: ['x'] } },
				{ id: 'bot', rank: 1, score: 10, reward: { score: 0, money: 0 } },
				{ id: 'ghost', rank: 2, score: 0, reward: { score: 1 } } // not known here: skipped
			]
		});
		const rows = s.game.result.rows;
		expect(rows.map((r) => r.id)).toEqual(['me', 'bot']);
		expect(rows[0]).toMatchObject({ rank: 0, score: 90, before: 100, after: 400, me: true, reward: { exp: 300, money: 40, ep: 2, baseExp: 250, baseMoney: 30 } });
		expect(rows[0].levelAfter).toBeGreaterThan(rows[0].levelBefore);
		expect(rows[1].me).toBe(false);
		expect(s.users.me.money).toBe(1234);
		expect(s.game.phase).toBe('ended');
	});
});

describe('the game and the room', () => {
	const roomPkt = (gaming, players = ['me', 'bot']) => ({ type: 'room', room: { id: 1, mode: 0, gaming, players: players.map((id) => ({ id, profile: { title: id }, data: { score: 0 }, game: {} })), master: 'me', limit: 8 } });
	it('forgets a game that stopped without a result', () => {
		const s = world();
		applyGamePacket(s, { type: 'starting' });
		applyPacket(s, roomPkt(false));
		expect(s.game).toBeNull();
	});
	it('keeps a finished game until the result is closed', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 0, char: '가' });
		applyGamePacket(s, { type: 'roundEnd', result: [] });
		applyPacket(s, roomPkt(false));
		expect(s.game.result).toBeTruthy();
	});
	it('keeps the game when a room I am not in changes', () => {
		const s = world();
		applyGamePacket(s, { type: 'starting' });
		applyPacket(s, { type: 'room', room: { id: 9, mode: 0, gaming: false, players: [{ id: 'x', profile: { title: 'x' }, data: { score: 0 }, game: {} }], master: 'x', limit: 8 } });
		expect(s.game).toBeTruthy();
	});
	it('forgets the game when I am no longer in the room', () => {
		const s = world();
		applyGamePacket(s, { type: 'starting' });
		applyPacket(s, roomPkt(true, ['bot']));
		expect(s.game).toBeNull();
	});
});

describe('failing two turns in a row (legacy checkFailCombo)', () => {
	const fail = (s) => applyGamePacket(s, { type: 'turnEnd', ok: false, target: 'me', score: -10 });
	it('warns the first time and sends me out the second time, in a game with fewer than 3 players', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 1, char: '가' });
		applyGamePacket(s, { type: 'turnStart', turn: 0, char: '가' });
		fail(s);
		expect(s.game.combo).toBeNull(); // the first failure only starts counting
		applyGamePacket(s, { type: 'turnStart', turn: 0, char: '가' });
		fail(s);
		expect(s.game.combo?.action).toBe('warn');
		applyGamePacket(s, { type: 'turnStart', turn: 0, char: '가' });
		fail(s);
		expect(s.game.combo?.action).toBe('leave');
	});
	it('a word in between resets it, and the server judges bigger games', () => {
		const s = world();
		applyGamePacket(s, { type: 'roundReady', round: 1, char: '가' });
		fail(s);
		applyGamePacket(s, { type: 'turnEnd', ok: true, value: '가위', score: 5, bonus: 0 });
		fail(s);
		expect(s.game.combo).toBeNull();
		s.meta.rules = { X: { rule: 'Classic' } };
		s.rooms[1].modeKey = 'X';
		s.game.seq = ['me', 'bot', 'c'];
		fail(s);
		fail(s);
		expect(s.game.combo).toBeNull();
	});
});

describe('the quiz modes', () => {
	const quiz = (rule) => {
		const s = world();
		s.meta.rules = { Q: { rule } };
		s.rooms[1].modeKey = 'Q';
		return s;
	};
	it('자음퀴즈: theme, hints, a correct answer that caps the clock, and the answer when nobody got it', () => {
		const s = quiz('Jaqwi');
		applyGamePacket(s, { type: 'roundReady', round: 1, theme: '320' });
		applyGamePacket(s, { type: 'turnStart', turn: 0, char: 'ㄱㅅ', roundTime: 60000 }, 1000);
		expect(s.game).toMatchObject({ theme: '320', char: 'ㄱㅅ', phase: 'turn', current: null });
		applyGamePacket(s, { type: 'turnHint', hint: '첫 번째 뜻' });
		expect(s.game.hints).toEqual(['첫 번째 뜻']);
		applyGamePacket(s, { type: 'turnEnd', target: 'bot', score: 12 }, 6000);
		expect(s.users.bot.game.score).toBe(12);
		expect(s.game.winner).toBe('bot');
		expect(s.game.roundTime).toBe(5000 + 10000); // only ten seconds are left once somebody has it
		applyGamePacket(s, { type: 'turnEnd', giveup: true, target: 'me' });
		expect(s.game.gaveUp.me).toBe(true);
		applyGamePacket(s, { type: 'turnEnd', answer: '감사' });
		expect(s.game).toMatchObject({ answer: '감사', phase: 'between' });
	});
	it('타자 대결: the list, my words count, a miss costs points, the end tells the speed', () => {
		const s = quiz('Typing');
		applyGamePacket(s, { type: 'roundReady', round: 1, list: ['가', '나'] });
		expect(s.game.list).toEqual(['가', '나', '가', '나']);
		applyGamePacket(s, { type: 'turnStart', turn: 0, roundTime: 60000 });
		applyGamePacket(s, { type: 'turnEnd', ok: true, target: 'me', value: '가', score: 5 });
		expect(s.game.chain).toBe(1);
		applyGamePacket(s, { type: 'turnEnd', ok: true, target: 'bot', value: '가', score: 5 });
		expect(s.game.chain).toBe(1); // somebody else's word is not mine
		applyGamePacket(s, { type: 'turnEnd', error: true, target: 'me', score: -3 });
		expect(s.game.chain).toBe(2);
		expect(s.users.me.game.score).toBe(2);
		applyGamePacket(s, { type: 'turnEnd', speed: { me: 310, bot: 280 } });
		expect(s.game.kpm).toEqual({ me: 310, bot: 280 });
	});
	it('수학 대결: my answers are listed with their problem', () => {
		const s = quiz('Mathquiz');
		applyGamePacket(s, { type: 'roundReady', round: 1, list: ['1+1'] });
		applyGamePacket(s, { type: 'turnStart', turn: 0, roundTime: 60000 });
		applyGamePacket(s, { type: 'turnEnd', ok: true, target: 'me', value: '2', problem: '1+1', score: 4 });
		applyGamePacket(s, { type: 'turnEnd', error: true, target: 'me', correctAnswer: '3', problem: '1+2', score: -2 });
		expect(s.game.history.map((h) => [h.word, h.mean, h.mark])).toEqual([['3', '1+2', 'bad'], ['2', '1+1', 'ok']]);
		// each entry keeps its key when newer ones are added, so only the new one animates in
		const first = s.game.history[1].key;
		applyGamePacket(s, { type: 'turnEnd', ok: true, target: 'me', value: '5', problem: '2+3', score: 4 });
		expect(s.game.history.find((h) => h.word === '2').key).toBe(first);
		expect(new Set(s.game.history.map((h) => h.key)).size).toBe(3);
	});
});

describe('솎솎, 잇땅 and 워드스택', () => {
	const mode = (rule) => {
		const s = world();
		s.meta.rules = { Q: { rule } };
		s.rooms[1].modeKey = 'Q';
		return s;
	};
	it('솎솎: a found word takes its letters off the board and scores', () => {
		const s = mode('Sock');
		applyGamePacket(s, { type: 'roundReady', round: 1, board: '가나다라마바사아' });
		applyGamePacket(s, { type: 'turnStart', turn: 0, roundTime: 60000 });
		applyGamePacket(s, { type: 'turnEnd', value: '나다', score: 20, target: 'me' });
		expect(s.game.board).toBe('가　　라마바사아');
		expect(s.game.maps).toEqual(['나다']);
		expect(s.game.picked.map((x) => [x.index, x.char])).toEqual([[1, '나'], [2, '다']]);
		expect(s.users.me.game.score).toBe(20);
		applyGamePacket(s, { type: 'turnEnd', score: 0 }); // the round is over
		expect(s.game.phase).toBe('between');
	});
	it('잇땅: the board and owners follow the packets, words are listed newest first, a settlement is a bonus', () => {
		const s = mode('Linkland');
		applyGamePacket(s, { type: 'roundReady', round: 1, boardSize: 3, board: 'abcdefghi', owners: [] });
		applyGamePacket(s, { type: 'turnEnd', value: '가나', score: 10, target: 'me', board: 'ABcdefghi', owners: ['me', 'me'], multiplier: 2 });
		applyGamePacket(s, { type: 'turnEnd', value: '다라', score: 5, target: 'bot', replacement: true });
		expect(s.game.board).toBe('ABcdefghi');
		expect(s.game.words).toEqual(['다라 ↻', '가나 ×2']);
		applyGamePacket(s, { type: 'turnEnd', settlement: true, score: 7, target: 'me' });
		expect(s.users.me.game.score).toBe(17);
		expect(s.game.deltas.me.kind).toBe('bonus');
	});
	it('워드스택: my pieces, the history of attacks, pieces that fly, and a round end', () => {
		const s = mode('Wordstack');
		applyGamePacket(s, { type: 'roundReady', round: 1, pool: { me: ['가', '나'], bot: ['다'] }, subpool: { me: ['', ''] }, pieceAges: { me: [0, 500] } });
		expect(s.game.pool).toEqual(['가', '나']);
		applyGamePacket(s, { type: 'turnStart', turn: 0, roundTime: 60000 });
		applyGamePacket(s, { type: 'turnEnd', ok: true, target: 'me', attack: 'bot', value: '가나', pool: ['다'], score: 9, piece: '나', mean: 'm' });
		expect(s.game.pool).toEqual(['다']);
		expect(s.game.history[0].word).toBe('▶② 가나');
		expect(s.game.throws.map((x) => [x.from, x.to, x.piece])).toEqual([['me', 'bot', '나']]);
		applyGamePacket(s, { type: 'turnEnd', ok: true, target: 'bot', attack: 'me', value: '다라', otherpool: ['다', '마'], score: 4 });
		expect(s.game.pool).toEqual(['다', '마']);
		expect(s.game.history[0].word).toBe('①▶ 다라');
		applyGamePacket(s, { type: 'turnEnd', isBonus: true, ok: false, target: 'me', score: -3, pieceEvent: 'gain', piece: '라' });
		expect(s.users.me.game.score).toBe(6);
		expect(s.game.deltas.me.kind).toBe('lost');
		applyGamePacket(s, { type: 'turnEnd' }); // nothing ok, no bonus: the round is over
		expect(s.game.phase).toBe('between');
	});
});

describe('십자말풀이', () => {
	const cwWorld = () => {
		const s = world();
		s.meta.rules = { Q: { rule: 'Crossword' } };
		s.rooms[1].modeKey = 'Q';
		s.rooms[1].rounds = 3;
		return s;
	};
	it('starts on the board of my place in the order, and keeps the bars it is given', () => {
		const s = cwWorld();
		applyGamePacket(s, { type: 'roundReady', round: 1, seq: ['bot', 'me'] });
		expect(s.game.selectedRound).toBe(2);
		applyGamePacket(s, { type: 'turnStart', turn: 0, roundTime: 60000, boards: [[[0, 0, 0, 3, '', null]], [[1, 1, 1, 2, '', null]]], means: [{}, { '1,1,1': { dir: '1', len: 2, mean: 'm' } }] });
		expect(s.game.boards[1][0]).toEqual([1, 1, 1, 2, '', null]);
	});
	it('a solved word fills its bar and belongs to the solver; one in another round makes its chip flash', () => {
		const s = cwWorld();
		applyGamePacket(s, { type: 'roundReady', round: 1, seq: ['me', 'bot'] });
		applyGamePacket(s, { type: 'turnStart', turn: 0, roundTime: 60000, boards: [[[0, 0, 0, 2, '', null]], [[0, 0, 1, 2, '', null]]], means: [{}, {}] });
		s.game.sel = [0, 0, 0, 0];
		applyGamePacket(s, { type: 'turnEnd', target: 'me', pos: [0, 0, 0, 0], value: '가나', score: 8 });
		expect(s.game.boards[0][0].slice(4)).toEqual(['가나', 'me']);
		expect(s.game.sel).toBeNull(); // that question is answered
		expect(s.users.me.game.score).toBe(8);
		applyGamePacket(s, { type: 'turnEnd', target: 'bot', pos: [1, 0, 0, 1], value: '다라', score: 5 });
		expect(s.game.boards[1][0].slice(4)).toEqual(['다라', 'bot']);
		expect(s.game.cwFlash.round).toBe(1);
		applyGamePacket(s, { type: 'turnEnd' }); // the round is over
		expect(s.game).toMatchObject({ phase: 'between', roundsDone: true });
	});
});

describe('그림퀴즈', () => {
	const dgWorld = () => {
		const s = world();
		s.meta.rules = { D: { rule: 'Drawing' } };
		s.meta.labels = { 'word.theme.30': '음식' };
		s.rooms[1].modeKey = 'D';
		return s;
	};
	it('knows the painter and only shows the picture to the others', () => {
		const s = dgWorld();
		applyGamePacket(s, { type: 'roundReady', round: 1, painter: 'bot', length: 3, theme: '30' });
		expect(s.game.painter).toBe('bot');
		expect(s.game.answerLength).toBe(3);
		applyGamePacket(s, { type: 'drawCanvas', image: true, data: 'data:image/jpeg;base64,AA' });
		expect(s.game.canvasImage).toBe('data:image/jpeg;base64,AA');
		s.game.painter = 'me'; // my own picture is not replaced by the echo
		applyGamePacket(s, { type: 'drawCanvas', image: true, data: 'other' });
		expect(s.game.canvasImage).toBe('data:image/jpeg;base64,AA');
	});
	it('tells only the painter the word; hints name the theme; a guess ends it with a score', () => {
		const s = dgWorld();
		applyGamePacket(s, { type: 'roundReady', round: 1, painter: 'me', length: 2, theme: '30' });
		applyGamePacket(s, { type: 'drawingContent', word: '사과', theme: '30' });
		expect(s.game.painterWord).toBe('사과');
		applyGamePacket(s, { type: 'turnHint', hint: ['30'] });
		expect(s.game.hints).toEqual(['음식']);
		applyGamePacket(s, { type: 'turnEnd', target: 'bot', ok: true, value: '사과', score: 7 });
		expect(s.game.winner).toBe('bot');
	});
});
