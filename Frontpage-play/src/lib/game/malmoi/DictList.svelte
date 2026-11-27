<script>
	import { t } from '../i18n.svelte.js';
	import PillTabs from './PillTabs.svelte';
	import Checkbox from '../ui/Checkbox.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import Icon from '../lobby/Icon.svelte';
	import EmptyState from '../lobby/EmptyState.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { prompt } from '../ui/confirm.svelte.js';
	import Popover from './Popover.svelte';
	import HelpButton from './HelpButton.svelte';
	import Cover from './Cover.svelte';
	import { LIMITS, titleOf } from './model.js';
	import { answerInvitation, attempt, book, filter, find, invitations, joinByLink, licensed, loadInvitations, loadList, myUnlisted, nav, register, showLicense } from './store.svelte.js';

	let { lead } = $props();
	let query = $state('');
	const SORTS = [
		{ value: 'popular', label: t('play.malmoi.list.sortPopular') },
		{ value: 'recent', label: t('play.malmoi.list.sortRecent') },
		{ value: 'words', label: t('play.malmoi.list.sortWords') }
	];
	const by = { popular: (a, b) => b.registrants - a.registrants, recent: (a, b) => b.updatedAt - a.updatedAt, words: (a, b) => b.wordCount - a.wordCount };
	// the server says what each tab holds (둘러보기: public ones; 내 등록: the ones I registered; 내 편집: the ones I made)
	const pool = $derived(book.scopes[nav.list].map(find).filter(Boolean));
	$effect(() => {
		const scope = nav.list;
		void attempt(() => loadList(scope), toast);
	});
	$effect(() => {
		if (nav.list === 'mine') void loadInvitations().catch(() => {});
	});
	async function answer(item, accept) {
		if (await attempt(() => answerInvitation(item.id, accept), toast)) toast(accept ? t('play.malmoi.list.nowEditor', { title: item.title }) : t('play.malmoi.list.declined'), { kind: accept ? 'success' : 'info' });
	}
	// a search starts when the tab, a registration or the like has changed what the server holds
	$effect(() => {
		void book.registered.length;
		if (nav.list === 'registered') void attempt(() => loadList('registered'), toast);
	});
	const shown = $derived(
		pool
			.filter((d) => !filter.only || d.kind === filter.only)
			.filter((d) => !filter.unregistered || !book.registered.includes(d.id))
			.filter((d) => !query.trim() || [titleOf(d), d.desc, ...d.preview].some((s) => s.includes(query.trim())))
			.toSorted(by[filter.sort])
	);
	const active = $derived((filter.only ? 1 : 0) + (filter.unregistered ? 1 : 0));
	const open = (id) => Object.assign(nav, { view: 'detail', id, tab: 'words' });
	async function toggle(d) {
		const on = !book.registered.includes(d.id);
		if (!(await attempt(() => register(d.id, on), toast))) return;
		toast(on ? t('play.malmoi.list.registeredToast', { title: titleOf(d) }) : t('play.malmoi.list.unregisteredToast', { title: titleOf(d) }), { kind: on ? 'success' : 'info' });
	}
	async function byLink() {
		const link = await prompt({ message: t('play.malmoi.list.linkPrompt'), max: 200, ok: t('play.malmoi.common.register') });
		const token = link?.trim().match(/[?&]join=([0-9a-f]+)/)?.[1] ?? link?.trim().match(/^[0-9a-f]{24}$/)?.[0];
		if (!link?.trim()) return;
		if (!token) return toast(t('play.malmoi.list.notLink'), { kind: 'error' });
		const id = await attempt(() => joinByLink(token), toast);
		if (id) toast(t('play.malmoi.common.registeredToast'), { kind: 'success' });
	}
	function make() {
		if (myUnlisted() >= LIMITS.unlisted) return toast(t('play.malmoi.list.unlistedLimit', { n: LIMITS.unlisted }), { kind: 'error' });
		Object.assign(nav, { view: 'form', id: '' });
	}
</script>

