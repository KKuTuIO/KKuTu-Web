import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		// `_play` keeps this build's files clear of the portal's `_app`; PlayController serves them (admins only) from the jar.
		appDir: '_play',
		adapter: adapter({
			pages: '../src/main/resources/play',
			assets: '../src/main/resources/play',
			fallback: '200.html',
			precompress: false,
			strict: true
		})
	},
	preprocess: vitePreprocess()
};

export default config;
