<script>
	import { onDestroy, untrack } from 'svelte';
	import { createChainGame } from './chainGame.svelte.js';
	import { playTutorialBgm, stopBgm, wantedBgm } from '../audio.svelte.js';
	import GameHeader from '../lobby/GameHeader.svelte';
	import GameScreen from '../lobby/GameScreen.svelte';
	import PauseFlash from './PauseFlash.svelte';

	/**
	 * The practice 끝말잇기 of lesson 2: the real game header and board (the same ones the replay player uses) on a game that runs here.
	 * `stage` is what the session gives it: `real` (the page's store), `api.tip` (what the guide says), `api.focus` (dims the page except the answer
	 * field), `finish()` (the result was closed), `exit()`.
	 * Music: a game of this mode has none of its own (only the turn clock and the round's sounds), so the tutorial's fades out while it is played;
	 * the result screen has its own music, and when the game is over (the result closed) the tutorial's comes back for the last lines (the
	 * session puts the lobby's back at the end).
	 * @type {{real:any, tip:(key:string, o?:object)=>void, line:(key:string, o?:object)=>Promise<void>, focus:(selector:string|null, label?:string)=>void, finish:()=>void, exit:()=>void}}
	 */
	let { real, tip, line, focus, finish, exit } = $props();
	// one game for the life of the stage
	const game = untrack(() => createChainGame({ real, tip, line, focus, onEnd: () => finish(), onExit: () => exit() }));
	stopBgm({ fade: true });
	game.start();
	onDestroy(() => {
		game.dispose();
		// the result is closed (or the game was left): the tutorial's music is back, unless the session is over and has put the lobby's back already
		const now = wantedBgm();
		if (now === 'result' || now === null) playTutorialBgm({ fromResult: true });
	});
</script>

<div class="absolute inset-0 flex flex-col gap-3 overflow-y-auto bg-(--kk-bg) p-4 text-sm text-(color:--kk-text)">
	<GameHeader store={game.store} />
	<div class="mx-auto flex min-h-0 w-full max-w-[1100px] flex-1 flex-col"><GameScreen store={game.store} /></div>
	<PauseFlash pause={game.store.ui.pause} />
</div>
