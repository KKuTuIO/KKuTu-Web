<script>
	import Tooltip from './Tooltip.svelte';
	/**
	 * Custom radio group (WAI-ARIA radiogroup): one tab stop, arrow keys move and select.
	 * variant "list" = dot + label rows, "chips" = segmented pills.
	 * Options may carry a `tip` (shown as a tooltip).
	 * @type {{options:{value:any,label:string,tip?:string}[], value:any, label?:string, variant?:'list'|'chips', onchange?:(v:any)=>void}}
	 */
	let { options = [], value = $bindable(), label = '', variant = 'list', onchange } = $props();
	let els = $state([]);
	const select = (v) => {
		if (v === value) return;
		value = v;
		onchange?.(v);
	};
	function nav(e, i) {
		const n = options.length;
		const to = { ArrowRight: i + 1, ArrowDown: i + 1, ArrowLeft: i - 1, ArrowUp: i - 1, Home: 0, End: n - 1 }[e.key];
		if (to === undefined) return;
		e.preventDefault();
		const j = (to + n) % n;
		select(options[j].value);
		els[j]?.focus();
	}
	const focusable = (o, i) => (options.some((x) => x.value === value) ? o.value === value : i === 0);
</script>

<div role="radiogroup" aria-label={label} class={variant === 'chips' ? 'flex flex-wrap gap-1.5' : 'flex flex-col'}>
	{#each options as o, i (o.value)}
		{@const on = o.value === value}
		<Tooltip text={o.tip ?? ''}>
		<button
			bind:this={els[i]}
			type="button"
			role="radio"
			aria-checked={on}
			tabindex={focusable(o, i) ? 0 : -1}
			onclick={() => select(o.value)}
			onkeydown={(e) => nav(e, i)}
			class="group focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-(--kk-accent) {variant === 'chips'
				? `min-h-10 rounded-xl px-3.5 text-sm font-bold transition-colors ${on ? 'bg-(--kk-fill) text-white' : 'bg-(--kk-surface-2) text-(color:--kk-muted) hover:bg-(--kk-accent-soft)'}`
				: 'inline-flex min-h-10 items-center gap-2.5 rounded-lg text-left'}"
		>
			{#if variant === 'list'}
				<span class="grid size-[22px] shrink-0 place-items-center rounded-full border-2 transition-colors {on ? 'border-(color:--kk-fill)' : 'border-(color:--kk-line-strong) group-hover:border-(color:--kk-accent)'}">
					<span class="size-2.5 rounded-full bg-(--kk-fill) transition-transform {on ? 'scale-100' : 'scale-0'}"></span>
				</span>
			{/if}
			{o.label}
		</button>
		</Tooltip>
	{/each}
</div>
