<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';

	/**
	 * A small icon button that puts the page into full screen and back (the Fullscreen API). Where the browser has none (iPhone's Safari)
	 * it is not drawn at all. Full screen can only be asked for from a tap, so it is never restored by itself on the next visit.
	 * `class` is for the place it sits in. `watermark`: not a button of a bar but a small, faint icon fixed in the top right corner of the screen
	 * (a phone's header has no room for another button; the icon stays out of the way and is full strength under the finger).
	 */
	let { class: cls = '', watermark = false } = $props();
	let supported = $state(false);
	let on = $state(false);
	onMount(() => {
		supported = !!document.fullscreenEnabled && typeof document.documentElement.requestFullscreen === 'function';
		const sync = () => (on = !!document.fullscreenElement);
		sync();
		document.addEventListener('fullscreenchange', sync);
		return () => document.removeEventListener('fullscreenchange', sync);
	});
	async function toggle() {
		try {
			if (document.fullscreenElement) await document.exitFullscreen();
			else await document.documentElement.requestFullscreen({ navigationUI: 'hide' });
		} catch {
			/* refused (not from a tap, or the browser said no): nothing to do */
		}
	}
	const label = $derived(t(on ? 'play.fullscreen.exit' : 'play.fullscreen.enter'));
</script>

{#if supported}
	{#if watermark}
		<button type="button" onclick={toggle} aria-pressed={on} aria-label={label} class="fixed top-[env(safe-area-inset-top)] right-0 z-30 grid size-5 place-items-center rounded-bl-lg text-(color:--kk-muted) opacity-40 transition-opacity active:opacity-100 {cls}"><Icon name={on ? 'fullscreen_exit' : 'fullscreen'} size={14} /></button>
	{:else}
		<Tooltip text={label} placement="bottom">
			<button type="button" onclick={toggle} aria-pressed={on} aria-label={label} class="grid size-10 shrink-0 place-items-center rounded-xl text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text) {cls}"><Icon name={on ? 'fullscreen_exit' : 'fullscreen'} size={22} /></button>
		</Tooltip>
	{/if}
{/if}
