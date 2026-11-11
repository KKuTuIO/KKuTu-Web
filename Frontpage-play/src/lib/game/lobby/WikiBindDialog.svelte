<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import { wikiOrigin } from '../wiki.js';
	import { isWikiAddress, loadBinding, requestChallenge, unbind, verifyChallenge } from '../wikiAccount.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';

	/**
	 * 리오위키 연동: link this account to a wiki user. Not bound yet: the wiki user name, then a one-time code to put on the wiki (user page or an
	 * edit summary), then 확인 lets the server look for it. Bound: who, since when, how many edits have settled (an edit counts after 24 hours).
	 */
	let { open = $bindable(false) } = $props();
	/** @type {import('../wikiAccount.js').WikiBinding|null} */
	let binding = $state(null);
	let step = $state('loading'); // loading | name | code | bound | failed
	let username = $state('');
	let challenge = $state(null); // {code, expires_at, page, wiki_url}
	let busy = $state(false);
	let error = $state('');
	let copied = $state(false);

	const KNOWN = { challenge_not_found: 'notFound', challenge_expired: 'expired', wiki_account_taken: 'taken', wiki_user_not_found: 'noUser', network: 'network' };
	const errorText = (e) => (KNOWN[e.code] ? t(`play.wiki.bind.err.${KNOWN[e.code]}`) : e.message || t('play.wiki.bind.err.generic'));
	const expired = $derived(error === t('play.wiki.bind.err.expired'));

	async function refresh() {
		step = 'loading';
		error = '';
		try {
			binding = await loadBinding();
			step = binding.bound ? 'bound' : 'name';
		} catch (e) {
			error = errorText(e);
			step = 'failed';
		}
	}
	$effect(() => {
		if (!open) return;
		username = '';
		challenge = null;
		copied = false;
		refresh();
	});

	async function start(e) {
		e?.preventDefault();
		if (busy || !username.trim()) return;
		busy = true;
		error = '';
		try {
			challenge = await requestChallenge(username);
			copied = false;
			step = 'code';
		} catch (err) {
			error = errorText(err);
		} finally {
			busy = false;
		}
	}
	async function verify() {
		if (busy) return;
		busy = true;
		error = '';
		try {
			binding = await verifyChallenge();
			challenge = null;
			step = 'bound';
			toast(t('play.wiki.bind.done'), { kind: 'success' });
		} catch (err) {
			error = errorText(err);
		} finally {
			busy = false;
		}
	}
	async function release() {
		if (busy || !(await confirm({ message: t('play.wiki.bind.unbindConfirm') }))) return;
		busy = true;
		try {
			await unbind();
			binding = null;
			username = '';
			step = 'name';
			toast(t('play.wiki.bind.unbound'), { kind: 'info' });
		} catch (err) {
			error = errorText(err);
		} finally {
			busy = false;
		}
	}
	async function copy() {
		try {
			await navigator.clipboard.writeText(challenge.code);
			copied = true;
			toast(t('play.wiki.bind.copied'), { kind: 'info' });
		} catch {
			/* the code is on screen to copy by hand */
		}
	}
	// The wiki itself opens at once; an address of any other site would ask first (the page would not be on the wiki).
	async function openWiki() {
		const href = challenge.wiki_url;
		if (!isWikiAddress(href, wikiOrigin()) && !(await confirm({ message: `${t('play.link.confirm')}\n${href}`, title: t('play.confirm.ok'), ok: t('play.link.yes'), cancel: t('play.link.no') }))) return;
		window.open(href, '_blank', 'noopener,noreferrer');
	}
	const when = (v) => new Date(v).toLocaleString(i18n.language);
	const sectionCls = 'flex flex-col gap-3 rounded-xl border border-(--kk-line) bg-(--kk-surface-2) p-4';
</script>

