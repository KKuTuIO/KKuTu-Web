/**
 * "자리 비움" by itself: after a while without any input an online player is shown as idle, and as soon as they touch anything
 * they are online again. Only a status that was set this way is taken back; one the player chose stays as chosen. The server is told it
 * was automatic, so it is not kept for the next visit.
 */
export const IDLE_AFTER_MS = 10 * 60 * 1000;
const EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart'];

/**
 * @param {{status:()=>string, set:(status:string, auto:boolean)=>void, after?:number, target?:Window, schedule?:typeof setTimeout, cancel?:typeof clearTimeout}} o
 * @returns {() => void} stop
 */
export function watchIdle({ status, set, after = IDLE_AFTER_MS, target = window, schedule = setTimeout, cancel = clearTimeout }) {
	let timer;
	let auto = false; // idle because of this watcher
	const goIdle = () => {
		if (status() === 'online') {
			auto = true;
			set('idle', true);
		}
	};
	const arm = () => {
		cancel(timer);
		timer = schedule(goIdle, after);
	};
	const activity = () => {
		if (auto && status() === 'idle') set('online', true);
		auto = false;
		arm();
	};
	const visibility = () => {
		if (document.visibilityState === 'visible') activity();
	};
	for (const e of EVENTS) target.addEventListener(e, activity, { passive: true });
	document.addEventListener('visibilitychange', visibility);
	arm();
	return () => {
		cancel(timer);
		for (const e of EVENTS) target.removeEventListener(e, activity);
		document.removeEventListener('visibilitychange', visibility);
	};
}
