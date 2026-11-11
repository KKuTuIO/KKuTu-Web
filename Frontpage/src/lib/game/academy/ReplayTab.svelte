<script>
	import { onMount, untrack } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { academyClient } from './academyClient.js';
	import { academyApi, friendlyError } from './api.js';
	import Icon from '../lobby/Icon.svelte';
	import EmptyState from '../lobby/EmptyState.svelte';
	import { toast } from '../ui/toast.svelte.js';

	let { config = $bindable(), initialGame = '' } = $props();
	let gameId = $state(untrack(() => initialGame));
	let replay = $state(null);
	let loading = $state(false);
	let error = $state('');
	let query = $state('');
	let selectedWord = $state('');
	let alternatives = $state(null);

	const decodeWords = (indices, dict) => (Array.isArray(indices) ? indices.map((i) => dict[i]).filter(Boolean) : []);
	const playerName = (payload, i) => payload?.p?.[i]?.[1] || payload?.p?.[i]?.[0];
	function decodeChains(payload, dict) {
		if (!Array.isArray(payload?.ap)) return [];
		return payload.ap
			.filter((row) => Array.isArray(row) && Array.isArray(row[1]))
			.map(([i, indices]) => ({ player: playerName(payload, i) || t('play.academy.replay.player', { n: i + 1 }), words: decodeWords(indices, dict) }))
			.filter((e) => e.words.length);
	}
	const stateLabel = (s) => (s ? (t(`play.academy.replay.state.${s}`) === `play.academy.replay.state.${s}` ? s : t(`play.academy.replay.state.${s}`)) : '-');

	const payload = $derived(replay?.game?.detailPayload ?? null);
	const dict = $derived(Array.isArray(payload?.w) ? payload.w : []);
	const chains = $derived(decodeChains(payload, dict));
	const globalAccepted = $derived(decodeWords(payload?.a, dict));
	const accepted = $derived(globalAccepted.length ? globalAccepted : chains.flatMap((e) => e.words));
	const unique = $derived([...new Set(accepted)]);
	const shown = $derived(unique.filter((w) => !query || w.includes(query)));
	const longest = $derived(unique.reduce((best, w) => (w.length > (best?.length ?? 0) ? w : best), ''));
	const inputs = $derived(
		Array.isArray(payload?.i)
			? payload.i.map((r) => ({ player: playerName(payload, r[0]) || t('play.academy.replay.unknown'), word: dict[r[1]] ?? '', ms: r[2] || 0, round: r[4] || 0, turn: r[5] || 0, extra: payload?.x?.[r[6]] ?? '' }))
			: []
	);
	const duration = (ms) => (Number.isFinite(ms) ? t('play.academy.replay.durVal', { m: Math.floor(ms / 60000), s: Math.floor(ms / 1000) % 60 }) : '-');

	onMount(() => {
		if (gameId) load();
	});

	async function load(e) {
		e?.preventDefault();
		if (!gameId.trim()) return;
		loading = true;
		error = '';
		replay = alternatives = null;
		try {
			const r = await academyApi.replay(gameId.trim());
			if (!r?.ok || !r?.game) throw new Error(r?.error || t('play.academy.replay.notFound'));
			replay = r;
			config.lang = r.game.lang || config.lang;
			const url = new URL(window.location.href);
			url.searchParams.set('view', 'academy');
			url.searchParams.set('tab', 'replay');
			url.searchParams.set('game', gameId.trim());
			history.replaceState(null, '', url);
		} catch (cause) {
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
	async function inspect(word) {
		selectedWord = word;
		alternatives = null;
		const need = config.direction === 'REVERSE' ? word[0] : word.at(-1);
		if (!need) return;
		loading = true;
		try {
			alternatives = await academyClient.strategy(config, need, unique, 6);
		} catch (cause) {
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
	async function copyChain() {
		try {
			await navigator.clipboard.writeText(accepted.join(' → '));
			toast(t('play.academy.copied'), { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}
</script>

<div class="grid min-w-0 gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
	<aside class="kk-card flex h-fit min-w-0 flex-col gap-3 p-4">
		<h2 class="flex items-center gap-2 font-extrabold"><Icon name="movie_edit" size={22} fill />{t('play.academy.replay.title')}</h2>
		<form onsubmit={load} class="flex flex-col gap-2">
			<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.replay.gameId')}</span><input bind:value={gameId} placeholder={t('play.academy.replay.placeholder')} class="h-11 kk-input px-3 font-mono text-sm" /></label>
			<button class="kk-btn kk-btn-primary inline-flex min-h-11 items-center justify-center gap-1.5" disabled={loading || !gameId.trim()}><Icon name={loading ? 'progress_activity' : 'search'} size={20} class={loading ? 'animate-spin' : ''} />{t('play.academy.replay.load')}</button>
		</form>
		{#if replay?.game}
			<dl class="grid grid-cols-2 gap-2 text-center">
				{#each [[t('play.academy.replay.mode'), replay.game.modeName], [t('play.academy.replay.players'), t('play.academy.replay.playersVal', { n: replay.game.playerCount })], [t('play.academy.replay.duration'), duration(replay.game.durationMs)], [t('play.academy.replay.accepted'), t('play.academy.replay.acceptedVal', { n: unique.length })]] as [k, v] (k)}
					<div class="min-w-0 rounded-xl bg-(--kk-surface-2) p-2"><dt class="text-xs text-(color:--kk-muted)">{k}</dt><dd class="truncate text-sm font-black">{v}</dd></div>
				{/each}
			</dl>
		{/if}
	</aside>

	<section class="flex min-w-0 flex-col gap-4">
		{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-4 py-3 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>{/if}
		{#if !replay}
			<div class="kk-card grid min-h-72 place-items-center p-8"><EmptyState compact size={130} title={t('play.academy.replay.enter')} /></div>
		{:else}
			<div class="grid gap-3 sm:grid-cols-3">
				{#each [[t('play.academy.replay.longest'), longest || '-', 'text-lg break-all'], [t('play.academy.replay.uniqueInput'), new Set(dict).size, 'text-2xl'], [t('play.academy.replay.inputEvents'), inputs.length, 'text-2xl']] as [k, v, cls] (k)}
					<div class="kk-card min-w-0 p-3"><span class="text-xs font-bold text-(color:--kk-muted)">{k}</span><strong class="mt-1 block {cls}">{v}</strong></div>
				{/each}
			</div>

			<section class="kk-card flex flex-col gap-3 p-4">
				<div class="flex flex-wrap items-center justify-between gap-2">
					<h3 class="text-lg font-black">{t(globalAccepted.length ? 'play.academy.replay.chain' : 'play.academy.replay.allAccepted')}</h3>
					<div class="flex gap-2"><input bind:value={query} placeholder={t('play.academy.replay.filter')} aria-label={t('play.academy.replay.filter')} class="h-10 w-36 kk-input px-3 text-sm" /><button type="button" onclick={copyChain} aria-label={t('play.academy.replay.copyChain')} class="kk-btn grid size-10 place-items-center p-0"><Icon name="content_copy" size={18} /></button></div>
				</div>
				{#if unique.length}
					<div class="flex max-h-80 flex-wrap gap-2 overflow-y-auto">{#each shown as word, i (word + i)}<button type="button" onclick={() => inspect(word)} class="rounded-xl border-[1.5px] px-3 py-1.5 text-left {selectedWord === word ? 'border-(color:--kk-accent) bg-(--kk-accent-soft)' : 'border-(color:--kk-line) bg-(--kk-surface-2) hover:border-(color:--kk-accent)'}"><span class="block text-[10px] font-bold text-(color:--kk-muted)">{i + 1}</span><strong class="break-all">{word}</strong></button>{/each}</div>
				{:else}<p class="text-sm text-(color:--kk-muted)">{t('play.academy.replay.noChain')}</p>{/if}
			</section>

			{#if chains.length}
				<section class="kk-card flex flex-col gap-3 p-4">
					<h3 class="text-lg font-black">{t('play.academy.replay.perPlayer')}</h3>
					<div class="grid min-w-0 gap-3 lg:grid-cols-2">
						{#each chains as entry, i (i)}
							<article class="min-w-0 rounded-xl bg-(--kk-surface-2) p-3">
								<div class="flex items-center justify-between gap-2"><strong class="min-w-0 truncate">{entry.player}</strong><span class="kk-chip shrink-0">{t('play.academy.replay.acceptedVal', { n: entry.words.length })}</span></div>
								<div class="mt-2 flex max-h-40 flex-wrap gap-1.5 overflow-y-auto">{#each entry.words as word, j (j)}<button type="button" onclick={() => inspect(word)} class="rounded-lg bg-(--kk-surface) px-2.5 py-1 text-sm font-bold break-all hover:text-(color:--kk-accent-strong)">{word}</button>{/each}</div>
							</article>
						{/each}
					</div>
				</section>
			{/if}

			{#if alternatives}
				<section class="kk-card flex flex-col gap-3 border-(color:--kk-blue) p-4">
					<div class="flex flex-wrap items-center justify-between gap-2"><h3 class="text-lg font-black">{t('play.academy.replay.others', { word: selectedWord })}</h3><span class="kk-chip">{stateLabel(alternatives.state)}</span></div>
					<div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{#each alternatives.alternatives.slice(0, 12) as move (move.word)}<div class="min-w-0 rounded-xl bg-(--kk-surface-2) p-3"><strong class="break-all">{move.word}</strong><span class="ml-2 text-xs text-(color:--kk-muted)">→{move.to}</span><p class="mt-1 text-xs text-(color:--kk-muted)">{t('play.academy.replay.defenseState', { n: move.defenseCount, state: stateLabel(move.resultingState) })}</p></div>{/each}</div>
				</section>
			{/if}

			<section class="kk-card flex flex-col gap-2 p-4">
				<h3 class="text-lg font-black">{t('play.academy.replay.timeline')}</h3>
				<div class="max-h-[30rem] overflow-y-auto">
					{#each inputs as row, i (i)}
						{@const fail = String(row.extra).startsWith('CR')}
						<div class="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 border-b border-(color:--kk-line) py-2 last:border-0">
							<span class="kk-chip">{row.round}R · {row.turn}T</span>
							<div class="min-w-0"><strong class="block truncate">{row.word}</strong><span class="text-xs text-(color:--kk-muted)">{row.player}</span></div>
							<div class="shrink-0 text-right"><strong class="text-sm">{t('play.academy.replay.seconds', { n: (row.ms / 1000).toFixed(2) })}</strong><span class="ml-2 rounded-md px-1.5 py-0.5 text-[10px] font-extrabold {fail ? 'bg-(--kk-danger-soft) text-(color:--kk-danger)' : 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)'}">{t(fail ? 'play.academy.replay.fail' : 'play.academy.replay.ok')}</span></div>
						</div>
					{/each}
				</div>
			</section>
		{/if}
	</section>
</div>
