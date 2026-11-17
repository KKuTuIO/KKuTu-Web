<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import TierBadge from './TierBadge.svelte';
	import { tierParts } from '../leagueModel.js';

	/**
	 * 시즌 보상표: 시즌이 끝났을 때의 최종 티어에 따른 골드와 칭호. 표는 서버가 보내 준다(`rewards`: [{id, gold, title}], 낮은 티어부터).
	 * 프리시즌에는 지급하지 않는다.
	 */
	let { open = $bindable(false), rewards = [], preseason = false } = $props();
	const rows = $derived([...rewards].reverse());
	const name = (id) => {
		const { group, step } = tierParts(id);
		return group ? `${t(`play.league.tier.${group}`)} ${step}` : id;
	};
</script>

<Dialog bind:open title={t('play.league.reward.heading')} size="md">
	<div class="flex flex-col gap-3">
		<p class="text-sm font-semibold text-(color:--kk-muted)">{t('play.league.reward.desc')}</p>
		{#if preseason}<p class="rounded-xl bg-(--kk-surface-2) px-3 py-2 text-sm font-extrabold">{t('play.league.reward.pre')}</p>{/if}
		<ul class="flex max-h-[min(52vh,26rem)] flex-col gap-1.5 overflow-y-auto pr-1">
			{#each rows as r (r.id)}
				<li class="flex items-center gap-3 rounded-xl border border-(color:--kk-line) px-3 py-1.5">
					<TierBadge tier={r.id} size={36} />
					<span class="min-w-0 flex-1 truncate font-extrabold">{name(r.id)}</span>
					{#if r.title}<span class="shrink-0 rounded-full bg-(--kk-surface-2) px-2.5 py-0.5 text-xs font-black">{t('play.league.reward.titleOf', { group: t(`play.league.tier.${r.title.toUpperCase()}`) })}</span>{/if}
					<span class="shrink-0 font-black tabular-nums">{t('play.league.reward.gold', { n: r.gold.toLocaleString() })}</span>
				</li>
			{/each}
		</ul>
	</div>
</Dialog>
