<script>
	import { onMount } from 'svelte';
	import Icon from './Icon.svelte';
	import RewardBundle from './RewardBundle.svelte';
	import ShareDialog from './ShareDialog.svelte';
	import { t } from '../i18n.svelte.js';
	import { ratio } from '../news.js';
	import { inviteUrl } from '../../invite.js';

	/**
	 * 친구 초대: my link to share, how far I am as someone who was invited, and each friend I invited (as many places as the event allows). A stage is
	 * what the invited one does (games played); when it is done both can take its rewards, each their own.
	 * @type {{store:any, event:any, shop?:any, details?:any, playable:boolean}}
	 */
	let { store, event, shop = {}, details = null, playable } = $props();
	const net = $derived(store.net);
	const info = $derived(event.invite);
	const status = $derived(net.invites[event.id]);
	const stages = $derived(info?.stages ?? []);
	const friends = $derived(Array.from({ length: info?.max ?? 0 }, (_, i) => status?.friends.find((f) => f.n === i + 1) ?? null));
	let pending = $state('');

	onMount(() => {
		store.loadInvite(event.id);
	});
	$effect(() => {
		status; // an answer ends the wait
		pending = '';
	});
	function take(stage, friend) {
		pending = `${friend ?? 0}:${stage}`;
		store.claimInvite(event.id, stage, friend);
		setTimeout(() => pending === `${friend ?? 0}:${stage}` && (pending = ''), 4000);
	}
	let shareOpen = $state(false); // the shared dialog of the links to pass on (the room invitation and the replays use it too)
	const link = $derived(status?.code ? inviteUrl(location.origin, status.code) : '');
	const num = (n) => Number(n).toLocaleString();
</script>

{#snippet stageRow(view, side, friend)}
	{@const stage = stages.find((s) => s.id === view.id)}
	{@const done = view.progress >= view.target}
	<li class="flex flex-col gap-2 rounded-xl border-[1.5px] p-3 @md:flex-row @md:items-center {done && !view.claimed ? 'border-(--ac,var(--kk-accent)) bg-(--kk-accent-soft)' : 'border-(color:--kk-line) bg-(--kk-surface-2)'} {view.claimed ? 'opacity-70' : ''}">
		<div class="flex min-w-0 flex-1 flex-col gap-1">
			<b class="text-sm font-extrabold">{stage?.name ?? view.id}</b>
			<span class="flex items-center gap-2">
				<span class="h-2 min-w-16 flex-1 overflow-hidden rounded-full bg-(--kk-line)" role="progressbar" aria-label={stage?.name} aria-valuemin="0" aria-valuemax={view.target} aria-valuenow={view.progress}><span class="block h-full rounded-full bg-(--ac,var(--kk-accent))" style:width="{ratio(view.progress, view.target) * 100}%"></span></span>
				<span class="text-xs font-extrabold tabular-nums">{num(view.progress)}/{num(view.target)}</span>
			</span>
		</div>
		<RewardBundle rewards={side === 'invitee' ? stage?.inviteeRewards : stage?.inviterRewards} {shop} {details} size={56} />
		<button type="button" disabled={!playable || !done || view.claimed || pending === `${friend ?? 0}:${view.id}`} onclick={() => take(view.id, friend)} class="kk-btn kk-btn-primary inline-flex h-9 min-h-0 shrink-0 items-center justify-center gap-1.5 px-4 text-sm">
			{view.claimed ? t('play.news.claimed') : t('play.news.claim')}
		</button>
	</li>
{/snippet}

<section class="flex flex-col gap-4">
	<h3 class="text-base font-extrabold">{t('play.news.invite')}</h3>

	<!-- my link: the code, and the whole link to pass on -->
	<div class="flex flex-wrap items-center gap-3 rounded-2xl bg-(--kk-surface-2) p-3.5 shadow-[0_3px_0_var(--kk-line)]">
		<Icon name="group_add" size={32} fill class="text-(--ac,var(--kk-accent-strong))" />
		<div class="flex min-w-40 flex-1 flex-col">
			<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.news.invite.mycode')}</span>
			<b class="font-mono text-2xl tracking-widest tabular-nums" aria-label={t('play.news.invite.mycode')}>{status?.code ?? '········'}</b>
		</div>
		<button type="button" disabled={!link} onclick={() => (shareOpen = true)} class="kk-btn kk-btn-primary inline-flex h-10 min-h-0 items-center justify-center gap-1.5 px-4 text-sm"><Icon name="share" size={18} />{t('play.news.invite.copy')}</button>
	</div>

	{#if status?.invitee}
		<div class="flex flex-col gap-2">
			<h4 class="text-sm font-extrabold">{t('play.news.invite.asInvitee')}</h4>
			<ul class="flex flex-col gap-2">
				{#each status.invitee.stages as view (view.id)}{@render stageRow(view, 'invitee', undefined)}{/each}
			</ul>
		</div>
	{/if}

	<div class="flex flex-col gap-2">
		<h4 class="flex items-baseline justify-between text-sm font-extrabold">{t('play.news.invite.friends')}<span class="tabular-nums">{status?.friends.length ?? 0}/{info?.max ?? 0}</span></h4>
		<ul class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,22rem),1fr))] gap-3">
			{#each friends as friend, i (i)}
				<li class="flex flex-col gap-2 rounded-2xl border-[1.5px] border-(color:--kk-line) p-3 {friend ? '' : 'border-dashed opacity-80'}" aria-label={t('play.news.invite.friend', { n: i + 1 })}>
					<b class="flex items-center gap-1.5 text-sm font-extrabold"><Icon name={friend ? 'person' : 'person_add'} size={18} fill={!!friend} />{t('play.news.invite.friend', { n: i + 1 })}</b>
					{#if friend}
						<ul class="flex flex-col gap-2">
							{#each friend.stages as view (view.id)}{@render stageRow(view, 'inviter', friend.n)}{/each}
						</ul>
					{:else}
						<p class="text-[13px] font-bold text-(color:--kk-muted)">{t('play.news.invite.empty')}</p>
					{/if}
				</li>
			{/each}
		</ul>
	</div>
</section>

<ShareDialog bind:open={shareOpen} title={t('play.news.invite.share')} {link} shareTitle={t('play.news.invite.shareTitle')} shareText={t('play.news.invite.shareText', { nick: net.users[net.id]?.name ?? '' })} copied={t('play.news.invite.copied')} />
