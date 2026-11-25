/**
 * Release-bundle only: blocks F12, the right-click menu, and Ctrl/Cmd+Shift+I/J/C, as the legacy client's `security.js` did.
 * Stops no bot (devtools can be opened a dozen other ways - undocking a panel, remote debugging, a different browser build) and is
 * a minor accessibility/UX cost for everyone else, but the request is to match the legacy client's posture on the release bundle;
 * the development bundle (identity.js §10A, `__PLAY_BUNDLE__ !== 'release'`) must keep devtools usable, so callers gate on that.
 *
 * @returns {() => void} removes the listeners
 */
export function blockDevtools(doc = document) {
	const win = doc.defaultView ?? globalThis;
	const onContextMenu = (e) => e.preventDefault();
	const onKeyDown = (e) => {
		if (e.key === 'F12') return e.preventDefault();
		if (!(e.ctrlKey || e.metaKey)) return;
		if (!(e.altKey || e.shiftKey)) return;
		if (['I', 'J', 'C'].includes(String(e.key).toUpperCase())) e.preventDefault();
	};
	win.addEventListener('contextmenu', onContextMenu);
	doc.addEventListener('keydown', onKeyDown, true);
	return () => {
		win.removeEventListener('contextmenu', onContextMenu);
		doc.removeEventListener('keydown', onKeyDown, true);
	};
}
