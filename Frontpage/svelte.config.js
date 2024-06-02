import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		adapter: adapter({
			// default options are shown. On some platforms
			// these options are set automatically — see below
			pages: '../src/main/resources/routes',
			assets: '../src/main/resources/static',
			fallback: undefined,
			precompress: false,
			strict: true
		})
	},
	preprocess: vitePreprocess()
};

export default config;