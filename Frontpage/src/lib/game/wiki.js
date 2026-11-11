/**
 * 리오위키 (kkutu.wiki) in the game: which words have an article (and its picture), and the article itself.
 * Talks to MediaWiki's own Action API, which allows anonymous cross-origin reads (`origin=*`), so nothing else has to be deployed.
 *   localStorage `kkutu_wiki_origin` (dev override) > VITE_WIKI_ORIGIN > https://kkutu.wiki
 * Everything fails soft: wiki unreachable, no extras on the cards, the game carries on.
 */
const DEFAULT_ORIGIN = 'https://kkutu.wiki';

export function wikiOrigin() {
	let o;
	try {
		o = globalThis.localStorage?.getItem('kkutu_wiki_origin');
	} catch {
		/* storage blocked */
	}
	return String(o || import.meta.env?.VITE_WIKI_ORIGIN || DEFAULT_ORIGIN).replace(/\/$/, '');
}

const slug = (title) => title.replace(/ /g, '_').split('/').map(encodeURIComponent).join('/');
export const articleUrl = (title, origin = wikiOrigin()) => `${origin}/w/${slug(title)}`;

/** MediaWiki's own edit page for a title: for a missing article it is the page that starts writing it. */
export const editUrl = (title, origin = wikiOrigin()) => `${origin}/index.php?title=${encodeURIComponent(title.replace(/ /g, '_'))}&action=edit`;

async function api(params, fetchFn, origin = wikiOrigin()) {
	const q = new URLSearchParams({ format: 'json', formatversion: '2', origin: '*', ...params });
	const res = await fetchFn(`${origin}/api.php?${q}`);
	if (!res.ok) throw new Error(`wiki ${res.status}`);
	const json = await res.json();
	if (json.error) throw Object.assign(new Error(json.error.info), { code: json.error.code });
	return json;
}

/** @typedef {{title:string, exists:boolean, thumb:string|null}} WikiSummary */

const cache = new Map(); // `${origin}\n${title}` -> Promise<WikiSummary|null>
const pending = new Map(); // origin -> {titles: Map<title, resolve[]>, fetchFn}

/**
 * Whether the wiki has an article for a title, with its picture. Cards that ask within the same tick share one request
 * (50 titles at most), and every answer is kept for the session. Resolves to null when the wiki can't be reached (asked again next time).
 * @returns {Promise<WikiSummary|null>}
 */
export function wikiSummary(title, fetchFn = fetch) {
	const t = String(title ?? '').trim();
	if (!t) return Promise.resolve(null);
	const origin = wikiOrigin();
	const key = `${origin}\n${t}`;
	if (!cache.has(key)) {
		const p = new Promise((resolve) => queue(origin, t, resolve, fetchFn));
		cache.set(key, p);
		p.then((v) => v === null && cache.delete(key));
	}
	return cache.get(key);
}

function queue(origin, title, resolve, fetchFn) {
	let batch = pending.get(origin);
	if (!batch) {
		batch = { titles: new Map(), fetchFn };
		pending.set(origin, batch);
		queueMicrotask(() => setTimeout(() => flush(origin), 0));
	}
	batch.titles.set(title, [...(batch.titles.get(title) ?? []), resolve]);
}

async function flush(origin) {
	const batch = pending.get(origin);
	pending.delete(origin);
	if (!batch) return;
	const all = [...batch.titles.keys()];
	for (let i = 0; i < all.length; i += 50) {
		const part = all.slice(i, i + 50);
		/** @type {Record<string, WikiSummary>|null} */
		let out = null;
		try {
			const q = (await api({ action: 'query', titles: part.join('|'), redirects: '1', prop: 'pageimages', piprop: 'thumbnail', pithumbsize: '300' }, batch.fetchFn, origin)).query ?? {};
			const hop = (l) => new Map((l ?? []).map((x) => [x.from, x.to]));
			const normalized = hop(q.normalized);
			const redirects = hop(q.redirects);
			const byTitle = new Map((q.pages ?? []).map((p) => [p.title, p]));
			out = {};
			for (const t of part) {
				const n = normalized.get(t) ?? t;
				const title = redirects.get(n) ?? n;
				const p = byTitle.get(title);
				const exists = !!p && !p.missing && !p.invalid;
				out[t] = { title, exists, thumb: exists ? (p.thumbnail?.source ?? null) : null };
			}
		} catch {
			/* offline / blocked: leave null */
		}
		for (const t of part) for (const done of batch.titles.get(t)) done(out?.[t] ?? null);
	}
}

const plain = (html) => new DOMParser().parseFromString(String(html), 'text/html').body.textContent.trim();

/**
 * The start of an article as plain text, for a summary. Articles here often open with a section heading instead of an intro, so heading
 * lines are skipped and the text is cut at a word boundary.
 * @returns {Promise<{title:string, exists:boolean, extract:string, thumb:string|null}|null>} null when the wiki can't be reached
 */
export async function wikiIntro(title, chars = 320, fetchFn = fetch) {
	try {
		const q = (await api({ action: 'query', titles: title, redirects: '1', prop: 'extracts|pageimages', explaintext: '1', exchars: '1200', exlimit: '1', piprop: 'thumbnail', pithumbsize: '400' }, fetchFn)).query ?? {};
		const p = q.pages?.[0];
		if (!p || p.missing || p.invalid) return { title, exists: false, extract: '', thumb: null };
		const text = String(p.extract ?? '').replace(/^=+[^\n]*=+[ \t]*$/gm, ' ').replace(/\s+/g, ' ').trim();
		const cut = text.length > chars ? text.slice(0, Math.max(text.slice(0, chars).lastIndexOf(' '), chars * 0.6)).trimEnd() + '…' : text;
		return { title: p.title, exists: true, extract: cut, thumb: p.thumbnail?.source ?? null };
	} catch {
		return null;
	}
}

/**
 * The rendered article (MediaWiki's own parser output, still to be sanitised by wikiHtml.js), following redirects.
 * `sections` is the table of contents: `{number:'1.2', text, level, anchor}` for each heading (not those from templates).
 * @returns {Promise<{title:string, html:string, sections:{number:string,text:string,level:number,anchor:string}[], redirectedFrom?:string}|null>} null when there is no such article
 * @throws when the wiki can't be reached
 */
export async function loadArticle(title, fetchFn = fetch) {
	try {
		const j = await api({ action: 'parse', page: title, redirects: '1', prop: 'text|displaytitle|sections', disabletoc: '1', disablelimitreport: '1', disableeditsection: '1' }, fetchFn);
		const p = j.parse;
		const sections = (p.sections ?? [])
			.filter((x) => x.index && !String(x.index).startsWith('T-'))
			.map((x) => ({ number: x.number, text: plain(x.line), level: x.toclevel, anchor: x.anchor }));
		return { title: p.title, html: p.text, sections, redirectedFrom: p.redirects?.[0]?.from };
	} catch (e) {
		if (['missingtitle', 'invalidtitle', 'pagecannotexist'].includes(e.code)) return null;
		throw e;
	}
}

/** Test hook. */
export const _reset = () => {
	cache.clear();
	pending.clear();
};
