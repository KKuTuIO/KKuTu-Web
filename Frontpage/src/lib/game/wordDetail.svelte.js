/** The one 단어 자세히 보기 screen: a word card asks for it, the host (WordDetail.svelte, mounted once on the play page) shows it. */
export const wordDetail = $state({ open: false, word: null, themeNames: {} });

export function openWordDetail(word, themeNames = {}) {
	wordDetail.word = word;
	wordDetail.themeNames = themeNames;
	wordDetail.open = true;
}
