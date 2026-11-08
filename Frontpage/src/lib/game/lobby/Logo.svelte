<script>
	import { t } from '../i18n.svelte.js';
	import { worn } from '../theme.svelte.js';
	/**
	 * The symbol mark. Light theme: colour symbol; dark theme: the white symbol (bi_white.svg). Without a worn 테마 the device's
	 * colour scheme decides; a worn theme decides by its own (`dark`), so a light theme on a dark device keeps the colour symbol.
	 */
	let { class: cls = 'h-8' } = $props();
	const MAIN = 'https://cdn.kkutu.io/img/bi/bi_main.svg';
	const WHITE = 'https://cdn.kkutu.io/img/bi/bi_white.svg';
</script>

{#if worn.theme}
	<img class={cls} src={worn.theme.dark ? WHITE : MAIN} alt={t('play.brand')} />
{:else}
	<img class="{cls} dark:hidden" src={MAIN} alt={t('play.brand')} />
	<img class="hidden {cls} dark:block" src={WHITE} alt="" aria-hidden="true" />
{/if}
