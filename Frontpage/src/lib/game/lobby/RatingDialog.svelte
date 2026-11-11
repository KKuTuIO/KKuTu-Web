<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import RatingIcon from './RatingIcon.svelte';
	import Icon from './Icon.svelte';
	import { ratingView, RATING_COLORS } from './ratingDialog.svelte.js';

	/**
	 * 평점: the five tiers, best first, with the current one tinted, and (on my own) what makes it better or worse. No numbers on purpose: the
	 * thresholds are not told, only which way things move.
	 */
	const TIERS = [4, 3, 2, 1, 0];
	const TIPS = [
		['trending_up', 'play.rating.tip.up'],
		['trending_down', 'play.rating.tip.leave'],
		['gavel', 'play.rating.tip.policy'],
		['handshake', 'play.rating.tip.friendly']
	];
</script>

<Dialog bind:open={ratingView.open} title={t('play.rating.title')}>
	<div class="flex flex-col gap-4">
		<ol class="flex flex-col gap-1.5" aria-label={t('play.rating.title')}>
			{#each TIERS as tier (tier)}
				{@const now = tier === ratingView.rating}
				<li aria-current={now ? 'true' : undefined} class="flex items-center gap-3 rounded-xl border-[1.5px] px-3 py-2.5 transition-colors {now ? '' : 'border-transparent opacity-70'}" style={now ? `background: color-mix(in srgb, ${RATING_COLORS[tier]} 14%, transparent); border-color: ${RATING_COLORS[tier]}` : ''}>
					<RatingIcon rating={tier} size={24} />
					<span class="min-w-0 flex-1 leading-tight">
						<b class="block text-sm font-extrabold">{t(`play.rating.label.${tier}`)}</b>
						<span class="block text-xs text-(color:--kk-muted)">{t(`play.rating.tier.${tier}`)}</span>
					</span>
					{#if now}<span class="shrink-0 rounded-full px-2.5 py-0.5 text-xs font-extrabold text-white" style="background: {RATING_COLORS[tier]}">{t(ratingView.mine ? 'play.rating.mine' : 'play.rating.theirs')}</span>{/if}
				</li>
			{/each}
		</ol>

		<section class="flex flex-col gap-2 rounded-xl bg-(--kk-surface-2) p-3">
			<h3 class="text-sm font-extrabold">{t(ratingView.mine ? 'play.rating.how' : 'play.rating.about')}</h3>
			<ul class="flex flex-col gap-1.5 text-xs leading-relaxed text-(color:--kk-muted)">
				{#each TIPS as [icon, key] (key)}
					<li class="flex gap-2"><Icon name={icon} size={16} class="mt-px shrink-0" />{t(key)}</li>
				{/each}
				{#if ratingView.mine && ratingView.rating < 2}
					<li class="flex gap-2 font-bold text-(color:--kk-danger)"><Icon name="warning" size={16} class="mt-px shrink-0" />{t('play.rating.tip.limit')}</li>
				{/if}
			</ul>
		</section>
	</div>
</Dialog>
