<script>
	// @ts-ignore
	import { page } from '$app/stores';
	import { onMount } from 'svelte';

	function hideForever() {
		localStorage.setItem('noticeVisible_241206', 'false');
	}
	function hideToday() {
		const today = new Date();
		const tomorrow = new Date(today);
		tomorrow.setDate(tomorrow.getDate() + 1);
		localStorage.setItem('noticeVisible', tomorrow.toISOString());
	}
</script>

<header class="fixed border-b dark:border-gray-700 w-full z-10">
	<div class="bg-white dark:bg-gray-800 lg:py-2 py-3">
		<nav class="max-w-screen-3xl mx-auto flex items-center justify-between px-4 lg:px-8 lg:py-0" aria-label="Global">
		<div class="flex lg:flex-1">
		<a href="/" class="md:flex">
			<span class="sr-only">끄투리오</span>
			<img class="h-8 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/>
			<img class="h-8 hidden dark:block" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/>
		</a>
		</div>
		<div class="flex flex-1 justify-end gap-x-2">
			<button 
					on:click={hideForever}
                    class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
                    <span class="material-symbols-outlined">
                        home
                    </span>
            </button>
			<button 
					on:click={hideToday}
					class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
					<span class="material-symbols-outlined">
						close
					</span>
					오늘만 보지 않기
			</button>
			<div class="border-l pl-2 dark:border-gray-500"></div>
			<a rel="external" href="https://kkutu.io/?server=0"
				class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-lg py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg">
				게임 시작
			</a>
		</div>
		</nav>
	</div>
</header>

<!-- Sidebar
<div class="hidden lg:block w-[214px] h-screen fixed z-50 bg-black bg-opacity-70">
	<div class="h-[195px] flex flex-col items-center justify-center border-b border-white/30 text-white text-center">
		<div class="w-[180px] rounded-xl mb-2 bg-black aspect-video">
			<img class="object-cover rounded-xl" src="https://cdn.kkutu.io/img/bgm/ost_1.png" alt="OST" />
		</div>
		8주년 카운트다운
	</div>
</div> -->