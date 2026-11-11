<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import Icon from './Icon.svelte';
	import { plainText } from '../protocol.js';
	let { store } = $props();
	const net = $derived(store.net);
	let captchaEl = $state();

	$effect(() => {
		if (net.status === 'captcha' && captchaEl) store.conn.renderCaptcha(captchaEl);
	});

	// Shown for everything but a working or still-starting connection. It is a modal <dialog>, i.e. in the browser's top
	// layer: it opens after (so above) every dialog, menu and window that may be open when the connection drops, and
	// makes them inert. They stay as they are and are usable again after the reconnect.
	const visible = $derived(!['ready', 'connecting', 'setup'].includes(net.status));
	let el = $state();
	$effect(() => {
		if (!el) return;
		if (visible && !el.open) el.showModal();
		else if (!visible && el.open) el.close();
	});

	// The countdown to the next automatic try.
	let now = $state(Date.now());
	const rc = $derived(net.reconnect);
	$effect(() => {
		if (!rc?.nextAt) return;
		now = Date.now();
		const id = setInterval(() => (now = Date.now()), 250);
		return () => clearInterval(id);
	});
	const seconds = $derived(rc?.nextAt ? Math.max(0, Math.ceil((rc.nextAt - now) / 1000)) : 0);

	// Why we are down: the device is offline, the server is being updated, or the operators announced a maintenance (with their own words)
	const OUTAGE_ICON = { network: 'wifi_off', update: 'system_update_alt', maintenance: 'construction' };
	const outage = $derived(net.outage && net.outage.kind !== 'unknown' && !rc?.exhausted && ['closed', 'reconnecting'].includes(net.status) ? net.outage : null);

	// Close codes the game server sends as `error` packets before dropping the socket.
	const guestCodes = [449, 402, 479, 457, 440];
	const nextHref = $derived(
		{ LOGIN_REQUIRED: '/login', ACCOUNT_REQUIRED: '/account', REGISTER_DISABLED: '/regFail', ACCOUNT_BLOCKED: '/account/delete' }[net.error?.code] ??
			(guestCodes.includes(net.error?.code) ? '/login' : undefined)
	);
</script>

<!-- Escape must not dismiss it: there is nothing behind it to go back to. -->
<dialog bind:this={el} oncancel={(e) => e.preventDefault()} aria-label={t('play.gate.label')} class="m-0 size-full max-h-none max-w-none overflow-auto border-0 bg-(--kk-bg) p-6 text-center break-keep text-(color:--kk-text) open:grid open:place-items-center">
	<div class="flex max-w-sm flex-col items-center gap-4 text-balance" aria-live="polite">
		{#if net.status === 'captcha'}
			<p class="text-base font-bold">{t('play.gate.captcha')}</p>
			<div bind:this={captchaEl}></div>
		{:else if outage}
			<Icon name={OUTAGE_ICON[outage.kind]} size={40} class="text-(color:--kk-muted)" />
			<p class="text-base font-bold">{outage.kind === 'maintenance' ? outage.name || plainText(i18n.messages['kkutu.js.error.500'] ?? '') : t(`play.gate.${outage.kind}`)}</p>
			{#if outage.kind === 'maintenance' && outage.reason}<p class="whitespace-pre-wrap text-(color:--kk-muted)">{outage.reason}</p>{/if}
			{#if outage.kind === 'maintenance' && outage.until}<p class="text-sm text-(color:--kk-muted)">{t('play.gate.until', { time: new Date(outage.until).toLocaleString(i18n.language) })}</p>{/if}
			{#if net.status === 'reconnecting'}
				<p class="inline-flex items-center gap-2 text-sm text-(color:--kk-muted)"><Icon name="sync" size={18} class="animate-spin" />{t('play.gate.trying')}</p>
			{:else if rc?.nextAt}
				<p class="text-sm text-(color:--kk-muted)">{t('play.gate.retryIn', { sec: seconds })}</p>
			{/if}
			<button type="button" onclick={() => store.conn.reconnect()} class="kk-btn kk-btn-primary min-h-12 px-6 text-base">{t('play.gate.reconnectNow')}</button>
		{:else if net.status === 'reconnecting'}
			<Icon name="sync" size={40} class="animate-spin text-(color:--kk-muted)" />
			<p class="text-base font-bold">{t('play.gate.reconnecting', { attempt: rc?.attempt ?? 1, max: rc?.max ?? 1 })}</p>
		{:else if net.status === 'closed' && rc && !rc.exhausted}
			<Icon name="signal_cellular_connected_no_internet_0_bar" size={40} class="text-(color:--kk-ping-poor)" />
			<p class="text-base font-bold">{t('play.gate.reconnectIn', { sec: seconds, attempt: rc.attempt, max: rc.max })}</p>
			<button type="button" onclick={() => store.conn.reconnect()} class="kk-btn kk-btn-primary min-h-12 px-6 text-base">{t('play.gate.reconnectNow')}</button>
		{:else}
			{#if net.error?.code === 'NO_SUCH_SERVER'}<img src={cdnImg('play/not-found.png')} alt="" class="w-40 max-w-full" />{/if}
			<p class="text-base font-bold">
				{rc?.exhausted ? t('play.gate.reconnectGaveUp') : net.status === 'closed' ? t('play.gate.closed') : (i18n.messages[`play.error.${net.error?.code}`] ?? t('play.gate.generic'))}
			</p>
			{#if i18n.messages[`play.close.${net.error?.code}`]}<p class="text-(color:--kk-muted)">{t(`play.close.${net.error.code}`)}</p>
			{:else if net.error?.message}<p class="text-(color:--kk-muted)">{net.error.message}</p>{/if}
			{#if net.status === 'closed' && net.error?.code}<p class="text-xs text-(color:--kk-muted)">(#{net.error.code})</p>{/if}
			<div class="flex gap-2">
				{#if nextHref}<a href={nextHref} class="kk-btn kk-btn-primary inline-flex min-h-12 items-center justify-center px-6 text-base">{nextHref === '/login' ? t('play.gate.login') : t('play.gate.go')}</a>{/if}
				{#if rc?.exhausted}<button type="button" onclick={() => store.conn.reconnect()} class="kk-btn kk-btn-primary min-h-12 px-6 text-base">{t('play.gate.reconnect')}</button>{/if}
				<button type="button" onclick={() => location.reload()} class="kk-btn min-h-12 px-6 text-base">{t('play.gate.retry')}</button>
			</div>
		{/if}
	</div>
</dialog>
