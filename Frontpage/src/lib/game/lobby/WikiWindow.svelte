<script>
	import { t } from '../i18n.svelte.js';
	import { articleUrl, loadArticle, wikiOrigin } from '../wiki.js';
	import { ID_PREFIX, sanitizeWikiHtml } from '../wikiHtml.js';
	import { wikiWindow } from '../wikiWindow.svelte.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import AdaptiveWindow from './AdaptiveWindow.svelte';
	import Icon from './Icon.svelte';

	/**
	 * A preview of a 리오위키 article (not a viewer): MediaWiki's own rendering of it, sanitised (see wikiHtml.js), with a table of contents.
	 * Footnotes and the contents scroll inside the preview; every link leaves it: to the wiki itself without asking, to any other site
	 * only after the same question as a link in a profile. "새 탭에서 열기" is the way to the full article.
	 */
	/** @type {{title:string, html:string, sections:{number:string,text:string,level:number,anchor:string}[]}|null} */
	let article = $state(null);
	let state = $state('loading'); // loading | ready | missing | error
	let box = $state();
	let tocFolded = $state(false);
	let seq = 0;
	const shownTitle = $derived(article?.title ?? wikiWindow.title);
	const byId = (anchor) => box?.querySelector(`[id="${CSS.escape(ID_PREFIX + anchor)}"]`);

	async function load(title) {
		const mine = ++seq;
		article = null;
		state = 'loading';
		try {
			const a = await loadArticle(title);
			if (mine !== seq) return;
			if (!a) return (state = 'missing');
			article = { title: a.title, html: sanitizeWikiHtml(a.html, wikiOrigin()), sections: a.sections };
			tocFolded = a.sections.length > 12;
			state = 'ready';
		} catch {
			if (mine === seq) state = 'error';
		}
	}

	$effect(() => {
		if (wikiWindow.open) load(wikiWindow.title);
	});

	// Headings get their number (the same as in the contents); pressing it jumps back up to the contents.
	$effect(() => {
		const sections = state === 'ready' ? article?.sections : null;
		if (!box || !sections) return;
		article.html;
		queueMicrotask(() => {
			for (const s of sections) {
				const h = byId(s.anchor);
				if (!h || h.querySelector('.hnum')) continue;
				const n = document.createElement('a');
				n.className = 'hnum';
				n.href = '#toc';
				n.textContent = `${s.number}.`;
				h.prepend(n, ' ');
			}
		});
	});

	async function leave(href) {
		let ours = false;
		try {
			ours = new URL(href).origin === new URL(wikiOrigin()).origin;
		} catch {
			/* not an address */
		}
		if (!ours && !(await confirm({ message: `${t('play.link.confirm')}\n${href}`, title: t('play.confirm.ok'), ok: t('play.link.yes'), cancel: t('play.link.no') }))) return;
		window.open(href, '_blank', 'noopener,noreferrer');
	}

	function click(e) {
		const a = e.target.closest?.('a');
		if (!a) return;
		const href = a.getAttribute('href');
		if (href === '#toc') {
			e.preventDefault();
			box?.querySelector('.wiki-toc')?.scrollIntoView({ block: 'start' });
		} else if (href?.startsWith('#')) {
			e.preventDefault();
			box?.querySelector(`[id="${CSS.escape(href.slice(1))}"]`)?.scrollIntoView({ block: 'center' });
		} else if (href) {
			e.preventDefault();
			leave(href);
		}
	}
</script>

