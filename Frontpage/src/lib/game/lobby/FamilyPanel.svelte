<script>
	import { t } from '../i18n.svelte.js';
	import { family } from '../familyStore.svelte.js';
	import { sortMembers, whereOf } from '../familyModel.js';
	import { channelName } from '../lobbyModel.js';
	import { toast } from '../ui/toast.svelte.js';
	import Moremi from './Moremi.svelte';
	import StatusDot from './StatusDot.svelte';
	import Icon from './Icon.svelte';
	import EmptyState from './EmptyState.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import CreateFamilyDialog from './CreateFamilyDialog.svelte';
	import FamilyDialog from './FamilyDialog.svelte';

	/**
	 * The 패밀리 tab. Not in a family: the ones that are recruiting (with a search box and my applications) and a button to found one.
	 * In one: the family's card, who is online, and the way to its window. Members are accounts; the row shows their 대표 프로필.
	 */
	let { store } = $props();
	const net = $derived(store.net);
	const parts = $derived(net.meta.moremiParts);
	const fam = $derived(family.mine);

	let filter = $state('recruit'); // not joined: recruit | applied
	let scope = $state('online'); // joined: online | all
	let query = $state('');
	let createOpen = $state(false);
	let infoOpen = $state(false);

	const q = $derived(query.trim().toLowerCase());
	// The server answers the search; "내 신청" are the families I asked, whether or not they still recruit.
	const applied = $derived(family.appliedFamilies.filter((f) => !q || f.name.toLowerCase().includes(q)));
	const rows = $derived(filter === 'applied' ? applied : family.directory);
	$effect(() => {
		if (!fam && filter === 'recruit') family.fetchList(query);
	});
	const filters = $derived([
		{ id: 'recruit', label: t('play.family.filter.recruit') },
		{ id: 'applied', label: t('play.family.filter.applied'), badge: family.applied.length }
	]);
	const members = $derived(fam ? sortMembers(fam.members).filter((m) => scope === 'all' || m.online) : []);
	const online = $derived(fam ? fam.members.filter((m) => m.online).length : 0);
	const pct = $derived(fam ? `${Math.min(100, (fam.into / fam.need) * 100)}%` : '0%');
	const me = $derived({ lv: store.me?.lv ?? 0, money: store.me?.money ?? net.money ?? 0 });
	const blocker = $derived(family.createBlocker(me));
	const whisper = (m) => store.fillChat(`/e ${m.name.replace(/\s/g, '')} `);

	async function toggleApply(f) {
		if (family.applied.includes(f.id)) return void (await family.cancelApply(f.id));
		if ((await family.apply(f.id)).ok) toast(t('play.family.applied', { name: f.name }));
	}
</script>

