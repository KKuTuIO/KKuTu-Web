<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';

	/** One dictionary word: how strong it is as a move, and what it is about. */
	let { word, themeNames = {}, compact = false, onopen } = $props();

	// How strong the word is as a move, shown as a coloured icon (the name is in its tooltip).
	const GRADE = {
		FINISH: { icon: 'flag', tone: 'bg-(--kk-danger-soft) text-(color:--kk-danger)' },
		DECISIVE: { icon: 'bolt', tone: 'bg-[#ffe9d6] text-[#d4600a] dark:bg-[#4a2a10] dark:text-[#ffa45c]' },
		VERY_HIGH: { icon: 'local_fire_department', tone: 'bg-(--kk-warn-soft) text-(color:--kk-warn)' },
		HIGH: { icon: 'trending_up', tone: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' },
		MEDIUM: { icon: 'balance', tone: 'bg-[color-mix(in_srgb,var(--kk-blue)_14%,var(--kk-surface))] text-(color:--kk-blue)' },
		LOW: { icon: 'verified_user', tone: 'bg-(--kk-surface-2) text-(color:--kk-muted)' }
	};
	const grade = $derived(word.attackGrade in GRADE ? word.attackGrade : 'LOW');

	function speak() {
		if (!globalThis.speechSynthesis) return;
		const u = new SpeechSynthesisUtterance(word.word);
		u.lang = /[가-힣]/.test(word.word) ? 'ko-KR' : 'en-US';
		globalThis.speechSynthesis.cancel();
		globalThis.speechSynthesis.speak(u);
	}
</script>

<!-- The whole card opens the definition: the word is a button whose click area is stretched over the card; the controls inside sit above it. -->
<article class="kk-card relative flex min-w-0 flex-col gap-2 text-left transition-transform hover:-translate-y-0.5 {compact ? 'p-3' : 'p-4'}">
	<div class="flex min-w-0 items-start justify-between gap-2">
		<div class="flex min-w-0 items-center gap-2.5">
			<Tooltip text={t(`play.academy.word.grade.${grade}`)}><span role="img" aria-label={t(`play.academy.word.grade.${grade}`)} class="grid size-9 shrink-0 place-items-center rounded-full {GRADE[grade].tone}"><Icon name={GRADE[grade].icon} size={20} fill /></span></Tooltip>
			<div class="min-w-0">
				<h3 class="min-w-0 {compact ? 'text-lg' : 'text-xl'} leading-tight font-black"><button type="button" onclick={() => onopen?.(word.word)} class="text-left break-all after:absolute after:inset-0 after:rounded-[inherit] focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-(--kk-accent)">{word.word}</button></h3>
				<p class="text-sm text-(color:--kk-muted)">{t('play.academy.word.meta', { length: word.length, from: word.startChar, to: word.nextChar })}</p>
			</div>
		</div>
		<div class="relative z-10 flex shrink-0 items-center gap-0.5">
			<Tooltip text={t('play.academy.word.defense', { n: word.defenseCount.toLocaleString() })}><span class="mr-1 inline-flex items-center gap-1 text-sm font-extrabold tabular-nums text-(color:--kk-muted)"><Icon name="shield" size={16} />{word.defenseCount.toLocaleString()}</span></Tooltip>
			<Tooltip text={t('play.academy.word.speak', { word: word.word })}><button type="button" onclick={speak} aria-label={t('play.academy.word.speak', { word: word.word })} class="grid size-9 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2)"><Icon name="volume_up" size={20} /></button></Tooltip>
		</div>
	</div>
	<div class="flex min-w-0 flex-wrap items-center gap-1.5">
		{#each word.themes.slice(0, compact ? 2 : 5) as theme (theme)}
			<span class="max-w-full truncate rounded-md bg-(--kk-surface-2) px-2 py-0.5 text-xs font-bold text-(color:--kk-muted)">{themeNames[theme] ?? theme}</span>
		{:else}
			<span class="text-xs text-(color:--kk-muted)">{t('play.academy.word.plain')}</span>
		{/each}
		<a href="https://kkutu.wiki/w/{encodeURIComponent(word.word)}" target="_blank" rel="noreferrer" class="relative z-10 ml-auto inline-flex shrink-0 items-center gap-0.5 text-xs font-extrabold text-(color:--kk-accent-strong) hover:underline">{t('play.academy.word.wiki')}<Icon name="open_in_new" size={14} /></a>
	</div>
</article>
