<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { clock } from '../replayPayload.js';
	import Icon from './Icon.svelte';
	import PlacementMedal from './PlacementMedal.svelte';
	import Tooltip from '../ui/Tooltip.svelte';

	/**
	 * A game from the server's records, as a sheet (the server keeps a summary of every game, not its packets, so there is nothing to
	 * play on the board): the result, and every word in order with its player and time, per round.
	 * @type {{store:any, sheet:any, onclose:()=>void, onplay?:(()=>void)|null, onshare?:(()=>void)|null}}
	 */
	let { store, sheet, onclose, onplay = null, onshare = null } = $props();
	const modeName = $derived(i18n.messages[`game.mode.${sheet.room.modeKey}.name`] ?? sheet.room.modeKey);
	let round = $state(0); // 0 = all
	const shown = $derived(sheet.inputs.filter((i) => !round || i.round === round));
	const nameOf = (i) => sheet.players[i]?.name ?? '?';
	const sec = (ms) => `${(ms / 1000).toFixed(1)}${t('kkutu.js.second')}`;
</script>

<section aria-label={modeName} class="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto">
	<header class="kk-card flex shrink-0 items-center gap-3 px-3 py-2">
		<Tooltip text={t('play.replay.back')}><button type="button" aria-label={t('play.replay.back')} onclick={onclose} class="grid size-9 shrink-0 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)"><Icon name="arrow_back" size={22} /></button></Tooltip>
		<h2 class="min-w-0 flex-1 truncate text-base font-extrabold">{sheet.room.title || modeName}</h2>
		<span class="kk-chip shrink-0">{modeName}</span>
		{#if onshare}<Tooltip text={t('play.replay.share')}><button type="button" aria-label={t('play.replay.share')} onclick={onshare} class="kk-btn grid size-11 shrink-0 place-items-center p-0"><Icon name="link" size={22} /></button></Tooltip>{/if}
		{#if onplay}<button type="button" class="kk-btn kk-btn-primary inline-flex shrink-0 items-center gap-1.5 px-4" onclick={onplay}><Icon name="play_arrow" size={20} fill />{t('kkutu.dialog.replay.viewBtn')}</button>{/if}
		<span class="hidden shrink-0 text-xs text-(color:--kk-muted) sm:inline">{new Date(sheet.start).toLocaleString(i18n.language)} · {clock(sheet.duration)}</span>
	</header>

	<div class="grid min-h-0 gap-3 lg:grid-cols-[minmax(0,20rem)_minmax(0,1fr)]">
		<div class="kk-card flex flex-col gap-1.5 p-3">
			<h3 class="text-sm font-extrabold">{t('kkutu.dialog.replay.recordPlayers')}</h3>
			{#each sheet.results as r (r.player)}
				<div class="flex items-center gap-2.5 rounded-xl bg-(--kk-surface-2) px-2.5 py-1.5">
					<PlacementMedal placement={r.rank} won={r.won} size={34} />
					<span class="min-w-0 flex-1 truncate text-sm font-extrabold">{nameOf(r.player)}</span>
					<span class="shrink-0 text-right text-xs tabular-nums"><b class="text-sm">{r.score.toLocaleString()}</b><br /><span class="text-(color:--kk-muted)">+{r.exp.toLocaleString()} XP</span></span>
				</div>
			{:else}
				{#each sheet.players as p (p.index)}<p class="truncate rounded-xl bg-(--kk-surface-2) px-2.5 py-1.5 text-sm font-bold">{p.name}</p>{/each}
			{/each}
		</div>

		<div class="kk-card flex min-h-0 flex-col gap-2 p-3">
			<div class="flex flex-wrap items-center gap-2">
				<h3 class="mr-auto text-sm font-extrabold">{t('play.academy.replay.timeline')}</h3>
				{#if sheet.rounds.length > 1}
					<div role="group" class="flex flex-wrap gap-1">
						<button type="button" aria-pressed={round === 0} onclick={() => (round = 0)} class="kk-chip {round === 0 ? 'bg-(--kk-fill) text-white' : ''}">{t('item.category.all')}</button>
						{#each sheet.rounds as r (r)}<button type="button" aria-pressed={round === r} onclick={() => (round = r)} class="kk-chip {round === r ? 'bg-(--kk-fill) text-white' : ''}">{r}R</button>{/each}
					</div>
				{/if}
			</div>
			{#if shown.length}
				<ol class="flex min-h-0 flex-col gap-1 overflow-y-auto pr-1">
					{#each shown as i, k (k)}
						<li class="flex items-center gap-2.5 rounded-lg px-2 py-1.5 text-sm {i.ok === false ? 'bg-(--kk-danger)/10' : 'bg-(--kk-surface-2)'}">
							<span class="w-14 shrink-0 text-xs text-(color:--kk-muted) tabular-nums">{clock(i.at)}</span>
							<span class="w-28 shrink-0 truncate text-xs font-bold text-(color:--kk-muted)">{nameOf(i.player)}</span>
							<b class="min-w-0 flex-1 truncate {i.ok === false ? 'line-through' : ''}">{i.word}</b>
							{#if i.turnMs}<span class="shrink-0 text-xs text-(color:--kk-muted) tabular-nums">{sec(i.turnMs)}</span>{/if}
							{#if i.ok && i.score != null}<span class="shrink-0 text-xs font-extrabold text-(color:--kk-accent-strong) tabular-nums">+{i.score}</span>{:else if i.ok === false}<Icon name="block" size={16} class="shrink-0 text-(color:--kk-danger)" />{/if}
						</li>
					{/each}
				</ol>
			{:else}
				<p class="py-6 text-center text-sm text-(color:--kk-muted)">{t('play.academy.replay.noChain')}</p>
			{/if}
		</div>
	</div>
</section>
