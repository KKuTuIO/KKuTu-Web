import { describe, expect, it } from 'vitest';
import { LESSONS } from './lessons.js';
import { LESSON_IDS } from './progress.svelte.js';
import { MEANING, ROUNDS } from './chainData.js';
import { checkScript, scriptKeys } from '../story/script.js';
import { i18n, t } from '../i18n.svelte.js';

// every line the guide speaks ends the way she speaks
const REMI_ENDING = /레미[.!?…~ ]*$/;
const plain = (s) => s.replace(/<[^>]*>/g, '').trim();

describe('the lessons', () => {
	it('are the lessons the server knows', () => {
		expect(LESSONS.map((l) => l.id).sort()).toEqual([...LESSON_IDS].sort());
	});
	for (const l of LESSONS) {
		it(`${l.id}: the script is sound and every line exists in the locale`, () => {
			expect(checkScript(l.script)).toEqual([]);
			for (const key of [...scriptKeys(l.script), `play.tutorial.lesson.${l.id}.title`, `play.tutorial.lesson.${l.id}.desc`]) expect(t(key), key).not.toBe(key);
		});
	}
	it('every word of the practice game has a meaning', () => {
		for (const { word: w } of ROUNDS.flatMap((r) => r.moves)) {
			expect(MEANING[w], w).toBeTruthy();
			expect(t(`play.tutorial.chain.mean.${MEANING[w]}`), w).not.toBe(`play.tutorial.chain.mean.${MEANING[w]}`);
		}
	});
	it('every message the practice game and the board use exists', () => {
		const keys = [
			'guide', 'good', 'pause', 'stuck', 'win', 'err.start', 'err.other',
			'r1.mission', 'r1.hint', 'r1.done', 'r1.out',
			'r2.intro', 'r2.hanbang', 'r2.lose', 'r2.on',
			'r3.intro', 'r3.hint', 'r3.refused', 'r3.why', 'r3.on', 'r3.again', 'r3.ok', 'r3.out'
		].map((k) => `play.story.chain.${k}`);
		for (const k of [...keys, 'play.tutorial.chain.type', 'play.tutorial.chain.err.start', 'play.tutorial.chain.err.other', 'play.tutorial.chain.room']) expect(t(k), k).not.toBe(k);
	});
	it('the guide speaks like 레미 in every line', () => {
		const lines = Object.entries(i18n.messages).filter(([k]) => k.startsWith('play.story.') && !k.startsWith('play.story.ui.'));
		expect(lines.length).toBeGreaterThan(30);
		for (const [k, v] of lines) expect(plain(v), k).toMatch(REMI_ENDING);
		expect(plain(t('play.nudge.tutorial'))).toMatch(REMI_ENDING);
	});
});
