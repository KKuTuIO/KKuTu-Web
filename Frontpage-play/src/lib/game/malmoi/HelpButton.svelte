<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from '../lobby/Dialog.svelte';
	import Icon from '../lobby/Icon.svelte';
	import { help } from './help.js';

	/** The button at the top right of every view; `topic` picks what it explains. */
	let { topic } = $props();
	let open = $state(false);
</script>

<button type="button" aria-label={t('play.malmoi.common.help')} onclick={() => (open = true)} class="kk-btn inline-flex h-11 min-w-11 shrink-0 items-center justify-center gap-1.5 px-0 text-sm md:px-3.5"><Icon name="help" size={20} /><span class="max-md:hidden">{t('play.malmoi.common.help')}</span></button>

<Dialog bind:open title={t('play.malmoi.help.title')}>
	<div class="flex flex-col gap-4">
		{#each help()[topic] as [head, body] (head)}
			<section>
				<h3 class="font-black">{head}</h3>
				<p class="mt-1 text-sm leading-relaxed break-keep text-(color:--kk-muted)">{body}</p>
			</section>
		{/each}
	</div>
</Dialog>
