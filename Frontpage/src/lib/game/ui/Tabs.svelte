<script>
	import Tooltip from './Tooltip.svelte';
	/**
	 * Horizontal tab strip (WAI-ARIA tablist). The parent renders the panels:
	 *   <Tabs bind:value tabs={[{id,label}]} idPrefix="x" />
	 *   <div role="tabpanel" id="x-panel-{id}" aria-labelledby="x-tab-{id}">…</div>
	 * `orientation="vertical"` stacks the tabs (indicator on the right edge) and uses Up/Down arrows.
	 * @type {{tabs:{id:string,label:string,count?:number,alert?:boolean,disabled?:boolean,hint?:string}[], value:string, idPrefix:string, label?:string, orientation?:'horizontal'|'vertical'}}
	 */
	let { tabs = [], value = $bindable(), idPrefix, label = '', orientation = 'horizontal' } = $props();
	const vertical = $derived(orientation === 'vertical');
	let els = $state([]);
	function nav(e, i) {
		const n = tabs.length;
		const next = vertical ? 'ArrowDown' : 'ArrowRight';
		const prev = vertical ? 'ArrowUp' : 'ArrowLeft';
		const to = { [next]: i + 1, [prev]: i - 1, Home: 0, End: n - 1 }[e.key];
		if (to === undefined) return;
		e.preventDefault();
		const j = (to + n) % n;
		if (tabs[j].disabled) return;
		value = tabs[j].id;
		els[j]?.focus();
	}
</script>

<div role="tablist" aria-orientation={orientation} aria-label={label} class="flex {vertical ? 'flex-col' : 'border-b border-(color:--kk-line)'}">
	{#each tabs as tab, i (tab.id)}
		<Tooltip text={tab.disabled ? (tab.hint ?? '') : ''}><button
			bind:this={els[i]}
			type="button"
			role="tab"
			id="{idPrefix}-tab-{tab.id}"
			aria-selected={value === tab.id}
			aria-controls="{idPrefix}-panel-{tab.id}"
			tabindex={value === tab.id ? 0 : -1}
			aria-disabled={tab.disabled || undefined}
			onclick={() => !tab.disabled && (value = tab.id)}
			onkeydown={(e) => nav(e, i)}
			class="kk-tab relative inline-flex items-center justify-center gap-1.5 {vertical ? 'kk-tab-v' : ''} {vertical && tab.alert && tab.count ? '!pl-2 !pr-5 whitespace-nowrap' : ''} focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-(--kk-accent)"
		>{tab.label}{#if tab.count}<span class="rounded-full px-2 py-0.5 text-xs leading-none font-extrabold tabular-nums {vertical ? 'absolute top-1/2 right-1 -translate-y-1/2' : ''} {tab.alert ? 'grid h-4 min-w-4 place-items-center bg-(--kk-danger) !px-1 !py-0 !text-[10px] text-white shadow-[0_1px_0_rgb(0_0_0/0.2)]' : value === tab.id ? 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' : 'bg-(--kk-surface-2) text-(color:--kk-muted)'}">{tab.count}</span>{/if}</button></Tooltip>
	{/each}
</div>
