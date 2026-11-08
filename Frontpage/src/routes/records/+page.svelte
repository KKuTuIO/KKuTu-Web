<script>
	import { onMount } from 'svelte';
	import { createPortalHost } from '$lib/game/replayHost.js';
	import ReplayScreen from '$lib/game/lobby/ReplayScreen.svelte';
	import ToastHost from '$lib/game/ui/ToastHost.svelte';
	import '$lib/game/nickSkins.css';

	/**
	 * 리플레이: the replay page on the portal, the same component as the game client's (no connection needed, and a game has a link anyone can open).
	 *   /records?type=gameId&q=<game id>   a game      /records?type=id&q=<player id>   a player's games      ?type=nick&q=<nickname>
	 */
	let host = $state(null);
	let failed = $state(false);
	let start = $state({ game: '', user: '', userType: '', n: 0 });

	onMount(async () => {
		const q = new URLSearchParams(location.search);
		const type = q.get('type');
		const value = q.get('q') ?? q.get('game') ?? '';
		start = { game: type === 'gameId' || (!type && q.get('game')) ? value : '', user: type === 'id' || type === 'nick' ? value : '', userType: type ?? '', n: 1 };
		try {
			host = await createPortalHost();
		} catch {
			failed = true;
		}
	});

	// the address follows the game that is open, so that its link can be copied from the bar as well
	function onroute(id) {
		const url = new URL(location.href);
		url.search = '';
		if (id) {
			url.searchParams.set('type', 'gameId');
			url.searchParams.set('q', id);
		}
		history.replaceState(history.state, '', url);
	}
</script>

<svelte:head><title>리플레이 | 끄투리오</title></svelte:head>

<ToastHost />

<div class="mx-auto flex h-[calc(100dvh-9rem)] min-h-[32rem] w-full max-w-6xl flex-col px-4 py-4 text-sm text-(color:--kk-text)">
	{#if host}
		<ReplayScreen store={host} {start} {onroute} />
	{:else}
		<p class="m-auto text-(color:--kk-muted)">{failed ? '리플레이를 불러오지 못했습니다.' : '불러오는 중...'}</p>
	{/if}
</div>
