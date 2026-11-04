<script>
	import { t } from '../i18n.svelte.js';
	import { history, dismiss } from '../ui/toast.svelte.js';
	import Icon from './Icon.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { prefs } from '../prefs.svelte.js';
	import { maskBad } from '../badwords.js';
	import { cubicOut } from 'svelte/easing';
	import { ms } from '../motion.js';

	/**
	 * 알림 and 우편함 in one pop-out under the mail button (the legacy "Social" dialog's last two tabs).
	 * 알림: friend requests, notices the server keeps until confirmed, and the toasts you may have missed.
	 * 우편함: letters, with their rewards to claim. A red count on the button says something is waiting.
	 */
	let { store } = $props();
	const net = $derived(store.net);
	let open = $state(false);
	let tab = $state('alert');
	let box = $state();
	let pos = $state({ top: 0, right: 0 });
	let seen = $state(new Set()); // toast-history entries I've dismissed
	// The panel opens at its default height every time. It can be pulled taller (from the bottom edge), never shorter.
	const WIDTH = 384;
	const MIN_H = 544;
	let height = $state(MIN_H);
	let resizing = $state(false);
	let grab = { y: 0, h: 0 };
	const maxHeight = () => Math.max(MIN_H, window.innerHeight - pos.top - 8);
	function startResize(e) {
		resizing = true;
		grab = { y: e.clientY, h: height };
		e.currentTarget.setPointerCapture?.(e.pointerId);
	}
	function resize(e) {
		if (resizing) height = Math.min(maxHeight(), Math.max(MIN_H, grab.h + (e.clientY - grab.y)));
	}
	// Drops in from the button and leaves quicker than it arrived.
	function pop(node, { duration = 170 } = {}) {
		return { duration: ms(duration), easing: cubicOut, css: (t, u) => `opacity:${t};transform:translateY(${-10 * u}px) scale(${0.96 + 0.04 * t});transform-origin:top right` };
	}
	const tabs = $derived([
		{ id: 'alert', label: t('play.mail.tab.alert'), count: alerts.length },
		{ id: 'mail', label: t('play.mail.tab.mail'), count: net.mails.length }
	]);

	const requests = $derived(net.friendRequests.map((r) => ({ ...r, name: net.users[r.from]?.name ?? r.from })));
	const notices = $derived(history.filter((h) => !seen.has(h.id)));
	const alerts = $derived([...requests, ...net.notifications, ...notices]);
	const count = $derived(store.unreadCount);
	const fmt = (ms) => new Date(ms).toLocaleString();
	const words = (s) => (prefs.filter === 'off' ? s : maskBad(s));

	function toggle() {
		open = !open;
		if (!open) return;
		tab = 'alert';
		height = MIN_H;
		seen = new Set();
		const r = box.getBoundingClientRect();
		pos = { top: r.bottom + 8, right: Math.max(8, window.innerWidth - r.right) };
		store.requestMailbox(); // legacy: looking at it refreshes it
	}

	function left(mail) {
		if (mail.claimable === false) return t('play.mail.expired');
		if (!mail.claimEndAt) return t('play.mail.noExpiry');
		const ms = new Date(mail.claimEndAt) - Date.now();
		const d = Math.floor(ms / 86_400_000);
		const h = Math.floor((ms % 86_400_000) / 3_600_000);
		const m = Math.floor((ms % 3_600_000) / 60_000);
		const time = d > 0 ? t('play.mail.day', { n: d }) : h > 0 ? `${t('play.mail.hour', { n: h })} ${t('play.mail.minute', { n: m })}` : t('play.mail.minute', { n: Math.max(0, m) });
		return t('play.mail.left', { time });
	}
	function drop(h) {
		dismiss(h.id);
		seen = new Set([...seen, h.id]);
	}
</script>

<svelte:window onclick={(e) => open && !box?.contains(e.target) && !e.target.closest?.('[data-mail-panel]') && (open = false)} onkeydown={(e) => open && e.key === 'Escape' && (open = false)} />

