<script>
	import { t } from '../i18n.svelte.js';
	import { lookupWord } from '../dictionary.js';
	import Dialog from '../lobby/Dialog.svelte';
	import WordEntry from '../lobby/WordEntry.svelte';

	/** A word's definition, in a popup (opened by clicking a word in the academy's lists). */
	let { word = '', open = $bindable(false) } = $props();
	let result = $state(null);
	let busy = $state(false);
	let seq = 0;

	$effect(() => {
		if (!open || !word) return;
		const mine = ++seq;
		result = null;
		busy = true;
		lookupWord(word).then((r) => {
			if (mine !== seq) return;
			result = r;
			busy = false;
		});
	});
</script>

<Dialog bind:open title={t('play.dict.title')}>
	<div class="min-h-32" aria-live="polite" aria-busy={busy}>
		{#if busy}<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.dict.searching')}</p>
		{:else if result}<WordEntry {result} />{/if}
	</div>
</Dialog>
