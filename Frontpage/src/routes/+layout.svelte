<script>
	import { page } from '$app/stores';
	import Header from './Header.svelte';
	import HeaderEvent from './Header_Event.svelte';
	import HeaderMan from './Header_Maintenance.svelte';
	import Footer from './Footer.svelte';
	import './styles.css';
	import '../app.css';
</script>

<div class="app">
	{#if $page.url.pathname.includes('man')}
	<HeaderMan/>
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