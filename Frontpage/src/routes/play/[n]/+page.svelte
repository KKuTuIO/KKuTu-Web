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
	import { plainText } from '$lib/game/protocol.js';
	import { toast } from '$lib/game/ui/toast.svelte.js';
	import { watchIdle } from '$lib/game/autoIdle.js';
	import ShopScreen from '$lib/game/lobby/ShopScreen.svelte';
	import { playSound } from '$lib/game/audio.svelte.js';
	import RankingDialog from '$lib/game/lobby/RankingDialog.svelte';
	import DictionaryDialog from '$lib/game/lobby/DictionaryDialog.svelte';
	import CalculatorWindow from '$lib/game/lobby/CalculatorWindow.svelte';
	import PolicyScreen from '$lib/game/lobby/PolicyScreen.svelte';
	import SetupScreen from '$lib/game/lobby/SetupScreen.svelte';
	import ProgressDialog from '$lib/game/lobby/ProgressDialog.svelte';
	import ConfirmHost from '$lib/game/ui/ConfirmHost.svelte';
	import { confirm } from '$lib/game/ui/confirm.svelte.js';
	import { prefs } from '$lib/game/prefs.svelte.js';
	import { isShut } from '$lib/game/blocks.svelte.js';
	import SettingsDialog from '$lib/game/lobby/SettingsDialog.svelte';
	import SecurityCodeDialog from '$lib/game/lobby/SecurityCodeDialog.svelte';
	import CommandHelpDialog from '$lib/game/lobby/CommandHelpDialog.svelte';
	import AcademyScreen from '$lib/game/academy/AcademyScreen.svelte';
	import { cubicIn } from 'svelte/easing';
	import { ms } from '$lib/game/motion.js';
	import UserMenu from '$lib/game/lobby/UserMenu.svelte';
	import ProfileWindow from '$lib/game/lobby/ProfileWindow.svelte';
	import { installUnlock, playLobbyBgm, playShopBgm, stopBgm } from '$lib/game/audio.svelte.js';
	import RoomScreen from '$lib/game/lobby/RoomScreen.svelte';
	import GameScreen from '$lib/game/lobby/GameScreen.svelte';
	import GameHeader from '$lib/game/lobby/GameHeader.svelte';
	import ResizeHandle from '$lib/game/ui/ResizeHandle.svelte';
	import { guard, linkGuard, unloadGuard } from '$lib/game/leaveGuard.svelte.js';
	import StatusMark from '$lib/game/lobby/StatusMark.svelte';
	import { layout, setLayout, SIDE, CHAT } from '$lib/game/layout.svelte.js';

	// The bars can be resized within limits; the real maximum also leaves the room list / the lobby room enough space.
	let winW = $state(1280);
	let winH = $state(800);
	const sideMax = () => Math.max(SIDE.min, Math.min(SIDE.max, winW - 32 - 16 - 480));
	const chatMax = () => Math.max(CHAT.min, Math.min(CHAT.max, winH - 56 - 32 - 12 - 280));
	// A size saved on a bigger screen still has to fit this one.
	const sideW = $derived(Math.min(layout.side, sideMax()));
	const chatH = $derived(Math.min(layout.chat, chatMax()));
	const leftHanded = $derived(prefs.hand === 'left');

	const store = createLobbyStore(Number(page.params.n));
	let createOpen = $state(false);
	let settingsOpen = $state(false);
	let quickOpen = $state(false);
	let passwordRoom = $state(null);
	let rankOpen = $state(false);
	let dictOpen = $state(false);
	let calcOpen = $state(false);
	let progressOpen = $state(false);
	let prefsOpen = $state(false);
	let prefsStart = $state('sound');
	let codeOpen = $state(false);
	let helpOpen = $state(false);
	let profileId = $state(null);
	let profileStart = $state({ tab: 'profile', n: 0 }); // which tab a profile opens on (프로필 관리 for my own, from the footer card / the menu)
	let userMenu = $state();
	// 사전: in the lobby it opens the 아카데미 as a page of its own; in a waiting room it is the small dictionary window.
	let academyOpen = $state(false);
	let academyTab = $state('dictionary');
	let academyGame = $state('');
	// Chat is a button (docked sheet) instead of the inline panel wherever a screen needs the room: the 아카데미 and the shop.
	let shopOpen = $state(false);
	let shopStart = $state({ tab: '', sub: '', n: 0 }); // where an outside link (해커레미's 보너스권) wants the shop to open
	const chatFab = $derived(shopOpen || (academyOpen && !store.net.myRoom));
	// Leaving: the academy sinks and fades while the lobby is already there (it is lifted out of the layout so nothing jumps).
	function sink(node, { duration = 260 } = {}) {
		const { offsetTop: top, offsetLeft: left, offsetWidth: width, offsetHeight: height } = node;
		Object.assign(node.style, { position: 'absolute', top: `${top}px`, left: `${left}px`, width: `${width}px`, height: `${height}px`, zIndex: 5 });
		return { duration: ms(duration), easing: cubicIn, css: (t, u) => `transform:translateY(${u * 8}%);opacity:${t}` };
	}
	function setAcademy(on) {
		academyOpen = on;
		const url = new URL(location.href);
		if (!on) for (const k of ['view', 'tab', 'game']) url.searchParams.delete(k);
		else url.searchParams.set('view', 'academy');
		history.replaceState(history.state, '', url);
	}
	let profileOpen = $state(false);
	// 상점: replaces the main area (lobby or waiting room), as the legacy shop box replaces the room box.
	function selectMenu(id) {
		if (id === 'shop') {
			if (store.net.guest) return toast(plainText(t('kkutu.js.error.423')), { kind: 'error' });
			shopOpen = !shopOpen;
			if (shopOpen) {
				if (academyOpen) setAcademy(false); // the shop and the 사전 page take turns
				playSound('shop');
			}
			return;
		}
		if (shopOpen && id === 'lobby') shopOpen = false;
		if (id === 'dict' && shopOpen) {
			// 상점 -> 사전: the page replaces the shop; in a waiting room the small window opens over it
			if (store.net.myRoom) dictOpen = true;
			else {
				shopOpen = false;
				setAcademy(true);
			}
			return;
		}
		if (id === 'lobby') return academyOpen && setAcademy(false);
		if (id === 'rank') {
			rankOpen = true;
			return;
		}
		if (id === 'progress') {
			store.saveProgress();
			return;
		}
		if (id === 'settings') {
			prefsStart = 'sound';
			prefsOpen = true;
			return;
		}
		if (id === 'dict') {
			if (store.net.myRoom) dictOpen = true;
			else setAcademy(!academyOpen);
			return;
		}
		toast(t('play.nav.notReady', { name: t(`play.nav.${id}`) }), { kind: 'info' });
	}
	const ready = $derived(store.net.status === 'ready');
	// Embers for the 트로피 리그 button (fixed values: the pattern is decoration, not data).
	const embers = Array.from({ length: 10 }, (_, i) => ({ x: 6 + ((i * 29) % 86), d: 0.9 + ((i * 11) % 5) * 0.2, delay: -i * 0.21, drift: (i % 2 ? 1 : -1) * (4 + (i % 3) * 3) }));

	onMount(() => {
		// What the chat commands (/설정, /정보, /주제) open.
		store.hooks.openSettings = (tab = 'sound') => {
			prefsStart = tab;
			prefsOpen = true;
		};
		store.hooks.openProfile = (who, opts = {}) => {
			profileId = typeof who === 'string' ? who : who.id;
			profileStart = { tab: opts.tab ?? 'profile', n: profileStart.n + 1 };
			profileOpen = true;
		};
		// Legacy 프로필 관리 (the dress dialog): my footer card and the menu's item open it; guests may not (421), nor may a player in a running game (438).
		// `tab`: the menu's 프로필 관리 opens that tab ('box'); the card at the foot of the side panel opens the profile itself
		store.hooks.openDress = (tab = 'box') => {
			const net = store.net;
			const refuse = (code) => toast(plainText(net.meta.labels[`kkutu.js.error.${code}`] ?? ''), { kind: 'error' });
			if (net.guest) return refuse(421);
			const mine = net.users[net.id];
			if (store.currentRoom?.playing && mine?.game.form !== 'S' && !mine?.game.practice) return refuse(438);
			store.hooks.openProfile(net.id, { tab });
		};
		store.hooks.openShop = (tab, subTab) => {
			if (store.net.guest) return toast(plainText(t('kkutu.js.error.423')), { kind: 'error' });
			profileOpen = false;
			if (academyOpen) setAcademy(false);
			if (!shopOpen) playSound('shop');
			shopStart = { tab, sub: subTab, n: shopStart.n + 1 };
			shopOpen = true;
		};
		store.hooks.openRoomSettings = () => (settingsOpen = true);
		store.hooks.openHelp = () => (helpOpen = true);
		store.hooks.openUserMenu = (id, e, layout) => userMenu?.show(id, e, layout);
		// The game is played with mouse, touch and its own keys: Tab doesn't walk through the controls (it only drew
		// focus rings around whatever came next).
		const noTab = (e) => e.key === 'Tab' && e.preventDefault();
		window.addEventListener('keydown', noTab, true);
		document.addEventListener('click', linkGuard, true);
		window.addEventListener('beforeunload', unloadGuard);
		const unlock = installUnlock(); // browsers keep audio locked until the first click or key press
		const stopIdle = watchIdle({ status: () => store.net.myStatus, set: (s, auto) => store.setStatus(s, auto) }); // 자리 비움 by itself after a quiet spell
		store.conn.open();
		return () => {
			stopIdle();
			window.removeEventListener('keydown', noTab, true);
			document.removeEventListener('click', linkGuard, true);
			window.removeEventListener('beforeunload', unloadGuard);
			guard.active = false;
			unlock();
			stopBgm();
			store.conn.close();
		};
	});

	// A game that starts covers the shop.
	$effect(() => {
		if (store.inGame && store.ui.gameCovered) shopOpen = false;
	});

	// Mid-game, leaving the page ends the game: links and navigation ask first (leaveGuard.svelte.js).
	$effect(() => {
		guard.active = !!store.currentRoom?.playing;
	});

	// Lobby music plays in the lobby and in waiting rooms (the shop has its own, crossfaded); the game will bring its own.
	$effect(() => {
		if (store.net.status === 'ready' && !store.currentRoom?.playing) shopOpen ? playShopBgm() : playLobbyBgm();
		else stopBgm();
	});

	// An address with ?view=academy&tab=… (a shared link) opens that tool.
	{
		const q = typeof location !== 'undefined' ? new URLSearchParams(location.search) : null;
		if (q?.get('view') === 'academy') {
			academyOpen = true;
			academyTab = q.get('tab') || 'dictionary';
			academyGame = q.get('game') || '';
		}
	}

	// The address mirrors the room you're in (#123), and opening an address with #123 joins that room, like the legacy client.
	const initialRoom = typeof location !== 'undefined' ? Number(location.hash.match(/^#(\d+)$/)?.[1] ?? NaN) : NaN;
	let hashSynced = $state(false);
	$effect(() => {
		if (store.net.status !== 'ready' || hashSynced) return;
		hashSynced = true;
		if (Number.isFinite(initialRoom)) {
			if (store.net.rooms[initialRoom]) join(initialRoom);
			else toast(t('play.rooms.notFound.title', { no: initialRoom }), { kind: 'info' });
		}
	});
	$effect(() => {
		if (!hashSynced) return;
		const n = store.net.myRoom;
		history.replaceState(history.state, '', location.pathname + location.search + (n ? `#${n}` : ''));
	});

	// An invitation from another player: ask, or turn it down automatically if the player chose "초대 거부".
	$effect(() => {
		const inv = store.net.invite;
		if (!inv) return;
		if (prefs.denyInvite || isShut(inv.by)) return store.answerInvite(inv.from, false);
		const by = store.net.users[inv.by];
		const message = by ? t('play.confirm.invited', { nick: by.name, room: inv.from }) : t('play.confirm.invitedAnon', { room: inv.from });
		confirm({ message }).then((yes) => {
			if (yes) store.answerInvite(inv.from, true);
			else store.net.invite = null;
		});
	});

	// A friend request waits in the mail menu's 알림. It is turned down at once by "친구 추가 거부" or when I blocked them.
	const askedAbout = new WeakSet();
	$effect(() => {
		for (const req of [...store.net.friendRequests]) {
			if (askedAbout.has(req)) continue;
			askedAbout.add(req);
			if (prefs.denyFriend || isShut(req.from)) store.answerFriendRequest(req.from, false);
			else toast(t('play.friend.newRequest'), { icon: 'alert' });
		}
	});

	// Mail: look for letters shortly after connecting and every couple of minutes (the server doesn't announce them).
	$effect(() => {
		if (store.net.status !== 'ready' || store.net.guest) return;
		const first = setTimeout(() => store.requestMailbox(), 2000);
		const again = setInterval(() => store.requestMailbox(), 120_000);
		return () => {
			clearTimeout(first);
			clearInterval(again);
		};
	});

	function join(no) {
		const room = store.net.rooms[no];
		if (room?.locked) passwordRoom = room;
		else store.join(no);
	}
</script>

<svelte:window bind:innerWidth={winW} bind:innerHeight={winH} />

<div class="flex h-dvh min-h-[600px] flex-col bg-(--kk-bg) text-sm text-(color:--kk-text)">
	{#if store.inGame && store.ui.gameCovered}
		<GameHeader {store} ondict={() => (dictOpen = true)} oncalc={() => (calcOpen = true)} />
	{:else}
		<TopBar {store} active={shopOpen ? 'shop' : academyOpen && !store.net.myRoom ? 'dict' : 'lobby'} onselect={selectMenu} />
	{/if}
	<div class="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4 lg:flex-row lg:overflow-hidden">
		<!-- Right hand: the side bar is on the left (default). Left hand: it moves to the right of the room list. -->
		<div style:--side-w="{sideW}px" class="relative order-2 flex max-h-72 lg:max-h-none lg:w-(--side-w) lg:shrink-0 {leftHanded ? 'lg:order-3' : 'lg:order-1'} {store.inGame && store.ui.gameCovered ? 'max-xl:hidden!' : ''} {shopOpen ? 'hidden!' : ''}">
			<SidePanel {store} />
			<ResizeHandle class="-right-4 hidden h-full w-4 lg:block {leftHanded ? 'lg:hidden' : ''}" orientation="vertical" grow={1} value={sideW} min={SIDE.min} max={sideMax} def={SIDE.def} label={t('play.layout.resizeSide')} onchange={(v) => setLayout('side', v)} />
			<ResizeHandle class="-left-4 hidden h-full w-4 {leftHanded ? 'lg:block' : ''}" orientation="vertical" grow={-1} value={sideW} min={SIDE.min} max={sideMax} def={SIDE.def} label={t('play.layout.resizeSide')} onchange={(v) => setLayout('side', v)} />
		</div>
		<!-- overflow-hidden clips the room while it slides in; the bottom padding keeps the cards' hard shadow visible -->
		<main class="relative order-1 flex min-h-0 min-w-0 flex-1 flex-col gap-3 overflow-hidden pb-[3px] lg:order-2">
			{#if shopOpen}
				<div class="flex min-h-0 flex-1 flex-col" out:sink|global><ShopScreen {store} start={shopStart} /></div>
			{:else if store.net.myRoom && store.currentRoom}
				<!-- the waiting room stays until the loading veil covers it; the board is set up beneath the veil -->
				{#if !(store.inGame && store.ui.gameCovered)}<RoomScreen {store} onsettings={() => (settingsOpen = true)} />{/if}
				{#if store.inGame}<GameScreen {store} />{/if}
			{:else if academyOpen}
				<div class="flex min-h-0 flex-1 flex-col" out:sink|global><AcademyScreen {store} bind:tab={academyTab} game={academyGame} /></div>
			{:else}
				<div class="flex min-h-0 flex-1 flex-col gap-3">
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
				</div>
			{/if}
			<!-- Desktop: chat lives under the room list, where the old client had it. -->
			{#if !chatFab}
				<div class="relative z-10 hidden lg:block">
					<ResizeHandle class="-top-3 right-0 left-0 h-3 w-full" orientation="horizontal" grow={-1} value={chatH} min={CHAT.min} max={chatMax} def={CHAT.def} label={t('play.layout.resizeChat')} onchange={(v) => setLayout('chat', v)} />
					<ChatPanel {store} inline height={chatH} />
				</div>
			{/if}
		</main>
	</div>
</div>

<!-- Mobile/tablet: a button opening a docked sheet instead. -->
<!-- Mobile/tablet: a button opening a docked sheet; in the 아카데미 and the shop the chat is that button on desktop too, so the screen gets the room. -->
<div class={chatFab ? 'contents' : 'contents lg:hidden'}><ChatPanel {store} fab={chatFab} /></div>
<CreateRoomDialog {store} bind:open={createOpen} />
<CreateRoomDialog {store} bind:open={settingsOpen} editing={store.currentRoom} />
<QuickMatchDialog {store} bind:open={quickOpen} />
<RankingDialog {store} bind:open={rankOpen} />
<DictionaryDialog bind:open={dictOpen} />
<CalculatorWindow bind:open={calcOpen} />
<SettingsDialog {store} bind:open={prefsOpen} start={prefsStart} />
<SecurityCodeDialog {store} bind:open={codeOpen} />
<CommandHelpDialog bind:open={helpOpen} />
<UserMenu bind:this={userMenu} {store} />
<ProfileWindow {store} userId={profileId} start={profileStart} bind:open={profileOpen} onai={(u) => store.hooks.openAi?.(u)} />
<ProgressDialog {store} bind:open={progressOpen} />
<PasswordDialog bind:room={passwordRoom} onsubmit={(no, pw) => store.join(no, pw)} />
<SetupScreen {store} />
<PolicyScreen {store} />
<ConnectionGate {store} />
<LoadingScreen {store} />
<StatusMark {store} />
<ToastHost />
<ConfirmHost />
