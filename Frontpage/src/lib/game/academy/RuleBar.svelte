<script>
	import { t } from '../i18n.svelte.js';
	import Select from '../ui/Select.svelte';
	import Checkbox from '../ui/Checkbox.svelte';
	import Icon from '../lobby/Icon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import ThemePicker from '../ui/ThemePicker.svelte';
	import { NON_TOPIC_THEMES } from './themes.js';

	/** The dictionary rules every tool of the academy shares: language, dictionary, direction and the details. */
	let { config = $bindable(), meta = null } = $props();
	let advanced = $state(false);
	let open = $state(false); // phone: the rules are one line until opened

	const FLAGS = ['includeLoanword', 'includeSpaced', 'includeDialect', 'includeOld', 'includeCultural', 'includeKung'];
	const langs = $derived(['ko', 'en'].map((v) => ({ value: v, label: t(`play.academy.rule.${v}`) })));
	const dictionaries = $derived((meta?.dictionaries ?? [{ value: 'BASIC' }, { value: 'STANDARD' }, { value: 'COMBINED' }]).map((d) => ({ value: d.value, label: i18nDict(d) })));
	const directions = $derived([
		{ value: 'FORWARD', label: t('play.academy.rule.forward') },
		{ value: 'REVERSE', label: t('play.academy.rule.reverse') }
	]);
	function i18nDict(d) {
		const key = `play.academy.rule.dict.${d.value}`;
		const text = t(key);
		return text === key ? (d.label ?? d.value) : text;
	}
	const num = (e, fallback) => (Number.isFinite(Number(e.currentTarget.value)) && e.currentTarget.value !== '' ? Number(e.currentTarget.value) : fallback);
</script>

<section aria-label={t('play.academy.rule.label')} class="kk-card flex flex-col gap-3 p-3">
	<button type="button" aria-expanded={open} onclick={() => (open = !open)} class="flex min-h-9 items-center gap-2 text-left text-sm font-extrabold lg:hidden">
		<Icon name="rule" size={20} /><span class="min-w-0 flex-1 truncate">{t('play.academy.rule.label')}<span class="ml-2 font-bold text-(color:--kk-muted)">{langs.find((l) => l.value === config.lang)?.label} · {dictionaries.find((d) => d.value === config.dictionary)?.label}</span></span>
		<Icon name={open ? 'keyboard_arrow_up' : 'keyboard_arrow_down'} size={22} />
	</button>
	<div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-[repeat(3,minmax(0,1fr))_auto] {open ? '' : 'max-lg:hidden'}">
		<div class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.rule.lang')}</span><Select label={t('play.academy.rule.lang')} bind:value={config.lang} options={langs} /></div>
		<div class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.rule.dictionary')}</span><Select label={t('play.academy.rule.dictionary')} bind:value={config.dictionary} options={dictionaries} /></div>
		<div class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.rule.direction')}</span><Select label={t('play.academy.rule.direction')} bind:value={config.direction} options={directions} /></div>
		<div class="flex items-end gap-2">
			<Checkbox bind:checked={config.duum} disabled={config.lang !== 'ko'}>{t('play.academy.rule.duum')}</Checkbox>
			<Tooltip text={t('play.academy.rule.advanced')}><button type="button" aria-expanded={advanced} aria-label={t('play.academy.rule.advanced')} onclick={() => (advanced = !advanced)} class="kk-btn grid size-11 place-items-center p-0 {advanced ? 'border-(color:--kk-accent)' : ''}"><Icon name="tune" size={22} /></button></Tooltip>
		</div>
	</div>
	{#if advanced}
		<div class="flex flex-col gap-3 border-t border-(color:--kk-line) pt-3 {open ? '' : 'max-lg:hidden'}">
			<div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
				<label class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.rule.minLength')}</span><input type="number" min="1" max="64" value={config.minLength} oninput={(e) => (config.minLength = num(e, 2))} class="h-11 kk-input px-3" /></label>
				<label class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.rule.maxLength')}</span><input type="number" min="1" max="128" value={config.maxLength} oninput={(e) => (config.maxLength = num(e, 64))} class="h-11 kk-input px-3" /></label>
				<div class="flex min-w-0 flex-col gap-1 sm:col-span-2"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.rule.themes')}</span><ThemePicker multiple themes={meta?.themes ?? {}} exclude={NON_TOPIC_THEMES} bind:value={config.themes} label={t('play.academy.rule.themes')} placeholder={t('play.theme.all')} /></div>
			</div>
			<div class="flex flex-wrap gap-x-5">
				{#each FLAGS as f (f)}<Checkbox bind:checked={config[f]}>{t(`play.academy.rule.flag.${f}`)}</Checkbox>{/each}
			</div>
		</div>
	{/if}
</section>