{#snippet newTab(cls)}
	<a href={articleUrl(shownTitle)} target="_blank" rel="noreferrer" class="inline-flex items-center gap-1 text-xs font-extrabold text-(color:--kk-accent-strong) hover:underline {cls}">{t('play.wiki.newtab')}<Icon name="open_in_new" size={14} /></a>
{/snippet}

<AdaptiveWindow bind:open={wikiWindow.open} id="wiki" title={shownTitle} width="34rem">
	{#snippet actions()}{@render newTab('mr-2 px-2 py-2')}{/snippet}
	{#snippet children(phone)}
		<div class="flex min-h-0 flex-col gap-2 {phone ? 'flex-1' : ''}">
			<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
			<div bind:this={box} onclick={click} class="min-h-0 overflow-y-auto bg-(--kk-surface) {phone ? 'flex-1 px-3 py-2' : 'h-[min(60dvh,38rem)] rounded-lg border border-(--kk-line) p-4'}" aria-live="polite" aria-busy={state === 'loading'}>
				{#if state === 'ready'}
					{#if article.sections.length}
						<nav class="wiki-toc" class:folded={tocFolded} aria-label={t('play.wiki.toc')}>
							<button type="button" class="wiki-toc-head" aria-expanded={!tocFolded} onclick={() => (tocFolded = !tocFolded)}>{t('play.wiki.toc')}<Icon name={tocFolded ? 'expand_more' : 'expand_less'} size={22} /></button>
							{#if !tocFolded}
								<ol>
									{#each article.sections as s (s.anchor + s.number)}
										<li style:padding-left="{(s.level - 1) * 1}rem"><a href="#{ID_PREFIX + s.anchor}"><span class="num">{s.number}.</span> {s.text}</a></li>
									{/each}
								</ol>
							{/if}
						</nav>
					{/if}
					<div class="wiki-reader">{@html article.html}</div>
				{:else if state === 'loading'}
					<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.wiki.loading')}</p>
				{:else if state === 'missing'}
					<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.wiki.missing')}</p>
				{:else}
					<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.wiki.error')}</p>
				{/if}
			</div>
			{#if !phone}{@render newTab('self-end')}{/if}
		</div>
	{/snippet}
</AdaptiveWindow>

<style>
	:global(.wiki-toc) { margin: 0 0 1rem; border: 1px solid var(--kk-line-strong); border-radius: 0.5rem; background: var(--kk-surface); }
	:global(.wiki-toc-head) { display: flex; width: 100%; align-items: center; justify-content: space-between; padding: 0.55rem 0.9rem; font-weight: 800; }
	:global(.wiki-toc ol) { padding: 0 0.9rem 0.7rem; font-size: 0.88rem; line-height: 1.8; list-style: none; }
	:global(.wiki-toc a) { color: var(--kk-text); }
	:global(.wiki-toc .num), :global(.wiki-reader .hnum) { color: var(--kk-blue); }
	:global(.wiki-reader .hnum) { margin-right: 0.1em; }
	:global(.wiki-reader) { line-height: 1.7; font-size: 0.92rem; overflow-wrap: anywhere; container-type: inline-size; }
	/* Templates float their boxes (infoboxes) inline; in a narrow reader they would squeeze the text, so they stack. */
	@container (max-width: 36rem) {
		:global(.wiki-reader [style*='float']) { float: none !important; margin-inline: auto !important; max-width: 100% !important; }
	}
	:global(.wiki-reader::after) { content: ''; display: block; clear: both; }
	:global(.wiki-reader p) { margin: 0.7em 0; }
	:global(.wiki-reader h2), :global(.wiki-reader h3), :global(.wiki-reader h4) { font-weight: 800; line-height: 1.35; margin: 1.4em 0 0.6em; }
	:global(.wiki-reader h2) { font-size: 1.25rem; padding-bottom: 0.2em; border-bottom: 1px solid var(--kk-line-strong); }
	:global(.wiki-reader h3) { font-size: 1.1rem; }
	:global(.wiki-reader h4) { font-size: 1rem; }
	:global(.wiki-reader .mw-heading) { margin: 0; }
	:global(.wiki-reader a) { color: var(--kk-blue); text-decoration: none; }
	:global(.wiki-reader a:hover) { text-decoration: underline; }
	:global(.wiki-reader a.new) { color: var(--kk-danger); }
	:global(.wiki-reader ul), :global(.wiki-reader ol) { margin: 0.6em 0; padding-left: 1.5em; }
	:global(.wiki-reader ul) { list-style: disc; }
	:global(.wiki-reader ol) { list-style: decimal; }
	:global(.wiki-reader dd) { margin-left: 1.5em; }
	:global(.wiki-reader blockquote) { margin: 0.8em 0; padding: 0.5em 1em; border-left: 4px solid var(--kk-line-strong); background: var(--kk-surface-2); }
	:global(.wiki-reader code), :global(.wiki-reader pre) { font-size: 0.9em; background: var(--kk-surface-2); border-radius: 0.375rem; }
	:global(.wiki-reader code) { padding: 0.05em 0.35em; }
	:global(.wiki-reader pre) { padding: 0.7em 0.9em; overflow-x: auto; }
	:global(.wiki-reader hr) { border: 0; border-top: 1px solid var(--kk-line); margin: 1.2em 0; }
	:global(.wiki-reader table) { border-collapse: collapse; max-width: 100%; }
	:global(.wiki-reader table.wikitable) { margin: 0.8em 0; display: block; overflow-x: auto; }
	:global(.wiki-reader .wikitable th), :global(.wiki-reader .wikitable td) { border: 1px solid var(--kk-line-strong); padding: 0.35em 0.7em; }
	:global(.wiki-reader .wikitable th) { background: var(--kk-surface-2); font-weight: 800; }
	:global(.wiki-reader img) { max-width: 100%; height: auto; }
	:global(.wiki-reader .thumb) { margin: 0.8em 0; }
	:global(.wiki-reader .thumb.tright), :global(.wiki-reader .floatright) { float: right; clear: right; margin: 0.4em 0 0.8em 1em; }
	:global(.wiki-reader .thumb.tleft), :global(.wiki-reader .floatleft) { float: left; clear: left; margin: 0.4em 1em 0.8em 0; }
	:global(.wiki-reader .thumbinner) { border: 1px solid var(--kk-line); background: var(--kk-surface-2); border-radius: 0.5rem; padding: 0.4rem; font-size: 0.85rem; max-width: 100%; }
	:global(.wiki-reader .thumbcaption) { padding-top: 0.3rem; color: var(--kk-muted); }
	:global(.wiki-reader sup.reference) { font-size: 0.72em; line-height: 0; }
	:global(.wiki-reader .references) { font-size: 0.85rem; color: var(--kk-muted); }
	:global(.wiki-reader .hatnote) { font-size: 0.9em; color: var(--kk-muted); }
	:global(.wiki-reader .noprint), :global(.wiki-reader .mw-empty-elt), :global(.wiki-reader .mw-editsection) { display: none; }
	@media (max-width: 640px) {
		:global(.wiki-reader .thumb.tright), :global(.wiki-reader .thumb.tleft), :global(.wiki-reader .floatright), :global(.wiki-reader .floatleft) { float: none; margin-inline: auto; }
	}
</style>
