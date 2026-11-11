<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import ShareButtons from './ShareButtons.svelte';

	/**
	 * A link to pass on: shown (to read, or select by hand) with the invitation's share buttons.
	 * @type {{open:boolean, title:string, link:string, shareTitle?:string, shareText?:string, copied?:string, onkakao?:(()=>Promise<void>)|null}}
	 */
	let { open = $bindable(false), title, link, shareTitle = '', shareText = '', copied = '', onkakao = null } = $props();
</script>

<Dialog bind:open {title}>
	<div class="flex flex-col gap-4">
		<input readonly value={link} aria-label={title} onfocus={(e) => e.currentTarget.select()} class="kk-input h-11 w-full min-w-0 px-3 text-sm" />
		<ShareButtons {link} title={shareTitle} text={shareText} {copied} {onkakao} />
	</div>
</Dialog>
