<script>
	import { tick } from 'svelte';
	import { t, i18n } from '../i18n.svelte.js';
	import { dmTime } from '../dm.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import { maskBad } from '../badwords.js';
	import { prefs } from '../prefs.svelte.js';
	import Icon from './Icon.svelte';
	import EmptyState from './EmptyState.svelte';
	import DmAvatar from './DmAvatar.svelte';
	import DmMembers from './DmMembers.svelte';
	import NickName from './NickName.svelte';
	import Tooltip from '../ui/Tooltip.svelte';

	/**
	 * The 귓속말 tab of the chat: the list of conversations (newest first, with the last line and its time), a conversation with its own
	 * message box, and the picker for a new one. Threads live in `store.dm` (see dm.js).
	 * @type {{store:any, uid:string}}
	 */
	let { store, uid } = $props();
	const peer = $derived(store.dm.active);
	const th = $derived(peer ? store.dm.threads[peer] : null);
	const user = $derived(peer ? (store.net.users[peer] ?? null) : null);
	const list = $derived(store.dmList);
	const words = (m) => (prefs.filter === 'off' ? m.msg : maskBad(m.shown ?? m.msg));
	const clock = (at) => new Date(at).toLocaleTimeString(i18n.language, { hour: 'numeric', minute: '2-digit' });
	let picking = $state(false);
	let query = $state('');
	let pick = $state(0);
	let text = $state('');
	let log = $state();
	let field = $state();
	let search = $state();
	const rows = $derived(picking ? store.dmMembers(query, 60) : []);

	// What is on screen is read. (Cleared on the way out, in a microtask: state may not change while the panel is being torn down.)
	$effect(() => {
		store.dmView(peer && !picking ? peer : null);
		return () => queueMicrotask(() => store.dmView(null));
	});
	// New lines follow the reader to the bottom of a conversation.
	$effect(() => {
		th?.messages.length;
		peer;
		tick().then(() => log?.scrollTo({ top: log.scrollHeight }));
	});
	$effect(() => {
		if (peer) tick().then(() => field?.focus());
	});
	$effect(() => {
		if (picking) tick().then(() => search?.focus());
	});
	$effect(() => {
		query;
		pick = 0;
	});

	function startPick(row) {
		picking = false;
		query = '';
		store.dm.active = row.id;
	}
	function searchKey(e) {
		if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
			e.preventDefault();
			if (rows.length) pick = (pick + (e.key === 'ArrowDown' ? 1 : -1) + rows.length) % rows.length;
		} else if (e.key === 'Enter') {
			e.preventDefault();
			if (rows[pick]) startPick(rows[pick]);
		} else if (e.key === 'Escape') {
			e.stopPropagation();
			picking = false;
		}
	}
	function send(e) {
		e.preventDefault();
		if (store.sendDm(peer, text) || text.trim()) text = '';
	}
	async function clear() {
		if (!peer || !(await confirm({ message: t('play.dm.clearConfirm') }))) return;
		store.dmClose(peer);
	}
	const presence = $derived(!user ? '' : (store.net.friendsStatus[peer] ?? 'online'));
</script>

