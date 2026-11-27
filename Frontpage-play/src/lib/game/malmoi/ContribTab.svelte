<script>
	import { t } from '../i18n.svelte.js';
	import { dayKey, grid } from './model.js';
	import Person from './Person.svelte';
	import RoleIcon from './RoleIcon.svelte';

	/** 기여: the days a 말모이 was worked on (a grid like GitHub's) and who holds how much of it. The one holding the most, if above the share line, is the 최다 기여자. */
	let { dict, now } = $props();
	const WEEKS = 26;
	const cols = $derived(grid(dict.activity ?? [], now, WEEKS));
	const people = $derived(dict.people ?? []);
	const days = $derived(cols.flat().filter((c) => c && c.n > 0).length);
	const total = $derived(people.reduce((s, p) => s + p.count, 0));
	const loaded = $derived(dict.loaded);
	// a month is named above its first column, and not again within four columns of the last name
	const months = $derived.by(() => {
		let last = -9;
		return cols.map((col, i) => {
			if (!col[0] || (i > 0 && cols[i - 1][0]?.month === col[0].month) || i - last < 4) return '';
			last = i;
			return t('play.malmoi.contrib.month', { n: col[0].month + 1 });
		});
	});
	const LEVEL = ['var(--kk-surface-2)', 'color-mix(in srgb, var(--kk-accent) 28%, var(--kk-surface-2))', 'color-mix(in srgb, var(--kk-accent) 50%, var(--kk-surface-2))', 'color-mix(in srgb, var(--kk-accent) 75%, var(--kk-surface-2))', 'var(--kk-accent-strong, var(--kk-accent))'];
	const today = $derived(dayKey(now));
</script>

<div class="flex min-w-0 flex-col gap-3">
	<div class="kk-card flex flex-col gap-3 p-4">
		<h3 class="text-sm font-black">{t('play.malmoi.contrib.summary', { weeks: WEEKS, days })}</h3>
<div class="overflow-x-auto pb-1">
			<div class="min-w-[26rem] max-w-[38rem]" role="img" aria-label={t('play.malmoi.contrib.gridLabel', { weeks: WEEKS, days })}>
				<div class="mb-1 flex gap-[3px] text-[10px] leading-3 font-bold text-(color:--kk-muted)" aria-hidden="true">{#each months as m, i (i)}<span class="min-w-0 flex-1 overflow-visible whitespace-nowrap">{m}</span>{/each}</div>
				<div class="flex gap-[3px]">
					{#each cols as col, i (i)}
						<div class="flex min-w-0 flex-1 flex-col gap-[3px]">
							{#each col as cell, j (j)}
								{#if cell}<span title={t('play.malmoi.contrib.cellTip', { day: cell.key, n: cell.n })} class="block aspect-square w-full rounded-[3px] {cell.key === today ? 'outline outline-1 outline-offset-1 outline-(color:--kk-text)/50' : ''}" style:background={LEVEL[cell.level]}></span>
								{:else}<span class="block aspect-square w-full"></span>{/if}
							{/each}
						</div>
					{/each}
				</div>
			</div>
		</div>
		<div class="flex items-center justify-end gap-1 text-[11px] font-bold text-(color:--kk-muted)" aria-hidden="true">{t('play.malmoi.contrib.less')}{#each LEVEL as c (c)}<span class="size-3 rounded-[3px]" style:background={c}></span>{/each}{t('play.malmoi.contrib.more')}</div>
	</div>

	<div class="kk-card flex flex-col gap-2 p-4">
		<h3 class="text-sm font-black">{t('play.malmoi.contrib.people')}</h3>
		{#if !people.length}<p class="text-sm text-(color:--kk-muted)">{loaded ? t('play.malmoi.contrib.none') : ''}</p>{/if}
		{#if people.length}
			<div class="flex h-3 w-full overflow-hidden rounded-full bg-(--kk-surface-2)" role="img" aria-label={t('play.malmoi.contrib.ratio')} >
				{#each people as p (p.name)}<span style:width="{p.share * 100}%" style:background={p.top ? 'var(--kk-accent)' : 'var(--kk-line-strong)'} class="border-r border-(--kk-surface) last:border-r-0"></span>{/each}
			</div>
			<ul class="flex flex-col">
				{#each people as p (p.name)}
					<li class="flex min-h-12 items-center gap-2.5 border-b border-(color:--kk-line) last:border-b-0">
						<Person name={p.name} size={34} />
						<span class="min-w-0 flex-1 truncate text-sm font-extrabold">{p.name}</span>
						{#if p.name === dict.owner}<RoleIcon role="owner" />{/if}
						{#if p.top}<RoleIcon role="top" />{/if}
						<span class="w-12 shrink-0 text-right text-sm font-bold tabular-nums">{Math.round(p.share * 100)}%</span>
					</li>
				{/each}
			</ul>
			<p class="text-xs text-(color:--kk-muted) tabular-nums">{t('play.malmoi.contrib.basis', { n: total.toLocaleString() })}</p>
		{/if}
	</div>
</div>
