<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import Cover from './Cover.svelte';
	import { titleOf } from './model.js';

	/** 말모이 names with their covers, for a tooltip or a sheet (it takes the text colour of whichever it is in). */
	let { books, limit = Infinity } = $props();
</script>

<ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-left">
	{#each books.slice(0, limit) as d (d.id)}
		<li class="flex min-w-0 items-center gap-2">
			<Cover cover={d.cover} size={22} />
			<span class="min-w-0 truncate text-sm font-bold">{titleOf(d)}</span>
			{#if d.state === 'unlisted'}<span class="ml-auto shrink-0 pl-3" role="img" aria-label={t('play.malmoi.common.unlisted')} title={t('play.malmoi.common.unlisted')}><Icon name="lock" size={16} fill /></span>{/if}
		</li>
	{/each}
	{#if books.length > limit}<li class="text-xs opacity-70">{t('play.malmoi.chip.more', { n: books.length - limit })}</li>{/if}
</ul>
