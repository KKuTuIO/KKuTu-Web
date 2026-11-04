import { applyPacket, initialState, plainText } from './protocol.js';
import { loadShop } from './images.js';
import { OKG_MINUTES } from './lobbyModel.js';
import { toast } from './ui/toast.svelte.js';
import { playSound, playGameBgm, stopBgm, playLobbyBgm, preloadGameSounds } from './audio.svelte.js';
import { prefs } from './prefs.svelte.js';
import { isHidden } from './chatFilter.js';
import { i18n, loadLocale, t } from './i18n.svelte.js';
import { telemetry } from './telemetry.js';
import { QUIZ } from './gameModel.js';
import { MAX_ATTEMPTS, retryDelay, shouldReconnect } from './reconnect.js';

/** Server messages are HTML snippets (<br>, links); toasts show plain text. */
export { plainText };

const TURNSTILE_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js';

/**
 * Owns the whole game-server handshake for one channel:
 *   1. GET /api/play/{n}/ticket      -> one-shot websocket URL (+ labels)
 *   2. open the websocket
 *   3. optional Turnstile `captcha` round trip
 *   4. `welcome` -> lobby state; send `identity` (mandatory liveness signal)
 *   5. `ping`/`pong` keep-alive, which also gives the latency shown in the top bar
 */
