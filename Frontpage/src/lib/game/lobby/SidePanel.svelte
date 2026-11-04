<script>
	import RatingIcon from './RatingIcon.svelte';
	import { fullNick } from './nickTip.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Icon from './Icon.svelte';
	import Select from '../ui/Select.svelte';
	import { prefs, setPref, USER_SORTS } from '../prefs.svelte.js';
	import { t } from '../i18n.svelte.js';
	import EmptyState from './EmptyState.svelte';
	import Moremi from './Moremi.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import GameIcon from './GameIcon.svelte';
	import { prettyTime, OKG_MINUTES, FRIEND_LIMIT } from '../lobbyModel.js';
	import { MAX_LEVEL } from '../level.js';
	import FriendsPanel from './FriendsPanel.svelte';
	import AddFriendDialog from './AddFriendDialog.svelte';
	let { store } = $props();
	let addOpen = $state(false);
	// Until the user picks a tab: friends if they have any, otherwise who is online.
	let picked = $state(null);
	const pending = $derived(store.pendingFriends);
	const waiting = $derived(pending.incoming.length + pending.outgoing.length);
	const tab = $derived(picked ?? (store.friends.length || waiting ? 'friends' : 'users'));
	const me = $derived(store.me);
	const sorts = $derived(USER_SORTS.map((k) => ({ value: k, label: t(`play.side.sort.${k}`) })));
	const parts = $derived(store.net.meta.moremiParts);
	const okgMinutes = $derived(OKG_MINUTES[store.net.membership] ?? OKG_MINUTES[0]);
	const okgPct = $derived(`${((store.net.playTime % (okgMinutes * 60_000)) / (okgMinutes * 60_000)) * 100}%`);
	// Legacy shows progress to 3 decimals, truncated.
	const progressPercent = (m) => Math.floor(((m.score - m.from) / (m.to - m.from)) * 1e5) / 1e3;
	const pct = (m) => (m && isFinite(m.to) ? `${Math.min(100, ((m.score - m.from) / (m.to - m.from)) * 100)}%` : '100%');
	const where = (f) => (!f.online ? t('play.friend.offline') : f.place ? t('play.friend.inGame', { room: f.place }) : f.server === store.server ? t('play.friend.lobby') : t('play.friend.channel', { channel: f.server }));
</script>

<aside aria-label={t('play.side.label')} class="kk-card flex w-full shrink-0 flex-col overflow-hidden">
	<div class="flex min-h-0 flex-1 flex-col">
		<div class="flex border-b border-(color:--kk-line) px-3">
			<!-- The tab is the name; its number is a chip beside it (the open tab's chip is green). -->
			<button type="button" aria-pressed={tab === 'friends'} aria-label={t('play.side.friendsAria', { current: store.friends.length, limit: FRIEND_LIMIT })} onclick={() => (picked = 'friends')} class="inline-flex min-h-11 flex-1 items-center justify-center gap-1.5 kk-tab">
				{t('play.side.friends')}
				<span class="rounded-full px-2 py-0.5 text-xs leading-none font-extrabold tabular-nums {tab === 'friends' ? 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' : 'bg-(--kk-surface-2) text-(color:--kk-muted)'}">{store.friends.length}/{FRIEND_LIMIT}</span>
				{#if pending.incoming.length}<span class="inline-grid h-[18px] min-w-[18px] place-items-center rounded-full bg-(--kk-danger) px-1 text-[11px] leading-none font-extrabold text-white" aria-label={t('play.friend.pendingIn', { n: pending.incoming.length })}>{pending.incoming.length}</span>{/if}
			</button>
			<button type="button" aria-pressed={tab === 'users'} aria-label={t('play.side.usersAria', { count: store.users.length })} onclick={() => (picked = 'users')} class="inline-flex min-h-11 flex-1 items-center justify-center gap-1.5 kk-tab">
				{t('play.side.users')}
				<span class="rounded-full px-2 py-0.5 text-xs leading-none font-extrabold tabular-nums {tab === 'users' ? 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' : 'bg-(--kk-surface-2) text-(color:--kk-muted)'}">{store.users.length}</span>
			</button>
		</div>
		{#if tab === 'users'}
			<div class="border-b border-(color:--kk-line) p-2"><Select label={t('play.side.sort.title')} value={prefs.userSort} options={sorts} onchange={(v) => setPref('userSort', v)} /></div>
		{/if}
		{#if tab === 'friends'}
			<FriendsPanel {store} onadd={() => (addOpen = true)} />
		{:else}
		<ul class="min-h-0 flex-1 overflow-y-auto p-2">
			{#each store.users as u (u.id)}
					<li>
					<button type="button" onclick={(e) => store.hooks.openUserMenu?.(u.id, e, 'userList')} class="flex min-h-10 w-full items-center gap-2.5 rounded-lg px-2 text-left hover:bg-(--kk-surface-2)">
						<LevelIcon level={u.lv} size={22} maskable={u.id !== store.net.id} />
						<Tooltip text={prefs.hideUsers && u.id !== store.net.id ? t('play.anonymous') : fullNick(u)}><span class="truncate">{#if prefs.hideUsers && u.id !== store.net.id}{t('play.anonymous')}{:else}{u.name}{/if}</span></Tooltip>
						{#if u.rating != null && u.rating < 2}<Tooltip text={t(`play.rating.${u.rating}`)}><span class="inline-flex shrink-0"><RatingIcon rating={u.rating} size={15} /></span></Tooltip>{/if}
					</button>
					</li>
				{/each}
		</ul>
		{/if}
	</div>
	<AddFriendDialog {store} bind:open={addOpen} />

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
			<button type="button" onclick={() => store.hooks.openDress?.('profile')} aria-label="{me.name}: {t('play.profile.windowTitle')}" class="flex w-full items-center gap-3 text-left">
				<Moremi equip={me.equip} shop={store.shop} {parts} size={64} label={me.name} frame={false} />
				<div class="min-w-0 flex-1">
					<p class="truncate text-base leading-tight font-extrabold">{me.name}</p>
					<p class="mt-1 flex items-center gap-1.5 text-xs font-bold text-(color:--kk-muted)"><LevelIcon level={me.lv} size={20} />{t('play.level', { level: me.lv })}</p>
				</div>
			</button>

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
