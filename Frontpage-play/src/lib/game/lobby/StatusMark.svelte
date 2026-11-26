<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import { pingLevel } from '../lobbyModel.js';

	/**
	 * A small watermark in the bottom-right corner: the connection (a signal icon in green / yellow / red by the latest
	 * ping, with the milliseconds) and the build (the Git commit). It never takes clicks, and sits in the page's bottom margin, below the
	 * chat button's edge. Shown while connected, and after the connection was lost (then red).
	 * A build players do not get (anything but `release`) says so in red, on every screen.
	 */
	let { store } = $props();
	const net = $derived(store.net);
	const shown = $derived(['ready', 'closed', 'reconnecting'].includes(net.status));
	const level = $derived(net.status === 'ready' ? pingLevel(net.pingMs) : 'offline');
	const ICON = { good: 'signal_cellular_alt', fair: 'signal_cellular_alt_2_bar', poor: 'signal_cellular_alt_1_bar', offline: 'signal_cellular_connected_no_internet_0_bar', unknown: 'signal_cellular_alt' };
	const COLOR = { good: 'var(--kk-ping-good)', fair: 'var(--kk-ping-fair)', poor: 'var(--kk-ping-poor)', offline: 'var(--kk-ping-poor)', unknown: 'var(--kk-muted)' };
	// the build's Git commit (vite.config.js), not a hand-kept version number
	const version = typeof __COMMIT__ === 'undefined' ? '' : __COMMIT__;
	const unreleased = typeof __PLAY_BUNDLE__ !== 'undefined' && __PLAY_BUNDLE__ !== 'release';
</script>

{#if shown || unreleased}
	<p class="pointer-events-none fixed right-2 bottom-[calc(var(--ad,0px)+1px)] flex max-md:top-px max-md:right-1/2 max-md:bottom-auto max-md:translate-x-1/2 items-center gap-1.5 whitespace-nowrap text-[11px] leading-none font-bold tabular-nums select-none {unreleased ? 'z-[200]' : 'z-10'}">
		{#if unreleased}
			<span class="text-red-600"><span class="max-md:hidden">{t('play.status.devBundle')}</span><span class="md:hidden">{t('play.status.devBundleShort')}</span></span>
		{/if}
		{#if shown}
			<span class="inline-flex items-center gap-0.5 opacity-70" style:color={COLOR[level]}>
				<Icon name={ICON[level]} size={14} />
				{#if level === 'good' || level === 'fair' || level === 'poor'}{net.pingMs}ms{/if}
				<span class="sr-only">{t(`play.status.${level}`)}</span>
			</span>
		{/if}
		{#if version}<span class="text-(color:--kk-muted) opacity-70 max-md:hidden">{t('play.status.version', { version })}</span>{/if}
	</p>
{/if}
