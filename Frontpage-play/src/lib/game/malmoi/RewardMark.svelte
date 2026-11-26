<script>
	import { t } from '../i18n.svelte.js';
	import GameIcon from '../lobby/GameIcon.svelte';
	import Icon from '../lobby/Icon.svelte';

	/**
	 * What a 말모이 room pays, as the pictures the 해커레미 uses: the 경험치 and 핑 icons side by side, a badge at their lower right (a red ✕ for
	 * nothing, the share on the accent colour for 75%) and the words beside them. `ring` is the colour behind it (a tooltip is dark, a sheet is light).
	 * @type {{share: number, size?: number, ring?: string}}
	 */
	let { share, size = 22, ring = 'var(--kk-surface)' } = $props();
	const none = $derived(share <= 0);
	const pct = $derived(`${Math.round(share * 100)}%`);
</script>

<span class="inline-flex items-center gap-2 text-sm font-bold">
	<span class="relative inline-flex items-center pr-2 pb-1.5" aria-hidden="true">
		<GameIcon name="xp" {size} />
		<span class="-ml-1.5 rounded-full" style:box-shadow="0 0 0 2px {ring}"><GameIcon name="ping" {size} /></span>
		{#if none}
			<span class="absolute right-0 bottom-0 grid place-items-center rounded-full bg-(--kk-danger) text-white" style:width="{size * 0.6}px" style:height="{size * 0.6}px" style:box-shadow="0 0 0 1.5px {ring}"><Icon name="close" size={size * 0.46} /></span>
		{:else}
			<span class="absolute right-0 bottom-0 rounded-full bg-(--kk-accent) px-1 text-[0.65rem] leading-4 font-black text-white tabular-nums" style:box-shadow="0 0 0 1.5px {ring}">{pct}</span>
		{/if}
	</span>
	<span>{t('play.malmoi.reward.text', { value: none ? t('play.malmoi.common.none') : pct })}</span>
</span>
