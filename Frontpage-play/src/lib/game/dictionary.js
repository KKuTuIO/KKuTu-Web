/**
 * Dictionary lookup (사전), ported from the legacy client: same endpoint (`/dictionary/{lang}/{word}`),
 * same meaning format. Pure functions so they can be tested without a DOM.
 */

// Theme codes that are rule flags, not shown; and "special" ones that get their own badge colour.
const FLAGS = ['HBW', 'SBW', 'EMW'];
const SPECIAL = ['ODW', 'KKT', 'DDW'];

/** Strip characters the dictionary can't hold and pick the language from the script. */
export function normalizeQuery(text) {
	let q = String(text ?? '').replace(/[^\sa-zA-Zㄱ-ㅎ0-9가-힣]/g, '');
	const lang = /[ㄱ-ㅎ가-힣]/.test(q) ? 'ko' : 'en';
	if (lang === 'en') q = q.toLowerCase();
	return { q, lang };
}

/** @returns {Promise<{word:string,mean:string,theme?:string,type?:string}|{error:number}>} */
export async function lookupWord(text, fetchFn = fetch) {
	const { q, lang } = normalizeQuery(text);
	if (!q.length) return { error: 404 };
	try {
		const res = await fetchFn(`/dictionary/${lang}/${encodeURIComponent(q)}`);
		const raw = await res.text();
		// The server sometimes sends lone backslashes (LaTeX-ish meanings); the legacy client repaired them the same way.
		const repaired = raw
			.replace(/\\(?![/u"bfnrt\\])/g, '\\\\')
			// Some meanings contain raw line breaks inside the JSON string, which JSON.parse rejects.
			.replace(/[\u0000-\u001f]/g, (c) => ({ '\n': '\\n', '\r': '\\r', '\t': '\\t' })[c] ?? '');
		return JSON.parse(repaired);
	} catch {
		return { error: 404 };
	}
}

/**
 * Meaning text -> segments. `$$x^{2}$$` is an equation, `**x**` superscript, `*x*` subscript.
 * @returns {{kind:'text'|'sup'|'sub', text:string, equ?:boolean}[]}
 */
export function formatMean(text) {
	const out = [];
	const inline = (s, equ = false) => {
		const re = /\*\*([^*]+)\*\*|\*([^*]+)\*/g;
		let last = 0;
		for (let m; (m = re.exec(s)); ) {
			if (m.index > last) out.push({ kind: 'text', text: s.slice(last, m.index), equ });
			out.push(m[1] !== undefined ? { kind: 'sup', text: m[1], equ } : { kind: 'sub', text: m[2], equ });
			last = m.index + m[0].length;
		}
		if (last < s.length) out.push({ kind: 'text', text: s.slice(last), equ });
	};
	const equation = (s) => {
		const re = /\^\{([^}]+)\}|_\{([^}]+)\}/g;
		const src = s.replace(/\\geq/g, '≥');
		let last = 0;
		for (let m; (m = re.exec(src)); ) {
			if (m.index > last) out.push({ kind: 'text', text: src.slice(last, m.index), equ: true });
			out.push(m[1] !== undefined ? { kind: 'sup', text: m[1], equ: true } : { kind: 'sub', text: m[2], equ: true });
			last = m.index + m[0].length;
		}
		if (last < src.length) out.push({ kind: 'text', text: src.slice(last), equ: true });
	};
	let last = 0;
	const re = /\$\$([^$]+)\$\$/g;
	const s = String(text ?? '');
	for (let m; (m = re.exec(s)); ) {
		if (m.index > last) inline(s.slice(last, m.index));
		equation(m[1]);
		last = m.index + m[0].length;
	}
	if (last < s.length) inline(s.slice(last));
	return out;
}

/** What kind of badge a theme code gets: a normal topic, a special source, or a rule-set ("인정") word. */
export function themeKind(code) {
	if (/^e?\d+$/.test(code)) return 'topic';
	return SPECIAL.includes(code) ? 'special' : 'injeong';
}

/**
 * Parses the legacy meaning format into senses.
 *   ＂1＂ ... ［1］ ... （1） ...   = sense > sub-sense > item (each level optionally numbered)
 * Themes are a flat comma list consumed in the same order as the items; a flag theme ends the entry.
 * @param {string} mean
 * @param {string} [themes] comma separated theme codes
 * @param {(code:string)=>string|undefined} [themeLabel]
 * @returns {{plain?:string, senses:{n:number|null, groups:{n:number|null, items:{n:number|null, theme:{label:string,kind:string}|null, text:string}[]}[]}[]}}
 */
export function parseMean(mean, themes = '', themeLabel = () => undefined) {
	if (!mean || !mean.includes('＂')) return { plain: mean ?? '', senses: [] };
	const means = mean
		.split(/＂[0-9]+＂/)
		.slice(1)
		.map((m1) =>
			!m1.includes('［')
				? [[m1]]
				: m1
						.split(/［[0-9]+］/)
						.slice(1)
						.map((m2) => (!m2.includes('（') ? [m2] : m2.split(/（[0-9]+）/).slice(1)))
		);
	const queue = themes ? themes.split(',') : [];
	const numberSenses = queue.filter((v) => !FLAGS.includes(v)).length > 1;
	const senses = [];
	let flagged = false;
	means.forEach((m1, x1) => {
		if (flagged) return;
		const groups = [];
		m1.forEach((m2, x2) => {
			if (flagged) return;
			const take = queue.splice(0, m2.length);
			const items = [];
			m2.forEach((m3, x3) => {
				if (flagged) return;
				const code = take.shift();
				if (FLAGS.includes(code)) {
					flagged = true;
					return;
				}
				const label = code ? themeLabel(code) : undefined;
				items.push({ n: m2.length > 1 ? x3 + 1 : null, theme: label ? { label, kind: themeKind(code) } : null, text: m3 });
			});
			if (!flagged) groups.push({ n: m1.length > 1 ? x2 + 1 : null, items });
		});
		if (!flagged) senses.push({ n: numberSenses ? x1 + 1 : null, groups });
	});
	return { senses };
}
