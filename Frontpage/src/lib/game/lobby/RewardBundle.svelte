<script>
	import GameIcon from './GameIcon.svelte';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import RewardChip from './RewardChip.svelte';
	import ShopThumb from './ShopThumb.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import { representative, rewardOf } from '../news.js';
	import { rewardLabel } from '../newsFormat.js';

	/**
	 * A bundle of rewards as one square: the picture of one of them as large as the square allows, its amount in the corner ("x3"), and when the bundle has
	 * more, a "+n" badge; pressing it lists everything in the bundle in a window. A small row of chips has no room for a picture, and the pictures of
	 * items are wide (a ticket is not square), so they were hard to tell apart; this gives one of them the whole square.
	 * The one shown is an item if there is one, else event points, and 핑 only when it is all the bundle has (see `representative`).
	 * `plain`: only the picture, for a place that is a button itself (a button cannot hold another).
	 * @type {{rewards:any[], shop?:any, details?:any, size?:number, dim?:boolean, plain?:boolean}}
	 */
	let { rewards, shop = {}, details = null, size = 72, dim = false, plain = false } = $props();
	const list = $derived((rewards ?? []).map(rewardOf));
	const rep = $derived(representative(list));
	let asked = $state(false); // the window is made the first time it is wanted
	let open = $state(false);
	const many = $derived((rep?.more ?? 0) > 0);
	const qty = (r) => Number(r.q).toLocaleString();
	const name = $derived(rep ? rewardLabel(rep.main, details) : '');
	// the amount is as big as the square likes (a quarter of it) but never wider than the square: a long number gets smaller, so it cannot overflow
	const amountSize = $derived(rep ? Math.max(9, Math.min(Math.round(size * 0.2), Math.floor((size * 0.9) / (`x${qty(rep.main)}`.length * 0.62)))) : 0);
</script>

{#snippet face()}
	<span style:width="{size}px" style:height="{size}px" class="relative block shrink-0 overflow-hidden rounded-xl border border-white/20 bg-gradient-to-b from-[#6b7388] to-[#3b4256] shadow-[0_2px_0_rgb(0_0_0/0.3)] {dim ? 'opacity-45' : ''}">
		<span class="absolute inset-0 grid place-items-center p-[7%]">
			{#if rep.main.kind === 'item' || rep.main.kind === 'letter'}<ShopThumb id={rep.main.id ?? ''} {shop} class="size-full" />
			{:else if rep.main.kind === 'ping'}<GameIcon name="ping" size={Math.round(size * 0.62)} />
			{:else}<Icon name="stars" size={Math.round(size * 0.6)} fill class="text-(color:--kk-warn)" />{/if}
		</span>
		{#if rep.main.q > 1}<b class="pointer-events-none absolute right-1 bottom-0.5 leading-none font-extrabold text-white tabular-nums [text-shadow:0_1px_0_rgb(0_0_0/0.9),0_0_3px_rgb(0_0_0/0.8)]" style:font-size="{amountSize}px">x{qty(rep.main)}</b>{/if}
		{#if many}<span class="pointer-events-none absolute top-0.5 left-0.5 rounded-full bg-black/70 px-1.5 text-[11px] leading-4 font-extrabold text-white tabular-nums">+{rep.more}</span>{/if}
	</span>
{/snippet}

{#if rep}
	{#if plain}
		<span class="block shrink-0">{@render face()}</span>
	{:else if many}
		<Tooltip text={t('play.news.bundle.open', { n: list.length })} placement="top">
			<button type="button" aria-label={t('play.news.bundle.aria', { name, n: rep.more })} onclick={() => ((asked = true), (open = true))} class="block shrink-0 rounded-xl transition-transform hover:-translate-y-px focus-visible:outline-2 focus-visible:outline-(--kk-accent) active:translate-y-px">{@render face()}</button>
		</Tooltip>
	{:else}
		<Tooltip text={name} placement="top"><span role="img" aria-label="{name}{rep.main.q > 1 ? ` ×${qty(rep.main)}` : ''}" class="block shrink-0">{@render face()}</span></Tooltip>
	{/if}
	{#if asked}
		<Dialog bind:open title={t('play.news.bundle.title')}>
			<ul class="flex max-h-[60dvh] flex-col gap-2 overflow-y-auto">
				{#each list as r, i (i)}
					<li class="flex items-center gap-3 rounded-xl bg-(--kk-surface-2) p-2">
						<RewardChip reward={r} {shop} {details} size={44} />
						<span class="min-w-0 flex-1 truncate font-bold">{rewardLabel(r, details)}</span>
						<b class="shrink-0 tabular-nums">×{qty(r)}</b>
					</li>
				{/each}
			</ul>
		</Dialog>
	{/if}
{/if}
