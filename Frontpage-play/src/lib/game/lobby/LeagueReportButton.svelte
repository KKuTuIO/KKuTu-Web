<script>
	import { t } from '../i18n.svelte.js';
	import { league } from '../leagueStore.svelte.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';

	/**
	 * 결과 화면의 "경기 신고"(아이콘 버튼, 이름은 툴팁). 누르면 신고할 상대를 고르는 창이 열리고, 고르면 일반 신고 창(ReportDialog)이 그 상대로 열린다.
	 * - 트로피 리그: 상대의 실제 닉네임도 ID도 보이지 않는다. 결과에 실려 온 상대 목록(판 안에서 쓴 임시 닉네임·등수·레벨, 서버만 읽는 토큰)에서 고른다.
	 *   누가 사람이고 누가 봇인지는 이 목록으로도 알 수 없다.
	 * - 일반 경기: 결과 화면의 참가자(`players`) 중 나를 뺀 사람에서 고른다.
	 * @type {{store:any, players?:any[]}}
	 */
	let { store, players = [] } = $props();
	let open = $state(false);
	const leagueRivals = $derived([...league.rivals].sort((a, b) => a.placement - b.placement || b.level - a.level));
	const others = $derived(players.filter((p) => !p.me && p.user && !p.user.robot).map((p) => ({ id: p.id, name: p.user.name, level: p.user.lv, rank: p.rank })));
	const inLeague = $derived(leagueRivals.length > 0);
	const shown = $derived(inLeague ? leagueRivals.length : others.length);
	const label = $derived(t('play.league.report.button'));
	const pick = (r) => {
		open = false;
		store.hooks.openReport?.(inLeague ? { type: 'league', token: r.token, placement: r.placement, level: r.level, name: r.name } : { type: 'user', id: r.id, name: r.name });
	};
</script>

{#if shown && !store.net.guest}
	<Tooltip text={label}><button type="button" class="kk-btn log icon" aria-label={label} onclick={() => (open = true)}><Icon name="flag" size={22} /></button></Tooltip>
	<Dialog bind:open title={t('play.league.report.title')} bouncy={false}>
		<div class="flex flex-col gap-3">
			<p class="text-sm text-(color:--kk-muted)">{t(inLeague ? 'play.league.report.hint' : 'play.result.report.hint')}</p>
			<ul class="flex flex-col gap-2">
				{#each inLeague ? leagueRivals : others as r (r.token ?? r.id)}
					<li>
						<button type="button" onclick={() => pick(r)} class="flex min-h-12 w-full items-center gap-3 rounded-xl border-[1.5px] border-(color:--kk-line) bg-(--kk-surface) px-4 text-left font-bold shadow-[0_2px_0_var(--kk-line)] hover:border-(color:--kk-accent)">
							<Icon name="person" size={22} class="shrink-0 text-(color:--kk-accent-strong)" />
							<span class="min-w-0 flex-1 truncate">{inLeague ? t('play.league.report.rival', { n: r.placement + 1, name: r.name, level: r.level }) : t('play.result.report.player', { n: r.rank, name: r.name, level: r.level })}</span>
							<Icon name="chevron_right" size={22} class="shrink-0 text-(color:--kk-muted)" />
						</button>
					</li>
				{/each}
			</ul>
		</div>
	</Dialog>
{/if}
