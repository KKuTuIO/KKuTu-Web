<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import Checkbox from '../ui/Checkbox.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { confirm, prompt } from '../ui/confirm.svelte.js';
	import LicenseDialog from './LicenseDialog.svelte';
	import { LIMITS, SHEET, remaining } from './model.js';
	import Person from './Person.svelte';
	import RoleIcon from './RoleIcon.svelte';
	import { attempt, delegate, invite, isOwner, licensed, me, nav, publish, reissueInvite, requestPromotion, requestReview, topOf, unblock } from './store.svelte.js';

	let { dict, now } = $props();
	const owner = $derived(isOwner(dict));
	let license = $state(false);
	let agreed = $state(false);
	const accepted = $derived(dict.editors.filter((e) => e.accepted).length);
	const live = $derived(dict.liveCount);
	const filled = $derived(!!(dict.criteria.detail && dict.criteria.include && dict.criteria.source));
	const checks = $derived([
		{ ok: me.level >= LIMITS.promoteLevel, text: t('play.malmoi.manage.checkLevel', { n: LIMITS.promoteLevel }), now: t('play.malmoi.manage.nowLevel', { n: me.level }) },
		{ ok: live >= LIMITS.promoteWords, text: t('play.malmoi.manage.checkWords', { n: LIMITS.promoteWords }), now: t('play.malmoi.manage.nowWords', { n: live }) },
		{ ok: filled, text: t('play.malmoi.manage.checkCriteria'), now: '' }
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
	async function goPublic() {
		if (!licensed()) return (license = true);
		if (!(await confirm({ message: t('play.malmoi.manage.publishAsk'), ok: t('play.malmoi.common.publish') }))) return;
		if (await attempt(() => publish(dict), toast)) toast(t('play.malmoi.manage.published'), { kind: 'success' });
	}
	async function askReview() {
		if (await attempt(() => requestReview(dict), toast)) toast(t('play.malmoi.manage.reviewRequested'), { kind: 'success' });
	}
	async function promote() {
		if (await attempt(() => requestPromotion(dict), toast)) toast(t('play.malmoi.manage.promotionRequested'), { kind: 'success' });
	}
	const row = 'flex min-h-11 flex-wrap items-center justify-between gap-2';
</script>

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
			{#each blocks as b (b.name)}<div class={row}><span class="min-w-0 text-sm"><b>{b.name}</b> · {t('play.malmoi.manage.timeLeft', { time: remaining(b.until - now) })}<span class="block truncate text-xs text-(color:--kk-muted)">{b.reason}</span></span><button type="button" onclick={() => attempt(() => unblock(dict, b.name), toast)} class="kk-btn min-h-9 px-3 text-sm">{t('play.malmoi.manage.release')}</button></div>{/each}
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
			<div class={row}><h3 class="text-sm font-black">{t('play.malmoi.common.public')}</h3>{#if me.level < LIMITS.publishLevel}<span class="text-xs font-bold text-(color:--kk-muted) tabular-nums">{t('play.malmoi.manage.editorCount', { n: accepted, max: LIMITS.editors })}</span>{/if}</div>
			{#if me.level >= LIMITS.publishLevel}
				<button type="button" onclick={goPublic} class="kk-btn kk-btn-primary">{licensed() ? t('play.malmoi.common.publish') : t('play.malmoi.common.getLicense')}</button>
			{:else}
				{#if !licensed()}<button type="button" onclick={() => (license = true)} class="kk-btn">{t('play.malmoi.common.getLicense')}</button>{/if}
				{#if dict.review === 'rejected'}<p class="text-xs font-bold text-(color:--kk-danger)">{t('play.malmoi.manage.reviewRejected')}</p>{/if}
				<button type="button" disabled={accepted < LIMITS.editors || !licensed() || !filled || dict.review === 'pending'} onclick={askReview} class="kk-btn kk-btn-primary">{dict.review === 'pending' ? t('play.malmoi.manage.reviewWaiting') : t('play.malmoi.manage.reviewRequest')}</button>
			{/if}
		</div>
	{/if}

	{#if owner && dict.state === 'public' && dict.kind === 'new'}
		<div class="kk-card flex flex-col gap-2 p-4">
			<h3 class="text-sm font-black">{t('play.malmoi.manage.promotionTitle')}</h3>
			<ul class="flex flex-col gap-1">
				{#each checks as c (c.text)}<li class="flex items-center gap-2 text-sm"><span class={c.ok ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'} role="img" aria-label={c.ok ? t('play.malmoi.manage.met') : t('play.malmoi.manage.unmet')}><Icon name={c.ok ? 'check_circle' : 'radio_button_unchecked'} size={20} fill={c.ok} /></span><span class="font-bold">{c.text}</span>{#if c.now}<span class="text-xs text-(color:--kk-muted)">{c.now}</span>{/if}</li>{/each}
			</ul>
			<a href={SHEET.common} target="_blank" rel="noreferrer" class="inline-flex items-center gap-1 self-start text-sm font-extrabold text-(color:--kk-accent-strong) hover:underline">{t('play.malmoi.manage.readCriteria')}<Icon name="open_in_new" size={16} /></a>
			<Checkbox bind:checked={agreed}><span class="text-sm">{t('play.malmoi.manage.agreed')}</span></Checkbox>
			<button type="button" disabled={!agreed || !checks.every((c) => c.ok) || dict.promotion === 'pending'} onclick={promote} class="kk-btn kk-btn-primary">{dict.promotion === 'pending' ? t('play.malmoi.manage.promotionWaiting') : t('play.malmoi.manage.promotionRequest')}</button>
		</div>
	{/if}
</div>

<LicenseDialog bind:open={license} />
