<script>
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';
	import { confirm, prompt } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { wardrobe, addOutfit, overwriteOutfit, renameOutfit, removeOutfit, moveOutfit } from '../wardrobe.svelte.js';
	import { MAX_PRESETS, MAX_NAME, canWear, currentEquip, planApply, presetItems } from '../wardrobe.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';

	/**
	 * 옷장 (the legacy wardrobe): named outfits saved from what I wear now. Put one on (it toggles the differing slots one by one, as the
	 * legacy client does), overwrite it with what I wear now, rename, delete, and re-order (drag the grip, or the arrows).
	 * @type {{open:boolean, store:any, nameOf:(id:string)=>string}}
	 */
	let { open = $bindable(false), store, nameOf } = $props();
	const parts = $derived(store.net.meta.moremiParts);
	const equip = $derived(store.me?.equip ?? {});
	const full = $derived(wardrobe.list.length >= MAX_PRESETS);
	const names = (p) => presetItems(p).map(nameOf);
	const fail = (code) => toast(plainText(store.net.meta.labels[`kkutu.js.error.${code}`] ?? ''), { kind: 'error' });

	function apply(p) {
		const mine = store.net.users[store.net.id];
		// legacy: nothing may be changed in the middle of a game I am playing (error 438)
		if (store.currentRoom?.playing && mine?.game.form !== 'S' && !mine?.game.practice) return fail(438);
		if (!canWear(p, store.shop, store.net.box)) return void toast(t('play.wardrobe.invalid'), { kind: 'error' });
		for (const change of planApply(currentEquip(equip), p.equip)) store.conn.send('consume', change);
	}
	async function save() {
		if (full) return;
		const name = await prompt({ message: t('play.wardrobe.enterName'), max: MAX_NAME });
		if (name?.trim()) addOutfit(name, currentEquip(equip));
	}
	async function overwrite(p) {
		if (await confirm({ message: t('play.wardrobe.confirmOverwrite', { name: p.name }) })) overwriteOutfit(p.id, currentEquip(equip));
	}
	async function rename(p) {
		const name = await prompt({ message: t('play.wardrobe.enterNewName'), value: p.name, max: MAX_NAME });
		if (name?.trim()) renameOutfit(p.id, name);
	}
	async function remove(p) {
		if (await confirm({ message: t('play.wardrobe.confirmRemove', { name: p.name }) })) removeOutfit(p.id);
	}

	let dragging = $state(null);
	let over = $state(null);
	function drop(e, target) {
		e.preventDefault();
		const to = wardrobe.list.findIndex((x) => x.id === target.id);
		if (dragging && to >= 0) moveOutfit(dragging, to);
		dragging = over = null;
	}
	const btn = 'grid size-8 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) disabled:opacity-35 disabled:hover:bg-transparent';
</script>

<Dialog bind:open title={t('play.wardrobe.title', { count: wardrobe.list.length, max: MAX_PRESETS })}>
	<div class="flex flex-col gap-3">
		{#if !wardrobe.list.length}
			<p class="rounded-xl bg-(--kk-surface-2) px-3 py-8 text-center text-sm text-(color:--kk-muted)">{t('play.wardrobe.empty')}</p>
		{:else}
			<ul class="flex max-h-[min(24rem,60dvh)] flex-col gap-2 overflow-y-auto pr-1">
				{#each wardrobe.list as p, i (p.id)}
					{@const list = names(p)}
					{@const ok = canWear(p, store.shop, store.net.box)}
					{#snippet tip()}<span class="block max-w-64 text-xs">{list.join(', ') || t('play.wardrobe.noItems')}</span>{/snippet}
					<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
					<li
						draggable="true"
						ondragstart={(e) => {
							dragging = p.id;
							e.dataTransfer.effectAllowed = 'move';
							e.dataTransfer.setData('text/plain', p.id);
						}}
						ondragend={() => (dragging = over = null)}
						ondragover={(e) => {
							e.preventDefault();
							over = p.id;
						}}
						ondrop={(e) => drop(e, p)}
						class="flex items-center gap-2 rounded-xl border-[1.5px] px-2 py-2 {over === p.id && dragging !== p.id ? 'border-(color:--kk-accent)' : 'border-(color:--kk-line)'} {dragging === p.id ? 'opacity-50' : ''}"
					>
						<span class="grid size-8 shrink-0 cursor-grab place-items-center text-(color:--kk-muted)" aria-hidden="true"><Icon name="drag_indicator" size={20} /></span>
						<Moremi equip={p.equip} shop={store.shop} {parts} size={52} label={p.name} animated={false} frame={false} />
						<Tooltip content={tip} placement="top">
							<div class="min-w-0 flex-1">
								<p class="truncate text-sm font-extrabold">#{i + 1} {p.name}</p>
								<p class="text-xs leading-snug text-(color:--kk-muted)">{t('play.wardrobe.itemCount', { count: list.length })}{#if !ok} · <span class="font-bold whitespace-nowrap text-red-500">{t('play.wardrobe.unwearable')}</span>{/if}</p>
							</div>
						</Tooltip>
						<div class="flex shrink-0 items-center">
							<Tooltip text={t('play.wardrobe.moveUp')}><button type="button" aria-label={t('play.wardrobe.moveUp')} disabled={i === 0} onclick={() => moveOutfit(p.id, i - 1)} class={btn}><Icon name="keyboard_arrow_up" size={20} /></button></Tooltip>
							<Tooltip text={t('play.wardrobe.moveDown')}><button type="button" aria-label={t('play.wardrobe.moveDown')} disabled={i === wardrobe.list.length - 1} onclick={() => moveOutfit(p.id, i + 1)} class={btn}><Icon name="keyboard_arrow_down" size={20} /></button></Tooltip>
							<Tooltip text={t('play.wardrobe.overwrite')}><button type="button" aria-label={t('play.wardrobe.overwrite')} onclick={() => overwrite(p)} class={btn}><Icon name="save" size={18} /></button></Tooltip>
							<Tooltip text={t('play.wardrobe.rename')}><button type="button" aria-label={t('play.wardrobe.rename')} onclick={() => rename(p)} class={btn}><Icon name="match_case" size={18} /></button></Tooltip>
							<Tooltip text={t('play.wardrobe.remove')}><button type="button" aria-label={t('play.wardrobe.remove')} onclick={() => remove(p)} class={btn}><Icon name="close" size={18} /></button></Tooltip>
							<button type="button" onclick={() => apply(p)} class="kk-btn kk-btn-primary ml-1 min-h-9 px-3 text-sm">{t('play.wardrobe.apply')}</button>
						</div>
					</li>
				{/each}
			</ul>
		{/if}
		<div class="flex justify-end">
			<button type="button" class="kk-btn kk-btn-primary inline-flex items-center gap-1.5" disabled={full} onclick={save}><Icon name="add" size={18} />{t('play.wardrobe.save')}</button>
		</div>
	</div>
</Dialog>
