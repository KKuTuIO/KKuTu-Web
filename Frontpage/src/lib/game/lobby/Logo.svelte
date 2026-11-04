<script>
	import { t } from '../i18n.svelte.js';
	/** The symbol mark. Light theme: colour symbol; dark theme: the white symbol (bi_white.svg). */
	let { class: cls = 'h-8' } = $props();
</script>

<img class="{cls} dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_main.svg" alt={t('play.brand')} />
<img class="hidden {cls} dark:block" src="https://cdn.kkutu.io/img/bi/bi_white.svg" alt="" aria-hidden="true" />