<!-- One header row, as on the other pages: back and tabs, then search, 정렬 및 필터, 새 말모이 and 도움말 (a second row where they don't fit, as on a phone). -->
<div class="relative z-20 flex shrink-0 flex-wrap items-center gap-2 px-3 py-2">
	{@render lead?.()}
	<div class="min-w-0">
		<PillTabs bind:value={nav.list} idPrefix="malmoi-list" label={t('play.malmoi.list.tabsLabel')} tabs={[{ id: 'browse', icon: 'travel_explore', label: t('play.malmoi.list.browse') }, { id: 'registered', icon: 'bookmark', label: t('play.malmoi.list.registered'), count: book.registered.length }, { id: 'mine', icon: 'edit_note', label: t('play.malmoi.list.mine') }]} />
	</div>
	<div class="flex min-w-0 items-center justify-end gap-2 max-md:w-full md:min-w-[34rem] md:flex-1">
		<label class="kk-input flex h-11 min-w-0 flex-1 items-center gap-2 rounded-xl px-3 xl:max-w-64">
			<Icon name="search" size={20} />
			<input bind:value={query} type="search" placeholder={t('play.malmoi.list.searchHint')} aria-label={t('play.malmoi.list.searchLabel')} class="min-w-0 flex-1 bg-transparent outline-none" />
		</label>
		<Popover label={t('play.malmoi.list.filter')} buttonClass="max-md:min-w-11 max-md:justify-center max-md:px-2.5">
			{#snippet button()}
				<Icon name="tune" /><span class="max-md:hidden">{t('play.malmoi.list.filter')}</span>
				{#if active}<span class="rounded-full bg-(--kk-accent-soft) px-2 text-xs font-extrabold text-(color:--kk-accent-strong)">{active}</span>{/if}
			{/snippet}
			<p class="pb-1 text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.list.sort')}</p>
			<RadioGroup label={t('play.malmoi.list.sort')} bind:value={filter.sort} options={SORTS} />
			<hr class="my-1.5 border-(color:--kk-line)" />
			<p class="pb-1 text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.list.filterHead')}</p>
			<div class="flex flex-col">
				<Checkbox checked={filter.only === 'ext'} onchange={(v) => (filter.only = v ? 'ext' : '')}>{t('play.malmoi.list.onlyExt')}</Checkbox>
				<Checkbox checked={filter.only === 'new'} onchange={(v) => (filter.only = v ? 'new' : '')}>{t('play.malmoi.list.onlyNew')}</Checkbox>
				<Checkbox bind:checked={filter.unregistered}>{t('play.malmoi.list.hideRegistered')}</Checkbox>
			</div>
		</Popover>
		<button type="button" aria-label={t('play.malmoi.form.newTitle')} onclick={make} class="kk-btn kk-btn-primary inline-flex h-11 min-w-11 shrink-0 items-center justify-center gap-1.5 px-0 text-sm md:px-3.5"><Icon name="add" size={20} /><span class="max-md:hidden">{t('play.malmoi.form.newTitle')}</span></button>
		<HelpButton topic="list" />
	</div>
</div>

<div id="malmoi-list-panel-{nav.list}" role="tabpanel" aria-labelledby="malmoi-list-tab-{nav.list}" class="min-h-0 flex-1 overflow-y-auto px-3">
	<div class="flex flex-col gap-3 pb-20">
		{#if nav.list === 'registered'}
			<button type="button" onclick={byLink} class="kk-btn inline-flex items-center gap-1.5 self-start px-4 text-sm"><Icon name="link" size={20} />{t('play.malmoi.list.byLink')}</button>
		{/if}
		{#if nav.list === 'mine' && invitations.items.length}
			<ul class="kk-card flex flex-col divide-y divide-(--kk-line)">
				{#each invitations.items as item (item.id)}
					<li class="flex flex-wrap items-center gap-2 px-4 py-2.5"><span class="min-w-0 flex-1 text-sm"><b>{item.title}</b> <span class="text-xs text-(color:--kk-muted)">{t('play.malmoi.list.invitedBy', { name: item.owner })}</span></span><button type="button" onclick={() => answer(item, false)} class="kk-btn min-h-9 px-3 text-sm">{t('play.malmoi.list.decline')}</button><button type="button" onclick={() => answer(item, true)} class="kk-btn kk-btn-primary min-h-9 px-3 text-sm">{t('play.malmoi.list.accept')}</button></li>
				{/each}
			</ul>
		{/if}
		{#if nav.list === 'mine' && licensed()}
			<button type="button" onclick={() => showLicense()} class="kk-btn inline-flex items-center gap-1.5 self-start px-4 text-sm"><Icon name="badge" size={20} />{t('play.malmoi.card.myLicense')}</button>
		{/if}
		{#if !shown.length}
			<div class="kk-card"><EmptyState compact size={130} title={t('play.malmoi.list.empty')} /></div>
		{:else}
			<ul class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,19rem),1fr))] gap-3">
				{#each shown as d (d.id)}
					{@const on = book.registered.includes(d.id)}
					<li class="min-w-0">
						<div class="kk-card relative flex h-full min-w-0 gap-3 p-3">
							<Cover cover={d.cover} size={72} />
							<div class="flex min-w-0 flex-1 flex-col gap-1">
								<!-- the title's button covers the card; the register button lies over it -->
								<button type="button" onclick={() => open(d.id)} class="min-w-0 text-left text-base leading-tight font-black break-keep after:absolute after:inset-0 after:rounded-[inherit] focus-visible:after:outline-2 focus-visible:after:outline-(--kk-accent)">{d.title}</button>
								<p class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-bold text-(color:--kk-muted)">
									{#if d.kind === 'ext'}<span class="kk-chip">{t('play.malmoi.common.extPackBadge')}</span>{/if}
									{#if d.state === 'unlisted'}<span role="img" aria-label={t('play.malmoi.common.unlisted')} title={t('play.malmoi.common.unlisted')}><Icon name="lock" size={16} fill /></span>{/if}
									<span class="tabular-nums">{t('play.malmoi.detail.wordsOnly', { n: d.wordCount.toLocaleString() })}</span><span class="tabular-nums">{t('play.malmoi.detail.registrants', { n: d.registrants.toLocaleString() })}</span>
								</p>
								<p class="truncate text-xs text-(color:--kk-muted)">{d.preview.join(', ') || t('play.malmoi.list.noWords')}</p>
								<button type="button" aria-pressed={on} onclick={() => toggle(d)} class="kk-btn relative z-10 mt-auto inline-flex min-h-9 items-center gap-1 self-end px-3 text-sm {on ? '' : 'kk-btn-primary'}"><Icon name={on ? 'check' : 'add'} size={18} />{on ? t('play.malmoi.common.registered') : t('play.malmoi.common.register')}</button>
							</div>
						</div>
					</li>
				{/each}
			</ul>
		{/if}
	</div>
</div>
