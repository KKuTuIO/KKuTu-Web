<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { lookupWord, parseMean, formatMean } from '../dictionary.js';
	import FloatWindow from './FloatWindow.svelte';
	import Icon from './Icon.svelte';
	import EmptyState from './EmptyState.svelte';

	let { open = $bindable(false) } = $props();
	let query = $state('');
	let input = $state();
	let busy = $state(false);
	/** @type {{word:string,mean:string,theme?:string,type?:string}|{error:number}|null} */
	let result = $state(null);

	const themeLabel = (code) => i18n.messages[`word.theme.${code}`];
	const entry = $derived(result && !('error' in result) ? { ...result, parsed: parseMean(result.mean, result.theme, themeLabel) } : null);
	// An entry lists a class per definition ("1,1,1,2"); show each distinct class once.
	const classes = $derived([
		...new Set(
			(entry?.type ?? '')
				.split(',')
				.map((c) => i18n.messages[`word.class.${c}`])
				.filter(Boolean)
		)
	]);
	const badge = {
		topic: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)',
		special: 'bg-(--kk-warn-soft) text-(color:--kk-warn)',
		injeong: 'bg-[color-mix(in_srgb,var(--kk-blue)_14%,var(--kk-surface))] text-(color:--kk-blue)'
	};

	// Fresh each time it opens; the field is ready to type in.
	$effect(() => {
		if (open) {
			query = '';
			result = null;
			queueMicrotask(() => setTimeout(() => input?.focus(), 60));
		}
	});

	async function search(e) {
		e.preventDefault();
		if (busy || !query.trim()) return;
		busy = true;
		result = null;
		try {
			result = await lookupWord(query);
		} finally {
			busy = false;
		}
	}
</script>

{#snippet text(s)}
	{#each formatMean(s) as seg}
		{#if seg.kind === 'sup'}<sup class={seg.equ ? 'font-serif' : ''}>{seg.text}</sup>{:else if seg.kind === 'sub'}<sub class={seg.equ ? 'font-serif' : ''}>{seg.text}</sub>{:else}<span class={seg.equ ? 'font-serif' : ''}>{seg.text}</span>{/if}
	{/each}
{/snippet}

<!-- A floating window, not a modal: the dictionary is meant to stay open next to the game. -->
<FloatWindow bind:open id="dictionary" title={t('play.dict.title')}>
	<div class="flex min-h-[22rem] flex-col gap-4">
		<form onsubmit={search} class="flex items-stretch gap-2">
			<input bind:this={input} bind:value={query} maxlength="200" autocomplete="off" aria-label={t('play.dict.label')} placeholder={t('play.dict.placeholder')} class="h-11 min-w-0 flex-1 kk-input px-3" />
			<button class="kk-btn kk-btn-primary inline-flex items-center gap-1.5 px-4 text-sm" disabled={busy || !query.trim()}><Icon name="search" size={20} />{t('play.dict.search')}</button>
		</form>

		<div class="min-h-0 flex-1" aria-live="polite" aria-busy={busy}>
			{#if busy}
				<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.dict.searching')}</p>
			{:else if entry}
				<article class="flex flex-col gap-3">
					<div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
						<h3 class="text-2xl font-black break-all">{entry.word}</h3>
						{#each classes as c}<span class="kk-chip">{c}</span>{/each}
					</div>
					{#if entry.parsed.plain !== undefined && !entry.parsed.senses.length}
						<p class="leading-relaxed whitespace-pre-line">{@render text(entry.parsed.plain)}</p>
					{:else}
						<ol class="flex flex-col gap-3">
							{#each entry.parsed.senses as sense}
								<li class="flex gap-2">
									{#if sense.n}<span class="mt-0.5 grid size-6 shrink-0 place-items-center rounded-md bg-(--kk-text) text-xs font-black text-(--kk-surface)">{sense.n}</span>{/if}
									<div class="flex min-w-0 flex-col gap-1.5">
										{#each sense.groups as g}
											<div class="flex gap-2">
												{#if g.n}<span class="shrink-0 text-xs font-black text-(color:--kk-muted)">{g.n}.</span>{/if}
												<div class="flex min-w-0 flex-col gap-1">
													{#each g.items as it}
														<p class="leading-relaxed whitespace-pre-line">
															{#if it.n}<span class="mr-1 text-xs font-black text-(color:--kk-muted)">({it.n})</span>{/if}
															{#if it.theme}<span class="mr-1.5 inline-block rounded-md px-1.5 py-0.5 align-middle text-[11px] font-bold {badge[it.theme.kind]}">{it.theme.label}</span>{/if}
															{@render text(it.text)}
														</p>
													{/each}
												</div>
											</div>
										{/each}
									</div>
								</li>
							{/each}
						</ol>
					{/if}
				</article>
			{:else if result && 'error' in result}
				<EmptyState title={i18n.messages[`kkutu.js.wpFail.${result.error}`] ?? t('play.error.request', { code: result.error })} />
			{:else}
				<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.dict.hint')}</p>
			{/if}
		</div>
	</div>
</FloatWindow>
