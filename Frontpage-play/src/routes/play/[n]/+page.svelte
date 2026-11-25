<script>
	import { page } from '$app/state';
	import { onMount, onDestroy, untrack, tick } from 'svelte';
	import { createLobbyStore } from '$lib/game/lobbyStore.svelte.js';
	import { blockDevtools } from '$lib/game/security/devtoolsBlock.js';
	import { worn } from '$lib/game/theme.svelte.js';
	import { themeOf } from '$lib/game/theme.js';
	import GameResult from '$lib/game/lobby/GameResult.svelte';
	import LevelUpHost from '$lib/game/lobby/LevelUpHost.svelte';
	import LeagueResultBand from '$lib/game/lobby/LeagueResultBand.svelte';
	import GiftHost from '$lib/game/lobby/GiftHost.svelte';
	import ObtainScreen from '$lib/game/lobby/ObtainScreen.svelte';
	import Icon from '$lib/game/lobby/Icon.svelte';
	import TopBar from '$lib/game/lobby/TopBar.svelte';
	import MobileTabBar from '$lib/game/lobby/MobileTabBar.svelte';
	import SidePanel from '$lib/game/lobby/SidePanel.svelte';
	import RoomList from '$lib/game/lobby/RoomList.svelte';
	import ChatPanel from '$lib/game/lobby/ChatPanel.svelte';
	import MobileHome from '$lib/game/lobby/MobileHome.svelte';
	import MatchHome from '$lib/game/lobby/MatchHome.svelte';
	import PushScreen from '$lib/game/lobby/PushScreen.svelte';
	import FullscreenButton from '$lib/game/lobby/FullscreenButton.svelte';
	import ConnectionGate from '$lib/game/lobby/ConnectionGate.svelte';
	import CreateRoomDialog from '$lib/game/lobby/CreateRoomDialog.svelte';
	import QuickMatchDialog from '$lib/game/lobby/QuickMatchDialog.svelte';
	import PasswordDialog from '$lib/game/lobby/PasswordDialog.svelte';
	import LoadingScreen from '$lib/game/lobby/LoadingScreen.svelte';
	import ToastHost from '$lib/game/ui/ToastHost.svelte';
	import AchievementToastHost from '$lib/game/ui/AchievementToastHost.svelte';
	import { t } from '$lib/game/i18n.svelte.js';
	import { plainText } from '$lib/game/protocol.js';
	import { toast } from '$lib/game/ui/toast.svelte.js';
	import { league } from '$lib/game/leagueStore.svelte.js';
	import { watchIdle } from '$lib/game/autoIdle.js';
	import ShopScreen from '$lib/game/lobby/ShopScreen.svelte';
	import { playSound } from '$lib/game/audio.svelte.js';
	import RankingDialog from '$lib/game/lobby/RankingDialog.svelte';
	import RuleReel from '$lib/game/lobby/RuleReel.svelte';
	import DictionaryDialog from '$lib/game/lobby/DictionaryDialog.svelte';
	import WikiWindow from '$lib/game/lobby/WikiWindow.svelte';
	import WordDetail from '$lib/game/lobby/WordDetail.svelte';
	import DmNotice from '$lib/game/lobby/DmNotice.svelte';
	import ReplayScreen from '$lib/game/lobby/ReplayScreen.svelte';
	import NewsScreen from '$lib/game/lobby/NewsScreen.svelte';
	import CalculatorWindow from '$lib/game/lobby/CalculatorWindow.svelte';
	import PolicyScreen from '$lib/game/lobby/PolicyScreen.svelte';
	import SetupScreen from '$lib/game/lobby/SetupScreen.svelte';
	import ProgressDialog from '$lib/game/lobby/ProgressDialog.svelte';
	import ConfirmHost from '$lib/game/ui/ConfirmHost.svelte';
	import YellBanner from '$lib/game/ui/YellBanner.svelte';
	import { confirm } from '$lib/game/ui/confirm.svelte.js';
	import { prefs } from '$lib/game/prefs.svelte.js';
	import { SPLIT, twoColumns, railWide } from '$lib/game/foldable.svelte.js';
	import { isShut } from '$lib/game/blocks.svelte.js';
	import SettingsDialog from '$lib/game/lobby/SettingsDialog.svelte';
	import ExpiredDialog from '$lib/game/lobby/ExpiredDialog.svelte';
	import ReportFeedbackDialog from '$lib/game/lobby/ReportFeedbackDialog.svelte';
	import SecurityCodeDialog from '$lib/game/lobby/SecurityCodeDialog.svelte';
	import CommandHelpDialog from '$lib/game/lobby/CommandHelpDialog.svelte';
	import TutorialDialog from '$lib/game/tutorial/TutorialDialog.svelte';
	import TutorialSession from '$lib/game/tutorial/TutorialSession.svelte';
	import { lessonById } from '$lib/game/tutorial/lessons.js';
	import { acceptDone } from '$lib/game/tutorial/progress.svelte.js';
	import { clearRef, pendingRef } from '$lib/invite.js';
	import { loadGoodDetails } from '$lib/game/shop.js';
	import AcademyScreen from '$lib/game/academy/AcademyScreen.svelte';
	import { cubicIn } from 'svelte/easing';
	import { ms } from '$lib/game/motion.js';
	import UserMenu from '$lib/game/lobby/UserMenu.svelte';
	import RoomMenu from '$lib/game/lobby/RoomMenu.svelte';
	import RoomInfoDialog from '$lib/game/lobby/RoomInfoDialog.svelte';
	import ReportDialog from '$lib/game/lobby/ReportDialog.svelte';
	import ChatLogDialog from '$lib/game/lobby/ChatLogDialog.svelte';
	import ProfileWindow from '$lib/game/lobby/ProfileWindow.svelte';
	import RatingDialog from '$lib/game/lobby/RatingDialog.svelte';
	import { installUiSounds } from '$lib/game/uiSounds.js';
	import { scheduleMotionBench } from '$lib/game/deviceBench.js';
	import { installUnlock, playLeagueBgm, playLobbyBgm, playShopBgm, stopBgm, wantedBgm } from '$lib/game/audio.svelte.js';
	import RoomScreen from '$lib/game/lobby/RoomScreen.svelte';
	import GameScreen from '$lib/game/lobby/GameScreen.svelte';
	import GameHeader from '$lib/game/lobby/GameHeader.svelte';
	import ResizeHandle from '$lib/game/ui/ResizeHandle.svelte';
	import { guard, linkGuard, unloadGuard } from '$lib/game/leaveGuard.svelte.js';
	import StatusMark from '$lib/game/lobby/StatusMark.svelte';
	import { layout, setLayout, setSideOpen, SIDE, CHAT } from '$lib/game/layout.svelte.js';
	import Tooltip from '$lib/game/ui/Tooltip.svelte';

	// The bars can be resized within limits; the real maximum also leaves the room list / the lobby room enough space.
	let winW = $state(1280);
	// Phone: the menu is a tab bar at the bottom (outside a room), and 사람 (friends, family, who is online) is a page of its own.
	const phone = $derived(winW < 768);
	const duoRail = $derived(railWide(winW, winH)); // the open folding iPhone, wider than tall: navigation moves to a rail at the side (HIG)
	// The soft keyboard takes the lower part of the screen; the page is as tall as what is left, so the answer field (at the bottom of the board) stays in view.
	let vvh = $state(0);
	// The keyboard is up: a text field has the focus and the visible part of the page is clearly shorter than the page was without it.
	// Then the chrome that only takes room (the phone's tab bar, my card under the people list) steps aside for the content.
	let kbd = $state(false);
	let fullH = 0;
	const typing = () => {
		const a = document.activeElement;
		return !!a && (a.tagName === 'TEXTAREA' || (a.tagName === 'INPUT' && !/^(checkbox|radio|button|submit|range|file|color)$/.test(a.type)) || a.isContentEditable);
	};
	// Release bundle only: the development bundle (the game-admin switch, StatusMark's watermark) must keep devtools usable.
	onMount(() => {
		if (typeof __PLAY_BUNDLE__ !== 'undefined' && __PLAY_BUNDLE__ === 'release') return blockDevtools();
	});

	onMount(() => {
		const vv = window.visualViewport;
		if (!vv) return;
		const sync = () => {
			vvh = Math.round(vv.height);
			if (!typing()) fullH = Math.max(vvh, window.innerHeight); // the height without a keyboard (also after a rotation)
			kbd = typing() && fullH - vvh > 120;
			// How much of the page's bottom the keyboard covers: iOS (and Chrome on Android) keep the layout viewport as it was and only shrink the visual one,
			// so a sheet fixed to the bottom of the page would sit behind the keyboard. The bottom sheets lift themselves by this much.
			document.documentElement.style.setProperty('--kb', `${Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop))}px`);
			if (vv.offsetTop) window.scrollTo(0, 0); // iOS slides the page up under the keyboard: put it back
		};
		// A dialog that unmounts while it holds the focus fires focusout in the middle of Svelte's teardown, where writing state is refused
		// (state_unsafe_mutation); so the focus events run `sync` right after that teardown, not inside it.
		const onFocus = () => queueMicrotask(sync);
		sync();
		vv.addEventListener('resize', sync);
		vv.addEventListener('scroll', sync);
		document.addEventListener('focusin', onFocus);
		document.addEventListener('focusout', onFocus);
		return () => {
			document.documentElement.style.removeProperty('--kb');
			document.removeEventListener('focusin', onFocus);
			document.removeEventListener('focusout', onFocus);
			vv.removeEventListener('resize', sync);
			vv.removeEventListener('scroll', sync);
		};
	});
	let peopleTab = $state(false);
	// 로비 선택 (설정): the match-centred lobby (the stage and 게임 시작!) or the room list as before
	const newLobby = $derived(prefs.lobbyStyle === 'match');
	let roomsOpen = $state(false); // the phone's room list: a screen of its own (PushScreen), pushed from the home's 방 목록 button
	// Being in a room closes it (the list is where I came from, the room is where I am now).
	$effect(() => {
		if (store.net.myRoom) roomsOpen = false;
	});
	const tabBar = $derived(phone && !store.net.myRoom);
	const showTabBar = $derived(tabBar && !kbd);
	// An open folding phone that is wider than tall (and too narrow for the two columns): the tab bar stands at the side as a rail, on the dominant hand's edge.
	// Under 768 px it is the phone layout's tab bar standing up; from 768 px (the two columns) it is the header's menu that moves to the side (not in a game: its own header).
	const railPhone = $derived(tabBar && duoRail);
	const railDesk = $derived(!phone && duoRail && !(store.inGame && store.ui.gameCovered));
	const rail = $derived(railPhone || railDesk);
	// The bottom capsule floats over the page: the page runs to the screen's edge, and what scrolls keeps this much room at its end (`--dock`).
	const dockOn = $derived(showTabBar && !rail);
	// The desktop's two columns (side bar beside the room list, chat under it): from 1024 px, and on an unfolded folding phone from 768 px (foldable.svelte.js).
	const desk = $derived(twoColumns(winW));
	// Under 1024 px the two columns are tight (an open fold is 845 px wide: the side bar would take a third of it), so the side bar can be folded away to a slim strip
	// there; until the player chooses, it starts folded. From 1024 px it is always open, as it was.
	const canFold = $derived(desk && winW < SPLIT);
	const sideFolded = $derived(canFold && !layout.sideOpen);
	// The phone's home scrolls to the edges of the page (its scrollbar too): the page's own padding would clip the cards short of the tab bar and push the scrollbar in.
	// The home has the same padding inside its scroller.
	const homeBleed = $derived(phone && !store.net.myRoom && !shopOpen && (!replayOpen || newLobby) && (!newsOpen || newLobby) && (!academyOpen || newLobby) && !peopleTab);
	const peopleShown = $derived(tabBar && peopleTab && !shopOpen && !replayOpen && !newsOpen && !academyOpen);
	// Moving between tabs slides the page the way the tab lies: to the right of this one, it comes in from the right.
	const TAB_ORDER = ['lobby', 'people', 'shop', 'news', 'rank', 'dict', 'replay'];
	let pageEl = $state();
	const tabNow = $derived(shopOpen ? 'shop' : replayOpen ? 'replay' : newsOpen ? 'news' : academyOpen ? 'dict' : peopleShown ? 'people' : 'lobby');
	// 리그 매칭을 기다리는 동안에는 설정 밖의 메뉴로 나갈 수 없다(큐를 비운 채 다른 화면으로 가는 것을 막는다)
	// 리그 판 안에서도 같다: 실력만 겨루는 익명 판이라 다른 메뉴로 나가거나 사전·채팅·소셜을 쓸 수 없다
	const inLeague = $derived(!!store.currentRoom?.league);
	const locked = $derived(league.queued || inLeague);
	// 리그 방에는 대기실이 없다: 판이 끝나고 결과 화면을 닫으면 방을 나가서 로비로 돌아온다(그냥 두면 가림막만 남고 메뉴도 잠겨 아무것도 못 한다).
	// 판이 끝내 시작되지 않는 경우에도 40초 뒤에는 나간다.
	let leagueGameSeen = false;
	$effect(() => {
		if (!inLeague) return void (leagueGameSeen = false);
		if (store.inGame) return void (leagueGameSeen = true);
		if (leagueGameSeen && !store.game) {
			leagueGameSeen = false;
			store.leaveGame();
			return;
		}
		if (leagueGameSeen) return;
		const timer = setTimeout(() => store.leaveGame(), 40_000);
		return () => clearTimeout(timer);
	});
	// 리그 판에서 돌아오면 다시 매칭할 수 있게 하고, 트로피와 시즌 정보를 새로 받는다
	let wasLeague = false;
	$effect(() => {
		if (inLeague) wasLeague = true;
		else if (wasLeague) {
			wasLeague = false;
			league.endMatch();
			league.fetch();
		}
	});
	$effect(() => {
		if (!inLeague) return;
		dictOpen = false; // 사전은 어느 길로도 열 수 없다(단축 요청, 인정 힌트 등)
		profileOpen = false; // 판이 시작될 때 열려 있던 프로필 창도 닫는다
	});
	const lockedOut = (id) => locked && id !== 'settings';
	async function selectTab(id) {
		if (lockedOut(id)) return void toast(t('play.league.queue.locked'), { kind: 'info' });
		const before = tabNow;
		peopleTab = false;
		roomsOpen = false; // any tab (the home's own too) leaves the room list page
		if (id === 'people') {
			selectMenu('lobby');
			peopleTab = true;
		} else selectMenu(id);
		await tick();
		// only a tab that really changed the page moves it (a dialog, the sheet, or a refused tap does not)
		if (tabNow === before || !pageEl || !matchMedia('(prefers-reduced-motion: no-preference)').matches) return;
		const dir = Math.sign(TAB_ORDER.indexOf(tabNow) - TAB_ORDER.indexOf(before)) || 1;
		pageEl.animate([{ transform: `translateX(${dir * 36}px)`, opacity: 0 }, { transform: 'none', opacity: 1 }], { duration: 240, easing: 'cubic-bezier(0.2, 0.9, 0.25, 1)' });
	}
	let winH = $state(800);
	const sideMax = () => Math.max(SIDE.min, Math.min(SIDE.max, winW - 32 - 16 - 480));
	const chatMax = () => Math.max(CHAT.min, Math.min(CHAT.max, winH - 56 - 32 - 12 - 280));
	// A size saved on a bigger screen still has to fit this one.
	const sideW = $derived(Math.min(layout.side, sideMax()));
	// An open fold is two columns but short (the address bar and the system bars take height): the chat shrinks to its smallest and the room list may too.
	const tight = $derived(winW < SPLIT && winH < 760 && twoColumns(winW));
	const chatH = $derived(tight ? CHAT.min : Math.min(layout.chat, chatMax()));
	const leftHanded = $derived(prefs.hand === 'left');

	const store = createLobbyStore(Number(page.params.n));
	// The worn 테마 recolours the whole client (the play layout puts its tokens on the page); taking it off, or leaving, restores the green.
	$effect(() => {
		worn.theme = themeOf(store.shop?.[store.me?.equip?.THM]);
	});
	// Styles keyed on `:root[data-theme]` (the shop's season and bonus decks) follow the worn theme's own light/dark too.
	$effect(() => {
		const root = document.documentElement;
		if (worn.theme) root.dataset.theme = worn.theme.dark ? 'dark' : 'light';
		else delete root.dataset.theme;
	});
	onDestroy(() => {
		worn.theme = null;
		delete document.documentElement.dataset.theme;
	});
	let createOpen = $state(false);
	let settingsOpen = $state(false);
	let quickOpen = $state(false);
	let quickMode = $state(2);
	let passwordRoom = $state(null);
	let rankOpen = $state(false);
	let rankKind = $state('score'); // which ranking the dialog starts on
	let dictOpen = $state(false);
	// a screen can ask for the small dictionary with a word already looked up (the riddle's answer): `store.ui.dictRequest`
	let dictWord = $state('');
	$effect(() => {
		const word = store.ui.dictRequest;
		if (!word) return;
		store.ui.dictRequest = '';
		dictWord = word;
		dictOpen = true;
	});
	$effect(() => {
		if (!dictOpen) dictWord = '';
	});
	let calcOpen = $state(false);
	let progressOpen = $state(false);
	let prefsOpen = $state(false);
	let prefsStart = $state('sound');
	let codeOpen = $state(false);
	let helpOpen = $state(false); // the chat command list (/도움말)
	let tutorialOpen = $state(false); // 학사모레미의 튜토리얼 (the 도움말 button)
	let lessonId = $state(''); // the lesson being played
	let tutorialReward = $state({ open: false, details: null }); // the 100 핑 a first-time clear pays, shown with the item-acquire screen
	let rewardSeen = 0;
	// The server's answer to a cleared lesson: what the account has cleared, and (the first time) the 핑 it paid.
	$effect(() => {
		const r = store.net.tutorial;
		if (!r || r.at === rewardSeen) return;
		rewardSeen = r.at;
		acceptDone(r.done);
		if (r.ping > 0)
			loadGoodDetails().then((details) => {
				tutorialReward.details = details;
				tutorialReward.open = true;
			});
	});
	let profileId = $state(null);
	let profileStart = $state({ tab: 'profile', n: 0 }); // which tab a profile opens on (프로필 관리 for my own, from the footer card / the menu)
	let userMenu = $state();
	let roomMenu = $state();
	let roomInfo = $state();
	let report = $state();
	let chatLogOpen = $state(false);
	// 사전: in the lobby it opens the 아카데미 as a page of its own; in a waiting room it is the small dictionary window.
	let academyOpen = $state(false);
	let academyTab = $state('dictionary');
	let academyGame = $state('');
	// Chat is a button (docked sheet) instead of the inline panel wherever a screen needs the room: the 아카데미 and the shop.
	let shopOpen = $state(false);
	let newsOpen = $state(false); // 공지·행사: another page of the main area; it takes turns with the others
	let replayOpen = $state(false); // 리플레이: a page like the shop and the 아카데미; they take turns
	let replayStart = $state({ game: '', n: 0 }); // a game an outside link (a profile's 경기 내역) wants opened
	let shopStart = $state({ tab: '', sub: '', n: 0 }); // where an outside link (해커레미's 보너스권) wants the shop to open
	// 채팅창 띄우기 (설정 > 게임) makes it so on every screen.
	// the new lobby always floats the chat (its stage takes the room), whatever the 채팅 띄움 setting says; in a room the setting is back
	// The new lobby shows the top bar's pages (공지·행사, 사전, 리플레이) on its blurred stage: the stage hands each one its back button.
	function closePages() {
		replayOpen = newsOpen = false;
		if (academyOpen) setAcademy(false);
	}
	const chatFab = $derived(prefs.chatFloating || (newLobby && !store.net.myRoom) || shopOpen || replayOpen || (academyOpen && !store.net.myRoom));
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
	/** A lesson plays over the lobby: not from a room (that has its own game), and with the lobby's own page showing. */
	function startLesson(id) {
		if (store.currentRoom) return toast(t('play.tutorial.inRoom'), { kind: 'info' });
		tutorialOpen = false;
		shopOpen = replayOpen = false;
		if (academyOpen) setAcademy(false);
		lessonId = id;
	}

	// 상점: replaces the main area (lobby or waiting room), as the legacy shop box replaces the room box.
	function selectMenu(id) {
		if (lockedOut(id)) return void toast(t('play.league.queue.locked'), { kind: 'info' });
		if (id === 'news') {
			shopOpen = replayOpen = false;
			if (academyOpen) setAcademy(false);
			newsOpen = !newsOpen;
			return;
		}
		if (id === 'lobby' || id === 'shop' || id === 'replay' || id === 'dict') newsOpen = false;
		if (id === 'shop') {
			if (store.net.guest) return toast(plainText(t('kkutu.js.error.423')), { kind: 'error' });
			shopOpen = !shopOpen;
			if (shopOpen) {
				replayOpen = false;
				if (academyOpen) setAcademy(false); // the shop and the 사전 page take turns
				playSound('shop');
			}
			return;
		}
		if (id === 'replay') {
			shopOpen = false;
			if (academyOpen) setAcademy(false);
			replayOpen = !replayOpen;
			return;
		}
		if (id === 'lobby') replayOpen = false;
		if (shopOpen && id === 'lobby') shopOpen = false;
		if (id === 'dict') replayOpen = false;
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
			rankKind = 'score';
			rankOpen = true;
			return;
		}
		if (id === 'progress') {
			store.saveProgress();
			return;
		}
		if (id === 'help') {
			tutorialOpen = true;
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
	// Entering a room (a shared link, an invitation) or opening the 사전 page leaves it behind.
	$effect(() => {
		if (store.net.myRoom || academyOpen) newsOpen = false;
	});
	const ready = $derived(store.net.status === 'ready');
	// A friend's link (`?ref=`) kept by the site is sent once, as soon as I am a member in the game; whatever the answer, it is let go of.
	// What the red dot of the events menu needs to know (where each campaign and invitation stands) is asked for whenever the events arrive.
	$effect(() => {
		const list = store.net.events.list;
		if (!ready || store.net.guest) return;
		untrack(() => {
			for (const e of list) {
				if (e.campaign) store.loadCampaign(e.id);
				if (e.invite) store.loadInvite(e.id);
			}
		});
	});
	let linkSent = false;
	$effect(() => {
		if (!ready || store.net.guest || linkSent) return;
		const code = pendingRef();
		if (!code) return;
		linkSent = true;
		store.linkInvite(code);
	});
	let linkSeen = 0;
	$effect(() => {
		const answer = store.net.inviteLinked;
		if (!answer || answer.at === linkSeen) return;
		linkSeen = answer.at;
		clearRef();
		toast(t(`play.news.invite.linked.${answer.result}`), { kind: answer.result === 'ok' ? 'success' : 'info' });
	});

	onMount(() => {
		// What the chat commands (/설정, /정보, /주제) open.
		store.hooks.openSettings = (tab = 'sound') => {
			prefsStart = tab;
			prefsOpen = true;
		};
		store.hooks.openProfile = (who, opts = {}) => {
			if (inLeague) return; // 리그 판에서는 상대(와 나)의 프로필·전적을 볼 수 없다: 카드, 이름, 명령어 모두 이 입구를 지난다
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
			if (store.dressLocked && tab === 'box') return refuse(438);
			store.hooks.openProfile(net.id, { tab: store.dressLocked ? 'profile' : tab });
		};
		store.hooks.openShop = (tab, subTab) => {
			if (store.net.guest) return toast(plainText(t('kkutu.js.error.423')), { kind: 'error' });
			profileOpen = false;
			replayOpen = false;
			if (academyOpen) setAcademy(false);
			if (!shopOpen) playSound('shop');
			shopStart = { tab, sub: subTab, n: shopStart.n + 1 };
			shopOpen = true;
		};
		// 단어 토큰 in my box leads to where it is spent: the 사전's 어인정 조회 (in a waiting room the 사전 is the small window)
		store.hooks.openInjeong = () => {
			profileOpen = false;
			shopOpen = false;
			replayOpen = false;
			if (store.net.myRoom) {
				dictOpen = true;
				return;
			}
			academyTab = 'injeong';
			setAcademy(true);
		};
		// The 이동 button of a mission: where its mission can be done (see `MISSION_GOTO` on the server). The page of events is left first.
		store.hooks.goto = (dest) => {
			if (dest.startsWith('tutorial:')) {
				newsOpen = false;
				return startLesson(dest.slice('tutorial:'.length));
			}
			if (dest === 'rooms') {
				newsOpen = false;
				return;
			}
			if (store.net.myRoom && dest !== 'shop' && dest !== 'gacha') return toast(t('play.news.gotoInRoom'), { kind: 'info' });
			newsOpen = false;
			if (dest === 'shop' || dest === 'gacha') return store.hooks.openShop(dest === 'gacha' ? 'pickup' : '');
			profileOpen = false;
			shopOpen = replayOpen = false;
			academyTab = dest; // 'practice' | 'dictionary'
			setAcademy(true);
		};
		store.hooks.openReplay = (game = '') => {
			profileOpen = false;
			shopOpen = false;
			if (academyOpen) setAcademy(false);
			replayStart = { game, n: replayStart.n + 1 };
			replayOpen = true;
		};
		store.hooks.openRoomSettings = () => (settingsOpen = true);
		store.hooks.setCreateOpen = (v) => (createOpen = v); // the 방 만들기 lesson opens and closes the real dialog
		store.hooks.openHelp = () => (helpOpen = true);
		store.hooks.openRanking = (kind = 'score') => {
			rankKind = kind;
			rankOpen = true;
		};
		store.hooks.openQuick = (mode = 2) => {
			quickMode = mode;
			quickOpen = true;
		};
		store.hooks.openUserMenu = (id, e, layout) => {
			if (inLeague) return; // 같은 이유(친구 추가, 신고, 귓속말 메뉴도 열 수 없다)
			userMenu?.show(id, e, layout);
		};
		store.hooks.openRoomMenu = (no, e) => roomMenu?.show(no, e);
		store.hooks.openRoomInfo = (no) => roomInfo?.show(no);
		store.hooks.openReport = (target) => report?.show(target);
		store.hooks.openChatLog = () => (chatLogOpen = true);
		// The game is played with mouse, touch and its own keys: Tab doesn't walk through the controls (it only drew
		// focus rings around whatever came next).
		const noTab = (e) => e.key === 'Tab' && e.preventDefault();
		window.addEventListener('keydown', noTab, true);
		document.addEventListener('click', linkGuard, true);
		window.addEventListener('beforeunload', unloadGuard);
		const unlock = installUnlock(); // browsers keep audio locked until the first click or key press
		scheduleMotionBench({ onEnabled: () => toast(t('play.motion.autoOn'), { kind: 'info' }) }); // 움직임 감소 is on by default on a device that cannot keep up
		const offUiSounds = installUiSounds(); // 모든 버튼: 누르면 click, 마우스를 올리면 hover 소리
		const stopIdle = watchIdle({ status: () => store.net.myStatus, set: (s, auto) => store.setStatus(s, auto) }); // 자리 비움 by itself after a quiet spell
		store.conn.open();
		return () => {
			stopIdle();
			window.removeEventListener('keydown', noTab, true);
			document.removeEventListener('click', linkGuard, true);
			window.removeEventListener('beforeunload', unloadGuard);
			guard.active = false;
			unlock();
			offUiSounds();
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
		// 리그: 매칭이 잡힌 때부터(규칙 룰렛, 판 시작 전) 메인 BGM을 끈다. 판이 끝난 뒤에는 결과 화면의 음악이 이어받는다.
		if (store.net.status !== 'ready' || store.replayView || league.reelOpen) stopBgm();
		else if (store.currentRoom?.playing) {
			// This runs again whenever the room changes (someone leaves, a setting changes): it must only silence the lobby's music when the game starts,
			// not cut the game's own music (a quiz mode's loop) each time.
			if (!wantedBgm()?.startsWith('game:')) stopBgm();
		} else if (store.game?.result) {
			// between the end of a game and its result screen: silence, then the result's own music (not the lobby's, which waits for the result to be closed)
		} else if (inLeague || league.found) stopBgm();
		else if (league.lobbyOpen) playLeagueBgm(); // 트로피 리그 로비에서는 로비와 다른 음악(인트로 한 번, 그다음 루프)
		else shopOpen ? playShopBgm() : playLobbyBgm({ fromResult: true }); // (a closed result: its music crossfades into the lobby's)
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

	// An address with ?view=replay[&game=ID] opens the 리플레이 page (at that game), ?profile=ID that player's profile
	let queryDone = false;
	$effect(() => {
		if (queryDone || store.net.status !== 'ready') return;
		queryDone = true;
		const q = new URLSearchParams(location.search);
		if (q.get('view') === 'replay') store.hooks.openReplay?.(q.get('game') || '');
		if (q.get('profile')) store.hooks.openProfile?.(q.get('profile'));
	});

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
	// The question is asked once per invitation and outside of tracking: confirm() reads and writes the dialog state, and the inviter's
	// user entry is replaced by every packet about them, so a tracked read here re-asked (and looped) while the dialog sat open.
	const askedInvite = new WeakSet();
	$effect(() => {
		const inv = store.net.invite;
		if (!inv) return;
		if (prefs.denyInvite || isShut(inv.by)) return store.answerInvite(inv.from, false);
		untrack(() => {
			if (askedInvite.has(inv)) return;
			askedInvite.add(inv);
			const by = store.net.users[inv.by];
			const message = by ? t('play.confirm.invited', { nick: by.name, room: inv.from }) : t('play.confirm.invitedAnon', { room: inv.from });
			confirm({ message }).then((yes) => {
				if (yes) store.answerInvite(inv.from, true);
				else if (store.net.invite === inv) store.net.invite = null; // not a newer invitation that replaced this one
			});
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

	// Events: the catalog sent at login does not change by itself, so an event that begins while I play (or ends) is looked for every few minutes in the lobby.
	$effect(() => {
		if (store.net.status !== 'ready' || store.net.guest || store.net.myRoom) return;
		const again = setInterval(() => store.loadEvents(), 300_000);
		return () => clearInterval(again);
	});

	function join(no) {
		const room = store.net.rooms[no];
		if (room?.locked) passwordRoom = room;
		else store.join(no);
	}
</script>

<svelte:head><title>끄투리오 - 게임</title></svelte:head>

<svelte:window bind:innerWidth={winW} bind:innerHeight={winH} />

<!-- overflow-x-clip: the tab slide moves the whole page sideways; without the clip the document grows wider for a moment and the fixed chat button is pushed off its corner -->
<div style:height={phone && vvh ? `${vvh}px` : undefined} style:--dock={dockOn && !store.ui.arcade ? 'calc(4.75rem + env(safe-area-inset-bottom))' : '0px'} class="relative flex h-dvh min-h-[600px] max-md:min-h-0 flex-col overflow-x-clip bg-(--kk-bg) text-sm text-(color:--kk-text)">
	{#if store.inGame && store.ui.gameCovered}
		<GameHeader {store} ondict={() => (dictOpen = true)} oncalc={() => (calcOpen = true)} />
	{:else}
		<TopBar {store} {phone} {locked} rail={duoRail} noHelp={newLobby && !store.net.myRoom} active={shopOpen ? 'shop' : replayOpen ? 'replay' : newsOpen ? 'news' : academyOpen && !store.net.myRoom ? 'dict' : 'lobby'} onselect={selectMenu} />
	{/if}
	<div class="flex min-h-0 min-w-0 flex-1 {rail ? (prefs.hand === 'left' ? 'flex-row-reverse' : 'flex-row') : 'flex-col'}">
	<div bind:this={pageEl} class="flex min-h-0 min-w-0 flex-1 {desk ? 'flex-row overflow-hidden' : 'flex-col overflow-y-auto'} gap-4 overflow-x-hidden p-4 max-md:gap-3 max-md:p-3 {phone && store.ui.phoneStage ? 'max-md:gap-0! max-md:p-0!' : ''} {homeBleed ? 'max-md:p-0!' : 'max-md:pb-[calc(0.75rem+var(--dock,0px))]'}">
		<!-- Right hand: the side bar is on the left (default). Left hand: it moves to the right of the room list. -->
		<div style:--side-w="{canFold ? 56 : sideW}px" class="relative flex {desk ? `w-(--side-w) shrink-0 ${leftHanded ? 'order-3' : 'order-1'}` : 'order-2 max-h-72'} {store.inGame && store.ui.gameCovered ? 'max-xl:hidden!' : ''} {shopOpen || inLeague ? 'hidden!' : ''} {tabBar ? (peopleShown ? 'max-md:max-h-none! max-md:min-h-0 max-md:flex-1' : 'max-md:hidden!') : ''} {phone && store.net.myRoom ? 'hidden!' : ''}">
			{#if canFold}
				<!-- where it can be folded the column is always this slim strip, with the one button that opens it (and a mark while friend requests wait); opened, the side bar floats over the room list instead of taking its width -->
				<div class="kk-card flex h-full w-full flex-col items-center py-2">
					<Tooltip text={t('play.side.open')} placement={leftHanded ? 'left' : 'right'}>
						<button type="button" aria-label={t('play.side.open')} aria-expanded="false" onclick={() => setSideOpen(true)} class="kk-btn relative grid size-10 min-h-0 place-items-center p-0">
							<Icon name="group" size={22} />
							{#if store.pendingFriends.incoming.length}<span class="absolute -top-1.5 -right-1.5 grid h-5 min-w-5 place-items-center rounded-full bg-(--kk-danger) px-1 text-[11px] font-extrabold text-white">{store.pendingFriends.incoming.length}</span>{/if}
						</button>
					</Tooltip>
				</div>
			{/if}
			{#if !sideFolded}
				<div style:width={canFold ? `${sideW}px` : undefined} class={canFold ? `absolute inset-y-0 z-30 flex rounded-2xl shadow-2xl ${leftHanded ? 'right-0' : 'left-0'}` : 'contents'}>
				<SidePanel {store} profile={(peopleShown || desk) && !kbd} noAvatar={newLobby && desk && !store.net.myRoom && !peopleShown} />
				{#if canFold}
					<Tooltip text={t('play.side.fold')} placement={leftHanded ? 'right' : 'left'}>
						<button type="button" aria-label={t('play.side.fold')} aria-expanded="true" onclick={() => setSideOpen(false)} class="absolute top-1/2 z-10 grid h-12 w-5 -translate-y-1/2 place-items-center border border-(color:--kk-line) bg-(--kk-surface) text-(color:--kk-muted) hover:text-(color:--kk-text) {leftHanded ? 'left-0 rounded-r-lg border-l-0' : 'right-0 rounded-l-lg border-r-0'}"><Icon name={leftHanded ? 'chevron_right' : 'chevron_left'} size={18} /></button>
					</Tooltip>
				{/if}
				<ResizeHandle class="-right-4 h-full w-4 {desk && !leftHanded ? 'block' : 'hidden'}" orientation="vertical" grow={1} value={sideW} min={SIDE.min} max={sideMax} def={SIDE.def} label={t('play.layout.resizeSide')} onchange={(v) => setLayout('side', v)} />
				<ResizeHandle class="-left-4 h-full w-4 {desk && leftHanded ? 'block' : 'hidden'}" orientation="vertical" grow={-1} value={sideW} min={SIDE.min} max={sideMax} def={SIDE.def} label={t('play.layout.resizeSide')} onchange={(v) => setLayout('side', v)} />
				</div>
			{/if}
		</div>
		<!-- overflow-hidden clips the room while it slides in; the bottom padding keeps the cards' hard shadow visible -->
		<main class="relative order-1 flex min-h-0 min-w-0 flex-1 flex-col gap-3 overflow-hidden {desk ? 'order-2' : ''} pb-[3px] {peopleShown ? 'max-md:hidden' : ''} {(phone && store.ui.phoneStage) || homeBleed ? 'max-md:pb-0!' : ''}">
			{#if shopOpen}
				<div class="flex min-h-0 flex-1 flex-col" out:sink|global><ShopScreen {store} start={shopStart} onclose={() => (shopOpen = false)} /></div>
			{:else if replayOpen && !newLobby}
				<div class="flex min-h-0 flex-1 flex-col" out:sink|global><ReplayScreen {store} start={replayStart} /></div>
			{:else if newsOpen && !newLobby}
				<div class="flex min-h-0 flex-1 flex-col" out:sink|global><NewsScreen {store} /></div>
			{:else if store.net.myRoom && store.currentRoom}
				<!-- the waiting room stays until the loading veil covers it; the board is set up beneath the veil -->
				<!-- 리그 판은 대기실 없이 서버가 바로 시작한다: 그 사이에는 준비·초대·채팅이 있는 일반 대기실 대신 규칙 릴과 같은 바탕만 보인다 -->
				{#if inLeague}{#if !store.inGame}<div class="kk-arcade min-h-0 flex-1 rounded-2xl" style="--ac: #3553a0"><div class="kk-checker-clip"><span class="kk-checker"></span></div></div>{/if}
				{:else if !(store.inGame && store.ui.gameCovered)}<RoomScreen {store} onsettings={() => (settingsOpen = true)} />{/if}
				{#if store.inGame}<GameScreen {store} />{/if}
			{:else if academyOpen && !newLobby}
				<div class="flex min-h-0 flex-1 flex-col" out:sink|global><AcademyScreen {store} bind:tab={academyTab} game={academyGame} /></div>
			{:else if newLobby}
				<MatchHome {store} {ready} variant={phone ? 'phone' : 'desk'} oncreate={() => (createOpen = true)} ontutorial={() => (tutorialOpen = true)} page={replayOpen ? 'replay' : newsOpen ? 'news' : academyOpen ? 'dict' : null} {screen} onpageclose={closePages} onjoin={join} />
			{:else if phone}
				<!-- phone: the home (the games); the room list is a screen of its own, pushed over it (below, like the mailbox and the shop) -->
				<MobileHome {store} {ready} onselect={selectTab} onrooms={() => (roomsOpen = true)} oncreate={() => (createOpen = true)} />
			{:else}
				<div class="flex min-h-0 flex-1 flex-col gap-3">
				<div class="grid grid-cols-2 gap-2.5 md:flex md:flex-wrap md:items-center">
					<button type="button" disabled={!ready} data-tour="quick-match" onclick={() => store.hooks.openQuick(2)} class="kk-btn kk-btn-hero kk-btn-lg col-span-2 min-w-40 flex-1 md:w-52 md:flex-none"><span class="kk-btn-icon"><Icon name="play_arrow" size={28} fill /></span><span class="kk-btn-label">{t('play.lobby.quickMatch')}</span></button>
					<button type="button" disabled={!ready} data-tour="create-room" onclick={() => (createOpen = true)} class="kk-btn kk-btn-lg min-w-40 flex-1 md:w-52 md:flex-none"><span class="kk-btn-icon"><Icon name="add" size={28} /></span><span class="kk-btn-label">{t('play.lobby.createRoom')}</span></button>
				</div>
				<RoomList {store} onjoin={join} compact={tight} />
				</div>
			{/if}
			<!-- Desktop: chat lives under the room list, where the old client had it. -->
			{#if !chatFab && !inLeague}
				<div data-tour="chat" class="relative z-10 {desk ? 'block' : 'hidden'}">
					<ResizeHandle class="-top-3 right-0 left-0 h-3 w-full" orientation="horizontal" grow={-1} value={chatH} min={CHAT.min} max={chatMax} def={CHAT.def} label={t('play.layout.resizeChat')} onchange={(v) => setLayout('chat', v)} />
					<ChatPanel {store} inline height={chatH} />
				</div>
			{/if}
		</main>
	</div>
	{#if railDesk}<MobileTabBar {store} {locked} noHelp={newLobby} noPeople active={shopOpen ? 'shop' : replayOpen ? 'replay' : newsOpen ? 'news' : academyOpen && !store.net.myRoom ? 'dict' : 'lobby'} onselect={selectMenu} side edge={prefs.hand === 'left' ? 'left' : 'right'} />{:else if showTabBar && !store.ui.arcade}<MobileTabBar {store} {locked} noHelp={newLobby} active={tabNow} onselect={selectTab} side={rail} edge={prefs.hand === 'left' ? 'left' : 'right'} />{/if}
	</div>
</div>

<!-- Mobile/tablet: a button opening a docked sheet instead. -->
<!-- Mobile/tablet: a button opening a docked sheet; in the 아카데미 and the shop the chat is that button on desktop too, so the screen gets the room. -->
{#if !(phone && shopOpen)}
{#if !inLeague}<div class={chatFab || !desk ? 'contents' : 'hidden'}><ChatPanel {store} rail={rail ? (prefs.hand === 'left' ? 'left' : 'right') : null} fab={chatFab} hideFab={store.ui.phoneStage || store.ui.hideFab} lift={tabBar ? !rail : phone && store.inGame ? 'top' : phone && store.net.myRoom ? 'room' : false} /></div>{/if}
{/if}
<!-- phone: full screen is a faint icon in the top right corner (the bars have no room for a button) -->
{#if phone}<FullscreenButton watermark />{/if}
<CreateRoomDialog {store} bind:open={createOpen} />
<CreateRoomDialog {store} bind:open={settingsOpen} editing={store.currentRoom} />
<QuickMatchDialog {store} start={quickMode} bind:open={quickOpen} />
<DmNotice {store} />
<RankingDialog {store} bind:open={rankOpen} startKind={rankKind} />
<RuleReel {store} />
<DictionaryDialog bind:open={dictOpen} initial={dictWord} />
<WordDetail />
<WikiWindow />
<CalculatorWindow bind:open={calcOpen} />
<SettingsDialog {store} bind:open={prefsOpen} start={prefsStart} />
<SecurityCodeDialog {store} bind:open={codeOpen} />
<ExpiredDialog {store} />
<ReportFeedbackDialog {store} />
<CommandHelpDialog bind:open={helpOpen} />
<TutorialDialog {store} bind:open={tutorialOpen} onstart={startLesson} oncommands={() => ((tutorialOpen = false), (helpOpen = true))} />
{#if lessonId}<TutorialSession {store} lesson={lessonById(lessonId)} onexit={() => (lessonId = '')} />{/if}
<!-- phone: the room list is a screen of its own, as the mailbox and the shop are (declared before the menus, which must lie over it) -->
<PushScreen open={roomsOpen && phone} title={t('play.home.rooms')} onclose={() => (roomsOpen = false)}>
	{#snippet actions()}
		<button type="button" disabled={!ready} onclick={() => (createOpen = true)} class="inline-flex h-11 items-center gap-1.5 rounded-full px-3 text-sm font-extrabold text-(color:--kk-accent-strong) hover:bg-(--kk-surface-2) disabled:opacity-50"><Icon name="add" size={22} />{t('play.lobby.createRoom')}</button>
	{/snippet}
	<RoomList {store} onjoin={join} bare />
</PushScreen>
{#snippet screen(lead)}
	{#if replayOpen}<ReplayScreen {store} start={replayStart} {lead} />
	{:else if newsOpen}<NewsScreen {store} bare {lead} />
	{:else if academyOpen}<div class="flex min-h-0 flex-1 flex-col px-3 pt-2 pb-3"><AcademyScreen {store} bind:tab={academyTab} game={academyGame} {lead} /></div>{/if}
{/snippet}
<UserMenu bind:this={userMenu} {store} />
<RoomMenu bind:this={roomMenu} {store} />
<RoomInfoDialog bind:this={roomInfo} {store} onjoin={join} />
<ReportDialog bind:this={report} {store} />
<ChatLogDialog bind:open={chatLogOpen} {store} />
<RatingDialog />
<ProfileWindow {store} userId={profileId} start={profileStart} bind:open={profileOpen} onai={(u) => store.hooks.openAi?.(u)} />
<ProgressDialog {store} bind:open={progressOpen} />
<PasswordDialog bind:room={passwordRoom} onsubmit={(no, pw) => store.join(no, pw)} />
<SetupScreen {store} />
<PolicyScreen {store} />
<ConnectionGate {store} />
<LoadingScreen {store} />
<StatusMark {store} />
<LevelUpHost {store} />
<LeagueResultBand {store} />
<GiftHost {store} />
{#if tutorialReward.details}<ObtainScreen bind:open={tutorialReward.open} title={t('play.obtain.acquired')} notice={t('play.tutorial.reward.notice')} items={[{ key: 'ping', q: store.net.tutorial?.ping || 100 }]} shop={store.shop} details={tutorialReward.details} ribbon ambient="shine" palette="teal" />{/if}
<ToastHost />
<AchievementToastHost />
<YellBanner />
<ConfirmHost />
