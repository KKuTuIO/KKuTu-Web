<script>
	import { t } from '../i18n.svelte.js';
	import { family } from '../familyStore.svelte.js';
	import { FAMILY_INTRO_MAX_BYTES, FAMILY_WEEKLY_CONTRIBUTION_CAP, canKick, canManage, clampIntro, introBytes, sortMembers } from '../familyModel.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';
	import EmptyState from './EmptyState.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import Checkbox from '../ui/Checkbox.svelte';

	/**
	 * The family's window: 정보 (level, attendance, my contribution, the intro), 멤버 (roles, kick), 가입 신청 (answer them; master and deputy only)
	 * and 설정 (대표 프로필, recruiting, leaving). A member is an account; everything here counts per account, not per profile.
	 */
	let { store, open = $bindable(false) } = $props();
	const fam = $derived(family.mine);
	const meId = $derived(family.me);
	const role = $derived(family.role(meId));
	const manager = $derived(canManage(role));
	const me = $derived(fam?.members.find((m) => m.accountId === meId));
	const parts = $derived(store.net.meta.moremiParts);
	let tab = $state('info');
	let intro = $state('');

	const tabs = $derived([
		{ id: 'info', label: t('play.family.tab.info') },
		{ id: 'members', label: t('play.family.tab.members'), count: fam?.members.length },
		...(manager ? [{ id: 'applicants', label: t('play.family.tab.applicants'), count: family.applicantCount, alert: family.applicantCount > 0 }] : []),
		{ id: 'settings', label: t('play.family.tab.settings') }
	]);

	$effect(() => {
		if (!open) return;
		tab = 'info';
		intro = fam?.intro ?? '';
	});
	$effect(() => {
		if (tab === 'applicants' && !manager) tab = 'info';
	});

	const weekly = $derived(me?.weekly ?? 0);
	const weeklyPct = $derived(`${Math.min(100, (weekly / FAMILY_WEEKLY_CONTRIBUTION_CAP) * 100)}%`);
	const famPct = $derived(fam ? `${Math.min(100, (fam.into / fam.need) * 100)}%` : '0%');
	const members = $derived(fam ? sortMembers(fam.members) : []);

	async function saveIntro() {
		if ((await family.update({ intro: intro.trim() })).ok) toast(t('play.family.saved'), { kind: 'success' });
	}
	async function leave() {
		const last = fam.members.length === 1;
		const ok = await confirm({ message: t(role === 'master' && !last ? 'play.family.leaveMasterConfirm' : 'play.family.leaveConfirm', { name: fam.name }), ok: t('play.family.leaveDo') });
		if (!ok || !(await family.leave()).ok) return;
		open = false;
		toast(t('play.family.left'));
	}
	async function kick(m) {
		if (await confirm({ message: t('play.family.kickConfirm', { name: m.name }), ok: t('play.family.kick') })) await family.kick(m.accountId);
	}
</script>

