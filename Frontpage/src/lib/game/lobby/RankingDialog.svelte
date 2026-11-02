<script>
	import { t } from '../i18n.svelte.js';
	import { loadUser } from '$lib/session.js';
	import { PAGE_SIZE, fetchRanking, displayDelta, equipOf, splitName } from '../ranking.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import EmptyState from './EmptyState.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import Select from '../ui/Select.svelte';
	import { toast } from '../ui/toast.svelte.js';

	let { store, open = $bindable(false) } = $props();
	const parts = $derived(store.net.meta.moremiParts);
	const canViewMine = $derived(!store.net.guest);

	// Ranking kinds. Only the score ranking exists today; the trophy league is announced but not built yet.
	let kind = $state('score');
	const kinds = $derived([
		{ value: 'score', label: t('play.rank.type.score') },
		{ value: 'trophy', label: t('play.rank.type.trophy'), disabled: true, badge: t('play.options.tbd') }
	]);

	let page = $state(0);
	let rows = $state([]);
	let top = $state([]); // first four of page 0, shown as cards
	let label = $state('');
	let query = $state('');
	let loading = $state(false);
	let failed = $state(false);

	async function run(opts, { resultLabel = '', keepTop = true } = {}) {
		loading = true;
		failed = false;
		try {
			const res = await fetchRanking(opts);
			rows = res.rows;
			page = res.page;
			label = resultLabel;
			if (!keepTop || opts.page === 0) top = (await topRows(res, opts)) ?? top;
		} catch {
			failed = true;
			toast(t('play.rank.error'), { kind: 'error' });
		} finally {
			loading = false;
		}
	}
	// The cards always show the real top four, whatever page or search is on screen.
	async function topRows(res, opts) {
		if (opts.page === 0 && !opts.me && !opts.query) return res.rows.slice(0, 4);
		if (top.length) return top;
		try {
			return (await fetchRanking({ page: 0 })).rows.slice(0, 4);
		} catch {
			return [];
		}
	}

	const refresh = () => run({ page }, { keepTop: false });
	const goto = (p) => run({ page: Math.max(0, p) });
	const showMine = () => run({ me: true }, { resultLabel: t('play.rank.myResult') });
	function search(e) {
		e.preventDefault();
		const q = query.trim();
		if (q) run({ query: q }, { resultLabel: t('play.rank.result', { query: q }) });
	}

	// Load when the dialog opens; start again from the top each time.
	$effect(() => {
		if (open) {
			query = '';
			kind = 'score';
			run({ page: 0 }, { keepTop: false });
		}
	});

	const openRecords = (id) => window.open(`/records?type=id&q=${encodeURIComponent(id)}`, '_blank', 'noopener');
	async function copyId(id) {
		try {
			await navigator.clipboard.writeText(id);
			toast(t('play.rank.copied'), { kind: 'success' });
		} catch {
			toast(t('play.rank.error'), { kind: 'error' });
		}
	}
	const medal = ['#f59e0b', '#94a3b8', '#c2813f', 'var(--kk-line-strong)'];
	const deltaClass = { up: 'text-(color:--kk-danger)', down: 'text-(color:--kk-blue)', none: 'text-(color:--kk-muted)' };
</script>

