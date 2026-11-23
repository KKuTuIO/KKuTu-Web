<script>
	import { onMount } from 'svelte';
	import { fly, fade } from 'svelte/transition';
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import { achievementToasts, dismissAchievement } from './achievementToast.svelte.js';
	import { toasts } from './toast.svelte.js';

	/**
	 * 업적 달성 알림: 어두운 알약 모양에 왼쪽은 표식, 오른쪽은 머리글("업적 달성!")과 업적 이름 두 줄. 화면 위에서부터 내려온다.
	 * 희귀 업적(숨김이거나 명성이 큰 단계)은 표식과 머리글이 달라진다. 공용 토스트와 같은 방식으로 최상위 레이어에 올려서
	 * 결과 화면 같은 대화상자 위에도 보인다. 한 번만 마운트한다.
	 */
	let host = $state();
	// 같은 위쪽에 공용 토스트(위에서 2rem, 하나에 약 3.25rem)가 떠 있으면 그 아래로 내려와서 겹치지 않는다
	const top = $derived(toasts.length ? 2.5 + 3.25 * toasts.length : 1.5);
	function promote() {
		if (!host?.showPopover) return;
		try {
			if (host.matches(':popover-open')) host.hidePopover();
			host.showPopover();
		} catch {
			/* 팝오버를 지원하지 않으면 아래 z-index로 모달이 아닌 모든 것 위에 보인다 */
		}
	}
	onMount(promote);
	$effect(() => {
		if (achievementToasts.length) promote();
	});
</script>

<div bind:this={host} popover="manual" style:top="{top}rem" class="pointer-events-none fixed inset-x-0 bottom-auto z-[110] transition-[top] duration-200 m-0 flex h-auto max-h-none w-auto max-w-none flex-col items-center gap-2 overflow-visible border-0 bg-transparent p-0 px-4" aria-live="polite">
	{#each achievementToasts as a (a.id)}
		<div
			in:fly={{ y: -28, duration: 260 }}
			out:fade={{ duration: 180 }}
			role="status"
			class="pointer-events-auto flex w-fit max-w-[min(26rem,100%)] items-center gap-3 rounded-full bg-[#1e242c] py-2.5 pr-6 pl-2.5 text-white shadow-[0_6px_20px_rgb(0_0_0/0.35)]"
		>
			<span class="grid size-12 shrink-0 place-items-center rounded-full border-2 {a.rare ? 'border-[#a68cff] bg-[#2b2547] text-[#cdbdff]' : 'border-[#5f86ff] bg-[#1f2c4d] text-[#a9c0ff]'}">
				<Icon name={a.rare ? 'diamond' : 'emoji_events'} size={26} fill />
			</span>
			<span class="flex min-w-0 flex-col gap-0.5 leading-tight">
				<b class="text-sm font-extrabold break-keep">{t(a.rare ? 'play.achievement.toast.rare' : 'play.achievement.toast.normal')}</b>
				<span class="min-w-0 truncate text-sm break-keep">{a.name}</span>
			</span>
			<button type="button" class="sr-only" onclick={() => dismissAchievement(a.id)}>{t('play.toast.close')}</button>
		</div>
	{/each}
</div>
