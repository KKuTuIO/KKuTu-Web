<script>
	import RewardChip from './RewardChip.svelte';
	import { t } from '../i18n.svelte.js';
	import { DEFAULT_BANNER, eventPhase, limitList, rewardOf, safeAccent, safeUrl, sectionOf } from '../news.js';
	import { whenText } from '../newsFormat.js';

	/**
	 * An event as a card: banner (or a colour plate), state, how long it goes, a line about it and its main rewards.
	 * The card never decides its own width: the grid around it does, so the same card serves any width.
	 * @type {{event:any, now:number, shop?:any, details?:any, selected?:boolean, fresh?:boolean, onpick:(id:string)=>void}}
	 */
	let { event, now, shop = {}, details = null, selected = false, fresh = false, onpick } = $props();
	const phase = $derived(eventPhase(event, now));
	const look = $derived(event.presentation ?? {});
	const accent = $derived(safeAccent(look.accent));
	const banner = $derived(safeUrl(look.banner) || DEFAULT_BANNER);
	const rewards = $derived(limitList((look.rewards ?? []).map(rewardOf), 5));
	// what kind it is (the list is one list, so the card says it): 새내기/복귀, 캠페인, 행사 or 공지
	const kind = $derived(t(`play.news.filter.${sectionOf(event) === 'audience' ? event.audience.kind : sectionOf(event)}`));
	const PILL = { ongoing: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)', upcoming: 'bg-(--kk-warn-soft) text-(color:--kk-warn)', ended: 'bg-(--kk-surface-2) text-(color:--kk-muted)' };
</script>

<button
	type="button"
	aria-pressed={selected}
	onclick={() => onpick(event.id)}
	style:--ac={accent || undefined}
	class="group flex w-full min-w-0 flex-col overflow-hidden rounded-(--kk-r-md) border-[1.5px] bg-(--kk-surface) text-left shadow-(--kk-shadow) transition-transform active:translate-y-px active:shadow-none {selected ? 'border-(--ac,var(--kk-accent))' : 'border-(color:--kk-line)'} {phase === 'ended' ? 'opacity-70' : ''}"
>
	<span class="relative block aspect-video w-full overflow-hidden bg-(--kk-surface-2)">
		<img src={banner} alt="" loading="lazy" decoding="async" draggable="false" class="size-full object-cover transition-transform duration-300 group-hover:scale-[1.03]" />
		<span class="absolute top-2 left-2 flex items-center gap-1">
			<span class="rounded-full px-2.5 py-0.5 text-xs font-extrabold {PILL[phase]}">{t(`play.news.phase.${phase}`)}</span>
			<span class="rounded-full bg-black/60 px-2.5 py-0.5 text-xs font-extrabold text-white">{kind}</span>
		</span>
		{#if fresh}<span class="absolute top-2 right-2 rounded-full bg-(--kk-danger) px-2 py-0.5 text-[10px] font-extrabold text-white" aria-label={t('play.news.fresh')}>N</span>{/if}
	</span>
	<span class="flex flex-1 flex-col gap-1.5 p-3">
		<b class="line-clamp-2 text-base leading-snug font-extrabold">{event.name}</b>
		<span class="text-xs font-bold text-(color:--kk-muted) tabular-nums">{whenText(event, now)}</span>
		{#if look.summary}<span class="line-clamp-2 text-[13px] leading-snug">{look.summary}</span>{/if}
		{#if rewards.shown.length}
			<span class="mt-auto flex items-center gap-1.5 pt-1.5">
				{#each rewards.shown as r, i (i)}<RewardChip reward={r} {shop} {details} size={34} />{/each}
				{#if rewards.more}<span class="text-xs font-extrabold text-(color:--kk-muted) tabular-nums">{t('play.news.rewardsMore', { n: rewards.more })}</span>{/if}
			</span>
		{/if}
	</span>
</button>
