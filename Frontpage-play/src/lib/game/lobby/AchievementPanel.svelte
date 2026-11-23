<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Select from '../ui/Select.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import AchievementGradeDialog from './AchievementGradeDialog.svelte';
	import GradeBadge from './GradeBadge.svelte';
	import { view, summarize, filterItems, sortItems, gradeProgress } from '../achievement.js';
	import { gradeAccent, NO_GRADE_ACCENT } from '../achievementGrade.js';
	import { confirm, notify } from '../ui/confirm.svelte.js';

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
	const accent = $derived(ready && data.grade ? gradeAccent(data.grade.id) : NO_GRADE_ACCENT);
	let guideOpen = $state(false);
	// 프로필에 올려 둔 업적(최대 2개): 달성한 업적 카드를 누르면 자랑할지 묻는다
	const pinned = $derived(store.net.users[store.net.id]?.showcase ?? []);
	async function toggleShowcase(item, v) {
		if (!item.ti) return;
		if (pinned.some((e) => e.k === item.k)) {
			if (await confirm({ message: t('play.achievement.showcase.confirmRemove', { name: v.title }) })) store.setShowcase(item.k, false);
			return;
		}
		if (pinned.length >= 2) return void (await notify({ message: t('play.achievement.showcase.full') }));
		if (await confirm({ message: t('play.achievement.showcase.confirmAdd', { name: v.title }) })) store.setShowcase(item.k, true);
	}
	const RING_R = 44;
	const RING_C = 2 * Math.PI * RING_R;
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
		<div class="flex items-center gap-4 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) p-3">
			<!-- 명성 자체가 링 게이지: 다음 등급까지 얼마나 왔는지를 링이 채워지는 만큼으로 보여 준다(색은 지금 등급의 색) -->
			<div class="relative size-24 shrink-0" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={bar} aria-label="{t('play.achievement.points')} {data.points}">
				<svg viewBox="0 0 100 100" class="size-full -rotate-90" aria-hidden="true">
					<circle cx="50" cy="50" r={RING_R} fill="none" stroke-width="9" style:stroke="var(--kk-line)" />
					<circle cx="50" cy="50" r={RING_R} fill="none" stroke-width="9" stroke-linecap="round" stroke-dasharray={RING_C} stroke-dashoffset={RING_C * (1 - bar / 100)} style:stroke={accent[1]} class="transition-[stroke-dashoffset] duration-500" />
				</svg>
				<span class="absolute inset-0 flex flex-col items-center justify-center leading-none">
					<b class="tabular-nums {data.points >= 10000 ? 'text-lg' : data.points >= 1000 ? 'text-xl' : 'text-2xl'}">{commify(data.points)}</b>
					<span class="mt-1 text-[11px] font-bold text-(color:--kk-muted)">{t('play.achievement.points')}</span>
				</span>
			</div>
			<div class="flex min-w-0 flex-1 flex-col gap-1">
				<div class="flex items-center gap-1.5">
					<GradeBadge id={data.grade?.id ?? null} size={40} label={data.grade?.name ?? t('play.achievement.noGrade')} />
					<b class="text-lg" style:color={accent[0]}>{data.grade?.name ?? t('play.achievement.noGrade')}</b>
					<Tooltip text={t('play.achievement.guideOpen')}>
						<button type="button" aria-label={t('play.achievement.guideOpen')} onclick={() => (guideOpen = true)} class="grid size-7 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2)"><Icon name="info" size={18} fill /></button>
					</Tooltip>
				</div>
				<span class="text-xs text-(color:--kk-muted) tabular-nums">{data.next ? t('play.achievement.untilNext', { n: commify(data.next.from - data.points) }) : t('play.achievement.maxGrade')}</span>
				<p class="mt-1 flex items-center gap-1.5 text-sm font-bold tabular-nums"><Icon name="emoji_events" size={18} fill />{t('play.achievement.doneCount', { done: sum.done, total: sum.total })}</p>
			</div>
		</div>
		<AchievementGradeDialog bind:open={guideOpen} grades={data.grades ?? []} points={data.points} current={data.grade?.id ?? null} />

		<div class="flex flex-wrap items-center gap-2">
			<input bind:value={query} maxlength="40" autocomplete="off" aria-label={t('play.achievement.search')} placeholder={t('play.achievement.search')} class="h-10 min-w-40 flex-1 kk-input px-3" />
			<div class="w-44"><Select options={sortOptions} bind:value={sort} label={t('play.achievement.sortLabel')} /></div>
		</div>

		{#if shown.length}
			<ul class="grid gap-2.5 md:grid-cols-2">
				{#each shown as item (item.k)}
					{@const v = view(item)}
					<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_to_interactive_role -->
					<li
							class="flex flex-col overflow-hidden rounded-xl border bg-(--kk-surface) {pinned.some((e) => e.k === item.k) ? 'border-(color:--kk-accent)' : 'border-(color:--kk-line)'} {item.ti ? 'cursor-pointer hover:bg-(--kk-surface-2)' : ''}"
							data-done={v.done}
							role={item.ti ? 'button' : undefined}
							tabindex={item.ti ? 0 : undefined}
							onclick={() => toggleShowcase(item, v)}
							onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), toggleShowcase(item, v))}
						>
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
									<b class="line-clamp-2 min-w-0 text-sm leading-snug break-keep" title={v.title}>{v.title}</b>
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
