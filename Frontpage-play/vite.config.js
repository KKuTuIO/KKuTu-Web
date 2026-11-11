import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';
import tailwindcss from '@tailwindcss/vite';
import { execSync } from 'node:child_process';

/** The Git commit this build is made from (short id, `+` when the working tree has changes). CI can pass it as COMMIT_SHA / GITHUB_SHA when there is no .git. */
function commit() {
	const given = process.env.COMMIT_SHA || process.env.GITHUB_SHA;
	if (given) return given.slice(0, 7);
	try {
		const run = (cmd) => execSync(cmd, { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();
		return run('git rev-parse --short=7 HEAD') + (run('git status --porcelain') ? '+' : '');
	} catch {
		return 'dev';
	}
}

/**
 * The portal's CDN (cdn.kkutu.io) does not carry what this client added (fonts, icon font, art), so those files ship in this build under
 * /_play/cdn and PlayController serves them to administrators; anything it does not have is redirected to the real CDN. The source stays
 * identical to the client in the revamp branch, the address is swapped here.
 */
const SELF_CDN = '/_play/cdn';
const selfHostedCdn = () => ({
	name: 'self-hosted-cdn',
	enforce: 'pre',
	transform(code, id) {
		if (!/\.(js|svelte|css)$/.test(id.split('?')[0]) || id.includes('node_modules') || !code.includes('https://cdn.kkutu.io')) return null;
		return { code: code.split('https://cdn.kkutu.io').join(SELF_CDN), map: null };
	},
});

export default defineConfig({
	define: { __COMMIT__: JSON.stringify(commit()) },
	// Component tests mount Svelte in jsdom, which needs the browser build of Svelte.
	resolve: process.env.VITEST ? { conditions: ['browser'] } : undefined,
	plugins: [
		selfHostedCdn(),
		sveltekit(),
		tailwindcss(),
	],
	test: {
		environment: 'jsdom',
		setupFiles: ['./src/test/setup.js']
	}
});
