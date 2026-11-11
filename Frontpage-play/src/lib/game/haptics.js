/**
 * A short tap under the finger. Android browsers have `navigator.vibrate`; iOS Safari has none, but (17.4+) toggling a
 * `<input type="checkbox" switch>` by its label ticks the Taptic Engine, so that is used there. Both only work inside a
 * user gesture, and neither throws anywhere else: no haptics is not an error.
 */
let label;
function iosTick() {
	if (!label) {
		const input = document.createElement('input');
		input.type = 'checkbox';
		input.setAttribute('switch', '');
		input.id = 'kk-haptic';
		label = document.createElement('label');
		label.htmlFor = input.id;
		for (const el of [input, label]) el.style.cssText = 'position:fixed;left:-9999px;top:-9999px;opacity:0;pointer-events:none';
		label.setAttribute('aria-hidden', 'true');
		input.tabIndex = label.tabIndex = -1;
		document.body.append(input, label);
	}
	label.click();
}
export function haptic(ms = 10) {
	try {
		if (typeof navigator === 'undefined') return;
		if (typeof navigator.vibrate === 'function') navigator.vibrate(ms);
		else iosTick();
	} catch {
		/* no haptics */
	}
}
