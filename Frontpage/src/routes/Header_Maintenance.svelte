<script>
	// @ts-ignore
	import { page } from '$app/state';
	import { onMount } from 'svelte';

	import { getLevelImage } from '../lib/getLevelImg.js';

	let user = $state("Guest User");
	let authVendor = "KKuTu";
	let vendorId = "0";
	let name = "Moremi";
	let profileImage = "";

	let ingameName = "";
	let score = 0;

	let loginDialog = $state(false);


	let data = { status: "Guest user" };
	let flyout = false;
	var noticeData = "";

	let defaultProfileImage = 'https://cdn.kkutu.io/img/default_profile.png';

	function handleImageError(event) {
		event.target.src = defaultProfileImage;
	}

	function getUserId(provider, id) {
		const normalizedProvider = typeof provider === 'string' ? provider.trim().toLowerCase() : '';
		const normalizedId = id === undefined || id === null ? '' : String(id).trim();
		if (!normalizedProvider || !normalizedId) return '';
		return normalizedProvider === 'local' ? normalizedId : `${normalizedProvider}-${normalizedId}`;
	}

	
	onMount(async () => {

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
		
		const userId = getUserId(data?.authVendor, data?.vendorId);
		if (data?.status !== "Guest user" && userId) {
			authVendor = String(data.authVendor);
			vendorId = String(data.vendorId);
			name = typeof data.name === 'string' && data.name ? data.name : 'Moremi';
			profileImage = typeof data.image === 'string' ? data.image : '';
			user = name;

			if (authVendor === "DISCORD") {
				profileImage = profileImage.replace("/avatars/0/", `/avatars/${vendorId}/`);
				profileImage = profileImage + ".webp";
			}

			console.log(`User ${name} is logged in with ${authVendor}`);

			// get user data
			const userRes = await fetch(`/user/${userId}`);
			const userData = await userRes.json();

			ingameName = typeof userData?.profile?.title === 'string' ? userData.profile.title : '계정 설정 필요';
			score = Number(userData?.data?.score) || 0;


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
		<!--{#if page.url.pathname === '/' || page.url.pathname === '/index.html'}
			<a href="/event_8th.html" class="md:flex" rel="external">
				<span class="sr-only">끄투리오 8주년</span>
				<img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_8th.png" alt="끄투리오"/>
				<img class="h-8 hidden dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/>
			</a>
		{:else}
		8주년 랜딩 완료 후 주석 해제
		{/if}-->
			<div class="md:flex">
				<span class="sr-only">끄투리오</span>
				<img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/>
				<img class="h-8 hidden dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/>
			</div>
		</div>
		<div class="hidden lg:flex lg:flex-1 justify-center gap-x-6">
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
		</div>
		<div class="flex flex-1 justify-end gap-x-2">
			{#if user == "Guest User"}
				<button onclick={() => loginDialog = true}
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				로그인
				</button>
			{:else}
				<a rel="external" href="/logout"
				class="bg-gray-200 text-gray-500 font-semibold dark:bg-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600 flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				로그아웃
				</a>
			
			{/if}
		</div>
		</nav>
	</div>
</header>

{#if loginDialog === true}
<div class="z-50 fixed inset-0 bg-black bg-opacity-50 flex justify-center items-center">

	<div class="bg-white dark:bg-gray-900 flex flex-col justify-center px-6 py-8 lg:px-6 max-w-md mx-auto rounded-lg shadow-lg">
		<div class="flex justify-between items-center">
		  <h2 class="text-center text-2xl font-bold leading-8 tracking-tight text-gray-900 dark:text-gray-100">
			로그인
		  </h2>
		  <button class="text-gray-500 dark:text-gray-300 hover:text-gray-600 dark:hover:text-gray-400" aria-label="Close modal" 
		  onclick={() => loginDialog = false}>
			<span class="material-symbols-outlined">close</span>
		  </button>
		</div>
	  
		<div class="mt-4 sm:mx-auto sm:w-full">
		  <div>
			<a href="https://kkutu.io/login/naver">
			  <button class="text-lg bg-[#03C75A] text-white mt-3 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95 rounded-md">
				<img src="https://cdn.kkutu.io/logo/fusion/naver.svg" class="h-6 mr-2" alt="NAVER" />
				네이버로 로그인
			  </button>
			</a>
			<a href="https://kkutu.io/login/google">
			  <button class="text-lg bg-gray-100 hover:bg-gray-200 text-black mt-3 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95 rounded-md">
				<img src="https://cdn.kkutu.io/logo/fusion/google.svg" class="h-6 mr-2" alt="Google" />
				Google로 로그인
			  </button>
			</a>
			<a href="https://kkutu.io/login/kakao">
			  <button class="text-lg bg-[#FFDE00] text-[#3C1E1E] mt-3 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95 rounded-md">
				<img src="https://cdn.kkutu.io/logo/fusion/kakao.svg" class="h-6 mr-2" alt="Kakao" />
				카카오로 로그인
			  </button>
			</a>
			<a href="https://kkutu.io/login/discord">
			  <button class="text-lg bg-[#5865F2] text-white mt-3 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95 rounded-md">
				<img src="https://cdn.kkutu.io/logo/fusion/discord.svg" class="h-6 mr-2" alt="Discord" />
				디스코드로 로그인
			  </button>
			</a>
			<a href="https://kkutu.io/login/daldalso">
			  <button class="text-lg bg-[#20318D] text-white mt-3 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95 rounded-md">
				<img src="https://cdn.kkutu.io/logo/fusion/daldalso.png" class="h-6 mr-2" alt="Daldalso" />
				<span class="text-[#FFE101]">달달소</span>로 로그인 (신규 가입 불가)
			  </button>
			</a>
		  </div>
		  <p class="mt-8 text-center text-sm text-gray-500 dark:text-gray-300">
			로그인하면 끄투리오의 <a href="https://cs.kkutu.io/terms" target="_blank" rel="noopener" class="link-signin">서비스 이용약관</a>과 <a href="https://cs.kkutu.io/operation" target="_blank" rel="noopener" class="link-signin">운영정책</a>, <a href="https://cs.kkutu.io/privacy-policy" target="_blank" rel="noopener" class="link-signin">개인정보처리방침</a>에 동의하는 것으로 봅니다.
		  </p>
		</div>
	  </div>
	  
	</div>

{/if}
