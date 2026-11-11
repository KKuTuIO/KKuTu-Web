<script>
	import Icon from './Icon.svelte';
	import { t } from '../i18n.svelte.js';
	import { untrack } from 'svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { choose } from '../ui/confirm.svelte.js';
	import Select from '../ui/Select.svelte';
	import { plainText } from '../protocol.js';
	import Dialog from './Dialog.svelte';
	import Seekbar from '../ui/Seekbar.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import Checkbox from '../ui/Checkbox.svelte';
	import GameModePicker from './GameModePicker.svelte';
	import GameOptions from './GameOptions.svelte';
	import { needsThemes as rulePicks } from '../lobbyModel.js';
	// ---- what the legacy room dialog remembers and asks ----
	// The settings of the last room made in each mode ("roomPresets", the same record the old client keeps): choosing that mode
	// again brings its rules and themes back.
	const PRESETS = 'roomPresets';
	const LAST_MODE = 'roomLastMode'; // the mode of the last room made
	const loadPresets = () => {
		try {
			return JSON.parse(localStorage.getItem(PRESETS) ?? '{}') || {};
		} catch {
			return {};
		}
	};
	let askedRankGame = false; // the "친선전" question is put once per visit (legacy $data.state.rg)

	/** With `editing` set to the current room, the same form changes that room's settings instead of creating one. */
	let { store, open = $bindable(false), editing = null } = $props();
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
	let injpick = $state([]);
	let clearPassword = $state(false);
	const rule = $derived(net.meta.rules[net.meta.modes[mode]]);
	const times = [5, 10, 30, 60, 90, 120, 150];
	// Same estimate as the legacy client: rounds x seconds per round, in whole minutes, rounded up.
	const etaMinutes = $derived(Math.ceil((round * time * (rule?.time ?? 1)) / 60));

	let lastMode = -1;
	// Choosing another mode (legacy #room-mode change): the themes start over, the typing / math modes play 3 rounds, and the
	// rules and themes of the last room made in that mode come back.
	$effect(() => {
		const m = mode;
		untrack(() => {
			if (m === lastMode) return;
			lastMode = m;
			const r = net.meta.rules[net.meta.modes[m]];
			injpick = [];
			if (r?.rule === 'Typing' || r?.rule === 'Mathquiz') round = 3;
			const preset = loadPresets()[m];
			if (!preset) return;
			for (const [k, v] of Object.entries(preset.opts ?? {})) if (typeof v === 'boolean') opts[k] = v;
			if (preset.injpick?.length) injpick = [...preset.injpick];
		});
	});

	// Every time the dialog opens: a fresh suggested name, or (editing) the room's current settings.
	$effect(() => {
		if (!open) return;
		// Everything below reads state it also sets; only opening should run it (and the guest notice must be said once).
		untrack(() => {
			clearPassword = false;
			password = '';
			if (editing) {
				const r = net.meta.rules[net.meta.modes[editing.modeIndex]];
				title = editing.title;
				suggestion = editing.title;
				limit = editing.limit;
				round = editing.rounds;
				time = Math.round(editing.time / (r?.time ?? 1)); // the server stores time already multiplied by the rule's factor
				mode = editing.modeIndex;
				lastMode = mode; // opening is not a change of mode: the room's own settings stay
				opts = { ...editing.opts };
				injpick = [...(editing.injpick ?? [])];
			} else {
				title = '';
				injpick = [];
				// Members: "<nickname>님의 방" is the name when the field is left empty. Guests may only pick from fixed titles.
				suggestion = net.guest ? store.randomRoomTitle('') : `${net.users[net.id]?.name ?? ''}${t('kkutu.dialog.room.room-title.placeholder')}`;
				if (net.guest) {
					title = suggestion;
					toast(plainText(t('kkutu.js.roomTitleRestricted')), { kind: 'info' });
				}
				// The mode of the last room made is where the dialog starts.
				try {
					const saved = Number(localStorage.getItem(LAST_MODE));
					if (localStorage.getItem(LAST_MODE) !== null && net.meta.modes[saved]) mode = saved;
				} catch {
					/* no storage */
				}
			}
		});
	});

	async function submit(e) {
		e.preventDefault();
		const picked = title.trim() || suggestion;
		if (!store.titleAllowed(picked)) return toast(t('play.create.titleBad'), { kind: 'error' });
		// A 단어장 game needs at least one theme (the server refuses to start otherwise).
		const needsThemes = !!rule && rulePicks(rule, opts);
		if (needsThemes && !injpick.length) return toast(net.meta.labels['kkutu.js.error.413'] ?? t('play.options.themesNone'), { kind: 'error' });
		const form = {
			title: picked,
			limit: Math.min(Math.max(limit, 2), store.roomLimitMax),
			mode,
			round: Math.min(Math.max(round, 1), 10),
			time,
			opts: Object.fromEntries(Object.entries(opts).filter(([, on]) => on)),
			injpick: needsThemes ? [...injpick] : []
		};
		// A password without 친선전 makes a game whose early leavers lose rating: ask once whether to switch it on.
		if (!editing && !askedRankGame && password && !opts.rankmode && net.meta.optionDefs && Object.values(net.meta.optionDefs).some((d) => d.name.toLowerCase() === 'rankmode')) {
			askedRankGame = true;
			// Like the legacy two-handed question: left switches it on, right keeps it off, and closing the question cancels the creation.
			const pick = await choose({
				message: (net.meta.labels['kkutu.js.sureRankGame'] ?? '').replace(/<\/?b>/g, ''),
				options: [
					{ value: 'on', label: net.meta.labels['kkutu.js.sureRankGame.on'] },
					{ value: 'off', label: net.meta.labels['kkutu.js.sureRankGame.off'] }
				]
			});
			if (!pick) return;
			if (pick === 'on') {
				opts.rankmode = true;
				form.opts.rankmode = true;
			}
		}
		try {
			const presets = loadPresets();
			presets[mode] = { opts: { ...opts }, injpick: [...injpick] };
			localStorage.setItem(PRESETS, JSON.stringify(presets));
			if (!editing) localStorage.setItem(LAST_MODE, String(mode));
		} catch {
			/* no storage: nothing is remembered */
		}
		if (editing) {
			// Blank keeps the current password; the checkbox removes it; typing sets a new one.
			store.updateRoom({ ...form, password: clearPassword ? '' : password ? password : undefined });
		} else {
			store.createRoom({ ...form, password });
		}
		open = false;
		title = '';
	}
