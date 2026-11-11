/**
 * A small, safe Markdown for the 소개 (bio): the parts people actually use, parsed into a tree the page draws itself (no HTML is ever
 * produced from the text, so nothing in it can inject markup). Raw HTML stays plain text; images are not loaded (an image becomes a link
 * with its description); only http(s) links are links.
 *
 * Blocks: paragraphs, # headings, - / 1. lists, > quotes, ``` code blocks, --- rules. Inline: **bold**, *italic* / _italic_,
 * ~~strike~~, `code`, [text](url), bare https:// addresses, \ escapes; a line break stays a line break.
 */
export const MAX_SOURCE = 2000; // what is parsed at all (the bio itself is limited by the server, lower)
const MAX_DEPTH = 4;

/** An address that may be a link: http(s) only, no spaces, not absurdly long. */
export function safeUrl(raw) {
	const u = String(raw ?? '').trim();
	if (!/^https?:\/\/[^\s<>"']+$/i.test(u) || u.length > 2048) return null;
	try {
		return new URL(u).href;
	} catch {
		return null;
	}
}

// a closing mark for the mark opened at `from`: not preceded by a space (and, for `_`, at a word edge)
function closing(s, from, mark) {
	let i = from;
	while ((i = s.indexOf(mark, i)) >= 0) {
		const before = s[i - 1];
		if (s[i - 1] === '\\') {
			i++;
			continue;
		}
		if (i === from || /\s/.test(before ?? '')) return -1; // empty, or space before the closing mark: not emphasis
		if (mark.length === 1 && s[i + 1] === mark) {
			i += 2; // part of a longer run (** inside *)
			continue;
		}
		if (mark === '_' && /\w/.test(s[i + 1] ?? '')) {
			i++;
			continue;
		}
		return i;
	}
	return -1;
}

/** Inline text -> nodes: {t:'text',v} {t:'br'} {t:'code',v} {t:'strong'|'em'|'del',c} {t:'link',href,c}. */
export function parseInline(s, depth = 0) {
	const out = [];
	let buf = '';
	const pushText = (v) => {
		const last = out[out.length - 1];
		if (last?.t === 'text') last.v += v;
		else out.push({ t: 'text', v });
	};
	const flush = () => {
		if (buf) pushText(buf);
		buf = '';
	};
	let i = 0;
	while (i < s.length) {
		const ch = s[i];
		if (ch === '\\' && i + 1 < s.length && /[\\`*_{}[\]()#+\-.!~>|<]/.test(s[i + 1])) {
			buf += s[i + 1];
			i += 2;
			continue;
		}
		if (ch === '\n') {
			flush();
			out.push({ t: 'br' });
			i++;
			continue;
		}
		if (ch === '`') {
			const m = /^(`+)([\s\S]*?[^`])\1(?!`)/.exec(s.slice(i));
			if (m) {
				flush();
				out.push({ t: 'code', v: m[2].trim() });
				i += m[0].length;
				continue;
			}
		}
		if (depth < MAX_DEPTH) {
			if (ch === '[' || ch === '!') {
				const m = /^!?\[([^\]\n]*)\]\(\s*<?([^\s)>]+)>?(?:\s+"[^"]*")?\s*\)/.exec(s.slice(i));
				if (m) {
					const href = safeUrl(m[2]);
					flush();
					if (href) out.push({ t: 'link', href, c: parseInline(m[1] || m[2], depth + 1) });
					else pushText(m[0]);
					i += m[0].length;
					continue;
				}
			}
			let done = false;
			for (const [mark, type] of [['**', 'strong'], ['__', 'strong'], ['~~', 'del'], ['*', 'em'], ['_', 'em']]) {
				if (!s.startsWith(mark, i)) continue;
				if (/\s/.test(s[i + mark.length] ?? ' ')) continue; // a mark followed by a space opens nothing
				if (mark.startsWith('_') && /\w/.test(s[i - 1] ?? '')) continue; // snake_case stays as it is
				const j = closing(s, i + mark.length, mark);
				if (j < 0) continue;
				flush();
				out.push({ t: type, c: parseInline(s.slice(i + mark.length, j), depth + 1) });
				i = j + mark.length;
				done = true;
				break;
			}
			if (done) continue;
		}
		if ((ch === 'h' || ch === 'H') && !/\w/.test(s[i - 1] ?? '')) {
			const m = /^https?:\/\/[^\s<>"']+/i.exec(s.slice(i));
			if (m) {
				const url = m[0].replace(/[.,;:!?)\]]+$/, '');
				const href = safeUrl(url);
				if (href) {
					flush();
					out.push({ t: 'link', href, c: [{ t: 'text', v: url }] });
					i += url.length;
					continue;
				}
			}
		}
		buf += ch;
		i++;
	}
	flush();
	return out;
}

const RULE = /^ {0,3}([-*_])( *\1){2,} *$/;
const HEAD = /^ {0,3}(#{1,6})\s+(.*?)\s*#*\s*$/;
const ITEM = /^\s*([-*+]|\d{1,9}[.)])\s+(.*)$/;
const startsBlock = (line) => RULE.test(line) || HEAD.test(line) || ITEM.test(line) || /^\s*>/.test(line) || /^\s*```/.test(line);

/** Blocks: {t:'p',c} {t:'h',l,c} {t:'ul'|'ol',items:[nodes[]]} {t:'quote',c:blocks} {t:'pre',v} {t:'hr'}. */
export function parseMarkdown(src, depth = 0) {
	const lines = String(src ?? '').slice(0, MAX_SOURCE).replace(/\r\n?/g, '\n').split('\n');
	const blocks = [];
	let i = 0;
	while (i < lines.length) {
		const line = lines[i];
		if (!line.trim()) {
			i++;
			continue;
		}
		if (/^\s*```/.test(line)) {
			const body = [];
			i++;
			while (i < lines.length && !/^\s*```/.test(lines[i])) body.push(lines[i++]);
			i++; // the closing fence (or the end)
			blocks.push({ t: 'pre', v: body.join('\n') });
			continue;
		}
		if (RULE.test(line)) {
			blocks.push({ t: 'hr' });
			i++;
			continue;
		}
		const h = HEAD.exec(line);
		if (h) {
			blocks.push({ t: 'h', l: h[1].length, c: parseInline(h[2]) });
			i++;
			continue;
		}
		if (/^\s*>/.test(line)) {
			const body = [];
			while (i < lines.length && /^\s*>/.test(lines[i])) body.push(lines[i++].replace(/^\s*> ?/, ''));
			blocks.push({ t: 'quote', c: depth < 2 ? parseMarkdown(body.join('\n'), depth + 1) : [{ t: 'p', c: parseInline(body.join('\n')) }] });
			continue;
		}
		const li = ITEM.exec(line);
		if (li) {
			const ordered = /\d/.test(li[1]);
			const items = [];
			while (i < lines.length) {
				const m = ITEM.exec(lines[i]);
				if (!m || /\d/.test(m[1]) !== ordered) break;
				items.push(parseInline(m[2]));
				i++;
			}
			blocks.push({ t: ordered ? 'ol' : 'ul', items });
			continue;
		}
		const para = [];
		while (i < lines.length && lines[i].trim() && !(para.length && startsBlock(lines[i]))) para.push(lines[i++].trim());
		blocks.push({ t: 'p', c: parseInline(para.join('\n')) });
	}
	return blocks;
}
