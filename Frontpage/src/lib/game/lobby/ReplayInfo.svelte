<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { getLevel } from '../level.js';
	import { clock } from '../replayPayload.js';
	import Icon from './Icon.svelte';
	import LevelIcon from './LevelIcon.svelte';

	/**
	 * What a recording (a .kkio) holds, as the legacy replay dialog said it: when, which version, who played; and a way to watch it
	 * (and to save it, for the game just played).
	 * @type {{store:any, rec:any, name?:string, onwatch:()=>void, onsave?:()=>void}}
	 */
	let { store, rec, name = '', onwatch, onsave } = $props();
	const room = $derived(store.net.meta.labels[`game.mode.${store.net.meta.modes[rec.mode]}.name`] ?? '');
	const length = $derived(rec.events.length ? rec.events[rec.events.length - 1].time : 0);
</script>

<article class="flex flex-col gap-3 rounded-2xl border border-(color:--kk-line) bg-(--kk-surface) p-4 shadow-[0_2px_0_var(--kk-line)]">
	<header class="flex items-start gap-3">
		<span class="grid size-12 shrink-0 place-items-center rounded-xl bg-(--kk-accent-soft) text-(color:--kk-accent-strong)"><Icon name="movie" size={26} fill /></span>
		<div class="min-w-0 flex-1">
			<h3 class="truncate text-base font-extrabold">{rec.title || name}</h3>
			<p class="truncate text-xs text-(color:--kk-muted)">{room}{room ? ' · ' : ''}{rec.round}R · {clock(length)}{name ? ` · ${name}` : ''}</p>
		</div>
	</header>
	<dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
		<dt class="font-bold text-(color:--kk-muted)">{t('kkutu.dialog.replay.recordDate')}</dt>
		<dd class="tabular-nums">{new Date(rec.time).toLocaleString(i18n.language)}</dd>
		<dt class="font-bold text-(color:--kk-muted)">{t('kkutu.dialog.replay.recordVersion')}</dt>
		<dd>{rec.version ?? '-'}</dd>
		<dt class="font-bold text-(color:--kk-muted)">{t('kkutu.dialog.replay.recordPlayers')}</dt>
		<dd class="flex flex-wrap gap-1.5">
			{#each rec.players as p (p.id)}
				<span class="inline-flex max-w-full items-center gap-1 rounded-lg bg-(--kk-surface-2) px-2 py-1 text-xs {p.id === rec.me ? 'font-extrabold' : ''}"><LevelIcon level={p.robot ? 1 : getLevel(p.data?.score ?? 0)} size={16} /><span class="truncate">{p.title}</span></span>
			{/each}
		</dd>
	</dl>
	<div class="flex flex-wrap justify-end gap-2">
		{#if onsave}<button type="button" class="kk-btn inline-flex items-center gap-1.5" onclick={onsave}><Icon name="download" size={19} />{t('kkutu.dialog.result.saveReplay')}</button>{/if}
		<button type="button" class="kk-btn kk-btn-primary inline-flex items-center gap-1.5 px-5" onclick={onwatch}><Icon name="play_arrow" size={20} fill />{t('kkutu.dialog.replay.viewBtn')}</button>
	</div>
</article>
