/**
 * While a game is running, anything that takes the player off the page ends their game (and lowers their rating).
 * `guard.active` is switched on by the play page; links, the browser's own unload and programmatic navigation
 * (`confirmLeave()`) all ask first, with the legacy client's warning.
 */
import { t } from './i18n.svelte.js';
import { confirm } from './ui/confirm.svelte.js';

export const guard = $state({ active: false });

/** Resolves true when navigating away is fine: no game running, or the player agreed. */
export async function confirmLeave() {
	if (!guard.active) return true;
	return confirm({ message: t('play.leave.playing'), ok: t('play.leave.go'), cancel: t('play.leave.stay') });
}

/** Document-level click handler (capture phase): a link that would leave the game page is held back and asked about. */
export function linkGuard(e) {
	if (!guard.active || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey) return;
	const a = e.target instanceof Element ? e.target.closest('a[href]') : null;
	if (!a || a.hasAttribute('data-own-guard')) return;
	const url = new URL(a.href, location.href);
	if (url.origin === location.origin && url.pathname === location.pathname && url.search === location.search) return; // same page (#room)
	e.preventDefault();
	e.stopPropagation();
	const external = url.origin !== location.origin;
	const target = a.target;
	confirm({ message: t(external ? 'play.leave.external' : 'play.leave.playing'), ok: t('play.leave.go'), cancel: t('play.leave.stay') }).then((yes) => {
		if (!yes) return;
		if (target === '_blank') window.open(url.href, '_blank', 'noopener');
		else location.href = url.href;
	});
}

/** Closing or reloading the tab: the browser's own confirmation (it shows its own text). */
export function unloadGuard(e) {
	if (!guard.active) return;
	e.preventDefault();
	e.returnValue = '';
}
