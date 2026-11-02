<script>
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import { getLevel } from '../level.js';
	import { levelSpriteStyle } from '../images.js';
	/** Level badge from the sprite sheet. Pass `score` (preferred) or `level`. */
	let { score = 0, level = 0, size = 24 } = $props();
	const lv = $derived(level || getLevel(score));
</script>

<Tooltip text="Lv.{lv}"><span class="inline-block shrink-0" role="img" aria-label={t('play.level', { level: lv })} style="{levelSpriteStyle(lv)};width:{size}px;height:{size}px"></span></Tooltip>
