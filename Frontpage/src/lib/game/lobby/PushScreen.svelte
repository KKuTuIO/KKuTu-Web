<script>
	import { cubicOut } from 'svelte/easing';
	import { t } from '../i18n.svelte.js';
	import { ms } from '../motion.js';
	import { haptic } from '../haptics.js';
	import Icon from './Icon.svelte';

	/**
	 * A phone screen of its own, pushed over the page: it slides in from the right, with the title on top and the way back at the
	 * top left (what a native navigation stack looks like). Use it where a desktop pop-out or window has no room on a phone, and
	 * where the content is a place to go rather than a question to answer (that is a Dialog, a bottom sheet).
	 *
	 *   <PushScreen bind:open title="우편함">…</PushScreen>
	 *
	 * Esc and the back button close it. `actions` is an optional snippet for buttons at the top right.
	 * @type {{open?:boolean, title:string, children?:import('svelte').Snippet, actions?:import('svelte').Snippet, onclose?:()=>void}}
	 */
	let { open = $bindable(false), title, children, actions, onclose } = $props();
	function back() {
		haptic(6);
		open = false;
		onclose?.();
	}
	function push(node, { duration = 280 } = {}) {
		return { duration: ms(duration), easing: cubicOut, css: (_t, u) => `transform:translateX(${u * 100}%)` };
	}
</script>

<svelte:window onkeydown={(e) => open && e.key === 'Escape' && !e.defaultPrevented && !document.querySelector('dialog[open]') && back()} />

{#if open}
	<section role="dialog" aria-modal="true" aria-label={title} transition:push class="fixed inset-0 z-50 flex flex-col bg-(--kk-bg) text-(color:--kk-text)">
		<header class="flex min-h-14 shrink-0 items-center gap-1 border-b border-(color:--kk-line) bg-(--kk-surface) pt-[env(safe-area-inset-top)] pr-2 pl-1">
			<button type="button" aria-label={t('play.dialog.back')} onclick={back} class="grid size-11 place-items-center rounded-full hover:bg-(--kk-surface-2)"><Icon name="arrow_back" size={24} /></button>
			<h2 class="min-w-0 flex-1 truncate text-lg font-extrabold">{title}</h2>
			{@render actions?.()}
		</header>
		<div class="flex min-h-0 flex-1 flex-col overflow-x-hidden overflow-y-auto pb-[env(safe-area-inset-bottom)]">{@render children?.()}</div>
	</section>
{/if}
