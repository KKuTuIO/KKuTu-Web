<script>
	import { t } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import { fly } from 'svelte/transition';
	import Icon from '../lobby/Icon.svelte';
	import { dismiss, pause, resume } from './toast.svelte.js';

	let { toast } = $props();

	// Legacy toast icon set (cdn img/play/toast). Errors and warnings share the alert icon.
	const iconFor = { info: 'info', success: 'info', warn: 'alert', error: 'alert' };

	const THRESHOLD = 70; // px of horizontal drag that counts as a swipe-away
	let dx = $state(0);
	let dragging = $state(false);
	let startX = 0;
	let leaveX = $state(0);

	function down(e) {
		if (e.target.closest('button')) return;
		dragging = true;
		startX = e.clientX;
		e.currentTarget.setPointerCapture(e.pointerId);
		pause(toast.id);
	}
	function move(e) {
		if (dragging) dx = e.clientX - startX;
	}
	function up() {
		if (!dragging) return;
		dragging = false;
		if (Math.abs(dx) > THRESHOLD) {
			leaveX = Math.sign(dx) * 320;
			dismiss(toast.id);
		} else {
			dx = 0;
			resume(toast.id);
		}
	}
</script>

<div
	in:fly={{ y: -20, duration: 220 }}
	out:fly={{ x: leaveX, y: leaveX ? 0 : -12, duration: 160 }}
	role={toast.kind === 'error' ? 'alert' : 'status'}
	onpointerdown={down}
	onpointermove={move}
	onpointerup={up}
	onpointercancel={up}
	onpointerenter={() => pause(toast.id)}
	onpointerleave={() => !dragging && resume(toast.id)}
	onfocusin={() => pause(toast.id)}
	onfocusout={() => resume(toast.id)}
	style:transform="translateX({dx}px)"
	style:opacity={dragging ? Math.max(0.35, 1 - Math.abs(dx) / 260) : 1}
	class="group pointer-events-auto relative flex w-fit max-w-[min(24rem,100%)] cursor-grab touch-pan-y items-center gap-2.5 rounded-xl border-[1.5px] border-(color:--kk-line-strong) bg-(--kk-surface) py-2.5 pr-3 pl-3 text-sm font-bold text-(color:--kk-text) shadow-[0_2px_0_var(--kk-line-strong)] select-none {dragging ? 'cursor-grabbing' : 'transition-[transform,opacity] duration-200'}"
>
	<img src={cdnImg(`play/toast/${toast.icon ?? iconFor[toast.kind] ?? 'info'}.png`)} alt="" aria-hidden="true" draggable="false" class="h-5 w-auto shrink-0" />
	<span class="min-w-0 break-keep">{toast.message}</span>
	<button
		type="button"
		aria-label={t('play.toast.close')}
		onclick={() => dismiss(toast.id)}
		class="grid size-6 shrink-0 place-items-center rounded-full text-(color:--kk-muted) opacity-0 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100 hover:bg-(--kk-surface-2) focus-visible:opacity-100"
	><Icon name="close" size={16} /></button>
</div>
