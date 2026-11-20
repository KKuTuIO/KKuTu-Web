<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';

	/**
	 * 트로피 리그 안내: 이용 시간, 매칭, 트로피와 티어, 중간에 나갔을 때, 시즌 보상, 신고. 문구가 서버의 규칙과 어긋나지 않도록
	 * 값이 있는 것은 서버가 보낸 상태(`s`)에서 읽고, 일반 경기 트로피 문장은 서버에서 켜져 있을 때만 보여 준다.
	 * @type {{open?: boolean, s: any}}
	 */
	let { open = $bindable(false), s } = $props();
	const sections = $derived([
		['hours', true],
		['match', true],
		['trophy', true],
		['normal', !!s?.normalTrophies],
		['leave', true],
		['season', true],
		['report', true]
	].filter(([, shown]) => shown).map(([k]) => k));
</script>

<Dialog bind:open title={t('play.league.guide.title')} size="md">
	<div class="flex max-h-[min(66vh,34rem)] flex-col gap-4 overflow-y-auto pr-1">
		{#each sections as k (k)}
			<section>
				<h3 class="font-black">{t(`play.league.guide.${k}.h`)}</h3>
				<p class="mt-1 text-sm leading-relaxed text-(color:--kk-muted)">{t(`play.league.guide.${k}.b`)}</p>
			</section>
		{/each}
	</div>
</Dialog>
