<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { academyApi, friendlyError } from './api.js';
	import Select from '../ui/Select.svelte';
	import Icon from '../lobby/Icon.svelte';
	import EmptyState from '../lobby/EmptyState.svelte';
	import { toast } from '../ui/toast.svelte.js';

	let lang = $state('ko');
	let word = $state('');
	let reason = $state(t('play.academy.admin.defaultReason'));
	let bulkWords = $state('');
	let bulkReason = $state(t('play.academy.admin.bulkReason'));
	let page = $state(0);
	let response = $state({ items: [], hasNext: false, page: 0, size: 50 });
	let loading = $state(false);
	let error = $state('');
	let authorized = $state(null);
	let confirmWord = $state('');

	const langs = $derived(['ko', 'en'].map((v) => ({ value: v, label: t(`play.academy.rule.${v}`) })));
	const parse = (v) => v.split(/[\s,]+/).map((x) => x.trim()).filter(Boolean);

	async function load(target = 0) {
		loading = true;
		error = '';
		try {
			response = await academyApi.adminPublished(lang, target, 50);
			page = response.page;
			authorized = true;
		} catch (cause) {
			authorized = false;
			error = friendlyError(cause);
		} finally {
			loading = false;
		}
	}
	/** Run one change, say what happened, reload the list. */
	async function act(fn, done, reload = 0) {
		loading = true;
		error = '';
		try {
			toast(done(await fn()), { kind: 'success' });
			await load(reload);
		} catch (cause) {
			error = friendlyError(cause);
			loading = false;
		}
	}
	const publish = (e) => (e.preventDefault(), word.trim() && act(() => academyApi.adminPublish(lang, word.trim(), reason.trim() || t('play.academy.admin.defaultReason')), () => t('play.academy.admin.published', { word: word.trim() }), 0).then(() => (word = '')));
	function bulk(e) {
		e.preventDefault();
		const list = [...new Set(parse(bulkWords))];
		if (!list.length) return;
		if (list.length > 1000) return void (error = t('play.academy.admin.bulkMax'));
		act(() => academyApi.adminBulkPublish(lang, list, bulkReason.trim() || t('play.academy.admin.bulkReason')), (r) => t('play.academy.admin.bulkDone', { n: r.affected })).then(() => (bulkWords = ''));
	}
	function unpublish(target) {
		if (confirmWord !== target) return void (confirmWord = target);
		confirmWord = '';
		act(() => academyApi.adminUnpublish(lang, target), (r) => t(r.success ? 'play.academy.admin.unpublished' : 'play.academy.admin.alreadyUnpublished', { word: target }), page);
	}
	const refresh = () => act(() => academyApi.adminRefresh(lang), () => t('play.academy.admin.refreshed', { lang: lang.toUpperCase() }), page);
	function changeLang() {
		page = 0;
		response = { items: [], hasNext: false, page: 0, size: 50 };
		load(0);
	}
	onMount(() => load(0));
</script>

