/** Lesson 2: a scripted 끝말잇기 against 학사모레미 on the real game screen (see chainGame.svelte.js). */
const L = (id, extra = {}) => ({ say: { who: 'haksamo', text: `play.story.chain.${id}`, mood: extra.mood }, ...extra, mood: undefined });

export default {
	id: 'chain',
	version: 1,
	cast: ['haksamo'],
	nodes: [
		L('hi', { mood: 'happy', at: 'bottom' }),
		L('rule'),
		L('how'),
		{ stage: 'chain', at: 'top' },
		L('bye', { mood: 'happy', at: 'bottom' }),
		{ do: 'complete' },
		{ end: true }
	]
};
