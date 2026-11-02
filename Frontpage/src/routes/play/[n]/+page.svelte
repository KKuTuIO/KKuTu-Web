<script>
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import { createLobbyStore } from '$lib/game/lobbyStore.svelte.js';
	import Icon from '$lib/game/lobby/Icon.svelte';
	import TopBar from '$lib/game/lobby/TopBar.svelte';
	import SidePanel from '$lib/game/lobby/SidePanel.svelte';
	import RoomList from '$lib/game/lobby/RoomList.svelte';
	import ChatPanel from '$lib/game/lobby/ChatPanel.svelte';
	import ConnectionGate from '$lib/game/lobby/ConnectionGate.svelte';
	import CreateRoomDialog from '$lib/game/lobby/CreateRoomDialog.svelte';
	import QuickMatchDialog from '$lib/game/lobby/QuickMatchDialog.svelte';
	import PasswordDialog from '$lib/game/lobby/PasswordDialog.svelte';
	import LoadingScreen from '$lib/game/lobby/LoadingScreen.svelte';
	import ToastHost from '$lib/game/ui/ToastHost.svelte';
	import { t } from '$lib/game/i18n.svelte.js';
	import { toast } from '$lib/game/ui/toast.svelte.js';
	import RankingDialog from '$lib/game/lobby/RankingDialog.svelte';
	import DictionaryDialog from '$lib/game/lobby/DictionaryDialog.svelte';
	import RoomPanel from '$lib/game/lobby/RoomPanel.svelte';

	const store = createLobbyStore(Number(page.params.n));
	let createOpen = $state(false);
	let quickOpen = $state(false);
	let passwordRoom = $state(null);
	let rankOpen = $state(false);
	let dictOpen = $state(false);
	function selectMenu(id) {
		if (id === 'lobby') return;
		if (id === 'rank') {
			rankOpen = true;
			return;
		}
		if (id === 'dict') {
			dictOpen = true;
			return;
		}
		toast(t('play.nav.notReady', { name: t(`play.nav.${id}`) }), { kind: 'info' });
	}
	const ready = $derived(store.net.status === 'ready');
	// Embers for the 트로피 리그 button (fixed values: the pattern is decoration, not data).
	const embers = Array.from({ length: 10 }, (_, i) => ({ x: 6 + ((i * 29) % 86), d: 0.9 + ((i * 11) % 5) * 0.2, delay: -i * 0.21, drift: (i % 2 ? 1 : -1) * (4 + (i % 3) * 3) }));

	onMount(() => {
		store.conn.open();
		return store.conn.close;
	});

	function join(no) {
		const room = store.net.rooms[no];
		if (room?.locked) passwordRoom = room;
		else store.join(no);
	}
</script>

<div class="flex h-dvh min-h-[600px] flex-col bg-(--kk-bg) text-sm text-(color:--kk-text)">
	<TopBar {store} onselect={selectMenu} />
	<div class="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4 lg:flex-row lg:overflow-hidden">
		<div class="order-2 flex max-h-72 lg:order-1 lg:max-h-none lg:contents"><SidePanel {store} /></div>
		<main class="order-1 flex min-h-0 min-w-0 flex-1 flex-col gap-3 lg:order-2">
			<div class="flex flex-wrap items-center gap-2.5">
				<button type="button" disabled={!ready} onclick={() => (quickOpen = true)} class="kk-btn kk-btn-hero kk-btn-lg min-w-40 flex-1 md:w-52 md:flex-none"><span class="kk-btn-icon"><Icon name="play_arrow" size={28} fill /></span><span class="kk-btn-label">{t('play.lobby.quickMatch')}</span></button>
				<button type="button" disabled={!ready} onclick={() => toast(t('play.lobby.trophyLeagueTbd'), { kind: 'info', icon: 'event' })} class="kk-btn kk-btn-lg kk-btn-league min-w-40 flex-1 md:w-52 md:flex-none">
					<span class="kk-embers" aria-hidden="true">
						{#each embers as e}<b style="--x:{e.x};--d:{e.d}s;--delay:{e.delay}s;--drift:{e.drift}px"></b>{/each}
					</span>
					<span class="kk-btn-icon"><Icon name="emoji_events" size={28} fill /></span>
					<span class="kk-btn-label">{t('play.lobby.trophyLeague')}</span>
				</button>
				<button type="button" disabled={!ready} onclick={() => (createOpen = true)} class="kk-btn kk-btn-lg min-w-40 flex-1 md:w-52 md:flex-none"><span class="kk-btn-icon"><Icon name="add" size={28} /></span><span class="kk-btn-label">{t('play.lobby.createRoom')}</span></button>
			</div>
			<RoomList {store} onjoin={join} />
		</main>
	</div>
</div>

<ChatPanel {store} />
<CreateRoomDialog {store} bind:open={createOpen} />
<QuickMatchDialog {store} bind:open={quickOpen} />
<RankingDialog {store} bind:open={rankOpen} />
<DictionaryDialog bind:open={dictOpen} />
<PasswordDialog room={passwordRoom} onsubmit={(no, pw) => store.join(no, pw)} />
<RoomPanel {store} />
<ConnectionGate {store} />
<LoadingScreen {store} />
<ToastHost />
