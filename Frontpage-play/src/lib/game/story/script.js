/**
 * Dialogue scripts: plain data, so a tutorial lesson and a story chapter are written the same way.
 *
 *   { id, version, cast: ['haksamo'], nodes: [ node, ... ] }
 *
 * A node runs when the one before it is done (or when another node says `goto` / `next` to it). Its kind is the first of these it has:
 *   say     {who, text, params?, mood?}  a line (text is a locale key); waits for 다음. `focus` (a selector, or null) points at part of the page.
 *   choice  [{text, goto}]               answers to pick from (with `say` for the question)
 *   wait    'event'                      blocks until the host sends that event (`say` may tell what to do)
 *   stage   'name'                       the host runs a part of the game (it may answer with the id of the node to go to)
 *   do      'name' (+ args)              the host does something (open a dialog...), then the script goes on at once
 *   goto    'id'                         jump
 *   end     true                         the script is over
 * Every node may have an `id` (needed to be a target) and a `next` (default: the node below it), and `at: 'top' | 'bottom'` (where the bubble sits).
 */

// `say` goes last: a node with a line and a `wait` / `choice` is a wait / choice that has something to say
const KINDS = ['choice', 'wait', 'stage', 'do', 'goto', 'end', 'say'];
export const kindOf = (node) => KINDS.find((k) => k in node) ?? null;

/** @returns {string[]} what is wrong with the script (empty: fine) */
export function checkScript(script) {
	const errors = [];
	const ids = new Set();
	script.nodes.forEach((n, i) => {
		if (!kindOf(n)) errors.push(`node ${i}: no say / choice / wait / stage / do / goto / end`);
		if (n.id !== undefined) {
			if (ids.has(n.id)) errors.push(`node ${i}: id "${n.id}" is used twice`);
			ids.add(n.id);
		}
	});
	const known = (id) => ids.has(id);
	script.nodes.forEach((n, i) => {
		for (const to of [n.next, n.goto, ...(n.choice ?? []).map((c) => c.goto)])
			if (to !== undefined && !known(to)) errors.push(`node ${i}: goes to "${to}", which does not exist`);
		const who = n.say?.who;
		if (who && !(script.cast ?? []).includes(who)) errors.push(`node ${i}: ${who} is not in the cast`);
	});
	return errors;
}

/** Every locale key the script shows (for the test that they all exist). */
export function scriptKeys(script) {
	const keys = new Set();
	for (const n of script.nodes) {
		if (n.say?.text) keys.add(n.say.text);
		for (const c of n.choice ?? []) if (c.text) keys.add(c.text);
	}
	return [...keys];
}
