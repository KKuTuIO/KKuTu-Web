<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { league } from '../leagueStore.svelte.js';
	import { tierParts, leagueClock } from '../leagueModel.js';
	import { hmsText } from '../news.js';
	import Icon from './Icon.svelte';
	import TierBadge from './TierBadge.svelte';
	import LeagueRewardDialog from './LeagueRewardDialog.svelte';
	import LeagueGuideDialog from './LeagueGuideDialog.svelte';

	/**
	 * 트로피 리그 전용 로비. 로비 무대 위에 그대로 올라온다(내 모레미는 무대 가운데에 서 있다).
	 * 맨 위 가운데에 시즌 이름과 기간, 오른쪽 위에 행사 탭의 머리줄처럼 따로따로 놓인 랭킹·보상 버튼과 오늘 남은 시간 칩(행사 탭과 같은 `kk-hdr-time`),
	 * 그 아래에 내 등급(티어 뱃지와 트로피), 왼쪽 위에 나가기. 오른쪽 아래의 매칭 시작·설정 버튼과 오늘의 혜택 말풍선은 로비 무대(MatchHome)가 그린다.
	 * 상태(시즌·운영 시간·내 티어)는 서버가 보내 주고 여기서는 그리기만 한다.
	 * `variant`는 'desk' | 'phone', `onback`은 모드 선택으로, `onranking`은 트로피 랭킹을 연다.
	 */
	let { store, variant = 'desk', onback, onranking } = $props();
	const s = $derived(league.state);
	const guest = $derived(store.net.guest);
	const phone = $derived(variant === 'phone');
	let rewardOpen = $state(false);
	let guideOpen = $state(false);

	// 남은 시간은 서버가 알려 준 시각에 맞춰(이 기기의 시계가 어긋나 있어도) 1초마다 센다
	let tick = $state(Date.now());
	let skew = $state(0);
	$effect(() => {
		if (s) skew = s.now - Date.now();
	});
	onMount(() => {
		if (guest) return;
		league.fetch();
		const poll = setInterval(() => league.fetch(), 30_000);
		const clock = setInterval(() => (tick = Date.now()), 1000);
		return () => {
			clearInterval(poll);
			clearInterval(clock);
		};
	});

	const tierName = $derived.by(() => {
		const { group, step } = tierParts(s?.mine.tierId);
		return group ? `${t(`play.league.tier.${group}`)} ${step}` : '';
	});
	const clock = $derived.by(() => {
		const c = leagueClock(tick + skew);
		return { open: c.open, text: c.open ? hmsText(c.ms) : t('play.league.clock.openShort', { time: hmsText(c.ms) }) };
	});
</script>

<!-- 매칭 대기 중에 창을 닫거나 새로고침하면 평점이 깎이므로, 브라우저의 기본 경고를 띄운다(문구는 브라우저가 정한다) -->
<svelte:window onbeforeunload={(e) => league.queued && (e.preventDefault(), (e.returnValue = ''))} />

