<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import Select from '../ui/Select.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';

	/** Difficulty (and, for an AI seat, team) picker. `target` is the AI seat being edited; without it this starts practice. */
	let { store, open = $bindable(false), target = null } = $props();
	let level = $state(3);
	let team = $state(0);
	const levels = $derived([0, 1, 2, 3, 4, 5].map((n) => ({ value: n, label: t(`play.ai.level.${n}`) })));
	const teams = $derived([{ value: 0, label: t('play.room.team.solo'), color: 'var(--kk-team-0)' }, ...[1, 2, 3, 4].map((n) => ({ value: n, label: 'ABCD'[n - 1], color: `var(--kk-team-${n})` }))]);

	$effect(() => {
		if (open) {
			level = target?.level ?? 3;
			team = target?.game?.team ?? 0;
		}
	});

	function submit(e) {
		e.preventDefault();
		if (target) store.setAI(target.id, level, team);
		else store.practice(level);
		open = false;
	}
</script>

<Dialog bind:open title={target ? t('play.ai.title') : t('play.ai.practiceTitle')}>
	<form onsubmit={submit} class="flex flex-col gap-4">
		<div class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.ai.level')}</span><Select label={t('play.ai.level')} bind:value={level} options={levels} /></div>
		{#if target}
			<div class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.ai.team')}</span><RadioGroup variant="chips" label={t('play.ai.team')} bind:value={team} options={teams} /></div>
		{/if}
		<button class="kk-btn kk-btn-primary min-h-12 text-base">{t('play.ai.ok')}</button>
	</form>
</Dialog>
