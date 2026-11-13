/** Lesson 1 (the match-centred lobby): a tour of the stage, the mode screen behind 게임 시작!, then the parts around it (found by their data-tour marks). */
const L = (id, extra = {}) => ({ say: { who: 'haksamo', text: `play.story.lobby.${id}`, mood: extra.mood }, ...extra, mood: undefined });

export default {
	id: 'lobby',
	version: 2,
	cast: ['haksamo'],
	nodes: [
		{ do: 'closeModes' }, // (it may be started from the mode screen's 튜토리얼 card)
		L('hi', { mood: 'happy', focus: null, at: 'bottom' }),
		L('start', { focus: '[data-tour=start]', at: 'top' }),
		{ do: 'openModes' },
		L('multi', { focus: '[data-tour=mode-multi]', at: 'bottom' }),
		L('quick', { focus: '[data-tour=quick-match]', at: 'bottom' }),
		L('rooms2', { focus: '[data-tour=room-list]', at: 'bottom' }),
		L('create', { focus: '[data-tour=create-room]', at: 'bottom' }),
		L('single', { focus: '[data-tour=mode-single]', at: 'top' }),
		L('tutorial', { focus: '[data-tour=tutorial]', at: 'top' }),
		{ do: 'closeModes' },
		L('chat', { focus: '[data-tour=chat]', at: 'top' }),
		L('side', { focus: '[data-tour=side]', at: 'bottom' }),
		L('profile', { focus: '[data-tour=profile]', at: 'top', noPhone: true }),
		L('nav', { focus: '[data-tour=nav]', at: 'bottom' }),
		L('bye', { mood: 'happy', focus: null }),
		{ do: 'complete' },
		{ end: true }
	]
};
