import { playUi, preloadUiFiles } from './audio.svelte.js';

/**
 * 모든 버튼의 소리: 눌렀을 때 'click', 마우스를 올렸을 때 'hover'. 개별 버튼에 일일이 달지 않고 문서 전체에서 이벤트를 위임해 받는다
 * (대화창, 팝업, 나중에 생기는 버튼까지 같다). 버튼이 아닌 곳, 눌 수 없는 버튼(disabled, aria-disabled)은 소리가 없고,
 * 손가락에는 올려 보는 일이 없으므로 hover는 마우스(펜 포함하지 않음)에서만 난다. 소리를 끄고 싶은 곳은 `data-no-ui-sound`를 단다.
 * 음량은 설정의 인터페이스(음성) 음량을 따른다(audio.svelte.js의 playFile).
 */
const SELECTOR = 'button, [role="button"], [role="tab"], [role="menuitem"], [role="switch"], [role="checkbox"], summary, a[href], input[type="checkbox"], input[type="radio"], select';
const HOVER_GAP_MS = 60; // 빠르게 훑을 때 소리가 쌓이지 않게

function buttonOf(e) {
	const el = e.target instanceof Element ? e.target.closest(SELECTOR) : null;
	if (!el) return null;
	if (el.matches(':disabled') || el.getAttribute('aria-disabled') === 'true' || el.closest('[data-no-ui-sound]')) return null;
	return el;
}

/** 소리를 단다. 되돌리는 함수를 돌려준다. */
export function installUiSounds() {
	if (typeof document === 'undefined') return () => {};
	let over = null;
	let lastHover = 0;
	const onClick = (e) => {
		if (buttonOf(e)) playUi('click');
	};
	const onOver = (e) => {
		if (e.pointerType !== 'mouse') return;
		const el = buttonOf(e);
		if (el === over) return;
		over = el;
		const now = performance.now();
		if (el && now - lastHover >= HOVER_GAP_MS) {
			lastHover = now;
			playUi('hover');
		}
	};
	// 처음 누르는 순간에 소리를 미리 받아 둔다(그 순간 오디오가 열린다)
	let warmed = false;
	const warm = () => {
		if (warmed) return;
		warmed = true;
		setTimeout(preloadUiFiles, 0);
	};
	document.addEventListener('click', onClick, true);
	document.addEventListener('pointerover', onOver, true);
	document.addEventListener('pointerdown', warm, { capture: true, once: true });
	return () => {
		document.removeEventListener('click', onClick, true);
		document.removeEventListener('pointerover', onOver, true);
		document.removeEventListener('pointerdown', warm, true);
	};
}
