<script nonce="kkutuio">
	import { page } from '$app/stores';
	import Header from './Header.svelte';
	import HeaderEvent from './Header_Event.svelte';
	import HeaderMan from './Header_Maintenance.svelte';
	import HeaderOkggg from './Header_Okggg.svelte';
	import Footer from './Footer.svelte';
	import './styles.css';
	import '../app.css';

	const okggg = ["rank", "okggg", "stats"];
</script>

<div class="app">
	{#if $page.url.pathname.includes('man')}
	<HeaderMan/>
	{:else if okggg.some((v) => $page.url.pathname.includes(v))}
	<HeaderOkggg/>
	{:else if !$page.url.pathname.includes('event_')}
	<Header />
	{:else}
	<HeaderEvent />
	{/if}

	<main>
		<slot />
	</main>

	{#if !$page.url.pathname.includes('event_')}
	<Footer />
	{/if}
</div>