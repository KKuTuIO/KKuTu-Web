/**
 * Chat commands typed as "/…", the way the legacy client runs them. Every command has Korean-keyboard and English
 * aliases ("/ㄱ" and "/r" are the same key presses on the two layouts).
 *
 * "#…" lines are not handled here: they are admin commands the game server runs itself, so the client sends them
 * as ordinary chat and only hides their echo (see `isAdminCommand`).
 */
export const ROUND_TIMES = [10, 30, 60, 90, 120, 150];

const ALIASES = {
	ready: ['/ㄱ', '/r'],
	clear: ['/청소', '/ㅊㅅ', '/ㅊ', '/cls'],
	log: ['/ㄹ', '/f'],
	whisper: ['/귓', '/ㄷ', '/e'],
	reply: ['/답', '/ㄷㄷ', '/ee'],
	ignore: ['/무시', '/wb'],
	block: ['/차단', '/block', '/shut'],
	uid: ['/보안코드', '/ㅕㅑㅇ', '/uid'],
	id: ['/식별번호', '/ㅑㅇ', '/id'],
	profile: ['/정보', '/ㅈㅂ', '/ㅈ', '/wq', '/info', '/profile'],
	settings: ['/설정', '/ㅅㅈ', '/tw', '/settings'],
	title: ['/방제', '/ㅂㅈ', '/qw'],
	time: ['/시간', '/ㅅㄱ', '/tr'],
	limit: ['/인원', '/ㅇㅇ', '/dd'],
	round: ['/라운드', '/ㄹㅇㄷ', '/fde'],
	password: ['/비번', '/ㅂㅂ', '/qq'],
	mode: ['/유형', '/ㅇㅎ', '/dg'],
	topic: ['/주제', '/ㅈㅈ', '/ww'],
	help: ['/도움말', '/ㄷㅇ', '/ed', '/help']
};
const BY_ALIAS = Object.fromEntries(Object.entries(ALIASES).flatMap(([name, list]) => list.map((a) => [a, name])));

/** The lines "/도움말" prints, in the legacy order (locale key suffix per command). */
export const HELP = ['r', 'cls', 'f', 'e', 'ee', 'wb', 'shut', 'qw', 'tr', 'dd', 'fde', 'qq', 'dg', 'ww', 'id', 'uid', 'profile', 'settings'];

/** "/e  to  hi" -> { name: 'whisper', args: ['to', 'hi'] }. Unknown commands get name null. */
export function parseCommand(text) {
	const parts = text.trim().split(' ');
	return { name: BY_ALIAS[parts[0].toLowerCase()] ?? null, args: parts.slice(1) };
}

/** An admin command: "#" followed by letters. The server runs it (admins only); nobody's client shows the line. */
export const isAdminCommand = (text) => /^#[a-zA-Z]+/.test(String(text).trim());

const compact = (s) => String(s).replace(/\s/g, '');

/** The id of whoever is called `nick` (spaces ignored), among the people the client knows. */
export function idFromNick(users, nick) {
	const want = compact(nick);
	return Object.values(users).find((u) => !u.robot && compact(u.name + (u.tag ? `#${u.tag}` : '')) === want || compact(u.name) === want)?.id;
}

/**
 * @param {string} text what was typed, starting with "/"
 * @param {object} c everything a command may touch: net, room, isMaster, t, notice(text), toast(text), send(type,data,toMaster),
 *   toggleReady(), start(), clearChat(), updateRoom(form), toggleBlock(kind, id), whisper(target, text), recentFrom, fetchId(nick), hooks {openSettings, openProfile(user), openRoomSettings(), openHelp()}
 */
