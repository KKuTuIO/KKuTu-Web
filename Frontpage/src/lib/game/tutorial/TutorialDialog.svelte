<script>
	import { t } from '../i18n.svelte.js';
	import { LESSONS } from './lessons.js';
	import { lessonDone } from './progress.svelte.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import Rich from '../ui/Rich.svelte';
	import Dialog from '../lobby/Dialog.svelte';
	import GameIcon from '../lobby/GameIcon.svelte';
	import Icon from '../lobby/Icon.svelte';
	import Mascot from '../lobby/Mascot.svelte';
	import { TUTORIAL_PING } from './lessons.js';

	/**
	 * 학사모레미의 튜토리얼: one card per lesson (all open). A cleared one has its picture dimmed and a stamp on it, and can be played again;
	 * the first clear of each lesson pays 100 핑 to a signed-in player (the card says so until then), so a guest is asked once more before starting.
	 * `onstart(id)` starts a lesson (the page closes this dialog first); `oncommands()` opens the chat command list.
	 * @type {{store:any, open?:boolean, onstart:(id:string)=>void, oncommands:()=>void}}
	 */
	let { store, open = $bindable(false), onstart, oncommands } = $props();
	const chips = $derived(t('play.tutorial.preview.chain').split(' → '));
	// "...100(P)를 줄게!": the (P) is the game's 핑 icon (its wrapper's offset was measured: the icon's centre on the Hangul's, in the browser)
	const subtitle = $derived(t('play.tutorial.subtitle').split('(P)'));

	async function pick(id) {
		if (store.net.guest && !(await confirm({ message: t('play.tutorial.guestConfirm'), ok: t('play.tutorial.guestOk'), cancel: t('play.tutorial.guestCancel') }))) return;
		onstart(id);
	}
</script>

