<script>
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import EmptyState from './EmptyState.svelte';
	import Icon from './Icon.svelte';
	import Checkbox from '../ui/Checkbox.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import { exactRoomNo } from '../lobbyModel.js';
	import RoomRow from './RoomRow.svelte';
	let { store, onjoin } = $props();
	const s = $derived(store.ui);
	// The table needs this much width; where it would scroll sideways the rooms default to tiles (until the user picks a view).
	const TABLE_MIN = 960;
	let width = $state(0);
	$effect(() => {
		if (s.viewPicked || !width) return;
		s.view = width < TABLE_MIN ? 'tile' : 'list';
	});
	let menu = $state(false);
	let missing = $state(null);
	$effect(() => {
		s.query;
		missing = null;
	});

	function submit(e) {
		e.preventDefault();
		const no = exactRoomNo(s.query);
		if (no === null) return;
		if (store.net.rooms[no]) onjoin(no);
		else missing = no;
	}
</script>

<section data-tour="room-list" bind:clientWidth={width} class="kk-card flex min-h-80 min-w-0 flex-1 flex-col overflow-hidden">
	<div class="flex flex-wrap items-center gap-2.5 border-b border-(color:--kk-line) px-3.5 py-2">
		<form onsubmit={submit} class="kk-input flex h-12 min-w-0 flex-1 items-center gap-2 rounded-xl px-4 sm:max-w-sm">
			<Icon name="search" />
			<input bind:value={s.query} aria-label={t('play.rooms.searchLabel')} placeholder={t('play.rooms.searchPlaceholder')} class="min-w-0 flex-1 bg-transparent text-base outline-none" />
		</form>
		<div class="relative ml-auto">
			<button type="button" aria-expanded={menu} onclick={() => (menu = !menu)} class="kk-btn inline-flex items-center gap-2 text-sm">
				<Icon name="tune" />{t('play.rooms.sortFilter')}
				{#if store.activeFilters}<span class="rounded-full bg-(--kk-accent-soft) px-2 text-xs font-extrabold text-(color:--kk-accent-strong)">{store.activeFilters}</span>{/if}
			</button>
			{#if menu}
				<div role="group" aria-label={t('play.rooms.sortFilterLabel')} class="absolute top-12 right-0 z-10 w-64 kk-card p-3">
					<p class="pb-1 text-xs font-bold text-(color:--kk-muted)">{t('play.rooms.sort')}</p>
					<RadioGroup label={t('play.rooms.sort')} value={s.sort} onchange={(v) => store.setRoomOption('sort', v)} options={[{ value: 'recent', label: t('play.rooms.sort.recent') }, { value: 'crowded', label: t('play.rooms.sort.crowded') }]} />
					<div class="pt-1"><Checkbox checked={s.joinableFirst} onchange={(v) => store.setRoomOption('joinableFirst', v)}>{t('play.rooms.sort.joinable')}</Checkbox></div>
					<hr class="my-1.5 border-(color:--kk-line)" />
					<p class="pb-1 text-xs font-bold text-(color:--kk-muted)">{t('play.rooms.filter')}</p>
					<Checkbox checked={s.waitingOnly} onchange={(v) => store.setRoomOption('waitingOnly', v)}>{t('play.rooms.filter.waiting')}</Checkbox>
					<Checkbox checked={s.hasSeat} onchange={(v) => store.setRoomOption('hasSeat', v)}>{t('play.rooms.filter.seat')}</Checkbox>
					<Checkbox checked={s.hideLocked} onchange={(v) => store.setRoomOption('hideLocked', v)}>{t('play.rooms.filter.hideLocked')}</Checkbox>
				</div>
			{/if}
		</div>
		<div role="group" aria-label={t('play.rooms.view')} class="hidden overflow-hidden rounded-xl border-[1.5px] border-(color:--kk-line-strong) md:flex">
			{#each [['list', 'view_list', 'play.rooms.view.list'], ['tile', 'grid_view', 'play.rooms.view.tile']] as [v, icon, key]}
				<Tooltip text={t(key)} placement="bottom"><button type="button" aria-pressed={s.view === v} aria-label={t(key)} onclick={() => {
							store.setRoomOption('view', v);
							store.setRoomOption('viewPicked', true);
						}} class="grid size-11 place-items-center {s.view === v ? 'bg-(--kk-fill) text-white' : 'bg-(--kk-surface) text-(color:--kk-muted) hover:bg-(--kk-accent-soft)'}"><Icon name={icon} /></button></Tooltip>
			{/each}
		</div>
	</div>

	{#if missing !== null}
		<div class="grid flex-1 place-items-center"><EmptyState title={t('play.rooms.notFound.title', { no: missing })} hint={t('play.rooms.notFound.hint')} /></div>
	{:else if store.visibleRooms.length === 0}
		<div class="grid flex-1 place-items-center"><EmptyState title={t('play.rooms.empty.title')} hint={t('play.rooms.empty.hint')} /></div>
	{:else if s.view === 'tile'}
		<div class="grid flex-1 grid-cols-[repeat(auto-fill,minmax(290px,1fr))] content-start gap-3 overflow-y-auto p-3.5 max-md:hidden">
			{#each store.visibleRooms as r (r.no)}<RoomRow room={r} tile onjoin={() => onjoin(r.no)} onmenu={(e) => store.hooks.openRoomMenu?.(r.no, e)} />{/each}
		</div>
	{:else}
		<div class="min-h-0 flex-1 overflow-x-auto max-md:hidden">
			<div class="flex h-full min-w-[960px] flex-col">
				<div class="grid min-h-11 grid-cols-[72px_minmax(220px,1.6fr)_190px_minmax(150px,1.4fr)_120px_110px_84px] items-center border-b border-(color:--kk-line) bg-(--kk-surface-2) px-3.5 text-xs font-extrabold text-(color:--kk-muted)">
					<span>{t('play.rooms.col.no')}</span><span>{t('play.rooms.col.name')}</span><span>{t('play.rooms.col.game')}</span><span>{t('play.rooms.col.rule')}</span><span>{t('play.rooms.col.roundTime')}</span><span>{t('play.rooms.col.players')}</span><span>{t('play.rooms.col.status')}</span>
				</div>
				<div class="min-h-0 flex-1 overflow-y-auto">
					{#each store.visibleRooms as r (r.no)}<RoomRow room={r} onjoin={() => onjoin(r.no)} onmenu={(e) => store.hooks.openRoomMenu?.(r.no, e)} />{/each}
				</div>
			</div>
		</div>
	{/if}

	<!-- Phone: always cards -->
	<div class="flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto p-3 md:hidden">
		{#each store.visibleRooms as r (r.no)}<RoomRow room={r} tile onjoin={() => onjoin(r.no)} onmenu={(e) => store.hooks.openRoomMenu?.(r.no, e)} />{/each}
	</div>
</section>
