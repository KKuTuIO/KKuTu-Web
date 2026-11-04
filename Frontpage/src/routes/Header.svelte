<script>
	// @ts-ignore
	import { page } from '$app/state';
	import { onMount } from 'svelte';

	import ProfileSwitcher from '$lib/ProfileSwitcher.svelte';
	import { loadClientPref, recommendedUrl } from '$lib/clientPref.svelte.js';
	import {loadAuth, loadBlock, loadUser} from '$lib/session.js';

	let user = $state("Guest User");
	let authVendor = $state("KKuTu");
	let vendorId = $state("0");
	let activeProfileId = $state("");
	let name = $state("Moremi");
	let ingameName = $state("");
	const p = $derived(page.url.pathname);
	let mobileMenuOpen = $state(false);

	// 타임 릴리즈
	let showRelayUpdate = false;
	let mourning = $state(false);
	let eighthevent = false;


	let data = $state({ status: "Guest user" });
	let accountRestricted = $state(false);
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
	loadClientPref();
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
				alert("손님 계정으로는 검색이 불가능합니다.\
로그인 후 이용해주세요.");
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
<svelte:head>
	<style>
		.nav-item { color: var(--kk-muted); }
		.nav-item:hover { background: var(--kk-surface-2); color: var(--kk-text); }
		.nav-item[aria-current='page'] { background: var(--c); color: #111; font-weight: 800; }
	</style>
</svelte:head>

<header class="kk fixed top-0 z-[60] w-full">
	<div class="flex h-16 items-center gap-3 border-b border-(color:--kk-line) bg-(--kk-surface) px-4 md:px-6">
		<button type="button" class="grid h-10 w-10 place-items-center rounded-xl text-(color:--kk-muted) transition-colors hover:bg-(--kk-surface-2) lg:hidden" aria-label="메뉴 열기" aria-expanded={mobileMenuOpen} onclick={() => (mobileMenuOpen = true)}>
			<span class="material-symbols-outlined">menu</span>
		</button>
		<a href="/" class="flex shrink-0 items-center" style={mourning ? 'filter: grayscale(100%)' : ''}>
			<span class="sr-only">끄투리오</span>
			<!-- Phones: symbol only (as in the game client). lg and up: full wordmark. -->
			<img class="h-8 lg:hidden dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_main.svg" alt="끄투리오"/>
			<img class="hidden h-8 dark:max-lg:block" src="https://cdn.kkutu.io/img/bi/bi_white.svg" alt="" aria-hidden="true"/>
			<img class="hidden h-8 lg:block dark:lg:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/>
			<img class="hidden h-8 dark:lg:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="" aria-hidden="true"/>
		</a>
		<nav class="mx-auto hidden min-w-0 items-center gap-1 lg:flex" aria-label="Global">
			<a href="/" style:--c="#B0D2F3" aria-current={p === '/' ? 'page' : undefined} class="nav-item inline-flex h-10 items-center gap-1.5 rounded-xl px-2.5 font-bold whitespace-nowrap transition-colors xl:px-3.5">
				<span class="material-symbols-outlined leading-none select-none" style="font-size:22px" aria-hidden="true">home</span>홈</a>
			<a href="/rank" style:--c="#FFADD3" aria-current={(p === '/rank' || (p !== '/' && p.startsWith('/rank'))) ? 'page' : undefined} class="nav-item inline-flex h-10 items-center gap-1.5 rounded-xl px-2.5 font-bold whitespace-nowrap transition-colors xl:px-3.5">
				<span class="material-symbols-outlined leading-none select-none" style="font-size:22px" aria-hidden="true">trophy</span>랭킹</a>
			<a href="/records" style:--c="#D9FF82" aria-current={(p === '/records' || (p !== '/' && p.startsWith('/records'))) ? 'page' : undefined} class="nav-item inline-flex h-10 items-center gap-1.5 rounded-xl px-2.5 font-bold whitespace-nowrap transition-colors xl:px-3.5">
				<span class="material-symbols-outlined leading-none select-none" style="font-size:22px" aria-hidden="true">id_card</span>전적</a>
			<a href="https://cafe.naver.com/kkutuio" target="_blank" rel="noopener" style:--c="#B3E7B7" aria-current={false ? 'page' : undefined} class="nav-item inline-flex h-10 items-center gap-1.5 rounded-xl px-2.5 font-bold whitespace-nowrap transition-colors xl:px-3.5">
				<span class="material-symbols-outlined leading-none select-none" style="font-size:22px" aria-hidden="true">local_cafe</span>공식카페</a>
			<a href="https://kkutu.wiki" target="_blank" rel="noopener" style:--c="#FFC67F" aria-current={false ? 'page' : undefined} class="nav-item inline-flex h-10 items-center gap-1.5 rounded-xl px-2.5 font-bold whitespace-nowrap transition-colors xl:px-3.5">
				<span class="material-symbols-outlined leading-none select-none" style="font-size:22px" aria-hidden="true">book_2</span>리오위키</a>
			<a href="https://cs.kkutu.io" target="_blank" rel="noopener" style:--c="#BBBBBB" aria-current={false ? 'page' : undefined} class="nav-item inline-flex h-10 items-center gap-1.5 rounded-xl px-2.5 font-bold whitespace-nowrap transition-colors xl:px-3.5">
				<span class="material-symbols-outlined leading-none select-none" style="font-size:22px" aria-hidden="true">help</span>고객지원</a>
		</nav>
		<div class="ml-auto flex shrink-0 items-center gap-2 lg:ml-0">
			{#if user == "Guest User"}
				<a href={recommendedUrl()} rel="external" onclick={guardRestrictedNavigation} class="kk-btn kk-btn-hero hidden h-10 min-h-0 items-center gap-1.5 whitespace-nowrap px-4 text-sm lg:inline-flex"><span class="material-symbols-outlined" style="font-size:20px;font-variation-settings:'FILL' 1" aria-hidden="true">play_arrow</span>게임 시작</a>
				<a href="/login" aria-label="로그인" class="kk-btn kk-btn-primary inline-flex h-10 min-h-0 items-center gap-1.5 whitespace-nowrap px-4 text-sm" class:opacity-60={accountRestricted}>
					<span class="material-symbols-outlined" style="font-size:20px" aria-hidden="true">login</span><span class="max-[420px]:sr-only">로그인</span>
				</a>
			{:else}
				<a href={recommendedUrl()} rel="external" onclick={guardRestrictedNavigation} class="kk-btn kk-btn-hero hidden h-10 min-h-0 items-center gap-1.5 whitespace-nowrap px-4 text-sm lg:inline-flex">
					<span class="material-symbols-outlined" style="font-size:20px;font-variation-settings:'FILL' 1" aria-hidden="true">play_arrow</span>게임 시작
				</a>
				<ProfileSwitcher
					profileSeed={activeProfileId || `${authVendor}:${vendorId}`}
					profileName={ingameName.replace(/<[^>]*>/g, '')}
					accountLabel={data?.email || name}
				/>
			{/if}
		</div>
	</div>
	{#if mobileMenuOpen}
		<div class="fixed inset-0 z-[70] lg:hidden">
			<button type="button" class="absolute inset-0 cursor-default bg-black/50" aria-label="메뉴 닫기" onclick={() => (mobileMenuOpen = false)}></button>
			<aside class="relative flex h-full w-[min(82vw,320px)] flex-col border-r border-(color:--kk-line) bg-(--kk-surface)" aria-label="모바일 메뉴">
				<div class="flex h-16 items-center justify-between border-b border-(color:--kk-line) px-5">
					<a href="/" class="flex items-center" onclick={() => (mobileMenuOpen = false)}><img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/><img class="hidden h-8 dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/></a>
					<button type="button" class="grid h-10 w-10 place-items-center rounded-xl text-(color:--kk-muted) transition-colors hover:bg-(--kk-surface-2)" aria-label="메뉴 닫기" onclick={() => (mobileMenuOpen = false)}><span class="material-symbols-outlined">close</span></button>
				</div>
				<nav class="flex flex-1 flex-col gap-1 p-3" aria-label="모바일 탐색">
					<a href="/" onclick={() => (mobileMenuOpen = false)} style:--c="#B0D2F3" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-(color:--kk-text) transition-colors hover:bg-(--c)/40"><span class="material-symbols-outlined">home</span>홈</a>
					<a href="/rank" onclick={() => (mobileMenuOpen = false)} style:--c="#FFADD3" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-(color:--kk-text) transition-colors hover:bg-(--c)/40"><span class="material-symbols-outlined">trophy</span>랭킹</a>
					<a href="/records" onclick={() => (mobileMenuOpen = false)} style:--c="#D9FF82" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-(color:--kk-text) transition-colors hover:bg-(--c)/40"><span class="material-symbols-outlined">id_card</span>전적</a>
					<a href="https://cafe.naver.com/kkutuio" target="_blank" rel="noopener" onclick={() => (mobileMenuOpen = false)} style:--c="#B3E7B7" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-(color:--kk-text) transition-colors hover:bg-(--c)/40"><span class="material-symbols-outlined">local_cafe</span>공식카페</a>
					<a href="https://kkutu.wiki" target="_blank" rel="noopener" onclick={() => (mobileMenuOpen = false)} style:--c="#FFC67F" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-(color:--kk-text) transition-colors hover:bg-(--c)/40"><span class="material-symbols-outlined">book_2</span>리오위키</a>
					<a href="https://cs.kkutu.io" target="_blank" rel="noopener" onclick={() => (mobileMenuOpen = false)} style:--c="#BBBBBB" class="flex items-center gap-3 rounded-xl px-4 py-3 font-bold text-(color:--kk-text) transition-colors hover:bg-(--c)/40"><span class="material-symbols-outlined">help</span>고객지원</a>
				</nav>
				<p class="border-t border-(color:--kk-line) px-5 py-4 text-xs text-(color:--kk-muted)">&copy; 주식회사 큐브시티, 모든 권리 보유.</p>
			</aside>
		</div>
	{/if}
</header>
