<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Select from '../ui/Select.svelte';
	import { view, summarize, filterItems, sortItems, gradeProgress } from '../achievement.js';

	/**
	 * 프로필의 업적 탭(본인만): 명성과 등급, 완료한 업적 수, 업적 카드 목록. 서버가 보낸 `store.net.achievements`를 그린다.
	 * 카드는 진행 중인 단계(없으면 마지막 단계)의 명성과 조건, 지금 값을 보여 주고, 단계 칩(Ⅰ~)으로 어디까지 왔는지 보여 준다.
	 */
	let { store } = $props();
	const data = $derived(store.net.achievements);
	let query = $state('');
	let sort = $state('progress');
	const sortOptions = $derived([
		{ value: 'progress', label: t('play.achievement.sort.progress') },
		{ value: 'recent', label: t('play.achievement.sort.recent') }
	]);
	const ready = $derived(!!data && !data.off);
	const list = $derived(ready ? (data.list ?? []) : []);
	const shown = $derived(sortItems(filterItems(list, query), sort));
	const sum = $derived(summarize(list));
	const bar = $derived(ready ? gradeProgress(data.points, data.grade, data.next) : 0);
	const commify = (n) => Number(n).toLocaleString('ko-KR');
	// 회원 중 이 단계를 달성한 비율(%): 서버가 집계할 만큼 회원이 모인 뒤에만 오고, 그 전에는 아무것도 보여 주지 않는다
	const rateOf = (key, tier) => {
		const v = data?.rates?.[key]?.[tier - 1];
		return typeof v === 'number' ? v : null;
	};
	const rateText = (p) => (p < 0.1 ? t('play.achievement.rateLow') : t('play.achievement.rate', { n: p.toLocaleString('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) }));
</script>

{#if !ready}
	<p class="py-8 text-center text-sm text-(color:--kk-muted)">{t('play.achievement.loading')}</p>
{:else}
	<div class="flex flex-col gap-3">
		<div class="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) p-3">
			<div class="grid size-16 shrink-0 place-items-center rounded-2xl bg-(--kk-accent) text-white" aria-label="{t('play.achievement.points')} {data.points}">
				<span class="flex flex-col items-center leading-none">
					<b class="text-xl tabular-nums">{commify(data.points)}</b>
					<span class="mt-1 text-[10px] font-bold">{t('play.achievement.points')}</span>
				</span>
			</div>
			<div class="flex min-w-48 flex-1 flex-col gap-1.5">
				<div class="flex flex-wrap items-baseline gap-x-2">
					<b class="text-base">{data.grade?.name ?? t('play.achievement.noGrade')}</b>
					<span class="text-xs text-(color:--kk-muted) tabular-nums">{data.next ? t('play.achievement.untilNext', { n: commify(data.next.from - data.points) }) : t('play.achievement.maxGrade')}</span>
				</div>
				<div class="h-2 overflow-hidden rounded-full bg-(--kk-line)" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={bar}>
					<div class="h-full rounded-full bg-(--kk-accent)" style:width="{bar}%"></div>
				</div>
			</div>
			<p class="flex items-center gap-1.5 text-sm font-bold tabular-nums"><Icon name="emoji_events" size={18} fill />{t('play.achievement.doneCount', { done: sum.done, total: sum.total })}</p>
		</div>

		<div class="flex flex-wrap items-center gap-2">
			<input bind:value={query} maxlength="40" autocomplete="off" aria-label={t('play.achievement.search')} placeholder={t('play.achievement.search')} class="h-10 min-w-40 flex-1 kk-input px-3" />
			<div class="w-44"><Select options={sortOptions} bind:value={sort} label={t('play.achievement.sortLabel')} /></div>
		</div>

		{#if shown.length}
			<ul class="grid gap-2.5 md:grid-cols-2">
				{#each shown as item (item.k)}
					{@const v = view(item)}
					<li class="flex flex-col overflow-hidden rounded-xl border border-(color:--kk-line) bg-(--kk-surface)" data-done={v.done}>
						<div class="flex items-start gap-2.5 p-2.5">
							<span class="grid size-12 shrink-0 place-items-center rounded-lg {v.done ? 'bg-(--kk-accent) text-white' : 'bg-(--kk-surface-2) text-(color:--kk-text)'}">
								<span class="flex flex-col items-center leading-none">
									<b class="text-base tabular-nums">{v.points}</b>
									<span class="mt-0.5 text-[9px] font-bold">{t('play.achievement.points')}</span>
								</span>
							</span>
							<div class="flex min-w-0 flex-1 flex-col gap-1">
								<div class="flex min-w-0 items-center gap-1.5">
									{#if v.hidden}<Icon name="lock_open" size={15} class="shrink-0 text-(color:--kk-muted)" />{/if}
									<b class="min-w-0 truncate text-sm break-keep" title={v.title}>{v.title}</b>
									{#if v.rare}<span class="shrink-0 rounded-md bg-(--kk-accent-soft) px-1 text-[10px] font-bold text-(color:--kk-accent)">{t('play.achievement.rare')}</span>{/if}
								</div>
								{#if v.count > 1}
									<div class="flex flex-wrap gap-1" aria-label={t('play.achievement.tierOf', { tier: v.tier, count: v.count })}>
										{#each v.chips as c (c.label)}
											<span class="grid h-5 min-w-5 place-items-center rounded px-1 text-[10px] font-bold {c.state === 'done' ? 'bg-(--kk-accent) text-white' : c.state === 'active' ? 'border border-(color:--kk-accent) text-(color:--kk-accent)' : 'border border-(color:--kk-line) text-(color:--kk-muted)'}">{c.label}</span>
										{/each}
									</div>
								{/if}
							</div>
						</div>
						<div class="mt-auto flex flex-col gap-1.5 border-t border-dashed border-(color:--kk-line) px-2.5 py-2">
							<div class="flex items-baseline justify-between gap-2 text-xs">
								<span class="min-w-0 break-keep">{v.cond}</span>
								<b class="shrink-0 tabular-nums {v.done ? 'text-(color:--kk-accent)' : ''}">{v.done ? t('play.achievement.done') : v.text}</b>
							</div>
							<div class="h-1.5 overflow-hidden rounded-full bg-(--kk-line)" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={v.pct} aria-label={v.title}>
								<div class="h-full rounded-full bg-(--kk-accent)" style:width="{v.pct}%"></div>
							</div>
							{#if rateOf(item.k, v.tier) !== null}
								<p class="text-right text-[11px] text-(color:--kk-muted) tabular-nums">{rateText(rateOf(item.k, v.tier))}</p>
							{/if}
						</div>
					</li>
				{/each}
			</ul>
		{:else}
			<p class="py-8 text-center text-sm text-(color:--kk-muted)">{t('play.achievement.none')}</p>
		{/if}
	</div>
{/if}
