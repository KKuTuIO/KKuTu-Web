<script>
	import { ToastHost } from '@kkutuio/ui';
	import { t } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';

	// Legacy toast icon set (cdn img/play/toast). Errors and warnings share the alert icon.
	const iconFor = { info: 'info', success: 'info', warn: 'alert', error: 'alert' };
</script>

<ToastHost closeLabel={t('play.toast.close')}>
	{#snippet icon(toast)}
		<img src={cdnImg(`play/toast/${toast.icon ?? iconFor[toast.kind] ?? 'info'}.png`)} alt="" aria-hidden="true" draggable="false" class="h-5 w-auto shrink-0" />
	{/snippet}
</ToastHost>
