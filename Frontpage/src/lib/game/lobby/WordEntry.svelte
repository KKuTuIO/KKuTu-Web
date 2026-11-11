<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { parseMean, formatMean } from '../dictionary.js';
	import EmptyState from './EmptyState.svelte';

	/**
	 * What the dictionary says about a word: the word, its classes and its numbered meanings.
	 * @type {{result: {word:string,mean:string,theme?:string,type?:string}|{error:number}|null}}
	 */
	let { result } = $props();

	const themeLabel = (code) => i18n.messages[`word.theme.${code}`];
	const entry = $derived(result && !('error' in result) ? { ...result, parsed: parseMean(result.mean, result.theme, themeLabel) } : null);
	// An entry lists a class per definition ("1,1,1,2"); show each distinct class once.
	const classes = $derived([...new Set((entry?.type ?? '').split(',').map((c) => i18n.messages[`word.class.${c}`]).filter(Boolean))]);
	const badge = {
		topic: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)',
		special: 'bg-(--kk-warn-soft) text-(color:--kk-warn)',
		injeong: 'bg-[color-mix(in_srgb,var(--kk-blue)_14%,var(--kk-surface))] text-(color:--kk-blue)'
	};
</script>

{#snippet text(s)}
	{#each formatMean(s) as seg}
		{#if seg.kind === 'sup'}<sup class={seg.equ ? 'font-serif' : ''}>{seg.text}</sup>{:else if seg.kind === 'sub'}<sub class={seg.equ ? 'font-serif' : ''}>{seg.text}</sub>{:else}<span class={seg.equ ? 'font-serif' : ''}>{seg.text}</span>{/if}
	{/each}
{/snippet}

{#if entry}
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
{/if}
