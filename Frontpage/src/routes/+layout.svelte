<script>
	import { page } from '$app/state';
	import Header from './Header.svelte';
	import HeaderEvent from './Header_Event.svelte';
	import HeaderMan from './Header_Maintenance.svelte';
	// import HeaderOkggg from './Header_Okggg.svelte';
	import Footer from './Footer.svelte';
  
	import './styles.css';
	import '../app.css';
	/**
	 * @typedef {Object} Props
	 * @property {import('svelte').Snippet} [children]
	 */

	/** @type {Props} */
	let { children } = $props();
  
 </script>

  <svelte:head>
      <link rel="canonical" href="https://kkutu.io{page.url.pathname.replace(/\.html$/, '')}" />
	  {#if page.url.pathname !== '/unsupported'}
	  <script>
		  if (window.navigator.userAgent.indexOf("MSIE ") > 0 || !!navigator.userAgent.match(/Trident.*rv\:11\./)) {
			  location.href = "/unsupported";
		  }
	  </script>
	  {/if}
  </svelte:head>
  
  <div class="app">
	  {#if page.url.pathname.includes('man')}
	  <HeaderMan />
	  {:else if page.url.pathname.includes('event_')}
	  <HeaderEvent />
	  {:else}
	  <Header />
	  {/if}
  
	  <main>
		  {@render children?.()}
	  </main>
  
	  {#if !page.url.pathname.includes('event_')}
	  <Footer />
	  {/if}
  
  </div>
  
