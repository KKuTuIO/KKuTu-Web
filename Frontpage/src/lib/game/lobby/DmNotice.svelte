<script>
	import { untrack } from 'svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { plainText } from '../protocol.js';
	import { prefs } from '../prefs.svelte.js';

	/**
	 * A whisper that arrives while its conversation is not on screen is said in a notice (the line itself waits in the 귓속말 tab).
	 * 귓속말 알림 off: nothing is said (the whisper is still received and kept).
	 */
	let { store } = $props();
	let seen = untrack(() => store.dm.last?.n ?? 0);
	$effect(() => {
		const last = store.dm.last;
		if (!last || last.n === seen) return;
		seen = last.n;
		if (!prefs.dmAlerts || store.dm.viewing === last.id) return;
		toast(`${last.name}: ${plainText(last.msg).slice(0, 60)}`, { kind: 'info', icon: 'mail' });
	});
</script>
