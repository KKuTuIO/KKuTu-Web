<script>
	import { t, i18n } from '../i18n.svelte.js';
	import { LOBBY_BGMS, SOUND_SETS } from '../audioCatalog.js';
	import { audio, setLobbyBgm, setMuted, setSoundSet, setVolume } from '../audio.svelte.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Seekbar from '../ui/Seekbar.svelte';
	import Select from '../ui/Select.svelte';
	import Checkbox from '../ui/Checkbox.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import Switch from '../ui/Switch.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { DEFAULTS, prefs, setPref } from '../prefs.svelte.js';
	import { blockedList } from '../blocks.svelte.js';
	import { MAX_NOTE, notes, setNote } from '../notes.svelte.js';
	import { confirm, prompt } from '../ui/confirm.svelte.js';
	import { DEFAULT_AUDIO } from '../audioCatalog.js';
	import { confirmLeave } from '../leaveGuard.svelte.js';

	let { store, open = $bindable(false), start = 'sound' } = $props();
	let tab = $state('sound');
	const tabs = $derived(['sound', 'chat', 'game', 'block', 'etc'].map((id) => ({ id, label: t(`play.settings.tab.${id}`) })));

	// Changes are collected here and only applied by 저장; closing the dialog drops them.
	const AUDIO_KEYS = ['vb', 've', 'vu', 'muted', 'set', 'lobby'];
	const FORM_PREFS = Object.keys(DEFAULTS).filter((k) => k !== 'userSort');
	const snapshot = () => ({ ...Object.fromEntries(FORM_PREFS.map((k) => [k, prefs[k]])), ...Object.fromEntries(AUDIO_KEYS.map((k) => [k, audio[k]])) });
	let draft = $state(snapshot());
	$effect(() => {
		if (open) {
			draft = snapshot();
			tab = start;
		}
	});
	const reset = () => (draft = { ...Object.fromEntries(FORM_PREFS.map((k) => [k, DEFAULTS[k]])), ...DEFAULT_AUDIO });
	function save() {
		for (const k of FORM_PREFS) if (draft[k] !== prefs[k]) setPref(k, draft[k]);
		if (draft.hideRecords !== prefs.hideRecords) {
			store.setRecordPrivacy(draft.hideRecords);
			toast(t(draft.hideRecords ? 'play.settings.recordsEnabled' : 'play.settings.recordsDisabled'), { kind: 'info' });
		}
		for (const k of ['vb', 've', 'vu']) if (draft[k] !== audio[k]) setVolume(k, draft[k]);
		if (draft.muted !== audio.muted) setMuted(draft.muted);
		if (draft.set !== audio.set) setSoundSet(draft.set);
		if (draft.lobby !== audio.lobby) setLobbyBgm(draft.lobby);
		open = false;
	}

	// The gauges work in percent; the mixer in 0..1.
	const channels = $derived([
		{ key: 'vb', icon: 'music_note', label: t('play.settings.bgmVolume') },
		{ key: 've', icon: 'graphic_eq', label: t('play.settings.effectVolume') },
		{ key: 'vu', icon: 'record_voice_over', label: t('play.settings.uiVolume') }
	]);
	const lobbyOptions = $derived(LOBBY_BGMS.map((k) => ({ value: k, label: t(`play.audio.lobby.${k}`) })));
	const setOptions = $derived(SOUND_SETS.map((k) => ({ value: k, label: t(`play.audio.set.${k}`) })));
	const handOptions = $derived(['right', 'left'].map((k) => ({ value: k, label: t(`play.settings.hand.${k}`) })));
	const ICONS = { off: 'groups', guest: 'verified_user', friend: 'group', near: 'favorite' };
	const whisper = $derived(['off', 'guest', 'friend', 'near'].map((k) => ({ value: k, icon: ICONS[k], label: t(`play.settings.whisper.${k}`) })));

	// 채널 이동: the channels and how many people are on each, from the server list.
	let servers = $state(null);
	let serversFailed = $state(false);
	$effect(() => {
		if (!open || tab !== 'etc') return;
		serversFailed = false;
		fetch('/servers')
			.then((r) => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
			.then((o) => (servers = o))
			.catch(() => (serversFailed = true));
	});
	// 차단: blocking takes effect at once (it isn't part of the form above), and the list is this browser's.
	const blocks = $derived(blockedList());
	const showProfile = (id) => {
		store.hooks.openProfile?.(id);
		open = false;
	};
	async function unblock(b) {
		if (await confirm({ message: `${b.name}\n${t('play.block.confirmRemove')}` })) store.toggleBlock(b.kind, b.id);
	}
	async function editNote(b) {
		const v = await prompt({ message: t('play.profile.noteEdit'), value: notes[b.id] ?? '', max: MAX_NOTE });
		if (v !== null) setNote(b.id, v.trim());
	}
	async function addBlock() {
		const id = (await prompt({ message: t('play.block.addPrompt'), max: 80 }))?.trim();
		if (!id) return;
		if (id === store.net.id) return toast(t('play.block.self'), { kind: 'error' });
		if (!store.net.users[id]) return toast(t('play.block.notHere'), { kind: 'error' });
		if (!blocks.some((b) => b.id === id && b.kind === 'shut')) store.toggleBlock('shut', id);
	}
	const channelName = (i) => i18n.messages[`server.${i}`] ?? `#${i}`;
	const goTo = async (i) => (await confirmLeave()) && (location.href = `/play/${i}`);
</script>

{#snippet toggle(name, label, expl)}
	<!-- An option with an explanation shows it as a tooltip instead of a line of text. -->
	<Tooltip text={expl} placement="top"><Checkbox checked={draft[name]} onchange={(v) => (draft[name] = v)}>{label}</Checkbox></Tooltip>
{/snippet}
{#snippet heading(icon, text)}
	<h3 class="flex items-center gap-1.5 text-sm font-extrabold"><Icon name={icon} size={20} />{text}</h3>
{/snippet}

<Dialog bind:open title={t('play.settings.title')}>
	<div class="flex flex-col gap-4">
		<Tabs bind:value={tab} {tabs} idPrefix="settings" label={t('play.settings.title')} />

		<div role="tabpanel" id="settings-panel-{tab}" aria-labelledby="settings-tab-{tab}" class="flex h-[22rem] flex-col gap-5 overflow-y-auto pr-1">
			{#if tab === 'sound'}
				<div class="flex flex-col gap-2">
					<div class="flex items-center justify-between gap-3">
						{@render heading('volume_up', t('play.settings.sound'))}
						<Checkbox checked={draft.muted} onchange={(v) => (draft.muted = v)}>{t('play.settings.muteAll')}</Checkbox>
					</div>
					<div class="flex flex-col {draft.muted ? 'opacity-50' : ''}">
						{#each channels as c (c.key)}
							<div class="flex flex-col">
								<span class="flex items-center gap-1.5 text-xs font-bold text-(color:--kk-muted)"><Icon name={c.icon} size={18} />{c.label}</span>
								<Seekbar value={Math.round(draft[c.key] * 100)} min={0} max={100} step={5} tickStep={25} label={c.label} format={(v) => `${v}%`} tickLabel={(v) => v} disabled={draft.muted} onchange={(v) => (draft[c.key] = v / 100)} />
							</div>
						{/each}
					</div>
				</div>
				<div class="flex flex-col gap-3">
					<div class="flex flex-col gap-1">
						<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.settings.lobbyBgm')}</span>
						<Select label={t('play.settings.lobbyBgm')} value={draft.lobby} options={lobbyOptions} onchange={(v) => (draft.lobby = v)} />
					</div>
					<div class="flex flex-col gap-1">
						<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.settings.soundSet')}</span>
						<Select label={t('play.settings.soundSet')} value={draft.set} options={setOptions} onchange={(v) => (draft.set = v)} />
					</div>
				</div>
			{:else if tab === 'chat'}
				<div class="flex flex-col gap-1">
					{@render heading('chat_bubble', t('play.settings.whisper.title'))}
					<RadioGroup variant="cards" label={t('play.settings.whisper.title')} value={draft.whisperFrom} options={whisper} onchange={(v) => (draft.whisperFrom = v)} />
				</div>
				<Switch checked={draft.filter === 'blind'} onchange={(v) => (draft.filter = v ? 'blind' : 'off')}><span class="flex items-center gap-1.5 text-sm font-extrabold"><Icon name="shield" size={20} />{t('play.settings.filter.title')}</span></Switch>
				<div class="flex flex-col gap-1">
					{@render heading('block', t('play.settings.tab.request'))}
					{@render toggle('denyInvite', t('play.settings.denyInvite'))}
					{@render toggle('denyGuestChat', t('play.settings.denyGuestChat'))}
					{@render toggle('denyFriend', t('play.settings.denyFriend'))}
				</div>
			{:else if tab === 'game'}
				<div class="flex flex-col gap-1">
					{@render heading('sports_esports', t('play.settings.tab.game'))}
					{@render toggle('autoReady', t('play.settings.autoReady'))}
					{@render toggle('vibration', t('play.settings.vibration'))}
					{@render toggle('spectatorTag', t('play.settings.spectatorTag'))}
					<div class="mt-1 flex flex-col gap-1">
						<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.settings.hand.title')}</span>
						<Select label={t('play.settings.hand.title')} value={draft.hand} options={handOptions} onchange={(v) => (draft.hand = v)} />
					</div>
				</div>
				<div class="flex flex-col gap-1">
					{@render heading('flag', t('play.settings.tab.match'))}
					{@render toggle('hideRoom', t('play.settings.hideRoom'))}
					{@render toggle('hideUsers', t('play.settings.hideUsers'), t('play.settings.hideUsersExpl'))}
					{@render toggle('ignoreSpectators', t('play.settings.ignoreSpectators'))}
					{@render toggle('hideRecords', t('play.settings.hideRecords'), t('play.settings.hideRecordsExpl'))}
				</div>
			{:else if tab === 'block'}
				<div class="flex items-center justify-between gap-2">
					{@render heading('block', `${t('play.block.title')} (${t('play.block.count', { n: blocks.length })})`)}
					<button type="button" onclick={addBlock} class="kk-btn inline-flex min-h-10 items-center gap-1.5 px-3 text-sm"><Icon name="person_off" size={18} />{t('play.block.add')}</button>
				</div>
				{#if blocks.length}
					<ul class="flex flex-col divide-y divide-(color:--kk-line) rounded-xl border-[1.5px] border-(color:--kk-line)">
						{#each blocks as b (b.kind + b.id)}
							<li class="flex items-center gap-2 px-3 py-2">
								<Tooltip text={t(`play.block.kind.${b.kind}`)}><span class="grid size-8 shrink-0 place-items-center rounded-lg bg-(--kk-surface-2) text-(color:--kk-muted)"><Icon name={b.kind === 'shut' ? 'block' : 'voice_over_off'} size={18} /></span></Tooltip>
								<button type="button" onclick={() => showProfile(b.id)} class="min-w-0 flex-1 text-left">
									<span class="block truncate text-sm font-bold">{b.name || t('play.block.hidden')}</span>
									{#if notes[b.id]}<span class="block truncate text-xs text-(color:--kk-muted)">{notes[b.id]}</span>{/if}
								</button>
								<Tooltip text={t('play.profile.note')}><button type="button" aria-label={t('play.profile.note')} onclick={() => editNote(b)} class="grid size-9 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2)"><Icon name="edit" size={18} /></button></Tooltip>
								<Tooltip text={t('play.block.unblock')}><button type="button" aria-label="{t('play.block.unblock')}: {b.name}" onclick={() => unblock(b)} class="grid size-9 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2)"><Icon name="person_remove" size={18} /></button></Tooltip>
							</li>
						{/each}
					</ul>
				{:else}
					<p class="rounded-xl bg-(--kk-surface-2) px-3 py-6 text-center text-sm text-(color:--kk-muted)">{t('play.block.empty')}</p>
				{/if}
			{:else}
				<div class="flex flex-col gap-2">
					{@render heading('swap_horiz', t('play.settings.channel.title'))}
					{#if serversFailed}
						<p class="text-sm text-(color:--kk-muted)">{t('play.settings.channel.failed')}</p>
					{:else if servers}
						<ul class="grid grid-cols-2 gap-2">
							{#each servers.list as players, i (i)}
								{@const here = i === store.server}
								<li>
									<button type="button" disabled={here || players == null} onclick={() => goTo(i)} class="kk-btn flex min-h-12 w-full flex-col items-center justify-center px-2 text-sm leading-tight {here ? 'border-(color:--kk-accent)' : ''}">
										<span class="font-extrabold">{channelName(i)}</span>
										<span class="text-xs font-medium text-(color:--kk-muted)">{here ? t('play.settings.channel.here') : players == null ? t('play.settings.channel.closed') : t('play.settings.channel.players', { count: players, max: servers.max })}</span>
									</button>
								</li>
							{/each}
						</ul>
					{/if}
				</div>
				<div class="border-t border-(color:--kk-line) pt-3">
					<button type="button" onclick={() => toast(t('play.settings.importTbd'), { kind: 'info' })} class="kk-btn inline-flex min-h-11 items-center gap-1.5 px-4"><Icon name="upload_file" size={20} />{t('play.settings.import')}</button>
				</div>
			{/if}
		</div>

		<div class="flex items-center justify-between gap-2 border-t border-(color:--kk-line) pt-3">
			<button type="button" onclick={reset} class="kk-btn inline-flex min-h-11 items-center gap-1.5 px-4"><Icon name="restart_alt" size={20} />{t('play.settings.reset')}</button>
			<button type="button" onclick={save} class="kk-btn kk-btn-primary min-h-11 px-8">{t('play.settings.save')}</button>
		</div>
	</div>
</Dialog>
