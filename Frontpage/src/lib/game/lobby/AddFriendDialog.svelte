<script>
	import { t } from '../i18n.svelte.js';
	import { fetchRecentOpponents } from '../profile.js';
	import { searchPlayers } from '../friendSearch.js';
	import Dialog from './Dialog.svelte';
	import Moremi from './Moremi.svelte';
	import NickName from './NickName.svelte';
	import Icon from './Icon.svelte';
	import Tabs from '../ui/Tabs.svelte';

	/**
	 * 친구 추가, in two tabs: 추천 친구 (the people you just played against) and 친구 검색 (anyone, by nickname with or without the #tag,
	 * or by 식별번호). A request waits for the other player, online or not, until they answer it.
	 */
	let { store, open = $bindable(false) } = $props();
	const net = $derived(store.net);
	const parts = $derived(net.meta.moremiParts);
	let tab = $state('recommended');
	let recs = $state(null); // null while loading
	let query = $state('');
	let found = $state({ status: 'idle', users: [] }); // idle | loading | done | rate | error
	let seq = 0;
	let timer;

	const tabs = $derived([
		{ id: 'recommended', label: t('play.friends.tab.recommended') },
		{ id: 'search', label: t('play.friends.tab.search') }
	]);

	$effect(() => {
		if (!open) return;
		tab = 'recommended';
		query = '';
		found = { status: 'idle', users: [] };
		recs = null;
		if (net.guest) return void (recs = []);
		const mine = ++seq;
		fetchRecentOpponents(net.id)
			.then((list) => mine === seq && (recs = list))
			.catch(() => mine === seq && (recs = []));
	});

	// Looking up as the typing pauses.
	$effect(() => {
		const q = query.trim();
		clearTimeout(timer);
		const mine = ++seq;
		if (q.length < 2) return void (found = { status: 'idle', users: [] });
		found = { status: 'loading', users: [] };
		timer = setTimeout(async () => {
			const r = await searchPlayers(q);
			if (mine !== seq) return;
			found = r.ok ? { status: 'done', users: r.users } : { status: r.reason === 'rate' ? 'rate' : 'error', users: [] };
		}, 350);
		return () => clearTimeout(timer);
	});

	const standing = (u) => (store.isFriend(u.id) ? 'friend' : net.friendPending.some((p) => p.target === u.id) ? 'pending' : 'ready');
	async function ask(u) {
		if (await store.addFriend(u.id, { confirm: false, name: u.name })) open = false; // done: the request is on its way
	}
	function profile(u) {
		open = false;
		store.hooks.openProfile?.(u.id);
	}
</script>

{#snippet person(u)}
	{@const st = standing(u)}
	<li class="flex items-center gap-3 px-3 py-2">
		<!-- the person (picture and name) opens their profile -->
		<button type="button" onclick={() => profile(u)} aria-label="{t('play.profile.windowTitle')}: {u.name}" class="flex min-w-0 flex-1 items-center gap-3 rounded-lg text-left hover:bg-(--kk-surface-2)">
			<Moremi equip={u.equip} shop={store.shop} {parts} size={40} label={u.name} frame={false} />
			<span class="min-w-0 flex-1 leading-tight"><span class="block truncate font-extrabold"><NickName user={u} /></span>{#if st !== 'ready' || net.users[u.id]}<span class="text-xs text-(color:--kk-muted)">{t(st === 'ready' ? 'play.friend.lobby' : st === 'friend' ? 'play.friend.isFriend' : 'play.friend.pendingTo')}</span>{/if}</span>
		</button>
		<button type="button" class="kk-btn {st === 'ready' ? 'kk-btn-primary' : ''} inline-flex min-h-10 items-center gap-1 px-3 text-sm" disabled={st !== 'ready'} onclick={() => ask(u)}><Icon name={st === 'friend' ? 'check' : st === 'pending' ? 'hourglass_top' : 'person_add'} size={18} />{t(st === 'friend' ? 'play.friend.isFriend' : st === 'pending' ? 'play.friends.requested' : 'play.friend.add')}</button>
	</li>
{/snippet}

<Dialog bind:open title={t('play.friend.add')}>
	{#if net.guest}
		<p class="rounded-xl bg-(--kk-surface-2) px-4 py-6 text-center text-sm text-(color:--kk-muted)">{t('play.friends.guestNo')}</p>
	{:else}
		<div class="flex min-h-72 flex-col gap-4">
			<Tabs bind:value={tab} idPrefix="addfriend" label={t('play.friend.add')} {tabs} />
			{#if tab === 'recommended'}
				<div role="tabpanel" id="addfriend-panel-recommended" aria-labelledby="addfriend-tab-recommended">
					{#if recs === null}
						<div class="flex flex-col gap-2" role="status" aria-label={t('play.profile.loading')}>{#each [0, 1, 2] as i (i)}<div class="h-14 animate-pulse rounded-xl bg-(--kk-surface-2)"></div>{/each}</div>
					{:else if !recs.length}
						<p class="rounded-xl bg-(--kk-surface-2) px-4 py-5 text-center text-sm text-(color:--kk-muted)">{t('play.friends.recommendedNone')}</p>
					{:else}
						<ul class="flex flex-col divide-y divide-(color:--kk-line) rounded-xl border-[1.5px] border-(color:--kk-line)">
							{#each recs as u (u.id)}{@render person(u)}{/each}
						</ul>
					{/if}
				</div>
			{:else}
				<div role="tabpanel" id="addfriend-panel-search" aria-labelledby="addfriend-tab-search" class="flex flex-col gap-3">
					<label class="kk-input flex h-11 items-center gap-2 px-3">
						<Icon name="search" size={20} class="shrink-0 text-(color:--kk-muted)" />
						<!-- svelte-ignore a11y_autofocus -->
						<input bind:value={query} autofocus aria-label={t('play.friends.tab.search')} placeholder={t('play.friends.searchPlaceholder')} autocomplete="off" maxlength="64" class="min-w-0 flex-1 bg-transparent text-sm outline-none" />
					</label>
					{#if found.status === 'loading'}
						<div class="flex flex-col gap-2" role="status" aria-label={t('play.profile.loading')}>{#each [0, 1] as i (i)}<div class="h-14 animate-pulse rounded-xl bg-(--kk-surface-2)"></div>{/each}</div>
					{:else if found.status === 'done' && found.users.length}
						<ul class="flex flex-col divide-y divide-(color:--kk-line) rounded-xl border-[1.5px] border-(color:--kk-line)">
							{#each found.users as u (u.id)}{@render person(u)}{/each}
						</ul>
					{:else if found.status !== 'idle'}
						<p class="rounded-xl bg-(--kk-surface-2) px-4 py-5 text-center text-sm text-(color:--kk-muted)" role="status">{t(found.status === 'rate' ? 'play.friends.searchRate' : found.status === 'error' ? 'play.friends.searchError' : 'play.friends.searchNone')}</p>
					{/if}
				</div>
			{/if}
		</div>
	{/if}
</Dialog>
