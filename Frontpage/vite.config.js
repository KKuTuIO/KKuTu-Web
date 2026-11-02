import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
	// Component tests mount Svelte in jsdom, which needs the browser build of Svelte.
	resolve: process.env.VITEST ? { conditions: ['browser'] } : undefined,
	plugins: [
		sveltekit(),
		tailwindcss()
	],
	test: {
		environment: 'jsdom',
		setupFiles: ['./src/test/setup.js']
	}
});
