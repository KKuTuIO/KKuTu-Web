import katex from 'katex';

/**
 * 수학 연습: a question's text is plain text with formulas between `$ … $` (LaTeX), as the pool writes them. This makes it HTML: the text is escaped, a
 * formula is drawn by KaTeX (one that cannot be drawn is shown as it is, not as an error), and a line break is a line break.
 */
const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
const escape = (s) => s.replace(/[&<>"']/g, (c) => ESC[c]);

/** One line of text with its formulas drawn. */
function inline(line) {
	return line
		.split('$')
		.map((part, i) => {
			if (i % 2 === 0) return escape(part);
			try {
				return katex.renderToString(part, { throwOnError: false, strict: false, trust: false, output: 'html' });
			} catch {
				return escape(`$${part}$`);
			}
		})
		.join('');
}

/** A text with formulas as HTML. A line that begins with `> ` is a box (the <보기> of a question: lines that stand together); the other lines break as they are. */
export function mathHtml(text) {
	if (!text) return '';
	const items = [];
	let quote = [];
	const flush = () => {
		if (quote.length) items.push({ html: `<div class="mq">${quote.join('<br>')}</div>`, block: true });
		quote = [];
	};
	for (const line of String(text).split('\n')) {
		if (line.startsWith('> ') || line === '>') quote.push(inline(line.slice(2)));
		else {
			flush();
			items.push({ html: inline(line), block: false });
		}
	}
	flush();
	return items.map((it, i) => (i > 0 && !it.block && !items[i - 1].block ? '<br>' : '') + it.html).join('');
}

/**
 * The text of a question with its points set right after the question, as an exam sets them ("… 값은? [3점]"): at the end of the text, or, when a <보기> box
 * (lines that begin with `> `) follows the question, at the end of the last line before it.
 */
export function mathHtmlPts(text, points) {
	if (!text) return '';
	const lines = String(text ?? '').split('\n');
	const box = lines.findIndex((l) => l.startsWith('> ') || l === '>');
	let at = (box < 0 ? lines.length : box) - 1;
	while (at > 0 && lines[at].trim() === '') at--;
	if (at < 0) return mathHtml(text);
	lines[at] += ' @@PTS@@';
	return mathHtml(lines.join('\n')).replace('@@PTS@@', `<span class="pts">[${Number(points) || 0}점]</span>`);
}

/** A formula alone (a choice): the same drawing, no text around it. */
/** A choice that is made of words ('ㄱ, ㄴ') is shown as text; the others are drawn as formulas. */
export const formulaHtml = (latex) => (/[가-힣ㄱ-ㅎ]/.test(latex) ? escape(latex) : mathHtml(`$${latex}$`));

/** "1:23": minutes and seconds left. */
export function clock(ms) {
	const s = Math.max(0, Math.ceil(ms / 1000));
	return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** The number of a question, as an exam sets it: bold, in the LaTeX face ("1."). */
export const numberHtml = (n) => katex.renderToString(`\\mathbf{${Number(n) || 0}.}`, { throwOnError: false, strict: false, output: 'html' });

export const CIRCLED = ['①', '②', '③', '④', '⑤'];

/** The locale key of the name of each level, and of each reason a send did not count. */
export const LEVEL_KEYS = { e1: 'play.math.level.e1', e2: 'play.math.level.e2', m: 'play.math.level.m', h1: 'play.math.level.h1', h23: 'play.math.level.h23' };
export const ERROR_KEYS = {
	level: 'play.math.error.level',
	cap: 'play.math.error.cap',
	none: 'play.math.error.none',
	over: 'play.math.error.over',
	fast: 'play.math.error.fast',
	late: 'play.math.error.late'
};
