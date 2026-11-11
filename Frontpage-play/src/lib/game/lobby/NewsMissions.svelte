<script>
	import Icon from './Icon.svelte';
	import Mascot from './Mascot.svelte';
	import RewardBundle from './RewardBundle.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import { t } from '../i18n.svelte.js';
	import { missionEntry, missionStatus, periodsOf, ratio, readyCount } from '../news.js';

	/**
	 * The missions of an event as cards, the ones that can be claimed first. When they run on more than one period (daily, weekly, the whole event) a
	 * tab per period tells which; the tab carries a red count of what waits to be claimed, and 모두 받기 takes everything that is done. A mission that
	 * is not done yet and says where it can be done has a 이동 button that goes there. An event for newcomers has 학사모레미 say a line above them.
	 * @type {{store:any, event:any, shop?:any, details?:any, playable:boolean}}
	 */
	let { store, event, shop = {}, details = null, playable } = $props();
	const net = $derived(store.net);
	const missions = $derived(event.missions ?? []);
	const periods = $derived(periodsOf(missions));
	const tabs = $derived(periods.map((id) => ({ id, label: t(`play.news.period.${id}`), count: readyCount(missions, net.eventProgress, event.id, id), alert: true })));
	let tab = $state('');
	$effect.pre(() => {
		if (periods.length && !periods.includes(tab)) tab = periods.find((p) => readyCount(missions, net.eventProgress, event.id, p)) ?? periods[0]; // the first one with something to claim
	});
	const RANK = { ready: 0, progress: 1, claimed: 2 };
	const rows = $derived(
		missions
			.filter((m) => periods.length < 2 || (m.period ?? 'daily') === tab)
			.map((mission) => {
				const entry = missionEntry(net.eventProgress, event.id, mission.id);
				return { mission, entry, status: missionStatus(mission, entry), progress: Math.min(entry?.progress ?? 0, mission.target) };
			})
			.sort((a, b) => RANK[a.status] - RANK[b.status]) // stable: the settings' order holds within a state
	);
	const ICON = { claimed: { name: 'check_circle', cls: 'text-(color:--kk-accent-strong)', fill: true }, ready: { name: 'redeem', cls: 'text-(--ac,var(--kk-accent-strong))', fill: true }, progress: { name: 'radio_button_unchecked', cls: 'text-(color:--kk-line-strong)', fill: false } };
	let pending = $state('');
	$effect(() => {
		net.eventProgress; // an answer ends the wait
		pending = '';
	});
	function claimAll() {
		pending = '*';
		store.claimEventMissions(event.id);
		setTimeout(() => pending === '*' && (pending = ''), 4000);
	}
	const totalReady = $derived(readyCount(missions, net.eventProgress, event.id));
	const go = (dest) => store.hooks?.goto?.(dest);
	function claim(id) {
		pending = id;
		store.claimEventMission(event.id, id);
		setTimeout(() => pending === id && (pending = ''), 4000);
	}
</script>

<section class="flex flex-col gap-3">
	<h3 class="text-base font-extrabold">{t('play.news.missions')}</h3>
	{#if event.audience?.kind === 'newcomer'}
		<div class="flex items-center gap-3 rounded-2xl bg-(--kk-surface-2) p-3">
			<Mascot {store} who="haksamo" size={64} />
			<p class="min-w-0 flex-1 rounded-2xl bg-(--kk-surface) px-3.5 py-2.5 text-sm font-bold shadow-[0_2px_0_var(--kk-line)]">{t('play.news.audience.newcomer')}</p>
		</div>
	{/if}
	<div class="flex flex-wrap items-center gap-2">
		{#if periods.length > 1}
			<div class="min-w-0 flex-1 overflow-x-auto [&_.kk-tab]:px-4 [&_.kk-tab]:whitespace-nowrap"><Tabs bind:value={tab} {tabs} idPrefix="newsm" label={t('play.news.missions')} /></div>
		{:else}<span class="flex-1"></span>{/if}
		{#if missions.length > 1}
			<button type="button" disabled={!playable || !totalReady || pending === '*'} onclick={claimAll} class="kk-btn kk-btn-primary inline-flex h-9 min-h-0 shrink-0 items-center justify-center gap-1.5 px-4 text-sm"><Icon name="redeem" size={18} />{t('play.news.claimAll')}</button>
		{/if}
	</div>
	<ul role={periods.length > 1 ? 'tabpanel' : undefined} id={periods.length > 1 ? `newsm-panel-${tab}` : undefined} aria-labelledby={periods.length > 1 ? `newsm-tab-${tab}` : undefined} class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,15rem),1fr))] gap-3">
		{#each rows as { mission, status, progress } (mission.id)}
			<li class="flex flex-col gap-2 rounded-2xl border-[1.5px] p-3 {status === 'ready' ? 'border-(--ac,var(--kk-accent)) bg-(--kk-accent-soft)' : 'border-(color:--kk-line) bg-(--kk-surface-2)'} {status === 'claimed' ? 'opacity-70' : ''}">
				<div class="flex items-center justify-between gap-2 text-xs font-extrabold tabular-nums">
					<span class="inline-flex items-center gap-1 rounded-full bg-(--kk-surface) px-2 py-0.5">{progress.toLocaleString()}/{mission.target.toLocaleString()}</span>
					<Icon name={ICON[status].name} size={22} fill={ICON[status].fill} class={ICON[status].cls} aria-hidden="true" />
				</div>
				<div class="flex min-w-0 flex-1 flex-col gap-1">
					<b class="text-sm font-extrabold">{mission.name}<span class="sr-only"> — {t(`play.news.missionStatus.${status}`)}</span></b>
					{#if mission.description}<span class="text-[13px] text-(color:--kk-muted)">{mission.description}</span>{/if}
				</div>
				<span class="h-2 overflow-hidden rounded-full bg-(--kk-line)" role="progressbar" aria-label={mission.name} aria-valuemin="0" aria-valuemax={mission.target} aria-valuenow={progress}><span class="block h-full rounded-full bg-(--ac,var(--kk-accent))" style:width="{ratio(progress, mission.target) * 100}%"></span></span>
				<div class="flex items-center justify-between gap-2">
					{#if mission.rewards?.length}<RewardBundle rewards={mission.rewards} {shop} {details} size={56} />{:else}<span></span>{/if}
					{#if status === 'progress' && mission.goto}
						<button type="button" onclick={() => go(mission.goto)} class="kk-btn inline-flex h-9 min-h-0 shrink-0 items-center justify-center gap-1.5 px-4 text-sm">{t('play.news.goto')}</button>
					{:else if mission.rewards?.length}
						<button type="button" disabled={!playable || status !== 'ready' || pending === mission.id || pending === '*'} onclick={() => claim(mission.id)} class="kk-btn kk-btn-primary inline-flex h-9 min-h-0 shrink-0 items-center justify-center gap-1.5 px-4 text-sm">
							{status === 'claimed' ? t('play.news.claimed') : t('play.news.claim')}
						</button>
					{:else}
						<span class="inline-flex h-9 items-center gap-1 px-2 text-sm font-extrabold {status === 'claimed' ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}">{status === 'claimed' ? t('play.news.done') : ''}</span>
					{/if}
				</div>
			</li>
		{/each}
	</ul>
</section>
