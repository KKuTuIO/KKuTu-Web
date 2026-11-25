import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

// The administrators' development bundle is built apart (its own folder and URL prefix), so the two never share a file.
const suffix = process.env.PLAY_DEV_BUNDLE ? '-dev' : '';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		// `_play` keeps this build's files clear of the portal's `_app`; PlayController serves them (admins only) from the jar.
		appDir: `_play${suffix}`,
		adapter: adapter({
			pages: `../src/main/resources/play${suffix}`,
			assets: `../src/main/resources/play${suffix}`,
			fallback: '200.html',
			precompress: false,
			strict: true
		})
	},
	preprocess: vitePreprocess()
};

export default config;
