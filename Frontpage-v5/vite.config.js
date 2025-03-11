import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
	plugins: [
		sveltekit(),
		tailwindcss()
	],
	build: {
		sourcemap: true, // Ensure source maps are generated
		minify: false, // Disable minification for readability
		rollupOptions: {
			output: {
				compact: false, // Prevent output compaction
				sourcemap: true // Ensure source maps are available
			}
		}
	},
	optimizeDeps: {
		exclude: ['svelte/store']
	}
});
