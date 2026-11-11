<script>
	import { onMount } from 'svelte';
	import { fly } from 'svelte/transition';
	import { quintOut } from 'svelte/easing';
	import { ms } from '../motion.js';
	import { t } from '../i18n.svelte.js';
	import { academyApi } from './api.js';
	import Icon from '../lobby/Icon.svelte';
	import RuleBar from './RuleBar.svelte';
	import DictionaryTab from './DictionaryTab.svelte';
	import InjeongTab from './InjeongTab.svelte';
	import SimulatorTab from './SimulatorTab.svelte';
	import PracticeTab from './PracticeTab.svelte';
	import EngineTab from './EngineTab.svelte';
	import ReplayTab from './ReplayTab.svelte';
	import AdminTab from './AdminTab.svelte';

	/**
	 * The 아카데미: the word tools (search, 어인정, 수순 실험, 루트 대결, 전략 엔진, 게임 복기) as a page of the game,
	 * shown in the lobby's main area like the waiting room. `tab` and `game` come from the address (?view=academy&tab=…).
	 */
	let { store, tab = $bindable('dictionary'), game = '' } = $props();

	const ids = $derived(['dictionary', 'injeong', 'simulator', 'practice', 'engine', 'replay', ...(store.net.admin ? ['admin'] : [])]);
	const ICONS = { dictionary: 'dictionary', injeong: 'verified', simulator: 'conversion_path', practice: 'swords', engine: 'account_tree', replay: 'movie_edit', admin: 'admin_panel_settings' };
	const tabs = $derived(ids.map((id) => ({ id, icon: ICONS[id], label: t(`play.academy.tab.${id}`) })));
	let tabEls = $state([]);
	// Arrow keys / Home / End move between the tools (a tab list works like one control).
	function nav(e, i) {
		const to = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: tabs.length - 1 }[e.key];
		if (to === undefined) return;
		e.preventDefault();
		const j = (to + tabs.length) % tabs.length;
		pick(tabs[j].id);
		tabEls[j]?.focus();
	}
	// Comes up from under the chat, like the waiting room.
	function rise(node, { duration = 520 } = {}) {
		return { duration: ms(duration), easing: quintOut, css: (t, u) => `transform: translateY(${u * 14}%); opacity: ${Math.min(1, t * 2.2)}` };
	}
	let meta = $state(null);
	let config = $state({
		lang: 'ko', dictionary: 'COMBINED', direction: 'FORWARD', duum: true, minLength: 2, maxLength: 64,
		includeLoanword: true, includeSpaced: true, includeDialect: true, includeOld: true, includeCultural: true, includeKung: true,
		themes: [], excludedThemes: [], excludedWords: []
	});
	const showRules = $derived(!['replay', 'injeong', 'admin'].includes(tab));

	// The address follows the tab (so a link opens the same tool).
	function pick(id) {
		tab = id;
		const url = new URL(window.location.href);
		url.searchParams.set('view', 'academy');
		url.searchParams.set('tab', id);
		if (id !== 'replay') url.searchParams.delete('game');
		history.replaceState(null, '', url);
	}
	onMount(async () => {
		try {
			meta = await academyApi.meta();
		} catch {
			meta = null;
		}
	});
</script>

<section aria-label={t('play.academy.title')} in:rise|global class="flex min-h-0 flex-1 flex-col gap-3">
	<!-- The tools as a row of cards: icon above the name, the open one raised. Scrolls sideways when the window is narrow. -->
	<div role="tablist" aria-label={t('play.academy.tabs.label')} class="kk-card flex shrink-0 gap-1.5 overflow-x-auto bg-(--kk-surface-2) p-1.5">
		{#each tabs as tb, i (tb.id)}
			{@const on = tb.id === tab}
			<button
				bind:this={tabEls[i]}
				type="button"
				role="tab"
				id="academy-tab-{tb.id}"
				aria-selected={on}
				aria-controls="academy-panel-{tb.id}"
				tabindex={on ? 0 : -1}
				onclick={() => pick(tb.id)}
				onkeydown={(e) => nav(e, i)}
				class="flex min-h-16 min-w-24 flex-1 flex-col items-center justify-center gap-1 rounded-xl border px-3 py-2 text-sm font-bold whitespace-nowrap transition-[background-color,color,box-shadow] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-(--kk-accent) {on ? 'border-(color:--kk-line-strong) bg-(--kk-surface) text-(color:--kk-text) shadow-[0_2px_0_var(--kk-line-strong)]' : 'border-transparent text-(color:--kk-muted) hover:text-(color:--kk-text)'}"
			>
				<Icon name={tb.icon} size={26} fill={on} class={on ? 'text-(color:--kk-accent-strong)' : ''} />
				{tb.label}
			</button>
		{/each}
	</div>
	<div class="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto pr-1 pb-1">
		{#if showRules}<RuleBar bind:config {meta} />{/if}
		{#key tab}
			<div id="academy-panel-{tab}" role="tabpanel" aria-labelledby="academy-tab-{tab}" in:fly|global={{ y: 14, duration: ms(260), easing: quintOut }} class="min-w-0">
				{#if tab === 'dictionary'}<DictionaryTab {config} {meta} />
				{:else if tab === 'injeong'}<InjeongTab bind:config {meta} />
				{:else if tab === 'simulator'}<SimulatorTab {config} />
				{:else if tab === 'practice'}<PracticeTab {config} />
				{:else if tab === 'engine'}<EngineTab {config} />
				{:else if tab === 'replay'}<ReplayTab bind:config initialGame={game} />
				{:else if tab === 'admin'}<AdminTab />{/if}
			</div>
		{/key}
	</div>
</section>
