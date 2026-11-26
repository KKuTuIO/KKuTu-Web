<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import EmptyState from '../lobby/EmptyState.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import WordDialog from './WordDialog.svelte';
	import BulkDialog from './BulkDialog.svelte';
	import LicenseDialog from './LicenseDialog.svelte';
	import ReasonDialog from './ReasonDialog.svelte';
	import { LIMITS, wordState } from './model.js';
	import { attempt, editing, editsLeft, isManager, loadWords, removeWord, report as sendReport } from './store.svelte.js';

	let { dict, now } = $props();
	let query = $state('');
	let waiting = $state(false); // only the words that are not in the game as shown yet
	let wordOpen = $state(false);
	let target = $state(null);
	let bulkOpen = $state(false);
	let license = $state(false);
	let ask = $state(null);
	const can = $derived(editing(dict));
	const left = $derived(editsLeft(dict));
	let busy = $state(false);
	// the server searches and pages: a new search starts over from the first page
	let page = 0;
	async function load(reset) {
		busy = true;
		try {
			page = reset ? 0 : page + 1;
			await attempt(() => loadWords(dict, query.trim(), page), toast);
		} finally {
			busy = false;
		}
	}
	let timer;
	$effect(() => {
		const q = query;
		const id = dict.id;
		clearTimeout(timer);
		timer = setTimeout(() => void load(true), q ? 250 : 0);
		return () => clearTimeout(timer);
	});
	const rows = $derived(dict.words.filter((w) => !waiting || wordState(w, now).kind !== 'live'));
	const pending = $derived(Math.max(0, dict.wordCount - dict.liveCount));
	function edit(w) {
		target = w;
		wordOpen = true;
	}
	function remove(w) {
		const later = dict.state === 'public' && !isManager(dict);
		ask = {
			title: t('play.malmoi.words.deleteTitle', { word: w.text }),
			ok: t('play.malmoi.common.delete'),
			danger: true,
			done: async (reason, line) => {
				if (await attempt(() => removeWord(dict, w, `${reason}: ${line}`).then(() => loadWords(dict, query.trim(), 0)), toast)) toast(later ? t('play.malmoi.words.deleteLater') : t('play.malmoi.words.deleted'), { kind: 'success' });
			}
		};
	}
	function report(w) {
		ask = {
			title: t('play.malmoi.words.reportTitle', { word: w.text }),
			ok: t('play.malmoi.common.report'),
			done: async (reason, line) => {
				if (await attempt(() => sendReport(dict, { word: w.text, reason, detail: line }), toast)) toast(t('play.malmoi.common.reportedToast'), { kind: 'success' });
			}
		};
	}
	const TONE = { live: 'text-(color:--kk-muted)', pending: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)', removing: 'bg-(--kk-danger-soft) text-(color:--kk-danger)' };
	const act = 'grid size-9 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)';
	const isUrl = (s) => /^https?:\/\//.test(s);
</script>

