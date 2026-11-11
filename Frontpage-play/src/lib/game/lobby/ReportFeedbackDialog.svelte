<script>
	import { t } from '../i18n.svelte.js';
	import { policyGate } from '../policyGate.svelte.js';
	import { reportRows } from '../reportFeedback.js';
	import Dialog from './Dialog.svelte';
	import DataTable from '../ui/DataTable.svelte';

	/**
	 * The results of the reports I made (the server queues one when the operators acted on a report): all that are waiting are listed
	 * together, once I am in, past the terms screen and not in a game. 확인 acknowledges every one listed; closing it another way leaves
	 * them in the 알림 list, and they are not shown again until the next connection.
	 */
	let { store } = $props();
	let open = $state(false);
	let shown = $state({ ids: [], rows: [] });
	const seen = new Set(); // shown in this visit
	$effect(() => {
		if (open || !policyGate.agreed || store.net.status !== 'ready' || store.inGame) return;
		const fresh = store.net.notifications.filter((n) => n.kind === 'REPORT_RESOLVED' && !seen.has(n.id));
		if (!fresh.length) return;
		for (const n of fresh) seen.add(n.id);
		shown = { ids: fresh.map((n) => n.id), rows: reportRows(fresh) };
		open = true;
	});
	function confirm() {
		for (const id of shown.ids) store.ackNotification(id);
		open = false;
	}
	const columns = $derived([
		{ key: 'target', label: t('play.report.col.target'), width: 'w-2/5', class: 'break-all' },
		{ key: 'reason', label: t('play.report.col.reason'), class: 'break-keep' }
	]);
</script>

<Dialog bind:open title={t('play.report.feedback')}>
	<div class="flex flex-col gap-4">
		<p class="text-base leading-relaxed break-keep whitespace-pre-line">{t('play.report.feedbackBody')}</p>
		<section class="flex flex-col gap-2">
			<h3 class="text-base font-extrabold">{t('play.report.feedbackList')}</h3>
			<DataTable caption={t('play.report.feedbackList')} {columns} rows={shown.rows} maxHeight="max-h-[40dvh]" isWide={(r) => r.raw !== undefined}>
				{#snippet wide(r)}{r.raw}{/snippet}
			</DataTable>
		</section>
		<button type="button" onclick={confirm} class="kk-btn kk-btn-primary min-h-12 w-full text-base">{t('play.mail.confirm')}</button>
	</div>
</Dialog>
