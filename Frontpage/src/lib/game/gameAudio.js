/**
 * The sounds the legacy client makes for a game's packets: the ticking of the turn clock (`T<speed>`), the all-at-once modes' effects and
 * music. One per game being shown: the live connection feeds it what it receives, a replay what it applies.
 */
import { playSound, playGameBgm, stopBgm } from './audio.svelte.js';
import { QUIZ } from './gameModel.js';

/** @param {object} state the lobby state the packets were applied to (users, rooms, myRoom, id) @param {{onTurnStart?:(pkt:object)=>void}} [o] */
export function createGameAudio(state, { onTurnStart } = {}) {
		/** The sounds the legacy client makes for the game's packets. */
		let turnSound; // the ticking of the turn clock, cut off when the turn ends
		const stopTurnSound = () => {
			turnSound?.then((h) => h?.stop());
			turnSound = null;
		};
		/** The sounds of the all-at-once modes, as each legacy module plays them. Returns true when the round's music should stop. */
		function quizSounds(pkt, fam) {
			const me = state.id;
			const spectating = state.users[me]?.game.form === 'S';
			const target = pkt.target && typeof pkt.target === 'object' ? pkt.target.id : (pkt.target ?? pkt.id);
			if (pkt.type === 'turnHint') {
				playSound(fam === 'Sock' || fam === 'Linkland' || fam === 'Crossword' ? 'fail' : 'mission');
				return false;
			}
			if (pkt.type !== 'turnEnd') return false;
			if (fam === 'Jaqwi' || fam === 'Drawing') {
				if (pkt.giveup) return false;
				playSound(pkt.answer ? 'horr' : 'success');
				return !!pkt.answer;
			}
			if (fam === 'Sock') {
				if (pkt.score) playSound(target === me ? 'success' : 'mission');
				else playSound('horr');
				return !pkt.score;
			}
			if (fam === 'Crossword') {
				if (pkt.score) playSound(target === me ? 'success' : 'mission');
				else playSound('horr');
				return !pkt.score;
			}
			if (fam === 'Linkland') {
				if (!pkt.settlement) playSound(target === me ? 'success' : 'mission');
				return false;
			}
			if (fam === 'Wordstack') {
				if (pkt.error) playSound('fail');
				else if (pkt.ok || pkt.isBonus) {
					if (!pkt.isBonus) {
						if (target === me) playSound('mission');
						else if (pkt.attack === me) playSound('kung');
						else if (spectating) playSound('mission');
					}
				} else {
					playSound('horr');
					return true;
				}
				return false;
			}
			// 타자 대결 / 수학 대결
			if (pkt.error) playSound('fail');
			else if (pkt.ok) {
				if (target === me || spectating) playSound('mission');
			} else {
				playSound('horr');
				return true;
			}
			return false;
		}
		function gameSounds(pkt) {
			const fam = state.meta.rules[state.rooms[state.myRoom]?.modeKey]?.rule;
			const quiz = QUIZ.includes(fam);
			if (quiz && pkt.type === 'turnStart') {
				// legacy: the quiz modes have a music of their own, a faster one for the last ten seconds
				onTurnStart?.(pkt);
				playGameBgm(pkt.roundTime <= 10000 ? 'JaqwiFastBGM' : 'JaqwiBGM');
				return;
			}
			if (quiz && ['turnHint', 'turnEnd'].includes(pkt.type)) {
				stopTurnSound();
				if (quizSounds(pkt, fam)) stopBgm();
				return;
			}
			if (pkt.type === 'turnStart') {
				onTurnStart?.(pkt);
				stopTurnSound();
				turnSound = playSound(`T${pkt.speed}`);
			} else if (pkt.type === 'roundEnd') stopTurnSound();
			else if (pkt.type === 'roundReady' && !pkt.enter) playSound('round_start');
			else if (pkt.type === 'turnError') playSound('fail');
			else if (pkt.type === 'turnEnd') {
				stopTurnSound();
				if (!pkt.ok) playSound('timeout');
				else if (pkt.baby) playSound('success');
			}
		}
	return { onPacket: gameSounds, stopTurnSound };
}
