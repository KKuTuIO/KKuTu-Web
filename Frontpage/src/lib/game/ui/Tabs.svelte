<script>
	/**
	 * Horizontal tab strip (WAI-ARIA tablist). The parent renders the panels:
	 *   <Tabs bind:value tabs={[{id,label}]} idPrefix="x" />
	 *   <div role="tabpanel" id="x-panel-{id}" aria-labelledby="x-tab-{id}">…</div>
	 * @type {{tabs:{id:string,label:string}[], value:string, idPrefix:string, label?:string}}
	 */
	let { tabs = [], value = $bindable(), idPrefix, label = '' } = $props();
	let els = $state([]);
	function nav(e, i) {
		const n = tabs.length;
		const to = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: n - 1 }[e.key];
		if (to === undefined) return;
		e.preventDefault();
		const j = (to + n) % n;
		value = tabs[j].id;
		els[j]?.focus();
	}
</script>

<div role="tablist" aria-label={label} class="flex border-b border-(color:--kk-line)">
	{#each tabs as tab, i (tab.id)}
		<button
			bind:this={els[i]}
			type="button"
			role="tab"
			id="{idPrefix}-tab-{tab.id}"
			aria-selected={value === tab.id}
			aria-controls="{idPrefix}-panel-{tab.id}"
			tabindex={value === tab.id ? 0 : -1}
			onclick={() => (value = tab.id)}
			onkeydown={(e) => nav(e, i)}
			class="kk-tab focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-(--kk-accent)"
		>{tab.label}</button>
	{/each}
</div>
