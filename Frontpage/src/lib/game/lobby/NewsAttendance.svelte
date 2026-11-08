<script>
	import Icon from './Icon.svelte';
	import RewardBundle from './RewardBundle.svelte';
	import RewardChip from './RewardChip.svelte';
	import { t } from '../i18n.svelte.js';
	import { attendanceDone, attendanceLength, rewardOf } from '../news.js';
	import { rewardLabel } from '../newsFormat.js';

	/**
	 * The attendance book as a board of day tiles (the way a daily-login event is usually drawn): each tile has its day, its main reward and a
	 * check once it is done; the one to claim today has a ring. A tile can be picked to read everything that day gives, beside the board when
	 * the page is wide and under it when it is not.
	 * @type {{store:any, event:any, shop?:any, details?:any, playable:boolean}}
	 */
	let { store, event, shop = {}, details = null, playable } = $props();
	const net = $derived(store.net);
	const book = $derived(net.eventProgress.attendance.find((a) => a.eventId === event.id));
	const length = $derived(attendanceLength(event.attendance.TYPE));
	const done = $derived(attendanceDone(event.attendance.TYPE, book?.attended ?? 0));
	const claimed = $derived(!!book?.todayClaimed);
	const nextDay = $derived(claimed ? 0 : Math.min(length, done + 1)); // the day today's check-in fills
	let picked = $state(0);
	const focus = $derived(picked || nextDay || Math.max(1, done));
	const rewardsOf = (day) => (event.attendance.DAILY_REWARDS ?? []).find((x) => x.day === day)?.rewards.map(rewardOf) ?? [];
	let pending = $state(false);
	$effect(() => {
		net.eventProgress; // an answer ends the wait
		pending = false;
	});
	function check() {
		pending = true;
		store.claimEventAttendance(event.id);
		setTimeout(() => (pending = false), 4000);
	}
</script>

<section class="flex flex-col gap-3">
	<div class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
		<h3 class="text-base font-extrabold">{t('play.news.attendance')}</h3>
		<span class="text-xs font-bold text-(color:--kk-muted) tabular-nums">{t('play.news.attendanceCount', { n: book?.attended ?? 0 })}</span>
		<button type="button" disabled={!playable || claimed || pending} onclick={check} class="kk-btn kk-btn-primary ml-auto inline-flex h-9 min-h-0 items-center gap-1.5 px-4 text-sm">{claimed ? t('play.news.attendanceDone') : t('play.news.attendanceCheck')}</button>
	</div>
	<div class="flex flex-col gap-3 @2xl:flex-row @2xl:items-start">
		<ol class="grid min-w-0 flex-1 grid-cols-[repeat(auto-fill,minmax(6.25rem,1fr))] gap-2">
			{#each Array.from({ length }, (_, i) => i + 1) as day (day)}
				{@const rewards = rewardsOf(day)}
				<li class="flex">
					<button
						type="button"
						aria-pressed={day === focus}
						aria-label={t('play.news.attendanceDay', { day })}
						onclick={() => (picked = day)}
						class="relative flex aspect-square w-full flex-col items-center justify-center rounded-xl border-[1.5px] px-1 {day <= done ? 'border-(color:--kk-line) bg-(--kk-surface-2) opacity-75' : 'border-(color:--kk-line) bg-(--kk-surface)'} {day === nextDay ? 'ring-2 ring-(--ac,var(--kk-accent))' : ''} {day === focus ? 'border-(--ac,var(--kk-accent)) shadow-[0_2px_0_var(--ac,var(--kk-accent))]' : ''}"
					>
						<span class="absolute top-1.5 left-2 text-[11px] font-extrabold text-(color:--kk-muted) italic tabular-nums">{day}</span>
						{#if rewards.length}<span class="mt-3"><RewardBundle rewards={event.attendance.DAILY_REWARDS.find((x) => x.day === day).rewards} {shop} {details} size={60} plain /></span>{/if}
						{#if day <= done}<Icon name="check_circle" size={20} fill class="absolute right-1 bottom-1 text-(color:--kk-accent-strong)" />{/if}
					</button>
				</li>
			{/each}
		</ol>
		<aside aria-label={t('play.news.attendanceRewards', { day: focus })} class="flex shrink-0 flex-col gap-2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface-2) p-3 @2xl:w-60">
			<b class="rounded-md bg-(--kk-surface) px-2 py-1 text-center text-xs font-extrabold">{t('play.news.attendanceRewards', { day: focus })}</b>
			<ul class="flex flex-col gap-1.5">
				{#each rewardsOf(focus) as reward, i (i)}
					<li class="flex items-center gap-2"><RewardChip {reward} {shop} {details} size={34} /><span class="min-w-0 flex-1 truncate text-[13px] font-bold">{rewardLabel(reward, details)}</span>{#if focus <= done}<Icon name="check_circle" size={18} fill class="shrink-0 text-(color:--kk-accent-strong)" />{/if}</li>
				{:else}
					<li class="text-xs text-(color:--kk-muted)">{t('play.news.attendanceNoReward')}</li>
				{/each}
			</ul>
		</aside>
	</div>
</section>
