/**
 * A dictionary meaning laid out like the legacy client's `processWord`: a meaning is "＂1＂ … ［1］ … （1） …" (up to three
 * levels of numbering), each part with the theme it belongs to. This returns the same tree the legacy code builds as DOM.
 */
const THEME_SPECIAL = ['ODW', 'KKT', 'DDW'];
const THEME_FLAGS = ['HBW', 'SBW', 'EMW'];

const escapeHtml = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Markdown-ish marks in meanings: $$…$$ equations, **sup**, *sub*. (Text is escaped first: only these tags come out.) */
export function formMean(v) {
	return escapeHtml(v)
		.replace(/\$\$[^$]+\$\$/g, (item) => {
			const txt = item
				.slice(2, item.length - 2)
				.replace(/\^\{([^}]+)\}/g, '<sup>$1</sup>')
				.replace(/_\{([^}]+)\}/g, '<sub>$1</sub>')
				.replace(/\\geq/g, '≥');
			return `<equ>${txt}</equ>`;
		})
		.replace(/\*\*([^*]+)\*\*/g, '<sup>$1</sup>')
		.replace(/\*([^*]+)\*/g, '<sub>$1</sub>');
}

/**
 * @param {string} mean
 * @param {string} theme  comma separated theme codes
 * @param {Record<string,string>} labels  `word.theme.*` names
 * @returns {{plain: string} | {numbered: boolean, groups: {index: number, head: number|null, subs: {head: number|null, items: {head: number|null, theme: {name: string, kind: 'plain'|'special'|'injeong'}|null, html: string}[]}[]}[]}}
 */
export function processWord(mean, theme, labels) {
	if (!mean || mean.indexOf('＂') === -1) return { plain: mean ?? '' };
	const means = mean.split(/＂[0-9]+＂/).slice(1).map((m1) => (m1.indexOf('［') === -1 ? [[m1]] : m1.split(/［[0-9]+］/).slice(1).map((m2) => (m2.indexOf('（') === -1 ? [m2] : m2.split(/（[0-9]+）/).slice(1)))));
	const themes = theme ? theme.split(',') : [];
	const numbered = themes.filter((v) => !THEME_FLAGS.includes(v)).length > 1;
	const groups = [];
	for (const [x1, m1] of means.entries()) {
		const subs = [];
		let isFlag = false;
		const m1s = m1.length > 1;
		for (const [x2, m2] of m1.entries()) {
			const tl = themes.splice(0, m2.length);
			const items = [];
			const m2s = m2.length > 1;
			if (!isFlag) {
				for (const [x3, m3] of m2.entries()) {
					const t = tl.shift();
					if (THEME_FLAGS.includes(t)) {
						isFlag = true;
						continue;
					}
					let th = null;
					if (t && `word.theme.${t}` in labels) {
						const name = labels[`word.theme.${t}`];
						th = { name, kind: /^e?\d+$/.test(t) ? 'plain' : THEME_SPECIAL.includes(t) ? 'special' : 'injeong' };
					}
					items.push({ head: m2s ? x3 + 1 : null, theme: th, html: formMean(m3) });
				}
			}
			if (isFlag) continue;
			subs.push({ head: m1s ? x2 + 1 : null, items });
		}
		if (isFlag) continue;
		groups.push({ index: x1, head: numbered ? x1 + 1 : null, subs });
	}
	return { numbered, groups };
}
