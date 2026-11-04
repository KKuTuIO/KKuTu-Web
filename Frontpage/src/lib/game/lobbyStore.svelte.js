import { prefs } from './prefs.svelte.js';
import { FRIEND_LIMIT, filterRooms, needsThemes as rulePicks } from './lobbyModel.js';
import { levelSpan } from './level.js';
import { createConnection } from './connection.svelte.js';
import { md5 } from './md5.js';
import { notice, plainText } from './protocol.js';
import { BAD, isAllowedText, isBad } from './badwords.js';
import { createSpamGuard } from './spamGuard.js';
import { isAdminCommand, runCommand } from './commands.js';
import { toast } from './ui/toast.svelte.js';
import { confirm } from './ui/confirm.svelte.js';
import { blocked, isShut, toggleBlock } from './blocks.svelte.js';
import { idFromNick } from './commands.js';
import { i18n, t } from './i18n.svelte.js';
import { telemetry } from './telemetry.js';
import { QUIZ, familyOf } from './gameModel.js';

const ROOM_LIMIT = [10, 12, 14, 16, 16]; // by membership tier, as in the legacy client
const quickTitles = () => [1, 2, 3, 4, 5, 6, 7].map((n) => t(`play.quick.roomTitle.${n}`));

/** Lobby view state and actions on top of the live connection. */
/** The text of a legacy error code. */
const plainError = (code) => plainText(i18n.messages[`kkutu.js.error.${code}`] ?? '');

