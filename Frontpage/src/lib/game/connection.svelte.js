import { setNudgeState } from './nudges.svelte.js';
import { setProgress } from './tutorial/progress.svelte.js';
import { showInjeongHint } from './injeongHint.js';
import { applyPacket, errorText, initialState, plainText } from './protocol.js';
import { loadShop } from './images.js';
import { OKG_MINUTES } from './lobbyModel.js';
import { toast } from './ui/toast.svelte.js';
import { confirm, notify } from './ui/confirm.svelte.js';
import { yell } from './ui/yell.svelte.js';
import { playSound, playGameBgm, stopBgm, playLobbyBgm, isResultBgm, preloadGameSounds } from './audio.svelte.js';
import { prefs } from './prefs.svelte.js';
import { isHidden } from './chatFilter.js';
import { i18n, loadLocale, t } from './i18n.svelte.js';
import { createTelemetry } from './telemetry.js';
import { identity } from './identity.js';
import { createSecurity } from './security/index.js';
import { socketSend } from './security/native.js';
import { QUIZ } from './gameModel.js';
import { MAX_ATTEMPTS, retryDelay, shouldReconnect } from './reconnect.js';
import { activeMaintenance, classifyOutage, SCHEDULE_EVERY, SCHEDULE_URL } from './outage.js';
import { createRecorder } from './replayRec.js';
import { createGameAudio } from './gameAudio.js';

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

	// Why we are down (see outage.js): the gate says it, and it decides how patient the retrying is.
	let scheduleAt = 0;
	function noteOutage(seen) {
		const kind = classifyOutage({ online: typeof navigator === 'undefined' ? true : navigator.onLine, ...seen });
		if (!(state.outage?.kind === 'maintenance' && kind !== 'network')) state.outage = { kind };
		if (kind !== 'network') checkSchedule();
	}
	/** Look in the operators' schedule for a maintenance that applies now: its name and reason are shown as they wrote them. */
	async function checkSchedule() {
		if (Date.now() - scheduleAt < SCHEDULE_EVERY) return;
		scheduleAt = Date.now();
		try {
			const m = activeMaintenance(await (await fetch(SCHEDULE_URL, { cache: 'no-store' })).json());
			if (state.status === 'ready' || !state.outage) return;
			if (m) state.outage = { kind: 'maintenance', ...m };
			else if (state.outage.kind === 'maintenance') state.outage = { kind: 'unknown' };
		} catch {
			// the file is not reachable (offline, or its host is down too): what we saw stays
		}
	}
	/** The ticket could not be had (no network, or the proxy answers instead of the app): the same waiting as for a dropped socket. */
	function down(seen) {
		state.error = { code: seen.httpStatus ? `HTTP_${seen.httpStatus}` : 'NETWORK', message: '' };
		noteOutage(seen);
		state.status = 'closed';
		scheduleReconnect();
	}

	/** Plan the next try, or give up (the gate then offers a manual retry). */
	function scheduleReconnect() {
		clearTimeout(retryTimer);
		if (leaving) return;
		// Offline: nothing to try until the network is back (the `online` event retries at once).
		if (state.outage?.kind === 'network') {
			state.reconnect = { attempt, max: MAX_ATTEMPTS, nextAt: null, exhausted: false, waiting: true };
			return;
		}
		// A maintenance the operators announced is waited out, not given up on.
		const patient = state.outage?.kind === 'maintenance';
		if (attempt >= MAX_ATTEMPTS && !patient) {
			state.reconnect = { attempt, max: MAX_ATTEMPTS, nextAt: null, exhausted: true };
			return;
		}
		const delay = patient ? 30_000 : retryDelay(attempt);
		attempt = Math.min(attempt + 1, MAX_ATTEMPTS);
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
			ticket = await res.json().catch(() => null);
			// The proxy answers with a page (or 502/503/504) while the web server restarts.
			if (!ticket || [502, 503, 504].includes(res.status)) return down({ httpStatus: res.status, badBody: !ticket });
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
			return down({}); // no network (or the server is not there at all)
		}
		state.meta = { modes: ticket.modes, options: ticket.options, labels: i18n.messages, rules: ticket.rules, optionDefs: ticket.optionDefs, moremiParts: ticket.moremiParts, moremiCategories: ticket.moremiCategories ?? [], moremiGroups: ticket.moremiGroups ?? {}, moremiEquips: ticket.moremiEquips ?? [], pickThemes: ticket.pickThemes ?? { ko: [], en: [] }, version: ticket.version ?? '' };
		wsUrl = ticket.websocketUrl;
		const shopReady = reconnecting && Object.keys(shop).length ? Promise.resolve() : loadShop().then((s) => (shop = s));
		// Never hold the loading screen hostage to a slow font/CDN.
		Promise.race([Promise.all([shopReady, document.fonts?.ready]), new Promise((r) => setTimeout(r, 8000))]).then(() => (state.assetsReady = true));
		state.guest = ticket.guest;
		setNudgeState({ signedIn: !ticket.guest, closed: ticket.nudgesClosed });
		setProgress({ signedIn: !ticket.guest, done: ticket.tutorialDone });

		const sock = (ws = new WebSocket(ticket.websocketUrl));
		masterHook = onMasterWelcome;
		sock.onmessage = (e) => handle(e.data, true);
		function onMasterWelcome(pkt) {
			if (pkt.type === 'welcome') {
				// Identity is a mandatory signal (the server drops the connection after 15 s without it). Collecting it can take a
				// moment (the audio probe), so each probe is cut off after 5 s and whatever is missing is sent as 'n/a'.
				security.start();
				security.sendInit();
				identity().then((report) => send('identity', report));
				startPing();
				state.outage = null;
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
			if (reconnectable) {
				noteOutage({ closeCode: e.code });
				scheduleReconnect();
			}
		};
	}

	const recorder = createRecorder();

	/** One path for packets from either socket: state, error toasts, 오끄감 notices, room handoff. */
	function handle(raw, fromMaster) {
		let pkt;
		try {
			pkt = JSON.parse(raw);
		} catch {
			return;
		}
		if (pkt.type === 'test') {
			// the server's challenges (heartbeat, last-word request): answered here, nothing for the game to show
			security.answerTest(pkt);
			if (pkt.reqType === 127) lastType = 'ack';
			return;
		}
		if (pkt.type === 'pong') state.pingMs = Math.round(performance.now() - sentAt);
		if (pkt.type === 'turnStart' && !fromMaster) telemetry.turnStart(pkt); // a new input trace context (room worker packets only)
		const before = pkt.type === 'okg' ? { playTime: state.playTime, count: state.okgCount } : null;
		const lastLine = state.chat.at(-1);
		const hadGame = !!state.game;
		applyPacket(state, pkt);
		// Broadcasts from "#notice", "#yell" and "#alert": a toast, the scrolling banner, and a dialog (to be confirmed)
		if (pkt.type === 'notice' && typeof pkt.value === 'string' && pkt.value) toast(plainText(pkt.value), { kind: 'info', icon: 'alert' });
		else if (pkt.type === 'yell' && typeof pkt.value === 'string') yell(plainText(pkt.value));
		else if (pkt.type === 'alert' && typeof pkt.value === 'string' && pkt.value) notify({ message: pkt.value });
		if (pkt.type === 'injeongHint' && state.game?.phase !== 'ended') showInjeongHint(state); // not in a result: now (otherwise when it is closed)
		if (recorder.feed(state, pkt)) state.replayReady = recorder.done; // a game for a .kkio file: from `starting` to its result
		// Legacy: whatever the server gave or changed (equipment, box), it then asks for a `refresh` so everyone sees the new look, unless it says `noref`.
		if (pkt.type === 'obtain' && !pkt.noref && fromMaster && state.status === 'ready') send('refresh');
		// Leaving (or losing) a game while its sounds are on: the turn clock's ticking and a quiz mode's music must not play on.
		if (hadGame && !state.game) {
			if (!recorder.done) (recorder.abandon(), (state.replayReady = false)); // a game that stopped without a result is not worth saving
			stopTurnSound();
			if (isResultBgm()) playLobbyBgm({ fromResult: true }); // the result is closed: its music crossfades into the lobby's
			else (stopBgm(), playLobbyBgm());
		}
		// Legacy: every chat line that is shown ticks (the "k" sound of the effect set, on the voice/UI channel).
		const line = state.chat.at(-1);
		if (pkt.type === 'chat' && line && line !== lastLine && !line.system && !isHidden(line, state, { ignoreSpectatorRule: true })) playSound('k', { ui: true });
		if (pkt.type === 'roomStuck') rws?.close(); // legacy: close the room connection
		gameAudio.onPacket(pkt);
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
			const a = state.notifications.filter((n) => n.kind !== 'REPORT_RESOLVED').length; // a report's result has its own dialog
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
		if (pkt.type === 'error' && Number(pkt.code) === 416 && state.status === 'ready') {
			// The room is in a game: the legacy client asks whether to go in and watch, then enters again as a spectator.
			confirm({ message: plainText(state.meta.labels['kkutu.js.error.416'] ?? '') }).then((yes) => yes && send('enter', { id: Number(pkt.target), password: enterPassword, spectate: true }));
		} else if (pkt.type === 'error' && state.status === 'ready') {
			// 630 is the answer to a nickname change that worked, 652 to a report that was received, 718 to a coupon that was used (the game reports results as codes)
			toast(errorText(state.meta.labels, pkt) || t('play.error.request', { code: pkt.code }), { kind: [630, 652, 718].includes(Number(pkt.code)) ? 'success' : 'error' });
		}
		if (fromMaster) {
			if (pkt.type === 'preRoom') connectRoom(pkt);
			masterHook?.(pkt);
		}
	}
	const gameAudio = createGameAudio(state);
	const stopTurnSound = gameAudio.stopTurnSound;
	let masterHook;
	const handled = new WeakSet(); // kick notices already shown
	const telemetry = createTelemetry({ send: (type, data) => send(type, data) }); // the answer field's input trace
	// what the server's heartbeat asks about, and the word it asks for when a `checker` did not match (legacy: lastType / lastRelay)
	let lastType = '';
	let lastRelay = '';
	const security = createSecurity({
		send: (type, data) => send(type, data, true),
		lastType: () => lastType,
		lastRelay: () => lastRelay,
		isGaming: () => !!state.rooms[state.myRoom]?.playing, // (the client's rooms say `playing`; `gaming` is the server's name)
		isAdmin: () => !!state.admin,
		debugContext: () => ({ roomId: Number(state.myRoom) || 0, masterState: ws?.readyState ?? -1, slaveState: rws?.readyState ?? -1 })
	});
	// legacy client-side flood guard: more than ten sends within a few seconds is a warning, three warnings close the connection
	let spamCount = 0;
	let spamWarning = 0;
	const spamDecay = setInterval(() => {
		if (spamCount > 0) spamCount = Math.max(0, spamCount - 3);
		else if (spamWarning > 0) spamWarning -= 0.03;
	}, 1000);
	function checkSpam(sock) {
		if (spamCount++ > 10) {
			if (++spamWarning >= 3) {
				sock.close();
				return false;
			}
			spamCount = 5;
		}
		return true;
	}
	const pageHide = (e) => rws && security.debug('pagehide', { persisted: !!e.persisted });
	const offline = () => rws && security.debug('offline');
	if (typeof window !== 'undefined') {
		window.addEventListener('pagehide', pageHide);
		window.addEventListener('offline', offline);
	}

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
	const MASTER_ONLY = new Set(['captcha', 'identity', 'enter', 'ping', 'refresh', 'refreshRoom', 'inviteRes', 'togglePrivacy', 'uid', 'newUid', 'friendAdd', 'friendAddRes', 'friendEdit', 'friendRemove', 'toggleBF', 'setStatus', 'setStatusMessage', 'mailBox', 'obtainMail', 'drawGacha', 'consume', 'notifications', 'notificationAck', 'report', 'reportRoom', 'reportChat', 'saveProgress', 'import', 'listSurvey', 'getSurveyToken', 'useCoupon', 'securityTelemetry']);
	const ROOM_ONLY = new Set(['leave', 'ready', 'start', 'practice', 'invite', 'form', 'team', 'kick', 'kickVote', 'handover', 'setAI', 'useItem', 'wp']);
	/** 22-char url-safe id (16 random bytes), required by the server on durable requests so a retry can't apply twice. */
	function newRequestId() {
		const bytes = crypto.getRandomValues(new Uint8Array(16));
		return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
	}
	const DURABLE = new Set(['saveProgress', 'import', 'newUid']);
	let enterPassword = ''; // what the last room entry sent: asking to watch a running game re-sends it (legacy 416)
	// observation traffic and the drawing stream do not use up the flood guard's budget
	const UNMETERED = new Set(['securityTelemetry', 'inputTrace', 'drawingCanvas', 'ping']);
	function send(type, data = {}, toMaster = false) {
		if (type === 'enter' && data.id !== undefined && !data.spectate) enterPassword = data.password ?? '';
		if (DURABLE.has(type) && !data.requestId) data = { ...data, requestId: newRequestId() };
		// Whispers go through the master (it knows everyone), like the other lobby-wide messages.
		const sock = toMaster || MASTER_ONLY.has(type) || (type === 'talk' && data.whisper) ? ws : ROOM_ONLY.has(type) ? rws : (rws ?? ws);
		if (sock?.readyState !== WebSocket.OPEN) return;
		if (!UNMETERED.has(type) && !checkSpam(sock)) return;
		lastType = type;
		if (type === 'talk' && data.relay) lastRelay = data.value;
		socketSend(sock, JSON.stringify({ ...data, type })); // through the browser's own send, so a hook on WebSocket.prototype.send is bypassed
	}
	const sendRoom = send; // kept for existing callers

	function connectRoom({ channel, id, handoffToken }) {
		rws?.close(1000, 'reconnect');
		const url = wsUrl.replace(/:(\d+)/, (_, port) => `:${Number(port) + 416 + Number(channel) - 1}`) + `&${channel}&${id}`;
		const sock = (rws = new WebSocket(url));
		sock.onopen = () => socketSend(sock, JSON.stringify({ type: 'handoff', token: handoffToken ?? state.preRoom?.token }));
		sock.onerror = () => security.debug('slave-error', { channel: Number(channel), targetRoomId: Number(id) });
		sock.onmessage = (e) => handle(e.data, false);
		sock.onclose = (e) => {
			security.debug('slave-close', { code: e.code, reason: e.reason || '', wasClean: !!e.wasClean, channel: Number(channel), targetRoomId: Number(id) });
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
		/** the answer field's input trace (see telemetry.js) */
		telemetry,
		/** the page-wide and field-level anti-cheat (see security/) */
		security,
		/** the recording of the last game (see replayRec.js) */
		recorder,
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
			security.stop();
			clearInterval(spamDecay);
			if (typeof window !== 'undefined') {
				window.removeEventListener('pagehide', pageHide);
				window.removeEventListener('offline', offline);
			}
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
