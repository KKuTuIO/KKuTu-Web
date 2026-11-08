/**
 * `await confirm({message})` -> true / false, `await prompt({message, value, max})` -> the text, or null when cancelled, and
 * `await choose({message, options:[{value,label}]})` -> the chosen value, or null when cancelled.
 * One question at a time; asking again answers the previous one with "no".
 */
export const question = $state({ current: null });
let resolve = null;

function ask(q) {
	if (resolve) resolve(question.current?.input || question.current?.options ? null : false);
	return new Promise((res) => {
		resolve = res;
		question.current = q;
	});
}

export const confirm = ({ message, ok, cancel, title } = {}) => ask({ message, ok, cancel, title });
/**
 * A one-button notice: `await notify({message, slots, ok, title})`. The message goes through <Rich> (<b>, <u>, <i>, <br> only), and
 * every `{name}` in it is replaced by `slots.name` as plain text, so what a player typed can never become markup.
 */
export const notify = ({ message, slots, ok, title } = {}) => ask({ message, slots, ok, title, notice: true });
export const choose = ({ message, options, cancel } = {}) => ask({ message, options, cancel });
export const prompt = ({ message, value = '', max = 50, ok, cancel } = {}) => ask({ message, ok, cancel, input: true, value, max });

/** `value` is the typed text for a prompt, the chosen value for a choice; for a confirm, a plain yes/no. */
export function answer(yes, value) {
	const r = resolve;
	const q = question.current;
	resolve = null;
	r?.(q?.input || q?.options ? (yes ? value : null) : yes);
	// The question goes away only after the caller had the chance to ask a follow-up (a hand, then "sure?"): if the dialog closed and
	// the next question were set while it was closing, it would be cancelled by that very close.
	setTimeout(() => {
		if (question.current === q) question.current = null;
	}, 0);
}
