<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { confirm } from '../ui/confirm.svelte.js';

	/**
	 * The account's security code, shown when the server sends it (the /보안코드 command).
	 * Laid out to resize rather than to fit one device (what Apple's guidance for folding phones asks too): no fixed widths, the code wraps onto
	 * as many lines as it needs, and the actions are a grid that is two across even on a 340 px cover screen, so the same dialog works
	 * on a cover display, an unfolded one and a desktop.
	 */
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
	<div class="mx-auto flex w-full max-w-md min-w-0 flex-col gap-4">
		<p class="text-sm leading-relaxed">{prompt ? t(`play.cmd.${prompt.lang}`) : ''}</p>
		<!-- break-all: a code has no spaces, and a single line would be cut off on a narrow screen -->
		<output class="kk-input block min-h-12 w-full min-w-0 px-3 py-2.5 font-mono text-base leading-relaxed font-extrabold break-all select-all sm:text-lg {shown ? 'tracking-wider' : 'tracking-normal'}">{shown ? (prompt?.value ?? '') : '•'.repeat(prompt?.value?.length ?? 0)}</output>
		<div class="grid grid-cols-2 gap-2">
			<button type="button" onclick={() => (shown = !shown)} aria-label={t(shown ? 'play.cmd.code.hide' : 'play.cmd.code.show')} aria-pressed={shown} class="kk-btn inline-flex min-h-12 items-center justify-center gap-1.5 px-2 text-sm whitespace-nowrap"><Icon name={shown ? 'visibility_off' : 'visibility'} size={20} />{t(shown ? 'play.cmd.code.hideShort' : 'play.cmd.code.showShort')}</button>
			<button type="button" onclick={copy} class="kk-btn inline-flex min-h-12 items-center justify-center gap-1.5 px-2 text-sm whitespace-nowrap"><Icon name="content_copy" size={20} />{t('play.cmd.code.copy')}</button>
			<button type="button" onclick={rotate} class="kk-btn col-span-2 inline-flex min-h-12 items-center justify-center gap-1.5 px-2 text-sm whitespace-nowrap"><Icon name="autorenew" size={20} />{t('play.cmd.code.rotate')}</button>
		</div>
		<button type="button" onclick={() => (open = false)} class="kk-btn kk-btn-primary min-h-12 text-base">{t('play.cmd.code.ok')}</button>
	</div>
</Dialog>
