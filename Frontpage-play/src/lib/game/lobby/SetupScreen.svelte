<script>
	import { t } from '../i18n.svelte.js';
	import { NICK_MAX, NICK_MIN, looksValid, submitNickname } from '../setup.js';
	import Icon from './Icon.svelte';
	import Logo from './Logo.svelte';
	import ProfileSwitcher from '$lib/ProfileSwitcher.svelte';

	/** First-run profile setup. It replaces the website's old /setup wizard; once it succeeds the connection simply restarts. */
	let { store } = $props();
	let nick = $state('');
	let busy = $state(false);
	let errorKey = $state('');
	let input = $state();
	const show = $derived(store.net.status === 'setup');

	$effect(() => {
		if (show) queueMicrotask(() => input?.focus());
	});

	async function submit(e) {
		e.preventDefault();
		if (busy || !looksValid(nick)) return;
		busy = true;
		errorKey = '';
		const r = await submitNickname(nick);
		busy = false;
		if (r.ok) store.conn.open();
		else errorKey = r.key;
	}
</script>

{#if show}
	<div class="fixed inset-0 z-50 flex flex-col overflow-y-auto bg-(--kk-bg)" role="dialog" aria-modal="true" aria-labelledby="setup-title">
		<!-- Same corners as the lobby bar: logo left, account menu right, so the user can always sign out. -->
		<header class="flex min-h-16 shrink-0 items-center border-b border-(color:--kk-line) bg-(--kk-surface) px-4 md:px-6">
			<a href="/" class="flex shrink-0 items-center" aria-label={t('play.home')}><Logo class="h-8" /></a>
			<div class="ml-auto"><ProfileSwitcher profileName="" accountLabel="" /></div>
		</header>

		<div class="grid flex-1 place-items-center p-6">
			<form onsubmit={submit} class="kk-card flex w-full max-w-md flex-col gap-4 p-6">
				<div class="flex flex-col items-center gap-3 text-center">
					<!-- Material's own fused person + pencil glyph, drawn plain (no backing shape). -->
					<Icon name="person_edit" size={64} fill class="mb-1 text-(color:--kk-accent-strong)" />
					<h1 id="setup-title" class="text-2xl font-black">{t('play.setup.title')}</h1>
					<p class="text-sm font-bold text-(color:--kk-muted)">{t('play.setup.lead')}</p>
				</div>

				<div class="flex flex-col gap-1">
					<label for="setup-nick" class="text-xs font-bold text-(color:--kk-muted)">{t('play.setup.label')}</label>
					<input id="setup-nick" bind:this={input} bind:value={nick} maxlength={NICK_MAX + 4} autocomplete="off" aria-invalid={!!errorKey} aria-describedby="setup-help setup-err" placeholder={t('play.setup.placeholder')} class="h-12 kk-input px-3 text-base" />
					<p id="setup-err" role="alert" class="min-h-5 text-xs font-bold text-(color:--kk-danger)">{errorKey ? t(errorKey) : ''}</p>
				</div>

				<ul id="setup-help" class="flex flex-col gap-1.5 rounded-xl bg-(--kk-surface-2) p-3 text-xs leading-relaxed text-(color:--kk-muted)">
					<li class="flex gap-1.5"><Icon name="check_circle" size={16} class="mt-px shrink-0" />{t('play.setup.ruleChars', { min: NICK_MIN, max: NICK_MAX })}</li>
					<li class="flex gap-1.5"><Icon name="check_circle" size={16} class="mt-px shrink-0" />{t('play.setup.ruleChange')}</li>
					<li class="flex gap-1.5"><Icon name="check_circle" size={16} class="mt-px shrink-0" />{t('play.setup.ruleAbuse')}</li>
					<li class="flex gap-1.5 font-bold text-(color:--kk-danger)"><Icon name="warning" size={16} class="mt-px shrink-0" />{t('play.setup.warning')}</li>
				</ul>

				<button class="kk-btn kk-btn-primary min-h-12 text-base" disabled={busy || !looksValid(nick)}>{busy ? t('play.setup.saving') : t('play.setup.submit')}</button>
			</form>
		</div>
	</div>
{/if}
