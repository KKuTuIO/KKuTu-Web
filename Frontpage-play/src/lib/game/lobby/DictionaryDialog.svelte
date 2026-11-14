<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { lookupWord } from '../dictionary.js';
	import WordEntry from './WordEntry.svelte';
	import FloatWindow from './FloatWindow.svelte';
	import Icon from './Icon.svelte';

	/** `initial`: a word to look up as soon as it opens (the riddle's answer), instead of an empty field. */
	let { open = $bindable(false), initial = '' } = $props();
	let query = $state('');
	let input = $state();
	let busy = $state(false);
	/** @type {{word:string,mean:string,theme?:string,type?:string}|{error:number}|null} */
	let result = $state(null);

	// Fresh each time it opens; the field is ready to type in.
	$effect(() => {
		if (open) {
			query = initial;
			result = null;
			if (initial) void run();
			else queueMicrotask(() => setTimeout(() => input?.focus(), 60));
		}
	});

	function search(e) {
		e.preventDefault();
		return run();
	}
	async function run() {
		if (busy || !query.trim()) return;
		busy = true;
		result = null;
		try {
			result = await lookupWord(query);
		} finally {
			busy = false;
		}
	}
</script>


<!-- A floating window, not a modal: the dictionary is meant to stay open next to the game. -->
<FloatWindow bind:open id="dictionary" title={t('play.dict.title')}>
	<div class="flex min-h-[22rem] flex-col gap-4">
		<form onsubmit={search} class="flex items-stretch gap-2">
			<input bind:this={input} bind:value={query} maxlength="200" autocomplete="off" aria-label={t('play.dict.label')} placeholder={t('play.dict.placeholder')} class="h-11 min-w-0 flex-1 kk-input px-3" />
			<button class="kk-btn kk-btn-primary inline-flex items-center gap-1.5 px-4 text-sm" disabled={busy || !query.trim()}><Icon name="search" size={20} />{t('play.dict.search')}</button>
		</form>

		<div class="min-h-0 flex-1" aria-live="polite" aria-busy={busy}>
			{#if busy}
				<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.dict.searching')}</p>
			{:else if result}
				<WordEntry {result} />
			{:else}
				<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.dict.hint')}</p>
			{/if}
		</div>
	</div>
</FloatWindow>
