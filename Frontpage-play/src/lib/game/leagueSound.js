/**
 * 트로피 리그 규칙 릴의 효과음.
 *  - 매칭 완료(matched.mp3): 게임의 `mission` 효과음과 동시에 한 번.
 *  - 회전음(spin.mp3): 4초(16박) 한 바퀴짜리 루프다. 릴이 도는 동안 끊김 없이 되풀이하다가 마지막 슬롯이 멈추면 서서히 끈다.
 *  - 확정음(lock.mp3): 모든 슬롯이 멈춘 '뒤에' 한 번.
 *  - 슬롯 하나가 멈출 때의 짧은 팝과 장난 띠의 스팅은 파일 없이 합성한다(audio.svelte.js의 playTone).
 * 파일은 KKuTu-Static의 media/league/ 에 있고 소리 크기와 음소거 설정을 따른다.
 */
import { playFile, playSound, playTone } from './audio.svelte.js';
import { leagueSoundUrl } from './audioCatalog.js';

const C6 = 1046.5, E6 = 1318.5;

/** 매칭 완료: 매칭 효과음과 게임의 mission 효과음을 동시에 낸다(둘 다 미리 받아 두므로 같은 순간에 시작한다). */
export const soundMatched = () => {
	playFile(leagueSoundUrl('matched'), { ui: true });
	playSound('mission', { ui: true });
};

/** 회전음을 되풀이하기 시작한다. 돌려주는 약속의 값(null일 수 있다)으로 `.stop(fade)`를 부른다. */
export const spinStart = () => playFile(leagueSoundUrl('spin'), { ui: true, loop: true });

/** 확정음(모든 슬롯이 멈춘 뒤에 부른다). */
export const lockSound = () => playFile(leagueSoundUrl('lock'), { ui: true });

/** 슬롯 하나가 멈출 때: 통통 튀는 팝과 맑은 꼬리. 아래 슬롯일수록 한 단계씩 높아진다. */
export const soundLand = (step = 0) => {
	const up = [1, 1.122, 1.26, 1.335, 1.498][Math.min(4, step)] ?? 1;
	playTone([
		{ hz: 330 * up, to: 660 * up, dur: 0.09, type: 'square', vol: 0.3 },
		{ at: 0.04, hz: C6 * up, dur: 0.22, type: 'sine', vol: 0.42 },
		{ at: 0.04, hz: E6 * up, dur: 0.16, type: 'triangle', vol: 0.18 }
	], { ui: true });
};

/** 파란 머리 모레미의 장난: 쭉 미끄러져 내려가는 소리와 어긋난 두 음, 그리고 반짝이는 올림. */
export const soundPrank = () => playTone([
	{ at: 0, hz: 1500, to: 180, dur: 0.34, type: 'sawtooth', vol: 0.3 },
	{ at: 0.34, hz: 233, dur: 0.1, type: 'square', vol: 0.3 },
	{ at: 0.44, hz: 247, dur: 0.1, type: 'square', vol: 0.3 },
	{ at: 0.62, hz: C6, dur: 0.1, type: 'sine', vol: 0.45 },
	{ at: 0.72, hz: E6, dur: 0.1, type: 'sine', vol: 0.45 },
	{ at: 0.82, hz: 1568, dur: 0.1, type: 'sine', vol: 0.45 },
	{ at: 0.92, hz: 2093, dur: 0.3, type: 'sine', vol: 0.5 }
], { ui: true });
