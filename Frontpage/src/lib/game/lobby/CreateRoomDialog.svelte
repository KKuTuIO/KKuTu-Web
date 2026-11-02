<script>
	import Icon from './Icon.svelte';
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import Seekbar from '../ui/Seekbar.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import GameModePicker from './GameModePicker.svelte';
	import GameOptions from './GameOptions.svelte';
	let { store, open = $bindable(false) } = $props();
	const net = $derived(store.net);
	const uid = `room-${Math.random().toString(36).slice(2, 7)}`;
	let title = $state('');
	let suggestion = $state(''); // shown as the placeholder; used as the name if the field is left blank
	let password = $state('');
	let limit = $state(8);
	let round = $state(5);
	let time = $state(60);
	let mode = $state(2);
	let opts = $state({});
	const rule = $derived(net.meta.rules[net.meta.modes[mode]]);
	const times = [5, 10, 30, 60, 90, 120, 150];
	// Same estimate as the legacy client: rounds x seconds per round, in whole minutes, rounded up.
	const etaMinutes = $derived(Math.ceil((round * time * (rule?.time ?? 1)) / 60));

	// Every time the dialog opens: a fresh suggested name.
	$effect(() => {
		if (open) suggestion = store.randomRoomTitle('');
	});

	function submit(e) {
		e.preventDefault();
		store.createRoom({
			title: title.trim() || suggestion,
			password,
			limit: Math.min(Math.max(limit, 2), store.roomLimitMax),
			mode,
			round: Math.min(Math.max(round, 1), 10),
			time,
			opts: Object.fromEntries(Object.entries(opts).filter(([, on]) => on))
		});
		open = false;
		title = '';
	}
</script>

<!-- One wide page instead of tabs: the game (left) and the room it will be played in (right) are chosen together. -->
<Dialog bind:open title={t('play.create.title')} size="lg">
	<form onsubmit={submit} class="flex flex-col gap-4">
		<div class="grid gap-6 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)] lg:gap-8">
			<section aria-labelledby="{uid}-game" class="flex flex-col gap-4">
				<h3 id="{uid}-game" class="text-sm font-extrabold">{t('play.create.sectionGame')}</h3>
				<GameModePicker {net} bind:mode idPrefix={uid} />
				<!-- Reserve room for the longest rule list so the dialog keeps its height when the game changes. -->
				<div class="min-h-[20rem]"><GameOptions {net} bind:mode bind:opts showMode={false} dense /></div>
			</section>

			<section aria-labelledby="{uid}-room" class="flex flex-col gap-4">
				<h3 id="{uid}-room" class="text-sm font-extrabold">{t('play.create.sectionRoom')}</h3>
				<div class="flex flex-col gap-1">
					<label for="{uid}-title" class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.roomTitle')}</label>
					<div class="flex items-stretch gap-2">
						<input id="{uid}-title" bind:value={title} maxlength="20" placeholder={suggestion} class="h-11 min-w-0 flex-1 kk-input rounded-xl px-3" />
						<Tooltip text={t('play.create.randomTitle')} placement="bottom">
							<button type="button" aria-label={t('play.create.randomTitle')} onclick={() => (title = store.randomRoomTitle(title || suggestion))} class="kk-btn grid size-11 shrink-0 place-items-center rounded-xl p-0">
								<Icon name="casino" size={22} />
							</button>
						</Tooltip>
					</div>
				</div>
				<div class="flex flex-col gap-1">
					<label for="{uid}-pw" class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.password')}</label>
					<input id="{uid}-pw" bind:value={password} type="password" autocomplete="off" maxlength="20" class="h-11 kk-input rounded-xl px-3" />
				</div>
				<div class="flex flex-col gap-1">
					<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.limit')}</span>
					<Seekbar bind:value={limit} min={2} max={store.roomLimitMax} label={t('play.create.limit')} format={(n) => t('play.create.limitValue', { n })} />
				</div>
				<div class="flex flex-col gap-1">
					<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.round')}</span>
					<Seekbar bind:value={round} min={1} max={10} label={t('play.create.round')} format={(n) => t('play.create.roundValue', { n })} />
				</div>
				<div class="flex flex-col gap-1">
					<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.time')}</span>
					<RadioGroup variant="chips" label={t('play.create.time')} bind:value={time} options={times.map((sec) => ({ value: sec, label: t('play.create.timeValue', { n: sec * (rule?.time ?? 1) }) }))} />
				</div>
			</section>
		</div>

		<!-- Stays visible while the form scrolls on shorter screens. -->
		<div class="sticky -bottom-4 z-10 -mx-4 -mb-4 flex items-center justify-between gap-3 border-t border-(color:--kk-line) bg-(--kk-surface) px-4 py-3">
			<p class="flex items-center gap-1.5 text-xs font-bold text-(color:--kk-muted)"><Icon name="schedule" size={18} />{t('play.create.eta', { n: etaMinutes })}</p>
			<button class="kk-btn kk-btn-primary min-h-12 px-8 text-base">{t('play.create.submit')}</button>
		</div>
	</form>
</Dialog>
