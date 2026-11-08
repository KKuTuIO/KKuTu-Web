<script>
	import { onDestroy } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { confirm } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { maskBad } from '../badwords.js';

	/**
	 * A report to the operators (the legacy 신고): the reason is picked in two steps like the legacy list's groups, a section on the left and
	 * that section's reasons on the right; then what happened in the reporter's words, then a warning about false reports before it is sent.
	 * The same target cannot be reported again for five minutes, and after one report the button waits 30 seconds. Opened by
	 * `store.hooks.openReport({type: 'user', id} | {type: 'room', no})`; a report of someone in the running game I am in (or of that room)
	 * carries the game's id, so the operators can look at the game.
	 */
	let { store } = $props();
	let open = $state(false);
	let target = $state(null); // {type, id, title, gameId, roomId}
	let section = $state('01'); // the section shown
	let picked = $state(''); // the category code
	let detail = $state('');
	const labels = $derived(store.net.meta.labels);
	const L = (k) => plainText(labels[k] ?? '');
	// words that may not be in a report (they are how a false report is usually written)
	const NO_REPORT = /(중퇴|강퇴|퇴장|추방|시작|손님|토닉슨)/g;
	const SECTIONS = [
		['01', ['01', '02', '03']],
		['02', ['04', '05']],
		['03', ['06', '07', '08', '09', '10', '15']],
		['04', ['11', '12']],
		['05', ['13', '14', '16', '17', '18', '00']]
	];
	const SECTION_ICON = { '01': 'forum', '02': 'do_not_disturb_on', '03': 'gavel', '04': 'bug_report', '05': 'more_horiz' };
	const ICON = { '01': 'badge', '02': 'sentiment_very_dissatisfied', '03': 'campaign', '04': 'front_hand', '05': 'admin_panel_settings', '06': 'payments', '07': 'ads_click', '08': 'masks', '09': 'groups', '10': 'rule', '11': 'smart_toy', '12': 'bug_report', '13': 'key', '14': 'privacy_tip', '15': 'gavel', '16': 'report', '17': 'person_off', '18': 'sentiment_neutral', '00': 'more_horiz' };
	// "name (examples)": the name reads first, the examples after it in a quieter line
	const split = (c) => {
		const m = L(`kkutu.dialog.report.category.${c}`).match(/^(.*)\s\(([^()]*)\)\s*$/);
		return m ? { name: m[1], eg: m[2] } : { name: L(`kkutu.dialog.report.category.${c}`), eg: '' };
	};

	const recent = new Map(); // target id -> when it was reported
	let wait = $state(0); // seconds left until the next report may be sent
	let timer;
	onDestroy(() => clearInterval(timer));

	export function show(t0) {
		const net = store.net;
		const gameOf = (id) => {
			const room = net.myRoom ? net.rooms[net.myRoom] : null;
			return room && net.game?.gameId && (id === null || room.game?.seq?.includes(id)) ? { gameId: net.game.gameId, roomId: String(room.no) } : {};
		};
		if (t0.type === 'room') {
			const room = net.rooms[t0.no];
			if (!room) return toast(L('kkutu.js.error.466'), { kind: 'error' });
			target = { type: 'room', id: String(room.no), title: `${L('kkutu.dialog.roomInfo.reportBtn')}: ${maskBad(room.title)}(${room.no})`, ...(net.myRoom === room.no ? gameOf(null) : {}) };
		} else {
			const user = net.users[t0.id];
			target = { type: 'user', id: t0.id, title: `${L('kkutu.dialog.report.title')}: ${user?.name ?? t0.name ?? t0.id}`, ...gameOf(t0.id) };
		}
		section = '01';
		picked = '';
		detail = '';
		open = true;
	}
	const error = (msg) => toast(msg, { kind: 'error' });
	const sectionOf = (c) => SECTIONS.find(([, cs]) => cs.includes(c))?.[0];
	const cats = $derived(SECTIONS.find(([s]) => s === section)?.[1] ?? []);

	async function send() {
		if (wait) return;
		if (Date.now() - (recent.get(target.id) ?? 0) < 5 * 60 * 1000) return error(L('kkutu.dialog.report.recentlyReported'));
		if (!picked) return error(L('kkutu.dialog.report.selectReason'));
		const banned = detail.match(NO_REPORT);
		if (banned) return error(L('kkutu.dialog.report.bannedWords').replace('${matched}', banned[0]));
		if (!(await confirm({ message: L('kkutu.dialog.report.falseReportWarning') }))) return;
		store.conn.send(target.type === 'room' ? 'reportRoom' : 'report', {
			id: target.id,
			reason: split(picked).name,
			categoryCode: picked,
			detail: detail.trim(),
			gameId: target.gameId,
			roomId: target.roomId
		});
		recent.set(target.id, Date.now());
		open = false; // the server answers with 652 (received) or a refusal (651 too soon, 653 same target again)
		wait = 30;
		clearInterval(timer);
		timer = setInterval(() => {
			wait -= 1;
			if (wait <= 0) clearInterval(timer);
		}, 1000);
	}
