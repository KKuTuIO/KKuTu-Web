<script>
	import { onMount, tick, untrack } from 'svelte';
	import { createRunner } from '../story/runner.svelte.js';
	import { forDevice } from '../story/device.js';
	import { i18n } from '../i18n.svelte.js';
	import StoryOverlay from '../story/StoryOverlay.svelte';
	import FakeGame from './FakeGame.svelte';
	import { completeLesson, progress } from './progress.svelte.js';
	import { playLobbyBgm, playTutorialBgm } from '../audio.svelte.js';

	/**
	 * Plays one lesson: its dialogue script on the story overlay, with the page's side of it: the practice game (`stage`) and the real
	 * "방 만들기" dialog (`do`) the scripts ask for. Skipping (건너뛰기, Esc) ends it without clearing it.
	 * @type {{store:any, lesson:{id:string, script:object}, onexit:()=>void}}
	 */
	let { store, lesson, onexit } = $props();
	let overlay = $state();
	let stage = $state(/** @type {null|{name:string, api:any, finish:()=>void}} */ (null));

	const runner = createRunner(untrack(() => forDevice(lesson.script, typeof innerWidth !== 'undefined' && innerWidth < 768, (k) => i18n.messages[k] !== undefined)), {
		stage: (name, node, api) =>
			new Promise((resolve) => {
				stage = { name, api, finish: () => ((stage = null), resolve()) };
			}),
		async do(name) {
			if (name === 'complete') {
				completeLesson(lesson.id);
				// signed in: the server marks it and pays the 100 핑 (the first time), the page shows what it paid; a guest gets nothing
				if (progress.signedIn) store.conn.send('tutorialClear', { id: lesson.id });
				return;
			}
			if (name === 'openModes' || name === 'closeModes') {
				const open = name === 'openModes';
				if (!store.hooks.openModes) return; // the room list lobby has no mode screen
				store.hooks.openModes(open);
				await tick();
				await new Promise((r) => setTimeout(r, open ? 750 : 450)); // the mode screen's slide in / the stage's unblur
				overlay?.restack();
				return;
			}
			if (name === 'openCreate' || name === 'closeCreate') {
				store.hooks.setCreateOpen?.(name === 'openCreate');
				await tick();
				await new Promise((r) => setTimeout(r, name === 'openCreate' ? 450 : 350)); // the dialog's own opening / closing motion
				overlay?.restack(); // the real dialog was opened after the overlay: put the overlay back on top of it
			}
		},
		end: () => onexit()
	});
	onMount(() => {
		playTutorialBgm(); // the tutorial has its own music, from the first line to the last (the lobby's crossfades out)
		runner.start();
		return () => {
			runner.dispose();
			store.hooks.setCreateOpen?.(false);
			store.hooks.openModes?.(false);
			playLobbyBgm({ fromResult: true, fromTutorial: true }); // and the lobby's comes back
		};
	});
</script>

<StoryOverlay bind:this={overlay} {runner} {store} dim={!stage} onexit={() => runner.skip()}>
	{#if stage?.name === 'chain'}
		<FakeGame real={store} tip={stage.api.tip} line={stage.api.line} focus={stage.api.focus} finish={stage.finish} exit={() => runner.skip()} />
	{/if}
</StoryOverlay>
