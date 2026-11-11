/**
 * UI strings for the game client. They come from the server's message*.properties files
 * (`play.*` keys, plus `game.*` / `kkutu.js.error.*`) via /api/play/locale, in the language of the
 * `user.language` cookie - the same source the legacy client used. Adding a language means adding a
 * message_xx.properties file on the server; nothing here changes.
 */
export const i18n = $state({ messages: {}, language: 'ko', ready: false });

// Only what must be readable if the locale request itself fails.
const FALLBACK = { 'play.error.NETWORK': '서버에 연결하지 못했습니다.', 'play.gate.retry': '다시 시도' };

/** @param {string} key @param {Record<string, string|number>} [params] values for `{name}` placeholders */
export function t(key, params) {
	const text = i18n.messages[key] ?? FALLBACK[key] ?? key;
	return params ? text.replace(/\{(\w+)\}/g, (m, k) => (k in params ? String(params[k]) : m)) : text;
}

/** All `prefix.N` messages in numeric order, e.g. the loading tips (`play.tip.1`, `play.tip.2`, ...). */
export function tList(prefix) {
	return Object.keys(i18n.messages)
		.filter((k) => k.startsWith(prefix + '.'))
		.sort((a, b) => a.localeCompare(b, 'en', { numeric: true }))
		.map((k) => i18n.messages[k]);
}

export function setMessages(messages, language = i18n.language) {
	i18n.messages = messages;
	i18n.language = language;
	i18n.ready = true;
	if (typeof document !== 'undefined') document.documentElement.lang = language;
}

export async function loadLocale() {
	try {
		const res = await fetch('/api/play/locale');
		if (res.ok) {
			const { language, messages } = await res.json();
			setMessages(messages, language);
			return;
		}
	} catch {
		/* fall through: keys/fallback strings are shown */
	}
	i18n.ready = true;
}
