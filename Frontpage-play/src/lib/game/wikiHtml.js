/**
 * MediaWiki's parser output -> something safe to show inside the game page.
 * The wiki is edited by its users, and the HTML lands in our page, so it is rebuilt through an inert DOM: no scripts, frames or forms,
 * no event handlers, no `javascript:` addresses, and inline styles can't take elements out of the flow or load things.
 * Links to other articles are marked (`data-wiki`) so the reader can open them in place; every other link opens in a new tab (the reader asks first when it leaves the wiki).
 */
const DROP = 'script,style,iframe,frame,object,embed,form,input,button,select,textarea,link,meta,base,template,audio,video,source,track,canvas';
const BAD_STYLE = /position|z-index|url\s*\(|expression|behavior|binding|@import|\\/i;

function cleanStyle(style) {
	return style
		.split(';')
		.filter((d) => d.trim() && !BAD_STYLE.test(d))
		.join(';');
}

/** The wiki's element ids are prefixed, so they can never collide with the ids of the game page around them. */
export const ID_PREFIX = 'wk-';

/** @param {string} html @param {string} origin the wiki, e.g. https://kkutu.wiki */
export function sanitizeWikiHtml(html, origin) {
	const doc = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
	doc.body.querySelectorAll(DROP).forEach((el) => el.remove());
	for (const el of doc.body.querySelectorAll('*')) {
		for (const a of [...el.attributes]) {
			const n = a.name.toLowerCase();
			if (n.startsWith('on') || n === 'srcdoc' || n === 'formaction' || n === 'target') el.removeAttribute(a.name);
			else if (n === 'id') el.setAttribute('id', ID_PREFIX + a.value);
			else if (n === 'name') el.removeAttribute('name');
			else if (n === 'style') {
				const s = cleanStyle(a.value);
				if (s) el.setAttribute('style', s);
				else el.removeAttribute('style');
			}
		}
		if (el.tagName === 'A') fixLink(el, origin);
		else if (el.tagName === 'IMG') fixImage(el, origin);
	}
	// A picture's link only leads to its file page: leave the picture, drop the link.
	for (const a of doc.body.querySelectorAll('a.mw-file-description')) a.replaceWith(...a.childNodes);
	return doc.body.innerHTML;
}

function resolve(value, origin) {
	try {
		return new URL(value, origin + '/');
	} catch {
		return null;
	}
}

function fixLink(a, origin) {
	const href = a.getAttribute('href');
	if (href === null) return;
	if (href.startsWith('#')) return a.setAttribute('href', '#' + ID_PREFIX + href.slice(1)); // footnotes and headings: scrolled to inside the reader
	const u = resolve(href, origin);
	if (!u || !/^https?:$/.test(u.protocol)) {
		a.removeAttribute('href');
		return;
	}
	const m = u.origin === origin && /^\/w\/([^?#]+)$/.exec(u.pathname);
	const isRed = a.classList.contains('new');
	const target = m ? decodeURIComponent(m[1]).replace(/_/g, ' ') : '';
	// Only plain articles open in place; namespaced pages (파일:, 분류:, 특수: ...) go to the wiki itself.
	if (m && !u.search && !isRed && !target.includes(':')) {
		a.setAttribute('data-wiki', target);
		a.setAttribute('href', u.href);
		return;
	}
	a.setAttribute('href', u.href);
	a.setAttribute('target', '_blank');
	a.setAttribute('rel', 'noopener noreferrer');
}

function fixImage(img, origin) {
	for (const attr of ['src', 'srcset']) {
		const v = img.getAttribute(attr);
		if (v === null) continue;
		const parts = v.split(',').map((p) => p.trim().split(/\s+/));
		const ok = parts.every(([u]) => {
			const r = resolve(u, origin);
			return r && r.protocol === 'https:';
		});
		if (!ok) {
			img.remove();
			return;
		}
		img.setAttribute(attr, parts.map(([u, d]) => resolve(u, origin).href + (d ? ' ' + d : '')).join(', '));
	}
	img.setAttribute('loading', 'lazy');
	img.setAttribute('referrerpolicy', 'no-referrer');
}
