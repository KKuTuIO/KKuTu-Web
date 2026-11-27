<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { confirm, prompt } from '../ui/confirm.svelte.js';
	import LicenseDialog from './LicenseDialog.svelte';
	import { LIMITS, SHEET, remaining } from './model.js';
	import Person from './Person.svelte';
	import RoleIcon from './RoleIcon.svelte';
	import { attempt, delegate, invite, isOwner, licensed, me, nav, reissueInvite, requestReview, topOf, unblock } from './store.svelte.js';

	let { dict, now } = $props();
	const owner = $derived(isOwner(dict));
	let license = $state(false);
	const live = $derived(dict.liveCount);
	// 공개 심사 신청 요건: 레벨, 단어 수, 집필 면허
	const publicChecks = $derived([
		{ ok: me.level >= LIMITS.publishLevel, text: t('play.malmoi.manage.checkLevel', { n: LIMITS.publishLevel }), now: t('play.malmoi.manage.nowLevel', { n: me.level }) },
		{ ok: live >= LIMITS.publishWords, text: t('play.malmoi.manage.checkWords', { n: LIMITS.publishWords }), now: t('play.malmoi.manage.nowWords', { n: live }) },
		{ ok: licensed(), text: t('play.malmoi.manage.checkLicense'), now: '' }
	]);
	const blocks = $derived(dict.blocks.filter((b) => b.until > now));

	async function giveOwner() {
		const name = await prompt({ message: t('play.malmoi.manage.delegatePrompt'), max: 30, ok: t('play.malmoi.manage.delegateOk') });
		if (!name?.trim()) return;
		if (!(await confirm({ message: t('play.malmoi.manage.delegateAsk', { name: name.trim() }), ok: t('play.malmoi.manage.delegateOk') }))) return;
		if (await attempt(() => delegate(dict, name.trim()), toast)) toast(t('play.malmoi.manage.delegated'), { kind: 'success' });
	}
	async function inviteEditor() {
		const name = await prompt({ message: t('play.malmoi.manage.invitePrompt'), max: 30, ok: t('play.malmoi.common.invite') });
		if (!name?.trim()) return;
		if (await attempt(() => invite(dict, name.trim()), toast)) toast(t('play.malmoi.manage.invited'), { kind: 'success' });
	}
	async function newLink() {
		if (!(await confirm({ message: t('play.malmoi.manage.reissueAsk'), ok: t('play.malmoi.manage.reissueOk') }))) return;
		if (await attempt(() => reissueInvite(dict), toast)) toast(t('play.malmoi.manage.reissued'), { kind: 'success' });
	}
	async function askReview() {
		if (await attempt(() => requestReview(dict), toast)) toast(t('play.malmoi.manage.reviewRequested'), { kind: 'success' });
	}
	const row = 'flex min-h-11 flex-wrap items-center justify-between gap-2';
</script>