export function createLobbyStore(server) {
	const spam = createSpamGuard();
	const chatUi = $state({ draft: { text: '', nonce: 0 } });
	let whisperPrefix = '';
	const conn = createConnection(server);
	const net = conn.state;
	const live = $state({ value: '', composing: false }); // what I am typing right now, from the board's field or the chat box
	const ui = $state({ query: '', sort: 'recent', joinableFirst: false, waitingOnly: true, hasSeat: false, hideLocked: false, view: 'list', viewPicked: false, gameCovered: false, exitReserved: false });

	const rooms = $derived(Object.values(net.rooms));
	// 접속자: everyone on this server (also those inside rooms), AI seats excluded. Order is the legacy one: by arrival,
	// or by score / name when asked.
	const serverUsers = $derived.by(() => {
		const all = Object.values(net.users).filter((u) => !u.robot);
		if (prefs.userSort === 'score') return all.sort((a, b) => b.score - a.score);
		if (prefs.userSort === 'name') return all.sort((a, b) => a.name.toUpperCase().localeCompare(b.name.toUpperCase()));
		return all;
	});
	const me = $derived(net.users[net.id]);
	const friends = $derived(
		Object.entries(net.friends)
			.map(([id, name]) => {
				const u = net.users[id];
				const online = id in net.friendsOnline;
				return { id, name: u?.name ?? name, online, bf: !!net.friendsData[id]?.bf, server: net.friendsOnline[id], user: u, place: u?.place ?? null, status: online ? (net.friendsStatus[id] ?? 'online') : 'offline' };
			})
			// Best friends first (legacy), then who is online.
			.sort((a, b) => Number(b.bf) - Number(a.bf) || Number(b.online) - Number(a.online) || a.name.localeCompare(b.name))
	);

	const modeIndex = (key) => net.meta.modes.indexOf(key);

	return {
		server,
		conn,
		net,
		ui,
		get shop() {
			return conn.shop;
		},
		get visibleRooms() {
			return filterRooms(rooms, ui);
		},
		get users() {
			return serverUsers;
		},
		get friends() {
			return friends;
		},
		get me() {
			return me && { ...me, ...levelSpan(me.score) };
		},
		get roomLimitMax() {
			return ROOM_LIMIT[net.membership] ?? ROOM_LIMIT[0];
		},
		get activeFilters() {
			return Number(ui.waitingOnly) + Number(ui.hasSeat) + Number(ui.hideLocked);
		},
		get currentRoom() {
			return net.myRoom ? net.rooms[net.myRoom] : null;
		},
		modeIndex,
		join(no, password = '') {
			conn.send('enter', { id: Number(no), password: password ? md5(password) : '' });
		},
		/** @param {{title:string,password?:string,limit:number,mode:number,round:number,time:number,opts:Record<string,boolean>}} f */
		createRoom(f) {
			conn.send('enter', { title: f.title, password: f.password ? md5(f.password) : '', limit: f.limit, mode: f.mode, round: f.round, time: f.time, opts: f.opts, injpick: f.injpick ?? [] });
		},
		/** Join a random open room of this mode, or open a fresh one - same as the legacy quick match. */
		quickMatch(mode, opts, injpick = []) {
			// With themes picked (단어장), only rooms that play all of them fit.
			const open = rooms.filter(
				(r) =>
					!r.playing && !r.locked && r.cur < r.max && r.modeIndex === mode && Object.keys(opts).every((k) => r.opts?.[k]) && injpick.every((c) => r.injpick?.includes(c))
			);
			if (open.length) {
				this.join(open[Math.floor(Math.random() * open.length)].no);
				return 'joined';
			}
			// A new 단어장 room needs themes (a mode that always has them, or the option switched on).
			const rule = net.meta.rules[net.meta.modes[mode]];
			const needsThemes = !!rule && rulePicks(rule, opts);
			if (needsThemes && !injpick.length) return 'needThemes';
			this.createRoom({ title: quickTitles()[Math.floor(Math.random() * 7)], limit: 8, mode, round: 5, time: 60, opts, injpick: needsThemes ? injpick : [] });
			return 'created';
		},
		/** The titles a guest may choose from (legacy allowedRoomTitles). */
		get allowedTitles() {
			return quickTitles();
		},
		/** A random room name from the same pool as quick match (the legacy client's list), never the one already typed. */
		randomRoomTitle(except = '') {
			const pool = quickTitles().filter((x) => x !== except);
			return pool[Math.floor(Math.random() * pool.length)];
		},
		/** Guests: ask for a code that moves this session's progress to a member account (이어하기). */
		saveProgress() {
			const code = net.prompt?.lang === 'gidInfo' ? net.prompt.value : undefined;
			conn.send('saveProgress', code ? { gid: code } : {});
		},
		/** A system line in the chat (used by room timers). */
		pushNotice: (text) => notice(net, text),
		leaveRoom() {
			conn.send('leave');
		},
		// ---- in-room actions (all go to the room worker socket) ----
		get roomPlayers() {
			const r = this.currentRoom;
			return r ? r.players.map((id) => net.users[id]).filter(Boolean) : [];
		},
		get isMaster() {
			return !!this.currentRoom && this.currentRoom.master === net.id;
		},
		get myGame() {
			return net.users[net.id]?.game ?? null;
		},
		/** Invite a lobby user (their id) or an AI of the given level (invite target "AI"). */
		invite: (target, level) => conn.send('invite', level === undefined ? { target } : { target, level }),
		answerKickVote(agree) {
			conn.send('kickVote', { agree });
			net.kickVote = null;
		},
		/** Accept or (when auto-declining) refuse an invitation. Answers go to the master socket, like legacy. */
		answerInvite(from, res) {
			conn.send('inviteRes', { from, res }, true);
			net.invite = null;
		},
		// ---- friends (legacy rules: a request needs the other player online, and only an offline friend can be removed) ----
		/** Shows others a different status (`auto`: set by the idle watcher, so the server does not keep it for next time). */
		setStatus(status, auto = false) {
			if (net.guest || status === net.myStatus) return;
			net.myStatus = status;
			conn.send('setStatus', { status, ...(auto ? { auto: true } : {}) });
		},
		/** Pins a line on my profile (an empty one removes it). */
		setStatusMessage(text) {
			if (net.guest) return;
			if (net.users[net.id]) net.users[net.id].statusMessage = text;
			conn.send('setStatusMessage', { text });
		},
		isFriend: (id) => id in net.friendsData,
		isBestFriend: (id) => !!net.friendsData[id]?.bf,
		/** The other player need not be online, nor on this channel: the request waits for them. `name` is who to call them when they are not here. */
		async addFriend(id, { confirm: ask = true, name = '' } = {}) {
			const who = net.users[id] ?? (name ? { name } : null);
			if (!who) return toast(plainError(424), { kind: 'error' });
			if (Object.keys(net.friends).length >= FRIEND_LIMIT) return toast(plainError(452), { kind: 'error' });
			if (ask && !(await confirm({ message: t('play.friend.confirmAdd', { nick: who.name }) }))) return;
			conn.send('friendAdd', { target: id });
			if (!net.friendPending.some((p) => p.target === id)) net.friendPending.push({ target: id, name: who.name, at: Date.now() });
			toast(t('play.friend.requestSent', { nick: who.name }), { kind: 'info' });
			return true; // sent (the caller can close its dialog)
		},
		async removeFriend(id) {
			if (id in net.friendsOnline) return toast(plainError(455), { kind: 'error' });
			const nick = net.friends[id] ?? id;
			if (!(await confirm({ message: `${nick}\n${t('play.friend.confirmRemove')}` }))) return;
			conn.send('friendRemove', { id });
		},
		async toggleBestFriend(id) {
			const bf = !!net.friendsData[id]?.bf;
			if (!(await confirm({ message: `${net.friends[id] ?? id}\n${t(bf ? 'play.friend.confirmBestOff' : 'play.friend.confirmBestOn')}` }))) return;
			conn.send('toggleBF', { id });
			if (net.friendsData[id]) net.friendsData[id].bf = !bf;
		},
		answerFriendRequest(from, res) {
			conn.send('friendAddRes', { from, res });
			net.friendRequests = net.friendRequests.filter((r) => r.from !== from);
		},
		// ---- mailbox and notifications ----
		requestMailbox: () => conn.send('mailBox'),
		claimMail: (id) => conn.send('obtainMail', { id }),
		ackNotification: (id) => conn.send('notificationAck', { id: String(id) }),
		/** What is waiting for me: unread mail, unconfirmed notices and friend requests. */
		/** Friend requests in either direction that are still open. */
		get pendingFriends() {
			return {
				incoming: net.friendRequests.map((r) => ({ id: r.from, name: net.users[r.from]?.name ?? (r.nick || r.from), user: net.users[r.from], at: r.at })),
				outgoing: net.friendPending.map((p) => ({ id: p.target, name: net.users[p.target]?.name ?? p.name, user: net.users[p.target], at: p.at }))
			};
		},
		get unreadCount() {
			return net.mails.length + net.notifications.length + net.friendRequests.length;
		},
		// ---- blocking (see blocks.svelte.js) ----
		/** Block or unblock (everything, or only whispers); says what happened in the chat like legacy. */
		toggleBlock(kind, id) {
			if (id === net.id) return notice(net, t('play.block.self'));
			const nick = net.users[id]?.name ?? blocked[kind][id] ?? id;
			const on = toggleBlock(kind, id, nick);
			notice(net, t(`play.block.${on ? '' : 'un'}${kind}`, { nick }));
		},
		setAI: (target, level, team) => conn.send('setAI', { target, level, team }),
		kickAI: (target) => conn.send('kick', { target, robot: true }),
		// ---- the game itself ----
		get game() {
			return net.game;
		},
		/** The game screen shows while my room plays, and after it until the result is closed. */
		get inGame() {
			const r = this.currentRoom;
			return !!r && (r.playing || !!net.game?.result);
		},
		/** Whose turn it is is me (and I am playing, not watching). */
		get myTurn() {
			if (!net.game || net.game.phase !== 'turn' || net.users[net.id]?.game.form === 'S') return false;
			if (net.game.painter === net.id) return false; // the painter shows the word, never says it
			// the quiz modes are played all at once: anyone playing may answer while the round runs
			return QUIZ.includes(familyOf(net)) ? true : net.game.current === net.id;
		},
		/** Send the word for my turn (the same message the legacy client sends, with the ids that tie it to this turn). */
		submitWord(value, data) {
			const g = net.game;
			if (!g || !value.trim()) return false;
			live.value = '';
			live.composing = false;
			g.submitted = value; // what I typed, so a refusal can strike it out on the board
			const bytes = new TextEncoder().encode(value);
			conn.send('talk', {
				relay: true,
				value, // as typed: the server compares it with `checker`
				checker: btoa(String.fromCharCode(...bytes)),
				gameId: g.gameId,
				turnId: g.turnId,
				inputEpoch: g.inputEpoch,
				lastInputSeq: telemetry.take(),
				...(data ? { data } : {}) // 십자말풀이: which bar is being answered
			});
			return true;
		},
		/** Leave the room; in a running game the legacy confirmation comes first. */
		async leaveGame() {
			const g = net.game;
			const room = this.currentRoom;
			if (g && g.phase !== 'ended') {
				const spectating = net.users[net.id]?.game.form === 'S';
				const key = spectating || room?.practice ? 'kkutu.js.confirmSpectate' : 'kkutu.js.confirmExit';
				if (!(await confirm({ message: plainText(t(key)) }))) return;
			}
			ui.exitReserved = false; // leaving now settles it
			conn.send('leave');
		},
		/**
		 * 타자 대결 / 수학 대결 without 속담 or 장문: Space sends the word, like Enter (the legacy client's `Typing.spaceOn`; its test
		 * compared `e.key` with "Space", which a browser never reports, so there it never fired). Returns whether the key was taken.
		 */
		spaceSubmits(e, value, clear) {
			if (e.code !== 'Space' && e.key !== ' ') return false;
			const room = this.currentRoom;
			if (!room || !['Typing', 'Mathquiz'].includes(familyOf(net)) || room.opts?.proverb || room.opts?.longtext || !this.myTurn) return false;
			if (value.startsWith('/')) return false;
			const el = e.currentTarget;
			const send = (v) => {
				v = v.replace(/\s+$/, '');
				if (v && this.submitWord(v)) clear();
			};
			if (e.isComposing) {
				// the syllable being composed is finished by the space itself: take the text once the browser has done that
				setTimeout(() => send(el.value), 0);
			} else {
				e.preventDefault();
				send(value);
			}
			return true;
		},
		/** 퇴장 예약: leave on my own when this game's result is closed (legacy ReserveExitBtn). Says so in the chat either way. */
		toggleExitReserve() {
			ui.exitReserved = !ui.exitReserved;
			notice(net, plainText(i18n.messages[ui.exitReserved ? 'kkutu.js.leaveReserved' : 'kkutu.js.leaveReserveDisabled'] ?? ''));
		},
		/** 그림퀴즈: the painter's picture, sent as a JPEG. */
		sendCanvas(data) {
			conn.send('drawingCanvas', { diffed: false, image: true, data });
		},
		/** 십자말풀이: choose which round's board is on show (the round chips in the header), or which bar is being answered. */
		selectRound(n) {
			if (net.game) {
				net.game.selectedRound = n;
				net.game.sel = null;
			}
		},
		selectBar(sel) {
			if (net.game) net.game.sel = sel;
		},
		/** Close the result and go back to the waiting room. */
		closeResult() {
			const practice = !!net.game?.result?.practice;
			net.game = null;
			if (practice) {
				// the practice stage ends with its result (legacy: leave, source 'result-practice'); a reserved exit is the same leave
				ui.exitReserved = false;
				conn.send('leave', { source: 'result-practice' });
			} else if (ui.exitReserved) {
				ui.exitReserved = false;
				conn.send('leave');
			}
		},
		telemetry,
		get typed() {
			return live.value;
		},
		/** True while the last character is still being composed (Korean IME): it is not judged until it is finished. */
		get composing() {
			return live.composing;
		},
		setTyped(v, composing = false) {
			live.value = v;
			live.composing = composing;
		},
		toggleReady: () => conn.send('ready'),
		start: () => conn.send('start'),
		setSpectate: (on) => conn.send('form', { mode: on ? 'S' : 'J' }),
		setTeam: (value) => conn.send('team', { value }),
		practice: (level = -1) => conn.send('practice', { level }),
		kick: (target, robot = false) => conn.send('kick', { target, robot }),
		handover: (target) => conn.send('handover', { target }),
		/** Change the settings of the room I am in (master only). `password` undefined keeps it, '' removes it. */
		updateRoom(f) {
			const msg = { title: f.title, limit: f.limit, mode: f.mode, round: f.round, time: f.time, opts: f.opts, injpick: f.injpick ?? [] };
			if (f.password !== undefined) msg.password = f.password ? md5(f.password) : '';
			conn.send('setRoom', msg);
		},
		/** 전적 숨기기: the server hides all but the last 10 games from other players' record lookups. */
		setRecordPrivacy(hide) {
			conn.send('togglePrivacy', { preferences: { recordHistoryPublic: !hide } });
		},
		/** Screens the page lends to commands (settings, a profile, the room settings). */
		hooks: {},
		/** "/…" typed in the chat. */
		runCommand(text) {
			runCommand(text, {
				net,
				room: this.currentRoom,
				t,
				notice: (s) => notice(net, s),
				toast: (s) => toast(s, { kind: 'info' }),
				send: (type, data, toMaster) => conn.send(type, data, toMaster),
				toggleReady: () => conn.send('ready'),
				start: () => conn.send('start'),
				clearChat: () => net.chat.splice(0),
				updateRoom: (f) => this.updateRoom(f),
				toggleBlock: (kind, id) => this.toggleBlock(kind, id),
				whisper: (target, text) => this.whisper(target, text),
				recentFrom: net.recentFrom,
				titleAllowed: (s) => this.titleAllowed(s),
				hooks: this.hooks,
				async fetchId(nick) {
					try {
						const r = await (await fetch(`/idFromNick/${encodeURIComponent(nick.replace(/[-_ ]/g, ''))}`)).json();
						notice(net, r.error ? (net.meta.labels[`kkutu.js.error.${r.error}`] ?? t('play.cmd.noUser')) : `[${nick}] ${r.id}`);
					} catch {
						notice(net, t('play.cmd.noUser'));
					}
				}
			});
		},
		/** Whether a room title may be used (the legacy check; the dialog and /방제 both ask). */
		titleAllowed: (title) => !isBad(title) && isAllowedText(title),
		// ---- whispers (talk with a `whisper` target goes to the master) ----
		/** Text waiting to be put into the chat box (a whisper prefix, from the user menu), and a counter so repeats count. */
		get draft() {
			return chatUi.draft;
		},
		fillChat(text) {
			chatUi.draft = { text, nonce: chatUi.draft.nonce + 1 };
		},
		/** After whispering, the box is refilled with "/e name " so the conversation can go on (legacy). */
		takeWhisperPrefix() {
			const p = whisperPrefix;
			whisperPrefix = '';
			return p;
		},
		/** Whisper `text` to the player called `target` (a nickname without spaces, or an id). Legacy checks and wording. */
		whisper(target, text) {
			if (!text) return;
			const id = idFromNick(net.users, target) ?? target;
			if (id === net.id) return notice(net, plainError(724));
			const who = net.users[id];
			if (!who) return notice(net, plainError(405));
			if (isShut(id)) return notice(net, plainError(723));
			const name = (who.name + (who.tag ? `#${who.tag}` : '')).replace(/\s/g, '');
			whisperPrefix = `/e ${name} `;
			const me = net.users[net.id];
			const mode = prefs.whisperFrom; // 귓속말 거부 also limits whom I may whisper (legacy)
			if (mode === 'near' && !net.friendsData[id]?.bf) return notice(net, t('play.whisper.blocked.near'));
			if (mode === 'friend' && !(id in net.friendsData)) return notice(net, t('play.whisper.blocked.friend'));
			if (mode === 'guest' && id.includes('guest')) return notice(net, t('play.whisper.blocked.guest'));
			conn.send('talk', { whisper: name, value: text });
			net.chat.push({ who: `→${name}`, msg: text, shown: text, at: Date.now(), system: false, uid: net.id, whisper: true, to: name, spectator: false, mine: true });
		},
		sendChat(msg) {
			const value = msg.trim();
			if (!value) return;
			// 그림퀴즈: the one who draws cannot send anything while the game runs (legacy)
			if (net.game && net.game.painter === net.id && net.game.phase !== 'ended') return notice(net, plainText(i18n.messages['kkutu.product.chat.painterChatBlocked'] ?? ''));
			if (value[0] === '/') return this.runCommand(value);
			// On my turn a line typed in the chat is my answer, just like one typed in the game's own field.
			if (this.myTurn) return this.submitWord(msg);
			const verdict = spam(value);
			if (!verdict.allow) {
				if (verdict.notice) notice(net, t(verdict.notice, { seconds: verdict.seconds }));
				return;
			}
			// Legacy: with the filter on, swearing is still sent, but the sender is told to stop. ("#" lines are admin commands.)
			if (prefs.filter === 'blind' && !isAdminCommand(value) && value.search(BAD) !== -1) toast(t('play.chat.noSwearing'), { kind: 'info' });
			conn.send('talk', { value });
		}
	};
}
