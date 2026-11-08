/** Lesson 1: a tour of the real lobby (the parts are found by their data-tour marks). */
const L = (id, extra = {}) => ({ say: { who: 'haksamo', text: `play.story.lobby.${id}`, mood: extra.mood }, ...extra, mood: undefined });

export default {
	id: 'lobby',
	version: 1,
	cast: ['haksamo'],
	nodes: [
		L('hi', { mood: 'happy', focus: null, at: 'bottom' }),
		L('rooms', { focus: '[data-tour=room-list]' }),
		L('quick', { focus: '[data-tour=quick-match]' }),
		L('create', { focus: '[data-tour=create-room]' }),
		L('chat', { focus: '[data-tour=chat]', at: 'top' }),
		L('side', { focus: '[data-tour=side]', at: 'bottom' }),
		L('profile', { focus: '[data-tour=profile]', at: 'top' }),
		L('nav', { focus: '[data-tour=nav]', at: 'bottom' }),
		L('help', { focus: '[data-tour=help]' }),
		L('bye', { mood: 'happy', focus: null }),
		{ do: 'complete' },
		{ end: true }
	]
};