<Dialog bind:open title={t('play.tutorial.title')} size="lg">
	<div class="flex items-center gap-3 rounded-2xl bg-(--kk-accent-soft) px-4 py-3">
		<Mascot {store} size={56} />
		<p class="text-base leading-relaxed font-extrabold text-(color:--kk-accent-strong)">
			{#each subtitle as part, i}{#if i > 0}<span class="mx-0.5 inline-block align-[1px] leading-[0]"><GameIcon name="ping" size={22} /></span>{/if}<Rich html={part} />{/each}
		</p>
	</div>
	<ul class="mt-4 grid gap-4 sm:grid-cols-3">
		{#each LESSONS as l (l.id)}
			{@const done = lessonDone(l.id)}
			<li class="flex min-w-0 flex-col overflow-hidden rounded-2xl border-[1.5px] border-(color:--kk-line-strong) bg-(--kk-surface) shadow-[0_3px_0_var(--kk-line-strong)]">
				<!-- the picture of the lesson: a few shapes that look like what it is about -->
				<div class="relative grid aspect-[4/3] place-items-center overflow-hidden bg-(--kk-surface-2) p-4">
					<div class="grid size-full place-items-center transition-[opacity,filter] {done ? 'opacity-30 grayscale' : ''}" aria-hidden="true">
					{#if l.id === 'lobby'}
						<div class="flex w-full flex-col gap-2">
							{#each [0, 1, 2] as i}
								<div class="flex h-8 items-center gap-2 rounded-xl bg-(--kk-surface) px-2 shadow-[0_2px_0_var(--kk-line)] {i === 1 ? 'ring-2 ring-(--kk-accent)' : 'opacity-70'}">
									<span class="size-4 rounded-full bg-(--kk-line-strong)"></span><span class="h-2 flex-1 rounded-full bg-(--kk-line)"></span><Icon name="groups" size={16} class="text-(color:--kk-muted)" />
								</div>
							{/each}
						</div>
					{:else if l.id === 'chain'}
						<div class="flex flex-wrap items-center justify-center gap-1.5 text-sm font-extrabold">
							{#each chips as c, i}
								<span class="rounded-xl bg-(--kk-surface) px-2.5 py-1.5 shadow-[0_2px_0_var(--kk-line-strong)] {i === 1 ? 'text-(color:--kk-accent-strong)' : ''}">{c}</span>
								{#if i < chips.length - 1}<Icon name="arrow_forward" size={16} class="text-(color:--kk-muted)" />{/if}
							{/each}
						</div>
					{:else}
						<div class="flex w-full flex-col gap-3">
							{#each [70, 35, 55] as w}
								<div class="relative h-2 rounded-full bg-(--kk-line)"><span class="absolute inset-y-0 left-0 rounded-full bg-(--kk-accent)" style:width="{w}%"></span><span class="absolute top-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-(--kk-accent) bg-(--kk-surface)" style:left="{w}%"></span></div>
							{/each}
						</div>
					{/if}
					</div>
					{#if done}
						<!-- the stamp of a cleared lesson, pressed on the dimmed picture -->
						<div class="kk-stamp pointer-events-none absolute top-1/2 left-1/2 grid size-28 place-items-center rounded-full border-[5px] border-double" role="img" aria-label={t('play.tutorial.cleared')}>
							<span class="flex flex-col items-center leading-none"><Icon name="check" size={30} weight={700} /><b class="mt-1 text-xl font-black tracking-tight">{t('play.tutorial.cleared')}</b></span>
						</div>
					{:else}
						<span class="absolute top-2 right-2 inline-flex items-center gap-1 rounded-full bg-(--kk-surface) py-0.5 pr-2.5 pl-1.5 text-xs font-extrabold shadow-[0_2px_0_var(--kk-line-strong)]"><GameIcon name="ping" size={16} />+{TUTORIAL_PING}</span>
					{/if}
				</div>
				<strong class="block bg-(--kk-text) px-3 py-2 text-center text-sm text-(--kk-surface)">{t(`play.tutorial.lesson.${l.id}.title`)}</strong>
				<p class="flex-1 px-3 pt-3 text-xs leading-relaxed font-bold text-(color:--kk-muted)">{t(`play.tutorial.lesson.${l.id}.desc`)}</p>
				<div class="p-3"><button type="button" class="kk-btn {done ? '' : 'kk-btn-primary'} w-full min-h-10" onclick={() => pick(l.id)}>{done ? t('play.tutorial.again') : t('play.tutorial.start')}</button></div>
			</li>
		{/each}
	</ul>
	<div class="mt-4 flex justify-end">
		<button type="button" class="inline-flex h-9 items-center gap-1 rounded-lg px-3 text-sm font-bold text-(color:--kk-muted) hover:bg-(--kk-surface-2)" onclick={oncommands}><Icon name="terminal" size={18} />{t('play.tutorial.commands')}</button>
	</div>
</Dialog>

<style>
	.kk-stamp {
		--stamp: var(--kk-accent-strong);
		color: var(--stamp);
		border-color: var(--stamp);
		outline: 2px solid var(--stamp);
		outline-offset: 4px;
		mix-blend-mode: multiply; /* ink pressed into the light card */
		translate: -50% -50%;
		rotate: -14deg;
		opacity: 0.9;
	}
	/* on the dark card multiply would swallow it: the bright green of the theme, drawn as it is */
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .kk-stamp {
			--stamp: var(--kk-accent);
			mix-blend-mode: normal;
		}
	}
	:global(:root[data-theme='dark']) .kk-stamp {
		--stamp: var(--kk-accent);
		mix-blend-mode: normal;
	}
	@media (prefers-reduced-motion: no-preference) {
		.kk-stamp {
			animation: kk-stamp-in 0.45s cubic-bezier(0.2, 1.4, 0.4, 1) 0.25s both;
		}
	}
	/* pressed onto the card: big and faint first, then down with a little thud */
	@keyframes kk-stamp-in {
		from {
			opacity: 0;
			scale: 2.1;
			rotate: -34deg;
		}
		to {
			opacity: 0.9;
			scale: 1;
			rotate: -14deg;
		}
	}
</style>