<div class="flex min-h-0 flex-1 flex-col">
	{#if !fam}
		<div class="flex items-center gap-1 border-b border-(color:--kk-line) px-2 py-2" role="group" aria-label={t('play.family.filters')}>
			{#each filters as f (f.id)}
				<button type="button" aria-pressed={filter === f.id} onclick={() => (filter = f.id)} class="inline-flex min-h-9 items-center gap-1 rounded-lg px-2 text-sm font-bold whitespace-nowrap transition-colors {filter === f.id ? 'bg-(--kk-surface-2) text-(color:--kk-text)' : 'text-(color:--kk-muted) hover:bg-(--kk-surface-2)'}">
					{f.label}{#if f.badge}<span class="inline-grid h-[18px] min-w-[18px] place-items-center rounded-full bg-(--kk-surface-2) px-1 text-[11px] leading-none font-extrabold text-(color:--kk-muted)">{f.badge}</span>{/if}
				</button>
			{/each}
		</div>
		<div class="min-h-0 flex-1 overflow-y-auto p-2">
			<label class="kk-input mb-2 flex h-10 items-center gap-2 px-2.5">
				<Icon name="search" size={20} class="shrink-0 text-(color:--kk-muted)" />
				<input bind:value={query} aria-label={t('play.family.search')} placeholder={t('play.family.search')} autocomplete="off" class="min-w-0 flex-1 bg-transparent text-sm outline-none" />
			</label>
			<ul class="flex flex-col">
				{#each rows as f (f.id)}
					{@const mine = family.applied.includes(f.id)}
					{@const full = f.members >= f.capacity}
					<li class="flex items-start gap-2.5 border-t border-(color:--kk-line) px-1 py-2.5">
						<span class="grid size-10 shrink-0 place-items-center rounded-xl bg-(--kk-accent-soft) text-(color:--kk-accent-strong)" aria-hidden="true"><Icon name="groups" size={24} fill /></span>
						<span class="min-w-0 flex-1 leading-tight">
							<span class="flex items-center gap-1.5 font-extrabold"><span class="truncate">{f.name}</span><span class="kk-chip shrink-0">{t('play.family.lv', { level: f.level })}</span></span>
							<span class="mt-0.5 block text-xs text-(color:--kk-muted)">{t('play.family.membersCount', { n: f.members, max: f.capacity })} · {t('play.family.masterOf', { name: f.master })}</span>
							{#if f.intro}<span class="mt-1 line-clamp-2 text-xs break-words">{f.intro}</span>{/if}
						</span>
						<button type="button" onclick={() => toggleApply(f)} disabled={!mine && (full || !f.recruiting)} class="kk-btn {mine ? '' : 'kk-btn-primary'} inline-flex min-h-9 shrink-0 items-center px-3 text-xs">
							{mine ? t('play.family.cancelApply') : full ? t('play.family.full') : t('play.family.apply')}
						</button>
					</li>
				{/each}
			</ul>
			{#if !rows.length}
				<EmptyState compact size={110} title={q ? t('play.family.empty.search') : t(`play.family.empty.${filter}`)} />
			{/if}
		</div>
		<div class="border-t border-(color:--kk-line) p-2">
			<button type="button" onclick={() => (createOpen = true)} class="kk-btn kk-btn-primary inline-flex min-h-11 w-full items-center justify-center gap-1.5 text-sm"><Icon name="add_home" size={20} fill />{t('play.family.create')}</button>
			{#if blocker === 'level' || blocker === 'money'}
				<p class="mt-1.5 px-1 text-center text-xs text-(color:--kk-muted)">{t(`play.family.createNeed.${blocker}`)}</p>
			{/if}
		</div>
		<CreateFamilyDialog {store} bind:open={createOpen} />
	{:else}
		<div class="border-b border-(color:--kk-line) p-3">
			<div class="flex items-center gap-2.5">
				<span class="grid size-11 shrink-0 place-items-center rounded-xl bg-(--kk-accent-soft) text-(color:--kk-accent-strong)" aria-hidden="true"><Icon name="groups" size={26} fill /></span>
				<span class="min-w-0 flex-1 leading-tight">
					<span class="block truncate text-base font-extrabold">{fam.name}</span>
					<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.family.lv', { level: fam.level })} · {t('play.family.membersCount', { n: fam.members.length, max: fam.capacity })}</span>
				</span>
			</div>
			<div class="kk-bar mt-2.5 h-2" role="img" aria-label={t('play.family.fame', { into: fam.into.toLocaleString(), need: fam.need.toLocaleString() })}><span style:width={pct}></span></div>
			{#if fam.intro}<p class="mt-2 line-clamp-2 text-xs break-words text-(color:--kk-muted)">{fam.intro}</p>{/if}
		</div>
		<div class="flex items-center gap-1 border-b border-(color:--kk-line) px-2 py-2" role="group" aria-label={t('play.family.scopes')}>
			<button type="button" aria-pressed={scope === 'online'} onclick={() => (scope = 'online')} class="inline-flex min-h-9 items-center rounded-lg px-2 text-sm font-bold {scope === 'online' ? 'bg-(--kk-surface-2)' : 'text-(color:--kk-muted) hover:bg-(--kk-surface-2)'}">{t('play.family.scope.online', { n: online })}</button>
			<button type="button" aria-pressed={scope === 'all'} onclick={() => (scope = 'all')} class="inline-flex min-h-9 items-center rounded-lg px-2 text-sm font-bold {scope === 'all' ? 'bg-(--kk-surface-2)' : 'text-(color:--kk-muted) hover:bg-(--kk-surface-2)'}">{t('play.family.scope.all', { n: fam.members.length })}</button>
		</div>
		<ul class="min-h-0 flex-1 overflow-y-auto p-2">
			{#each members as m (m.accountId)}
				{@const w = whereOf(m, store.server, (id) => channelName(net.meta.labels, id))}
				<li class="group flex min-h-14 items-center gap-1 border-t border-(color:--kk-line) hover:bg-(--kk-surface-2)">
					<span class="flex min-w-0 flex-1 items-center gap-2.5 px-1 py-1.5 {m.online ? '' : 'opacity-55'}">
						<span class="relative shrink-0">
							<Moremi equip={m.equip} shop={store.shop} {parts} size={38} label={m.name} frame={false} />
							<span class="absolute -right-1 -bottom-1 grid size-[18px] place-items-center rounded-full bg-(--kk-surface) group-hover:bg-(--kk-surface-2)" aria-hidden="true"><StatusDot status={m.online ? m.status : 'offline'} size={12} /></span>
						</span>
						<span class="min-w-0 leading-tight">
							<span class="flex items-center gap-1 font-extrabold">
								<span class="truncate">{m.name}</span>
								{#if m.role !== 'member'}<Tooltip text={t(`play.family.role.${m.role}`)}><Icon name={m.role === 'master' ? 'workspace_premium' : 'shield_person'} size={15} fill class="shrink-0 text-(color:--kk-accent-strong)" /></Tooltip>{/if}
							</span>
							<span class="block truncate text-xs text-(color:--kk-muted)">{t(w.key, w.vars)}{#if m.online && m.activeName} · {t('play.family.playingAs', { name: m.activeName })}{/if}</span>
						</span>
					</span>
					{#if m.online && m.accountId !== family.me}
						<Tooltip text={t('play.profile.whisper')}><button type="button" aria-label="{t('play.profile.whisper')}: {m.name}" onclick={() => whisper(m)} class="mr-1 grid size-9 shrink-0 place-items-center rounded-full bg-(--kk-surface-2) text-(color:--kk-muted) hover:text-(color:--kk-text) group-hover:bg-(--kk-surface)"><Icon name="chat_bubble" size={18} fill /></button></Tooltip>
					{/if}
				</li>
			{/each}
			{#if !members.length}<EmptyState compact size={110} title={t('play.family.empty.members')} />{/if}
		</ul>
		<div class="border-t border-(color:--kk-line) p-2">
			<button type="button" onclick={() => (infoOpen = true)} class="kk-btn kk-btn-primary inline-flex min-h-11 w-full items-center justify-center gap-1.5 text-sm">
				<Icon name="info" size={20} fill />{t('play.family.info')}
				{#if family.applicantCount}<span class="inline-grid h-[18px] min-w-[18px] place-items-center rounded-full bg-(--kk-danger) px-1 text-[11px] leading-none font-extrabold text-white">{family.applicantCount}</span>{/if}
			</button>
		</div>
		<FamilyDialog {store} bind:open={infoOpen} />
	{/if}
</div>
