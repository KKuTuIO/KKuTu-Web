<script>
	import { t } from '../i18n.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import Icon from './Icon.svelte';
	import { IMAGE_CDN, IMAGE_VERSION } from '../images.js';

	/**
	 * The ways to pass a link on, as the room invitation offers them: KakaoTalk (only where there is a message for it: `onkakao`), the device's
	 * share sheet (a desktop browser without one gets the link copied instead), and copying the link.
	 * `copied`: what is said once the link is copied (the invitation's own wording by default).
	 * @type {{link:string, title?:string, text?:string, copied?:string, onkakao?:(()=>Promise<void>)|null}}
	 */
	let { link, title = '', text = '', copied = '', onkakao = null } = $props();

	async function copyLink() {
		try {
			await navigator.clipboard.writeText(link);
			toast(copied || t('play.invite.linkCopied'), { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}
	async function shareLink() {
		if (typeof navigator.share !== 'function') return copyLink();
		try {
			await navigator.share({ title, text, url: link });
		} catch {
			/* the share sheet was dismissed */
		}
	}
	async function kakao() {
		try {
			await onkakao?.();
		} catch {
			toast(t('play.invite.kakaoFailed'), { kind: 'error' });
		}
	}
</script>

<div class="flex flex-wrap gap-2">
	{#if onkakao}<button type="button" onclick={kakao} class="kk-btn inline-flex min-h-11 items-center gap-2 px-4"><img src="{IMAGE_CDN}kkutu/kakao.png{IMAGE_VERSION}" alt="" class="size-6" />{t('play.invite.kakao')}</button>{/if}
	<button type="button" onclick={shareLink} class="kk-btn inline-flex min-h-11 items-center gap-1.5 px-4"><Icon name="share" size={20} />{t('play.invite.shareVia')}</button>
	<button type="button" onclick={copyLink} class="kk-btn inline-flex min-h-11 items-center gap-1.5 px-4"><Icon name="link" size={20} />{t('play.invite.copyLink')}</button>
</div>
