<script>
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import ShopThumb from './ShopThumb.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { rewardLabel } from '../newsFormat.js';
	import { t } from '../i18n.svelte.js';

	/**
	 * One reward. An item is a square tile (the picture, its whole amount on the corner, the name in the tooltip).
	 * A currency (핑, 행사 포인트) is a pill that grows with its amount, because it runs to 3,000 or 10,000 and a square would
	 * clip it: the icon and the whole number side by side. The tooltip always has the full amount.
	 * @type {{reward:{kind:'ping'|'ep'|'xp'|'item'|'letter', id?:string, tier?:string, q:number}, shop?:any, details?:any, size?:number}}
	 */
	let { reward, shop = {}, details = null, size = 40 } = $props();
	const label = $derived(rewardLabel(reward, details));
	const currency = $derived(reward.kind === 'ping' || reward.kind === 'ep' || reward.kind === 'xp');
	const full = $derived(Number(reward.q).toLocaleString());
</script>

<Tooltip text="{label} {full}" placement="top">
	{#if currency}
		<span role="img" aria-label="{label} {full}" style:height="{size}px" style:font-size="{Math.max(12, Math.round(size * 0.38))}px" class="inline-flex shrink-0 items-center gap-1 rounded-xl border border-(color:--kk-line) bg-(--kk-surface-2) pr-2.5 pl-1.5 font-extrabold whitespace-nowrap tabular-nums">
			{#if reward.kind === 'ping'}<GameIcon name="ping" size={Math.round(size * 0.62)} />{:else if reward.kind === 'xp'}<GameIcon name="xp" size={Math.round(size * 0.62)} />{:else}<Icon name="stars" size={Math.round(size * 0.6)} fill class="text-(color:--kk-warn)" />{/if}
			{full}
		</span>
	{:else}
		<span role="img" aria-label="{label} ×{full}" style:width="{size}px" style:height="{size}px" class="relative inline-grid shrink-0 place-items-center rounded-xl border border-(color:--kk-line) bg-(--kk-surface-2)">
			<ShopThumb id={reward.id ?? ''} {shop} class="size-4/5" />
			{#if reward.q > 1}<b class="pointer-events-none absolute right-0.5 bottom-0 rounded-md bg-(--kk-surface)/90 px-1 text-[10px] leading-4 whitespace-nowrap tabular-nums">×{full}</b>{/if}
		</span>
	{/if}
</Tooltip>
