<script nonce="kkutuio">
	// @ts-ignore
	import { page } from '$app/stores';
	import { onMount } from 'svelte';

	import ProfileSwitcher from '$lib/ProfileSwitcher.svelte';
	import {loadAuth, loadBlock, loadUser} from '$lib/session.js';

	import { showDialog } from './dialogStore';

	let user = "Guest User";
	let authVendor = "KKuTu";
	let vendorId = "0";
	let activeProfileId = "";
	let name = "Moremi";
	let ingameName = "";
	let mobileMenuOpen = false;

	// 타임 릴리즈
	let showRelayUpdate = false;
	let mourning = false;
	let eighthevent = false;


	let data = { status: "Guest user" };
	let accountRestricted = false;
	var noticeData = "";

    function processNick(nick) {
        if (typeof nick !== 'string' || nick.length === 0) return '계정 설정 필요';
        const [title, discriminator] = nick.split("#", 2);
        return title + (discriminator ? `<small style="color:#bbb">#${discriminator}</small>` : "");
    }

    function getUserId(provider, id) {
        const normalizedProvider = typeof provider === 'string' ? provider.trim().toLowerCase() : '';
        const normalizedId = id === undefined || id === null ? '' : String(id).trim();
        if (!normalizedProvider || !normalizedId) return '';
        return normalizedProvider === 'local' ? normalizedId : `${normalizedProvider}-${normalizedId}`;
    }

	function guardRestrictedNavigation(event) {
		if (!accountRestricted) return;
		event.preventDefault();
		event.stopPropagation();
	}
	
	onMount(async () => {
	// 12월 26일 0시 5분 이후부터 타임 릴리즈 기능 활성화
	const today = new Date();
	const releaseDate = new Date('2024-12-26T00:05:00');
	if (today > releaseDate) {
		showRelayUpdate = true;
	}

	//국가애도기간동안 로고 흑백 처리
	const mourningStart = new Date('2024-12-29T00:00:00');
	const mourningEnd = new Date('2025-01-04T23:59:59');
	if (today > mourningStart && today < mourningEnd) {
		mourning = true;
	}

	//8주년 기간 (~2/15)
	const eventStart = new Date('2025-02-01T00:00:00');
	const eventEnd = new Date('2025-02-15T23:59:59');
	if (today > eventStart && today < eventEnd) {
		eighthevent = true;
	}

	const noticeVisible = localStorage.getItem('noticeVisible_241206');
	const deviceWidth = window.innerWidth;

	const urlParams = new URLSearchParams(window.location.search);
	const evtHide = urlParams.get('evtHide');

	/*if (evtHide === '1') {
	}
	else if (noticeVisible !== 'false' && deviceWidth > 900) {
		location.href = "/event_8th.html";
	} else {
		const today = new Date();
		if (new Date(noticeVisible) > today && deviceWidth > 900) {
			location.href = "/event_8th.html";
		}
	}*/
		try {
			const jsonData = await loadAuth();
			data = jsonData;
		} catch (e) {
			data = { status: "Guest user" };
			/*if (window.location.pathname === "/wordsheet" || window.location.pathname === "/wordsheet.html") {
				alert("손님 계정으로는 검색이 불가능합니다.\n로그인 후 이용해주세요.");
			}*/
		}
		
		const userId = getUserId(data?.authVendor, data?.vendorId);
		if (data?.status !== "Guest user" && userId) {
			authVendor = String(data.authVendor);
			vendorId = String(data.vendorId);
			activeProfileId = typeof data.profileId === 'string' ? data.profileId : '';
			name = typeof data.name === 'string' && data.name ? data.name : 'Moremi';
			user = name;

			console.log(`User ${name} is logged in with ${authVendor}`);

			try {
				const block = await loadBlock();
				accountRestricted = block?.blocked === true;
			} catch (_) {
				accountRestricted = false;
			}

			// get user data
			const userData = await loadUser(userId);

			ingameName = processNick(userData?.profile?.title);


		} else {
			console.log("User is not logged in");
		}
		
	});

</script>

