<script>
	import { t, i18n } from '../i18n.svelte.js';
	let { store } = $props();
	const net = $derived(store.net);
	let captchaEl = $state();

	$effect(() => {
		if (net.status === 'captcha' && captchaEl) store.conn.renderCaptcha(captchaEl);
	});

	// Close codes the game server sends as `error` packets before dropping the socket.
	const guestCodes = [449, 402, 479, 457, 440];
	const nextHref = $derived(
		{ LOGIN_REQUIRED: '/login', SETUP_REQUIRED: '/setup', ACCOUNT_BLOCKED: '/account/delete' }[net.error?.code] ??
			(guestCodes.includes(net.error?.code) ? '/login' : undefined)
	);
</script>

{#if net.status !== 'ready' && net.status !== 'connecting'}
	<div class="fixed inset-0 z-50 grid place-items-center bg-(--kk-bg) p-6 text-center break-keep" role="alertdialog" aria-live="polite" aria-label={t('play.gate.label')}>
		<div class="flex max-w-sm flex-col items-center gap-4 text-balance">
			{#if net.status === 'captcha'}
				<p class="text-base font-bold">{t('play.gate.captcha')}</p>
				<div bind:this={captchaEl}></div>
			{:else}
				{#if net.error?.code === 'NO_SUCH_SERVER'}<img src="/img/play/not-found.png" alt="" class="w-40 max-w-full" />{/if}
				<p class="text-base font-bold">
					{net.status === 'closed' ? t('play.gate.closed') : (i18n.messages[`play.error.${net.error?.code}`] ?? t('play.gate.generic'))}
				</p>
				{#if i18n.messages[`play.close.${net.error?.code}`]}<p class="text-(color:--kk-muted)">{t(`play.close.${net.error.code}`)}</p>
				{:else if net.error?.message}<p class="text-(color:--kk-muted)">{net.error.message}</p>{/if}
				{#if net.status === 'closed' && net.error?.code}<p class="text-xs text-(color:--kk-muted)">(#{net.error.code})</p>{/if}
				<div class="flex gap-2">
					{#if nextHref}<a href={nextHref} class="kk-btn kk-btn-primary inline-flex min-h-12 items-center justify-center px-6 text-base">{nextHref === '/login' ? t('play.gate.login') : t('play.gate.go')}</a>{/if}
					<button type="button" onclick={() => location.reload()} class="kk-btn min-h-12 px-6 text-base">{t('play.gate.retry')}</button>
				</div>
			{/if}
		</div>
	</div>
{/if}
