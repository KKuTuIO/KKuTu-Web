import { describe, expect, it } from 'vitest';
import { contrast, mix, reach, themeOf, themeVars, themeStyle } from './theme.js';

const latte = {
	dark: false,
	colors: { base: '#eff1f5', mantle: '#e6e9ef', crust: '#dce0e8', surface0: '#ccd0da', surface1: '#bcc0cc', text: '#4c4f69', subtext0: '#6c6f85', accent: '#40a02b', red: '#d20f39', yellow: '#df8e1d', blue: '#1e66f5', teal: '#179299', lavender: '#7287fd' }
};
const mocha = {
	dark: true,
	colors: { base: '#1e1e2e', mantle: '#181825', crust: '#11111b', surface0: '#313244', surface1: '#45475a', text: '#cdd6f4', subtext0: '#a6adc8', accent: '#cba6f7', red: '#f38ba8', yellow: '#f9e2af', blue: '#89b4fa', teal: '#94e2d5', lavender: '#b4befe' }
};

describe('themeOf', () => {
	it('reads the palette of an item', () => expect(themeOf({ options: { theme: latte } })).toEqual(latte));
	it('is null for an item without one, or with a broken one', () => {
		expect(themeOf({ options: {} })).toBeNull();
		expect(themeOf(undefined)).toBeNull();
		expect(themeOf({ options: { theme: { colors: { ...latte.colors, base: 'red' } } } })).toBeNull();
		expect(themeOf({ options: { theme: { colors: { ...latte.colors, text: undefined } } } })).toBeNull();
	});
});

describe('colour maths', () => {
	it('mixes', () => expect(mix('#000000', '#ffffff', 0.5)).toBe('#808080'));
	it('measures contrast', () => {
		expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 0);
		expect(contrast('#777777', '#777777')).toBe(1);
	});
	it('leaves a colour that already reads alone and pulls one that does not', () => {
		expect(reach('#000000', '#ffffff', '#000000')).toBe('#000000');
		expect(contrast(reach('#aaaaaa', '#ffffff', '#000000'), '#ffffff')).toBeGreaterThanOrEqual(4.5);
	});
});

describe('themeVars', () => {
	for (const [name, theme] of [['latte', latte], ['mocha', mocha]]) {
		it(`keeps text readable (${name})`, () => {
			const v = themeVars(theme);
			expect(v['color-scheme']).toBe(theme.dark ? 'dark' : 'light');
			for (const k of ['--kk-text', '--kk-muted', '--kk-accent-strong', '--kk-warn', '--kk-danger', '--kk-blue', '--kk-whisper']) {
				expect(contrast(v[k], v['--kk-surface']), k).toBeGreaterThanOrEqual(4.5);
			}
			for (const k of ['--kk-ping-good', '--kk-ping-fair', '--kk-ping-poor']) expect(contrast(v[k], v['--kk-bg']), k).toBeGreaterThanOrEqual(4.5);
			for (const k of ['--kk-fill', '--kk-danger-fill', '--kk-cta-to']) expect(contrast('#ffffff', v[k]), k).toBeGreaterThanOrEqual(4.5);
		});
	}
	it('gives the palette its place', () => {
		const v = themeVars(mocha);
		expect(v['--kk-bg']).toBe('#11111b');
		expect(v['--kk-surface']).toBe('#1e1e2e');
		expect(v['--kk-accent']).toBe('#cba6f7');
	});
	it('is an inline style', () => expect(themeStyle(latte)).toContain('--kk-surface:#eff1f5;'));
});
