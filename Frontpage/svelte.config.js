import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';
import { randomBytes } from 'node:crypto';

/**
 * Class names Svelte generates for component styles (`svelte-1abc23` by default) are a pure function of the component, so a script or a
 * copied stylesheet written against one keeps working build after build. Here they are salted with a value that is new for every build
 * (set CSS_SALT to pin it, e.g. for a reproducible CI build) and lose the telltale `svelte-` prefix, so a selector written for one
 * release does not match the next. Only scoped component styles are covered: our own `kk-*` classes and Tailwind's utility classes are
 * written out as they are (see ~/Documents/KKuTuIO-anticheat-guide.md, section 10A).
 */
const SALT = process.env.CSS_SALT || randomBytes(8).toString('hex');
const PREFIX = `k${SALT.replace(/[^a-z]/g, '').slice(0, 2).padEnd(2, 'x')}`;

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		adapter: adapter({
			// default options are shown. On some platforms
			// these options are set automatically — see below
			pages: '../src/main/resources/static',
			assets: '../src/main/resources/static',
			fallback: '200.html',
			precompress: false,
			strict: true
		})
	},
	compilerOptions: {
		cssHash: ({ hash, filename, css }) => `${PREFIX}-${hash(`${SALT}|${filename ?? ''}|${css}`)}`
	},
	preprocess: vitePreprocess()
};

export default config;