<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import GradeBadge from './GradeBadge.svelte';

	/**
	 * 명성과 등급 안내: 명성이 무엇인지, 어느 명성부터 어느 등급인지, 지금 내 등급. 트로피 리그 안내처럼 꾸밈 없는 목록이다.
	 * 등급 표는 서버가 보낸 것(`grades`)을 그대로 그려서 문구나 숫자가 서버의 정의와 어긋나지 않는다.
	 * @type {{open?: boolean, grades: {id:string,name:string,from:number}[], points: number, current?: string|null}}
	 */
	let { open = $bindable(false), grades = [], points = 0, current = null } = $props();
	const commify = (n) => Number(n).toLocaleString('ko-KR');
</script>

<Dialog bind:open title={t('play.achievement.guide.title')} size="md">
	<div class="flex max-h-[min(66vh,34rem)] flex-col gap-3 overflow-y-auto pr-1">
		<p class="text-sm leading-relaxed text-(color:--kk-muted)">{t('play.achievement.guide.intro')}</p>
		<p class="text-sm font-bold tabular-nums">{t('play.achievement.guide.mine', { n: commify(points) })}</p>
		<ul class="divide-y divide-(color:--kk-line)">
			{#each grades as g (g.id)}
				{@const mine = g.id === current}
				<li class="flex items-center gap-3 py-1.5" aria-current={mine || undefined}>
					<GradeBadge id={g.id} size={32} label={g.name} />
					<span class="min-w-0 flex-1 text-sm {mine ? 'font-extrabold' : 'font-bold'}">{g.name}</span>
					{#if mine}<span class="shrink-0 text-xs font-extrabold text-(color:--kk-accent)">{t('play.achievement.guide.current')}</span>{/if}
					<span class="w-28 shrink-0 text-right text-xs text-(color:--kk-muted) tabular-nums">{t('play.achievement.guide.from', { n: commify(g.from) })}</span>
				</li>
			{/each}
		</ul>
	</div>
</Dialog>
