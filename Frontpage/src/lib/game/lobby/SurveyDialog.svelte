<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { plainText } from '../protocol.js';
	import Dialog from './Dialog.svelte';
	import EmptyState from './EmptyState.svelte';
	import Icon from './Icon.svelte';

	/**
	 * 설문 조사 확인: the surveys one can take part in for a reward (ping, experience), with their period; 참여하기 opens the survey (a Google form
	 * in a new tab, any other inside this dialog) with this account's token, as the legacy client did.
	 */
	let { store, open = $bindable(false) } = $props();
	const net = $derived(store.net);
	let frame = $state(null); // {url, name}
	$effect(() => {
		if (open) {
			frame = null;
			store.loadSurveys();
		}
	});
	const list = $derived([...(net.surveys ?? [])].sort((a, b) => new Date(b.openAt) - new Date(a.openAt)));
	const when = (v) => new Date(v).toLocaleString(i18n.language);
	const rewards = (s) => [s.gives?.ping && `${Number(s.gives.ping).toLocaleString()}${t('kkutu.js.ping')}`, s.gives?.xp && `${Number(s.gives.xp).toLocaleString()}${t('kkutu.js.xp')}`].filter(Boolean).join(', ');
	const ready = $derived(!!net.surveyToken);

	function participate(s) {
		if (!ready) return;
		const me = net.users[net.id];
		const nick = me ? me.name + (me.tag ? `#${me.tag}` : '') : t('kkutu.js.hidden');
		const tok = net.surveyToken;
		if (s.surveyUrl.startsWith('https://docs.google.com')) {
			const url = s.surveyUrl.replace('{surveyToken}', encodeURIComponent(tok.value)).replace('{surveyDate}', tok.date).replace('{userId}', net.id);
			window.open(url, '_blank', 'noopener');
			return toast(plainText(t('kkutu.js.surveyNewTab')), { kind: 'info' });
		}
		const u = new URL(s.surveyUrl);
		u.search = new URLSearchParams({ user_id: net.id, nickname: nick, survey_token: tok.value, date: tok.date, embed: 'true' }).toString();
		frame = { url: u.toString(), name: s.name };
	}
</script>

<Dialog bind:open title={frame ? frame.name : t('kkutu.dialog.survey.title')} size="lg">
	{#if frame}
		<div class="flex flex-col gap-3">
			<button type="button" onclick={() => (frame = null)} class="kk-btn inline-flex min-h-10 items-center gap-1.5 self-start px-3 text-sm"><Icon name="arrow_back" size={20} />{t('kkutu.dialog.survey.title')}</button>
			<iframe src={frame.url} title={frame.name} class="h-[60vh] w-full rounded-xl border-[1.5px] border-(color:--kk-line) bg-white"></iframe>
		</div>
	{:else if list.length}
		<ul class="flex max-h-[60vh] flex-col gap-3 overflow-y-auto pr-1">
			{#each list as s, i (i)}
				<li class="flex flex-col gap-1.5 rounded-xl border-[1.5px] border-(color:--kk-line) p-3">
					<div class="flex flex-wrap items-center gap-2">
						<b class="min-w-0 flex-1 text-base">{s.name}</b>
						{#if rewards(s)}<span class="kk-chip shrink-0">{rewards(s)}</span>{/if}
					</div>
					<p class="text-sm leading-relaxed">{s.desc}</p>
					<p class="text-xs text-(color:--kk-muted) tabular-nums">{when(s.openAt)} - {when(s.closeAt)}</p>
					<button type="button" disabled={!ready} onclick={() => participate(s)} class="kk-btn kk-btn-primary mt-1 min-h-10 self-end px-5">{t('kkutu.js.participate')}</button>
				</li>
			{/each}
		</ul>
	{:else}
		<EmptyState compact title={net.surveys ? t('play.settings.survey.none') : t('play.profile.loading')} />
	{/if}
</Dialog>