<Dialog bind:open title={t('play.wiki.bind.title')}>
	<div class="flex flex-col gap-4" aria-live="polite" aria-busy={busy || step === 'loading'}>
		{#if step === 'loading'}
			<p class="py-8 text-center text-sm text-(color:--kk-muted)">{t('play.wiki.bind.loading')}</p>
		{:else if step === 'failed'}
			<p class="rounded-xl bg-(--kk-danger-soft) px-3 py-3 text-sm font-bold text-(color:--kk-danger)" role="alert">{error}</p>
			<button type="button" onclick={refresh} class="kk-btn min-h-11 self-start px-4">{t('play.gate.retry')}</button>
		{:else if step === 'bound'}
			<section class={sectionCls}>
				<p class="text-xs font-black text-(color:--kk-muted)">{t('play.wiki.bind.bound')}</p>
				<p class="flex items-center gap-2 text-xl font-black break-all"><Icon name="verified" size={24} fill class="shrink-0 text-(color:--kk-accent-strong)" />{binding.wiki_username}</p>
				{#if binding.bound_at}<p class="text-sm text-(color:--kk-muted)">{t('play.wiki.bind.since', { date: when(binding.bound_at) })}</p>{/if}
				<p class="rounded-lg bg-(--kk-surface) px-3 py-2 text-center font-extrabold">{t('play.wiki.bind.count', { settled: binding.contributions.settled.toLocaleString(), pending: binding.contributions.pending.toLocaleString() })}</p>
				<p class="text-xs leading-relaxed text-(color:--kk-muted)">{t('play.wiki.bind.note')}</p>
			</section>
			{#if error}<p class="text-sm font-bold text-(color:--kk-danger)" role="alert">{error}</p>{/if}
			<button type="button" onclick={release} disabled={busy} class="kk-btn inline-flex min-h-11 items-center justify-center gap-1.5 self-start px-4 max-md:self-stretch"><Icon name="link_off" size={20} />{t('play.wiki.bind.unbind')}</button>
		{:else if step === 'code'}
			<section class={sectionCls}>
				<p class="text-sm font-black">{t('play.wiki.bind.steps')}</p>
				<ol class="flex list-decimal flex-col gap-1.5 pl-5 text-sm leading-relaxed">
					<li>{t('play.wiki.bind.step1')}</li>
					<li>{t('play.wiki.bind.step2', { page: challenge.page })}</li>
					<li>{t('play.wiki.bind.step3')}</li>
				</ol>
				<div class="flex flex-col gap-1.5">
					<p class="text-xs font-black text-(color:--kk-muted)">{t('play.wiki.bind.code')}</p>
					<div class="flex items-stretch gap-2">
						<code class="flex min-h-11 min-w-0 flex-1 items-center rounded-lg bg-(--kk-surface) px-3 font-mono text-base font-extrabold tracking-wide break-all select-all">{challenge.code}</code>
						<button type="button" onclick={copy} class="kk-btn inline-flex min-h-11 shrink-0 items-center gap-1 px-3 text-sm"><Icon name={copied ? 'check' : 'content_copy'} size={18} />{t('play.wiki.bind.copy')}</button>
					</div>
					<p class="text-xs text-(color:--kk-muted)">{t('play.wiki.bind.expires', { time: when(challenge.expires_at) })}</p>
				</div>
				<button type="button" onclick={openWiki} class="kk-btn inline-flex min-h-11 items-center justify-center gap-1.5 text-sm font-extrabold"><Icon name="open_in_new" size={18} />{t('play.wiki.bind.openWiki')}</button>
			</section>
			{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-3 py-2.5 text-sm font-bold text-(color:--kk-danger)" role="alert">{error}</p>{/if}
			<div class="flex flex-wrap items-center justify-between gap-2">
				<button type="button" onclick={() => ((step = 'name'), (error = ''))} class="kk-btn min-h-11 px-4 text-sm">{t('play.wiki.bind.restart')}</button>
				<div class="flex gap-2 max-md:w-full max-md:flex-col-reverse">
					{#if expired}<button type="button" onclick={start} disabled={busy} class="kk-btn min-h-11 px-4">{t('play.wiki.bind.newCode')}</button>{/if}
					<button type="button" onclick={verify} disabled={busy || expired} class="kk-btn kk-btn-primary min-h-11 px-8">{busy ? t('play.wiki.bind.verifying') : t('play.wiki.bind.verify')}</button>
				</div>
			</div>
		{:else}
			<form onsubmit={start} class="flex flex-col gap-3">
				<p class="text-sm leading-relaxed text-(color:--kk-muted)">{t('play.wiki.bind.intro')}</p>
				<label class="flex flex-col gap-1.5">
					<span class="text-xs font-black text-(color:--kk-muted)">{t('play.wiki.bind.username')}</span>
					<input bind:value={username} maxlength="85" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder={t('play.wiki.bind.usernamePh')} class="h-11 kk-input px-3" />
				</label>
				{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-3 py-2.5 text-sm font-bold text-(color:--kk-danger)" role="alert">{error}</p>{/if}
				<button class="kk-btn kk-btn-primary min-h-11 px-8 self-end max-md:self-stretch" disabled={busy || !username.trim()}>{t('play.wiki.bind.start')}</button>
			</form>
		{/if}
	</div>
</Dialog>