export function createConnection(server) {
	const state = $state({ ...initialState(), server });
	let seenMails = 0; // how many letters I've already been told about
	let seenAlerts = 0;
	let shop = $state({});
	let wsUrl = '';
	let rws;
	let ws;
	let pingTimer;
	let sentAt = 0;
	// Reconnecting: after an unexplained drop we try again by ourselves (see reconnect.js).
	let leaving = false; // the page is going away: nothing to reconnect to
	let attempt = 0;
	let retryTimer;

	/** Plan the next try, or give up (the gate then offers a manual retry). */
	function scheduleReconnect() {
		clearTimeout(retryTimer);
		if (leaving) return;
		if (attempt >= MAX_ATTEMPTS) {
			state.reconnect = { attempt, max: MAX_ATTEMPTS, nextAt: null, exhausted: true };
			return;
		}
		const delay = retryDelay(attempt);
		attempt++;
		state.reconnect = { attempt, max: MAX_ATTEMPTS, nextAt: Date.now() + delay, exhausted: false };
		retryTimer = setTimeout(retryNow, delay);
	}
	function retryNow() {
		clearTimeout(retryTimer);
		if (leaving || !state.reconnect || state.status === 'reconnecting' || state.status === 'ready') return;
		state.reconnect = { ...state.reconnect, nextAt: null };
		open({ reconnecting: true });
	}
	/** The "reconnect now" / "reconnect" buttons: start over with a fresh budget of attempts. */
	function reconnectNow() {
		attempt = 0;
		state.reconnect = { attempt: 0, max: MAX_ATTEMPTS, nextAt: null, exhausted: false };
		retryNow();
	}
	// A returning network or tab is the best moment to try, no need to wait out the delay.
	const wake = () => state.status === 'closed' && state.reconnect && !state.reconnect.exhausted && retryNow();
	const onVisible = () => document.visibilityState === 'visible' && wake();
	const browser = typeof window !== 'undefined'; // this also runs while the page is rendered on the server
	if (browser) {
		window.addEventListener('online', wake);
		document.addEventListener('visibilitychange', onVisible);
	}

	async function open({ reconnecting = false } = {}) {
		state.status = reconnecting ? 'reconnecting' : 'connecting';
		state.error = null;
		await loadLocale(); // before anything that might need to show a translated message
		let ticket;
		try {
			const res = await fetch(`/api/play/${server}/ticket`, { cache: 'no-store' });
			ticket = await res.json();
			if (!res.ok) {
				// A new account has no nickname yet: ask for one here instead of sending the user to another page.
				if (ticket.code === 'SETUP_REQUIRED') {
					state.status = 'setup';
					return;
				}
				state.status = 'error';
				state.error = { code: ticket.code, message: ticket.reason ?? '' };
				return;
			}
		} catch {
			state.error = { code: 'NETWORK', message: '' };
			// While reconnecting, "no network" is just another failed try.
			if (reconnecting) {
				state.status = 'closed';
				scheduleReconnect();
			} else state.status = 'error';
			return;
		}
		state.meta = { modes: ticket.modes, options: ticket.options, labels: i18n.messages, rules: ticket.rules, optionDefs: ticket.optionDefs, moremiParts: ticket.moremiParts, moremiCategories: ticket.moremiCategories ?? [], moremiGroups: ticket.moremiGroups ?? {}, moremiEquips: ticket.moremiEquips ?? [], pickThemes: ticket.pickThemes ?? { ko: [], en: [] }, version: ticket.version ?? '' };
		wsUrl = ticket.websocketUrl;
		const shopReady = reconnecting && Object.keys(shop).length ? Promise.resolve() : loadShop().then((s) => (shop = s));
		// Never hold the loading screen hostage to a slow font/CDN.
		Promise.race([Promise.all([shopReady, document.fonts?.ready]), new Promise((r) => setTimeout(r, 8000))]).then(() => (state.assetsReady = true));
		state.guest = ticket.guest;

		const sock = (ws = new WebSocket(ticket.websocketUrl));
		masterHook = onMasterWelcome;
		sock.onmessage = (e) => handle(e.data, true);
		function onMasterWelcome(pkt) {
			if (pkt.type === 'welcome') {
				// Identity is a mandatory liveness signal (15s). We collect no fingerprint: the server's
				// schema forbids empty strings, and confidence 0 is explicitly a non-disconnecting value.
				const n = 'n/a';
				send('identity', { fingerPrint2: n, fingerjsId: n, mainfpId: n, audioId: n, webglId: n, canvasId: n, pcid: n, confidence: 0 });
				startPing();
				if (attempt > 0 || state.reconnect) {
					attempt = 0;
					state.reconnect = null;
					toast(t('play.gate.reconnected'), { kind: 'success' });
				}
			}
		}
		sock.onclose = (e) => {
			if (sock !== ws) return; // an older socket closing after a newer one took over
			clearInterval(pingTimer);
			rws?.close(); // the room socket belongs to this session too
			rws = undefined;
			if (leaving || state.status === 'error') return;
			const reconnectable = shouldReconnect(e.code, state.error?.code);
			state.status = 'closed';
			state.myRoom = null; // the room is gone with the session
			state.preRoom = null;
			state.error = reconnectable ? { code: e.code, message: '' } : (state.error ?? { code: e.code, message: '' });
			if (reconnectable) scheduleReconnect();
		};
	}

	/** One path for packets from either socket: state, error toasts, 오끄감 notices, room handoff. */
	function handle(raw, fromMaster) {
		let pkt;
		try {
			pkt = JSON.parse(raw);
		} catch {
			return;
		}
		if (pkt.type === 'pong') state.pingMs = Math.round(performance.now() - sentAt);
		const before = pkt.type === 'okg' ? { playTime: state.playTime, count: state.okgCount } : null;
		const lastLine = state.chat.at(-1);
		const hadGame = !!state.game;
		applyPacket(state, pkt);
		// Legacy: whatever the server gave or changed (equipment, box), it then asks for a `refresh` so everyone sees the new look, unless it says `noref`.
		if (pkt.type === 'obtain' && !pkt.noref && fromMaster && state.status === 'ready') send('refresh');
		// Leaving (or losing) a game while its sounds are on: the turn clock's ticking and a quiz mode's music must not play on.
		if (hadGame && !state.game) {
			stopTurnSound();
			stopBgm();
			playLobbyBgm();
		}
		// Legacy: every chat line that is shown ticks (the "k" sound of the effect set, on the voice/UI channel).
		const line = state.chat.at(-1);
		if (pkt.type === 'chat' && line && line !== lastLine && !line.system && !isHidden(line, state, { ignoreSpectatorRule: true })) playSound('k', { ui: true });
		if (pkt.type === 'roomStuck') rws?.close(); // legacy: close the room connection
		gameSounds(pkt);
		if (pkt.type === 'room' && state.myRoom) preloadGameSounds(); // being in a room means a game may start: have its sounds ready
		// Legacy: failing a second turn in a row makes me leave (and says why).
		if (pkt.type === 'turnEnd' && state.game?.combo?.action === 'leave') {
			send('leave', { source: 'fail-combo' });
			toast(plainText(state.meta.labels['kkutu.js.error.437'] ?? ''), { kind: 'error' });
		}
		if (pkt.type === 'room' && state.kicked && state.kicked.at > Date.now() - 1000 && !handled.has(state.kicked)) {
			handled.add(state.kicked);
			toast(t('play.room.kicked'), { kind: 'error', duration: 6000 });
		}
		if (pkt.type === 'welcome' && Number(pkt.ratingRestrictionUntil) > Date.now() / 1000) toast(t('play.rating.restriction'), { kind: 'error', duration: 10000 });
		if (pkt.type === 'friendAddRes') {
			const who = state.users[pkt.target]?.name ?? pkt.nick ?? '';
			toast(t(pkt.res ? 'play.friend.accepted' : 'play.friend.declined', { nick: who }), { kind: pkt.res ? 'success' : 'info' });
		}
		// The mailbox: say so when there is something new (legacy: right after connecting, then whenever it is looked at).
		if (pkt.type === 'mailBox') {
			const n = state.mails.length;
			if (n > seenMails) toast(t('play.mail.unread', { n }), { icon: 'mail' });
			seenMails = n;
			const a = state.notifications.length;
			if (a > seenAlerts && !seenAlerts) toast(t('play.mail.unreadAlerts', { n: a }), { icon: 'alert' });
			seenAlerts = a;
		}
		if (pkt.type === 'obtainMail') {
			send('mailBox'); // the claimed letter is gone: legacy asks for the box again
			for (const r of pkt.rewards ?? []) {
				if (r.type === 'ping') toast(t('play.mail.obtainedPing', { n: Number(r.q || 0).toLocaleString() }), { kind: 'success' });
				else if (r.name) toast(t('play.mail.obtainedItem', { name: i18n.messages[`item.name.${r.name}`] ?? r.name }), { kind: 'success' });
			}
		}
		if (before) okgNotice(before, pkt);
		if (pkt.type === 'error' && state.status === 'ready') {
			// 630 is the answer to a nickname change that worked (the game reports results as codes)
			toast(plainText(state.meta.labels[`kkutu.js.error.${pkt.code}`] ?? pkt.message) || t('play.error.request', { code: pkt.code }), { kind: Number(pkt.code) === 630 ? 'success' : 'error' });
		}
		if (fromMaster) {
			if (pkt.type === 'preRoom') connectRoom(pkt);
			masterHook?.(pkt);
		}
	}
	/** The sounds the legacy client makes for the game's packets. */
	let turnSound; // the ticking of the turn clock, cut off when the turn ends
	const stopTurnSound = () => {
		turnSound?.then((h) => h?.stop());
		turnSound = null;
	};
	/** The sounds of the all-at-once modes, as each legacy module plays them. Returns true when the round's music should stop. */
	function quizSounds(pkt, fam) {
		const me = state.id;
		const spectating = state.users[me]?.game.form === 'S';
		const target = pkt.target && typeof pkt.target === 'object' ? pkt.target.id : (pkt.target ?? pkt.id);
		if (pkt.type === 'turnHint') {
			playSound(fam === 'Sock' || fam === 'Linkland' || fam === 'Crossword' ? 'fail' : 'mission');
			return false;
		}
		if (pkt.type !== 'turnEnd') return false;
		if (fam === 'Jaqwi' || fam === 'Drawing') {
			if (pkt.giveup) return false;
			playSound(pkt.answer ? 'horr' : 'success');
			return !!pkt.answer;
		}
		if (fam === 'Sock') {
			if (pkt.score) playSound(target === me ? 'success' : 'mission');
			else playSound('horr');
			return !pkt.score;
		}
		if (fam === 'Crossword') {
			if (pkt.score) playSound(target === me ? 'success' : 'mission');
			else playSound('horr');
			return !pkt.score;
		}
		if (fam === 'Linkland') {
			if (!pkt.settlement) playSound(target === me ? 'success' : 'mission');
			return false;
		}
		if (fam === 'Wordstack') {
			if (pkt.error) playSound('fail');
			else if (pkt.ok || pkt.isBonus) {
				if (!pkt.isBonus) {
					if (target === me) playSound('mission');
					else if (pkt.attack === me) playSound('kung');
					else if (spectating) playSound('mission');
				}
			} else {
				playSound('horr');
				return true;
			}
			return false;
		}
		// 타자 대결 / 수학 대결
		if (pkt.error) playSound('fail');
		else if (pkt.ok) {
			if (target === me || spectating) playSound('mission');
		} else {
			playSound('horr');
			return true;
		}
		return false;
	}
	function gameSounds(pkt) {
		const fam = state.meta.rules[state.rooms[state.myRoom]?.modeKey]?.rule;
		const quiz = QUIZ.includes(fam);
		if (quiz && pkt.type === 'turnStart') {
			// legacy: the quiz modes have a music of their own, a faster one for the last ten seconds
			telemetry.turnStart(pkt);
			playGameBgm(pkt.roundTime <= 10000 ? 'JaqwiFastBGM' : 'JaqwiBGM');
			return;
		}
		if (quiz && ['turnHint', 'turnEnd'].includes(pkt.type)) {
			stopTurnSound();
			if (quizSounds(pkt, fam)) stopBgm();
			return;
		}
		if (pkt.type === 'turnStart') {
			telemetry.turnStart(pkt);
			stopTurnSound();
			turnSound = playSound(`T${pkt.speed}`);
		} else if (pkt.type === 'roundEnd') stopTurnSound();
		else if (pkt.type === 'roundReady' && !pkt.enter) playSound('round_start');
		else if (pkt.type === 'turnError') playSound('fail');
		else if (pkt.type === 'turnEnd') {
			stopTurnSound();
			if (!pkt.ok) playSound('timeout');
			else if (pkt.baby) playSound('success');
		}
	}
	let masterHook;
	const handled = new WeakSet(); // kick notices already shown

	/** 오끄감 buff toasts, as in the legacy client: reset at midnight, or a new buff earned. */
	function okgNotice(before, pkt) {
		if (before.playTime > pkt.time) toast(t('play.okg.expired'), { kind: 'info' });
		else if (before.count !== pkt.count) {
			const minutes = OKG_MINUTES[state.membership] ?? OKG_MINUTES[0];
			toast(`${t('play.okg.gained', { minutes })} (${t('play.okg.stack', { count: pkt.count })})`, { kind: 'success', icon: 'xp' });
		}
	}

	// Which socket a message goes to, same rule as the legacy client: lobby-level messages to master, room
	// actions to the room worker, and everything else (chat, room settings) to the worker while in a room.
	const MASTER_ONLY = new Set(['captcha', 'identity', 'enter', 'ping', 'refresh', 'refreshRoom', 'inviteRes', 'togglePrivacy', 'uid', 'newUid', 'friendAdd', 'friendAddRes', 'friendEdit', 'friendRemove', 'toggleBF', 'setStatus', 'setStatusMessage', 'mailBox', 'obtainMail', 'drawGacha', 'consume', 'notifications', 'notificationAck']);
	const ROOM_ONLY = new Set(['leave', 'ready', 'start', 'practice', 'invite', 'form', 'team', 'kick', 'kickVote', 'handover', 'setAI', 'useItem', 'wp']);
	/** 22-char url-safe id (16 random bytes), required by the server on durable requests so a retry can't apply twice. */
	function newRequestId() {
		const bytes = crypto.getRandomValues(new Uint8Array(16));
		return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
	}
	const DURABLE = new Set(['saveProgress', 'import', 'newUid']);
	function send(type, data = {}) {
		if (DURABLE.has(type) && !data.requestId) data = { ...data, requestId: newRequestId() };
		// Whispers go through the master (it knows everyone), like the other lobby-wide messages.
		const sock = MASTER_ONLY.has(type) || (type === 'talk' && data.whisper) ? ws : ROOM_ONLY.has(type) ? rws : (rws ?? ws);
		if (sock?.readyState === WebSocket.OPEN) sock.send(JSON.stringify({ ...data, type }));
	}
	const sendRoom = send; // kept for existing callers

	function connectRoom({ channel, id, handoffToken }) {
		rws?.close(1000, 'reconnect');
		const url = wsUrl.replace(/:(\d+)/, (_, port) => `:${Number(port) + 416 + Number(channel) - 1}`) + `&${channel}&${id}`;
		const sock = (rws = new WebSocket(url));
		sock.onopen = () => sock.send(JSON.stringify({ type: 'handoff', token: handoffToken ?? state.preRoom?.token }));
		sock.onmessage = (e) => handle(e.data, false);
		sock.onclose = () => {
			if (rws === sock) rws = undefined;
			state.myRoom = null;
			if (state.game) {
				state.game = null;
				stopTurnSound();
				stopBgm();
				playLobbyBgm();
			}
		};
	}

	function startPing() {
		const tick = () => {
			sentAt = performance.now();
			send('ping');
		};
		tick();
		pingTimer = setInterval(tick, 30_000);
	}

	function loadTurnstile() {
		return new Promise((resolve, reject) => {
			if (window.turnstile) return resolve(window.turnstile);
			const s = document.createElement('script');
			s.src = TURNSTILE_SRC;
			s.onload = () => resolve(window.turnstile);
			s.onerror = reject;
			document.head.append(s);
		});
	}

	return {
		state,
		open,
		send,
		sendRoom,
		get shop() {
			return shop;
		},
		reconnect: reconnectNow,
		close: () => {
			leaving = true;
			clearTimeout(retryTimer);
			clearInterval(pingTimer);
			if (browser) {
				window.removeEventListener('online', wake);
				document.removeEventListener('visibilitychange', onVisible);
			}
			rws?.close();
			ws?.close();
		},
		/** Renders the Turnstile widget into `el`; the token goes straight back to the server. */
		async renderCaptcha(el) {
			const ts = await loadTurnstile();
			ts.render(el, { sitekey: state.captchaSiteKey, callback: (token) => send('captcha', { token }) });
		}
	};
}
