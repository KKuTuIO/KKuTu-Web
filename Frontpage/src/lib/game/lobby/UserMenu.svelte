<script>
	import { t } from '../i18n.svelte.js';
	import { cubicOut } from 'svelte/easing';
	import { ms } from '../motion.js';
	import { isShut } from '../blocks.svelte.js';
	import Icon from './Icon.svelte';
	import { confirm } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';

	/**
	 * What you can do with another player, shown where you clicked (the legacy client's right-click menu, now on the left
	 * click). Opened by the page's hook `store.hooks.openUserMenu(id, event, layout)`. The items and who may see them
	 * follow the legacy rules.
	 */
	let { store } = $props();
	const net = $derived(store.net);

	// 'userList': the 접속자 list and friends; 'roomUser': a seat in the waiting room.
	const LAYOUT = {
		userList: ['friend', 'unfriend', 'info', 'block', 'report', 'whisper', 'id'],
		roomUser: ['friend', 'unfriend', 'info', 'dress', 'handover', 'kick', 'whisper', 'block', 'report', 'id']
	};
	// Which items make no sense for whom.
	const IGNORE = {
		self: ['friend', 'unfriend', 'block', 'report', 'whisper', 'kick', 'handover'],
		robot: ['friend', 'unfriend', 'dress', 'block', 'report', 'whisper', 'handover'],
		user: ['dress'],
		guest: ['friend', 'unfriend', 'whisper', 'block']
	};

	let menu = $state(null); // {id, x, y, layout}
	let el = $state();
	let at = $state({ left: 0, top: 0 });

	const target = $derived(menu ? net.users[menu.id] : null);
	const kinds = $derived(!target ? [] : [target.id === net.id && 'self', target.robot && 'robot', !target.robot && 'user', target.guest && 'guest'].filter(Boolean));
	const shut = $derived(!!menu && isShut(menu.id));

	const items = $derived.by(() => {
		if (!menu || !target) return [];
		return LAYOUT[menu.layout]
			.filter((k) => !kinds.some((kind) => IGNORE[kind].includes(k)))
			.filter((k) => !((k === 'friend' || k === 'unfriend') && net.guest))
			.filter((k) => !(k === 'friend' && store.isFriend(target.id)))
			.filter((k) => !(k === 'unfriend' && !store.isFriend(target.id)))
			.filter((k) => !((k === 'kick' || k === 'handover') && !store.isMaster))
			.concat(menu.layout === 'roomUser' && target.robot && store.isMaster ? ['ai'] : []);
	});

	const SPEC = {
		info: { icon: 'account_circle', label: () => t('play.menu.info'), run: (u) => store.hooks.openProfile?.(u.id) },
		friend: { icon: 'person_add', label: () => t('play.friend.add'), run: (u) => store.addFriend(u.id) },
		unfriend: { icon: 'person_remove', label: () => t('play.friend.remove'), run: (u) => store.removeFriend(u.id) },
		whisper: { icon: 'chat', label: () => t('play.profile.whisper'), run: (u) => store.fillChat(`/e ${(u.name + (u.tag ? `#${u.tag}` : '')).replace(/\s/g, '')} `) },
		block: { icon: 'block', label: () => t(shut ? 'play.block.unblock' : 'play.profile.block'), run: (u) => store.toggleBlock('shut', u.id) },
		report: { icon: 'flag', label: () => t('play.profile.report'), run: () => toast(t('play.profile.tbd'), { kind: 'info' }) },
		id: { icon: 'badge', label: () => t('play.profile.copyId'), run: copyId },
		dress: { icon: 'checkroom', label: () => t('play.menu.dress'), run: () => store.hooks.openDress?.() },
		handover: {
			icon: 'workspace_premium',
			label: () => t('play.profile.handover'),
			run: async (u) => (await confirm({ message: t('play.confirm.handover') })) && store.handover(u.id)
		},
		kick: { icon: 'exit_to_app', label: () => t('play.profile.kick'), run: (u) => store.kick(u.id, u.robot) },
		ai: { icon: 'tune', label: () => t('play.ai.title'), run: (u) => store.hooks.openAi?.(u) }
	};

	async function copyId(u) {
		try {
			await navigator.clipboard.writeText(u.id);
			toast(t('play.profile.idCopied'), { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}

	/** Called from the page; `e` is the click (or the keyboard activation of the button). */
	export function show(id, e, layout = 'userList') {
		let x = e?.clientX ?? 0;
		let y = e?.clientY ?? 0;
		if (!e?.detail || (!x && !y)) {
			// activated from the keyboard: below the element
			const r = e?.currentTarget?.getBoundingClientRect?.();
			if (r) (x = r.left + 12), (y = r.bottom);
		}
		menu = { id, x, y, layout };
	}
	const close = () => (menu = null);

	// Keep the menu inside the window once it has a size.
	$effect(() => {
		if (!menu || !el) return;
		const w = el.offsetWidth;
		const h = el.offsetHeight;
		at = { left: Math.max(8, Math.min(menu.x, window.innerWidth - w - 8)), top: Math.max(8, Math.min(menu.y, window.innerHeight - h - 8)) };
		el.querySelector('button')?.focus({ preventScroll: true });
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
	function pick(k) {
		const u = target;
		close();
		if (u) SPEC[k].run(u);
	}
</script>

<svelte:window onpointerdown={(e) => menu && el && !el.contains(e.target) && close()} onresize={close} onblur={close} />

{#if menu && target && items.length}
	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<ul bind:this={el} role="menu" aria-label={target.name} transition:pop onkeydown={key} onwheel={close} class="kk-card fixed z-50 flex min-w-44 flex-col p-1.5" style="left:{at.left}px;top:{at.top}px">
		{#each items as k (k)}
			<li role="none">
				<button type="button" role="menuitem" onclick={() => pick(k)} class="flex min-h-10 w-full items-center gap-2.5 rounded-lg px-3 text-left text-sm font-bold hover:bg-(--kk-accent-soft) focus-visible:bg-(--kk-accent-soft) focus-visible:outline-none {k === 'kick' || (k === 'block' && !shut) ? 'text-(color:--kk-danger)' : ''}">
					<Icon name={SPEC[k].icon} size={20} />{SPEC[k].label()}
				</button>
			</li>
		{/each}
	</ul>
{/if}
