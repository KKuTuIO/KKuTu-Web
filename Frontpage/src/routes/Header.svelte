<script>
	// @ts-ignore
	import { page } from '$app/stores';
	import { onMount } from 'svelte';
	// @ts-ignore
	/**
	 * @type {HTMLElement | null}
	 */
	let flyoutMenu = null;
	onMount(() => {
		flyoutMenu = document.getElementById('flyoutMenu');
	});
</script>

<header class="top-0 fixed w-full z-10">
	<div class="bg-[#262626] shadow lg:py-2 py-4">
		<nav class="max-w-screen-xl mx-auto flex items-center justify-between px-4 lg:px-8 lg:py-0" aria-label="Global">
		<div class="flex lg:flex-1">
			<a href="/">
		<button class="md:flex"><span class="sr-only">끄투리오</span>
			<img class="h-6" src="https://cdn.kkutu.io/img/bi/bi_vertical_white.png" alt="끄투리오"/></button>
		</a>
		</div>
		<div class="flex flex-1 justify-end">
			<a href="/login"
			class="text-white text-xl flex rounded-xl py-2 px-2 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg hover:bg-white/20">
			로그인
			</a>
		</div>
		</nav>
	</div>
  </header>