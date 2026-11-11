<script>
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import { getLevel } from '../level.js';
	import { levelSpriteStyle, IMAGE_CDN, IMAGE_VERSION } from '../images.js';
	import { prefs } from '../prefs.svelte.js';
	/** Level badge from the sprite sheet. Pass `score` (preferred) or `level`. */
	let { score = 0, level = 0, size = 24, maskable = false } = $props();
	const lv = $derived(level || getLevel(score));
</script>

{#if maskable && prefs.hideUsers}
	<!-- 참여자 숨기기: the level is shown as a blank badge -->
	<span class="inline-block shrink-0" role="img" aria-label={t('play.level.hidden')} style="background:url({IMAGE_CDN}kkutu/lv/hidelv.png{IMAGE_VERSION}) 0 0 / 100% 100%;width:{size}px;height:{size}px"></span>
{:else}
	<Tooltip text="Lv.{lv}"><span class="inline-block shrink-0" role="img" aria-label={t('play.level', { level: lv })} style="{levelSpriteStyle(lv)};width:{size}px;height:{size}px"></span></Tooltip>
{/if}
