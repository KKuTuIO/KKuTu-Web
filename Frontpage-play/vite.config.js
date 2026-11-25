import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';
import tailwindcss from '@tailwindcss/vite';
import { execSync } from 'node:child_process';
import { obfuscateSecurity } from './tools/obfuscateSecurity.js';

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

// PLAY_DEV_BUNDLE=1 builds the unobfuscated twin that game administrators can switch to (svelte.config.js puts it in its own folder).
const devBundle = !!process.env.PLAY_DEV_BUNDLE;
const obfuscate = !!process.env.OBFUSCATE_SECURITY && !devBundle;

export default defineConfig({
	// `release` = what players get; anything else carries the red watermark (lobby/StatusMark.svelte).
	define: { __COMMIT__: JSON.stringify(commit()), __PLAY_BUNDLE__: JSON.stringify(obfuscate ? 'release' : devBundle ? 'dev' : 'local') },
	// Component tests mount Svelte in jsdom, which needs the browser build of Svelte.
	resolve: process.env.VITEST ? { conditions: ['browser'] } : undefined,
	plugins: [
		sveltekit(),
		tailwindcss(),
		// release builds only: the anti-cheat modules are obfuscated (see tools/obfuscateSecurity.js)
		...(obfuscate ? [obfuscateSecurity()] : [])
	],
	test: {
		// The board tests mount a lot of Svelte; on a shared CI runner a single one can pass 5 s although nothing is wrong.
		testTimeout: 20000,
		// On the CI runner one test of the board suite now and then stalls past any limit (not reproducible here, even with the CPUs oversubscribed); a
		// test that is really broken fails every try, so only a stalled one is let through. Locally nothing is retried.
		retry: process.env.CI ? 2 : 0,
		environment: 'jsdom',
		setupFiles: ['./src/test/setup.js']
	}
});
