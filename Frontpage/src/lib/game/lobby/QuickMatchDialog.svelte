<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Dialog from './Dialog.svelte';
	import GameOptions from './GameOptions.svelte';
	let { store, open = $bindable(false) } = $props();
	let mode = $state(2);
	let opts = $state({});
	const waiting = $derived(Object.values(store.net.rooms).filter((r) => !r.playing && !r.locked && r.cur < r.max && r.modeIndex === mode).length);

	function start(e) {
		e.preventDefault();
		store.quickMatch(mode, Object.fromEntries(Object.entries(opts).filter(([, on]) => on)));
		open = false;
	}
</script>

<Dialog bind:open title={t('play.quick.title')}>
	<form onsubmit={start} class="flex flex-col gap-3">
		<GameOptions net={store.net} bind:mode bind:opts />
		<p class="text-xs text-(color:--kk-muted)">{t('play.quick.hint', { count: waiting })}</p>
		<button class="kk-btn kk-btn-primary inline-flex min-h-12 items-center justify-center gap-1.5 px-6 text-base"><Icon name="play_arrow" fill />{t('play.quick.start')}</button>
	</form>
</Dialog>
