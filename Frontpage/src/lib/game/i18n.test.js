import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { i18n, setMessages, t, tList } from './i18n.svelte.js';

const localeDir = resolve(process.cwd(), '../src/main/resources/locale');
const parse = (file) =>
	Object.fromEntries(
		readFileSync(join(localeDir, file), 'utf8')
			.split(/\r?\n/)
			.map((l) => l.match(/^([A-Za-z0-9_.-]+)=(.*)$/))
			.filter(Boolean)
			.map((m) => [m[1], m[2]])
	);

function* sources(dir) {
	for (const name of readdirSync(dir)) {
		const p = join(dir, name);
		if (statSync(p).isDirectory()) yield* sources(p);
		else if (/\.(svelte|js)$/.test(name) && !/\.test\./.test(name)) yield p;
	}
}

describe('t()', () => {
	it('fills {placeholders}, leaves unknown ones and falls back to the key', () => {
		const saved = { ...i18n.messages };
		setMessages({ a: '{n}명 · {x}', 'tip.2': 'B', 'tip.10': 'C', 'tip.1': 'A' }, 'ko');
		expect(t('a', { n: 3 })).toBe('3명 · {x}');
		expect(t('missing.key')).toBe('missing.key');
		expect(tList('tip')).toEqual(['A', 'B', 'C']); // numeric order
		setMessages(saved, 'ko');
	});
});

describe('locale files', () => {
	const files = ['message.properties', 'message_ko.properties'].map(parse);
	const used = new Set();
	for (const dir of ['src/lib/game', 'src/routes/play']) {
		for (const file of sources(resolve(process.cwd(), dir))) {
			for (const m of readFileSync(file, 'utf8').matchAll(/\bt\(\s*'(play\.[A-Za-z0-9_.]+)'/g)) used.add(m[1]);
		}
	}
	// keys built at runtime
	const dynamic = [
		...['LobbyBGM', 'LobbyBGM_NEW', 'LobbyBGM_2024_Fall', 'LobbyBGM_2024_Winter', 'LobbyBGM_2025_8th', 'LobbyBGM_2025_Winter', 'LobbyBGM_2026_Spring', 'LobbyBGM_2026_10th'].map((k) => `play.audio.lobby.${k}`),
		...['kkutu', 'kkutuio', 'new_start', 'acoustic'].map((k) => `play.audio.set.${k}`),
		...['terms', 'operation', 'privacy'].flatMap((k) => [`play.policy.${k}`, `play.policy.${k}Version`]),
		...[0, 1, 2, 3, 4, 5].map((n) => `play.ai.level.${n}`),
		...['joined', 'left'].map((k) => `play.room.notice.${k}`),
		...['master', 'ready', 'wait', 'spectate', 'practice'].map((k) => `play.room.status.${k}`),
		...['lobby', 'rank', 'shop', 'dict', 'replay', 'news', 'progress', 'help', 'mail', 'settings'].map((id) => `play.nav.${id}`),
		...[1, 2, 3, 4, 5, 6, 7].map((n) => `play.quick.roomTitle.${n}`),
		...[449, 402, 479, 457, 440].map((c) => `play.close.${c}`),
		...['NETWORK', 'LOGIN_REQUIRED', 'ACCOUNT_REQUIRED', 'REGISTER_DISABLED', 'BLOCKED', 'ACCOUNT_BLOCKED', 'NO_SUCH_SERVER'].map((c) => `play.error.${c}`),
		...['right', 'left'].map((k) => `play.settings.hand.${k}`),
		...['good', 'fair', 'poor', 'offline', 'unknown'].map((k) => `play.status.${k}`),
		'play.rooms.view.list', 'play.rooms.view.tile', 'play.tip.1', 'play.setup.error.600', 'play.setup.error.601', 'play.setup.error.602', 'play.setup.error.603', 'play.setup.error.620', 'play.setup.error.BAD_REQUEST', 'play.setup.error.generic', 'play.create.group.ko', 'play.create.group.en', 'play.create.group.other'
	];
	it('finds the keys it should check', () => expect(used.size).toBeGreaterThan(60));
	it.each(['message.properties', 'message_ko.properties'])('%s defines every key the client uses', (name) => {
		const msgs = parse(name);
		const missing = [...used, ...dynamic].filter((k) => !(k in msgs));
		expect(missing).toEqual([]);
	});
	it('keeps both files in sync for play.* keys', () => {
		const keys = (m) => Object.keys(m).filter((k) => k.startsWith('play.')).sort();
		expect(keys(files[0])).toEqual(keys(files[1]));
	});
});
