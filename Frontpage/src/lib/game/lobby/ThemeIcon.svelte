<script>
	import { themeOf } from '../theme.js';

	/**
	 * A theme's picture, drawn from its palette (a theme has no art file): a tiny window of the game in those colours, the page behind,
	 * a card on it with two lines of text and an accent button, and the accent colours as dots.
	 * @type {{item:any, class?:string}}
	 */
	let { item, class: cls = '' } = $props();
	const c = $derived(themeOf(item)?.colors);
</script>

{#if c}
	<svg viewBox="0 0 64 64" class="aspect-square {cls}" role="img" aria-hidden="true" draggable="false">
		<rect x="2" y="6" width="60" height="52" rx="9" fill={c.crust} />
		<rect x="2" y="6" width="60" height="11" rx="9" fill={c.mantle} />
		<rect x="2" y="12" width="60" height="5" fill={c.mantle} />
		<circle cx="11" cy="11.5" r="2.2" fill={c.red} />
		<circle cx="18" cy="11.5" r="2.2" fill={c.yellow} />
		<circle cx="25" cy="11.5" r="2.2" fill={c.teal} />
		<rect x="8" y="22" width="48" height="29" rx="6" fill={c.base} stroke={c.surface1} stroke-width="1.2" />
		<rect x="13" y="27" width="26" height="3.6" rx="1.8" fill={c.text} />
		<rect x="13" y="34" width="19" height="3" rx="1.5" fill={c.subtext0} />
		<rect x="13" y="42" width="17" height="5.4" rx="2.7" fill={c.accent} />
		<circle cx="45" cy="28.5" r="3" fill={c.blue} />
		<circle cx="51" cy="35" r="3" fill={c.lavender} />
		<circle cx="45" cy="42" r="3" fill={c.accent} />
	</svg>
{/if}
