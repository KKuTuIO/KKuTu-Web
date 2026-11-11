<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { wikiSummary } from '../wiki.js';
	import { openWordDetail } from '../wordDetail.svelte.js';
	import { GRADE, gradeOf } from './grades.js';

	/** One dictionary word: how strong it is as a move, and what it is about. */
	let { word, themeNames = {}, compact = false } = $props();

	const grade = $derived(gradeOf(word));

	// What the wiki has on this word (null/undefined until known or when it can't be reached): a picture for the card's background.
	/** @type {import('../wiki.js').WikiSummary|null|undefined} */
	let wiki = $state();
	$effect(() => {
		const w = word.word;
		wiki = undefined;
		let live = true;
		wikiSummary(w).then((s) => live && (wiki = s));
		return () => (live = false);
	});
	const wikiThumb = $derived(wiki?.exists ? wiki.thumb : null);

	function speak() {
		if (!globalThis.speechSynthesis) return;
		const u = new SpeechSynthesisUtterance(word.word);
		u.lang = /[가-힣]/.test(word.word) ? 'ko-KR' : 'en-US';
		globalThis.speechSynthesis.cancel();
		globalThis.speechSynthesis.speak(u);
	}
</script>

<!-- The card itself does nothing when pressed: everything about the word is behind its 자세히 보기 button. -->
<article class="kk-card relative isolate flex min-w-0 flex-col gap-2 text-left {compact ? 'p-3' : 'p-4'}">
	{#if wikiThumb}<div aria-hidden="true" class="pointer-events-none absolute inset-y-0 right-0 -z-10 w-3/5 rounded-r-[inherit] bg-cover bg-center opacity-35 [mask-image:linear-gradient(to_left,black,transparent)]" style:background-image="url({wikiThumb})"></div>{/if}
	<div class="flex min-w-0 items-start justify-between gap-2">
		<div class="flex min-w-0 items-center gap-2.5">
			<Tooltip text={t(`play.academy.word.grade.${grade}`)}><span role="img" aria-label={t(`play.academy.word.grade.${grade}`)} class="grid size-9 shrink-0 place-items-center rounded-full {GRADE[grade].tone}"><Icon name={GRADE[grade].icon} size={20} fill /></span></Tooltip>
			<div class="min-w-0">
				<h3 class="min-w-0 {compact ? 'text-lg' : 'text-xl'} leading-tight font-black"><span class="break-all">{word.word}</span></h3>
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
		<button type="button" onclick={() => openWordDetail(word, themeNames)} class="relative z-10 ml-auto inline-flex shrink-0 items-center gap-0.5 text-xs font-extrabold text-(color:--kk-accent-strong) hover:underline">{t('play.academy.word.detail')}<Icon name="chevron_right" size={16} /></button>
	</div>
</article>
