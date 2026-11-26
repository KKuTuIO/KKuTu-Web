<script>
	import { tick } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { academyClient } from '../academy/academyClient.js';
	import { friendlyError } from '../academy/api.js';
	import { analysisConfig } from '../replayPayload.js';
	import { toast } from '../ui/toast.svelte.js';
	import Icon from './Icon.svelte';

	/**
	 * 복기: the words of a finished game (the whole chain, each player's own), and for a chain mode, what else could have been said after a
	 * word (the academy's engine, under the rules that game was played with).
	 * @type {{sheet:any}} a sheet whose `review` is there (see `summarize`)
	 */
	let { sheet } = $props();
	const review = $derived(sheet.review);
	const config = $derived(analysisConfig(sheet.room));
	let query = $state('');
	let selected = $state('');
	let moves = $state(null);
	let loading = $state(false);
	let error = $state('');
	let panel = $state();
	let asked = 0;
	const shown = $derived(review.unique.filter((w) => !query || w.includes(query)));
	const nameOf = (i) => sheet.players[i]?.name ?? '?';
	const stateLabel = (s) => (s ? (t(`play.academy.replay.state.${s}`) === `play.academy.replay.state.${s}` ? s : t(`play.academy.replay.state.${s}`)) : '-');

	async function inspect(word) {
		const mine = ++asked; // a slower answer for a word clicked earlier is dropped
		selected = word;
		moves = null;
		error = '';
		loading = true;
		await tick();
		panel?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
		try {
			const need = config.direction === 'REVERSE' ? word[0] : word.at(-1);
			const answer = await academyClient.strategy(config, need, review.unique, 6);
			if (mine !== asked) return;
			moves = answer;
			await tick();
			panel?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
		} catch (cause) {
			if (mine === asked) error = friendlyError(cause);
		} finally {
			if (mine === asked) loading = false;
		}
	}
	async function copyChain() {
		try {
			await navigator.clipboard.writeText(review.accepted.join(' → '));
			toast(t('play.academy.copied'), { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}
</script>

<div class="flex min-w-0 shrink-0 flex-col gap-3">
	<dl class="grid grid-cols-2 gap-3 lg:grid-cols-4">
		{#each [[t('play.academy.replay.accepted'), t('play.academy.replay.acceptedVal', { n: review.unique.length })], [t('play.academy.replay.longest'), review.longest], [t('play.academy.replay.uniqueInput'), review.typed], [t('play.academy.replay.inputEvents'), review.events]] as [k, v] (k)}
			<div class="kk-card min-w-0 p-3"><dt class="text-xs font-bold text-(color:--kk-muted)">{k}</dt><dd class="mt-1 text-lg font-black break-all tabular-nums">{v}</dd></div>
		{/each}
	</dl>

	<section class="kk-card flex flex-col gap-3 p-3">
		<div class="flex flex-wrap items-center justify-between gap-2">
			<h3 class="text-sm font-extrabold">{t(review.ordered ? 'play.academy.replay.chain' : 'play.academy.replay.allAccepted')}</h3>
			<div class="flex gap-2"><input bind:value={query} placeholder={t('play.academy.replay.filter')} aria-label={t('play.academy.replay.filter')} class="kk-input h-10 w-36 px-3 text-sm" /><button type="button" onclick={copyChain} aria-label={t('play.academy.replay.copyChain')} class="kk-btn grid size-10 place-items-center p-0"><Icon name="content_copy" size={18} /></button></div>
		</div>
		<div class="flex max-h-80 flex-wrap gap-2 overflow-y-auto">
			{#each shown as word, i (word)}
				<svelte:element this={config ? 'button' : 'span'} type={config ? 'button' : undefined} role={config ? 'button' : undefined} aria-pressed={config ? selected === word : undefined} onclick={config ? () => inspect(word) : undefined} class="rounded-xl border-[1.5px] px-3 py-1.5 text-left {selected === word ? 'border-(color:--kk-accent) bg-(--kk-accent-soft)' : 'border-(color:--kk-line) bg-(--kk-surface-2)'} {config ? 'hover:border-(color:--kk-accent)' : ''}"><span class="block text-[10px] font-bold text-(color:--kk-muted) tabular-nums">{i + 1}</span><strong class="break-all">{word}</strong></svelte:element>
			{/each}
		</div>
	</section>

	{#if selected}
		<section bind:this={panel} aria-live="polite" class="kk-card flex flex-col gap-3 border-(color:--kk-blue) p-3">
			<div class="flex flex-wrap items-center justify-between gap-2">
				<h3 class="text-sm font-extrabold">{t('play.academy.replay.others', { word: selected })}</h3>
				{#if moves}<span class="kk-chip">{stateLabel(moves.state)}</span>{/if}
			</div>
			{#if loading}
				<p role="status" class="flex items-center justify-center gap-2 py-3 text-sm text-(color:--kk-muted)"><Icon name="progress_activity" size={20} class="animate-spin" />{t('play.profile.loading')}</p>
			{:else if error}
				<p role="alert" class="rounded-xl bg-(--kk-danger-soft) px-3 py-2 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>
			{:else if moves?.alternatives.length}
				<div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{#each moves.alternatives.slice(0, 12) as move (move.word)}<div class="min-w-0 rounded-xl bg-(--kk-surface-2) p-3"><strong class="break-all">{move.word}</strong><span class="ml-2 text-xs text-(color:--kk-muted)">→{move.to}</span><p class="mt-1 text-xs text-(color:--kk-muted)">{t('play.academy.replay.defenseState', { n: move.defenseCount, state: stateLabel(move.resultingState) })}</p></div>{/each}</div>
			{:else}
				<p class="py-3 text-center text-sm text-(color:--kk-muted)">{t('play.replay.review.noMoves')}</p>
			{/if}
		</section>
	{/if}

	{#if review.chains.length}
		<section class="kk-card flex flex-col gap-3 p-3">
			<h3 class="text-sm font-extrabold">{t('play.academy.replay.perPlayer')}</h3>
			<div class="grid min-w-0 gap-3 lg:grid-cols-2">
				{#each review.chains as entry, i (i)}
					<article class="min-w-0 rounded-xl bg-(--kk-surface-2) p-3">
						<div class="flex items-center justify-between gap-2"><strong class="min-w-0 truncate text-sm">{nameOf(entry.player)}</strong><span class="kk-chip shrink-0">{t('play.academy.replay.acceptedVal', { n: entry.words.length })}</span></div>
						<div class="mt-2 flex max-h-40 flex-wrap gap-1.5 overflow-y-auto">
							{#each entry.words as word, j (j)}
								<svelte:element this={config ? 'button' : 'span'} type={config ? 'button' : undefined} role={config ? 'button' : undefined} onclick={config ? () => inspect(word) : undefined} class="rounded-lg bg-(--kk-surface) px-2.5 py-1 text-sm font-bold break-all {config ? 'hover:text-(color:--kk-accent-strong)' : ''}">{word}</svelte:element>
							{/each}
						</div>
					</article>
				{/each}
			</div>
		</section>
	{/if}
</div>
