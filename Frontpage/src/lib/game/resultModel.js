/**
 * What the result screen shows, worked out from the result rows (see `resultRows` in gameModel.js), the players and the words played.
 * Pure, so the screen only draws it.
 *
 *  layout 'teams': a game with teams, the team bar and my score as the hero
 *  layout 'duel':  two players and no teams, two scores facing each other
 *  layout 'list':  three or more players and no teams
 */
/** The colours of the teams: the client's own tokens (`--kk-team-N` in game.css), so they stay what the room and the board show. */
export const TEAM_COLORS = { 1: 'var(--kk-team-1)', 2: 'var(--kk-team-2)', 3: 'var(--kk-team-3)', 4: 'var(--kk-team-4)' };
export const TEAM_LETTERS = ['A', 'B', 'C', 'D'];
export const BRAND_COLOR = 'var(--kk-accent)';
const OPPONENT_COLOR = 'var(--kk-team-0)';
const MIN_SHARE = 0.14; // the smallest width a team's part of the bar gets, so its letter and score still fit

const letterOf = (team) => TEAM_LETTERS[team - 1] ?? '';

/** Place numbers with ties sharing one: scores 30, 30, 20 give 1, 1, 3. */
function ranked(items, scoreOf) {
	let rank = 0;
	return items.map((item, i) => {
		if (i === 0 || scoreOf(item) !== scoreOf(items[i - 1])) rank = i + 1;
		return { ...item, rank };
	});
}

/**
 * My words: how many, my "best move" and the top few by points. The best move is the dead-end word (the next player had nothing to
 * go on with) that scored most; when there was none it is plainly the word that scored most, and `kind` says which of the two it is.
 */
function wordsOf(played, id, top = 5) {
	const mine = played.filter((w) => w.id === id);
	const most = (list) => list.reduce((b, w) => (!b || (w.score ?? 0) > (b.score ?? 0) ? w : b), null);
	const dead = mine.filter((w) => w.hanbang);
	const best = dead.length ? most(dead) : most(mine);
	const sorted = mine.map((w, i) => ({ w, i })).sort((a, b) => (b.w.score ?? 0) - (a.w.score ?? 0) || a.i - b.i).map(({ w }) => w);
	return { count: mine.length, best, kind: dead.length ? 'deadend' : 'top', top: sorted.slice(0, top), more: Math.max(0, mine.length - top) };
}

/**
 * @param {{rows:object[], users:object, played?:object[], meId:string, quiz?:{solved?:object, kpm?:object}}} o
 *   rows: [{id, rank, score, team, me, levelBefore, levelAfter, reward}] as the game model makes them
 *   users: the players by id (their `game.score` is what each of them scored; in a team game a row's own score is the team's)
 *   played: [{id, word, score, bonus, round}] every accepted word of the game
 */
export function buildResultView({ rows = [], users = {}, played = [], meId = '', quiz = {} }) {
	const teamGame = rows.some((r) => r.team > 0);
	const own = (r) => (teamGame ? Math.round(Number(users[r.id]?.game?.score) || 0) : Math.round(Number(r.score) || 0));
	const people = rows.map((r) => ({ id: r.id, user: users[r.id] ?? null, team: r.team || 0, letter: letterOf(r.team), color: r.team ? TEAM_COLORS[r.team] : OPPONENT_COLOR, score: own(r), me: !!r.me || r.id === meId, row: r }));
	const players = ranked(people.slice().sort((a, b) => b.score - a.score), (p) => p.score);
	const me = players.find((p) => p.me) ?? null;

	let teams = [];
	if (teamGame) {
		const sums = new Map();
		for (const p of players) if (p.team) sums.set(p.team, (sums.get(p.team) ?? 0) + p.score);
		const list = [...sums].map(([team, score]) => ({ team, letter: letterOf(team), color: TEAM_COLORS[team], score })).sort((a, b) => b.score - a.score || a.team - b.team);
		const total = list.reduce((n, t) => n + t.score, 0);
		const raw = list.map((t) => Math.max(total ? t.score / total : 1 / list.length, MIN_SHARE));
		const sum = raw.reduce((n, v) => n + v, 0);
		teams = ranked(list, (t) => t.score).map((t, i) => ({ ...t, share: raw[i] / sum, mine: !!me && t.team === me.team }));
	}
	const myTeam = teams.find((t) => t.mine) ?? null;

	const layout = teamGame ? 'teams' : players.length === 2 ? 'duel' : 'list';
	const opponent = layout === 'duel' ? (players.find((p) => !p.me) ?? null) : null;
	const outcome = layout === 'teams' ? (myTeam ? (myTeam.rank === 1 ? 'win' : 'lose') : null) : layout === 'duel' && me ? (me.rank === 1 ? 'win' : 'lose') : null;

	const gap = opponent && me ? { mine: me.score, theirs: opponent.score, diff: Math.abs(me.score - opponent.score), share: me.score + opponent.score ? me.score / (me.score + opponent.score) : 0.5 } : null;
	const words = me ? wordsOf(played, me.id) : { count: 0, best: null, kind: 'top', top: [], more: 0 };
	const stat = me && !words.count ? (quiz.solved?.[me.id] !== undefined ? { kind: 'solved', value: quiz.solved[me.id] } : quiz.kpm?.[me.id] !== undefined ? { kind: 'speed', value: quiz.kpm[me.id] } : null) : null;

	return {
		layout,
		teams,
		myTeam,
		players,
		me,
		mine: me?.row ?? null,
		opponent,
		outcome,
		gap,
		words,
		opponentBest: opponent ? (({ best, kind }) => (best ? { ...best, kind } : null))(wordsOf(played, opponent.id)) : null,
		stat,
		hero: { score: me?.score ?? 0, color: myTeam ? myTeam.color : BRAND_COLOR }
	};
}
