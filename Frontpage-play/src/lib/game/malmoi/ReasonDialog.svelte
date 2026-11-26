<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from '../lobby/Dialog.svelte';
	import { reasons, LIMITS } from './model.js';

	/**
	 * Why a word goes, why a person is blocked, what is being reported: a reason from the list and one line about it.
	 * `ask` is `{title, ok, danger?, days?, done(reason, line, days)}`, or null when closed.
	 */
	let { ask = $bindable(null) } = $props();
	let open = $state(false);
	let shown = $state(null); // kept while the dialog animates closed
	let reason = $state('');
	let line = $state('');
	let days = $state(LIMITS.blockDays);
	$effect(() => {
		if (ask) {
			shown = ask;
			reason = line = '';
			days = LIMITS.blockDays;
			open = true;
		} else open = false;
	});
	function submit(e) {
		e.preventDefault();
		if (!reason || !line.trim()) return;
		const q = shown;
		ask = null;
		q.done(reason, line.trim(), days);
	}
</script>

<Dialog bind:open title={shown?.title ?? ''} onclose={() => (ask = null)}>
	<form onsubmit={submit} class="flex flex-col gap-4">
		<div class="flex flex-wrap gap-2" role="radiogroup" aria-label={t('play.malmoi.reason.label')}>
			{#each reasons() as r (r)}
				<button type="button" role="radio" aria-checked={reason === r} onclick={() => (reason = r)} class="kk-btn min-h-10 px-3 text-sm {reason === r ? 'kk-btn-primary' : ''}">{r}</button>
			{/each}
		</div>
		<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.reason.line')}</span>
			<input bind:value={line} maxlength="100" autocomplete="off" class="h-11 kk-input px-3" />
		</label>
		{#if shown?.days}
			<div class="flex flex-wrap items-center gap-2" role="radiogroup" aria-label={t('play.malmoi.reason.blockDays')}>
				<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.reason.period')}</span>
				{#each [1, 3, LIMITS.blockDays] as d (d)}<button type="button" role="radio" aria-checked={days === d} onclick={() => (days = d)} class="kk-btn min-h-10 px-3 text-sm {days === d ? 'kk-btn-primary' : ''}">{t('play.malmoi.reason.days', { n: d })}</button>{/each}
			</div>
		{/if}
		<div class="flex justify-end gap-2">
			<button type="button" class="kk-btn min-h-12 px-6" onclick={() => (ask = null)}>{t('play.malmoi.common.cancel')}</button>
			<button class="kk-btn {shown?.danger ? 'kk-btn-danger' : 'kk-btn-primary'} min-h-12 px-8" disabled={!reason || !line.trim()}>{shown?.ok}</button>
		</div>
	</form>
</Dialog>
