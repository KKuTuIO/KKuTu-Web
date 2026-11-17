<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import Switch from '../ui/Switch.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { MATCH_KINDS, loadOffKinds, saveOffKinds, canTurnOff } from '../leagueModel.js';

	/** 매칭 설정: 매칭에서 만날 게임 유형을 이용자가 켜고 끈다. 꺼 둔 유형은 이 브라우저에 저장된다. */
	let { open = $bindable(false) } = $props();
	let off = $state(loadOffKinds());

	function flip(id, on) {
		if (!on && !canTurnOff(off, id)) return void toast(t('play.league.settings.minOne'), { kind: 'info' });
		off = on ? off.filter((x) => x !== id) : [...off, id];
		saveOffKinds(off);
	}
</script>

<Dialog bind:open title={t('play.league.settings')} size="md">
	<div class="flex flex-col gap-4">
		<p class="text-sm font-semibold text-(color:--kk-muted)">{t('play.league.settings.desc')}</p>
		<ul class="flex flex-col gap-1.5">
			{#each MATCH_KINDS as k (k.id)}
				<li class="flex items-center gap-2 rounded-xl border border-(color:--kk-line) px-3">
					<span class="pill-lang {k.lang}">{t(`play.league.lang.${k.lang}`)}</span>
					<div class="min-w-0 flex-1"><Switch checked={!off.includes(k.id)} onchange={(on) => flip(k.id, on)}>{t(`play.league.kind.${k.mode}`)}</Switch></div>
				</li>
			{/each}
		</ul>
	</div>
</Dialog>

<style>
	.pill-lang {
		flex-shrink: 0;
		border-radius: 999px;
		padding: 0.1rem 0.6rem;
		font-size: 0.75rem;
		font-weight: 900;
		background: #eef0f6;
		color: #3a4058;
	}
	.pill-lang.en {
		background: #fbe8e8;
		color: #8a2a2a;
	}
</style>
