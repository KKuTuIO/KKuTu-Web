<script>
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { captureRef } from '$lib/invite.js';
	import Header from './Header.svelte';
	import HeaderEvent from './Header_Event.svelte';
	import HeaderMan from './Header_Maintenance.svelte';
	// import HeaderOkggg from './Header_Okggg.svelte';
	import Footer from './Footer.svelte';
  
	import './styles.css';
	import '../app.css';
	import '$lib/game/game.css';
	/**
	 * @typedef {Object} Props
	 * @property {import('svelte').Snippet} [children]
	 */

	// 페이지가 load() 에서 description 을 돌려주면 그걸 쓰고, 없으면 사이트 기본 문구 (app.html 에 있던 값 그대로).
	const DEFAULT_DESCRIPTION = '글자로 놀자, 끄투리오! 끄투리오에서 어휘력의 제왕이 되어 보세요. / 온라인 끝말잇기, 앞말잇기, 초성퀴즈, 자음퀴즈, 단어대결, 십자말풀이, 타자대결, 워드스택, 그림퀴즈';
	const DEFAULT_OG_DESCRIPTION = '끄투리오, 끄투 온라인, 끄투, 온라인 끝말잇기, 앞말잇기, 초성퀴즈, 자음퀴즈, 단어대결, 십자말풀이, 타자대결, 워드스택, 그림퀴즈';

	/** @type {Props} */
	let { children } = $props();
	onMount(() => {
		captureRef(location.href); // a friend's link (`?ref=`) is kept until the player is in the game (see the play page)
	});
  
 </script>

  <svelte:head>
      <link rel="canonical" href="https://kkutu.io{page.url.pathname.replace(/\.html$/, '')}" />
	  <meta name="description" content={page.data.description ?? DEFAULT_DESCRIPTION} />
	  <meta property="og:description" content={page.data.description ?? DEFAULT_OG_DESCRIPTION} />
	  {#if page.url.pathname !== '/unsupported'}
	  <script>
		  if (window.navigator.userAgent.indexOf("MSIE ") > 0 || !!navigator.userAgent.match(/Trident.*rv\:11\./)) {
			  location.href = "/unsupported";
		  }
	  </script>
	  {/if}
  </svelte:head>
  
  {#if page.url.pathname.startsWith('/play')}
	{@render children?.()}
{:else}
<div class="kk app min-h-screen">
	  {#if page.url.pathname.includes('man')}
	  <HeaderMan />
	  {:else if page.url.pathname.includes('event_')}
	  <HeaderEvent />
	  {:else}
	  <Header />
	  {/if}
  
	  <main class="pt-16">
		  {@render children?.()}
	  </main>
  
	  {#if !page.url.pathname.includes('event_')}
	  <Footer />
	  {/if}
  
  </div>
{/if}
  
