<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from '../lobby/Dialog.svelte';
	import Icon from '../lobby/Icon.svelte';
	import PriceChip from '../lobby/PriceChip.svelte';
	import { LIMITS, oath, sameOath } from './model.js';
	import { toast } from '../ui/toast.svelte.js';
	import { attempt, bridge, me, showLicense, takeLicense } from './store.svelte.js';

	/** 말모이 집필 면허: the oath is typed line by line, then the fee is paid. */
	let { open = $bindable(false) } = $props();
	let typed = $state(oath().map(() => ''));
	let busy = $state(false);
	const done = $derived(oath().map((line, i) => sameOath(typed[i], line)));
	const short = $derived(me.ping < LIMITS.fee);
	const low = $derived(me.level < LIMITS.publishLevel);
	$effect(() => {
		if (open) typed = oath().map(() => '');
	});
	async function submit(e) {
		e.preventDefault();
		if (low || !done.every(Boolean) || busy) return;
		busy = true;
		const r = await attempt(() => takeLicense($state.snapshot(typed)), toast);
		busy = false;
		if (!r) return;
		bridge.setMoney(r.money); // the game's own Ping number was paid on the server's side
		open = false;
		showLicense(true); // the card itself says it was issued (LicenseCardScreen, mounted once on the page)
	}
</script>

<Dialog bind:open title={t('play.malmoi.license.title')} size="lg">
	{#if low}
		<p class="py-6 text-center text-sm font-extrabold break-keep">{t('play.malmoi.license.fromLevel', { n: LIMITS.publishLevel, level: me.level })}</p>
	{:else}
	<form onsubmit={submit} class="flex flex-col gap-4">
		<fieldset class="flex flex-col gap-3">
			<legend class="pb-2 text-sm font-extrabold">{t('play.malmoi.license.legend', { days: LIMITS.licenseDays })}</legend>
			{#each oath() as line, i (line)}
				<label class="flex flex-col gap-1">
					<span class="text-sm font-bold select-none">{i + 1}. {line}</span>
					<span class="flex items-center gap-2">
						<!-- two lines on a phone, so the whole sentence being typed stays in sight -->
						<textarea bind:value={typed[i]} onpaste={(e) => e.preventDefault()} onkeydown={(e) => e.key === 'Enter' && e.preventDefault()} autocomplete="off" rows="1" class="h-11 min-w-0 flex-1 resize-none kk-input px-3 py-2 max-sm:h-17"></textarea>
						<span class="grid size-6 place-items-center {done[i] ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-line-strong)'}" aria-label={done[i] ? t('play.malmoi.license.typedRight') : t('play.malmoi.license.notYet')} role="img"><Icon name="check_circle" size={22} fill={done[i]} /></span>
					</span>
				</label>
			{/each}
		</fieldset>
		<div class="flex justify-end border-t border-(color:--kk-line) pt-3">
			<button class="kk-btn kk-btn-primary inline-flex min-h-12 items-center gap-2.5 px-6" disabled={short || busy || !done.every(Boolean)}>
				{t('play.malmoi.license.swear')}<PriceChip price={LIMITS.fee} lacking={short} title={short ? t('play.malmoi.license.lackingPing') : undefined} />
			</button>
		</div>
	</form>
	{/if}
</Dialog>
