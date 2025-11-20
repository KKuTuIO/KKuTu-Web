<script nonce="kkutuio">
	// @ts-ignore
	import { page } from '$app/stores';
	import { onMount } from 'svelte';

	import { getLevelImage } from '../lib/getLevelImg.js';

	import { showDialog } from './dialogStore';

	let user = "Guest User";
	let authVendor = "KKuTu";
	let vendorId = "0";
	let name = "Moremi";
	let profileImage = "";

	let ingameName = "";
	let score = 0;

	// 타임 릴리즈
	let showRelayUpdate = false;
	let mourning = false;
	let eighthevent = false;


	let data = "";
	let flyout = false;
	var noticeData = "";

	let defaultProfileImage = 'https://cdn.kkutu.io/img/default_profile.png';

	function handleImageError(event) {
		event.target.src = defaultProfileImage;
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
			const res = await fetch('/user/oauth');
			const jsonData = await res.json();
			data = jsonData;
		} catch (e) {
			data = { status: "Guest user" };
			/*if (window.location.pathname === "/wordsheet" || window.location.pathname === "/wordsheet.html") {
				alert("손님 계정으로는 검색이 불가능합니다.\n로그인 후 이용해주세요.");
			}*/
		}
		
		if (data.status !== "Guest user") {
			authVendor = data.authVendor;
			vendorId = data.vendorId;
			name = data.name;
			profileImage = data.image;
			user = name;

			if (authVendor === "DISCORD") {
				profileImage = profileImage.replace("/avatars/0/", `/avatars/${vendorId}/`);
				profileImage = profileImage + ".webp";
			}

			console.log(`User ${name} is logged in with ${authVendor}`);

			// get user data
			const userRes = await fetch(`https://kkutu.io/user/${authVendor.toLowerCase()}-${vendorId}`);
			const userData = await userRes.json();

			ingameName = userData.profile.title;
			score = userData.data.score;


		} else {
			console.log("User is not logged in");
		}
		
	});

	function flyoutMenu() {
		flyout = !flyout;
	}
</script>

<header class="top-0 fixed w-full z-10">
	<div class="bg-white dark:bg-gray-800 shadow lg:py-2 py-3">
		<nav class="max-w-screen-xl mx-auto flex items-center justify-between px-4 lg:px-8 lg:py-0" aria-label="Global">
		<div class="flex lg:flex-1">
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
			<a rel="external" href="/rank" class="link-header">
				<span class="material-symbols-outlined icons-header">
					trophy
				</span>
				랭킹</a>
			<a target="_blank" href="https://discord.gg/kkutuio-395143193114705920" class="link-header">
				<span class="material-symbols-outlined icons-header">
					forum
				</span>
				디스코드</a>
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
				<a href="/game/server/0" rel="external"
				class="bg-gray-200 text-gray-500 font-semibold dark:bg-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600 flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</a>
				<a href="/login"
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				로그인
				</a>
			{:else}
				<!--<button on:click={() => showDialog.set(true)}
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</button>-->
				<a href="/game/server/0" rel="external"
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</a>
				<button on:click={flyoutMenu}>
					<img src={profileImage} class="h-8 w-8 rounded-full" id="pfp"/>
				</button>
				<!-- Flyout Menu -->
				{#if flyout}
				<div class="absolute left-11/12 top-14 transform -translate-x-11/12 dark:text-white bg-white dark:bg-gray-800 shadow-lg rounded-lg p-2 max-w-screen-xl w-max">
					<div class="flex items-center gap-x-4 px-2">
						<div class="level" style={getLevelImage(Number(score))}></div>
						<div>
							<div class="font-bold truncate">{ingameName}</div>
							<div class="text-gray-500 dark:text-gray-300 text-sm">{authVendor} 계정</div>
						</div>
					</div>
					<div class="mt-2">
						<!--<button
						class="flex text-left w-full py-1 px-2 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-md">
							<span class="material-symbols-outlined text-md mr-2">
								account_circle
							</span>
							내 전적
						</button>-->
						<button
						on:click={() => location.href = "https://kkutu.io/login"}
						class="flex text-left w-full py-1 px-2 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-md">
							<span class="material-symbols-outlined text-md mr-2">
								account_circle
							</span>
							계정 변경
						</button>
						<button
						on:click={() => confirm('정말로 로그아웃 할까요?') ? location.href = "https://kkutu.io/logout" : console.log("user cancel")}
						class="flex text-left w-full py-1 px-2 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-md">
							<span class="material-symbols-outlined text-md mr-2">
								logout
							</span>
							로그아웃
						</button>
					</div>
				</div>
			{/if}
			
			{/if}
		</div>
		</nav>
	</div>
</header>
