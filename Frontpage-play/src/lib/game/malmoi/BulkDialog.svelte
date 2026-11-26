<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from '../lobby/Dialog.svelte';
	import Icon from '../lobby/Icon.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { decodeCsv, parseCsv, previewRows, rowsText, textRows, toRows } from './model.js';
	import { addRows, attempt, editsLeft } from './store.svelte.js';

	/** Many words at once: paste from a spreadsheet or open a CSV, look at what saving would do, then save. */
	let { open = $bindable(false), dict, onsaved } = $props();
	let busy = $state(false);
	let text = $state('');
	let preview = $state(null);
	let file = $state();
	$effect(() => {
		if (!open) return;
		text = '';
		preview = null;
	});
	const rows = $derived(textRows(text));
	const count = (status) => preview?.filter((r) => r.status === status).length ?? 0;
	const savable = $derived(count('add') + count('meaning'));
	const saving = $derived(Math.min(savable, editsLeft(dict)));
	const STATUS = {
		add: { label: t('play.malmoi.common.add'), tone: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' },
		meaning: { label: t('play.malmoi.bulk.addMeaning'), tone: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' },
		dup: { label: t('play.malmoi.common.duplicate'), tone: 'bg-(--kk-surface-2) text-(color:--kk-muted)' },
		fail: { label: t('play.malmoi.bulk.cannotSave'), tone: 'bg-(--kk-danger-soft) text-(color:--kk-danger)' }
	};
	async function importCsv(e) {
		const f = e.currentTarget.files?.[0];
		e.currentTarget.value = '';
		if (!f) return;
		const found = toRows(parseCsv(decodeCsv(await f.arrayBuffer())));
		if (!found.length) return toast(t('play.malmoi.bulk.csvEmpty'), { kind: 'error' });
		text = rowsText(found);
	}
	async function save() {
		busy = true;
		const r = await attempt(() => addRows(dict, preview), toast);
		busy = false;
		if (!r) return;
		open = false;
		onsaved?.();
		const n = r.added + r.meaning;
		toast([t('play.malmoi.bulk.saved', { n }), r.stopped ? t('play.malmoi.bulk.stopped') : dict.state === 'public' ? t('play.malmoi.common.appliesLater') : ''].filter(Boolean).join(' '), { kind: r.stopped ? 'info' : 'success' });
	}
</script>

<Dialog bind:open title={t('play.malmoi.bulk.title')} size="lg">
	{#if !preview}
		<div class="flex flex-col gap-3">
			<textarea bind:value={text} rows="9" aria-label={t('play.malmoi.bulk.listLabel')} placeholder={t('play.malmoi.bulk.placeholder')} class="kk-input w-full resize-y px-3 py-2 font-mono text-sm [tab-size:12]"></textarea>
			<div class="flex flex-wrap items-center justify-between gap-2">
				<input bind:this={file} type="file" accept=".csv,text/csv" class="hidden" aria-label={t('play.malmoi.bulk.fileLabel')} onchange={importCsv} />
				<button type="button" onclick={() => file.click()} class="kk-btn inline-flex items-center gap-1.5 px-4 text-sm"><Icon name="upload_file" size={20} />{t('play.malmoi.bulk.loadCsv')}</button>
				<button type="button" disabled={!rows.length} onclick={() => (preview = previewRows(rows, dict))} class="kk-btn kk-btn-primary min-h-12 px-6">{rows.length ? t('play.malmoi.bulk.previewN', { n: rows.length }) : t('play.malmoi.bulk.preview')}</button>
			</div>
		</div>
	{:else}
		<div class="flex flex-col gap-3">
			<p class="flex flex-wrap gap-x-3 gap-y-1 text-sm font-extrabold" role="status">
				<span>{t('play.malmoi.bulk.countAdd', { n: count('add') })}</span><span>{t('play.malmoi.bulk.countMeaning', { n: count('meaning') })}</span><span class="text-(color:--kk-muted)">{t('play.malmoi.bulk.countDup', { n: count('dup') })}</span><span class="text-(color:--kk-danger)">{t('play.malmoi.bulk.countFail', { n: count('fail') })}</span>
			</p>
			{#if saving < savable}<p class="rounded-xl bg-(--kk-danger-soft) px-4 py-3 text-sm font-bold text-(color:--kk-danger)">{t('play.malmoi.bulk.limited', { left: editsLeft(dict), n: saving })}</p>{/if}
			<div class="max-h-80 overflow-y-auto rounded-xl border border-(color:--kk-line)">
				<table class="w-full table-fixed text-left text-sm">
					<thead class="sticky top-0 bg-(--kk-surface-2) text-xs text-(color:--kk-muted)"><tr><th class="w-24 px-3 py-2">{t('play.malmoi.bulk.colVerdict')}</th><th class="w-1/3 px-2 py-2">{t('play.malmoi.common.word')}</th><th class="px-2 py-2">{t('play.malmoi.bulk.colNote')}</th></tr></thead>
					<tbody>
						{#each preview as r, i (i)}
							<tr class="border-t border-(color:--kk-line) align-top">
								<td class="px-3 py-2"><span class="rounded-md px-1.5 py-0.5 text-xs font-extrabold whitespace-nowrap {STATUS[r.status].tone}">{STATUS[r.status].label}</span></td>
								<td class="px-2 py-2 font-extrabold break-all">{r.word}</td>
								<td class="px-2 py-2 break-words">{r.mean}{#if r.note}<span class="block text-xs font-bold text-(color:--kk-danger)">{r.note}</span>{/if}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
			<div class="flex justify-end gap-2">
				<button type="button" onclick={() => (preview = null)} class="kk-btn min-h-12 px-5">{t('play.malmoi.bulk.again')}</button>
				<button type="button" disabled={!saving || busy} onclick={save} class="kk-btn kk-btn-primary min-h-12 px-6">{t('play.malmoi.bulk.saveN', { n: saving })}</button>
			</div>
		</div>
	{/if}
</Dialog>
