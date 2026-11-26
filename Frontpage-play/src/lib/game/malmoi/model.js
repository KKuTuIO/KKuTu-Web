import { t } from '../i18n.svelte.js';
/** 말모이 (사용자가 모은 낱말): the rules as pure functions and constants, so they can be tested without a DOM. */

export const LIMITS = {
	dailyEdits: 50,
	unlisted: 5,
	wordMax: 80,
	applyMs: 24 * 60 * 60 * 1000,
	fee: 500,
	licenseDays: 90,
	publishLevel: 30,
	promoteLevel: 30,
	promoteWords: 50,
	editors: 3,
	topShare: 0.1,
	topDays: 30,
	blockDays: 7
};

const SHEET_URL = 'https://docs.google.com/spreadsheets/d/1u6YvurX316DB1bBKg8pH6Wi2Z61FLcJqv3AhrbNfYMY/edit';
export const SHEET = {
	common: `${SHEET_URL}#gid=774372110`,
	ko: `${SHEET_URL}#gid=1649347777`,
	trial: `${SHEET_URL}#gid=1677326085`
};

/** The reasons a manager picks when deleting or blocking. */
export const reasons = () => [t('play.malmoi.reason.notReal'), t('play.malmoi.reason.lowFame'), t('play.malmoi.reason.common'), t('play.malmoi.reason.offTopic'), t('play.malmoi.reason.spelling'), t('play.malmoi.common.duplicate'), t('play.malmoi.reason.similar'), t('play.malmoi.reason.gambling'), t('play.malmoi.reason.balance')];

/** The oath, line by line. The server compares what is typed with its own copy (`MalmoiRules.OATH`): a change here needs the same change there. */
export const oath = () => [t('play.malmoi.oath.1'), t('play.malmoi.oath.2'), t('play.malmoi.oath.3'), t('play.malmoi.oath.4')];

/** An oath line counts as typed whatever the spacing and the final period. */
export const sameOath = (typed, line) => {
	const bare = (s) => String(s).replace(/[\s.,]/g, '');
	return bare(typed) === bare(line);
};

/** 표제어: 띄어쓰기는 생략해서 적는다. */
export const normalizeWord = (raw) => String(raw ?? '').replace(/\s+/g, '');

/** @returns {string} why the word cannot be saved, or '' */
export function wordError(raw) {
	const w = normalizeWord(raw);
	if (!w) return t('play.malmoi.word.empty');
	if (!/^[ㄱ-ㅎ가-힣0-9]+$/.test(w)) return t('play.malmoi.word.hangulOnly');
	if (w.length > LIMITS.wordMax) return t('play.malmoi.word.tooLong', { n: LIMITS.wordMax });
	return '';
}

// A stand-in for the real analysis: endings that (almost) nothing starts with.
const DEAD_ENDS = new Set(['륨', '슘', '늄', '릇', '녘', '쁨', '튬']);
export const maybeOneShot = (word) => DEAD_ENDS.has(normalizeWord(word).slice(-1));

/** 면허 번호: 발급 연-월-일련번호-회차, in the shape a card number has. */
export function licenseNumber(issued, serial) {
	const d = new Date(issued);
	return `${String(d.getFullYear()).slice(2)}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(serial % 1e6).padStart(6, '0')}-01`;
}

// ---- 여러 개 추가 ----

/** RFC 4180 CSV (quoted fields, "" escapes, line breaks inside a field, CRLF). */
export function parseCsv(text) {
	const rows = [];
	let row = [];
	let field = '';
	let quoted = false;
	for (let i = 0; i < text.length; i++) {
		const ch = text[i];
		if (quoted) {
			if (ch === '"') {
				if (text[i + 1] === '"') (field += '"'), i++;
				else quoted = false;
			} else field += ch;
		} else if (ch === '"' && field === '') quoted = true;
		else if (ch === ',') row.push(field), (field = '');
		else if (ch === '\n' || ch === '\r') {
			if (ch === '\r' && text[i + 1] === '\n') i++;
			row.push(field), rows.push(row), (row = []), (field = '');
		} else field += ch;
	}
	if (field !== '' || row.length) row.push(field), rows.push(row);
	return rows;
}

