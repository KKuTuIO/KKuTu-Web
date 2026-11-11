<script>
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';
	import { nickState, NICK_MIN, NICK_MAX, FIXED_COST } from '../nick.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import Checkbox from '../ui/Checkbox.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import Dialog from './Dialog.svelte';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import NickName from './NickName.svelte';

	/**
	 * 별명 변경: a plain change (a #tag is added, once every 7 days) or 별명 고정 (no tag, nobody else can take the name; 100 ping, free for
	 * members). The game server decides; it answers with a code, 630 being the success (it shows in the toast and the name updates).
	 * @type {{open:boolean, store:any}}
	 */
	let { open = $bindable(false), store } = $props();
	const net = $derived(store.net);
	const me = $derived(store.me);
	const member = $derived(!!net.membership);
	let value = $state('');
	let fixed = $state(false);
	let sent = $state(false);
	let nextChangeAt = $state(null);
	let seenError = null;

	$effect(() => {
		if (!open) return;
		value = me?.name ?? '';
		fixed = false;
		sent = false;
		nextChangeAt = null;
		// when the next change is allowed (the account service knows; without it the game server still refuses a change that is too early)
		fetch('/api/account/nickname-policy')
			.then((r) => (r.ok ? r.json() : null))
			.then((p) => (nextChangeAt = Number(p?.next_change_at) || null))
			.catch(() => {});
	});

	const s = $derived(nickState({ value, base: me?.name ?? '', fixed, meFixed: !!me?.fixed, member, money: me?.money ?? 0, nextChangeAt }));
	const labels = $derived(net.meta.labels);

	async function submit() {
		if (!s.canSubmit || sent) return;
		const message = `${t('play.nick.confirmName', { name: s.value })}\n${t(fixed ? (member ? 'play.nick.confirmFixedMember' : 'play.nick.confirmFixed') : 'play.nick.confirmChange')}`;
		if (!(await confirm({ message }))) return;
		seenError = net.error;
		sent = true;
		store.conn.send('nickChange', { value: s.value, isFixed: fixed });
	}
	// The result arrives like an error packet: 630 is the success, anything else leaves the editor open (its toast says why).
	$effect(() => {
		const e = net.error;
		if (!sent || e === seenError) return;
		seenError = e;
		sent = false;
		if (e?.code === 630 || e?.code === '630') open = false;
	});
	const reason = $derived(s.cooling ? t('play.nick.cooldown', { date: new Date(nextChangeAt).toLocaleString() }) : s.problem ? plainText(labels[`kkutu.js.error.${s.problem === 'length' ? 600 : 601}`] ?? '') : s.poor ? t('play.nick.short') : '');
</script>

<Dialog bind:open title={t('play.nick.title')}>
	<form class="flex flex-col gap-4" onsubmit={(e) => (e.preventDefault(), submit())}>
		{#if me}
			<div class="flex items-center gap-2 rounded-xl bg-(--kk-surface-2) px-3 py-2.5">
				<span class="min-w-0 flex-1 truncate text-sm font-extrabold"><NickName user={me} showCheck /></span>
			</div>
		{/if}
		<Icon name="arrow_downward" size={22} class="-my-1.5 self-center text-(color:--kk-muted)" />
		<label class="flex flex-col gap-1.5">
			<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.nick.label')}</span>
			<!-- svelte-ignore a11y_autofocus -->
			<input bind:value maxlength={NICK_MAX} autofocus autocomplete="off" placeholder={t('play.nick.placeholder')} class="kk-input h-11 px-3 text-base font-bold" />
		</label>

		<p class="-mt-2 min-h-5 text-xs font-bold text-(color:--kk-danger)" role="alert">{reason}</p>

		<!-- the rule of fixing is the tooltip of the whole row -->
		<Tooltip text={t('play.nick.fixedDesc')} placement="top">
			<div class="-mt-1 w-fit">
				<Checkbox bind:checked={fixed}>
					<span class="flex items-center gap-2 text-sm font-extrabold">
						{t('play.nick.fixed')}
						<span class="inline-flex items-center gap-1 rounded-full bg-(--kk-surface-2) py-0.5 pr-2.5 pl-1.5 text-xs font-extrabold tabular-nums"><GameIcon name="ping" size={16} />{member ? t('play.nick.free') : FIXED_COST}</span>
					</span>
				</Checkbox>
			</div>
		</Tooltip>

		<ul class="flex flex-col gap-1.5 rounded-xl bg-(--kk-surface-2) p-3 text-xs leading-relaxed text-(color:--kk-muted)">
			<li class="flex gap-1.5"><Icon name="check_circle" size={16} class="mt-px shrink-0" />{t('play.setup.ruleChars', { min: NICK_MIN, max: NICK_MAX })}</li>
			<li class="flex gap-1.5"><Icon name="check_circle" size={16} class="mt-px shrink-0" />{t('play.setup.ruleChange')}</li>
			<li class="flex gap-1.5"><Icon name="check_circle" size={16} class="mt-px shrink-0" />{t('play.setup.ruleAbuse')}</li>
			<li class="flex gap-1.5 font-bold text-(color:--kk-danger)"><Icon name="warning" size={16} class="mt-px shrink-0" />{t('play.setup.warning')}</li>
		</ul>

		<div class="flex justify-end gap-2">
			<button type="button" class="kk-btn min-h-12 px-6" onclick={() => (open = false)}>{t('play.confirm.cancel')}</button>
			<button class="kk-btn kk-btn-primary inline-flex min-h-12 items-center gap-2.5 px-6" disabled={!s.canSubmit || sent}>
				{t('play.nick.submit')}
				{#if s.cost}<span class="inline-flex items-center gap-1 rounded-full bg-black/20 py-0.5 pr-3 pl-1.5 text-base font-extrabold tabular-nums"><GameIcon name="ping" size={22} />{s.cost}</span>{/if}
			</button>
		</div>
	</form>
</Dialog>
