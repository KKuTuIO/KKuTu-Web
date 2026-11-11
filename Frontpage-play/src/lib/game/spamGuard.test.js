import { describe, expect, it } from 'vitest';
import { createSpamGuard } from './spamGuard.js';
import { isAllowedText, isBad } from './badwords.js';

describe('spam guard (legacy numbers)', () => {
	it('lets normal chatting through', () => {
		const check = createSpamGuard();
		let t = 1000;
		for (const line of ['안녕', '반가워', '뭐해', '밥 먹었어?', '나는 게임']) {
			expect(check(line, t).allow).toBe(true);
			t += 3000;
		}
	});
	it('mutes for 5 s after a burst, with the legacy messages, and extends when it is tried again', () => {
		const check = createSpamGuard();
		const out = [];
		for (let i = 0; i < 6 && !out.some((r) => !r.allow); i++) out.push(check('도배', 1000 + i * 100));
		const first = out.findIndex((r) => !r.allow);
		expect(first).toBeGreaterThan(0);
		expect(out[first]).toEqual({ allow: false, notice: 'play.chat.spam.detected' });
		const now = 1000 + first * 100;
		expect(check('또', now + 100)).toEqual({ allow: false, notice: 'play.chat.spam.muted', seconds: 5 });
		expect(check('또', now + 200)).toEqual({ allow: false }); // told once, then silent
		expect(check('또', now + 300).notice).toBe('play.chat.spam.extended');
		expect(check('이제', now + 20_000).allow).toBe(true); // long after the mute
	});
	it('counts the same text differently written as a repeat', () => {
		const check = createSpamGuard();
		const r = ['ㅋㅋ', 'ㅋ ㅋ 1', 'ㅋ!ㅋ', 'ㅋㅋ.', 'ㅋ ㅋ'].map((v, i) => check(v, 1000 + i * 200));
		expect(r.some((x) => !x.allow)).toBe(true);
	});
});

describe('room title rules (legacy)', () => {
	it('allows letters, digits, Hangul and a few marks only', () => {
		expect(isAllowedText('바른 방 제목 (1)')).toBe(true);
		expect(isAllowedText('방<script>')).toBe(false);
		expect(isAllowedText('이모지😀')).toBe(false);
	});
	it('refuses swearing, also with digits or spaces inside', () => {
		expect(isBad('병신 방')).toBe(true);
		expect(isBad('좋은 방')).toBe(false);
	});
});