{#snippet who(r)}
	{@const n = splitName(r.name)}
	{n.name || t('play.rank.unknown')}{#if n.tag}<small class="font-normal text-(color:--kk-muted)">{n.tag}</small>{/if}
{/snippet}

<Dialog bind:open title={t('play.rank.title')} size="lg">
	<div class="flex flex-col gap-4" aria-busy={loading}>
		<!-- Top four, like the homepage -->
		{#if top.length && page === 0 && !label}
			<ul class="grid grid-cols-2 gap-3 lg:grid-cols-4">
				{#each top as r, i (r.id)}
					{@const d = displayDelta(r.delta)}
					<li class="kk-card relative flex flex-col gap-2 overflow-hidden p-3 pt-4">
						<span class="absolute inset-x-0 top-0 h-1" style:background={medal[i] ?? medal[3]}></span>
						<div class="flex items-start justify-between gap-2">
							<span class="rounded-lg bg-(--kk-text) px-2 py-0.5 text-xs font-black text-(--kk-surface)">{t('play.rank.rankValue', { n: r.rank + 1 })}</span>
							{#await loadUser(r.id) then u}
								<Moremi equip={equipOf(u)} shop={store.shop} {parts} size={56} label={r.name ?? ''} frame={false} />
							{:catch}
								<span class="size-14"></span>
							{/await}
						</div>
						<p class="flex min-w-0 items-center gap-1.5 text-sm font-extrabold"><LevelIcon score={Number(r.score)} size={20} /><span class="truncate">{@render who(r)}</span></p>
						<div class="mt-auto flex items-end justify-between gap-2 border-t border-(color:--kk-line) pt-2">
							<span class="text-sm font-black tabular-nums">{t('play.rank.scoreValue', { n: Number(r.score).toLocaleString() })}</span>
							<span class="text-xs font-black {deltaClass[d.dir]}" aria-label={t('play.rank.delta', { delta: d.text })}>{d.text}</span>
						</div>
					</li>
				{/each}
			</ul>
		{/if}

		<!-- Toolbar -->
		<div class="flex flex-wrap items-center gap-2">
			<div class="mr-auto w-56"><Select label={t('play.rank.type.label')} bind:value={kind} options={kinds} /></div>
			<Tooltip text={canViewMine ? '' : t('play.rank.meDisabled')}>
				<button type="button" class="kk-btn inline-flex items-center gap-1.5 text-sm" disabled={loading || !canViewMine} onclick={showMine}><Icon name="person_pin_circle" size={20} />{t('play.rank.me')}</button>
			</Tooltip>
			<form onsubmit={search} class="kk-input flex h-11 items-center gap-1 pr-1 pl-3">
				<input bind:value={query} aria-label={t('play.rank.searchLabel')} placeholder={t('play.rank.searchPlaceholder')} class="w-44 min-w-0 bg-transparent text-sm outline-none sm:w-56" />
				<button aria-label={t('play.rank.searchLabel')} disabled={loading || !query.trim()} class="grid size-9 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-accent-soft) disabled:opacity-40"><Icon name="search" size={20} /></button>
			</form>
			<Tooltip text={t('play.rank.refresh')}>
				<button type="button" aria-label={t('play.rank.refresh')} class="kk-btn grid size-11 place-items-center p-0" disabled={loading} onclick={refresh}><Icon name="refresh" size={22} class={loading ? 'animate-spin' : ''} /></button>
			</Tooltip>
		</div>

		{#if label}<p class="-mt-2 text-xs font-bold text-(color:--kk-muted)">{label}</p>{/if}

		<!-- Table -->
		<div class="overflow-x-auto rounded-xl border-[1.5px] border-(color:--kk-line)">
			<table class="w-full min-w-[34rem] text-left text-sm">
				<thead class="bg-(--kk-panel) text-xs text-(color:--kk-muted)">
					<tr>
						<th class="w-20 px-3 py-2.5 text-center font-bold">{t('play.rank.col.rank')}</th>
						<th class="px-3 py-2.5 font-bold">{t('play.rank.col.name')}</th>
						<th class="w-20 px-3 py-2.5 text-center font-bold">{t('play.rank.col.level')}</th>
						<th class="w-32 px-3 py-2.5 text-right font-bold">{t('play.rank.col.score')}</th>
						<th class="w-16 px-3 py-2.5 text-center font-bold">{t('play.rank.col.delta')}</th>
						<th class="w-24 px-3 py-2.5"><span class="sr-only">{t('play.rank.col.actions')}</span></th>
					</tr>
				</thead>
				<tbody class={loading ? 'opacity-60' : ''}>
					{#each rows as r (r.id)}
						{@const d = displayDelta(r.delta)}
						<tr class="group border-t border-(color:--kk-line) hover:bg-(--kk-accent-soft)">
							<td class="px-3 py-2 text-center font-black tabular-nums">{t('play.rank.rankValue', { n: r.rank + 1 })}</td>
							<td class="max-w-0 px-3 py-2 font-bold"><span class="block truncate">{@render who(r)}</span></td>
							<td class="px-3 py-2"><div class="flex justify-center"><LevelIcon score={Number(r.score)} size={22} /></div></td>
							<td class="px-3 py-2 text-right font-semibold tabular-nums">{t('play.rank.scoreValue', { n: Number(r.score).toLocaleString() })}</td>
							<td class="px-3 py-2 text-center font-black {deltaClass[d.dir]}" aria-label={t('play.rank.delta', { delta: d.text })}>{d.text}</td>
							<td class="px-2 py-1">
								<div class="flex justify-end gap-0.5 opacity-60 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100">
									<Tooltip text={t('play.rank.records')} placement="bottom"><button type="button" aria-label="{t('play.rank.records')}: {r.name ?? ''}" onclick={() => openRecords(r.id)} class="grid size-9 place-items-center rounded-lg hover:bg-(--kk-surface)"><Icon name="id_card" size={20} /></button></Tooltip>
									<Tooltip text={t('play.rank.copyId')} placement="bottom"><button type="button" aria-label="{t('play.rank.copyId')}: {r.name ?? ''}" onclick={() => copyId(r.id)} class="grid size-9 place-items-center rounded-lg hover:bg-(--kk-surface)"><Icon name="content_copy" size={20} /></button></Tooltip>
								</div>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
			{#if !rows.length && !loading}
				<EmptyState title={t(failed ? 'play.rank.error' : 'play.rank.empty')} />
			{/if}
		</div>

		<!-- Paging -->
		<div class="flex items-center justify-center gap-3">
			<button type="button" aria-label={t('play.rank.prev')} class="kk-btn grid size-11 place-items-center p-0" disabled={loading || page === 0} onclick={() => goto(page - 1)}><Icon name="chevron_left" size={24} /></button>
			<span class="min-w-24 text-center text-sm font-bold">{t('play.rank.page', { n: page + 1 })}</span>
			<button type="button" aria-label={t('play.rank.next')} class="kk-btn grid size-11 place-items-center p-0" disabled={loading || rows.length < PAGE_SIZE} onclick={() => goto(page + 1)}><Icon name="chevron_right" size={24} /></button>
		</div>
	</div>
</Dialog>