export function runCommand(text, c) {
	const { name, args } = parseCommand(text);
	const { net, room, t } = c;
	const arg = args.join(' ');
	const tbd = (key) => c.toast(t('play.cmd.notReady', { name: t(`play.cmd.name.${key}`) }));

	// The room commands: only the master of the room I'm in.
	const asMaster = (fn) => {
		if (!room || room.master !== net.id) return c.notice(t('play.cmd.onlyMaster'));
		fn();
	};
	const rule = () => net.meta.rules[room.modeKey];
	// What the settings dialog would send for the room as it is now (`time` there is the base value, before the rule's factor).
	const form = () => ({ title: room.title, limit: room.limit, mode: room.modeIndex, round: room.rounds, time: Math.round(room.time / (rule()?.time ?? 1)), opts: room.opts, injpick: room.injpick });
	const usage = (key) => c.notice(t(`play.cmd.help.${key}`).replace(/[[\]]/g, ''));

	switch (name) {
		case 'ready':
			if (room) (room.master === net.id ? c.start : c.toggleReady)();
			break;
		case 'clear':
			c.clearChat();
			break;
		case 'log':
			tbd('log');
			break;
		case 'whisper':
			c.whisper(args[0], args.slice(1).join(' '));
			break;
		case 'reply':
			if (c.recentFrom) c.whisper(c.recentFrom, arg);
			else c.notice(t('play.cmd.noReply'));
			break;
		case 'ignore':
		case 'block': {
			if (!args.length) return usage(name === 'block' ? 'shut' : 'wb');
			const id = idFromNick(net.users, arg) ?? arg;
			// Legacy: "/차단" shuts the player out and also flips the whisper block; "/무시" only the whisper block.
			if (name === 'block') c.toggleBlock('shut', id);
			c.toggleBlock('mute', id);
			break;
		}
		case 'uid':
			if (net.guest) return c.notice(t('play.cmd.uidGuest'));
			if (!args.length) c.send('uid', undefined, true);
			else if (!['confirm', '확인'].includes(args.slice(1).join(' '))) c.notice(t('play.cmd.uidConfirm'));
			else c.send('newUid', undefined, true);
			break;
		case 'id':
			if (!args.length) return c.notice(t('play.cmd.myId', { id: net.id }));
			if (arg.includes('운영자')) return c.toast(t('play.cmd.noAdminInfo'));
			{
				const id = idFromNick(net.users, arg);
				if (id) c.notice(`[${arg}] ${id}`);
				else c.fetchId(arg);
			}
			break;
		case 'profile': {
			// Someone not here is looked up by the profile window itself (it says so when there is no such member).
			c.hooks.openProfile?.(args.length ? (net.users[args[0]]?.id ?? idFromNick(net.users, arg) ?? arg) : net.id);
			break;
		}
		case 'settings':
			c.hooks.openSettings?.();
			break;
		case 'title':
			// Without a title the room gets the form's default, "<nickname>님의 방" (legacy).
			asMaster(() => {
				const title = arg || t('play.cmd.defaultTitle', { nick: net.users[net.id]?.name ?? '' });
				if (!c.titleAllowed(title)) return c.toast(t('play.create.titleBad'));
				c.updateRoom({ ...form(), title });
			});
			break;
		case 'time':
			if (!args.length) return usage('tr');
			asMaster(() => {
				const v = parseInt(args[0], 10);
				if (!ROUND_TIMES.includes(v)) c.notice(`${t('play.cmd.invalidTime', { times: ROUND_TIMES.join(', ') + t('play.cmd.second') })}`);
				else c.updateRoom({ ...form(), time: v });
			});
			break;
		case 'limit':
			if (!args.length) return usage('dd');
			asMaster(() => c.updateRoom({ ...form(), limit: Number(args[0]) }));
			break;
		case 'round':
			if (!args.length) return usage('fde');
			asMaster(() => c.updateRoom({ ...form(), round: Number(args[0]) }));
			break;
		case 'password':
			if (!args.length) return usage('qq');
			asMaster(() => c.updateRoom({ ...form(), password: args[0] }));
			break;
		case 'mode':
			if (!args.length) return usage('dg');
			asMaster(() => {
				const index = net.meta.modes.findIndex((k) => net.meta.labels[`game.mode.${k}.name`] === arg.trim());
				if (index < 0) return c.notice(t('play.cmd.invalidMode'));
				// Options the new mode doesn't offer must not be sent along.
				const allowed = new Set((net.meta.rules[net.meta.modes[index]]?.opts ?? []).map((code) => net.meta.optionDefs[code]?.name?.toLowerCase()).filter(Boolean));
				const opts = Object.fromEntries(Object.entries(room.opts).filter(([k, on]) => on && allowed.has(k)));
				c.updateRoom({ ...form(), mode: index, opts });
			});
			break;
		case 'topic':
			asMaster(() => c.hooks.openRoomSettings?.());
			break;
		case 'help':
			c.hooks.openHelp?.();
			break;
		default:
			c.notice(t('play.cmd.invalid'));
	}
}
