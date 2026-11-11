<script>
	import { parseMarkdown } from '../markdown.js';

	/**
	 * The safe Markdown of the 소개, drawn node by node (no {@html}). A link asks `onlink(href)` first (the page shows the
	 * "really leave?" question); without a handler it is plain text.
	 * `mask` (the profanity filter) is applied to the words only: never to an address, so a filtered word cannot break a link.
	 * @type {{source:string, onlink?:(href:string)=>void, mask?:(s:string)=>string}}
	 */
	let { source = '', onlink, mask = (s) => s } = $props();
	const bare = (n) => n.c.length === 1 && n.c[0].t === 'text' && /^https?:\/\//i.test(n.c[0].v); // a link shown as its own address
	const blocks = $derived(parseMarkdown(source));
	const HEAD = ['text-lg', 'text-base', 'text-[15px]', 'text-sm', 'text-sm', 'text-sm'];
</script>

{#snippet inline(nodes, raw = false)}
	{#each nodes as n}
		{#if n.t === 'text'}{raw ? n.v : mask(n.v)}
		{:else if n.t === 'br'}<br />
		{:else if n.t === 'strong'}<strong class="font-extrabold">{@render inline(n.c, raw)}</strong>
		{:else if n.t === 'em'}<em>{@render inline(n.c, raw)}</em>
		{:else if n.t === 'del'}<del class="opacity-70">{@render inline(n.c, raw)}</del>
		{:else if n.t === 'code'}<code class="rounded bg-(--kk-surface-2) px-1 py-px font-mono text-[0.9em]">{raw ? n.v : mask(n.v)}</code>
		{:else if n.t === 'link'}
			{#if onlink}
				<a href={n.href} target="_blank" rel="noopener noreferrer nofollow ugc" onclick={(e) => (e.preventDefault(), onlink(n.href))} class="break-all font-bold text-(color:--kk-accent-strong) underline decoration-1 underline-offset-2 hover:decoration-2">{@render inline(n.c, bare(n))}</a>
			{:else}{@render inline(n.c, bare(n))}{/if}
		{/if}
	{/each}
{/snippet}

{#snippet render(list)}
	{#each list as b}
		{#if b.t === 'p'}<p>{@render inline(b.c)}</p>
		{:else if b.t === 'h'}<p class="{HEAD[b.l - 1]} font-black">{@render inline(b.c)}</p>
		{:else if b.t === 'ul'}<ul class="list-disc pl-5">{#each b.items as it}<li>{@render inline(it)}</li>{/each}</ul>
		{:else if b.t === 'ol'}<ol class="list-decimal pl-5">{#each b.items as it}<li>{@render inline(it)}</li>{/each}</ol>
		{:else if b.t === 'quote'}<blockquote class="flex flex-col gap-1.5 border-l-[3px] border-(color:--kk-line-strong) pl-3 text-(color:--kk-muted)">{@render render(b.c)}</blockquote>
		{:else if b.t === 'pre'}<pre class="overflow-x-auto rounded-lg bg-(--kk-surface-2) px-3 py-2 font-mono text-xs leading-snug">{mask(b.v)}</pre>
		{:else if b.t === 'hr'}<hr class="border-(color:--kk-line)" />{/if}
	{/each}
{/snippet}

<div class="flex flex-col gap-1.5 break-words">{@render render(blocks)}</div>