<span bind:this={box} class="relative inline-flex">
	<Tooltip text={t('play.nav.mail')} placement="bottom">
		<button type="button" aria-expanded={open} aria-haspopup="dialog" aria-label={count ? `${t('play.nav.mail')} (${count})` : t('play.nav.mail')} onclick={toggle} class="relative inline-flex h-10 min-w-10 items-center justify-center rounded-xl px-2.5 transition-colors {open ? 'bg-[#FECDD3] text-[#111]' : 'text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)'}">
			<Icon name="mail" size={22} fill={open || count > 0} />
			{#if count}<span class="kk-nudge absolute -top-0.5 -right-0.5 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-(--kk-danger) px-1 text-[11px] leading-none font-extrabold text-white">{count > 99 ? '99+' : count}</span>{/if}
		</button>
	</Tooltip>
</span>

{#if open}
	<div data-mail-panel role="dialog" aria-label={t('play.nav.mail')} transition:pop class="kk-card fixed z-50 flex flex-col overflow-hidden" style="top:{pos.top}px;right:{pos.right}px;width:min({WIDTH}px,calc(100vw - 1rem));height:min({height}px,calc(100dvh - {pos.top + 8}px))">
		<div class="px-3 pt-1"><Tabs bind:value={tab} {tabs} idPrefix="mail" label={t('play.nav.mail')} /></div>
		<div role="tabpanel" id="mail-panel-{tab}" aria-labelledby="mail-tab-{tab}" class="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto p-3">
			{#if tab === 'alert'}
				{#each requests as r (r.from)}
					<article class="flex flex-col gap-2 rounded-xl border border-(color:--kk-line) p-3">
						<p class="flex items-center gap-1.5 text-sm font-extrabold"><Icon name="person_add" size={18} fill />{t('play.friend.add')}<time class="ml-auto text-[11px] font-medium text-(color:--kk-muted)">{fmt(r.at)}</time></p>
						<p class="text-sm">{t('play.friend.requestFrom', { nick: r.name })}</p>
						<div class="flex justify-end gap-2">
							<button type="button" class="kk-btn min-h-10 px-4 text-sm" onclick={() => store.answerFriendRequest(r.from, false)}>{t('play.friend.deny')}</button>
							<button type="button" class="kk-btn kk-btn-primary min-h-10 px-4 text-sm" onclick={() => store.answerFriendRequest(r.from, true)}>{t('play.friend.accept')}</button>
						</div>
					</article>
				{/each}
				{#each net.notifications as n (n.id)}
					<article class="flex flex-col gap-2 rounded-xl border border-(color:--kk-line) p-3">
						<p class="flex items-center gap-1.5 text-sm font-extrabold"><Icon name="campaign" size={18} fill />{t('play.mail.alertTitle')}<time class="ml-auto text-[11px] font-medium text-(color:--kk-muted)">{fmt((Number(n.createdAt) || Date.now() / 1000) * 1000)}</time></p>
						<p class="text-sm break-words whitespace-pre-wrap">{n.message}</p>
						<div class="flex justify-end"><button type="button" class="kk-btn kk-btn-primary min-h-10 px-4 text-sm" onclick={() => store.ackNotification(n.id)}>{t('play.mail.confirm')}</button></div>
					</article>
				{/each}
				{#each notices as h (h.id)}
					<button type="button" onclick={() => drop(h)} class="flex items-start gap-2 rounded-xl bg-(--kk-surface-2) px-3 py-2 text-left text-sm" aria-label="{t('play.mail.dismiss')}: {h.message}">
						<Icon name={h.kind === 'error' ? 'error' : h.kind === 'success' ? 'check_circle' : 'info'} size={18} class="mt-0.5 shrink-0 text-(color:--kk-muted)" />
						<span class="min-w-0 flex-1 break-words">{h.message}</span>
						<time class="shrink-0 text-[11px] text-(color:--kk-muted)">{new Date(h.at).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</time>
					</button>
				{/each}
				{#if !alerts.length}<p class="py-8 text-center text-sm text-(color:--kk-muted)">{t('play.mail.noAlerts')}</p>{/if}
			{:else}
				{#each net.mails as m (m._id)}
					<article class="flex flex-col gap-2 rounded-xl border border-(color:--kk-line) p-3">
						<p class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm font-extrabold">
							<Icon name="mail" size={18} fill />{words(m.name)}
							<span class="rounded-md bg-(--kk-accent-soft) px-1.5 py-0.5 text-[11px] font-bold text-(color:--kk-accent-strong)">{t(m.hasRewards ? 'play.mail.kind.reward' : 'play.mail.kind.info')}</span>
							<span class="ml-auto text-[11px] font-medium text-(color:--kk-muted)">{left(m)}</span>
						</p>
						<p class="text-sm break-words whitespace-pre-wrap">{words(m.content)}</p>
						<div class="flex justify-end"><button type="button" class="kk-btn kk-btn-primary min-h-10 px-5 text-sm" disabled={m.claimable === false} onclick={() => store.claimMail(m._id)}>{t(m.hasRewards ? 'play.mail.claim' : 'play.mail.confirm')}</button></div>
					</article>
				{:else}
					<p class="py-8 text-center text-sm text-(color:--kk-muted)">{t('play.mail.noMails')}</p>
				{/each}
			{/if}
		</div>
		<!-- Bottom edge: drag down to make the panel taller (it can't get shorter than it opens). Arrow keys work too. -->
		<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
		<div
			role="separator"
			aria-label={t('play.mail.resize')}
			aria-orientation="horizontal"
			tabindex="0"
			onpointerdown={startResize}
			onpointermove={resize}
			onpointerup={() => (resizing = false)}
			onpointercancel={() => (resizing = false)}
			onkeydown={(e) => {
				if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
				e.preventDefault();
				height = Math.min(maxHeight(), Math.max(MIN_H, height + (e.key === 'ArrowDown' ? 24 : -24)));
			}}
			class="grid h-3 shrink-0 cursor-ns-resize touch-none place-items-center border-t border-(color:--kk-line) text-(color:--kk-line-strong) hover:text-(color:--kk-muted) focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-(--kk-accent)"
		>
			<span class="h-1 w-10 rounded-full bg-current"></span>
		</div>
	</div>
{/if}