<Dialog bind:open title={fam?.name ?? t('play.family.info')}>
	{#if fam}
		<div class="flex min-w-0 flex-col">
			<Tabs bind:value={tab} {tabs} idPrefix="family" label={t('play.family.info')} />
			<div role="tabpanel" id="family-panel-{tab}" aria-labelledby="family-tab-{tab}" class="h-[min(31rem,calc(85dvh-11rem))] overflow-x-hidden overflow-y-auto pt-4">
				{#if tab === 'info'}
					<div class="flex flex-col gap-4">
						<section class="rounded-xl bg-(--kk-surface-2) p-3.5">
							<p class="flex items-baseline justify-between text-sm font-extrabold">{t('play.family.lv', { level: fam.level })}<span class="text-xs font-bold tabular-nums text-(color:--kk-muted)">{fam.into.toLocaleString()} / {fam.need.toLocaleString()}</span></p>
							<div class="kk-bar mt-2 h-2"><span style:width={famPct}></span></div>
							<p class="mt-2 text-xs text-(color:--kk-muted)">{t('play.family.membersCount', { n: fam.members.length, max: fam.capacity })}</p>
						</section>
						<section class="grid grid-cols-2 gap-3">
							<div class="rounded-xl bg-(--kk-surface-2) p-3.5">
								<p class="text-xs font-bold text-(color:--kk-muted)">{t('play.family.attendance')}</p>
								<p class="mt-1 flex items-center gap-1.5 text-lg font-extrabold tabular-nums">{t('play.family.attendanceCount', { n: fam.attendance.today })}</p>
								<p class="mt-0.5 flex items-center gap-1 text-xs {fam.attendance.me ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}"><Icon name={fam.attendance.me ? 'check_circle' : 'radio_button_unchecked'} size={14} fill />{fam.attendance.me ? t('play.family.attendedMe') : t('play.family.notAttendedMe')}</p>
							</div>
							<div class="rounded-xl bg-(--kk-surface-2) p-3.5">
								<p class="text-xs font-bold text-(color:--kk-muted)">{t('play.family.myContribution')}</p>
								<p class="mt-1 text-lg font-extrabold tabular-nums">{weekly.toLocaleString()}</p>
								<div class="kk-bar mt-1.5 h-1.5"><span style:width={weeklyPct}></span></div>
								<p class="mt-1 text-[11px] text-(color:--kk-muted)">{t('play.family.weeklyCap', { cap: FAMILY_WEEKLY_CONTRIBUTION_CAP.toLocaleString() })}</p>
							</div>
						</section>
						<section>
							<p class="flex items-baseline justify-between text-sm font-extrabold">{t('play.family.intro')}{#if manager}<span class="text-xs font-bold tabular-nums text-(color:--kk-muted)">{introBytes(intro)}/{FAMILY_INTRO_MAX_BYTES}</span>{/if}</p>
							{#if manager}
								<textarea value={intro} oninput={(e) => (intro = clampIntro(e.currentTarget.value))} rows="3" aria-label={t('play.family.intro')} class="kk-input mt-1.5 w-full resize-none px-3 py-2 text-sm outline-none"></textarea>
								<div class="mt-2 flex justify-end"><button type="button" onclick={saveIntro} disabled={intro.trim() === fam.intro} class="kk-btn kk-btn-primary min-h-10 px-4 text-sm">{t('play.family.save')}</button></div>
							{:else}
								<p class="mt-1.5 text-sm break-words whitespace-pre-wrap {fam.intro ? '' : 'text-(color:--kk-muted)'}">{fam.intro || t('play.family.noIntro')}</p>
							{/if}
						</section>
					</div>
				{:else if tab === 'members'}
					<ul class="flex flex-col">
						{#each members as m (m.accountId)}
							<li class="flex min-h-14 items-center gap-2.5 border-t border-(color:--kk-line) px-1 py-1.5 first:border-t-0">
								<Moremi equip={m.equip} shop={store.shop} {parts} size={38} label={m.name} frame={false} />
								<span class="min-w-0 flex-1 leading-tight">
									<span class="block truncate font-extrabold">{m.name}</span>
									<span class="text-xs text-(color:--kk-muted)">{t(`play.family.role.${m.role}`)} · {t('play.family.weeklyOf', { n: m.weekly.toLocaleString() })}</span>
								</span>
								{#if role === 'master' && m.role !== 'master'}
									<Tooltip text={m.role === 'deputy' ? t('play.family.demote') : t('play.family.promote')}><button type="button" aria-label="{m.role === 'deputy' ? t('play.family.demote') : t('play.family.promote')}: {m.name}" onclick={() => family.setRole(m.accountId, m.role === 'deputy' ? 'member' : 'deputy')} class="grid size-9 place-items-center rounded-full bg-(--kk-surface-2) text-(color:--kk-muted) hover:text-(color:--kk-text)"><Icon name={m.role === 'deputy' ? 'remove_moderator' : 'add_moderator'} size={20} /></button></Tooltip>
								{/if}
								{#if canKick(role, m.role)}
									<Tooltip text={t('play.family.kick')}><button type="button" aria-label="{t('play.family.kick')}: {m.name}" onclick={() => kick(m)} class="grid size-9 place-items-center rounded-full bg-(--kk-surface-2) text-(color:--kk-muted) hover:text-(color:--kk-danger)"><Icon name="person_remove" size={20} /></button></Tooltip>
								{/if}
							</li>
						{/each}
					</ul>
				{:else if tab === 'applicants'}
					<ul class="flex flex-col">
						{#each fam.applicants as a (a.accountId)}
							<li class="flex min-h-14 items-center gap-2.5 border-t border-(color:--kk-line) px-1 py-1.5 first:border-t-0">
								<Moremi equip={a.equip} shop={store.shop} {parts} size={38} label={a.name} frame={false} />
								<span class="min-w-0 flex-1 truncate font-extrabold">{a.name}</span>
								<Tooltip text={t('play.family.deny')}><button type="button" aria-label="{t('play.family.deny')}: {a.name}" onclick={() => family.answer(a.accountId, false)} class="grid size-9 place-items-center rounded-full bg-(--kk-surface-2) text-(color:--kk-muted) hover:text-(color:--kk-danger)"><Icon name="close" size={20} /></button></Tooltip>
								<Tooltip text={fam.members.length >= fam.capacity ? t('play.family.full') : t('play.family.accept')}><button type="button" aria-label="{t('play.family.accept')}: {a.name}" disabled={fam.members.length >= fam.capacity} onclick={() => family.answer(a.accountId, true)} class="grid size-9 place-items-center rounded-full bg-(--kk-fill) text-white disabled:opacity-50"><Icon name="check" size={20} /></button></Tooltip>
							</li>
						{/each}
					</ul>
					{#if !fam.applicants.length}<EmptyState compact size={110} title={t('play.family.empty.applicants')} />{/if}
				{:else}
					<div class="flex flex-col gap-4">
						<section class="rounded-xl bg-(--kk-surface-2) p-3.5">
							<p class="text-sm font-extrabold">{t('play.family.rep')}</p>
							<p class="mt-1 text-xs text-(color:--kk-muted)">{t('play.family.repHelp')}</p>
							<p class="mt-2 text-sm">{t('play.family.repNow', { name: me?.name ?? '' })}</p>
							<button type="button" disabled={!store.me || store.me.name === me?.name} onclick={() => family.setRepresentative()} class="kk-btn mt-2 min-h-10 px-3 text-sm">{t('play.family.repUseCurrent', { name: store.me?.name ?? '' })}</button>
						</section>
						{#if manager}
							<Checkbox checked={fam.recruiting} onchange={(v) => family.update({ recruiting: v })}>{t('play.family.recruiting')}</Checkbox>
						{/if}
						<button type="button" onclick={leave} class="kk-btn inline-flex min-h-11 items-center justify-center gap-1.5 text-(color:--kk-danger)"><Icon name="logout" size={20} />{t('play.family.leave')}</button>
					</div>
				{/if}
			</div>
		</div>
	{/if}
</Dialog>
