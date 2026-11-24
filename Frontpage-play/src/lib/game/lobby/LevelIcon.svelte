<script>
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import { getLevel } from '../level.js';
	import { hasLevelFx, levelFxStyle, levelSpriteStyle, staffLevelImage, IMAGE_CDN, IMAGE_VERSION } from '../images.js';
	import { staffIdOf, STAFF_LEVEL_BADGE } from '../staffTag.js';
	import { prefs } from '../prefs.svelte.js';
	/**
	 * Level badge from the sprite sheet. Pass `score` (preferred) or `level`. Pass the player's `equip` where it is known: a worn staff
	 * staff badge (any team's) swaps the number for the one staff level badge. It only changes the picture, nothing the server decides.
	 * `tip={false}` for a badge inside something that has its own tooltip (two at once would pile up).
	 */
	let { score = 0, level = 0, size = 24, maskable = false, equip = null, tip = true } = $props();
	const lv = $derived(level || getLevel(score));
	// 751+: the animated badge, unless the player asked for less motion (then the same picture stands still, from the plain sheet)
	const staff = $derived(staffIdOf(equip));
	const fx = $derived(!staff && hasLevelFx(lv) && !prefs.reduceMotion);
</script>

{#if maskable && prefs.hideUsers}
	<!-- 참여자 숨기기: the level is shown as a blank badge -->
	<span class="inline-block shrink-0" role="img" aria-label={t('play.level.hidden')} style="background:url({IMAGE_CDN}kkutu/lv/hidelv.png{IMAGE_VERSION}) 0 0 / 100% 100%;width:{size}px;height:{size}px"></span>
{:else if staff}
	<Tooltip text={tip ? t(`play.staff.${staff}`) : ''}><span class="inline-block shrink-0" role="img" aria-label={t(`play.staff.${staff}`)} style="background:url({staffLevelImage(STAFF_LEVEL_BADGE)}) 0 0 / 100% 100%;width:{size}px;height:{size}px"></span></Tooltip>
{:else}
	<Tooltip text={tip ? `Lv.${lv}` : ''}><span class="inline-block shrink-0" class:lv-fx={fx} role="img" aria-label={t('play.level', { level: lv })} style="{fx ? levelFxStyle(lv, size) : levelSpriteStyle(lv)};width:{size}px;height:{size}px"></span></Tooltip>
{/if}

<style>
	/* 8 frames: 0..6 move (0.1s each), frame 7 is the rest frame and is held, so one round is 2s with a 1.3s pause. */
	.lv-fx {
		animation: lv-fx 2s steps(1, end) infinite;
	}
	@keyframes lv-fx {
		0% { background-position-x: 0; }
		5% { background-position-x: calc(var(--s) * -1); }
		10% { background-position-x: calc(var(--s) * -2); }
		15% { background-position-x: calc(var(--s) * -3); }
		20% { background-position-x: calc(var(--s) * -4); }
		25% { background-position-x: calc(var(--s) * -5); }
		30% { background-position-x: calc(var(--s) * -6); }
		35%, 100% { background-position-x: calc(var(--s) * -7); }
	}
	@media (prefers-reduced-motion: reduce) {
		.lv-fx { animation: none; background-position-x: calc(var(--s) * -7); }
	}
</style>
