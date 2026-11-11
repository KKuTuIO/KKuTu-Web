import { describe, it, expect } from 'vitest';
import { saveBio, MAX_BIO, applyFormat } from './bio.js';

const reply = (ok, body) => async () => ({ ok, json: async () => body });

describe('saveBio', () => {
	it('sends the text as the form field `data` and returns what the server kept', async () => {
		let seen;
		const r = await saveBio('안녕 **끄투**', async (url, init) => ((seen = { url, init }), { ok: true, json: async () => ({ text: '안녕 **끄투**' }) }));
		expect(seen.url).toBe('/exordial');
		expect(seen.init.method).toBe('POST');
		expect(seen.init.body.get('data')).toBe('안녕 **끄투**');
		expect(r).toEqual({ text: '안녕 **끄투**' });
	});
	it('returns the server message for a refusal', async () => {
		expect(await saveBio('x', reply(false, { error: 'exordial_has_bad_words', error_description: '사용이 제한된 단어가 포함되어 있습니다.' }))).toEqual({ error: '사용이 제한된 단어가 포함되어 있습니다.' });
	});
	it('does not throw when the request fails', async () => {
		expect(await saveBio('x', async () => { throw new Error('offline'); })).toEqual({ error: 'request' });
	});
	it('has a limit well above the old 100', () => {
		expect(MAX_BIO).toBeGreaterThan(100);
	});
});

describe('applyFormat', () => {
	it('wraps the selection in a mark and keeps it selected', () => {
		const r = applyFormat('안녕 세상', 3, 5, 'bold');
		expect(r.text).toBe('안녕 **세상**');
		expect(r.text.slice(r.start, r.end)).toBe('세상');
	});
	it('keeps the marks off the spaces at the edges of the selection', () => {
		expect(applyFormat('끄투 리오', 0, 3, 'bold').text).toBe('**끄투** 리오');
		// a selection of only spaces counts as nothing selected: the placeholder goes after it
		expect(applyFormat('a  b', 1, 3, 'italic').text).toBe('a  *기울임*b');
	});
	it('puts a placeholder where nothing is selected', () => {
		const r = applyFormat('', 0, 0, 'italic');
		expect(r.text).toBe('*기울임*');
		expect(r.text.slice(r.start, r.end)).toBe('기울임');
	});
	it('takes a mark off when pressed again (marks outside the selection, or inside it)', () => {
		expect(applyFormat('a **b** c', 4, 5, 'bold').text).toBe('a b c');
		expect(applyFormat('a **b** c', 2, 7, 'bold').text).toBe('a b c');
	});
	it('makes a link and selects its address', () => {
		const r = applyFormat('see 끄투', 4, 6, 'link');
		expect(r.text).toBe('see [끄투](https://)');
		expect(r.text.slice(r.start, r.end)).toBe('https://');
	});
	it('turns a selected address into the link target', () => {
		const r = applyFormat('https://kkutu.io', 0, 16, 'link');
		expect(r.text).toBe('[글자](https://kkutu.io)');
		expect(r.text.slice(r.start, r.end)).toBe('글자');
	});
	it('prefixes the lines of the selection for a list or a quote, and removes the prefix again', () => {
		expect(applyFormat('a\nb\nc', 0, 3, 'list').text).toBe('- a\n- b\nc');
		expect(applyFormat('- a\n- b', 0, 7, 'list').text).toBe('a\nb');
		expect(applyFormat('hello', 2, 2, 'quote').text).toBe('> hello');
	});
});
