<script>
	import { t } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import { POLICY_DOCS, PORTAL_URL, recordAgreement } from '../policy.js';
	import { policyGate } from '../policyGate.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import Icon from './Icon.svelte';
	import { fade } from 'svelte/transition';
	import { ms } from '../motion.js';

	/** First-run terms gate: shown over the game until the three documents' current versions are agreed to. */
	let { store } = $props();
	let openKey = $state(null); // one document open at a time
	let loaded = $state({}); // key -> iframe was opened once (we only fetch a document when someone asks to read it)
	// Only after the lobby is up: the loading screen and any refusal/setup screen come first.
	const show = $derived(!policyGate.agreed && store.net.status === 'ready' && store.net.assetsReady);

	function toggle(key) {
		openKey = openKey === key ? null : key;
		if (openKey) loaded[key] = true;
	}
	function agree() {
		recordAgreement();
		policyGate.agreed = true;
		if (store.net.guest) toast(t('play.policy.guestWelcome'), { kind: 'info', duration: 10000 });
	}
</script>

{#if show}
	<div out:fade={{ duration: ms(150) }} class="fixed inset-0 z-[55] grid place-items-center overflow-y-auto bg-(--kk-bg) p-4" role="dialog" aria-modal="true" aria-labelledby="policy-title">
		<div class="kk-card my-auto flex w-full max-w-xl flex-col gap-5 p-6">
			<h1 id="policy-title" class="text-2xl font-black">{t('play.policy.title')}</h1>

			<!-- Content rating -->
			<div class="flex items-center gap-5 rounded-2xl bg-(--kk-panel) p-4">
				<img src={cdnImg('play/game_grade_all.svg')} alt={t('play.policy.gradeAlt')} width="56" height="64" class="h-16 w-14 shrink-0" />
				<p class="text-base leading-relaxed font-extrabold break-keep">{t('play.policy.gradeTitle')}<br />{t('play.policy.gradeBody')}</p>
			</div>

			<!-- The documents. They are separate pages with dark text and no background of their own, so the frame is always white (and `color-scheme: light`, or dark mode would paint it dark under that dark text). -->
			<ul class="flex flex-col">
				{#each POLICY_DOCS as d (d.key)}
					<li class="border-b border-(color:--kk-line) last:border-b-0">
						<h2>
							<button type="button" aria-expanded={openKey === d.key} aria-controls="policy-{d.key}" onclick={() => toggle(d.key)} class="flex min-h-14 w-full items-center gap-2 text-left">
								<span class="text-lg font-extrabold">{t(`play.policy.${d.key}`)}</span>
								<span class="text-base font-bold text-(color:--kk-muted)">{t(`play.policy.${d.key}Version`)}</span>
								<span class="ml-auto inline-flex items-center gap-0.5 text-sm font-bold">{openKey === d.key ? t('play.policy.collapse') : t('play.policy.expand')}<Icon name="expand_more" size={22} class="transition-transform duration-300 motion-reduce:transition-none {openKey === d.key ? 'rotate-180' : ''}" /></span>
							</button>
						</h2>
						<!-- Height animates through a grid row going 0fr -> 1fr; `inert` keeps the closed panel out of the tab order. -->
						<div id="policy-{d.key}" inert={openKey !== d.key} class="grid transition-[grid-template-rows,opacity] duration-300 ease-out motion-reduce:transition-none {openKey === d.key ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'}">
							<div class="min-h-0 overflow-hidden">
								<div class="pb-3">
									{#if loaded[d.key]}
										<iframe src={d.url} title={t(`play.policy.${d.key}`)} loading="lazy" style="color-scheme: light" class="h-64 w-full rounded-lg border-[1.5px] border-(color:--kk-line) bg-white"></iframe>
									{/if}
								</div>
							</div>
						</div>
					</li>
				{/each}
			</ul>

			<p class="text-center text-xs leading-relaxed break-keep">
				{t('play.policy.tailBefore')}<a href={PORTAL_URL} target="_blank" rel="noopener" class="font-bold text-(color:--kk-blue) underline">{t('play.policy.portal')}</a>{t('play.policy.tailAfter')}
			</p>

			<button type="button" onclick={agree} class="kk-btn kk-btn-primary mx-auto min-h-14 w-full max-w-xs text-lg">{t('play.policy.agree')}</button>
		</div>
	</div>
{/if}
