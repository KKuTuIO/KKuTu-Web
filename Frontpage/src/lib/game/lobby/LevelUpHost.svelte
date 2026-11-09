<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { crossedLevels } from '../levelUp.js';
	import { levelUp } from '../levelUp.svelte.js';
	import { loadGoodDetails } from '../shop.js';
	import Icon from './Icon.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import ObtainScreen from './ObtainScreen.svelte';

	/**
	 * Shows the level-up popups on the shared reward screen (`ObtainScreen`), on top of whatever else is open. Also watches my
	 * experience: a level reached any other way than a game result (a 백과사전 낱장, a coupon) is announced from here; the result
	 * board announces its own as its gauge passes them.
	 */
	let { store } = $props();
	const cur = $derived(levelUp.current);
	const entry = $derived(cur?.entry);
	const gift = $derived(entry?.gift);
	const isLast = $derived(cur ? cur.index === cur.total - 1 : true);
	const items = $derived(gift ? [...(gift.ping > 0 ? [{ key: 'ping', q: gift.ping }] : []), ...gift.items] : []);
	const mail = $derived(gift?.mail?.length ? t('play.levelUp.mail', { count: gift.mail.reduce((n, m) => n + m.q, 0) }) : '');

	let open = $state(false);
	let details = $state(null);
	onMount(() => {
		loadGoodDetails().then((d) => (details = d));
		return () => levelUp.reset();
	});
	$effect(() => {
		open = !!cur;
	});

	// timed gifts wait in the mailbox: look again so its count is right when the popup says so
	$effect(() => {
		if (entry?.gift?.mail?.length) store.requestMailbox();
	});

	// experience that rose outside a game result
	let last = null;
	$effect(() => {
		const score = store.net.users[store.net.id]?.score;
		if (typeof score !== 'number') return;
		if (last !== null && score > last && !store.game?.result) {
			const levels = crossedLevels(last, score);
			if (levels.length) levelUp.announce(levels, (level) => store.net.levelGifts[level] ?? null);
		}
		last = score;
	});
</script>

<ObtainScreen
	bind:open
	title={t('kkutu.js.lvUp')}
	palette="violet"
	notice={mail}
	ribbon
	{items}
	shop={store.shop}
	{details}
	rev={cur?.step ?? 0}
	kind={entry?.kind}
	ambient="shine"
	confirmLabel={t(isLast ? 'play.levelUp.done' : 'play.levelUp.next')}
	skipLabel={isLast ? '' : t('play.levelUp.skip')}
	counter={cur && cur.total > 1 ? t('play.levelUp.count', { current: cur.index + 1, total: cur.total }) : ''}
	onconfirm={() => levelUp.next()}
	onskip={() => levelUp.skip()}
>
	{#snippet lead()}
		<!-- a run of levels reads [icon] 레벨 5 >>> [icon] 레벨 100, the chevrons fading in -->
		<div class="lvrow">
			{#if entry?.kind === 'summary'}
				<LevelIcon level={entry.from} size={34} />
				<b>{t('play.level', { level: entry.from })}</b>
				<span class="fade" aria-hidden="true"><Icon name="chevron_right" size={28} weight={700} /><Icon name="chevron_right" size={28} weight={700} class="-ml-4" /><Icon name="chevron_right" size={28} weight={700} class="-ml-4" /></span>
				<LevelIcon level={entry.to} size={34} />
				<b>{t('play.level', { level: entry.to })}</b>
			{:else if entry}
				<LevelIcon level={entry.level} size={34} />
				<b>{t('play.level', { level: entry.level })}</b>
			{/if}
		</div>
	{/snippet}
</ObtainScreen>

<style>
	.lvrow {
		display: inline-flex;
		align-items: center;
		gap: 0.6rem;
		font-size: 1.25rem;
		font-weight: 800;
		line-height: 1;
	}
	/* clear at the tail, solid at the head: the three chevrons' glyphs are filled by a gradient of the theme's text colour, so it reads on the
	   light band and on the dark one alike */
	.fade {
		display: inline-flex;
		align-items: center;
		background: linear-gradient(90deg, color-mix(in srgb, var(--kk-text) 12%, transparent), var(--kk-text));
		-webkit-background-clip: text;
		background-clip: text;
		color: transparent;
	}
</style>
