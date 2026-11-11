<script>
	import FloatWindow from './FloatWindow.svelte';
	import PushScreen from './PushScreen.svelte';

	/**
	 * Where a tool opens: a movable window next to the game on a wide screen, a screen of its own (like 우편함) on a phone, where a window
	 * would only be a bottom sheet. `children` is told which one it got; `actions` are the buttons at the top right of the phone screen.
	 * @type {{open?:boolean, title:string, id:string, width?:string, ghost?:boolean, children?:import('svelte').Snippet<[boolean]>, actions?:import('svelte').Snippet}}
	 */
	let { open = $bindable(false), title, id, width = '34rem', ghost = false, children, actions } = $props();
	let winW = $state(1280);
	const phone = $derived(winW < 768);
</script>

<svelte:window bind:innerWidth={winW} />

{#if phone}
	<PushScreen bind:open {title} {actions}>{@render children?.(true)}</PushScreen>
{:else}
	<FloatWindow bind:open {id} {title} {width} {ghost}>{@render children?.(false)}</FloatWindow>
{/if}
