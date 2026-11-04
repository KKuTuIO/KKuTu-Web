<script>
	import { onMount } from 'svelte';
	import { scale } from 'svelte/transition';
	import { backOut } from 'svelte/easing';
	import { ms } from '../motion.js';
	import { t } from '../i18n.svelte.js';
	import { academyClient } from './academyClient.js';
	import { friendlyError } from './api.js';
	import Select from '../ui/Select.svelte';
	import Switch from '../ui/Switch.svelte';
	import Icon from '../lobby/Icon.svelte';
	import EmptyState from '../lobby/EmptyState.svelte';
	import { toast } from '../ui/toast.svelte.js';

	let { config } = $props();
	let chain = $state([]);
	let input = $state('');
	let shields = $state(1);
	let botLevel = $state('EXPERT');
	let autoBot = $state(true);
	let specialRule = $state('MANNER');
	let loading = $state(false);
	let error = $state('');
	let result = $state(null);
	let suggestion = $state(null);

	const chainRequired = $derived(chain.length ? (config.direction === 'REVERSE' ? chain.at(-1)?.[0] : chain.at(-1)?.at(-1)) || '' : '');
	const required = $derived(result?.nextChar || chainRequired);
	const specials = $derived(['NONE', 'MANNER', 'SAFE', 'GENTLE'].map((v) => ({ value: v, label: t(`play.academy.sim.special.${v}`) })));
	const bots = $derived(['RANDOM', 'BALANCED', 'EXPERT'].map((v) => ({ value: v, label: t(`play.academy.sim.bot.${v}`) })));
	const stateName = (s) => (s ? (t(`play.academy.sim.state.${s}`) === `play.academy.sim.state.${s}` ? s : t(`play.academy.sim.state.${s}`)) : '-');

	// A shared link can carry a start, a chain, the dictionary and the special rule.
	onMount(() => {
		const p = new URLSearchParams(window.location.search);
		if (p.get('dictionary')) config.dictionary = p.get('dictionary');
		if (['NONE', 'MANNER', 'SAFE', 'GENTLE'].includes(p.get('special'))) specialRule = p.get('special');
		if (p.get('start')) input = p.get('start');
		if (p.get('chain')) chain = p.get('chain').split(',').map((x) => x.trim()).filter(Boolean).slice(0, 100);
	});

	async function submit(e) {
		e?.preventDefault();
		if (!input.trim()) return;
		loading = true;
		error = '';
		suggestion = null;
		try {
			const r = await academyClient.simulate(config, chain, input.trim(), shields, autoBot ? botLevel : null, specialRule);
			result = r;
			if (r.shieldUsed) shields = Math.max(0, shields - 1);
			if (r.accepted) {
				chain = r.chain;
				input = '';
				if (autoBot && r.botMove) {
					chain = [...chain, r.botMove.word];
					result = { ...r, nextChar: r.botMove.to, analysis: r.botMove };
				}
			}
		} catch (cause) {
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
	async function expertPick() {
		const target = required || input.trim().slice(0, 1);
		if (!target) return void (error = t('play.academy.sim.needStart'));
		loading = true;
		error = '';
		try {
			suggestion = (await academyClient.strategy(config, target, chain, 8)).alternatives?.[0] ?? null;
			if (!suggestion) error = t('play.academy.sim.noSuggestion');
		} catch (cause) {
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
	function undo() {
		if (!chain.length) return;
		chain = chain.slice(0, -1);
		result = suggestion = null;
	}
	function reset() {
		chain = [];
		input = '';
		result = suggestion = null;
		shields = 1;
	}
	async function copy(text, message) {
		try {
			await navigator.clipboard.writeText(text);
			toast(message, { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}
	function share() {
		const url = new URL(window.location.href);
		url.searchParams.set('view', 'academy');
		url.searchParams.set('tab', 'simulator');
		url.searchParams.set('dictionary', config.dictionary);
		url.searchParams.set('special', specialRule);
		if (chain[0]) url.searchParams.set('start', chain[0]);
		if (chain.length) url.searchParams.set('chain', chain.join(','));
		copy(url.toString(), t('play.academy.sim.shareCopied'));
	}
</script>

<div class="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_17rem]">
	<section class="kk-card flex min-w-0 flex-col gap-4 p-4 md:p-5">
		<div class="flex items-center justify-between gap-3">
			<h2 class="text-xl font-black">{t('play.academy.tab.simulator')}</h2>
			<div class="flex gap-2">
				<button type="button" onclick={undo} disabled={!chain.length} class="kk-btn inline-flex items-center gap-1 text-sm"><Icon name="undo" size={18} />{t('play.academy.sim.undo')}</button>
				<button type="button" onclick={reset} aria-label={t('play.academy.sim.reset')} class="kk-btn grid size-10 place-items-center p-0"><Icon name="restart_alt" size={20} /></button>
			</div>
		</div>

		<div class="min-h-40 rounded-2xl bg-(--kk-surface-2) p-3">
			{#if !chain.length}
				<EmptyState compact size={110} title={t('play.academy.sim.empty')} />
			{:else}
				<ol class="flex flex-wrap items-center gap-2">
					{#each chain as word, i (i)}
						<li class="flex items-center gap-2" in:scale|global={{ start: 0.6, duration: ms(260), easing: backOut }}>
							<span class="max-w-full rounded-xl border-[1.5px] px-3 py-2 {autoBot && i % 2 === 1 ? 'border-(color:--kk-blue) bg-[color-mix(in_srgb,var(--kk-blue)_10%,var(--kk-surface))]' : 'border-(color:--kk-accent) bg-(--kk-surface)'}">
								<span class="block text-[10px] font-extrabold text-(color:--kk-muted)">{autoBot && i % 2 === 1 ? t('play.academy.sim.bot') : t('play.academy.sim.ply', { n: i + 1 })}</span>
								<strong class="break-all text-lg">{word}</strong>
							</span>
							{#if i < chain.length - 1}<Icon name="arrow_forward" size={18} class="text-(color:--kk-muted)" />{/if}
						</li>
					{/each}
				</ol>
			{/if}
		</div>

		<form onsubmit={submit} class="flex flex-col gap-2 sm:flex-row">
			<div class="relative min-w-0 flex-1">
				{#if required}<span class="absolute top-1/2 left-3 -translate-y-1/2 rounded-md bg-(--kk-accent-soft) px-2 py-0.5 text-sm font-black text-(color:--kk-accent-strong)">{required}</span>{/if}
				<input bind:value={input} autocomplete="off" aria-label={t('play.academy.sim.enter')} placeholder={required ? t('play.academy.sim.continues', { c: required }) : t('play.academy.sim.empty')} class="h-13 w-full kk-input pr-3 text-lg font-black {required ? 'pl-14' : 'pl-3'}" />
			</div>
			<button class="kk-btn kk-btn-primary inline-flex min-h-13 items-center justify-center gap-1.5 px-5" disabled={loading || !input.trim()}><Icon name={loading ? 'progress_activity' : 'play_arrow'} size={20} class={loading ? 'animate-spin' : ''} />{t('play.academy.sim.enter')}</button>
			<button type="button" onclick={expertPick} disabled={loading} class="kk-btn inline-flex min-h-13 items-center justify-center gap-1.5 px-5"><Icon name="psychology" size={20} />{t('play.academy.sim.expert')}</button>
		</form>

		{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-4 py-3 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>{/if}
		{#if result}
			<div in:scale|global={{ start: 0.96, duration: ms(180) }} class="rounded-xl border-[1.5px] p-3 {result.accepted ? 'border-(color:--kk-accent) bg-(--kk-accent-soft)' : 'border-(color:--kk-warn) bg-(--kk-warn-soft)'}">
				<p class="flex items-center gap-2 font-extrabold"><Icon name={result.accepted ? 'check_circle' : result.shieldUsed ? 'shield' : 'error'} size={20} fill />{result.message}</p>
				{#if result.analysis}<p class="mt-1 text-sm text-(color:--kk-muted)">{t('play.academy.sim.analysis', { to: result.analysis.to, n: result.analysis.defenseCount, state: stateName(result.analysis.resultingState) })}</p>{/if}
			</div>
		{/if}
		{#if suggestion}
			<button type="button" onclick={() => (input = suggestion.word)} class="rounded-xl border-[1.5px] border-(color:--kk-blue) bg-[color-mix(in_srgb,var(--kk-blue)_10%,var(--kk-surface))] p-3 text-left">
				<span class="text-xs font-extrabold text-(color:--kk-blue)">{t('play.academy.sim.recommend')}</span>
				<span class="flex flex-wrap items-end justify-between gap-2"><strong class="text-xl break-all">{suggestion.word}</strong><span class="text-sm text-(color:--kk-muted)">{t('play.academy.sim.defenseLine', { to: suggestion.to, n: suggestion.defenseCount })}</span></span>
			</button>
		{/if}
		{#if result?.alternatives?.length}
			<div class="flex flex-col gap-1.5"><h3 class="text-sm font-extrabold">{t('play.academy.sim.others')}</h3>
				<div class="flex flex-wrap gap-2">{#each result.alternatives as move (move.word)}<button type="button" onclick={() => (input = move.word)} class="kk-btn text-left text-sm"><strong class="break-all">{move.word}</strong><span class="ml-1 text-xs text-(color:--kk-muted)">→ {move.to} ({move.defenseCount})</span></button>{/each}</div>
			</div>
		{/if}
	</section>

	<aside class="flex h-fit min-w-0 flex-col gap-4">
		<section class="kk-card flex flex-col gap-3 p-4">
			<h3 class="font-extrabold">{t('play.academy.sim.settings')}</h3>
			<div class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.sim.special')}</span><Select label={t('play.academy.sim.special')} bind:value={specialRule} options={specials} /></div>
			<Switch bind:checked={autoBot}>{t('play.academy.sim.autoBot')}</Switch>
			<div class="flex flex-col gap-1 {autoBot ? '' : 'opacity-50'}"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.sim.botLevel')}</span><Select label={t('play.academy.sim.botLevel')} bind:value={botLevel} options={bots} /></div>
			<p class="flex items-center justify-between rounded-xl bg-(--kk-surface-2) px-3 py-2.5 text-sm font-bold"><span class="flex items-center gap-1.5"><Icon name="shield" size={18} />{t('play.academy.sim.shield')}</span><strong class="text-lg">{shields}</strong></p>
		</section>
		<section class="kk-card flex flex-col gap-3 p-4">
			<h3 class="font-extrabold">{t('play.academy.sim.tools')}</h3>
			<div class="grid grid-cols-2 gap-2">
				<button type="button" class="kk-btn inline-flex items-center justify-center gap-1 text-sm" disabled={!chain.length} onclick={() => copy(chain.join(' → '), t('play.academy.copied'))}><Icon name="content_copy" size={18} />{t('play.academy.sim.copy')}</button>
				<button type="button" class="kk-btn inline-flex items-center justify-center gap-1 text-sm" onclick={share}><Icon name="share" size={18} />{t('play.academy.sim.share')}</button>
			</div>
			<dl class="grid grid-cols-2 gap-2 text-center">
				<div class="rounded-xl bg-(--kk-surface-2) p-2"><dt class="text-xs text-(color:--kk-muted)">{t('play.academy.sim.length')}</dt><dd class="text-lg font-black">{chain.length}</dd></div>
				<div class="rounded-xl bg-(--kk-surface-2) p-2"><dt class="text-xs text-(color:--kk-muted)">{t('play.academy.sim.current')}</dt><dd class="text-lg font-black">{required || '-'}</dd></div>
			</dl>
		</section>
	</aside>
</div>