{#snippet checklist(list)}
	<ul class="flex flex-col gap-1">
		{#each list as c (c.text)}<li class="flex items-center gap-2 text-sm"><span class={c.ok ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'} role="img" aria-label={c.ok ? t('play.malmoi.manage.met') : t('play.malmoi.manage.unmet')}><Icon name={c.ok ? 'check_circle' : 'radio_button_unchecked'} size={20} fill={c.ok} /></span><span class="font-bold">{c.text}</span>{#if c.now}<span class="text-xs text-(color:--kk-muted)">{c.now}</span>{/if}</li>{/each}
	</ul>
{/snippet}

<div class="grid min-w-0 gap-3 lg:grid-cols-2">
	<div class="kk-card flex flex-col gap-2 p-4">
		<h3 class="text-sm font-black">{t('play.malmoi.manage.managers')}</h3>
		{#if dict.kind === 'new'}
			<div class={row}><span class="inline-flex min-w-0 items-center gap-2"><RoleIcon role="owner" /><Person name={dict.owner} size={32} /><b class="min-w-0 truncate">{dict.owner}</b></span>{#if owner && dict.kind === 'new'}<button type="button" onclick={giveOwner} class="kk-btn min-h-9 px-3 text-sm">{t('play.malmoi.manage.delegate')}</button>{/if}</div>
		{/if}
		<p class="flex items-center"><RoleIcon role="top" size={22} /></p>
		<ul class="flex flex-wrap gap-2">{#each topOf(dict) as c (c)}<li class="inline-flex items-center gap-2 rounded-full bg-(--kk-surface-2) py-1 pr-3 pl-1 text-sm font-extrabold"><Person name={c} size={26} ring="var(--kk-surface-2)" />{c}</li>{:else}<li class="text-sm text-(color:--kk-muted)">{t('play.malmoi.manage.noneYet')}</li>{/each}</ul>
		{#if blocks.length}
			<p class="pt-1 text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.manage.blocked')}</p>
			{#each blocks as b (b.name)}<div class={row}><span class="min-w-0 text-sm"><b>{b.name}</b> <span class="text-xs text-(color:--kk-muted)">({t('play.malmoi.manage.timeLeft', { time: remaining(b.until - now) })})</span><span class="block truncate text-xs text-(color:--kk-muted)">{b.reason}</span></span><button type="button" onclick={() => attempt(() => unblock(dict, b.name), toast)} class="kk-btn min-h-9 px-3 text-sm">{t('play.malmoi.manage.release')}</button></div>{/each}
		{/if}
	</div>

	{#if owner}
		<div class="kk-card flex flex-col gap-2 p-4">
			<h3 class="text-sm font-black">{t('play.malmoi.manage.settings')}</h3>
			<button type="button" onclick={() => Object.assign(nav, { view: 'form' })} class="kk-btn inline-flex items-center justify-center gap-1.5 text-sm"><Icon name="palette" size={20} />{t('play.malmoi.manage.editBook')}</button>
			{#if dict.state === 'unlisted'}
				<button type="button" onclick={newLink} class="kk-btn inline-flex items-center justify-center gap-1.5 text-sm"><Icon name="link_off" size={20} />{t('play.malmoi.manage.newLink')}</button>
			{/if}
		</div>
	{/if}

	{#if owner && dict.state === 'unlisted'}
		<div class="kk-card flex flex-col gap-2 p-4">
			<div class={row}><h3 class="text-sm font-black">{t('play.malmoi.manage.editors')}</h3><button type="button" onclick={inviteEditor} class="kk-btn inline-flex min-h-9 items-center gap-1 px-3 text-sm"><Icon name="person_add" size={18} />{t('play.malmoi.common.invite')}</button></div>
			<ul class="flex flex-col">{#each dict.editors as e (e.name)}<li class={row}><span class="inline-flex min-w-0 items-center gap-2"><Person name={e.name} size={26} /><b class="min-w-0 truncate text-sm">{e.name}</b></span><span class="text-xs font-bold {e.accepted ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}">{e.accepted ? t('play.malmoi.manage.accepted') : t('play.malmoi.manage.waiting')}</span></li>{:else}<li class="text-sm text-(color:--kk-muted)">{t('play.malmoi.manage.noneYet')}</li>{/each}</ul>
		</div>
		<div class="kk-card flex flex-col gap-2 p-4">
			<h3 class="text-sm font-black">{t('play.malmoi.manage.reviewTitle')}</h3>
			{@render checklist(publicChecks)}
			<a href={SHEET.common} target="_blank" rel="noreferrer" class="inline-flex items-center gap-1 self-start text-sm font-extrabold text-(color:--kk-accent-strong) hover:underline">{t('play.malmoi.manage.readCriteria')}<Icon name="open_in_new" size={16} /></a>
			{#if dict.review === 'rejected'}<p class="text-xs font-bold text-(color:--kk-danger)">{t('play.malmoi.manage.reviewRejected')}</p>{/if}
			{#if !licensed() && me.level >= LIMITS.publishLevel}<button type="button" onclick={() => (license = true)} class="kk-btn">{t('play.malmoi.common.getLicense')}</button>{/if}
			<button type="button" disabled={!publicChecks.every((c) => c.ok) || dict.review === 'pending'} onclick={askReview} class="kk-btn kk-btn-primary">{dict.review === 'pending' ? t('play.malmoi.manage.reviewWaiting') : t('play.malmoi.manage.reviewRequest')}</button>
		</div>
	{/if}
</div>

<LicenseDialog bind:open={license} />
