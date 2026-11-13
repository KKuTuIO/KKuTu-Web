/** The lobby stage's pictures (stage/*.webp), built into the page's own files: the web serves them with the rest of the client, no CDN needed. */
const files = import.meta.glob('./stage/*.webp', { eager: true, query: '?url', import: 'default' });
/** @param {string} name e.g. 'sky.webp' */
export const stageUrl = (name) => files[`./stage/${name}`] ?? '';
