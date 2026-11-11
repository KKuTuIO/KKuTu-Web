<script>
	import { t, i18n } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Rich from '../ui/Rich.svelte';
	import { toast } from '../ui/toast.svelte.js';

	/** 이어하기: shows the guest's transfer code. It opens when the server sends the code. */
	let { store, open = $bindable(false) } = $props();
	const code = $derived(store.net.prompt?.lang === 'gidInfo' ? store.net.prompt.value : '');
	const info = $derived(t('play.progress.info', { chn: i18n.messages[`server.${store.server}`] ?? store.server }));

	// A new code from the server (each 이어하기 press) opens the dialog.
	$effect(() => {
		if (store.net.prompt?.lang === 'gidInfo') open = true;
	});

	async function copy() {
		try {
			await navigator.clipboard.writeText(code);
			toast(t('play.progress.copied'), { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}
</script>

<Dialog bind:open title={t('play.progress.title')}>
	<div class="flex flex-col gap-4">
		<p class="text-sm leading-relaxed"><Rich html={info} /></p>
		<div class="flex flex-col gap-1">
			<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.progress.code')}</span>
			<div class="flex items-stretch gap-2">
				<output class="kk-input flex h-12 min-w-0 flex-1 items-center px-3 font-mono text-lg font-extrabold tracking-widest select-all">{code}</output>
				<button type="button" onclick={copy} class="kk-btn inline-flex items-center gap-1.5 px-4"><Icon name="content_copy" size={20} />{t('play.progress.copy')}</button>
			</div>
		</div>
		<button type="button" onclick={() => (open = false)} class="kk-btn kk-btn-primary min-h-12 text-base">{t('play.progress.ok')}</button>
	</div>
</Dialog>
