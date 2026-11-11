/**
 * When the game connection drops without the server saying why, the client reconnects by itself: a fresh ticket, a new
 * socket, then the lobby again. This file only holds the policy (pure functions, unit-tested); connection.svelte.js runs it.
 */
export const MAX_ATTEMPTS = 12; // about five minutes with the delays below
const DELAYS = [1, 2, 4, 8, 15, 30]; // seconds, the last one repeats

/** Close codes of a dropped network / restarting server (1006 abnormal, 1001 going away, 1011-1013 server trouble, 1015 TLS). */
const TRANSIENT = new Set([1001, 1006, 1011, 1012, 1013, 1015]);
/** The server explains these by an `error` packet and then closes on purpose: guests refused, so retrying cannot help. */
const REFUSED = new Set([449, 402, 479, 457, 440]);

/** Should a socket that closed with `closeCode` (and after a server error `errorCode`, if any) be reconnected? */
export function shouldReconnect(closeCode, errorCode) {
	return TRANSIENT.has(closeCode) && !REFUSED.has(errorCode);
}

/** Milliseconds to wait before attempt number `attempt` (0 = the first retry), +-20% so many clients don't return at once. */
export function retryDelay(attempt, random = Math.random()) {
	const base = DELAYS[Math.min(Math.max(attempt, 0), DELAYS.length - 1)] * 1000;
	return Math.round(base * (0.8 + random * 0.4));
}