<header class="top-0 fixed w-full z-[60]">
	<div class="bg-white dark:bg-gray-800 shadow lg:py-2 py-3">
		<nav class="max-w-screen-xl mx-auto flex items-center justify-between px-4 lg:px-8 lg:py-0" aria-label="Global">
		<div class="flex lg:flex-1">
			<button type="button" class="mr-2 grid h-9 w-9 place-items-center rounded-xl text-slate-700 transition hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-700 lg:hidden" aria-label="메뉴 열기" aria-expanded={mobileMenuOpen} on:click={() => (mobileMenuOpen = true)}>
				<span class="material-symbols-outlined">menu</span>
			</button>
		<!--{#if $page.url.pathname === '/' || $page.url.pathname === '/index.html'}
			<a href="/event_8th.html" class="md:flex" rel="external">
				<span class="sr-only">끄투리오 8주년</span>
				<img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_8th.png" alt="끄투리오"/>
				<img class="h-8 hidden dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/>
			</a>
		{:else}
		8주년 랜딩 완료 후 주석 해제
		{/if}-->
			<a href="/" class="md:flex items-center space-x-2" style={mourning ? 'filter: grayscale(100%)' : ''}>
				<span class="sr-only">끄투리오</span>
				<div class="flex items-center space-x-4">
					<img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/>
				</div>
				<div class="flex items-center space-x-2">
					<img class="h-8 hidden dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/>
				</div>
			</a>
		
		</div>
		<div class="hidden lg:flex lg:flex-1 justify-center gap-x-6">
			<a href="/" class="link-header">
				<span class="material-symbols-outlined icons-header">
					home
				</span>
					홈</a>
			<a href="/rank" class="link-header">
				<span class="material-symbols-outlined icons-header">
					trophy
				</span>
				랭킹</a>
			<a href="/records" class="link-header">
				<span class="material-symbols-outlined icons-header">
					id_card
				</span>
                전적</a>
			<a target="_blank" href="https://cafe.naver.com/kkutuio" class="link-header">
				<span class="material-symbols-outlined icons-header">
					local_cafe
				</span>
				공식카페</a>
			<a target="_blank" href="https://kkutu.wiki" class="link-header">
				<span class="material-symbols-outlined icons-header">
					book_2
				</span>
				리오위키</a>
			<a href="/wordsheet" class="link-header">
				<span class="material-symbols-outlined icons-header">
					collections_bookmark
				</span>
                단어장</a>
			<a target="_blank" href="https://cs.kkutu.io" class="link-header">
				<span class="material-symbols-outlined icons-header">
					help
				</span>
				고객지원</a>
		</div>
		<div class="flex flex-1 justify-end gap-x-2">
			{#if user == "Guest User"}
				<!--<button on:click={() => showDialog.set(true)}
				class="bg-gray-200 text-gray-500 font-semibold dark:bg-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600 flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</button>--->
				<a href="/game/recommended" rel="external" on:click={guardRestrictedNavigation}
				class="bg-gray-200 text-gray-500 font-semibold dark:bg-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600 flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</a>
				<a href="/login"
				class:bg-gray-300={accountRestricted} class:text-gray-500={accountRestricted} class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				로그인
				</a>
			{:else}
				<!--<button on:click={() => showDialog.set(true)}
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</button>-->
				<a href="/game/recommended" rel="external" on:click={guardRestrictedNavigation}
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</a>
				<ProfileSwitcher
					profileSeed={activeProfileId || `${authVendor}:${vendorId}`}
					profileName={ingameName.replace(/<[^>]*>/g, '')}
					accountLabel={data?.email || name}
				/>
			
			{/if}
		</div>
		</nav>
	</div>
	{#if mobileMenuOpen}
		<div class="fixed inset-0 z-[70] lg:hidden">
			<button type="button" class="absolute inset-0 cursor-default bg-slate-950/50 backdrop-blur-[2px]" aria-label="메뉴 닫기" on:click={() => (mobileMenuOpen = false)}></button>
			<aside class="relative flex h-full w-[min(82vw,320px)] flex-col bg-white shadow-2xl dark:bg-slate-900" aria-label="모바일 메뉴" on:click|stopPropagation>
				<div class="flex items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-700">
					<a href="/" class="flex items-center" on:click={() => (mobileMenuOpen = false)}><img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/><img class="hidden h-8 dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/></a>
					<button type="button" class="grid h-9 w-9 place-items-center rounded-xl text-slate-500 transition hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800" aria-label="메뉴 닫기" on:click={() => (mobileMenuOpen = false)}><span class="material-symbols-outlined">close</span></button>
				</div>
				<nav class="flex flex-1 flex-col gap-1 p-3" aria-label="모바일 탐색">
					<a href="/" on:click={() => (mobileMenuOpen = false)} class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-slate-700 transition hover:bg-slate-100 dark:text-slate-100 dark:hover:bg-slate-800"><span class="material-symbols-outlined text-slate-500">home</span>홈</a>
					<a href="/rank" on:click={() => (mobileMenuOpen = false)} class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-slate-700 transition hover:bg-slate-100 dark:text-slate-100 dark:hover:bg-slate-800"><span class="material-symbols-outlined text-amber-500">trophy</span>랭킹</a>
					<a href="/records" on:click={() => (mobileMenuOpen = false)} class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-slate-700 transition hover:bg-slate-100 dark:text-slate-100 dark:hover:bg-slate-800"><span class="material-symbols-outlined text-sky-500">id_card</span>전적</a>
					<a target="_blank" href="https://cafe.naver.com/kkutuio" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-slate-700 transition hover:bg-slate-100 dark:text-slate-100 dark:hover:bg-slate-800"><span class="material-symbols-outlined text-emerald-500">local_cafe</span>공식카페</a>
					<a target="_blank" href="https://kkutu.wiki" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-slate-700 transition hover:bg-slate-100 dark:text-slate-100 dark:hover:bg-slate-800"><span class="material-symbols-outlined text-violet-500">book_2</span>리오위키</a>
					<a href="/wordsheet" on:click={() => (mobileMenuOpen = false)} class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-slate-700 transition hover:bg-slate-100 dark:text-slate-100 dark:hover:bg-slate-800"><span class="material-symbols-outlined text-rose-500">collections_bookmark</span>단어장</a>
					<a target="_blank" href="https://cs.kkutu.io" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-slate-700 transition hover:bg-slate-100 dark:text-slate-100 dark:hover:bg-slate-800"><span class="material-symbols-outlined text-indigo-500">help</span>고객지원</a>
				</nav>
				<p class="border-t border-slate-200 px-5 py-4 text-xs text-slate-400 dark:border-slate-700">끄투리오 메뉴</p>
			</aside>
		</div>
	{/if}
</header>
