import katex from 'katex';

/**
 * 수학 연습: a question's text is plain text with formulas between `$ … $` (LaTeX), as the pool writes them. This makes it HTML: the text is escaped, a
 * formula is drawn by KaTeX (one that cannot be drawn is shown as it is, not as an error), and a line break is a line break.
 */
const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
const escape = (s) => s.replace(/[&<>"']/g, (c) => ESC[c]);

export function mathHtml(text) {
	if (!text) return '';
	return String(text)
		.split('$')
		.map((part, i) => {
			if (i % 2 === 0) return escape(part).replace(/\n/g, '<br>');
			try {
				return katex.renderToString(part, { throwOnError: false, strict: false, trust: false, output: 'html' });
			} catch {
				return escape(`$${part}$`);
			}
		})
		.join('');
}

/** A formula alone (a choice): the same drawing, no text around it. */
export const formulaHtml = (latex) => mathHtml(`$${latex}$`);

/** "1:23": minutes and seconds left. */
export function clock(ms) {
	const s = Math.max(0, Math.ceil(ms / 1000));
	return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

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
