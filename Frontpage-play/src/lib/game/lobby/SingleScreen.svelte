<script>
	import { toast } from '../ui/toast.svelte.js';
	import Icon from './Icon.svelte';
	import { t } from '../i18n.svelte.js';

	/**
	 * The single-player list (아케이드); the games are placeholders for now. `lead` is a snippet at the left end of the header (the new lobby's back button).
	 * Nothing starts yet. On a wide stage the games sit in two columns.
	 * @type {{lead?:import('svelte').Snippet}}
	 */
	let { lead } = $props();
	const games = [
		{ id: 'daily', name: '오늘의 워들', note: '하루 한 문제 · 새 문제까지 06:12:00', badge: true, side: '미완료' },
		{ id: 'wordle', name: '워들', note: '한국어 · 자유 플레이', side: '최고 3회' },
		{ id: 'sokssok', name: '미니 솎솎', note: '규칙 확인 필요' },
		{ id: 'typing', name: '타자 연습', note: '단어 · 문장', side: '최고 412타' },
		{ id: 'math', name: '수학 연습', note: '사칙연산 · 난이도 선택' }
	];
</script>

<div class="flex min-h-0 flex-1 flex-col">
	<div class="flex items-center gap-3 px-3 py-2">
		{@render lead?.()}
		<h2 class="title text-xl font-extrabold">{t('play.lobby.arcade')}</h2>
	</div>
	<ul class="grid min-h-0 flex-1 auto-rows-min grid-cols-1 content-start gap-2.5 overflow-y-auto px-3 pt-1 pb-3 md:grid-cols-2">
		{#each games as g (g.id)}
			<li>
				<button type="button" onclick={() => toast('준비 중입니다', { kind: 'info', icon: 'event' })} class="row flex min-h-20 w-full items-center gap-3 rounded-2xl px-4 py-3 text-left">
					<span class="min-w-0 flex-1">
						<span class="flex items-center gap-1.5 text-base font-extrabold">{g.name}{#if g.badge}<span class="size-2 rounded-full bg-red-500" aria-label="새 문제"></span>{/if}</span>
						<span class="block text-xs font-bold text-(color:--kk-muted)">{g.note}</span>
					</span>
					{#if g.side}<span class="shrink-0 text-xs font-bold text-(color:--kk-muted) tabular-nums">{g.side}</span>{/if}
					<Icon name="chevron_right" size={22} class="text-(color:--kk-muted)" />
				</button>
			</li>
		{/each}
	</ul>
</div>

<style>
	/* each game sits on its own translucent container so it reads on the blurred stage; the title is white with a shade */
	.row {
		background: color-mix(in srgb, var(--kk-surface) 78%, transparent);
		box-shadow: 0 2px 6px rgb(20 40 80 / 0.22);
		transition: background 0.15s, transform 0.15s;
	}
	.row:hover {
		background: color-mix(in srgb, var(--kk-surface) 94%, transparent);
		transform: translateY(-1px);
	}
	.title {
		color: #fff;
		text-shadow: 0 1px 4px rgb(10 14 50 / 0.65);
	}
</style>
