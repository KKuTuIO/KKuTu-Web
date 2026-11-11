/** The one 리오위키 reader window: a word card asks for an article, the host (WikiWindow.svelte, mounted once on the play page) shows it. */
export const wikiWindow = $state({ open: false, title: '' });

export function openWiki(title) {
	wikiWindow.title = title;
	wikiWindow.open = true;
}
