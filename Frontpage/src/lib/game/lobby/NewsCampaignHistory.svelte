<script>
	import Icon from './Icon.svelte';
	import { t } from '../i18n.svelte.js';

	/**
	 * The earlier hot times of a campaign, newest first, each with its total, the tier it reached and whether I was eligible and took the reward.
	 * @type {{past:any[]}}
	 */
	let { past = [] } = $props();
	const num = (n) => Number(n).toLocaleString();
	const time = (ms) => new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
	const day = (ms) => new Date(ms).toLocaleDateString(undefined, { month: 'numeric', day: 'numeric', weekday: 'short' });
	const span = (r) => `${day(r.startsAt)} ${time(r.startsAt)} ~ ${time(r.endsAt)}`;
</script>

<ul aria-label={t('play.news.campaign.history')} class="flex flex-col gap-1">
	{#each past.slice(0, 5) as p (p.key)}
		<li class="flex items-center gap-2 rounded-lg bg-(--kk-surface-2) px-3 py-1.5 text-xs font-bold tabular-nums">
			<span class="min-w-0 flex-1 truncate">{span(p)}</span>
			<span>{num(p.total)}{t('play.news.campaign.unit')}</span>
			<span class="w-14 text-right {p.tier >= 0 ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted)'}">{p.tier >= 0 ? t('play.news.campaign.tierN', { n: p.tier + 1 }) : t('play.news.campaign.missed')}</span>
			<span class="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-extrabold text-white {p.joined ? 'bg-(--kk-accent-strong)' : 'bg-(--kk-danger)'}">{p.joined ? t('play.news.campaign.eligible') : t('play.news.campaign.ineligible')}</span>
			{#if p.joined && p.tier >= 0}<Icon name={p.claimed ? 'check_circle' : 'redeem'} size={16} fill class={p.claimed ? 'text-(color:--kk-accent-strong)' : 'text-(--ac,var(--kk-accent-strong))'} />{/if}
		</li>
	{/each}
</ul>