<div role="dialog" aria-modal="true" aria-label={t('play.lobby.trophyLeague')} class="league absolute inset-0 z-30 flex flex-col p-4 max-md:p-3 max-md:pb-[calc(var(--dock,0px)+6rem)]">
	<!-- 맨 위 줄: 나가기 · 제목 ········ 랭킹 · 보상 · 오늘 남은 시간 (행사 탭의 머리줄처럼 각자 따로) -->
	<header class="flex shrink-0 items-center gap-3">
		<button type="button" aria-label={t('play.dialog.back')} onclick={onback} class="grid size-11 shrink-0 place-items-center rounded-full bg-white/90 text-slate-700 shadow-md transition-transform hover:scale-105 hover:bg-white"><Icon name="arrow_back" size={24} /></button>
		<h2 class="text-2xl font-black tracking-tight whitespace-nowrap text-white max-md:sr-only">{t('play.lobby.trophyLeague')}</h2>
		{#if !guest && s}
			<div class="ml-auto flex items-center gap-2">
				<button type="button" onclick={onranking} aria-label={t('play.league.rankingShort')} class="kk-btn kk-hdr"><Icon name="trophy" size={16} /><span class="max-md:sr-only">{t('play.league.rankingShort')}</span></button>
				<button type="button" onclick={() => (rewardOpen = true)} aria-label={t('play.league.reward')} class="kk-btn kk-hdr"><Icon name="redeem" size={16} /><span class="max-md:sr-only">{t('play.league.reward')}</span></button>
				<button type="button" onclick={() => (guideOpen = true)} aria-label={t('play.league.guide.button')} class="kk-btn kk-hdr"><Icon name="help" size={16} /><span class="max-md:sr-only">{t('play.league.guide.button')}</span></button>
				<span class="kk-hdr kk-hdr-time max-md:!w-auto" role="timer" aria-label={t(clock.open ? 'play.league.clock.leftLabel' : 'play.league.clock.openLabel')} title={t(clock.open ? 'play.league.clock.leftLabel' : 'play.league.clock.openLabel')}><Icon name="schedule" size={18} fill />{clock.text}</span>
			</div>
		{/if}
	</header>

	{#if guest}
		<p class="m-auto rounded-2xl bg-black/50 px-5 py-4 text-center text-sm font-bold text-white">{t('play.league.guestOnly')}</p>
	{:else if !s}
		<p class="ltop absolute top-4 left-1/2 -translate-x-1/2 text-sm font-bold text-white/80" aria-busy="true">{t('play.league.loading')}</p>
	{:else}
		<!-- 최상단 가운데: 시즌 이름과 기간 -->
		<div class="ltop absolute left-1/2 flex -translate-x-1/2 flex-col items-center text-center {phone ? 'top-[4.1rem]' : 'top-4'}" style="--c:#3553a0">
			<span class="kk-fire-text text-2xl leading-none max-md:text-xl" style="-webkit-text-stroke-width:6px">{s.season.preseason ? t('play.league.preseason') : s.season.name}</span>
			<span class="mt-1.5 text-xs font-extrabold text-white/90 [text-shadow:0_1px_3px_rgb(0_0_0/0.6)]">{t('play.league.period', { from: s.season.period.from, to: s.season.period.to })}</span>
		</div>
		<!-- 그 아래: 내 등급(티어 뱃지와 트로피) -->
		<div class="ltop absolute left-1/2 flex -translate-x-1/2 flex-col items-center {phone ? 'top-[8.2rem]' : 'top-[6.6rem]'}" style="--c:#3553a0">
			<span class="kk-fire-text text-lg leading-none max-md:text-base" style="-webkit-text-stroke-width:5px">{tierName}</span>
			<div class="mt-1 flex items-center gap-2.5">
				<TierBadge tier={s.mine.tierId} size={phone ? 48 : 64} />
				<span class="kk-fire-text leading-none {phone ? 'text-4xl' : 'text-5xl'}">{s.mine.trophies.toLocaleString()}</span>
			</div>
			<span class="mt-1.5 text-xs font-extrabold text-white/80 [text-shadow:0_1px_3px_rgb(0_0_0/0.6)]">{t('play.rank.recordValue', { wins: s.mine.wins, games: s.mine.games })}</span>
		</div>
	{/if}
</div>

{#if s}<LeagueRewardDialog bind:open={rewardOpen} rewards={s.rewards ?? []} preseason={s.season.preseason} />{/if}
{#if s}<LeagueGuideDialog bind:open={guideOpen} {s} />{/if}

<style>
	.league {
		pointer-events: none;
	}
	.league > * {
		pointer-events: auto;
	}
	/* 위의 등급·시즌 글자는 보여 주기만 하고, 머리 줄은 빈 곳의 클릭을 무대로 통과시킨다(그 아래 매칭 시작 버튼이 눌려야 한다) */
	.league > .ltop,
	.league > header {
		pointer-events: none;
	}
	.league > header > :global(*) {
		pointer-events: auto;
	}
	.league > header > div > :global(*) {
		pointer-events: auto;
	}
</style>