</script>

<Dialog bind:open title={target?.title ?? ''} size="lg">
	<div class="flex flex-col gap-4">
		<p class="text-sm font-bold text-(color:--kk-muted)">{L('kkutu.dialog.report.category')}</p>
		<div class="grid min-h-[21rem] gap-4 sm:grid-cols-[14rem_1fr]">
			<div role="tablist" aria-orientation="vertical" aria-label={L('kkutu.dialog.report.category')} class="flex flex-col gap-1.5">
				{#each SECTIONS as [s] (s)}
					<button type="button" role="tab" aria-selected={section === s} onclick={() => (section = s)} class="flex min-h-12 items-center gap-2.5 rounded-xl px-3 text-left text-sm font-bold {section === s ? 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong) shadow-[0_2px_0_var(--kk-line)]' : 'hover:bg-(--kk-surface-2)'}">
						<Icon name={SECTION_ICON[s]} size={22} fill={section === s} />
						<span class="min-w-0 flex-1 leading-snug">{L(`kkutu.dialog.report.section.${s}`)}</span>
						{#if sectionOf(picked) === s}<Icon name="check_circle" size={20} fill class="shrink-0 text-(color:--kk-fill)" />{/if}
					</button>
				{/each}
			</div>
			<div role="radiogroup" aria-label={L(`kkutu.dialog.report.section.${section}`)} class="flex flex-col gap-1.5">
				{#each cats as c (c)}
					{@const label = split(c)}
					<Tooltip text={label.eg} placement="left">
					<button type="button" role="radio" aria-checked={picked === c} onclick={() => (picked = c)} class="flex items-center gap-3 rounded-xl border-[1.5px] px-3 py-2 text-left {picked === c ? 'border-(color:--kk-fill) bg-(--kk-accent-soft) shadow-[0_2px_0_var(--kk-fill-edge)]' : 'border-(color:--kk-line) bg-(--kk-surface) shadow-[0_2px_0_var(--kk-line)] hover:border-(color:--kk-accent)'}">
						<Icon name={ICON[c]} size={24} class="shrink-0 text-(color:--kk-accent-strong)" />
						<span class="min-w-0 flex-1 text-sm leading-snug"><b class="font-bold">{label.name}</b></span>
						<span aria-hidden="true" class="grid size-5 shrink-0 place-items-center rounded-full border-2 {picked === c ? 'border-(color:--kk-fill) bg-(--kk-fill) text-white' : 'border-(color:--kk-line-strong)'}">{#if picked === c}<Icon name="check" size={14} />{/if}</span>
					</button>
					</Tooltip>
				{/each}
			</div>
		</div>
		<label class="flex flex-col gap-2">
			<span class="text-sm font-bold">{L('kkutu.dialog.report.tail')}</span>
			<textarea bind:value={detail} placeholder={L('kkutu.dialog.report.placeholder')} maxlength="250" class="kk-input h-24 w-full resize-none p-3 text-sm leading-relaxed"></textarea>
		</label>
		<div class="flex justify-center">
			<button type="button" disabled={wait > 0} onclick={send} class="kk-btn kk-btn-primary min-h-12 min-w-56 text-base">{wait > 0 ? t('play.create.timeValue', { n: wait }) : L('kkutu.dialog.report.ok')}</button>
		</div>
	</div>
</Dialog>
