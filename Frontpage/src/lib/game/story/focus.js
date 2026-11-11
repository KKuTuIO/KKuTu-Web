/** The first element the selector finds that is on screen (has a size). A page holds both the desktop's and the phone's part under one `data-tour` mark; only one of them shows. */
export function visibleEl(selector) {
	if (!selector) return null;
	for (const el of document.querySelectorAll(selector)) {
		const r = el.getBoundingClientRect();
		if (r.width > 0 && r.height > 0) return el;
	}
	return null;
}