<div class="relative flex min-h-0 flex-1 flex-col">
	{#if picking}
		<!-- 새 대화: pick who to talk to (the same list as the autocomplete after /e) -->
		<div class="flex items-center gap-1.5 border-b border-(color:--kk-line) p-2">
			<button type="button" aria-label={t('play.dm.back')} onclick={() => (picking = false)} class="grid size-9 shrink-0 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)"><Icon name="arrow_back" size={20} /></button>
			<label class="kk-input flex h-9 min-w-0 flex-1 items-center gap-1.5 px-2.5"><Icon name="search" size={17} /><input bind:this={search} bind:value={query} onkeydown={searchKey} role="combobox" aria-expanded="true" aria-controls="{uid}-dm-pick" aria-activedescendant={rows.length ? `${uid}-dm-pick-${pick}` : undefined} placeholder={t('play.dm.search')} aria-label={t('play.dm.search')} autocomplete="off" class="min-w-0 flex-1 bg-transparent text-sm outline-none" /></label>
		</div>
		<div class="min-h-0 flex-1 overflow-y-auto p-2">
			<p class="px-2.5 pb-1 text-xs font-extrabold text-(color:--kk-muted)">{t('play.dm.members')}</p>
			{#if rows.length}<DmMembers {store} {rows} active={pick} onpick={startPick} id="{uid}-dm-pick" />{:else}<EmptyState compact size={96} title={t('play.dm.noMembers')} />{/if}
		</div>
	{:else if peer}
		<!-- a conversation -->
		<div class="flex items-center gap-2 border-b border-(color:--kk-line) p-2">
			<button type="button" aria-label={t('play.dm.back')} onclick={() => (store.dm.active = null)} class="grid size-9 shrink-0 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)"><Icon name="arrow_back" size={20} /></button>
			<button type="button" onclick={() => user && store.hooks.openProfile?.(peer)} disabled={!user} class="flex min-w-0 flex-1 items-center gap-2.5 text-left">
				<DmAvatar {store} {user} size={36} status={presence} />
				<span class="min-w-0 flex-1">
					<b class="block truncate text-sm leading-tight">{#if user}<NickName {user} />{:else}{th?.name}{/if}</b>
					{#if !user}<span class="block truncate text-xs text-(color:--kk-muted)">{t('play.dm.offline')}</span>{/if}
				</span>
			</button>
			<Tooltip text={t('play.dm.clear')}><button type="button" aria-label={t('play.dm.clear')} onclick={clear} class="grid size-9 shrink-0 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-danger)"><Icon name="delete" size={19} /></button></Tooltip>
		</div>
		<div bind:this={log} role="log" aria-live="polite" class="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto p-3 text-sm">
			{#each th?.messages ?? [] as m, i (i)}
				{#if m.system}
					<p class="flex items-center gap-3 text-xs text-(color:--kk-muted)"><span class="h-px flex-1 bg-(--kk-line)"></span><span>{m.msg}</span><span class="h-px flex-1 bg-(--kk-line)"></span></p>
				{:else if m.mine}
					<div class="flex items-end justify-end gap-1.5">
						<span class="pb-0.5 text-[11px] text-(color:--kk-muted)">{clock(m.at)}</span>
						<p class="max-w-[75%] rounded-xl rounded-br-sm bg-(--kk-accent-soft) px-3 py-2 break-words whitespace-pre-wrap">{m.msg}</p>
					</div>
				{:else}
					<div class="flex items-end gap-1.5">
						<p class="max-w-[75%] rounded-xl rounded-bl-sm bg-(--kk-surface-2) px-3 py-2 break-words whitespace-pre-wrap">{words(m)}</p>
						<span class="pb-0.5 text-[11px] text-(color:--kk-muted)">{clock(m.at)}</span>
					</div>
				{/if}
			{/each}
		</div>
		<form onsubmit={send} class="flex items-center gap-2 border-t border-(color:--kk-line) bg-(--kk-panel) p-2.5">
			<input bind:this={field} bind:value={text} disabled={!user} aria-label={t('play.dm.input')} placeholder={user ? t('play.dm.placeholder', { name: user.name }) : t('play.dm.offline')} maxlength="200" autocomplete="off" class="h-11 min-w-0 flex-1 kk-input px-3" />
			<button class="kk-btn kk-btn-primary grid size-11 shrink-0 place-items-center p-0" aria-label={t('play.chat.send')} disabled={!user || !text.trim()}><Icon name="send" size={20} fill /></button>
		</form>
	{:else}
		<!-- the conversations -->
		<div class="flex items-center gap-2 border-b border-(color:--kk-line) p-2">
			<button type="button" onclick={() => (picking = true)} class="kk-btn inline-flex h-9 min-h-0 flex-1 items-center justify-center gap-1.5 text-sm"><Icon name="edit_square" size={18} />{t('play.dm.new')}</button>
		</div>
		{#if list.length}
			<ul class="min-h-0 flex-1 overflow-y-auto p-1.5">
				{#each list as c (c.id)}
					{@const u = store.net.users[c.id]}
					<li>
						<button type="button" onclick={() => (store.dm.active = c.id)} class="flex w-full items-center gap-3 rounded-xl px-2.5 py-2 text-left hover:bg-(--kk-surface-2) focus-visible:bg-(--kk-surface-2) focus-visible:outline-none">
							<DmAvatar {store} user={u} size={46} status={u ? (store.net.friendsStatus[c.id] ?? 'online') : ''} />
							<span class="min-w-0 flex-1">
								<b class="block truncate text-sm leading-tight">{#if u}<NickName user={u} hideTag />{:else}{c.name}{/if}</b>
								<span class="line-clamp-2 text-xs leading-snug break-all text-(color:--kk-muted)">{c.mine ? '→ ' : ''}{c.preview}</span>
							</span>
							<span class="flex shrink-0 flex-col items-end gap-1 self-start pt-0.5">
								<span class="text-xs text-(color:--kk-muted) tabular-nums">{dmTime(c.lastAt, Date.now(), i18n.language)}</span>
								{#if c.unread && prefs.dmAlerts}<span aria-label={t('play.chat.unread', { count: c.unread })} class="grid h-5 min-w-5 place-items-center rounded-full bg-(--kk-danger) px-1.5 text-[11px] leading-none font-extrabold text-white">{c.unread > 99 ? '99+' : c.unread}</span>{/if}
							</span>
						</button>
					</li>
				{/each}
			</ul>
		{:else}
			<div class="grid min-h-0 flex-1 place-items-center overflow-y-auto"><EmptyState compact size={110} title={t('play.dm.empty')} hint={t('play.dm.emptyHint')} /></div>
		{/if}
	{/if}
</div>
