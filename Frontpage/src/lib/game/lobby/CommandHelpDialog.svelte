<script>
	import { t } from '../i18n.svelte.js';
	import { HELP } from '../commands.js';
	import Dialog from './Dialog.svelte';

	/**
	 * The "/도움말" window: every chat command with its aliases and what it does.
	 * Each locale line reads "(aliases [parameter] ...) what it does"; the [parameters] are shown as chips.
	 */
	let { open = $bindable(false) } = $props();
	const rows = $derived(
		HELP.map((k) => {
			const line = t(`play.cmd.help.${k}`);
			const end = line.indexOf(') ');
			const usage = end < 0 ? '' : line.slice(1, end);
			// "/귓, /e [대상] [내용]" -> text and parameter pieces, in order
			const parts = usage.split(/\[([^\]]+)\]/).map((s, i) => ({ text: s.trim(), param: i % 2 === 1 })).filter((p) => p.text);
			return { parts, text: end < 0 ? line : line.slice(end + 2) };
		})
	);
</script>

<Dialog bind:open title={t('play.cmd.help.title')}>
	<ul class="flex max-h-[60dvh] flex-col divide-y divide-(color:--kk-line) overflow-y-auto rounded-xl border-[1.5px] border-(color:--kk-line)">
		{#each rows as r (r.text)}
			<li class="flex flex-col gap-1 px-3 py-2.5">
				<span class="flex flex-wrap items-center gap-1.5">
					{#each r.parts as p}
						{#if p.param}
							<span class="rounded-md bg-(--kk-accent-soft) px-2 py-0.5 text-xs font-bold text-(color:--kk-accent-strong)">{p.text}</span>
						{:else}
							<span class="text-sm font-extrabold">{p.text}</span>
						{/if}
					{/each}
				</span>
				<span class="text-sm text-(color:--kk-muted)">{r.text}</span>
			</li>
		{/each}
	</ul>
</Dialog>