/** UTF-8 first, then the Korean spreadsheet encoding (CP949). The BOM goes. */
export function decodeCsv(buffer) {
	let text;
	try {
		text = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
	} catch {
		text = new TextDecoder('euc-kr').decode(buffer);
	}
	return text.replace(/^﻿/, '');
}

const HEADERS = new Set(['낱말', '단어', 'word', 'words']);

/** Columns 낱말 / 뜻 / 출처 -> rows. A header line and empty lines are dropped. */
export function toRows(cells) {
	return cells
		.map((c) => ({ word: (c[0] ?? '').trim(), mean: (c[1] ?? '').trim(), source: (c[2] ?? '').trim() }))
		.filter((r, i) => r.word && !(i === 0 && HEADERS.has(r.word.toLowerCase())));
}

/** Typed or pasted from a spreadsheet: one word a line, 뜻 and 출처 after tabs. */
export const textRows = (text) => toRows(String(text ?? '').split('\n').map((line) => line.split('\t')));

/** Rows written back as the text the field holds (after a CSV import). */
export const rowsText = (rows) => rows.map((r) => [r.word, r.mean, r.source].join('\t').replace(/\t+$/, '')).join('\n');

/**
 * What saving would do to each row: `add`, `meaning` (a new meaning for a word that is there), `dup`, or `fail`.
 * @param {{word:string,mean:string,source:string}[]} rows
 * @param {{words:any[], baseWords?:string[]}} dict
 */
export function previewRows(rows, dict) {
	const have = new Map(dict.words.map((w) => [w.text, new Set(w.meanings)]));
	const base = new Set(dict.baseWords ?? []);
	return rows.map((r) => {
		const word = normalizeWord(r.word);
		const error = wordError(word);
		if (error) return { ...r, word: r.word, status: 'fail', note: error };
		if (base.has(word)) return { ...r, word, status: 'fail', note: t('play.malmoi.word.inBase') };
		const meanings = have.get(word);
		if (meanings) {
			if (!r.mean || meanings.has(r.mean)) return { ...r, word, status: 'dup', note: '' };
			meanings.add(r.mean);
			return { ...r, word, status: 'meaning', note: '' };
		}
		have.set(word, new Set(r.mean ? [r.mean] : []));
		return { ...r, word, status: 'add', note: maybeOneShot(word) ? t('play.malmoi.word.maybeOneShot') : '' };
	});
}

/** A 확장팩 goes by its base theme's name, so it says what it is wherever no badge stands beside it. */
export const titleOf = (d) => (d.kind === 'ext' ? t('play.malmoi.common.extPack', { title: d.title }) : d.title);

// ---- 상태 ----

export function remaining(ms) {
	const h = Math.ceil(ms / 3600000);
	if (h > 1) return t('play.malmoi.state.hours', { n: h });
	return t('play.malmoi.state.minutes', { n: Math.max(1, Math.ceil(ms / 60000)) });
}

/** How a word stands now: waiting to enter the game, on the way out, or in the game. */
export function wordState(word, now = Date.now()) {
	if (word.removeAt) return { kind: 'removing', label: t('play.malmoi.state.removingIn', { time: remaining(word.removeAt - now) }) };
	if (word.appliesAt > now) return { kind: 'pending', label: t('play.malmoi.state.appliesIn', { time: remaining(word.appliesAt - now) }) };
	if (word.next) return { kind: 'pending', label: t('play.malmoi.state.editAppliesIn', { time: remaining(word.next.appliesAt - now) }) };
	return { kind: 'live', label: t('play.malmoi.state.live') };
}

/** The share of 핑·경험치 a room with these 말모이 pays: none with any 일부 공개, else 75%. */
export const rewardShare = (dicts) => (dicts.some((d) => d.state === 'unlisted') ? 0 : 0.75);

// ---- 기여 ----

const DAY_MS = 86400000;
export const dayKey = (at) => {
	const d = new Date(at);
	return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Each person's share of a 말모이's contribution (낱말 that stayed). A `top` contributor holds at least LIMITS.topShare of it.
 * @param {{who:string, n:number}[]} activity
 */
export function shares(activity) {
	const sum = new Map();
	for (const a of activity) sum.set(a.who, (sum.get(a.who) ?? 0) + a.n);
	const total = [...sum.values()].reduce((x, y) => x + y, 0);
	return [...sum]
		.map(([name, count]) => ({ name, count, share: total ? count / total : 0, top: total > 0 && count / total >= LIMITS.topShare }))
		.sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, 'ko'));
}

