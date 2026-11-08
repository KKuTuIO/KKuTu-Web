/**
 * 테마 (shop group `THM`): an item whose `options.theme` is a palette,
 * `{ dark: boolean, colors: { base, mantle, crust, surface0, surface1, text, subtext0, accent, red, yellow, blue, teal, lavender, green? } }`
 * (hex strings). The client turns it into the game's own theme tokens (`game.css`: `--kk-*`) and nothing else: the palette is plain data,
 * so a new theme is a row in the shop table, no art and no client change. The pure rules are here; `theme.svelte.js` applies them.
 */

export const THEME_GROUP = 'THM';
const KEYS = ['base', 'mantle', 'crust', 'surface0', 'surface1', 'text', 'subtext0', 'accent', 'red', 'yellow', 'blue', 'teal', 'lavender'];
const HEX = /^#[0-9a-f]{6}$/i;

/** The palette of a shop item, or null when it has none (or an unusable one: every colour must be `#rrggbb`). */
export function themeOf(item) {
	const theme = item?.options?.theme;
	const colors = theme?.colors;
	if (!colors || typeof colors !== 'object') return null;
	for (const key of KEYS) if (typeof colors[key] !== 'string' || !HEX.test(colors[key])) return null;
	return { dark: !!theme.dark, colors };
}

const parse = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const hex = (rgb) => '#' + rgb.map((v) => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0')).join('');

/** `a` blended towards `b` by `amount` (0..1). */
export function mix(a, b, amount) {
	const x = parse(a);
	const y = parse(b);
	return hex(x.map((v, i) => v + (y[i] - v) * amount));
}

/** WCAG relative luminance. */
function luminance(color) {
	const [r, g, b] = parse(color).map((v) => {
		const c = v / 255;
		return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
	});
	return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** WCAG contrast ratio of two colours (1..21). */
export function contrast(a, b) {
	const [hi, lo] = [luminance(a), luminance(b)].sort((p, q) => q - p);
	return (hi + 0.05) / (lo + 0.05);
}

/** `color` pulled towards `toward` in small steps until it reaches `min` contrast against `against` (as it is when it already does). */
export function reach(color, against, toward, min = 4.5) {
	let out = color;
	for (let i = 0; i < 40 && contrast(out, against) < min; i++) out = mix(out, toward, 0.05);
	return out;
}

/**
 * The game's theme tokens for a palette. The tokens that carry text keep their legacy contract: `--kk-fill` and `--kk-danger-fill` hold
 * white text (the primary buttons), `--kk-accent-strong`, `--kk-warn`, `--kk-danger`, `--kk-blue` and `--kk-whisper` are text on the
 * surface, so each is darkened (light palette) or lightened (dark palette) until it reads at 4.5:1.
 * @returns {Record<string,string>} CSS custom properties (and `color-scheme`)
 */
export function themeVars(theme) {
	const c = theme.colors;
	const onSurface = (color) => reach(color, c.base, c.text);
	const withWhite = (color) => reach(color, '#ffffff', '#000000');
	const fill = withWhite(c.accent);
	const fillEdge = mix(fill, '#000000', 0.35);
	const dangerFill = withWhite(c.red);
	return {
		'color-scheme': theme.dark ? 'dark' : 'light',
		'--kk-bg': c.crust,
		'--kk-surface': c.base,
		'--kk-surface-2': c.mantle,
		'--kk-line': c.surface0,
		'--kk-line-strong': c.surface1,
		'--kk-text': c.text,
		'--kk-muted': reach(c.subtext0, c.base, c.text),
		'--kk-accent': c.accent,
		'--kk-accent-strong': onSurface(c.accent),
		'--kk-accent-soft': mix(c.accent, c.base, theme.dark ? 0.78 : 0.82),
		'--kk-fill': fill,
		'--kk-fill-edge': fillEdge,
		'--kk-panel': mix(c.mantle, c.surface0, 0.45),
		'--kk-warn': onSurface(c.yellow),
		'--kk-warn-soft': mix(c.yellow, c.base, 0.8),
		'--kk-danger': onSurface(c.red),
		'--kk-danger-fill': dangerFill,
		'--kk-danger-soft': mix(c.red, c.base, 0.82),
		'--kk-whisper': onSurface(c.lavender),
		'--kk-blue': onSurface(c.blue),
		// the connection indicator sits on the page itself (crust)
		'--kk-ping-good': reach(c.green ?? (theme.dark ? '#4ade80' : '#16a34a'), c.crust, c.text),
		'--kk-ping-fair': reach(c.yellow, c.crust, c.text),
		'--kk-ping-poor': reach(c.red, c.crust, c.text),
		'--kk-shadow': `0 2px 0 ${theme.dark ? 'rgb(0 0 0 / 0.5)' : c.surface1}`,
		'--kk-cta-from': fill,
		'--kk-cta-to': withWhite(c.teal),
		'--kk-cta-edge': fillEdge
	};
}

/** The same as an inline `style` value. */
export const themeStyle = (theme) =>
	Object.entries(themeVars(theme))
		.map(([k, v]) => `${k}:${v}`)
		.join(';');
