<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from '../lobby/Dialog.svelte';
	import Rich from './Rich.svelte';
	import { answer, question } from './confirm.svelte.js';

	let open = $state(false);
	let text = $state(null); // kept while the dialog animates closed
	let typed = $state('');
	$effect(() => {
		if (question.current) {
			text = question.current;
			typed = text.value ?? '';
			open = true;
		} else open = false;
	});
	// a notice's message in pieces: the markup subset as Rich, the {slots} as plain text
	const pieces = $derived(text?.notice ? String(text.message ?? '').split(/(\{\w+\})/).filter(Boolean).map((s) => { const k = s.match(/^\{(\w+)\}$/)?.[1]; return k && text.slots && k in text.slots ? { plain: String(text.slots[k]) } : { rich: s }; }) : []);
</script>

<Dialog bind:open title={text?.title ?? t('play.chat.system')} onclose={() => question.current && answer(false)}>
	<form class="flex flex-col gap-5" onsubmit={(e) => { e.preventDefault(); answer(true, typed); }}>
		{#if text?.notice}
			<p class="text-base leading-relaxed font-bold break-keep whitespace-pre-line">{#each pieces as p}{#if p.plain}<span>{p.plain}</span>{:else}<Rich html={p.rich} />{/if}{/each}</p>
		{:else}
			<p class="text-base leading-relaxed font-bold break-keep whitespace-pre-line">{text?.message}</p>
		{/if}
		{#if text?.input}<!-- svelte-ignore a11y_autofocus --><input bind:value={typed} autofocus maxlength={text.max} aria-label={text.message} autocomplete="off" class="h-11 kk-input px-3" />{/if}
		<div class="flex justify-end gap-2">
			{#if !text?.notice}<button type="button" class="kk-btn min-h-12 px-6" onclick={() => answer(false)}>{text?.cancel ?? t('play.confirm.cancel')}</button>{/if}
			{#if text?.options}
				{#each text.options as o (o.value)}<button type="button" class="kk-btn kk-btn-primary min-h-12 px-8" onclick={() => answer(true, o.value)}>{o.label}</button>{/each}
			{:else}
				<button class="kk-btn kk-btn-primary min-h-12 px-8">{text?.ok ?? t('play.confirm.ok')}</button>
			{/if}
		</div>
	</form>
</Dialog>
