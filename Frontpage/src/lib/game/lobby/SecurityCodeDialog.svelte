<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { confirm } from '../ui/confirm.svelte.js';

	/** The account's security code, shown when the server sends it (the /보안코드 command). */
	let { store, open = $bindable(false) } = $props();
	const prompt = $derived(['uidInfo', 'newUidInfo'].includes(store.net.prompt?.lang) ? store.net.prompt : null);
	// The code is a secret: it opens hidden, and closing the dialog hides it again.
	let shown = $state(false);
	$effect(() => {
		if (prompt) open = true;
	});
	$effect(() => {
		if (!open) shown = false;
	});

	async function rotate() {
		if (!(await confirm({ message: t('play.cmd.code.rotateConfirm') }))) return;
		shown = false;
		store.conn.send('newUid', undefined, true);
	}

	async function copy() {
		try {
			await navigator.clipboard.writeText(prompt.value);
			toast(t('play.cmd.code.copied'), { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}
</script>

<Dialog bind:open title={t('play.cmd.code.title')}>
	<div class="flex flex-col gap-4">
		<p class="text-sm leading-relaxed">{prompt ? t(`play.cmd.${prompt.lang}`) : ''}</p>
		<div class="flex items-stretch gap-2">
			<output class="kk-input flex h-12 min-w-0 flex-1 items-center px-3 font-mono text-lg font-extrabold select-all {shown ? 'tracking-widest' : 'tracking-normal'}">{shown ? (prompt?.value ?? '') : '•'.repeat(prompt?.value?.length ?? 0)}</output>
			<Tooltip text={t(shown ? 'play.cmd.code.hide' : 'play.cmd.code.show')}>
				<button type="button" onclick={() => (shown = !shown)} aria-label={t(shown ? 'play.cmd.code.hide' : 'play.cmd.code.show')} aria-pressed={shown} class="kk-btn inline-flex aspect-square h-12 items-center justify-center"><Icon name={shown ? 'visibility_off' : 'visibility'} size={22} /></button>
			</Tooltip>
			<button type="button" onclick={copy} class="kk-btn inline-flex items-center gap-1.5 px-4"><Icon name="content_copy" size={20} />{t('play.cmd.code.copy')}</button>
		</div>
		<div class="flex">
			<button type="button" onclick={rotate} class="kk-btn inline-flex items-center gap-1.5 px-4"><Icon name="autorenew" size={20} />{t('play.cmd.code.rotate')}</button>
		</div>
		<button type="button" onclick={() => (open = false)} class="kk-btn kk-btn-primary min-h-12 text-base">{t('play.cmd.code.ok')}</button>
	</div>
</Dialog>
