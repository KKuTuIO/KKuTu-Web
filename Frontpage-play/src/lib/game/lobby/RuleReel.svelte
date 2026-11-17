<script>
	import { league } from '../leagueStore.svelte.js';
	import RuleReelScene from './RuleReelScene.svelte';

	/**
	 * 리그 매칭이 성사되면 규칙 슬롯머신 장면(RuleReelScene)을 띄우는 연결부. 규칙은 서버가 이미 정했고(`league.matched`),
	 * 장면이 끝나면 `reelOpen`을 끈다. 방은 서버가 만들어 두었고 연출이 끝나는 시각에 준비 없이 바로 시작한다(그 사이 이 장면이 방 화면 위를 덮는다).
	 * 매칭 장면(참가자 소개)은 따로 만들 것이라 여기에는 두지 않는다.
	 * @type {{store:any}}
	 */
	let { store } = $props();
	const m = $derived(league.matched);
</script>

{#if league.reelOpen && m}
	{#key m.matchId}
		<RuleReelScene {store} rules={m.rules} seed={m.matchId} onend={() => (league.reelOpen = false)} />
	{/key}
{/if}
