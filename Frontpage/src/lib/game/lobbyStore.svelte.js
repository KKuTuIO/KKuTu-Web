import { filterRooms } from './lobbyModel.js';
import { levelSpan } from './level.js';
import { createConnection } from './connection.svelte.js';
import { md5 } from './md5.js';
import { t } from './i18n.svelte.js';

const ROOM_LIMIT = [10, 12, 14, 16, 16]; // by membership tier, as in the legacy client
const quickTitles = () => [1, 2, 3, 4, 5, 6, 7].map((n) => t(`play.quick.roomTitle.${n}`));

/** Lobby view state and actions on top of the live connection. */
export function createLobbyStore(server) {
	const conn = createConnection(server);
	const net = conn.state;
	const ui = $state({ query: '', sort: 'recent', waitingOnly: true, hasSeat: false, hideLocked: false, view: 'list' });

	const rooms = $derived(Object.values(net.rooms));
	const lobbyUsers = $derived(Object.values(net.users).filter((u) => u.place === 0).sort((a, b) => b.lv - a.lv));
	const me = $derived(net.users[net.id]);
	const friends = $derived(
		Object.entries(net.friends)
			.map(([id, name]) => {
				const u = net.users[id];
				const online = id in net.friendsOnline;
				return { id, name: u?.name ?? name, online, server: net.friendsOnline[id], user: u, place: u?.place ?? null };
			})
			.sort((a, b) => Number(b.online) - Number(a.online) || a.name.localeCompare(b.name))
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
			return lobbyUsers;
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
			conn.send('enter', { title: f.title, password: f.password ? md5(f.password) : '', limit: f.limit, mode: f.mode, round: f.round, time: f.time, opts: f.opts, injpick: [] });
		},
		/** Join a random open room of this mode, or open a fresh one - same as the legacy quick match. */
		quickMatch(mode, opts) {
			const open = rooms.filter(
				(r) => !r.playing && !r.locked && r.cur < r.max && r.modeIndex === mode && Object.keys(opts).every((k) => r.opts?.[k])
			);
			if (open.length) return this.join(open[Math.floor(Math.random() * open.length)].no);
			this.createRoom({ title: quickTitles()[Math.floor(Math.random() * 7)], limit: 8, mode, round: 5, time: 60, opts });
		},
		/** A random room name from the same pool as quick match (the legacy client's list), never the one already typed. */
		randomRoomTitle(except = '') {
			const pool = quickTitles().filter((x) => x !== except);
			return pool[Math.floor(Math.random() * pool.length)];
		},
		leaveRoom() {
			conn.sendRoom('leave');
		},
		sendChat(msg) {
			const value = msg.trim();
			if (value) conn.send('talk', { value });
		}
	};
}
