<script>
	import { t } from '../i18n.svelte.js';
	import { academyClient } from './academyClient.js';
	import { friendlyError } from './api.js';
	import Seekbar from '../ui/Seekbar.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import Icon from '../lobby/Icon.svelte';

	let { config } = $props();
	let maxPly = $state(7);
	let exclusions = $state('');
	let comparedExclusions = $state('');
	let analysis = $state(null);
	let comparison = $state(null);
	let strategy = $state(null);
	let startChar = $state('');
	let strategyDepth = $state(10);
	let stateTab = $state('WIN');
	let stateQuery = $state('');
	let loading = $state(false);
	let error = $state('');

	const words = (v) => v.split(/[\s,]+/).map((x) => x.trim()).filter(Boolean).slice(0, 1000);
	const label = (s) => (s ? (t(`play.academy.engine.state.${s}`) === `play.academy.engine.state.${s}` ? s : t(`play.academy.engine.state.${s}`)) : '-');
	const rule = (excluded = exclusions) => ({ ...config, excludedWords: words(excluded) });
	const states = $derived(['WIN', 'LOSS', 'ROUTE'].map((v) => ({ value: v, label: label(v) })));
	const rows = $derived(
		analysis
			? Object.values(analysis.states)
					.filter((x) => x.state === stateTab)
					.filter((x) => !stateQuery || x.syllable.includes(stateQuery))
					.sort((a, b) => (a.ply ?? 999) - (b.ply ?? 999) || a.syllable.localeCompare(b.syllable))
					.slice(0, 500)
			: []
	);

	async function guard(fn) {
		loading = true;
		error = '';
		try {
			await fn();
		} catch (cause) {
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
	const runAnalysis = () => guard(async () => ((comparison = null), (analysis = await academyClient.analyze(rule(), { maxPly, routeGroupLimit: 50, criticalWordLimit: 500 }))));
	const runCompare = () => guard(async () => (comparison = await academyClient.compare(rule(), rule(comparedExclusions))));
	async function runStrategy(from = startChar) {
		startChar = from;
		if (startChar.trim().length !== 1) return void (error = t('play.academy.engine.needStart'));
		await guard(async () => (strategy = await academyClient.strategy(rule(), startChar.trim(), words(exclusions), strategyDepth)));
	}
	function downloadJson() {
		if (!analysis) return;
		const link = document.createElement('a');
		link.href = URL.createObjectURL(new Blob([JSON.stringify(analysis, null, 2)], { type: 'application/json' }));
		link.download = `kkutu-academy-analysis-${Date.now()}.json`;
		link.click();
		URL.revokeObjectURL(link.href);
	}
</script>

<div class="grid min-w-0 gap-4 lg:grid-cols-[19rem_minmax(0,1fr)]">
	<aside class="kk-card flex h-fit min-w-0 flex-col gap-4 p-4">
		<div class="flex flex-col"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.engine.depth')}</span><Seekbar value={maxPly} min={1} max={20} step={1} tickStep={5} label={t('play.academy.engine.depth')} format={(v) => String(v)} tickLabel={(v) => v} onchange={(v) => (maxPly = v)} /></div>
		<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.engine.exclude')}</span>
			<textarea bind:value={exclusions} rows="6" placeholder={t('play.academy.engine.excludeHint')} class="kk-input resize-y p-3 font-mono text-sm"></textarea>
			<span class="text-right text-xs text-(color:--kk-muted)">{words(exclusions).length}/1000</span>
		</label>
		<button type="button" onclick={runAnalysis} disabled={loading} class="kk-btn kk-btn-primary inline-flex min-h-12 items-center justify-center gap-1.5"><Icon name={loading ? 'progress_activity' : 'calculate'} size={20} class={loading ? 'animate-spin' : ''} />{t('play.academy.engine.analyzeAll')}</button>
		<div class="flex flex-col gap-2 border-t border-(color:--kk-line) pt-4">
			<h3 class="text-sm font-extrabold">{t('play.academy.engine.compareTitle')}</h3>
			<textarea bind:value={comparedExclusions} rows="4" placeholder={t('play.academy.engine.comparePlaceholder')} aria-label={t('play.academy.engine.compareTitle')} class="kk-input resize-y p-3 font-mono text-sm"></textarea>
			<button type="button" onclick={runCompare} disabled={loading} class="kk-btn inline-flex items-center justify-center gap-1.5 text-sm"><Icon name="difference" size={18} />{t('play.academy.engine.compare')}</button>
		</div>
	</aside>

	<section class="flex min-w-0 flex-col gap-4">
		{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-4 py-3 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>{/if}
		{#if !analysis}
			<div class="kk-card grid min-h-80 place-items-center p-8 text-center">
				<div class="flex flex-col items-center gap-3"><Icon name="schema" size={56} class="text-(color:--kk-muted)" /><h2 class="text-xl font-black">{t('play.academy.engine.graph')}</h2><button type="button" onclick={runAnalysis} class="kk-btn kk-btn-primary px-6">{t('play.academy.engine.startAnalysis')}</button></div>
			</div>
		{:else}
			<div class="grid gap-3 sm:grid-cols-3 xl:grid-cols-5">
				{#each [['words', analysis.corpusSize, ''], ['syllables', analysis.syllableCount, ''], ['win', analysis.counts.WIN ?? 0, 'text-(color:--kk-accent-strong)'], ['loss', analysis.counts.LOSS ?? 0, 'text-(color:--kk-danger)'], ['route', analysis.counts.ROUTE ?? 0, 'text-(color:--kk-blue)']] as [k, n, tone] (k)}
					<div class="kk-card p-3"><span class="text-xs font-bold text-(color:--kk-muted)">{t(`play.academy.engine.stat.${k}`)}</span><strong class="block text-2xl tabular-nums {tone}">{n.toLocaleString()}</strong></div>
				{/each}
			</div>

			<section class="kk-card flex flex-col gap-3 p-4">
				<div class="flex flex-wrap items-center justify-between gap-2"><h3 class="text-lg font-black">{t('play.academy.engine.winWithin', { n: maxPly })}</h3><div class="flex items-center gap-2"><span class="kk-chip">{t('play.academy.engine.count', { n: analysis.winningWithinPly?.[maxPly]?.length ?? 0 })}</span><button type="button" onclick={downloadJson} class="kk-btn text-xs">JSON</button></div></div>
				<div class="flex max-h-44 flex-wrap gap-1.5 overflow-y-auto">{#each analysis.winningWithinPly?.[maxPly] ?? [] as syl (syl)}<button type="button" onclick={() => runStrategy(syl)} class="grid size-10 place-items-center rounded-lg bg-(--kk-accent-soft) font-black text-(color:--kk-accent-strong) hover:brightness-95">{syl}</button>{/each}</div>
			</section>

			<div class="grid min-w-0 gap-4 xl:grid-cols-2">
				<section class="kk-card flex min-w-0 flex-col gap-3 p-4">
					<div class="flex flex-wrap items-center justify-between gap-2"><h3 class="text-lg font-black">{t('play.academy.engine.syllableState')}</h3><input bind:value={stateQuery} maxlength="4" placeholder={t('play.academy.engine.findSyl')} aria-label={t('play.academy.engine.findSyl')} class="h-10 w-28 kk-input px-3 text-sm" /></div>
					<RadioGroup variant="chips" label={t('play.academy.engine.syllableState')} bind:value={stateTab} options={states} />
					<div class="max-h-96 overflow-y-auto rounded-xl border border-(color:--kk-line)">
						{#each rows as row (row.syllable)}
							<button type="button" onclick={() => runStrategy(row.syllable)} class="grid w-full grid-cols-[3rem_minmax(0,1fr)_auto] items-center gap-3 border-b border-(color:--kk-line) px-3 py-2 text-left last:border-0 hover:bg-(--kk-accent-soft)"><strong class="text-xl">{row.syllable}</strong><span class="truncate text-sm text-(color:--kk-muted)">{row.representativeWord || t('play.academy.engine.noRep')}</span><span class="text-xs text-(color:--kk-muted)">{row.ply === null ? '∞' : t('play.academy.engine.plyMoves', { ply: row.ply, n: row.moveCount })}</span></button>
						{/each}
					</div>
				</section>
				<section class="kk-card flex min-w-0 flex-col gap-3 p-4">
					<h3 class="text-lg font-black">{t('play.academy.engine.critical')}</h3>
					<div class="max-h-[26rem] overflow-y-auto">
						{#each analysis.criticalWords as w (w.word)}
							<button type="button" onclick={() => runStrategy(w.from)} class="flex w-full items-center justify-between gap-3 border-b border-(color:--kk-line) py-2 text-left last:border-0 hover:bg-(--kk-accent-soft)"><span class="min-w-0"><strong class="break-all">{w.word}</strong><span class="ml-2 text-xs text-(color:--kk-muted)">{w.from}→{w.to}</span></span><span class="shrink-0 rounded-md bg-(--kk-danger-soft) px-2 py-0.5 text-xs font-bold text-(color:--kk-danger)">{t('play.academy.engine.defense', { n: w.defenseCount })}</span></button>
						{/each}
					</div>
				</section>
			</div>

			<section class="kk-card flex flex-col gap-3 p-4">
				<div class="flex flex-wrap items-end gap-3">
					<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.engine.startSyl')}</span><input bind:value={startChar} maxlength="1" class="h-11 w-24 kk-input px-2 text-center text-xl font-black" /></label>
					<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.engine.strategyDepth')}</span><input type="number" min="1" max="30" bind:value={strategyDepth} class="h-11 w-24 kk-input px-3" /></label>
					<button type="button" onclick={() => runStrategy()} disabled={loading} class="kk-btn kk-btn-primary min-h-11 px-5">{t('play.academy.engine.explore')}</button>
				</div>
				{#if strategy}<div class="flex flex-wrap gap-2">{#each strategy.line as step, i (i)}<div class="rounded-xl bg-(--kk-surface-2) px-3 py-2 text-sm"><span class="text-xs text-(color:--kk-muted)">{t('play.academy.engine.step', { turn: step.turn, from: step.from, to: step.to })}</span><strong class="ml-2">{step.word}</strong><span class="ml-2 text-xs text-(color:--kk-muted)">{t('play.academy.engine.defense', { n: step.defenseCount })}</span></div>{/each}</div>{/if}
			</section>

			{#if analysis.routeGroups?.length}
				<section class="kk-card flex flex-col gap-3 p-4">
					<h3 class="text-lg font-black">{t('play.academy.engine.routes')}</h3>
					<div class="grid gap-3 md:grid-cols-2">
						{#each analysis.routeGroups.slice(0, 20) as group, i (i)}
							<div class="rounded-xl bg-(--kk-surface-2) p-3"><div class="flex flex-wrap gap-1">{#each group.syllables.slice(0, 30) as syl (syl)}<span class="rounded-md bg-(--kk-surface) px-2 py-0.5 text-sm font-black">{syl}</span>{/each}</div><p class="mt-2 text-xs text-(color:--kk-muted)">{t('play.academy.engine.edges', { n: group.edgeCount })} · {group.sampleWords.slice(0, 5).join(', ')}</p></div>
						{/each}
					</div>
				</section>
			{/if}

			{#if comparison}
				<section class="kk-card flex flex-col gap-3 p-4">
					<h3 class="text-lg font-black">{t('play.academy.engine.changed', { n: comparison.changed.length })}</h3>
					<div class="max-h-72 overflow-y-auto">{#each comparison.changed.slice(0, 500) as item (item.syllable)}<div class="grid grid-cols-[3.25rem_1fr] gap-3 border-b border-(color:--kk-line) py-2 text-sm last:border-0"><strong class="text-xl">{item.syllable}</strong><span class="text-(color:--kk-muted)">{label(item.before)} {item.beforePly ?? '∞'} → {label(item.after)} {item.afterPly ?? '∞'}</span></div>{/each}</div>
				</section>
			{/if}
		{/if}
	</section>
</div>
