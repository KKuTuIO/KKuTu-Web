/**
 * Build-time obfuscation of the anti-cheat code, in the spirit of the legacy client's `obfuscate.js`: the security
 * script got the heavy options, the rest of the client the light ones. Here only the anti-cheat modules are obfuscated, per module
 * before bundling, so the rest of the app is untouched.
 *
 *   pnpm build:hardened         (= OBFUSCATE_SECURITY=1 vite build)
 *
 * The release workflow (.github/workflows/build.yml) sets OBFUSCATE_SECURITY=1.
 *
 * It is OFF in a plain `pnpm build` and in dev/tests, so nothing changes unless this is asked for.
 *
 * Taken from the legacy "base2" options: control-flow flattening, dead-code injection, RC4-encoded shuffled string table, split
 * strings, hex identifiers, number expressions, object-key transformation. Every build gets a different result (no fixed seed), so a
 * patch written for one build does not apply to the next.
 *
 * Left out on purpose, although the legacy security script used them:
 *   debugProtection    an endless `debugger` loop that freezes DevTools for everyone, honest players and support staff included;
 *                      a bot is not slowed by it
 *   selfDefending      breaks when the file is reformatted by a CDN/minifier step, and fails silently
 *   disableConsoleOutput  would hide our own and Sentry's diagnostics
 *   (and the legacy security.js, which blocked F12 and the right-click menu: no effect on bots, hostile to everyone else)
 */
import JavaScriptObfuscator from 'javascript-obfuscator';

// the anti-cheat modules (not their tests)
const TARGET = /[\\/]src[\\/]lib[\\/]game[\\/](security[\\/][^\\/]+|telemetry|identity)\.js$/;

export const OPTIONS = {
	compact: true,
	sourceType: 'module',
	target: 'browser',
	controlFlowFlattening: true,
	controlFlowFlatteningThreshold: 0.75,
	deadCodeInjection: true,
	deadCodeInjectionThreshold: 0.2,
	identifierNamesGenerator: 'hexadecimal',
	numbersToExpressions: true,
	renameGlobals: false,
	simplify: true,
	splitStrings: true,
	splitStringsChunkLength: 5,
	stringArray: true,
	stringArrayCallsTransform: true,
	stringArrayEncoding: ['rc4'],
	stringArrayIndexShift: true,
	stringArrayRotate: true,
	stringArrayShuffle: true,
	stringArrayWrappersCount: 3,
	stringArrayWrappersChainedCalls: true,
	stringArrayThreshold: 1,
	transformObjectKeys: true,
	unicodeEscapeSequence: false,
	// Without this the package name in `import('@fingerprintjs/fingerprintjs')` (identity.js) goes into the string table: the bundler then
	// cannot see the import, the library is left out of the build and every player gets the low-confidence fallback id.
	ignoreImports: true,
	// see the header: not used
	debugProtection: false,
	selfDefending: false,
	disableConsoleOutput: false,
	log: false
};

export function obfuscateSecurity() {
	return {
		name: 'kk-obfuscate-security',
		apply: 'build',
		enforce: 'pre',
		transform(code, id) {
			const file = id.split('?')[0];
			if (!TARGET.test(file) || /\.test\.js$/.test(file)) return null;
			return { code: JavaScriptObfuscator.obfuscate(code, OPTIONS).getObfuscatedCode(), map: null };
		}
	};
}
