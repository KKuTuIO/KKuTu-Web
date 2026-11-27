<script>
	import { t } from '../i18n.svelte.js';
	import PillTabs from './PillTabs.svelte';
	import Icon from '../lobby/Icon.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import Cover from './Cover.svelte';
	import HelpButton from './HelpButton.svelte';
	import WordsTab from './WordsTab.svelte';
	import ChangesTab from './ChangesTab.svelte';
	import ManageTab from './ManageTab.svelte';
	import ContribTab from './ContribTab.svelte';
	import RewardMark from './RewardMark.svelte';
	import Person from './Person.svelte';
	import RoleIcon from './RoleIcon.svelte';
	import PersonStack from './PersonStack.svelte';
	import { LIMITS, SHEET, rewardShare } from './model.js';
	import { attempt, book, isEditorOf, isManager, nav, openBook, register, topOf } from './store.svelte.js';

	let { dict, lead } = $props();
	let now = $state(Date.now()); // the countdowns move on their own
	$effect(() => {
		const id = dict.id;
		void attempt(() => openBook(id), toast);
	});
	$effect(() => {
		const timer = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(timer);
	});
	// a change made just now must not read as older than the clock
	const clock = $derived(Math.max(now, dict.updatedAt));
	const on = $derived(book.registered.includes(dict.id));
	const manages = $derived(isManager(dict));
	const waiting = $derived(dict.changes.filter((c) => !c.reverted && (c.type === 'add' || c.type === 'edit') && c.at + LIMITS.applyMs > clock).length);
	const tabs = $derived([
		{ id: 'words', icon: 'list_alt', label: t('play.malmoi.common.word') },
		{ id: 'changes', icon: 'history', label: t('play.malmoi.common.recent'), count: dict.state === 'public' ? waiting : 0 },
		{ id: 'contrib', icon: 'insights', label: t('play.malmoi.common.contrib') },
		{ id: 'rules', icon: 'rule', label: t('play.malmoi.common.criteria') },
		...(manages ? [{ id: 'manage', icon: 'settings', label: t('play.malmoi.common.manage') }] : [])
	]);
	const tab = $derived(tabs.some((x) => x.id === nav.tab) ? nav.tab : 'words');
	async function toggle() {
		if (!(await attempt(() => register(dict.id, !on), toast))) return;
		toast(!on ? t('play.malmoi.detail.registeredToast') : t('play.malmoi.detail.unregisteredToast'), { kind: !on ? 'success' : 'info' });
	}
	async function share() {
		try {
			await navigator.clipboard.writeText(`${location.origin}${location.pathname}?view=malmoi&${dict.invite ? `join=${dict.invite}` : `id=${dict.id}`}`);
			toast(t('play.malmoi.detail.linkCopied'), { kind: 'success' });
		} catch {
			toast(t('play.malmoi.detail.linkFailed'), { kind: 'error' });
		}
	}
	const RULES = [['detail', t('play.malmoi.criteria.detail')], ['include', t('play.malmoi.criteria.include')], ['source', t('play.malmoi.criteria.source')]];
</script>

<div class="relative z-20 flex shrink-0 items-center gap-2 px-3 py-2 max-[400px]:gap-1.5 max-[400px]:px-2">
	{@render lead()}
	<div class="min-w-0">
		<PillTabs bind:value={() => tab, (v) => (nav.tab = v)} {tabs} idPrefix="malmoi-detail" label={t('play.malmoi.common.name')} />
	</div>
	<div class="ml-auto shrink-0"><HelpButton topic="detail" /></div>
</div>

