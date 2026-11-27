<script>
	import Icon from '../lobby/Icon.svelte';

	/**
	 * The page's tabs, in the look of the 사전 (academy) page: one translucent pill, an icon and a name on each tab, the open one tinted. It is a tab
	 * list like `ui/Tabs`: the parent draws the panels (`id="{idPrefix}-panel-{id}"`, `aria-labelledby="{idPrefix}-tab-{id}"`).
	 * @type {{tabs:{id:string, label:string, icon:string, count?:number}[], value:string, idPrefix:string, label?:string}}
	 */
	let { tabs = [], value = $bindable(), idPrefix, label = '' } = $props();
	let els = $state([]);
	function nav(e, i) {
		const to = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: tabs.length - 1 }[e.key];
		if (to === undefined) return;
		e.preventDefault();
		const j = (to + tabs.length) % tabs.length;
		value = tabs[j].id;
		els[j]?.focus();
	}
</script>

<div role="tablist" aria-label={label} class="flex min-w-0 gap-1.5 overflow-x-auto rounded-xl bg-(--kk-surface)/80 p-1">
	{#each tabs as tb, i (tb.id)}
		{@const on = tb.id === value}
		<button
			bind:this={els[i]}
			type="button"
			role="tab"
			id="{idPrefix}-tab-{tb.id}"
			aria-selected={on}
			aria-controls="{idPrefix}-panel-{tb.id}"
			tabindex={on ? 0 : -1}
			onclick={() => (value = tb.id)}
			onkeydown={(e) => nav(e, i)}
			class="inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-lg px-3 text-sm font-bold whitespace-nowrap transition-colors focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-(--kk-accent) max-[400px]:px-2.5 {on ? 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted) hover:bg-(--kk-surface-2)'}"
		>
			<Icon name={tb.icon} size={20} fill={on} />
			{tb.label}
			{#if tb.count}<span class="rounded-full bg-(--kk-surface-2) px-1.5 text-xs tabular-nums {on ? 'text-(color:--kk-accent-strong)' : ''}">{tb.count}</span>{/if}
		</button>
	{/each}
</div>
