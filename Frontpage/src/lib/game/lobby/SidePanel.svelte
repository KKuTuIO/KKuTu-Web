<script>
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import EmptyState from './EmptyState.svelte';
	import Moremi from './Moremi.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import GameIcon from './GameIcon.svelte';
	import { prettyTime, OKG_MINUTES } from '../lobbyModel.js';
	import { MAX_LEVEL } from '../level.js';
	let { store } = $props();
	// Until the user picks a tab: friends if they have any, otherwise who is online.
	let picked = $state(null);
	const tab = $derived(picked ?? (store.friends.length ? 'friends' : 'users'));
	const me = $derived(store.me);
	const parts = $derived(store.net.meta.moremiParts);
	const onlineCount = $derived(store.friends.filter((f) => f.online).length);
	const okgMinutes = $derived(OKG_MINUTES[store.net.membership] ?? OKG_MINUTES[0]);
	const okgPct = $derived(`${((store.net.playTime % (okgMinutes * 60_000)) / (okgMinutes * 60_000)) * 100}%`);
	// Legacy shows progress to 3 decimals, truncated.
	const progressPercent = (m) => Math.floor(((m.score - m.from) / (m.to - m.from)) * 1e5) / 1e3;
	const pct = (m) => (m && isFinite(m.to) ? `${Math.min(100, ((m.score - m.from) / (m.to - m.from)) * 100)}%` : '100%');
	const where = (f) => (!f.online ? t('play.friend.offline') : f.place ? t('play.friend.inGame', { room: f.place }) : f.server === store.server ? t('play.friend.lobby') : t('play.friend.channel', { channel: f.server }));
</script>

<aside aria-label={t('play.side.label')} class="kk-card flex w-full shrink-0 flex-col overflow-hidden lg:w-72">
	<div class="flex min-h-0 flex-1 flex-col">
		<div class="flex border-b border-(color:--kk-line) px-3">
			<button type="button" aria-pressed={tab === 'friends'} onclick={() => (picked = 'friends')} class="min-h-11 flex-1 kk-tab">{t('play.side.friends', { online: onlineCount, total: store.friends.length })}</button>
			<button type="button" aria-pressed={tab === 'users'} onclick={() => (picked = 'users')} class="min-h-11 flex-1 kk-tab">{t('play.side.users', { count: store.users.length })}</button>
		</div>
		<ul class="min-h-0 flex-1 overflow-y-auto p-2">
			{#if tab === 'friends'}
				{#each store.friends as f (f.id)}
					<li class="flex min-h-12 items-center gap-2.5 rounded-lg px-2 {f.online ? '' : 'opacity-50'}">
						<Moremi equip={f.user?.equip} shop={store.shop} {parts} size={34} label={f.name} />
						<span class="min-w-0 leading-tight"><span class="block truncate font-bold">{f.name}</span><span class="text-xs text-(color:--kk-muted)">{where(f)}</span></span>
					</li>
				{:else}
					<li><EmptyState compact size={120} title={t('play.side.noFriends')} /></li>
				{/each}
			{:else}
				{#each store.users as u (u.id)}
					<li class="flex min-h-10 items-center gap-2.5 px-2">
						<LevelIcon level={u.lv} size={22} />
						<span class="truncate">{u.name}</span>
					</li>
				{/each}
			{/if}
		</ul>
	</div>

	{#if me}
		<!-- Same hover info as the legacy client: level / XP left / progress, and the 오끄감 thank-you. -->
		{#snippet xpTip()}
			<span class="block">{t('play.xp.level', { level: me.lv, max: MAX_LEVEL })}</span>
			{#if isFinite(me.to)}
				<span class="block">{t('play.xp.remain', { n: (me.to - me.score).toLocaleString() })}</span>
				<span class="block">{t('play.xp.progress', { percent: progressPercent(me) })}</span>
			{:else}
				<span class="block">{t('play.xp.max')}</span>
			{/if}
		{/snippet}
		{#snippet okgTip()}
			<span class="block text-sm">{t('play.okg.title')}</span>
			<span class="mt-1 block font-medium">{t('play.okg.body', { minutes: okgMinutes })}</span>
			<span class="mt-1 block font-medium">{t('play.okg.membership', { minutes: Math.min(...OKG_MINUTES) })}</span>
			<span class="mt-1 block">{t('play.okg.stack', { count: store.net.okgCount })}</span>
		{/snippet}

		<div class="hidden border-t border-(color:--kk-line) bg-(--kk-panel) p-4 lg:block">
			<div class="flex items-center gap-3">
				<Moremi equip={me.equip} shop={store.shop} {parts} size={64} label={me.name} frame={false} />
				<div class="min-w-0 flex-1">
					<p class="truncate text-base leading-tight font-extrabold">{me.name}</p>
					<p class="mt-1 flex items-center gap-1.5 text-xs font-bold text-(color:--kk-muted)"><LevelIcon level={me.lv} size={20} />{t('play.level', { level: me.lv })}</p>
				</div>
			</div>

			<!-- Two equal halves with identical gauges, so the bars line up. -->
			<div class="mt-4 grid grid-cols-2 items-stretch gap-3">
				<Tooltip content={xpTip}>
					<div class="flex min-w-0 flex-col">
						<span class="inline-flex items-center gap-1.5 text-xs font-bold"><GameIcon name="xp" size={16} />{t('play.me.xp')}</span>
						<span class="mt-0.5 mb-1.5 text-[11px] leading-tight font-bold tabular-nums text-(color:--kk-muted)">{me.score.toLocaleString()}{isFinite(me.to) ? ` / ${me.to.toLocaleString()}` : ''}</span>
						<div class="kk-bar mt-auto h-2"><span style:width={pct(me)}></span></div>
					</div>
				</Tooltip>
				<Tooltip content={okgTip}>
					<div class="flex min-w-0 flex-col">
						<span class="inline-flex items-center gap-1.5 text-xs font-bold"><GameIcon name="playtime" size={16} />{t('play.me.playtime')}</span>
						<span class="mt-0.5 mb-1.5 text-[11px] leading-tight font-bold tabular-nums text-(color:--kk-muted)">{prettyTime(store.net.playTime)}</span>
						<div class="kk-bar mt-auto h-2"><span style:width={okgPct}></span></div>
					</div>
				</Tooltip>
			</div>
		</div>
	{/if}
</aside>
