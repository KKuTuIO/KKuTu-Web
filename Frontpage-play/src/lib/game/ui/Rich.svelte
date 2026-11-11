<script>
	/**
	 * Renders the small HTML subset used by locale descriptions (<br>, <u>, <b>, <i>) without {@html}:
	 * anything else is dropped, so a message can never inject markup.
	 */
	let { html = '' } = $props();
	const parts = $derived.by(() => {
		const out = [];
		const flags = { u: false, b: false, i: false };
		for (const tok of String(html).split(/(<[^>]*>)/)) {
			const tag = tok.match(/^<\s*(\/?)\s*([a-zA-Z]+)[^>]*>$/);
			if (!tag) {
				if (tok) out.push({ text: tok, ...flags });
			} else if (tag[2].toLowerCase() === 'br') out.push({ br: true });
			else if (tag[2].toLowerCase() in flags) flags[tag[2].toLowerCase()] = !tag[1];
		}
		return out;
	});
</script>

{#each parts as p}{#if p.br}<br />{:else}<span class="{p.u ? 'underline decoration-2 underline-offset-2' : ''} {p.b ? 'font-extrabold' : ''} {p.i ? 'italic' : ''}">{p.text}</span>{/if}{/each}
