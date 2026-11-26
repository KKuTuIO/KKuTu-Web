<script>
	import { t } from '../i18n.svelte.js';
	import { academyClient } from '../academy/academyClient.js';
	import { friendlyError } from '../academy/api.js';
	import Icon from './Icon.svelte';

	/**
	 * 복기: what else could have been said after a word of a finished game (the academy's engine, under the rules that game was played with).
	 * It opens under the word's own line in the timeline.
	 * @type {{word:string, config:any, used:string[], onready?:()=>void}} `onready`: the answer is on the page (the line can scroll it into view)
	 */
	let { word, config, used, onready } = $props();
	let moves = $state(null);
	let error = $state('');
	const stateLabel = (s) => (s ? (t(`play.academy.replay.state.${s}`) === `play.academy.replay.state.${s}` ? s : t(`play.academy.replay.state.${s}`)) : '-');

	$effect(() => {
		const need = config.direction === 'REVERSE' ? word[0] : word.at(-1);
		let live = true; // an answer that comes after the line was closed (or another one opened) is dropped
		moves = null;
		error = '';
		academyClient
			.strategy(config, need, used, 6)
			.then((answer) => live && (moves = answer))
			.catch((cause) => live && (error = friendlyError(cause)))
			.finally(() => live && queueMicrotask(() => onready?.()));
		return () => (live = false);
	});
</script>

<div role="region" aria-live="polite" aria-label={t('play.academy.replay.others', { word })} class="flex flex-col gap-1 px-2 pt-1 pb-2">
	<p class="flex items-center gap-2 text-xs font-bold text-(color:--kk-muted)">
		<span class="min-w-0 truncate">{t('play.academy.replay.others', { word })}</span>
		{#if moves}<span class="kk-chip shrink-0">{stateLabel(moves.state)}</span>{/if}
	</p>
	{#if error}
		<p role="alert" class="rounded-lg bg-(--kk-danger-soft) px-2.5 py-1.5 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>
	{:else if !moves}
		<p role="status" class="flex items-center gap-2 py-1 text-sm text-(color:--kk-muted)"><Icon name="progress_activity" size={18} class="animate-spin" />{t('play.profile.loading')}</p>
	{:else if moves.alternatives.length}
		<ul class="grid gap-x-4 xl:grid-cols-2">
			{#each moves.alternatives.slice(0, 12) as move (move.word)}
				<li class="flex min-w-0 items-baseline gap-1.5 border-t border-(color:--kk-line) py-1">
					<b class="min-w-0 truncate text-sm">{move.word}</b>
					<span class="shrink-0 text-xs text-(color:--kk-muted)">→{move.to}</span>
					<span class="ml-auto shrink-0 text-xs text-(color:--kk-muted) tabular-nums">{t('play.academy.replay.defenseState', { n: move.defenseCount, state: stateLabel(move.resultingState) })}</span>
				</li>
			{/each}
		</ul>
	{:else}
		<p class="py-1 text-sm text-(color:--kk-muted)">{t('play.replay.review.noMoves')}</p>
	{/if}
</div>
