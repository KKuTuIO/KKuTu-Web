<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { academyClient } from './academyClient.js';
	import { friendlyError } from './api.js';
	import Select from '../ui/Select.svelte';
	import Icon from '../lobby/Icon.svelte';
	import EmptyState from '../lobby/EmptyState.svelte';
	import WordCard from './WordCard.svelte';

	let { config, meta = null } = $props();
	let text = $state('');
	let match = $state('CONTAINS');
	let start = $state('');
	let end = $state('');
	let mission = $state('');
	let sort = $state('HIT_DESC');
	let page = $state(0);
	let loading = $state(false);
	let error = $state('');
	let result = $state({ items: [], hasNext: false, page: 0, size: 30 });
	let more = $state(false); // phone: the finer conditions (match kind, first / last / mission letter, sort) are folded away until asked for

	const matches = $derived(['CONTAINS', 'EXACT', 'STARTS_WITH', 'ENDS_WITH'].map((v) => ({ value: v, label: t(`play.academy.dict.match.${v}`) })));
	const sorts = $derived(['HIT_DESC', 'LENGTH_DESC', 'LENGTH_ASC', 'WORD_ASC', 'WORD_DESC'].map((v) => ({ value: v, label: t(`play.academy.dict.sort.${v}`) })));

	async function search(target = 0) {
		loading = true;
		error = '';
		try {
			result = await academyClient.search(config, { text, match, start, end, mission, sort, page: target, size: 30 });
			page = result.page;
		} catch (cause) {
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
	function clear() {
		text = start = end = mission = '';
		match = 'CONTAINS';
		sort = 'HIT_DESC';
		search(0);
	}
	onMount(() => search(0));
</script>

{#snippet pager()}
	<div class="flex items-center gap-1.5" role="group" aria-label={t('play.academy.dict.pagination')}>
		<button type="button" class="kk-btn grid size-10 place-items-center p-0" aria-label={t('play.academy.dict.prev')} disabled={page === 0 || loading} onclick={() => search(page - 1)}><Icon name="chevron_left" size={22} /></button>
		<span class="min-w-16 text-center text-sm font-extrabold tabular-nums" aria-live="polite">{t('play.academy.dict.page', { n: page + 1 })}</span>
		<button type="button" class="kk-btn grid size-10 place-items-center p-0" aria-label={t('play.academy.dict.next')} disabled={!result.hasNext || loading} onclick={() => search(page + 1)}><Icon name="chevron_right" size={22} /></button>
	</div>
{/snippet}

<div class="grid min-w-0 gap-4 lg:grid-cols-[19rem_minmax(0,1fr)]">
	<!-- Stays in view while the results scroll (only beside them: on a phone it would sit over the results it is for). -->
	<form onsubmit={(e) => (e.preventDefault(), search(0))} class="kk-card flex h-fit min-w-0 flex-col gap-3 self-start p-3 lg:sticky lg:top-0 lg:p-4">
		<p class="text-sm text-(color:--kk-muted) max-lg:hidden">{t('play.academy.dict.sub')}</p>
		<div class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted) max-lg:hidden">{t('play.academy.dict.query')}</span>
			<div class={more ? '' : 'max-lg:hidden'}><Select label={t('play.academy.dict.match')} bind:value={match} options={matches} /></div>
			<input bind:value={text} maxlength="100" placeholder={t('play.academy.dict.placeholder')} aria-label={t('play.academy.dict.query')} class="h-11 kk-input px-3" />
		</div>
		<div class="grid grid-cols-3 gap-2 {more ? '' : 'max-lg:hidden'}">
			{#each [['start', t('play.academy.dict.start')], ['end', t('play.academy.dict.end')], ['mission', t('play.academy.dict.mission')]] as [k, label] (k)}
				<label class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{label}</span>
					{#if k === 'start'}<input bind:value={start} maxlength="1" class="h-11 kk-input px-1 text-center text-lg font-black" />
					{:else if k === 'end'}<input bind:value={end} maxlength="1" class="h-11 kk-input px-1 text-center text-lg font-black" />
					{:else}<input bind:value={mission} maxlength="1" class="h-11 kk-input px-1 text-center text-lg font-black" />{/if}
				</label>
			{/each}
		</div>
		<div class="flex flex-col gap-1 {more ? '' : 'max-lg:hidden'}"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.dict.sort')}</span><Select label={t('play.academy.dict.sort')} bind:value={sort} options={sorts} /></div>
		<div class="flex gap-2">
			<button class="kk-btn kk-btn-primary inline-flex min-h-11 flex-1 items-center justify-center gap-1.5" disabled={loading}><Icon name={loading ? 'progress_activity' : 'search'} size={20} class={loading ? 'animate-spin' : ''} />{t('play.academy.dict.search')}</button>
			<button type="button" onclick={clear} aria-label={t('play.academy.dict.reset')} class="kk-btn grid size-11 place-items-center p-0"><Icon name="restart_alt" size={22} /></button>
			<button type="button" onclick={() => (more = !more)} aria-expanded={more} aria-label={t('play.academy.rule.advanced')} class="kk-btn grid size-11 place-items-center p-0 lg:hidden {more ? 'border-(color:--kk-accent)' : ''}"><Icon name="tune" size={22} /></button>
		</div>
	</form>

	<section class="flex min-w-0 flex-col gap-3">
		<div class="flex flex-wrap items-center justify-between gap-2">
			<h2 class="text-xl font-black">{result.items.length ? t('play.academy.dict.count', { n: result.items.length }) : t('play.academy.dict.setConditions')}</h2>
			{@render pager()}
		</div>
		{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-4 py-3 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>{/if}
		{#if loading && !result.items.length}
			<div class="grid min-w-0 gap-3 xl:grid-cols-2" role="status" aria-label={t('play.academy.dict.searching')}>{#each Array(6) as _, i (i)}<div class="kk-card h-24 animate-pulse bg-(--kk-surface-2)"></div>{/each}</div>
		{:else if !result.items.length}
			<EmptyState compact size={130} title={t('play.academy.dict.none')} />
			<p class="text-center text-sm text-(color:--kk-muted)">{t('play.academy.dict.noneHint')}</p>
		{:else}
			<div class="grid min-w-0 gap-3 xl:grid-cols-2">{#each result.items as word (word.word)}<WordCard {word} themeNames={meta?.themes ?? {}} />{/each}</div>
			<div class="flex justify-center">{@render pager()}</div>
		{/if}
	</section>
</div>

