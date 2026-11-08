<script>
	import { t } from '../i18n.svelte.js';
	import { endingSoon, eventPhase, safeAccent } from '../news.js';
	import { whenText } from '../newsFormat.js';

	/**
	 * An entry of the page's list rail: the name, the state and how long it goes, with the event's colour as a bar. No picture: the overview
	 * cards already showed it, and the rail is there to move between entries quickly.
	 * @type {{event:any, now:number, selected?:boolean, fresh?:boolean, onpick:(id:string)=>void}}
	 */
	let { event, now, selected = false, fresh = false, onpick } = $props();
	const phase = $derived(eventPhase(event, now));
	const look = $derived(event.presentation ?? {});
	const accent = $derived(safeAccent(look.accent));
	const soon = $derived(endingSoon(event, now));
	const PILL = { ongoing: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)', upcoming: 'bg-(--kk-warn-soft) text-(color:--kk-warn)', ended: 'bg-(--kk-surface-2) text-(color:--kk-muted)' };
</script>

<button
	type="button"
	aria-pressed={selected}
	aria-current={selected ? 'true' : undefined}
	onclick={() => onpick(event.id)}
	style:--ac={accent || undefined}
	class="relative flex w-full min-w-0 items-center gap-2.5 rounded-xl border-[1.5px] p-1.5 text-left transition-colors {selected ? 'border-(--ac,var(--kk-accent)) bg-(--kk-accent-soft)' : 'border-transparent hover:bg-(--kk-surface-2)'} {phase === 'ended' && !selected ? 'opacity-70' : ''}"
>
	<span class="h-9 w-1 shrink-0 rounded-full bg-(--ac,var(--kk-accent))" class:opacity-40={phase === 'ended'} aria-hidden="true"></span>
	<span class="flex min-w-0 flex-1 flex-col gap-0.5">
		<b class="line-clamp-2 text-[13px] leading-snug font-extrabold">{event.name}</b>
		<span class="flex min-w-0 items-center gap-1.5 text-[11px] font-bold">
			<span class="shrink-0 rounded-full px-1.5 py-px font-extrabold {PILL[phase]}">{t(`play.news.phase.${phase}`)}</span>
			{#if soon}<span class="shrink-0 font-extrabold text-(color:--kk-danger)">{t('play.news.endingSoon')}</span>
			{:else}<span class="truncate text-(color:--kk-muted) tabular-nums">{whenText(event, now)}</span>{/if}
		</span>
	</span>
	{#if fresh}<span class="absolute top-1 right-1 size-2 rounded-full bg-(--kk-danger)" aria-label={t('play.news.fresh')}></span>{/if}
</button>
