<script>
	import { adLayout, mountAd } from '../ads.js';
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';

	/**
	 * The bottom of the screen that belongs to the ad, not to the game, with a word to a guest on a PC that signing in takes it away.
	 * While it is there `--ad` (on the root) is its height: the game ends above it, and whatever sticks to the bottom of the screen
	 * moves up by that much.
	 * @type {{width:number, height:number}} the window's size
	 */
	let { width, height } = $props();
	const layout = $derived(adLayout(width, height));

	$effect(() => {
		const root = document.documentElement;
		root.style.setProperty('--ad', `calc(${layout.size}px + env(safe-area-inset-bottom))`);
		return () => root.style.removeProperty('--ad');
	});
	function ad(node) {
		return { destroy: mountAd(node) };
	}
</script>

<aside aria-label={t('play.ad.label')} class="fixed inset-x-0 bottom-0 z-[5] flex h-(--ad) items-center justify-center gap-x-5 overflow-hidden bg-(--kk-bg) pb-[env(safe-area-inset-bottom)] text-(color:--kk-text) {layout.beside ? 'flex-row' : 'flex-col'}">
	{#if layout.nudge}
		<p class="flex shrink-0 items-center gap-3 text-sm font-bold {layout.beside ? 'order-2 flex-col !items-start gap-2' : 'h-8'}">
			<span class="whitespace-nowrap text-(color:--kk-muted)">{t('play.ad.nudge')}</span>
			<a href="/login" rel="external" class="kk-btn inline-flex h-7 min-h-0 shrink-0 items-center gap-1 px-3 text-xs"><Icon name="login" size={16} />{t('play.signIn')}</a>
		</p>
	{/if}
	{#key layout.banner.unit}
		<div class="shrink-0" style:width="{layout.banner.width}px" style:height="{layout.banner.height}px">
			<ins use:ad class="kakao_ad_area" style="display:none" data-ad-unit={layout.banner.unit} data-ad-width={layout.banner.width} data-ad-height={layout.banner.height}></ins>
		</div>
	{/key}
</aside>
