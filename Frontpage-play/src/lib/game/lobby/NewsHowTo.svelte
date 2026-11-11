<script>
	import Icon from './Icon.svelte';
	import { i18n, t } from '../i18n.svelte.js';
	import { howTo } from '../news.js';

	/**
	 * "이렇게 진행돼요": what an event of its own kind (a campaign, a tile roulette) needs explained, written in the client. An event of a kind with nothing
	 * to explain shows nothing. The goal of a campaign is named by its signal; one the client has no words for is called "활동".
	 * @type {{event:any}}
	 */
	let { event } = $props();
	const lines = $derived(howTo(event));
	const text = (line) => {
		const args = { ...line.args };
		if (args.metric !== undefined) args.metric = `play.news.how.metric.${args.metric}` in i18n.messages ? t(`play.news.how.metric.${args.metric}`) : t('play.news.how.metric.other');
		if (typeof args.n === 'number') args.n = args.n.toLocaleString();
		return t(line.key, args);
	};
</script>

{#if lines.length}
	<section aria-label={t('play.news.how')} class="flex flex-col gap-2 rounded-2xl bg-(--kk-surface-2) p-3.5">
		<h4 class="flex items-center gap-1.5 text-sm font-extrabold"><Icon name="help" size={18} fill class="text-(--ac,var(--kk-accent-strong))" />{t('play.news.how')}</h4>
		<ul class="flex flex-col gap-1.5 text-sm font-semibold">
			{#each lines as line (line.key)}
				<li class="flex gap-2"><Icon name="check" size={16} class="mt-0.5 shrink-0 text-(--ac,var(--kk-accent-strong))" /><span>{text(line)}</span></li>
			{/each}
		</ul>
	</section>
{/if}
