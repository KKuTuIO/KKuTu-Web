<script>
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import { prefs } from '../prefs.svelte.js';
	import { badgeUrl, badgeName, tierParts, BADGE_RATIO } from '../leagueModel.js';

	/**
	 * 트로피 리그 티어 뱃지(CDN의 SVG). `tier`는 서버의 티어 id(`GOLD_II`) 또는 특수 뱃지 이름(`master-star`, `unranked-dash`, `unranked-question`).
	 * `size`는 높이(px). 마스터는 빛줄기·반짝임이 도는 판을 쓰고, 움직임 감소 설정이면 정지판을 쓴다.
	 */
	let { tier, size = 40 } = $props();
	const name = $derived(badgeName(tier));
	const src = $derived(badgeUrl(tier, { motion: !prefs.reduceMotion }));
	const label = $derived.by(() => {
		const { group, step } = tierParts(tier);
		if (group) return `${t(`play.league.tier.${group}`)} ${step}`;
		return name.startsWith('unranked') ? t('play.league.unranked') : name ? t('play.league.tier.MASTER') : '';
	});
</script>

{#if src}
	<Tooltip text={label}><img {src} alt={label} height={size} width={Math.round(size * BADGE_RATIO)} draggable="false" class="inline-block shrink-0 select-none" /></Tooltip>
{/if}
