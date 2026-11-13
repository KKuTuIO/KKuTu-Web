<script>
	import { staffOf } from '../staffTag.js';
	import { t } from '../i18n.svelte.js';

	/** The badge of a staff team in front of a name; nothing for an ordinary name skin. `nik` is the worn `equip.NIK`. */
	let { nik } = $props();
	const staff = $derived(staffOf(nik));
</script>

{#if staff}<span class="kk-staff" style:--staff={staff.color}>{t(`play.staff.${staff.id}`)}</span>{/if}

<style>
	.kk-staff {
		display: inline-block;
		margin-right: 0.3em;
		padding: 0 0.45em;
		border: 1px solid color-mix(in srgb, var(--staff) 45%, transparent);
		border-radius: 0.5em;
		background: color-mix(in srgb, var(--staff) 14%, transparent);
		color: color-mix(in srgb, var(--staff) 72%, var(--kk-text));
		font-size: 0.82em;
		font-weight: 800;
		line-height: 1.5;
		vertical-align: 0.05em;
		white-space: nowrap;
	}
</style>
