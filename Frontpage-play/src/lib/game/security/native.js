/**
 * Browser functions the anti-cheat relies on, captured when this module loads (before any later script can wrap them), plus
 * the checks that tell whether they are still the browser's own. Everything that talks to the server or reads the answer
 * field goes through these references, so a hook installed afterwards neither sees nor changes it.
 */
const win = typeof window === 'undefined' ? globalThis : window;

export const NativeWebSocket = win.WebSocket;
export const nativeSend = NativeWebSocket?.prototype?.send;
export const nativeFetch = win.fetch;
export const nativeAttachShadow = typeof Element === 'undefined' ? undefined : Element.prototype.attachShadow;
export const nativeSetTimeout = win.setTimeout?.bind(win);
const valueDescriptor = typeof HTMLInputElement === 'undefined' ? undefined : Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
const offsetWidthOf = typeof HTMLElement === 'undefined' ? undefined : Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth')?.get;
const offsetHeightOf = typeof HTMLElement === 'undefined' ? undefined : Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight')?.get;

/** Function.prototype.toString of a fresh frame: the page cannot have replaced that one. */
let cleanToString;
function toStringOf() {
	if (cleanToString) return cleanToString;
	try {
		const frame = document.createElement('iframe');
		frame.style.display = 'none';
		(document.body || document.documentElement).appendChild(frame);
		cleanToString = frame.contentWindow.Function.prototype.toString;
		frame.remove();
	} catch {
		cleanToString = Function.prototype.toString;
	}
	return cleanToString;
}

/** True for a function the browser itself implements (not a wrapper written in JavaScript). */
export function isNative(fn) {
	try {
		return /^[^{]+\{\s*\[native code\]\s*\}$/.test(toStringOf().call(fn));
	} catch {
		return false;
	}
}

/** The field's text as the browser holds it, whatever an instance-level `value` property says. */
export function readValue(el) {
	try {
		if (valueDescriptor?.get) return String(valueDescriptor.get.call(el) ?? '');
	} catch {
		/* fall through */
	}
	return String(el?.value ?? '');
}

export function writeValue(el, value) {
	try {
		if (valueDescriptor?.set) return void valueDescriptor.set.call(el, String(value ?? ''));
	} catch {
		/* fall through */
	}
	el.value = String(value ?? '');
}

export const nativeValueSetter = valueDescriptor?.set;

/**
 * The element's layout size by the browser's own getters (an element can fake its own `offsetWidth`; the getters on the prototype
 * cannot be faked per element). Layout size, not the drawn size: the game stage is scaled with a transform, which would make a
 * 1 px decoy measure larger than 1 px.
 */
export function sizeOf(el) {
	try {
		return { width: offsetWidthOf.call(el), height: offsetHeightOf.call(el) };
	} catch {
		return { width: 0, height: 0 };
	}
}

/** Send on a socket through the captured `send` (a hook on `WebSocket.prototype.send` is bypassed). */
export function socketSend(sock, data) {
	if (nativeSend && NativeWebSocket && sock instanceof NativeWebSocket) nativeSend.call(sock, data);
	else sock.send(data);
}

export const defaultNative = { NativeWebSocket, nativeSend, nativeFetch, nativeAttachShadow, isNative, readValue, writeValue, sizeOf, socketSend };
