<script>
	import Tooltip from '../ui/Tooltip.svelte';
	import { t } from '../i18n.svelte.js';
	import ProfileSwitcher from '$lib/ProfileSwitcher.svelte';
	import Icon from './Icon.svelte';
	import GameIcon from './GameIcon.svelte';
	let { store, active = 'lobby', onselect } = $props();
	const me = $derived(store.me);

	// `color` is each button's theme colour from the legacy client's menu.html. It is shown only while the
	// item is open.
	const menu = [
		{ id: 'lobby', icon: 'home', color: '#B0D2F3' },
		{ id: 'rank', icon: 'trophy', color: '#FFADD3' },
		{ id: 'shop', icon: 'storefront', color: '#B3E7B7' },
		{ id: 'dict', icon: 'menu_book', color: '#73D07A' },
		{ id: 'replay', icon: 'replay', color: '#D9FF82' },
		{ id: 'news', icon: 'campaign', color: '#FFC67F' }
	];
	const utilities = [
		{ id: 'mail', icon: 'mail', color: '#FECDD3' },
		{ id: 'help', icon: 'help', color: '#BBBBBB' },
		{ id: 'settings', icon: 'settings', color: '#CCCCCC' }
	];
	const fmt = (n) => (n ?? 0).toLocaleString();
</script>

<!--
	Labels cost width. On tablets only the current page keeps its label (the others get a tooltip);
	from xl up everything is spelled out. The phone strip scrolls, so it always shows labels.
	Utilities (mail / help / settings) live at the right of the bar and are always icon-only.
-->
{#snippet item(m, { strip = false, utility = false } = {})}
	{@const on = m.id === active}
	<Tooltip text={t(`play.nav.${m.id}`)} placement="bottom">
		<button
			type="button"
			aria-current={on ? 'page' : undefined}
			onclick={() => onselect?.(m.id)}
			style:--c={m.color}
			class="inline-flex h-10 min-w-10 items-center justify-center gap-1.5 rounded-xl font-bold whitespace-nowrap transition-colors {on
				? 'bg-(--c) px-3.5 font-extrabold text-[#111]'
				: `${utility ? 'px-2.5' : 'px-2.5 xl:px-3.5'} text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)`}"
		>
			<Icon name={m.icon} size={22} fill={on} />
			<span class={on || strip ? (utility && !on ? 'sr-only' : '') : utility ? 'sr-only' : 'sr-only xl:not-sr-only'}>{t(`play.nav.${m.id}`)}</span>
		</button>
	</Tooltip>
{/snippet}

<header class="flex min-h-16 shrink-0 items-center gap-3 border-b border-(color:--kk-line) bg-(--kk-surface) px-4 md:px-6">
	<a href="/" class="flex shrink-0 items-center" aria-label={t('play.home')}>
		<!-- Symbol only, at every size. -->
		<img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_main.svg" alt={t('play.brand')} />
		<img class="hidden h-8 dark:block" src="https://cdn.kkutu.io/img/bi/bi_white.svg" alt="" aria-hidden="true" />
	</a>
	<nav aria-label={t('play.nav.label')} class="hidden min-w-0 items-center gap-1 md:ml-4 md:flex">
		{#each menu as m (m.id)}{@render item(m)}{/each}
	</nav>
	<div class="ml-auto flex shrink-0 items-center gap-2">
		{#if me}
			<Tooltip text={t('play.currency.pingHint')}><span class="inline-flex h-10 items-center gap-2 rounded-xl bg-(--kk-surface-2) pr-4 pl-2 font-extrabold tabular-nums"><GameIcon name="ping" size={26} />{fmt(me.money)}<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.currency.ping')}</span></span></Tooltip>
			<Tooltip text={t('play.stat.winHint')}><span class="hidden h-10 items-center gap-2 rounded-xl bg-(--kk-surface-2) pr-4 pl-2 font-extrabold tabular-nums lg:inline-flex"><GameIcon name="win" size={26} />{fmt(me.wins)}<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.stat.win')}</span></span></Tooltip>
		{/if}
		<div class="hidden items-center gap-1 md:flex">
			{#each utilities as m (m.id)}{@render item(m, { utility: true })}{/each}
		</div>
		{#if store.net.id}
			<ProfileSwitcher profileSeed={store.net.id} profileName={me?.name ?? ''} accountLabel={store.net.guest ? t('play.guest') : ''} />
		{/if}
	</div>
</header>
<nav aria-label={t('play.nav.label')} class="flex shrink-0 items-center gap-1 overflow-x-auto border-b border-(color:--kk-line) bg-(--kk-surface) px-3 py-1.5 md:hidden">
	{#each menu as m (m.id)}{@render item(m, { strip: true })}{/each}
	<span class="mx-1.5 h-5 w-px shrink-0 bg-(--kk-line-strong)" aria-hidden="true"></span>
	{#each utilities as m (m.id)}{@render item(m, { strip: true, utility: true })}{/each}
</nav>
