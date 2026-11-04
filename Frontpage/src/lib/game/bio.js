/** The 소개 (bio): its length and saving it. The legacy endpoint `POST /exordial` (form field `data`) answers `{text}`, or an error. */
export const MAX_BIO = 500; // must match the server's MAX_EXORDIAL_LENGTH (NicknameService)

/** @returns {Promise<{text:string}|{error:string}>} an empty text removes the bio */
export async function saveBio(text, fetchFn = fetch) {
	try {
		const res = await fetchFn('/exordial', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ data: String(text ?? '') }) });
		const body = await res.json().catch(() => ({}));
		if (res.ok && typeof body.text === 'string') return { text: body.text };
		return { error: body.error_description || (body.error ? String(body.error) : '') || 'request' };
	} catch {
		return { error: 'request' };
	}
}

/**
 * A formatting button on the editor: what the text becomes when `kind` is applied to the selection [start, end), and where the
 * selection is afterwards. Marks (bold, italic, strike, code) wrap the selection (and unwrap it when it is already wrapped); a link
 * wraps it as [text](https://) with the address selected; list and quote prefix every line the selection touches.
 * @returns {{text:string, start:number, end:number}}
 */
export function applyFormat(text, start, end, kind) {
	const MARKS = { bold: ['**', '**', '굵게'], italic: ['*', '*', '기울임'], strike: ['~~', '~~', '취소선'], code: ['`', '`', '코드'] };
	if (MARKS[kind]) {
		// a double click often selects the space after the word: the marks go around the word itself ("** a **" is not bold)
		while (start < end && /\s/.test(text[start])) start++;
		while (end > start && /\s/.test(text[end - 1])) end--;
	}
	const sel = text.slice(start, end);
	if (MARKS[kind]) {
		const [a, b, ph] = MARKS[kind];
		// already wrapped (the marks are just outside the selection, or inside it): take them off
		if (text.slice(start - a.length, start) === a && text.slice(end, end + b.length) === b && start - a.length >= 0) {
			return { text: text.slice(0, start - a.length) + sel + text.slice(end + b.length), start: start - a.length, end: end - a.length };
		}
		if (sel.length >= a.length + b.length && sel.startsWith(a) && sel.endsWith(b)) {
			const inner = sel.slice(a.length, sel.length - b.length);
			return { text: text.slice(0, start) + inner + text.slice(end), start, end: start + inner.length };
		}
		const body = sel || ph;
		const out = text.slice(0, start) + a + body + b + text.slice(end);
		return { text: out, start: start + a.length, end: start + a.length + body.length };
	}
	if (kind === 'link') {
		const label = sel || '글자';
		const url = /^https?:\/\/\S+$/i.test(sel) ? sel : 'https://';
		const inner = /^https?:\/\/\S+$/i.test(sel) ? '글자' : label;
		const out = text.slice(0, start) + `[${inner}](${url})` + text.slice(end);
		// select the address so it can be typed over (or the label, when the selection was an address)
		if (url === sel) return { text: out, start: start + 1, end: start + 1 + inner.length };
		const from = start + inner.length + 3;
		return { text: out, start: from, end: from + url.length };
	}
	if (kind === 'list' || kind === 'quote') {
		const prefix = kind === 'list' ? '- ' : '> ';
		const lineStart = text.lastIndexOf('\n', start - 1) + 1;
		const nl = text.indexOf('\n', end);
		const lineEnd = nl < 0 ? text.length : nl;
		const lines = text.slice(lineStart, lineEnd).split('\n');
		const all = lines.every((l) => l.startsWith(prefix));
		const next = lines.map((l) => (all ? l.slice(prefix.length) : l.startsWith(prefix) ? l : prefix + l)).join('\n');
		return { text: text.slice(0, lineStart) + next + text.slice(lineEnd), start: lineStart, end: lineStart + next.length };
	}
	return { text, start, end };
}
