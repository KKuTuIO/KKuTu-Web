/**
 * Private notes about other players (the legacy "메모"). Kept in this browser under the same key the legacy client
 * used, so notes made there show up here too. The server never sees them.
 */
const KEY = 'notes';
export const MAX_NOTE = 20;

function load() {
	try {
		const o = JSON.parse(localStorage.getItem(KEY) ?? 'null');
		return o && typeof o === 'object' ? o : {};
	} catch {
		return {};
	}
}

export const notes = $state(load());

/** Replace all notes (a settings file was opened). */
export function replaceNotes(all) {
	for (const k of Object.keys(notes)) delete notes[k];
	for (const [id, text] of Object.entries(all)) if (text) notes[id] = String(text).slice(0, MAX_NOTE);
	try {
		localStorage.setItem(KEY, JSON.stringify({ ...notes }));
	} catch {
		/* private mode */
	}
}

/** An empty text removes the note. */
export function setNote(id, text) {
	if (text) notes[id] = text.slice(0, MAX_NOTE);
	else delete notes[id];
	try {
		localStorage.setItem(KEY, JSON.stringify({ ...notes }));
	} catch {
		/* private mode */
	}
}
