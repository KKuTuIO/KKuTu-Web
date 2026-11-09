<script>
	import { t } from '../i18n.svelte.js';
	import { family } from '../familyStore.svelte.js';
	import { FAMILY_CREATE_COST, FAMILY_CREATE_LEVEL, FAMILY_INTRO_MAX_BYTES, FAMILY_NAME_MAX, clampIntro, introBytes, nameProblem } from '../familyModel.js';
	import { toast } from '../ui/toast.svelte.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Checkbox from '../ui/Checkbox.svelte';

	/** 패밀리 만들기: a name, an intro (150 bytes) and whether it takes applications. The cost and the level it needs are shown up front. */
	let { store, open = $bindable(false) } = $props();
	let name = $state('');
	let intro = $state('');
	let recruiting = $state(true);
	let busy = $state(false);
	let error = $state('');

	$effect(() => {
		if (!open) return;
		name = '';
		intro = '';
		recruiting = true;
		error = '';
	});

	const me = $derived({ lv: store.me?.lv ?? 0, money: store.me?.money ?? 0 });
	const blocker = $derived(family.createBlocker(me));
	const problem = $derived(name.trim() ? nameProblem(name) : 'short');
	const bytes = $derived(introBytes(intro));
	const reqs = $derived([
		{ ok: me.lv >= FAMILY_CREATE_LEVEL, text: t('play.family.req.level', { level: FAMILY_CREATE_LEVEL }) },
		{ ok: me.money >= FAMILY_CREATE_COST, text: t('play.family.req.cost', { cost: FAMILY_CREATE_COST.toLocaleString() }) }
	]);

	async function submit(e) {
		e.preventDefault();
		if (busy || blocker || problem) return;
		busy = true;
		const r = await family.create({ name, intro, recruiting });
		busy = false;
		if (!r.ok) return void (error = t(`play.family.createError.${r.reason}`));
		toast(t('play.family.created', { name: name.trim() }), { kind: 'success' });
		open = false;
	}
</script>

<Dialog bind:open title={t('play.family.create')}>
	<form onsubmit={submit} class="flex min-w-0 flex-col gap-4">
		<ul class="flex flex-col gap-1.5 rounded-xl bg-(--kk-surface-2) p-3 text-sm">
			{#each reqs as r (r.text)}
				<li class="flex items-center gap-2 {r.ok ? '' : 'text-(color:--kk-danger)'}"><Icon name={r.ok ? 'check_circle' : 'cancel'} size={18} fill />{r.text}</li>
			{/each}
		</ul>

		<label class="flex flex-col gap-1.5">
			<span class="text-sm font-extrabold">{t('play.family.name')}</span>
			<span class="kk-input flex h-11 items-center px-3"><input bind:value={name} maxlength={FAMILY_NAME_MAX} placeholder={t('play.family.namePlaceholder')} autocomplete="off" class="min-w-0 flex-1 bg-transparent outline-none" /></span>
			<span class="text-xs {name.trim() && problem ? 'text-(color:--kk-danger)' : 'text-(color:--kk-muted)'}">{name.trim() && problem ? t(`play.family.nameError.${problem}`, { max: FAMILY_NAME_MAX }) : t('play.family.nameHint', { max: FAMILY_NAME_MAX })}</span>
		</label>

		<label class="flex flex-col gap-1.5">
			<span class="flex items-baseline justify-between text-sm font-extrabold">{t('play.family.intro')}<span class="text-xs font-bold tabular-nums text-(color:--kk-muted)">{bytes}/{FAMILY_INTRO_MAX_BYTES}</span></span>
			<textarea value={intro} oninput={(e) => (intro = clampIntro(e.currentTarget.value))} rows="3" placeholder={t('play.family.introPlaceholder')} class="kk-input resize-none px-3 py-2 text-sm outline-none"></textarea>
		</label>

		<Checkbox bind:checked={recruiting}>{t('play.family.recruiting')}</Checkbox>

		{#if error}<p role="alert" class="text-sm text-(color:--kk-danger)">{error}</p>{/if}

		<div class="flex justify-end gap-2">
			<button type="button" onclick={() => (open = false)} class="kk-btn min-h-11 px-4">{t('play.dialog.close')}</button>
			<button class="kk-btn kk-btn-primary min-h-11 px-5" disabled={busy || !!blocker || !!problem}>{t('play.family.createDo')}</button>
		</div>
	</form>
</Dialog>
