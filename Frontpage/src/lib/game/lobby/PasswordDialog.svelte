<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	let { room = null, onsubmit } = $props();
	let open = $state(false);
	let pw = $state('');
	$effect(() => {
		if (room) {
			pw = '';
			open = true;
		}
	});
</script>

<Dialog bind:open title={t('play.password.title', { no: room?.no ?? '' })} onclose={() => (room = null)}>
	<form onsubmit={(e) => { e.preventDefault(); onsubmit(room.no, pw); open = false; }} class="flex flex-col gap-3">
		<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.password.label')}</span>
			<!-- svelte-ignore a11y_autofocus -->
			<input bind:value={pw} type="password" autocomplete="off" autofocus class="h-11 kk-input rounded-xl px-3" /></label>
		<button class="kk-btn kk-btn-primary min-h-12 px-6 text-base">{t('play.password.submit')}</button>
	</form>
</Dialog>
