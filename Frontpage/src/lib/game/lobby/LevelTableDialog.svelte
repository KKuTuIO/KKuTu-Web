<script>
	import { tick } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { EXP, MAX_LEVEL, requiredScore } from '../level.js';
	import DataTable from '../ui/DataTable.svelte';
	import Dialog from './Dialog.svelte';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import ShopThumb from './ShopThumb.svelte';
	import Tooltip from '../ui/Tooltip.svelte';

	/**
	 * Every level with the experience it takes to reach it and what reaching it gives. `level` is the one to highlight and scroll to;
	 * `nameOf(key)` turns an item id into its name, `shop` draws the pictures.
	 * `table` is what the server sent (`{level: [ping, [[item, q]], [[ticket, q]]]}`); until it arrives the dialog says it is loading.
	 */
	let { open = $bindable(false), level = 0, nameOf = (k) => k, shop = {}, table = null } = $props();

	const commify = (n) => Number(n).toLocaleString('en-US');
	const list = (pairs) => (pairs ?? []).map(([key, q]) => ({ key, q }));
	const rows = $derived(
		table
			? Array.from({ length: MAX_LEVEL }, (_, i) => {
					const lv = i + 1;
					const row = table[lv];
					return { key: lv, lv, total: lv > 1 ? EXP[lv - 2] : 0, step: lv > 1 ? requiredScore(lv - 1) : 0, gift: row ? { ping: row[0], items: list(row[1]), mail: list(row[2]) } : null };
				})
			: []
	);
	const columns = $derived([
		{ key: 'lv', label: t('play.levelTable.level'), width: 'w-28', nowrap: true, class: 'h-12 align-middle' },
		{ key: 'total', label: t('play.levelTable.exp'), align: 'right', numeric: true, nowrap: true, class: 'align-middle' },
		{ key: 'gift', label: t('play.levelTable.reward'), class: 'align-middle' }
	]);

	let box = $state();
	$effect(() => {
		if (!open || !box || !level || !rows.length) return;
		tick().then(() => {
			const table = box?.querySelector('[data-table]');
			const row = table?.querySelector(`[data-row="${level}"]`);
			if (table && row) table.scrollTop = row.offsetTop - table.clientHeight / 2 + row.offsetHeight / 2;
		});
	});
</script>

{#snippet tile(g, mail)}
	<Tooltip text={mail ? `${nameOf(g.key)} · ${t('play.levelTable.mail')}` : nameOf(g.key)}>
		<span class="relative grid size-9 place-items-center rounded-lg border-[1.5px] border-(color:--kk-line) bg-(--kk-surface-2) shadow-[0_2px_0_var(--kk-line)]" role="img" aria-label={nameOf(g.key)}>
			<ShopThumb id={g.key} {shop} class="size-7" />
			{#if g.q > 1}<b class="pointer-events-none absolute right-0 bottom-0 rounded-md bg-(--kk-surface)/90 px-0.5 text-[10px] leading-4 tabular-nums">×{g.q}</b>{/if}
			{#if mail}<span class="pointer-events-none absolute -top-1 -left-1 grid size-4 place-items-center rounded-full bg-(--kk-accent) text-white"><Icon name="mail" size={10} weight={700} /></span>{/if}
		</span>
	</Tooltip>
{/snippet}

<Dialog bind:open title={t('play.levelTable.title')} size="lg">
	<div bind:this={box}>
		{#if !table}<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.profile.loading')}</p>{:else}
		<DataTable caption={t('play.levelTable.title')} {columns} {rows} dense maxHeight="max-h-[60vh]" rowClass={(r) => (r.lv === level ? 'bg-(--kk-accent-soft) font-extrabold' : '')}>
			{#snippet cell(r, col)}
				{#if col.key === 'lv'}
					<span class="inline-flex items-center gap-1.5"><LevelIcon level={r.lv} size={20} />{t('play.profile.level', { level: r.lv })}</span>
				{:else if col.key === 'total'}
					<span class="inline-flex items-center justify-end gap-1"><GameIcon name="xp" size={16} />{commify(r.total)}{#if r.step}<span class="text-[11px] font-bold text-(color:--kk-muted)">(+{commify(r.step)})</span>{/if}</span>
				{:else if r.gift}
					<span class="flex flex-wrap items-center gap-1.5">
						<span class="inline-flex items-center gap-1 pr-1 font-bold tabular-nums"><GameIcon name="ping" size={20} />{commify(r.gift.ping)}</span>
						{#each r.gift.items as g (g.key)}{@render tile(g, false)}{/each}
						{#each r.gift.mail as g (g.key)}{@render tile(g, true)}{/each}
					</span>
				{:else}-{/if}
			{/snippet}
		</DataTable>
		{/if}
		<p class="mt-2 flex items-center gap-1 text-xs text-(color:--kk-muted)"><Icon name="mail" size={14} />{t('play.levelTable.mail')}</p>
	</div>
</Dialog>