/**
 * The GitHub-style grid: `weeks` columns of 7 days (Sunday on top), the last one holding today. A cell is `{key, n, level 0-4}`; days after today
 * are null. The levels split the busiest day into quarters.
 */
export function grid(activity, now = Date.now(), weeks = 26) {
	const perDay = new Map();
	for (const a of activity) perDay.set(dayKey(a.at), (perDay.get(dayKey(a.at)) ?? 0) + a.n);
	const max = Math.max(1, ...perDay.values());
	const today = new Date(now);
	today.setHours(12, 0, 0, 0);
	const start = today.getTime() - (today.getDay() + (weeks - 1) * 7) * DAY_MS;
	const cols = [];
	for (let w = 0; w < weeks; w++) {
		const col = [];
		for (let d = 0; d < 7; d++) {
			const at = start + (w * 7 + d) * DAY_MS;
			if (at > today.getTime()) col.push(null);
			else {
				const key = dayKey(at);
				const n = perDay.get(key) ?? 0;
				col.push({ key, n, level: n ? Math.min(4, Math.ceil((n / max) * 4)) : 0, month: new Date(at).getMonth() });
			}
		}
		cols.push(col);
	}
	return cols;
}

// ---- 표지 ----

function luminance(hex) {
	const n = parseInt(String(hex).slice(1), 16);
	const [r, g, b] = [n >> 16, (n >> 8) & 255, n & 255].map((v) => {
		const c = v / 255;
		return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
	});
	return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(a, b) {
	const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
	return (x + 0.05) / (y + 0.05);
}

export const SYMBOLS = [
	'menu_book', 'auto_stories', 'history_edu', 'school', 'translate', 'psychology', 'calculate', 'science',
	'music_note', 'mic', 'headphones', 'movie', 'tv', 'theater_comedy', 'palette', 'brush',
	'sports_esports', 'swords', 'shield', 'smart_toy', 'rocket_launch', 'bolt', 'local_fire_department', 'diamond',
	'public', 'flag', 'castle', 'temple_buddhist', 'account_balance', 'museum', 'location_city', 'landscape',
	'train', 'flight', 'directions_car', 'sailing', 'restaurant', 'ramen_dining', 'icecream', 'cookie',
	'pets', 'park', 'forest', 'eco', 'water_drop', 'sports_soccer', 'sports_baseball', 'sports_basketball',
	'star', 'favorite', 'emoji_events', 'anchor'
];

export const EMOJIS = [
	'📚', '📖', '✏️', '🎓', '🧠', '🔤', '➗', '🔬',
	'🎵', '🎤', '🎧', '🎬', '📺', '🎭', '🎨', '🖌️',
	'🎮', '⚔️', '🛡️', '🤖', '🚀', '⚡', '🔥', '💎',
	'🌏', '🚩', '🏰', '🛕', '🏛️', '🗿', '🏙️', '⛰️',
	'🚆', '✈️', '🚗', '⛵', '🍜', '🍚', '🍦', '🍪',
	'🐾', '🌳', '🌱', '💧', '⚽', '⚾', '🏀', '🏆'
];

export const BG_COLORS = ['#3553a0', '#1f2937', '#b91c1c', '#ea580c', '#facc15', '#15803d', '#0f766e', '#0369a1', '#6d28d9', '#be185d', '#fef3c7', '#dcfce7', '#dbeafe', '#fce7f3', '#f3f4f6', '#ffffff'];
export const ICON_COLORS = ['#ffffff', '#111827', '#fde047', '#3553a0', '#dc2626', '#16a34a', '#0ea5e9', '#f472b6'];

/** The first emoji of what was typed (a whole one, joined sequences too), or ''. */
export function firstEmoji(text) {
	const parts = typeof Intl !== 'undefined' && Intl.Segmenter ? [...new Intl.Segmenter().segment(String(text))].map((s) => s.segment) : Array.from(String(text));
	return parts.find((p) => /\p{Extended_Pictographic}|\p{Regional_Indicator}/u.test(p)) ?? '';
}
