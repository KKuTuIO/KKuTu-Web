<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import RewardMark from './RewardMark.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import CoverEditor from './CoverEditor.svelte';
	import HelpButton from './HelpButton.svelte';
	import { LIMITS, SHEET } from './model.js';
	import { attempt, create, find, nav, update } from './store.svelte.js';

	/** Making a new 말모이, or changing the name, description, 기준 and cover of mine (`nav.id`). */
	const old = find(nav.id);
	let title = $state(old?.title ?? '');
	let desc = $state(old?.desc ?? '');
	let criteria = $state(old ? { ...old.criteria } : { detail: '', include: '', source: '' });
	let cover = $state(old ? { ...old.cover } : { type: 'symbol', icon: 'menu_book', color: '#ffffff', bg: '#3553a0' });
	let { lead } = $props();
	let busy = $state(false);
	const ready = $derived(!!title.trim() && !!desc.trim() && !busy);
	const back = () => Object.assign(nav, { view: old ? 'detail' : 'list' });
	async function save(e) {
		e.preventDefault();
		if (!ready) return;
		const value = { title: title.trim(), desc: desc.trim(), criteria: $state.snapshot(criteria), cover: $state.snapshot(cover) };
		busy = true;
		if (old) {
			const ok = await attempt(() => update(old, value), toast);
			busy = false;
			if (!ok) return;
			toast(t('play.malmoi.form.saved'), { kind: 'success' });
			return back();
		}
		const id = await attempt(() => create(value), toast);
		busy = false;
		if (!id) return;
		toast(t('play.malmoi.form.created', { title: value.title }), { kind: 'success' });
		Object.assign(nav, { view: 'detail', id: String(id), tab: 'words' });
	}
	const FIELDS = [
		['detail', t('play.malmoi.form.fieldDetail'), t('play.malmoi.form.hintDetail')],
		['include', t('play.malmoi.criteria.include'), t('play.malmoi.form.hintInclude')],
		['source', t('play.malmoi.form.fieldSource'), t('play.malmoi.form.hintSource')]
	];
</script>

<div class="relative z-20 flex shrink-0 items-center gap-2 px-3 py-2">
	{@render lead()}
	<div class="ml-auto shrink-0"><HelpButton topic="form" /></div>
</div>

<div class="min-h-0 flex-1 overflow-y-auto px-3">
<form onsubmit={save} aria-label={old ? t('play.malmoi.form.editTitle') : t('play.malmoi.form.newTitle')} class="mx-auto flex w-full max-w-3xl flex-col gap-3 pb-20">

	<div class="kk-card flex flex-col gap-3 p-4">
		<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.form.name')}</span>
			<input bind:value={title} maxlength="24" autocomplete="off" placeholder={t('play.malmoi.form.titleHint')} class="h-11 kk-input px-3 font-extrabold" />
		</label>
		<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.malmoi.form.desc')}</span>
			<input bind:value={desc} maxlength="60" autocomplete="off" class="h-11 kk-input px-3" />
		</label>
	</div>

	<div class="kk-card flex flex-col gap-3 p-4">
		<h3 class="text-sm font-black">{t('play.malmoi.form.cover')}</h3>
		<CoverEditor bind:cover />
	</div>

	<div class="kk-card flex flex-col gap-3 p-4">
		<div class="flex flex-wrap items-center justify-between gap-2">
			<h3 class="text-sm font-black">{t('play.malmoi.form.criteria')}</h3>
			<a href={SHEET.common} target="_blank" rel="noreferrer" class="inline-flex items-center gap-1 text-xs font-extrabold text-(color:--kk-accent-strong) hover:underline">{t('play.malmoi.form.sheetLink')}<Icon name="open_in_new" size={16} /></a>
		</div>
		{#each FIELDS as [key, label, hint] (key)}
			<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{label}</span>
				<textarea bind:value={criteria[key]} maxlength="300" rows="2" placeholder={hint} class="kk-input w-full resize-y px-3 py-2"></textarea>
			</label>
		{/each}
	</div>

	{#if !old}
		<!-- every new one starts unlisted: public is applied for from its 관리 tab, once the words are there -->
		<div class="kk-card flex flex-col gap-2 p-4">
			<h3 class="text-sm font-black">{t('play.malmoi.form.scope')}</h3>
			<ul class="grid gap-2 sm:grid-cols-2">
				{#each [['unlisted', t('play.malmoi.common.unlisted'), t('play.malmoi.form.unlistedHint'), 0], ['public', t('play.malmoi.common.public'), t('play.malmoi.form.publicAfter', { n: LIMITS.publishWords }), 0.75]] as [id, label, hint, pay] (id)}
					<li class="flex flex-col gap-1 rounded-xl border-2 p-3 {id === 'unlisted' ? 'border-(color:--kk-accent) bg-(--kk-accent-soft)' : 'border-(color:--kk-line)'}" aria-current={id === 'unlisted' ? 'true' : undefined}>
						<b class="inline-flex items-center gap-1.5 font-extrabold">{#if id === 'unlisted'}<Icon name="lock" size={18} fill />{/if}{label}</b><span class="text-xs leading-relaxed text-(color:--kk-muted)">{hint}</span><RewardMark share={pay} size={18} />
					</li>
				{/each}
			</ul>
		</div>
	{/if}

	<div class="flex justify-end gap-2">
		<button type="button" onclick={back} class="kk-btn min-h-12 px-6">{t('play.malmoi.common.cancel')}</button>
		<button class="kk-btn kk-btn-primary min-h-12 px-8" disabled={!ready}>{old ? t('play.malmoi.common.save') : t('play.malmoi.form.create')}</button>
	</div>
</form>
</div>
