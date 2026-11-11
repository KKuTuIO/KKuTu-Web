import { t } from './i18n.svelte.js';
import { takeInjeongHint } from './protocol.js';
import { notify } from './ui/confirm.svelte.js';

/** The 어인정 nudge as a dialog: the words I typed are plain text slots, the rest of the text may use <b>. */
export function showInjeongHint(state) {
	const words = takeInjeongHint(state);
	if (!words) return;
	notify({ message: t('play.chat.injeongHint'), slots: { words: words.join(', ') } });
}
