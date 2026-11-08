/** Lesson 3: what each setting of the real "방 만들기" dialog does. The dialog is opened and closed for the player; nothing is created. */
const L = (id, extra = {}) => ({ say: { who: 'haksamo', text: `play.story.room.${id}`, mood: extra.mood }, ...extra, mood: undefined });

export default {
	id: 'room',
	version: 1,
	cast: ['haksamo'],
	nodes: [
		L('hi', { mood: 'happy', focus: null, at: 'bottom' }),
		{ do: 'openCreate' },
		L('mode', { focus: '[data-tour=create-mode]' }),
		L('options', { focus: '[data-tour=create-options]' }),
		L('title', { focus: '[data-tour=create-title]' }),
		L('password', { focus: '[data-tour=create-password]' }),
		L('limit', { focus: '[data-tour=create-limit]', at: 'top' }),
		L('round', { focus: '[data-tour=create-round]' }),
		L('time', { focus: '[data-tour=create-time]' }),
		L('submit', { focus: '[data-tour=create-submit]', mood: 'surprised' }),
		{ do: 'closeCreate', focus: null },
		L('bye', { mood: 'happy' }),
		{ do: 'complete' },
		{ end: true }
	]
};
