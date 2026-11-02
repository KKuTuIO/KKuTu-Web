/** Global toast queue. Call `toast(...)` from anywhere; <ToastHost/> renders it. */
export const toasts = $state([]);
const timers = new Map();
const left = new Map(); // id -> { start, remaining } so hover pauses the countdown instead of resetting it
let seq = 0;

export function dismiss(id) {
	clearTimeout(timers.get(id));
	timers.delete(id);
	left.delete(id);
	const i = toasts.findIndex((t) => t.id === id);
	if (i >= 0) toasts.splice(i, 1);
}

function arm(id, ms) {
	clearTimeout(timers.get(id));
	if (ms <= 0) return;
	left.set(id, { start: Date.now(), remaining: ms });
	timers.set(id, setTimeout(() => dismiss(id), ms));
}

/**
 * @param {string} message
 * @param {{kind?:'info'|'success'|'error'|'warn', icon?:'alert'|'event'|'info'|'lv'|'mail'|'maintenance'|'xp', duration?:number}} [o]
 *   `icon` overrides the icon implied by `kind` (the legacy client's toast icon set).
 */
export function toast(message, { kind = 'info', icon, duration = 3800 } = {}) {
	const id = ++seq;
	toasts.push({ id, message, kind, icon, duration });
	if (toasts.length > 4) dismiss(toasts[0].id);
	arm(id, duration);
	return id;
}

/** Hovering or focusing a toast freezes its countdown; leaving continues where it stopped. */
export function pause(id) {
	const l = left.get(id);
	if (!l || !timers.has(id)) return;
	clearTimeout(timers.get(id));
	timers.delete(id);
	l.remaining -= Date.now() - l.start;
}
export function resume(id) {
	const l = left.get(id);
	if (l && !timers.has(id)) arm(id, Math.max(l.remaining, 800));
}
