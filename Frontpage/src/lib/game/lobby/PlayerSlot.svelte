<script>
	import { t } from '../i18n.svelte.js';
	import Moremi from './Moremi.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import NickName from './NickName.svelte';
	import RatingIcon from './RatingIcon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { prefs } from '../prefs.svelte.js';
	import { fullNick } from './nickTip.js';
	import Icon from './Icon.svelte';
	import ChatBalloon from './ChatBalloon.svelte';

	/**
	 * One seat in the waiting room, laid out like the legacy room screen: Moremi on the left, status and team pills on the
	 * right, level + name along the bottom. Clicking the seat opens the player's profile (where the master manages them).
	 */
	let { player, store, isMaster = false, isMe = false, size = 96, compact = false, onopen } = $props();
	const parts = $derived(store.net.meta.moremiParts);
	const g = $derived(player.game);
	const status = $derived(
		isMaster ? 'master' : player.robot ? 'ready' : g.form === 'S' ? 'spectate' : g.practice ? 'practice' : g.ready ? 'ready' : 'wait'
	);
	const team = $derived(g.form === 'S' ? null : g.team);
	const TEAM_LABEL = ['', 'A', 'B', 'C', 'D'];
	// Long Korean nicknames get a smaller font so they still fit (legacy "nick-small").
	const small = $derived(/[ㄱ-ㅎㅏ-ㅣ가-힣]/.test(player.name) && player.name.length > 10);

</script>

{#snippet pills()}
	<div class="flex flex-wrap items-center justify-end gap-1.5 {compact ? '' : 'w-full'}">
		<span class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-extrabold {status === 'practice' ? 'text-[#2c2200]' : 'text-white'}" style:background-color="var(--kk-st-{status})">
			{t(`play.room.status.${status}`)}
			<!-- The master can also be practising or spectating: legacy marks that next to 방장 -->
			{#if isMaster && g.practice}<Icon name="edit" size={14} />{/if}
			{#if isMaster && g.form === 'S'}<Icon name="visibility" size={14} />{/if}
		</span>
		{#if team !== null && team > 0}
			<span class="rounded-lg px-2 py-1 text-xs font-extrabold text-[#111]" style:background-color="var(--kk-team-{team})">{TEAM_LABEL[team]}</span>
		{/if}
	</div>
{/snippet}
{#snippet who()}
	<div class="flex min-w-0 items-center gap-1.5 {compact ? '' : 'w-full justify-center'}">
		<LevelIcon level={player.robot ? 1 : player.lv} size={compact ? 22 : 24} maskable={!isMe && !player.robot} />
		<Tooltip text={prefs.hideUsers && !isMe && !player.robot ? t('play.anonymous') : fullNick(player)}><span class="min-w-0 truncate font-extrabold {small ? 'text-xs' : ''}"><NickName user={player} showCheck maskable={!isMe && !player.robot} /></span></Tooltip>
		{#if !player.robot && player.rating != null && player.rating < 2}<Tooltip text={t(`play.rating.${player.rating}`)}><span class="inline-flex shrink-0"><RatingIcon rating={player.rating} size={compact ? 14 : 16} /></span></Tooltip>{/if}
		{#if isMe && !compact}<span class="kk-chip shrink-0">{t('play.room.you')}</span>{/if}
	</div>
{/snippet}

<li class="relative h-full min-h-0">
	<ChatBalloon {store} id={player.id} class="top-9" />
	<button
		type="button"
		onclick={(e) => onopen?.(player, e)}
		aria-label="{player.name}, {t(`play.room.status.${status}`)}"
		class="kk-card flex h-full min-h-0 w-full p-2.5 text-left transition-transform hover:-translate-y-0.5 {compact ? 'items-center gap-2' : 'flex-col items-center'} {isMe ? 'border-(color:--kk-accent)' : ''}"
	>
		{#if compact}
			<!-- Short cards (big rooms): Moremi on the left, pills above name on the right. -->
			<Moremi equip={player.equip} shop={store.shop} {parts} {size} label={player.name} frame={false} maskable={!isMe && !player.robot} />
			<div class="flex h-full min-w-0 flex-1 flex-col items-end justify-between py-0.5">
				{@render pills()}
				<div class="w-full min-w-0">{@render who()}</div>
			</div>
		{:else}
			{@render pills()}
			<div class="grid min-h-0 flex-1 place-items-center"><Moremi equip={player.equip} shop={store.shop} {parts} {size} label={player.name} frame={false} maskable={!isMe && !player.robot} /></div>
			{@render who()}
		{/if}
	</button>
</li>
