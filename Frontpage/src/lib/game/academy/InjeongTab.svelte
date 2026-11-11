<script>
	import { t } from '../i18n.svelte.js';
	import { academyApi, friendlyError } from './api.js';
	import Select from '../ui/Select.svelte';
	import ThemePicker from '../ui/ThemePicker.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import Icon from '../lobby/Icon.svelte';
	import Dialog from '../lobby/Dialog.svelte';
	import WordCard from './WordCard.svelte';
	import GameIcon from '../lobby/GameIcon.svelte';
	import { NON_TOPIC_THEMES } from './themes.js';


	let { config = $bindable(), meta = null } = $props();
	let criterion = $state('START');
	let edgeChar = $state('');
	let exactWord = $state('');
	let theme = $state('');
	let mission = $state('');
	let loading = $state(false);
	let error = $state('');
	let response = $state(null);
	let confirmOpen = $state(false);

	const tokenCost = $derived(criterion === 'WORD' ? 0 : mission.trim() ? 2 : 1);
	const position = $derived(t(criterion === 'END' ? 'play.academy.injeong.endChar' : 'play.academy.injeong.startChar'));
	const criteria = $derived(['START', 'END', 'THEME', 'WORD'].map((v) => ({ value: v, label: t(`play.academy.injeong.crit.${v}`) })));
	const langs = $derived(['ko', 'en'].map((v) => ({ value: v, label: t(`play.academy.rule.${v}`) })));
	const value = $derived(criterion === 'THEME' ? theme : criterion === 'WORD' ? exactWord.trim() : edgeChar.trim());
	const ready = $derived(criterion === 'THEME' ? !!theme : criterion === 'WORD' ? !!exactWord.trim() : edgeChar.trim().length === 1);
	const what = $derived(
		criterion === 'THEME' ? t('play.academy.injeong.confirmThemeOf', { theme: meta?.themes?.[theme] ?? theme }) : t(criterion === 'END' ? 'play.academy.injeong.confirmTo' : 'play.academy.injeong.confirmFrom', { char: edgeChar })
	);

	function pick(v) {
		criterion = v;
		error = '';
		response = null;
	}
	function prepare(e) {
		e.preventDefault();
		if (!ready || (criterion !== 'WORD' && mission.trim() && mission.trim().length !== 1)) {
			error = criterion === 'THEME' ? t('play.academy.injeong.needTheme') : criterion === 'WORD' ? t('play.academy.injeong.needWord') : t('play.academy.injeong.needChars', { position });
			return;
		}
		error = '';
		if (criterion === 'WORD') return run(); // checking one word uses no token, so there is nothing to confirm
		confirmOpen = true;
	}
	async function run() {
		confirmOpen = false;
		loading = true;
		error = '';
		response = null;
		try {
			response = await academyApi.injeong(config.lang, criterion, value, mission.trim());
		} catch (cause) {
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
</script>

<!-- What it costs, on the button itself: the word-token icon and the number. Nothing is shown when it is free. -->
{#snippet cost(n)}
	{#if n}<span class="inline-flex items-center gap-1 rounded-full bg-black/20 py-0.5 pr-3 pl-1.5 text-base font-extrabold tabular-nums"><GameIcon name="wordToken" size={22} />{n}</span>{/if}
{/snippet}

<div class="mx-auto flex max-w-3xl flex-col gap-4">
	<form onsubmit={prepare} class="kk-card flex flex-col gap-4 p-4 md:p-5">
		<div class="flex items-center justify-between gap-3">
			<h2 class="flex items-center gap-2 text-xl font-black"><Icon name="verified" size={24} fill />{t('play.academy.tab.injeong')}</h2>
			<div class="w-32"><Select label={t('play.academy.rule.lang')} bind:value={config.lang} options={langs} onchange={() => ((response = null), (error = ''))} /></div>
		</div>
		<RadioGroup variant="chips" label={t('play.academy.tab.injeong')} value={criterion} options={criteria} onchange={pick} />
		{#if criterion === 'WORD'}
			<label class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.injeong.word')}</span><input bind:value={exactWord} maxlength="64" placeholder={t('play.academy.injeong.wordPlaceholder')} class="h-16 kk-input px-3 text-center text-2xl font-black" /></label>
		{:else}
			<div class="grid gap-3 sm:grid-cols-2">
				<label class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{criterion === 'THEME' ? t('play.academy.injeong.theme') : position}</span>
					{#if criterion === 'THEME'}<ThemePicker themes={meta?.themes ?? {}} exclude={NON_TOPIC_THEMES} bind:value={theme} label={t('play.academy.injeong.theme')} placeholder={t('play.academy.injeong.pickTheme')} />
					{:else}<input bind:value={edgeChar} maxlength="1" placeholder="가" class="h-16 kk-input px-3 text-center text-3xl font-black" />{/if}
				</label>
				<label class="flex min-w-0 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.injeong.mission')} · {t('play.academy.injeong.optional')}</span><input bind:value={mission} maxlength="1" placeholder="라" class="h-16 kk-input px-3 text-center text-3xl font-black" /></label>
			</div>
		{/if}
		<button class="kk-btn kk-btn-primary inline-flex min-h-13 items-center justify-center gap-2.5 text-base" disabled={loading || !ready} aria-label={tokenCost ? t('play.academy.injeong.runCost', { n: tokenCost }) : t('play.academy.injeong.run')}>
			<Icon name={loading ? 'progress_activity' : 'search'} size={22} class={loading ? 'animate-spin' : ''} />{t('play.academy.injeong.run')}
			{@render cost(tokenCost)}
		</button>
		{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-4 py-3 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>{/if}
	</form>

	{#if response}
		<section class="kk-card flex flex-col gap-3 p-4 md:p-5">
			<div class="flex flex-wrap items-center justify-between gap-2">
				<h3 class="text-lg font-black">{t('play.academy.injeong.resultCount', { n: response.items.length })}</h3>
				<div class="flex flex-wrap gap-1.5">{#if response.remainingDailyQueries != null}<span class="kk-chip">{t('play.academy.injeong.remaining', { n: response.remainingDailyQueries })}</span>{/if}</div>
			</div>
			{#if response.items.length}
				<div class="grid min-w-0 gap-3 md:grid-cols-2">{#each response.items as word (word.word)}<WordCard {word} themeNames={meta?.themes ?? {}} compact />{/each}</div>
			{:else}
				<p class="rounded-xl bg-(--kk-surface-2) px-4 py-6 text-center text-sm text-(color:--kk-muted)">{t('play.academy.injeong.none')}</p>
			{/if}
		</section>
	{/if}
</div>

<Dialog bind:open={confirmOpen} title={t('play.academy.injeong.confirmTitle')}>
	<div class="flex flex-col gap-4">
		<p class="text-sm leading-relaxed">{t('play.academy.injeong.confirmBody', { what, mission: mission ? t('play.academy.injeong.confirmMission', { m: mission }) : '', n: tokenCost })}</p>
		<div class="flex justify-end gap-2">
			<button type="button" class="kk-btn min-h-12 px-6" onclick={() => (confirmOpen = false)}>{t('play.academy.injeong.cancel')}</button>
			<button type="button" class="kk-btn kk-btn-primary inline-flex min-h-12 items-center gap-2.5 px-8" onclick={run}>{t('play.academy.injeong.use')}{@render cost(tokenCost)}</button>
		</div>
	</div>
</Dialog>

