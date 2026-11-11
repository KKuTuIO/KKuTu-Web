<script>
	/**
	 * A row of small filter chips (a radio group): the chosen one is filled, each can carry a count. `scroll` keeps one line and
	 * scrolls sideways instead of wrapping (for sub-categories).
	 * @type {{options:{value:any,label:string,count?:number}[], value:any, label?:string, size?:'md'|'sm', scroll?:boolean, onchange?:(v:any)=>void}}
	 */
	let { options = [], value = $bindable(), label = '', size = 'md', scroll = false, onchange } = $props();
	let els = $state([]);
	const pick = (v) => {
		if (v === value) return;
		value = v;
		onchange?.(v);
	};
	function nav(e, i) {
		const to = { ArrowRight: i + 1, ArrowDown: i + 1, ArrowLeft: i - 1, ArrowUp: i - 1, Home: 0, End: options.length - 1 }[e.key];
		if (to === undefined) return;
		e.preventDefault();
		const j = (to + options.length) % options.length;
		pick(options[j].value);
		els[j]?.focus();
	}
</script>

<div role="radiogroup" aria-label={label} class="flex gap-1.5 {scroll ? 'overflow-x-auto pb-1 [scrollbar-width:thin]' : 'flex-wrap'}">
	{#each options as o, i (o.value)}
		{@const on = o.value === value}
		<button
			bind:this={els[i]}
			type="button"
			role="radio"
			aria-checked={on}
			tabindex={on || (!options.some((x) => x.value === value) && i === 0) ? 0 : -1}
			onclick={() => pick(o.value)}
			onkeydown={(e) => nav(e, i)}
			class="inline-flex shrink-0 items-center gap-1 rounded-full border-[1.5px] font-bold whitespace-nowrap {size === 'sm' ? 'h-7 px-2.5 text-xs' : 'h-8 px-3 text-sm'} {on ? 'border-transparent bg-(--kk-fill) text-white shadow-[0_2px_0_var(--kk-fill-edge)]' : 'border-(color:--kk-line) bg-(--kk-surface) text-(color:--kk-muted)'}"
		>
			{o.label}{#if o.count !== undefined}<span class="tabular-nums opacity-70">{o.count}</span>{/if}
		</button>
	{/each}
</div>
