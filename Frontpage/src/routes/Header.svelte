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

	onMount(async () => {
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
	});
</script>

<header class="top-0 fixed w-full z-10">
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
			<a rel="external" href="/ost.html" class="link-header"><i class="fa-solid fa-music icons-header"></i>OST</a>
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
