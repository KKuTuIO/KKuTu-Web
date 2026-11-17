/** Static files (images, fonts, css) live in the static-assets repository and are served from the CDN, not from this app. */
export const CDN = 'https://cdn.kkutu.io';
/**
 * 아직 CDN에 올리지 않은 자산(img/play/league, media/league, media/ui)의 주소. 로컬에서 개발할 때(127.0.0.1)는 로컬 Static 서버(:8081)에서 받고,
 * 그 밖에서는 CDN이다. 자산을 CDN에 올린 뒤에는 아무 일도 하지 않는다(로컬 Static이 없는 곳에서는 쓰이지 않는다).
 */
export const PENDING_CDN =
	typeof location !== 'undefined' && (location.hostname === '127.0.0.1' || location.hostname === 'localhost') ? 'http://127.0.0.1:8081' : CDN;
/** `cdnImg('play/xp.png')` -> https://cdn.kkutu.io/img/play/xp.png */
export const cdnImg = (path) => `${path.startsWith('play/league/') ? PENDING_CDN : CDN}/img/${path}`;