<div class="min-h-0 flex-1 overflow-y-auto px-3">
<div class="flex min-w-0 flex-col gap-3 pb-20">
	<div class="kk-card flex flex-col gap-3 p-4">
		<!-- the picture, the title and the description, and the buttons -->
		<div class="flex flex-wrap items-start gap-3">
			<Cover cover={dict.cover} size={72} />
			<div class="min-w-0 flex-1 basis-44">
				<h2 class="flex flex-wrap items-center gap-2 text-2xl leading-tight font-black break-keep">{dict.title}{#if dict.kind === 'ext'}<span class="kk-chip">{t('play.malmoi.common.extPackBadge')}</span>{/if}{#if dict.state === 'unlisted'}<span class="grid size-7 place-items-center rounded-lg bg-(--kk-surface-2) text-(color:--kk-muted)" role="img" aria-label={t('play.malmoi.common.unlisted')} title={t('play.malmoi.common.unlisted')}><Icon name="lock" size={18} fill /></span>{/if}</h2>
				<p class="text-sm break-keep">{dict.desc}</p>
			</div>
			<div class="ml-auto flex gap-2 max-sm:w-full">
				{#if dict.state === 'public' || isEditorOf(dict)}<button type="button" onclick={share} class="kk-btn inline-flex items-center justify-center gap-1.5 px-4 text-sm max-sm:flex-1"><Icon name="link" size={20} />{t('play.malmoi.common.inviteLink')}</button>{/if}
				<button type="button" aria-pressed={on} onclick={toggle} class="kk-btn inline-flex items-center justify-center gap-1.5 px-5 text-sm max-sm:flex-1 {on ? '' : 'kk-btn-primary'}"><Icon name={on ? 'check' : 'add'} size={20} />{on ? t('play.malmoi.common.registered') : t('play.malmoi.common.register')}</button>
			</div>
		</div>
		<!-- who, how many, what it pays: one line each, so a long nickname is cut short and nothing else moves -->
		<div class="flex min-w-0 flex-col gap-1.5 text-sm font-bold">
			{#if dict.kind === 'new'}<div class="flex min-w-0 items-center gap-2"><RoleIcon role="owner" size={22} /><Person name={dict.owner} size={30} /><span class="min-w-0 truncate">{dict.owner}</span></div>{/if}
			{#if topOf(dict).length}<div class="flex min-w-0 items-center gap-2"><RoleIcon role="top" size={22} /><PersonStack names={topOf(dict)} size={30} /><span class="min-w-0 truncate">{topOf(dict).join(', ')}</span></div>{/if}
			<div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-(color:--kk-muted) tabular-nums">
				<span>{t('play.malmoi.detail.words', { n: dict.wordCount.toLocaleString(), live: dict.liveCount.toLocaleString() })}</span><span>{t('play.malmoi.detail.registrants', { n: dict.registrants.toLocaleString() })}</span>
			</div>
			<RewardMark share={dict.state === 'public' ? rewardShare([dict]) : 0} size={22} />
		</div>
	</div>

	<div id="malmoi-detail-panel-{tab}" role="tabpanel" aria-labelledby="malmoi-detail-tab-{tab}" class="min-w-0">
		{#if tab === 'words'}<WordsTab {dict} now={clock} />
		{:else if tab === 'changes'}<ChangesTab {dict} now={clock} />
		{:else if tab === 'contrib'}<ContribTab {dict} now={clock} />
		{:else if tab === 'manage'}<ManageTab {dict} now={clock} />
		{:else}
			<div class="kk-card flex flex-col gap-4 p-4">
				{#if dict.kind === 'ext'}
					<p class="text-sm">{t('play.malmoi.detail.baseCriteria', { base: dict.base })}</p>
				{:else}
					<dl class="flex flex-col gap-3">
						{#each RULES as [key, label] (key)}<div><dt class="text-xs font-bold text-(color:--kk-muted)">{label}</dt><dd class="text-sm leading-relaxed break-words">{dict.criteria[key] || t('play.malmoi.criteria.empty')}</dd></div>{/each}
					</dl>
				{/if}
				<div class="flex flex-wrap gap-2 border-t border-(color:--kk-line) pt-3">
					{#each [[SHEET.common, t('play.malmoi.detail.sheetCommon')], [SHEET.ko, t('play.malmoi.detail.sheetKo')], [SHEET.trial, t('play.malmoi.detail.sheetTrial')]] as [href, label] (href)}
						<a {href} target="_blank" rel="noreferrer" class="kk-btn inline-flex items-center gap-1 px-3 text-sm">{label}<Icon name="open_in_new" size={16} /></a>
					{/each}
				</div>
			</div>
		{/if}
	</div>
</div>
</div>
