<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from '../lobby/Dialog.svelte';
	import Rich from './Rich.svelte';
	import Moremi from '../lobby/Moremi.svelte';
	import { answer, question } from './confirm.svelte.js';

	let open = $state(false);
	let text = $state(null); // kept while the dialog animates closed
	let typed = $state('');
	let hovered = $state(null); // the option the pointer or the keyboard is on (its `equip` is previewed)
	$effect(() => {
		if (question.current) {
			text = question.current;
			typed = text.value ?? '';
			hovered = null;
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
		{#if text?.preview}
			<div class="grid place-items-center"><Moremi equip={hovered?.equip ?? text.preview.equip} shop={text.preview.shop} parts={text.preview.parts} size={150} frame={false} label={hovered?.label ?? ''} /></div>
		{/if}
		{#if text?.input}<!-- svelte-ignore a11y_autofocus --><input bind:value={typed} autofocus maxlength={text.max} aria-label={text.message} autocomplete="off" class="h-11 kk-input px-3" />{/if}
		<!-- a question with choices has three buttons: on a phone they stack (the choices first, 취소 last), they never break their words -->
		<div class="flex gap-2 {text?.options ? 'flex-col sm:flex-row sm:justify-end' : 'justify-end'}">
			{#if !text?.notice}<button type="button" class="kk-btn min-h-12 px-6 whitespace-nowrap {text?.options ? 'max-sm:order-last' : ''}" onclick={() => answer(false)}>{text?.cancel ?? t('play.confirm.cancel')}</button>{/if}
			{#if text?.options}
				{#each text.options as o (o.value)}<button type="button" class="kk-btn kk-btn-primary min-h-12 px-8 whitespace-nowrap" onclick={() => answer(true, o.value)} onpointerenter={() => (hovered = o)} onpointerleave={() => (hovered = null)} onfocus={() => (hovered = o)} onblur={() => (hovered = null)}>{o.label}</button>{/each}
			{:else}
				<button class="kk-btn kk-btn-primary min-h-12 px-8">{text?.ok ?? t('play.confirm.ok')}</button>
			{/if}
		</div>
	</form>
</Dialog>
