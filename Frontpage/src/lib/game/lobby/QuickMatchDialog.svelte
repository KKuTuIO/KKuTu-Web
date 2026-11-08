<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Dialog from './Dialog.svelte';
	import GameOptions from './GameOptions.svelte';
	import { toast } from '../ui/toast.svelte.js';
	/** `start`: the mode to open with (the profile's recommendation), else the default one. */
	let { store, open = $bindable(false), start: startMode = 2 } = $props();
	let mode = $state(2);
	let opts = $state({});
	let injpick = $state([]);
	// Every opening starts from the defaults.
	$effect(() => {
		if (open) {
			mode = startMode;
			opts = {};
			injpick = [];
		}
	});
	const waiting = $derived(Object.values(store.net.rooms).filter((r) => !r.playing && !r.locked && r.cur < r.max && r.modeIndex === mode).length);

	function go(e) {
		e.preventDefault();
		const result = store.quickMatch(mode, Object.fromEntries(Object.entries(opts).filter(([, on]) => on)), [...injpick]);
		// Nothing to join and no themes to open a 단어장 room with: stay and ask for them.
		if (result === 'needThemes') return toast(store.net.meta.labels['kkutu.js.error.413'] ?? t('play.options.themesNone'), { kind: 'error' });
		open = false;
	}
</script>

<Dialog bind:open title={t('play.quick.title')}>
	<form onsubmit={go} class="flex flex-col gap-3">
		<GameOptions net={store.net} bind:mode bind:opts bind:injpick withThemes />
		<p class="text-xs text-(color:--kk-muted)">{t('play.quick.hint', { count: waiting })}</p>
		<button class="kk-btn kk-btn-primary inline-flex min-h-12 items-center justify-center gap-1.5 px-6 text-base"><Icon name="play_arrow" fill />{t('play.quick.start')}</button>
	</form>
</Dialog>
