<script>
	import Tooltip from './Tooltip.svelte';
	import Icon from '../lobby/Icon.svelte';
	/**
	 * Custom radio group (WAI-ARIA radiogroup): one tab stop, arrow keys move and select.
	 * variant "list" = dot + label rows, "chips" = segmented pills, "cards" = a row of icon + label cards (options carry an `icon`).
	 * Options may carry a `tip` (tooltip) and, for chips, a `color` used as the selected background.
	 * `vertical` stacks the chips. Disabled chips can't be picked.
	 * @type {{options:{value:any,label:string,tip?:string,color?:string,icon?:string}[], vertical?:boolean, disabled?:boolean, value:any, label?:string, variant?:'list'|'chips'|'cards', onchange?:(v:any)=>void}}
	 */
	let { options = [], value = $bindable(), label = '', variant = 'list', vertical = false, disabled = false, onchange } = $props();
	let els = $state([]);
	const select = (v) => {
		if (disabled || v === value) return;
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

<div role="radiogroup" aria-label={label} aria-disabled={disabled || undefined} class={variant === 'cards' ? 'grid grid-cols-4 gap-1.5 rounded-xl bg-(--kk-surface-2) p-1.5' : variant === 'chips' && !vertical ? 'flex flex-wrap gap-1.5' : 'flex flex-col gap-1.5'}>
	{#each options as o, i (o.value)}
		{@const on = o.value === value}
		<Tooltip text={o.tip ?? ''}>
		<button
			bind:this={els[i]}
			type="button"
			role="radio"
			aria-checked={on}
			tabindex={focusable(o, i) ? 0 : -1}
			style:background-color={on && o.color ? o.color : undefined}
			onclick={() => select(o.value)}
			onkeydown={(e) => nav(e, i)}
			class="group focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-(--kk-accent) {variant === 'cards'
				? `flex min-h-20 w-full flex-col items-center justify-center gap-1.5 rounded-lg border px-1 text-sm font-bold transition-[background-color,color,box-shadow] ${on ? 'border-(color:--kk-line-strong) bg-(--kk-surface) text-(color:--kk-text) shadow-[0_2px_0_var(--kk-line-strong)]' : 'border-transparent text-(color:--kk-muted) hover:text-(color:--kk-text)'}`
				: variant === 'chips'
				? `min-h-10 rounded-xl px-3.5 text-sm font-bold transition-colors ${on ? (o.color ? 'font-extrabold text-[#111]' : 'bg-(--kk-fill) text-white') : disabled ? 'bg-(--kk-surface-2) text-(color:--kk-muted) opacity-60' : 'bg-(--kk-surface-2) text-(color:--kk-muted) hover:bg-(--kk-accent-soft)'}`
				: 'inline-flex min-h-10 items-center gap-2.5 rounded-lg text-left'}"
		>
			{#if variant === 'list'}
				<span class="grid size-[22px] shrink-0 place-items-center rounded-full border-2 transition-colors {on ? 'border-(color:--kk-fill)' : 'border-(color:--kk-line-strong) group-hover:border-(color:--kk-accent)'}">
					<span class="size-2.5 rounded-full bg-(--kk-fill) transition-transform {on ? 'scale-100' : 'scale-0'}"></span>
				</span>
			{/if}
			{#if variant === 'cards' && o.icon}<Icon name={o.icon} size={26} fill={on} />{/if}
			{o.label}
		</button>
		</Tooltip>
	{/each}
</div>