</script>

<!-- One wide page instead of tabs: the game (left) and the room it will be played in (right) are chosen together. -->
<Dialog bind:open title={editing ? t('play.create.editTitle') : t('play.create.title')} size="lg">
	<form onsubmit={submit} class="flex flex-col gap-4">
		<div class="grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)] lg:gap-8">
			<section aria-labelledby="{uid}-game" class="flex min-w-0 flex-col gap-4">
				<h3 id="{uid}-game" class="text-sm font-extrabold">{t('play.create.sectionGame')}</h3>
				<div data-tour="create-mode"><GameModePicker {net} bind:mode idPrefix={uid} /></div>
				<!-- Reserve room for the longest rule list so the dialog keeps its height when the game changes. -->
				<div data-tour="create-options" class="min-h-[20rem]"><GameOptions {net} bind:mode bind:opts bind:injpick withThemes showMode={false} dense /></div>
			</section>

			<section aria-labelledby="{uid}-room" class="flex min-w-0 flex-col gap-4">
				<h3 id="{uid}-room" class="text-sm font-extrabold">{t('play.create.sectionRoom')}</h3>
				<div data-tour="create-title" class="flex flex-col gap-1">
					<label for="{uid}-title" class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.roomTitle')}</label>
					{#if net.guest}
						<!-- Guests may only use the fixed titles (legacy: the field is replaced by a list). -->
						<Select label={t('play.create.roomTitle')} bind:value={title} options={store.allowedTitles.map((x) => ({ value: x, label: x }))} />
					{:else}
						<div class="flex items-stretch gap-2">
							<input id="{uid}-title" bind:value={title} maxlength="20" placeholder={suggestion} class="h-11 min-w-0 flex-1 kk-input rounded-xl px-3" />
							<Tooltip text={t('play.create.randomTitle')} placement="bottom">
								<button type="button" aria-label={t('play.create.randomTitle')} onclick={() => (title = store.randomRoomTitle(title || suggestion))} class="kk-btn grid size-11 shrink-0 place-items-center rounded-xl p-0">
									<Icon name="casino" size={22} />
								</button>
							</Tooltip>
						</div>
					{/if}
				</div>
				<div data-tour="create-password" class="flex flex-col gap-1">
					<label for="{uid}-pw" class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.password')}</label>
					<input id="{uid}-pw" bind:value={password} type="password" autocomplete="off" maxlength="20" disabled={clearPassword} placeholder={editing?.locked ? t('play.create.pwKeep') : ''} class="h-11 kk-input rounded-xl px-3" />
					{#if editing?.locked}<Checkbox bind:checked={clearPassword}>{t('play.create.pwClear')}</Checkbox>{/if}
				</div>
				<div data-tour="create-limit" class="flex flex-col gap-1">
					<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.limit')}</span>
					<Seekbar bind:value={limit} min={2} max={store.roomLimitMax} label={t('play.create.limit')} format={(n) => t('play.create.limitValue', { n })} tickLabel={(n) => n} />
				</div>
				<div data-tour="create-round" class="flex flex-col gap-1">
					<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.round')}</span>
					<Seekbar bind:value={round} min={1} max={10} label={t('play.create.round')} format={(n) => t('play.create.roundValue', { n })} />
				</div>
				<div data-tour="create-time" class="flex flex-col gap-1">
					<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.create.time')}</span>
					<Seekbar bind:value={time} stops={times} label={t('play.create.time')} format={(sec) => t('play.create.timeValue', { n: sec * (rule?.time ?? 1) })} tickLabel={(sec) => sec * (rule?.time ?? 1)} />
				</div>
			</section>
		</div>

		<!-- Stays visible while the form scrolls on shorter screens. -->
		<div class="sticky -bottom-4 z-10 -mx-4 -mb-4 flex items-center justify-between gap-3 border-t border-(color:--kk-line) bg-(--kk-surface) px-4 py-3">
			<p class="flex items-center gap-1.5 text-xs font-bold text-(color:--kk-muted)"><Icon name="schedule" size={18} />{t('play.create.eta', { n: etaMinutes })}</p>
			<button data-tour="create-submit" class="kk-btn kk-btn-primary min-h-12 px-8 text-base">{editing ? t('play.create.save') : t('play.create.submit')}</button>
		</div>
	</form>
</Dialog>
