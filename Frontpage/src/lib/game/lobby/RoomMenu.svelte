<script>
	import { t } from '../i18n.svelte.js';
	import { cubicOut } from 'svelte/easing';
	import { ms } from '../motion.js';
	import Icon from './Icon.svelte';
	import { plainText } from '../protocol.js';

	/**
	 * The `...` menu of a room (the legacy client's right-click menu on a room): 방 정보 and 방 신고. Opened by the page's hook
	 * `store.hooks.openRoomMenu(no, event)`, from the room list (the `...` on a row, or a right-click) and from the room's own header.
	 */
	let { store } = $props();
	let menu = $state(null); // {no, x, y}
	let el = $state();
	let at = $state({ left: 0, top: 0 });
	const room = $derived(menu ? store.net.rooms[menu.no] : null);
	const items = $derived([
		{ id: 'info', icon: 'info', label: t('play.room.info'), run: (no) => store.hooks.openRoomInfo?.(no) },
		{ id: 'report', icon: 'flag', label: plainText(store.net.meta.labels['kkutu.dialog.roomInfo.reportBtn'] ?? ''), run: (no) => store.hooks.openReport?.({ type: 'room', no }) }
	]);

	export function show(no, e) {
		let x = e?.clientX ?? 0;
		let y = e?.clientY ?? 0;
		if (!e?.detail || (!x && !y)) {
			// activated from the keyboard: below the button
			const r = e?.currentTarget?.getBoundingClientRect?.();
			if (r) (x = r.left), (y = r.bottom + 4);
		}
		menu = { no, x, y };
	}
	const close = () => (menu = null);

	$effect(() => {
		if (!menu || !el) return;
		at = { left: Math.max(8, Math.min(menu.x, window.innerWidth - el.offsetWidth - 8)), top: Math.max(8, Math.min(menu.y, window.innerHeight - el.offsetHeight - 8)) };
		el.querySelector('button')?.focus({ preventScroll: true });
	});
	$effect(() => {
		if (menu && !room) close(); // the room is gone
	});

	function pop(node, { duration = 140 } = {}) {
		return { duration: ms(duration), easing: cubicOut, css: (t, u) => `opacity:${t};transform:scale(${0.95 + 0.05 * t});transform-origin:top left` };
	}
	function key(e) {
		const bs = [...el.querySelectorAll('button')];
		const i = bs.indexOf(document.activeElement);
		if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
			e.preventDefault();
			bs[(i + (e.key === 'ArrowDown' ? 1 : -1) + bs.length) % bs.length]?.focus();
		} else if (e.key === 'Escape') close();
	}
	function pick(item) {
		const no = menu.no;
		close();
		item.run(no);
	}
</script>

<svelte:window onpointerdown={(e) => menu && el && !el.contains(e.target) && close()} onresize={close} onblur={close} />

{#if menu && room}
	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<ul bind:this={el} role="menu" aria-label="{room.no} {room.title}" transition:pop onkeydown={key} onwheel={close} class="kk-card fixed z-50 flex min-w-44 flex-col p-1.5" style="left:{at.left}px;top:{at.top}px">
		{#each items as item (item.id)}
			<li role="none">
				<button type="button" role="menuitem" onclick={() => pick(item)} class="flex min-h-10 w-full items-center gap-2.5 rounded-lg px-3 text-left text-sm font-bold hover:bg-(--kk-accent-soft) focus-visible:bg-(--kk-accent-soft) focus-visible:outline-none {item.id === 'report' ? 'text-(color:--kk-danger)' : ''}">
					<Icon name={item.icon} size={20} />{item.label}
				</button>
			</li>
		{/each}
	</ul>
{/if}
