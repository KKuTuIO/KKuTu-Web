<script>
	import { t } from '../i18n.svelte.js';
	import { lookupWord } from '../dictionary.js';
	import { editUrl, wikiIntro } from '../wiki.js';
	import { openWiki } from '../wikiWindow.svelte.js';
	import { wordDetail } from '../wordDetail.svelte.js';
	import { GRADE, gradeOf } from '../academy/grades.js';
	import AdaptiveWindow from './AdaptiveWindow.svelte';
	import WordEntry from './WordEntry.svelte';
	import Icon from './Icon.svelte';

	/**
	 * Everything the game knows about one word of the 단어장, in one place: how it plays (the analysis), what the dictionary says it means
	 * and what 리오위키 says about it (or an invitation to write that article). The mini-dictionary stays small; this is the full view.
	 */
	const word = $derived(wordDetail.word);
	const grade = $derived(word ? gradeOf(word) : 'LOW');
	let meaning = $state(null);
	/** @type {Awaited<ReturnType<typeof wikiIntro>>|undefined} undefined = loading, null = the wiki can't be reached */
	let wiki = $state();
	$effect(() => {
		if (!wordDetail.open || !word) return;
		const w = word.word;
		meaning = null;
		wiki = undefined;
		let live = true;
		lookupWord(w).then((r) => live && (meaning = r));
		wikiIntro(w).then((r) => live && (wiki = r));
		return () => (live = false);
	});
	const themes = $derived((word?.themes ?? []).map((c) => wordDetail.themeNames[c] ?? c));
	const section = 'flex flex-col gap-2.5 rounded-xl border border-(--kk-line) bg-(--kk-surface) p-4';
	const h = 'text-sm font-black text-(color:--kk-muted)';
</script>

<AdaptiveWindow bind:open={wordDetail.open} id="word-detail" title={t('play.academy.detail.title')} width="36rem">
	{#snippet children(phone)}
		{#if word}
			<div class="flex flex-col gap-3 {phone ? 'p-3' : ''}">
				<h3 class="text-2xl font-black break-all">{word.word}</h3>

				<section class={section}>
					<h4 class={h}>{t('play.academy.detail.analysis')}</h4>
					<div class="flex items-start gap-3">
						<span role="img" aria-label={t(`play.academy.word.grade.${grade}`)} class="grid size-11 shrink-0 place-items-center rounded-full {GRADE[grade].tone}"><Icon name={GRADE[grade].icon} size={24} fill /></span>
						<div class="min-w-0">
							<p class="font-extrabold">{t(`play.academy.word.grade.${grade}`)}</p>
							<p class="text-sm leading-relaxed text-(color:--kk-muted)">{t(`play.academy.detail.grade.${grade}`)}</p>
						</div>
					</div>
					<dl class="grid grid-cols-3 gap-2 text-center">
						{#each [[t('play.academy.detail.length'), t('play.academy.detail.chars', { n: word.length })], [t('play.academy.detail.chain'), `${word.startChar} → ${word.nextChar}`], [t('play.academy.detail.defense'), t('play.academy.detail.count', { n: word.defenseCount.toLocaleString() })]] as [k, v]}
							<div class="rounded-lg bg-(--kk-surface-2) px-2 py-2"><dt class="text-xs text-(color:--kk-muted)">{k}</dt><dd class="font-extrabold">{v}</dd></div>
						{/each}
					</dl>
					<div class="flex flex-wrap gap-1.5">
						{#each themes as name (name)}<span class="rounded-md bg-(--kk-surface-2) px-2 py-0.5 text-xs font-bold text-(color:--kk-muted)">{name}</span>{:else}<span class="text-xs text-(color:--kk-muted)">{t('play.academy.word.plain')}</span>{/each}
					</div>
				</section>

				<section class={section}>
					<h4 class={h}>{t('play.academy.detail.dict')}</h4>
					{#if meaning}<WordEntry result={meaning} />{:else}<p class="py-4 text-center text-sm text-(color:--kk-muted)">{t('play.dict.searching')}</p>{/if}
				</section>

				<section class={section}>
					<h4 class={h}>{t('play.academy.detail.wiki')}</h4>
					{#if wiki === undefined}
						<div class="h-16 animate-pulse rounded-lg bg-(--kk-surface-2)" aria-hidden="true"></div>
					{:else if wiki?.exists}
						<div class="flex gap-3">
							{#if wiki.thumb}<img src={wiki.thumb} alt="" loading="lazy" referrerpolicy="no-referrer" class="size-20 shrink-0 rounded-lg bg-(--kk-surface-2) object-cover" />{/if}
							<p class="min-w-0 text-sm leading-relaxed break-words">{wiki.extract || t('play.academy.detail.wikiEmpty')}</p>
						</div>
						<button type="button" onclick={() => openWiki(wiki.title)} class="kk-btn inline-flex items-center gap-1 self-start px-3 py-1.5 text-sm font-extrabold"><Icon name="menu_book" size={18} />{t('play.academy.detail.wikiRead')}</button>
					{:else if wiki}
						<p class="text-sm leading-relaxed">{t('play.academy.detail.wikiNone')}<br /><span class="text-(color:--kk-muted)">{t('play.academy.detail.wikiCta')}</span></p>
						<a href={editUrl(word.word)} target="_blank" rel="noreferrer" class="kk-btn kk-btn-primary inline-flex items-center gap-1 self-start px-3 py-1.5 text-sm font-extrabold"><Icon name="edit_square" size={18} />{t('play.academy.detail.wikiWrite')}</a>
					{:else}
						<p class="text-sm text-(color:--kk-muted)">{t('play.wiki.error')}</p>
					{/if}
				</section>
			</div>
		{/if}
	{/snippet}
</AdaptiveWindow>
