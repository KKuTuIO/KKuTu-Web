/**
 * A Svelte action that puts an answer field inside a CLOSED shadow root.
 *
 * Why: `document.querySelector('input')`, `querySelectorAll`, `getElementById`, `elementFromPoint` and a MutationObserver on the
 * document cannot see into a closed shadow root. A script that looks for "the input" in the page finds only the decoys around it
 * (see security/fieldGuard.js), and typing or setting a value on those is what gives it away. To get at the real field it has to
 * replace `Element.prototype.attachShadow` before this page loads, which security/environment.js notices (Bengal, modified 2); and a
 * root that was forced open is noticed right here.
 *
 * Not a lock: someone with enough control of the browser can still reach it. It takes away the cheap way in.
 *
 * Tests run with an OPEN root so that they can reach the field; nothing else turns this off (no switch an attacker could flip first).
 *
 * Use:  <div use:shadowField={{ value, variant, placeholder, label, onkeydown, ..., watch, onready, ontamper }}></div>
 * `value` must be an accessor pair (get/set) so the host and the field share one variable.
 */
import { mount, unmount } from 'svelte';
import AnswerInput from './lobby/AnswerInput.svelte';

const MODE = import.meta.env?.MODE === 'test' ? 'open' : 'closed';

export function shadowField(host, initial) {
	let params = initial;
	const root = host.attachShadow({ mode: MODE, delegatesFocus: true });
	// asked for a closed root and it came back open: something replaced attachShadow
	if (MODE === 'closed' && host.shadowRoot) params.ontamper?.();
	const app = mount(AnswerInput, {
		target: root,
		props: {
			get value() {
				return params.value;
			},
			set value(v) {
				params.value = v;
			},
			get variant() {
				return params.variant;
			},
			get placeholder() {
				return params.placeholder;
			},
			get label() {
				return params.label;
			},
			onkeydown: (e) => params.onkeydown?.(e),
			oninput: (e) => params.oninput?.(e),
			oncompositionend: (e) => params.oncompositionend?.(e),
			onblur: (e) => params.onblur?.(e),
			attach: (node) => {
				params.onready?.(node, host);
				return params.watch?.(node, { anchor: host, root });
			}
		}
	});
	return {
		update(next) {
			params = next;
		},
		destroy() {
			unmount(app);
		}
	};
}
