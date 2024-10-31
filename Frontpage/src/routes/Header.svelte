<script>
	// @ts-ignore
	import { page } from '$app/stores';
	import { onMount } from 'svelte';

	let user = "Guest User";
	let authVendor = "NEXON";
	let vendorId = "0";
	let name = "Moremi";
	let profileImage = "";
	let data = "";
	var noticeData = "";
	let noticeVisible = true;

	onMount(async () => {
		noticeVisible = localStorage.getItem('noticeVisible') !== 'false';
		try {
			const res = await fetch('https://kkutu.io/user/oauth');
			const jsonData = await res.json();
			data = jsonData;
		} catch (e) {
			data = { status: "Guest user" };
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
		} else {
			console.log("User is not logged in");
		}
		
		
        const noticeResponse = await fetch('https://static.kkutu.io/static_notice.html');
        noticeData = await noticeResponse.text();
	});

	function closeNotice() {
		noticeVisible = false;
		localStorage.setItem('noticeVisible', 'false');
	}
</script>

<header class="top-0 fixed w-full z-10">
	{#if noticeVisible}
	<div class="text-center py-2 text-white bg-black">
		{@html noticeData}
		<button class="text-white" on:click={() => closeNotice()}>×</button>
	</div>
	{/if}
	<div class="bg-white dark:bg-gray-800 shadow lg:py-2 py-3">
		<nav class="max-w-screen-xl mx-auto flex items-center justify-between px-4 lg:px-8 lg:py-0" aria-label="Global">
		<div class="flex lg:flex-1">
			<a href="/">
		<button class="md:flex"><span class="sr-only">끄투리오</span>
			<img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/>
			<img class="h-8 hidden dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/>
		</button>
		</a>
		</div>
		<div class="hidden lg:flex lg:flex-1 justify-center gap-x-6">
			<a href="/" class="link-header"><i class="fa-solid fa-house icons-header"></i>홈</a>
			<a rel="external" href="https://kkutu.io/?server=0" class="link-header"><i class="fa-solid fa-gamepad icons-header"></i>게임하기</a>
			<a target="_blank" href="https://discord.gg/kkutuio-395143193114705920" class="link-header"><i class="fa-brands fa-discord icons-header"></i>디스코드</a>
			<a target="_blank" href="https://cafe.naver.com/kkutuio" class="link-header"><i class="fa-solid fa-coffee icons-header"></i>공식카페</a>
			<a target="_blank" href="https://wiki.kkutu.io/" class="link-header"><i class="fa-solid fa-book icons-header"></i>리오위키</a>
		</div>
		<div class="flex flex-1 justify-end">
			{#if user == "Guest User"}
			<a href="/login"
			class="bg-[#55aa55] hover:bg-[#51a351] text-white flex rounded-lg py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
			로그인
			</a>
			{:else}
			<div class="flex items-center gap-x-2">
				<img src={profileImage} class="h-8 w-8 rounded-full" id="pfp"/>
				<button class="flex flex-col" on:click={() => confirm('정말로 로그아웃 할까요?') ? location.href = "https://kkutu.io/logout" : console.log("user cancel")}>
					<span class="text-sm text-gray-600 dark:text-gray-200">{user}</span>
					<span class="text-xs text-gray-400 dark:text-gray-300">#{authVendor}</span>
				</button>
			</div>
			{/if}
		</div>
		</nav>
	</div>
</header>
<div class="hidden lg:block absolute top-48 z-20 h-[calc(100%-584px)] w-32 right-0">
	<div class="sticky top-16 overflow-x-hidden shadow-md rounded-l-xl">
	  <div class="bg-white dark:bg-black flex flex-col w-32 border border-gray-200 dark:border-gray-700 rounded-tl-xl rounded-bl-xl border-r-0 divide-y divide-gray-200 dark:divide-gray-700">
		<a class="text-gray-700 dark:text-gray-300 group active w-32 h-20 flex flex-col gap-y-1.5 items-center justify-center" rel="external" href="/ost.html">
		<i class="fa-solid fa-music icons-sidebar"></i>
		  <span class="link-sidebar">OST</span>
		</a>
		<a class="text-gray-700 dark:text-gray-300 group active w-32 h-20 flex flex-col gap-y-1.5 items-center justify-center" rel="external" href="https://support.kkutu.io/" target="_blank">
			<i class="fa-solid fa-circle-question icons-sidebar"></i>
		  <span class="link-sidebar">고객지원</span>
		</a>
		<a class="text-gray-700 dark:text-gray-300 group active w-32 h-20 flex flex-col gap-y-1.5 items-center justify-center" rel="external" href="https://support.kkutu.io/order/main/packages/membership/?group_id=2" target="_blank">
			<i class="fa-solid fa-circle-up icons-sidebar"></i>
		  <span class="link-sidebar">멤버십</span>
		</a>
		<a class="text-gray-700 dark:text-gray-300 group active w-32 h-20 flex flex-col gap-y-1.5 items-center justify-center" href="#" onclick="window.open('https://kkutu.io/help', '_blank', 'width=550,height=375'); return false;">
			<i class="fa-solid fa-book-open icons-sidebar"></i>
			<span class="link-sidebar">게임 가이드</span>
		</a>		
	  </div>
	</div>
</div>
