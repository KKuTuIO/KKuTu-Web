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
	<div class="bg-white dark:bg-gray-800 shadow lg:py-2 py-3">
		<nav class="max-w-screen-xl mx-auto flex items-center justify-between px-4 lg:px-8 lg:py-0" aria-label="Global">
		<div class="flex lg:flex-1">
		<a href="/" class="md:flex">
			<span class="sr-only">끄투리오</span>
			<img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/>
			<img class="h-8 hidden dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/>
		</a>
		</div>
		<div class="hidden lg:flex lg:flex-1 justify-center gap-x-6">
			<a href="/" class="link-header">
				<span class="material-symbols-outlined icons-header">
					home
				</span>
					홈</a>
			<a rel="external" href="/rank.html" class="link-header">
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
			<a target="_blank" href="https://wiki.kkutu.io/" class="link-header">
				<span class="material-symbols-outlined icons-header">
					collections_bookmark
				</span>
			리오위키</a>
		</div>
		<div class="flex flex-1 justify-end gap-x-2">
			{#if user == "Guest User"}
				<a rel="external" href="https://kkutu.io/?server=0"
				class="bg-gray-200 text-gray-500 font-semibold dark:bg-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600 flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</a>
				<a href="/login"
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				로그인
				</a>
			{:else}
				<a rel="external" href="https://kkutu.io/?server=0"
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
				</a>
				<button on:click={() => confirm('정말로 로그아웃 할까요?') ? location.href = "https://kkutu.io/logout" : console.log("user cancel")}>
					<img src={profileImage} class="h-8 w-8 rounded-full" id="pfp"  />
				</button>
			{/if}
		</div>
		</nav>
	</div>
</header>
