<script>
	import { onMount } from 'svelte';
	import { titleOf, framePath } from '../title.js';
	import { loadGoodDetails, itemName } from '../shop.js';
	import { prefs } from '../prefs.svelte.js';

	/**
	 * The 칭호 a player wears (`equip.TTL`), over or beside their name: the words of the item on a frame drawn as SVG from the item's `options.title`.
	 * Nothing when none is worn (or the players are hidden: 참여자 숨기기, with `maskable`). `size`: 'sm' for narrow places (lists, slots), 'md' for the profile.
	 * `label` replaces the words (a small tile shows a short label: its name is printed beside it).
	 * @type {{user:any, shop:Record<string,any>, size?:'sm'|'md', maskable?:boolean, label?:string, class?:string}}
	 */
	let { user, shop, size = 'sm', maskable = false, label = '', class: cls = '' } = $props();
	const id = $derived(user?.equip?.TTL);
	const title = $derived(maskable && prefs.hideUsers ? null : titleOf(id ? shop?.[id] : null));
	let details = $state(null);
	onMount(async () => (details = await loadGoodDetails()));
	const text = $derived(id ? label || itemName(details, id) : '');
	let w = $state(0);
	let h = $state(0);
	const uid = `ttl${Math.random().toString(36).slice(2, 8)}`;
	const pad = $derived(title?.ornament ? (size === 'md' ? 30 : 22) : 0);
</script>

{#if title && text}
	<span class="inline-flex max-w-full items-center {cls}" role="img" aria-label={text}>
		{#if title.ornament === 'wings'}
			<svg width={pad * 0.7} height={size === 'md' ? 26 : 18} viewBox="0 0 14 20" class="-mr-0.5 shrink-0" aria-hidden="true"><path d="M13 10 C9 9 6 6 1 1 C2 8 4 13 13 14 Z M13 12 C9 13 7 15 3 19 C8 19 11 17 13 16 Z" fill={title.to} stroke={title.edge} stroke-width="0.8" stroke-linejoin="round" /></svg>
		{/if}
		<span bind:clientWidth={w} bind:clientHeight={h} class="relative isolate inline-block min-w-0 truncate whitespace-nowrap align-middle font-extrabold {size === 'md' ? 'px-4 py-1 text-sm' : 'px-2.5 py-px text-[11px] leading-4'}" style:color={title.text} style:text-shadow={title.outline ? `0 0 2px ${title.outline}, 0 0 2px ${title.outline}, 0 1px 1px ${title.outline}` : undefined}>
			{#if w && h}
				<svg width={w} height={h} viewBox="0 0 {w} {h}" class="absolute inset-0 -z-10" aria-hidden="true">
					<defs>
						<linearGradient id={uid} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color={title.from} /><stop offset="1" stop-color={title.to} /></linearGradient>
					</defs>
					<path d={framePath(title.shape, w, h)} fill="url(#{uid})" stroke={title.edge} stroke-width="1.4" />
					<path d={framePath(title.shape, w, h)} fill="none" stroke="#fff" stroke-opacity="0.45" stroke-width="1" transform="translate(0.6 0.6) scale({(w - 1.2) / w} {(h - 1.2) / h})" />
				</svg>
			{/if}
			<span class="relative">{text}</span>
		</span>
		{#if title.ornament === 'wings'}
			<svg width={pad * 0.7} height={size === 'md' ? 26 : 18} viewBox="0 0 14 20" class="-ml-0.5 shrink-0 -scale-x-100" aria-hidden="true"><path d="M13 10 C9 9 6 6 1 1 C2 8 4 13 13 14 Z M13 12 C9 13 7 15 3 19 C8 19 11 17 13 16 Z" fill={title.to} stroke={title.edge} stroke-width="0.8" stroke-linejoin="round" /></svg>
		{:else if title.ornament === 'ring'}
			<svg width={pad * 0.85} height={size === 'md' ? 26 : 19} viewBox="0 0 22 26" class="-ml-1 shrink-0" aria-hidden="true"><path d="M7 1 H15 L11 8 Z" fill={title.to} stroke={title.edge} stroke-width="1.2" stroke-linejoin="round" /><ellipse cx="11" cy="17" rx="9" ry="6" fill="none" stroke={title.edge} stroke-width="4.4" /><ellipse cx="11" cy="17" rx="9" ry="6" fill="none" stroke={title.from} stroke-width="2" /></svg>
		{/if}
	</span>
{/if}
