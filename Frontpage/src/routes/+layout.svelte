<script>
	import { page } from '$app/stores';
	import Header from './Header.svelte';
	import HeaderMan from './Header_Maintenance.svelte';
	import HeaderOkggg from './Header_Okggg.svelte';
	import Footer from './Footer.svelte';
	import Dialog from './Dialog.svelte';
	import { showDialog } from './dialogStore';
  
	import './styles.css';
	import '../app.css';
	/**
	 * @typedef {Object} Props
	 * @property {import('svelte').Snippet} [children]
	 */

	/** @type {Props} */
	let { children } = $props();
  
	const okggg = ["rank", "okggg", "stats"];
  </script>
  
  <div class="app">
	  {#if $page.url.pathname.includes('man')}
	  <HeaderMan />
	  {:else if okggg.some((v) => $page.url.pathname.includes(v))}
	  <HeaderOkggg />
	  {:else}
	  <Header />
	  {/if}
  
	  <main>
		  {@render children?.()}
	  </main>
  
	  <Footer />
	  <Dialog />
  </div>
  