<div class="flex min-w-0 flex-col gap-4">
	<div class="flex flex-wrap items-center justify-between gap-3">
		<div><h2 class="text-xl font-black">{t('play.academy.admin.title')}</h2><p class="text-sm text-(color:--kk-muted)">{t('play.academy.admin.basic')}</p></div>
		<div class="flex items-center gap-2">
			<div class="w-32"><Select label={t('play.academy.rule.lang')} bind:value={lang} options={langs} onchange={changeLang} /></div>
			<button type="button" onclick={refresh} disabled={loading || authorized === false} class="kk-btn inline-flex min-h-11 items-center gap-1.5 text-sm"><Icon name="sync" size={18} />{t('play.academy.admin.refresh')}</button>
		</div>
	</div>

	{#if error}<p class="rounded-xl bg-(--kk-danger-soft) px-4 py-3 text-sm font-bold break-words text-(color:--kk-danger)">{error}</p>{/if}

	{#if authorized === false}
		<div class="kk-card grid min-h-60 place-items-center p-8 text-center"><div class="flex flex-col items-center gap-2"><Icon name="lock" size={44} class="text-(color:--kk-muted)" /><h3 class="text-lg font-black">{t('play.academy.admin.denied')}</h3><p class="text-sm text-(color:--kk-muted)">{t('play.academy.admin.auth')}</p></div></div>
	{:else}
		<div class="grid gap-4 lg:grid-cols-2">
			<form onsubmit={publish} class="kk-card flex flex-col gap-3 p-4">
				<h3 class="flex items-center gap-2 font-extrabold"><Icon name="verified" size={20} fill />{t('play.academy.admin.one')}</h3>
				<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.admin.word')}</span><input bind:value={word} maxlength="128" placeholder={t('play.academy.admin.wordPlaceholder')} class="h-11 kk-input px-3 text-lg font-black" /></label>
				<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.admin.reason')}</span><input bind:value={reason} maxlength="200" class="h-11 kk-input px-3" /></label>
				<button class="kk-btn kk-btn-primary inline-flex min-h-11 items-center justify-center gap-1.5" disabled={loading || !word.trim()}><Icon name="add" size={20} />{t('play.academy.admin.add')}</button>
			</form>
			<form onsubmit={bulk} class="kk-card flex flex-col gap-3 p-4">
				<h3 class="flex items-center gap-2 font-extrabold"><Icon name="playlist_add" size={20} />{t('play.academy.admin.bulk')}</h3>
				<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.admin.list.words')} <span class="font-medium">· {t('play.academy.admin.bulkHint')}</span></span><textarea bind:value={bulkWords} rows="4" class="kk-input resize-y p-3 font-mono text-sm"></textarea><span class="text-right text-xs text-(color:--kk-muted)">{parse(bulkWords).length}/1000</span></label>
				<label class="flex flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.academy.admin.reason')}</span><input bind:value={bulkReason} maxlength="200" class="h-11 kk-input px-3" /></label>
				<button class="kk-btn kk-btn-primary inline-flex min-h-11 items-center justify-center gap-1.5" disabled={loading || !parse(bulkWords).length}><Icon name="add" size={20} />{t('play.academy.admin.add')}</button>
			</form>
		</div>

		<section class="kk-card overflow-hidden">
			<div class="flex items-center justify-between gap-2 border-b border-(color:--kk-line) px-4 py-3"><h3 class="font-extrabold">{t('play.academy.admin.list')}</h3><span class="text-sm font-bold text-(color:--kk-muted)">{t('play.academy.admin.page', { n: page + 1 })}</span></div>
			{#if !response.items.length}
				<div class="p-6"><EmptyState compact size={110} title={loading ? t('play.academy.loading') : t('play.academy.admin.none')} /></div>
			{:else}
				<div class="overflow-x-auto">
					<table class="w-full min-w-[40rem] text-left text-sm">
						<thead class="bg-(--kk-surface-2) text-xs text-(color:--kk-muted)"><tr><th class="px-4 py-2">{t('play.academy.admin.word')}</th><th class="px-4 py-2">{t('play.academy.admin.reason')}</th><th class="px-4 py-2">{t('play.academy.admin.by')}</th><th class="px-4 py-2">{t('play.academy.admin.at')}</th><th class="px-4 py-2"></th></tr></thead>
						<tbody>
							{#each response.items as item (item.word)}
								<tr class="border-t border-(color:--kk-line)">
									<td class="px-4 py-2 text-base font-black">{item.word}</td>
									<td class="max-w-xs truncate px-4 py-2 text-(color:--kk-muted)">{item.reason}</td>
									<td class="px-4 py-2 font-mono text-xs text-(color:--kk-muted)">{item.createdBy}</td>
									<td class="px-4 py-2 text-xs text-(color:--kk-muted)">{new Date(item.createdAt).toLocaleString()}</td>
									<td class="px-4 py-2 text-right"><button type="button" onclick={() => unpublish(item.word)} class="rounded-lg px-3 py-1.5 text-xs font-extrabold {confirmWord === item.word ? 'bg-(--kk-danger) text-white' : 'bg-(--kk-danger-soft) text-(color:--kk-danger)'}">{t(confirmWord === item.word ? 'play.academy.admin.confirmUnpublish' : 'play.academy.admin.unpublish')}</button></td>
								</tr>
							{/each}
						</tbody>
					</table>
				</div>
				<div class="flex items-center justify-center gap-3 border-t border-(color:--kk-line) p-3">
					<button type="button" class="kk-btn text-sm" disabled={page === 0 || loading} onclick={() => load(page - 1)}>{t('play.academy.admin.prev')}</button>
					<button type="button" class="kk-btn text-sm" disabled={!response.hasNext || loading} onclick={() => load(page + 1)}>{t('play.academy.admin.next')}</button>
				</div>
			{/if}
		</section>

		<section class="rounded-2xl bg-(--kk-warn-soft) p-4 text-(color:--kk-warn)">
			<p class="flex items-center gap-2 font-extrabold"><Icon name="warning" size={20} fill />{t('play.academy.admin.caution')}</p>
			<ul class="mt-1 list-inside list-disc text-xs leading-5"><li>{t('play.academy.admin.effect')}</li><li>{t('play.academy.admin.notice1')}</li><li>{t('play.academy.admin.cautionAuto')}</li><li>{t('play.academy.admin.cautionCache')}</li></ul>
		</section>
	{/if}
</div>
