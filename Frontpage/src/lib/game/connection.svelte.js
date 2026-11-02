import { applyPacket, initialState } from './protocol.js';
import { loadShop } from './images.js';
import { OKG_MINUTES } from './lobbyModel.js';
import { toast } from './ui/toast.svelte.js';
import { i18n, loadLocale, t } from './i18n.svelte.js';

/** Server messages are HTML snippets (<br>, links); toasts show plain text. */
export const plainText = (html = '') => String(html).replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]*>/g, '').trim();

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
	const state = $state(initialState());
	let shop = $state({});
	let wsUrl = '';
	let rws;
	let ws;
	let pingTimer;
	let sentAt = 0;

	async function open() {
		state.status = 'connecting';
		state.error = null;
		await loadLocale(); // before anything that might need to show a translated message
		let ticket;
		try {
			const res = await fetch(`/api/play/${server}/ticket`, { cache: 'no-store' });
			ticket = await res.json();
			if (!res.ok) {
				state.status = 'error';
				state.error = { code: ticket.code, message: ticket.reason ?? '' };
				return;
			}
		} catch {
			state.status = 'error';
			state.error = { code: 'NETWORK', message: '' };
			return;
		}
		state.meta = { modes: ticket.modes, options: ticket.options, labels: i18n.messages, rules: ticket.rules, optionDefs: ticket.optionDefs, moremiParts: ticket.moremiParts };
		wsUrl = ticket.websocketUrl;
		const shopReady = loadShop().then((s) => (shop = s));
		// Never hold the loading screen hostage to a slow font/CDN.
		Promise.race([Promise.all([shopReady, document.fonts?.ready]), new Promise((r) => setTimeout(r, 8000))]).then(() => (state.assetsReady = true));
		state.guest = ticket.guest;

		ws = new WebSocket(ticket.websocketUrl);
		ws.onmessage = (e) => {
			let pkt;
			try {
				pkt = JSON.parse(e.data);
			} catch {
				return;
			}
			if (pkt.type === 'pong') state.pingMs = Math.round(performance.now() - sentAt);
			const before = pkt.type === 'okg' ? { playTime: state.playTime, count: state.okgCount } : null;
			applyPacket(state, pkt);
			if (before) okgNotice(before, pkt);
			if (pkt.type === 'error' && state.status === 'ready') {
				toast(plainText(state.meta.labels[`kkutu.js.error.${pkt.code}`] ?? pkt.message) || t('play.error.request', { code: pkt.code }), { kind: 'error' });
			}
			if (pkt.type === 'preRoom') connectRoom(pkt);
			if (pkt.type === 'welcome') {
				// Identity is a mandatory liveness signal (15s). We collect no fingerprint: the server's
				// schema forbids empty strings, and confidence 0 is explicitly a non-disconnecting value.
				const n = 'n/a';
				send('identity', { fingerPrint2: n, fingerjsId: n, mainfpId: n, audioId: n, webglId: n, canvasId: n, pcid: n, confidence: 0 });
				startPing();
			}
		};
		ws.onclose = (e) => {
			clearInterval(pingTimer);
			if (state.status !== 'error') {
				state.status = 'closed';
				state.error ??= { code: e.code, message: '' };
			}
		};
	}

	/** 오끄감 buff toasts, as in the legacy client: reset at midnight, or a new buff earned. */
	function okgNotice(before, pkt) {
		if (before.playTime > pkt.time) toast(t('play.okg.expired'), { kind: 'info' });
		else if (before.count !== pkt.count) {
			const minutes = OKG_MINUTES[state.membership] ?? OKG_MINUTES[0];
			toast(`${t('play.okg.gained', { minutes })} (${t('play.okg.stack', { count: pkt.count })})`, { kind: 'success', icon: 'xp' });
		}
	}

	function send(type, data = {}) {
		if (ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ ...data, type }));
	}

	// Room traffic lives on a per-channel worker socket: master hands us a one-time token.
	function sendRoom(type, data = {}) {
		if (rws?.readyState === WebSocket.OPEN) rws.send(JSON.stringify({ ...data, type }));
	}

	function connectRoom({ channel, id, handoffToken }) {
		rws?.close(1000, 'reconnect');
		const url = wsUrl.replace(/:(\d+)/, (_, port) => `:${Number(port) + 416 + Number(channel) - 1}`) + `&${channel}&${id}`;
		const sock = (rws = new WebSocket(url));
		sock.onopen = () => sock.send(JSON.stringify({ type: 'handoff', token: handoffToken ?? state.preRoom?.token }));
		sock.onmessage = (e) => {
			try {
				applyPacket(state, JSON.parse(e.data));
			} catch {}
		};
		sock.onclose = () => {
			if (rws === sock) rws = undefined;
			state.myRoom = null;
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
		close: () => {
			clearInterval(pingTimer);
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
