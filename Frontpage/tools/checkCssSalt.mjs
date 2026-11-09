/**
 * Fails the build when the generated pages, scripts and stylesheets do not agree on the scoped-class salt (svelte.config.js).
 * If they disagree, the HTML names one class and the CSS styles another, and every scoped style on the page is silently lost.
 *
 *   node tools/checkCssSalt.mjs [staticDir]
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(process.argv[2] ?? join(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/static'));
// salted component classes look like `kca-1ylf61l`: prefix + a short base-36 hash that contains a digit (our own `kkg-backdrop` does not)
const TOKEN = /\b(k[a-z]{2})-(?=[a-z]*[0-9])[a-z0-9]{5,8}\b/g;
const prefixes = new Map(); // prefix -> files

// Only what the pages actually load counts: adapter-static leaves chunks of earlier builds in the folder. Start from the HTML pages and
// follow every .js / .css file name mentioned (the names are content-hashed, so a name identifies one file).
const byName = new Map();
const htmls = [];
(function index(dir) {
	for (const name of readdirSync(dir)) {
		const path = join(dir, name);
		if (statSync(path).isDirectory()) index(path);
		else if (/\.(js|css)$/.test(name)) byName.set(name, path);
		else if (name.endsWith('.html')) htmls.push(path);
	}
})(root);
const seen = new Set();
const queue = [...htmls];
while (queue.length) {
	const path = queue.pop();
	if (seen.has(path)) continue;
	seen.add(path);
	const text = readFileSync(path, 'utf8');
	for (const [, prefix] of text.matchAll(TOKEN)) {
		if (!prefixes.has(prefix)) prefixes.set(prefix, new Set());
		prefixes.get(prefix).add(path.slice(root.length + 1));
	}
	for (const [name] of text.matchAll(/[A-Za-z0-9_.-]+\.(?:js|css)/g)) if (byName.has(name)) queue.push(byName.get(name));
}

if (prefixes.size > 1) {
	console.error('\nCSS salt mismatch: the build output mixes scoped-class salts, so scoped styles would not apply.');
	for (const [prefix, files] of prefixes) console.error(`  ${prefix}-…  in ${files.size} files, e.g. ${[...files].slice(0, 3).join(', ')}`);
	process.exit(1);
}
console.log(`CSS salt check: ok (${prefixes.size ? [...prefixes.keys()][0] : 'no scoped classes'})`);