<div class="flex min-w-0 flex-col gap-3">
	<div class="flex flex-wrap items-center gap-2">
		<input bind:value={query} type="search" placeholder={t('play.malmoi.words.searchHint')} aria-label={t('play.malmoi.words.searchLabel')} class="h-11 min-w-44 flex-1 kk-input px-3" />
		{#if pending}<button type="button" aria-pressed={waiting} onclick={() => (waiting = !waiting)} class="kk-btn px-3 text-sm {waiting ? 'kk-btn-primary' : ''}">{t('play.malmoi.words.waiting', { n: pending })}</button>{/if}
		{#if can.license}
			<button type="button" onclick={() => (license = true)} class="kk-btn kk-btn-primary px-4 text-sm max-sm:w-full">{t('play.malmoi.common.getLicense')}</button>
		{:else if can.ok}
			{#if dict.state === 'public'}<span class="rounded-lg bg-(--kk-surface)/85 px-3 py-1.5 text-xs font-bold whitespace-nowrap text-(color:--kk-muted) tabular-nums">{t('play.malmoi.words.editsLeft', { left, max: LIMITS.dailyEdits })}</span>{/if}
			<div class="flex gap-2 max-sm:w-full">
				<button type="button" disabled={left <= 0} onclick={() => (bulkOpen = true)} class="kk-btn inline-flex items-center justify-center gap-1.5 px-4 text-sm max-sm:flex-1"><Icon name="playlist_add" size={20} />{t('play.malmoi.bulk.title')}</button>
				<button type="button" disabled={left <= 0} onclick={() => edit(null)} class="kk-btn kk-btn-primary inline-flex items-center justify-center gap-1.5 px-4 text-sm max-sm:flex-1"><Icon name="add" size={20} />{t('play.malmoi.word.addTitle')}</button>
			</div>
		{/if}
	</div>

	{#if !rows.length}
		<div class="kk-card"><EmptyState compact size={120} title={query.trim() || dict.wordCount ? (busy ? t('play.malmoi.words.loading') : t('play.malmoi.words.notFound')) : t('play.malmoi.list.noWords')} /></div>
	{:else}
		<ul class="kk-card divide-y divide-(--kk-line) overflow-hidden">
			{#each rows as w (w.id)}
				{@const s = wordState(w, now)}
				<li class="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-x-3 gap-y-1 px-4 py-2.5 md:grid-cols-[minmax(0,12rem)_minmax(0,1fr)_9rem_auto]">
					<b class="min-w-0 pt-1.5 text-base font-black break-all">{w.text}</b>
					<div class="min-w-0 text-sm max-md:col-span-2 max-md:row-start-2 md:pt-2">
						{#each w.meanings as m, i (i)}<p class="break-words">{#if w.meanings.length > 1}<span class="mr-1 text-xs font-extrabold text-(color:--kk-muted)">{i + 1}</span>{/if}{m}</p>{:else}<p class="text-(color:--kk-muted)">{t('play.malmoi.words.noMeaning')}</p>{/each}
						<p class="truncate text-xs text-(color:--kk-muted)">{w.by}{#if w.source} · {#if isUrl(w.source)}<a href={w.source} target="_blank" rel="noreferrer nofollow" class="underline">{t('play.malmoi.common.source')}</a>{:else}{w.source}{/if}{/if}</p>
					</div>
					<span class="justify-self-start rounded-md py-0.5 text-xs font-extrabold whitespace-nowrap max-md:col-span-2 md:mt-2 {s.kind === 'live' ? '' : 'px-2'} {TONE[s.kind]}">{s.label}</span>
					<div class="flex justify-end max-md:col-start-2 max-md:row-start-1">
						{#if can.ok && !w.removeAt}
							<Tooltip text={t('play.malmoi.words.edit')}><button type="button" aria-label={t('play.malmoi.words.editAria', { word: w.text })} onclick={() => edit(w)} class={act}><Icon name="edit" size={20} /></button></Tooltip>
							<Tooltip text={t('play.malmoi.common.delete')}><button type="button" aria-label={t('play.malmoi.words.deleteAria', { word: w.text })} onclick={() => remove(w)} class={act}><Icon name="delete" size={20} /></button></Tooltip>
						{/if}
						<Tooltip text={t('play.malmoi.common.report')}><button type="button" aria-label={t('play.malmoi.words.reportAria', { word: w.text })} onclick={() => report(w)} class={act}><Icon name="flag" size={20} /></button></Tooltip>
					</div>
				</li>
			{/each}
		</ul>
		{#if dict.more}<button type="button" disabled={busy} onclick={() => load(false)} class="kk-btn self-center px-6 text-sm">{t('play.malmoi.words.more')}</button>{/if}
	{/if}
</div>

<WordDialog bind:open={wordOpen} {dict} word={target} onsaved={() => load(true)} />
<BulkDialog bind:open={bulkOpen} {dict} onsaved={() => load(true)} />
<LicenseDialog bind:open={license} />
<ReasonDialog bind:ask />
