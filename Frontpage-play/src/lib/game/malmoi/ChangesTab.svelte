<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import EmptyState from '../lobby/EmptyState.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import ReasonDialog from './ReasonDialog.svelte';
	import { LIMITS, remaining } from './model.js';
	import Person from './Person.svelte';
	import { attempt, block, canRevert, isManager, look, report, revert, topOf, unblock } from './store.svelte.js';

	/** 최근 변경: what came in, how long until it counts, and what a manager can do about it before then. */
	let { dict, now } = $props();
	let ask = $state(null);
	const manager = $derived(isManager(dict));
	const KINDS = {
		add: { icon: 'add', label: t('play.malmoi.common.add') },
		edit: { icon: 'edit', label: t('play.malmoi.changes.edit') },
		remove: { icon: 'delete', label: t('play.malmoi.common.delete') },
		revert: { icon: 'undo', label: t('play.malmoi.changes.revert') },
		create: { icon: 'auto_awesome', label: t('play.malmoi.changes.create') },
		settings: { icon: 'tune', label: t('play.malmoi.changes.settings') },
		publish: { icon: 'public', label: t('play.malmoi.common.public') },
		block: { icon: 'block', label: t('play.malmoi.common.block') },
		review: { icon: 'fact_check', label: t('play.malmoi.changes.review') },
		promotion: { icon: 'workspace_premium', label: t('play.malmoi.changes.promotion') }
	};
	// a kind this screen does not know (the 운영팀's own actions) still gets a line
	const KIND = new Proxy(KINDS, { get: (k, type) => k[type] ?? { icon: 'info', label: String(type).startsWith('admin') ? t('play.malmoi.changes.admin') : t('play.malmoi.changes.other') } });
	// Managers and the 주인장 are never blocked by one another; the 운영팀 settles those.
	const blockable = (name) => manager && name !== look.name && name !== dict.owner && !topOf(dict).includes(name);
	const blocked = (name) => dict.blocks.find((b) => b.name === name && b.until > now);
	function wait(c) {
		if (dict.state !== 'public' || (c.type !== 'add' && c.type !== 'edit')) return '';
		const left = c.at + LIMITS.applyMs - now;
		return left > 0 && !c.reverted ? t('play.malmoi.state.appliesIn', { time: remaining(left) }) : '';
	}
	function ago(at) {
		const m = Math.max(0, Math.floor((now - at) / 60000));
		return m < 1 ? t('play.malmoi.changes.justNow') : m < 60 ? t('play.malmoi.changes.minutesAgo', { n: m }) : m < 1440 ? t('play.malmoi.changes.hoursAgo', { n: Math.floor(m / 60) }) : t('play.malmoi.changes.daysAgo', { n: Math.floor(m / 1440) });
	}
	async function undo(c) {
		if (await attempt(() => revert(dict, c), toast)) toast(t('play.malmoi.changes.reverted', { word: c.text, kind: KIND[c.type].label }), { kind: 'success' });
	}
	function stop(c) {
		ask = {
			title: t('play.malmoi.changes.blockTitle', { name: c.who }),
			ok: t('play.malmoi.common.block'),
			danger: true,
			days: true,
			done: async (reason, line, days) => {
				if (await attempt(() => block(dict, c, days, `${reason}: ${line}`), toast)) toast(t('play.malmoi.changes.blocked', { name: c.who, days }), { kind: 'success' });
			}
		};
	}
	function reportChange(c) {
		ask = {
			title: t('play.malmoi.changes.reportTitle', { name: c.who }),
			ok: t('play.malmoi.common.report'),
			done: async (reason, line) => {
				if (await attempt(() => report(dict, { word: c.text, reason, detail: line }), toast)) toast(t('play.malmoi.common.reportedToast'), { kind: 'success' });
			}
		};
	}
</script>

{#if !dict.changes.length}
	<div class="kk-card"><EmptyState compact size={120} title={t('play.malmoi.changes.empty')} /></div>
{:else}
	<ul class="kk-card divide-y divide-(--kk-line) overflow-hidden">
		{#each dict.changes as c (c.id)}
			<li class="flex flex-wrap items-center gap-x-3 gap-y-1.5 px-4 py-2.5">
				<span class="grid size-8 shrink-0 place-items-center rounded-full {c.type === 'remove' ? 'bg-(--kk-danger-soft) text-(color:--kk-danger)' : 'bg-(--kk-surface-2) text-(color:--kk-muted)'}" role="img" aria-label={KIND[c.type].label}><Icon name={KIND[c.type].icon} size={18} /></span>
				<div class="min-w-0 flex-1 basis-48">
					<p class="flex flex-wrap items-center gap-x-2"><b class="font-black break-all {c.reverted ? 'line-through opacity-60' : ''}">{c.text}</b><span class="text-xs font-bold text-(color:--kk-muted)">{KIND[c.type].label}</span>{#if wait(c)}<span class="rounded-md bg-(--kk-accent-soft) px-2 py-0.5 text-xs font-extrabold text-(color:--kk-accent-strong)">{wait(c)}</span>{/if}</p>
					<p class="flex items-center gap-1.5 text-xs text-(color:--kk-muted)"><Person name={c.who} size={20} />{c.who} · {ago(c.at)}{#if c.reason} · {c.reason}{/if}</p>
				</div>
				<div class="ml-auto flex flex-wrap justify-end gap-2">
					{#if manager && canRevert(c)}<button type="button" onclick={() => undo(c)} class="kk-btn min-h-9 px-3 text-sm">{t('play.malmoi.changes.undo')}</button>{/if}
					{#if blockable(c.who)}
						{#if blocked(c.who)}<button type="button" onclick={() => attempt(() => unblock(dict, c.who), toast)} class="kk-btn min-h-9 px-3 text-sm">{t('play.malmoi.changes.unblock')}</button>
						{:else}<button type="button" onclick={() => stop(c)} class="kk-btn min-h-9 px-3 text-sm">{t('play.malmoi.common.block')}</button>{/if}
					{/if}
					{#if c.who !== look.name}<button type="button" onclick={() => reportChange(c)} class="kk-btn min-h-9 px-3 text-sm">{t('play.malmoi.common.report')}</button>{/if}
				</div>
			</li>
		{/each}
	</ul>
{/if}

<ReasonDialog bind:ask />
