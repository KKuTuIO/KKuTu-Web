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

export default defineConfig({
	define: { __COMMIT__: JSON.stringify(commit()) },
	// Component tests mount Svelte in jsdom, which needs the browser build of Svelte.
	resolve: process.env.VITEST ? { conditions: ['browser'] } : undefined,
	plugins: [
		sveltekit(),
		tailwindcss(),
	],
	test: {
		environment: 'jsdom',
		setupFiles: ['./src/test/setup.js']
	}
});
