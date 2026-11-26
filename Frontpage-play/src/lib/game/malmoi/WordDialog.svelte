<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from '../lobby/Dialog.svelte';
	import Icon from '../lobby/Icon.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { maybeOneShot, normalizeWord, wordError } from './model.js';
	import { addWord, attempt, changeWord, editsLeft } from './store.svelte.js';

	/** One word: add (`word` null) or change. 품사, 주제 and flags are fixed by the 말모이, so only 뜻 and 출처 are asked. */
	let { open = $bindable(false), dict, word = null, onsaved } = $props();
	let saving = $state(false);
	let text = $state('');
	let meanings = $state(['']);
	let source = $state('');
	$effect(() => {
		if (!open) return;
		const from = word?.next ?? word;
		text = word?.text ?? '';
		meanings = from?.meanings.length ? [...from.meanings] : [''];
		source = from?.source ?? '';
	});
	const clean = $derived(normalizeWord(text));
	const taken = $derived(!word && dict.words.some((w) => w.text === clean));
	const error = $derived(!text ? '' : wordError(text) || (taken ? t('play.malmoi.word.taken') : ''));
	const kept = $derived(meanings.map((m) => m.trim()).filter(Boolean));
	const same = $derived.by(() => {
		const from = word?.next ?? word;
		return !!from && source.trim() === from.source && kept.join('\n') === from.meanings.join('\n');
	});
	const ready = $derived(!!clean && !error && kept.length > 0 && !same && editsLeft(dict) > 0 && !saving);
	async function save(e) {
		e.preventDefault();
		if (!ready) return;
		saving = true;
		const value = { text: clean, meanings: kept, source: source.trim() };
		const res = await attempt(() => (word ? changeWord(dict, word, value) : addWord(dict, value)), toast);
		saving = false;
		if (!res) return;
		open = false;
		onsaved?.();
		if (res.status === 'dup') return toast(t('play.malmoi.word.sameExists'), { kind: 'info' });
		toast(dict.state === 'public' ? t('play.malmoi.word.savedLater') : t('play.malmoi.word.savedNow'), { kind: 'success' });
	}
</script>

<Dialog bind:open title={word ? t('play.malmoi.word.editTitle') : t('play.malmoi.word.addTitle')}>
	<form onsubmit={save} class="flex flex-col gap-4">
		<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.common.word')}</span>
			{#if word}<b class="text-xl font-black break-all">{word.text}</b>
			{:else}<input bind:value={text} maxlength="100" autocomplete="off" placeholder={t('play.malmoi.word.placeholder')} class="h-11 kk-input px-3 text-lg font-extrabold" />{/if}
		</label>
		{#if error}<p class="-mt-2 text-sm font-bold text-(color:--kk-danger)" role="alert">{error}</p>
		{:else if !word && clean}
			<p class="-mt-2 flex flex-wrap items-center gap-1.5 text-xs text-(color:--kk-muted)">
				{#if clean !== text}<span>{t('play.malmoi.word.saveAs')} <b>{clean}</b></span>{/if}
				<span class="rounded bg-(--kk-surface-2) px-1.5 py-0.5 tabular-nums">{t('play.malmoi.word.length', { n: clean.length })}</span>
				{#if maybeOneShot(clean)}<span class="rounded bg-(--kk-danger-soft) px-1.5 py-0.5 font-bold text-(color:--kk-danger)">{t('play.malmoi.word.deadEnd')}</span>{/if}
			</p>
		{/if}
		<fieldset class="flex flex-col gap-2">
			<legend class="pb-1 text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.common.meaning')}</legend>
			{#each meanings as _, i (i)}
				<div class="flex items-center gap-2">
					<span class="w-5 shrink-0 text-center text-sm font-extrabold text-(color:--kk-muted)">{i + 1}</span>
					<input bind:value={meanings[i]} maxlength="200" aria-label={t('play.malmoi.word.meaningN', { n: i + 1 })} autocomplete="off" class="h-11 min-w-0 flex-1 kk-input px-3" />
					{#if meanings.length > 1}<button type="button" aria-label={t('play.malmoi.word.removeMeaning', { n: i + 1 })} onclick={() => meanings.splice(i, 1)} class="grid size-11 shrink-0 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2)"><Icon name="remove_circle" size={22} /></button>{/if}
				</div>
			{/each}
			<button type="button" onclick={() => meanings.push('')} disabled={meanings.length >= 8} class="kk-btn inline-flex min-h-10 items-center gap-1 self-start px-3 text-sm"><Icon name="add" size={18} />{t('play.malmoi.word.addMeaning')}</button>
		</fieldset>
		<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.common.source')}</span>
			<input bind:value={source} maxlength="200" autocomplete="off" placeholder={dict.criteria.source || t('play.malmoi.word.sourceHint')} class="h-11 kk-input px-3" />
		</label>
		<div class="flex flex-wrap items-center justify-between gap-2 border-t border-(color:--kk-line) pt-3">
			<p class="text-xs font-bold text-(color:--kk-muted) tabular-nums">{#if dict.state === 'public'}{t('play.malmoi.word.editsLeft', { n: editsLeft(dict) })}{/if}</p>
			<button class="kk-btn kk-btn-primary min-h-12 px-8" disabled={!ready}>{t('play.malmoi.common.save')}</button>
		</div>
	</form>
</Dialog>
