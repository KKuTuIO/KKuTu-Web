import '@testing-library/jest-dom/vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { setMessages } from '../lib/game/i18n.svelte.js';

// Tests run against the real Korean message file the server ships, so a missing key shows up as a failure.
// vitest runs from the Frontpage directory; the server's locale files live one level up.
const props = readFileSync(resolve(process.cwd(), '../src/main/resources/locale/message_ko.properties'), 'utf8');
const messages = {};
for (const line of props.split(/\r?\n/)) {
	const m = line.match(/^([A-Za-z0-9_.-]+)=(.*)$/);
	if (m) messages[m[1]] = m[2];
}
setMessages(messages, 'ko');

// jsdom has no Web Animations API; Svelte transitions need `element.animate`. Finish instantly.
if (!Element.prototype.animate) {
	Element.prototype.animate = function () {
		return {
			cancel() {},
			finish() {},
			finished: Promise.resolve(),
			set onfinish(fn) {
				queueMicrotask(() => fn?.());
			}
		};
	};
}
