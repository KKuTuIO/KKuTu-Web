/** Static files (images, fonts, css) live in the static-assets repository and are served from the CDN, not from this app. */
export const CDN = 'https://cdn.kkutu.io';
/** `cdnImg('play/xp.png')` -> https://cdn.kkutu.io/img/play/xp.png */
export const cdnImg = (path) => `${CDN}/img/${path}`;
