<script>
    import {onMount} from 'svelte';
    import ToastStack from '$lib/ToastStack.svelte';

    let summary = null;
    let selectedProfile = '';
    let sanctions = [];
    let loading = true;
    let toasts = [];
    let toastId = 0;
    const toastTimers = new Map();

    const effectNames = {
        WARNING: '경고',
        CHAT_RESTRICTION: '채팅 제한',
        GAME_RESTRICTION: '게임 이용 제한',
        GUEST_ACCESS_RESTRICTION: '비회원 이용 제한',
        IP_RESTRICTION: 'IP 제한',
        NICKNAME_RESET: '닉네임 초기화',
        NICKNAME_CHANGE_RESTRICTION: '닉네임 변경 제한',
        RESOURCE_ADJUSTMENT: '경험치·핑 회수',
        EXTEND_RELATED_RESTRICTION: '연관 제재 연장',
        RELATED_SERVICE_RESTRICTION: '관련 서비스 제한'
    };

    function notify(message, kind = 'success') {
        const id = ++toastId;
        toasts = [...toasts, {id, message, kind}];
        toastTimers.set(id, setTimeout(() => dismissToast(id), 4200));
    }

    function dismissToast(id) {
        const timer = toastTimers.get(id);
        if (timer) clearTimeout(timer);
        toastTimers.delete(id);
        toasts = toasts.filter(toast => toast.id !== id);
    }

    function avatarUrl() {
        const seed = summary?.uuid || summary?.sub || summary?.legacy_user_id || 'kkutu';
        return `https://api.dicebear.com/9.x/personas/svg?seed=${encodeURIComponent(seed)}`;
    }

    function fallbackAvatar(event) {
        event.currentTarget.onerror = null;
        event.currentTarget.src = 'https://cdn.kkutu.io/img/bi/bi_profile_main.png';
    }

    async function loadSummary() {
        const response = await fetch('/api/account/summary');
        if (response.status === 401) {
            location.href = '/login';
            return false;
        }
        if (!response.ok) throw new Error();
        summary = await response.json();
        selectedProfile = summary.selected_profile_id || summary.profiles?.[0]?.id || '';
        return true;
    }

    async function loadSanctions() {
        if (!selectedProfile) {
            sanctions = [];
            return;
        }
        loading = true;
        try {
            const response = await fetch(`/api/account/sanctions?profile_id=${encodeURIComponent(selectedProfile)}`);
            if (!response.ok) throw new Error();
            sanctions = await response.json();
        } catch (_) {
            notify('제재 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'error');
        } finally {
            loading = false;
        }
    }

    async function load() {
        loading = true;
        try {
            if (await loadSummary()) await loadSanctions();
        } catch (_) {
            notify('계정 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'error');
            loading = false;
        }
    }

    function effectLabel(effect) {
        const name = effectNames[effect.type] || effect.type;
        if (effect.permanent) return `${name} (영구)`;
        return effect.endsAt ? `${name} (~${new Date(effect.endsAt).toLocaleString()})` : name;
    }

    function active(caseItem) {
        const now = Date.now();
        return !caseItem.revokedAt && (caseItem.effects || []).some(effect =>
            effect.status === 'APPLIED' && new Date(effect.startsAt).getTime() <= now &&
            (effect.permanent || (effect.endsAt && new Date(effect.endsAt).getTime() > now))
        );
    }

    function changeProfile() {
        loadSanctions();
    }

    onMount(load);
</script>

<svelte:head><title>끄투리오 - 제재 내역</title></svelte:head>

<main class="min-h-screen bg-gray-100 px-4 pb-16 pt-24 text-slate-900 dark:bg-gray-900 dark:text-gray-100 sm:px-6">
    <div class="mx-auto max-w-3xl">
        <a class="inline-flex items-center gap-2 rounded-xl px-2 py-2 text-lg font-bold transition hover:bg-slate-200 dark:hover:bg-gray-800" href="/account"><span class="material-symbols-outlined">arrow_back</span>계정 관리</a>

        <header class="mt-7"><p class="text-sm font-bold text-[#438c43]">계정 및 보안</p><h1 class="mt-1 text-3xl font-bold tracking-tight">제재 내역</h1><p class="mt-3 max-w-2xl text-slate-600 dark:text-gray-300">선택한 게임 프로필의 최근 1년 내 제재 내역을 최대 10건까지 확인할 수 있습니다.</p></header>

        {#if summary}
            <section class="mt-7 flex items-center gap-4 rounded-2xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <img class="h-16 w-16 shrink-0 rounded-2xl bg-slate-100" src={avatarUrl()} alt="계정 아바타" on:error={fallbackAvatar}/>
                <div class="min-w-0 flex-1"><h2 class="truncate text-xl font-bold">{summary.nickname || '별명 설정 필요'}</h2><p class="mt-1 truncate text-sm text-gray-500 dark:text-gray-300">{summary.legacy_user_id}</p></div>
                <label class="sr-only" for="sanction-profile">게임 프로필</label>
                <select id="sanction-profile" class="max-w-[9rem] rounded-xl border border-gray-300 bg-white px-3 py-2 text-sm font-semibold dark:border-gray-600 dark:bg-gray-900" bind:value={selectedProfile} on:change={changeProfile}>
                    {#each summary.profiles || [] as profile}<option value={profile.id}>{profile.nickname || profile.legacy_user_id}</option>{/each}
                </select>
            </section>
        {/if}

        <section class="mt-7 overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
            {#if loading}<p class="p-8 text-center text-sm text-slate-500">제재 내역을 불러오는 중입니다.</p>
            {:else if sanctions.length === 0}<div class="p-8 text-center"><span class="material-symbols-outlined text-3xl text-slate-400">history</span><p class="mt-2 text-sm text-slate-500">최근 1년 내 제재 내역이 없습니다.</p></div>
            {:else}
                {#each sanctions as item, index}
                    <details class="group border-b border-gray-200 last:border-0 dark:border-gray-700" open={index === 0}>
                        <summary class="flex cursor-pointer list-none items-center justify-between gap-3 p-5">
                            <span class="min-w-0"><span class="block truncate font-bold">{item.inquiryId} [{item.primaryCategoryCode}] {item.summary}</span><span class="mt-1 block text-sm text-slate-500">{new Date(item.issuedAt).toLocaleDateString()}</span></span>
                            <span class="flex shrink-0 items-center gap-2">{#if item.revokedAt}<span class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600 dark:bg-gray-700 dark:text-gray-200">철회됨</span>{/if}{#if active(item)}<span class="rounded-full bg-[#e8f5e9] px-2.5 py-1 text-xs font-bold text-[#438c43]">적용 중</span>{/if}<span class="material-symbols-outlined text-slate-500 transition group-open:rotate-180">expand_more</span></span>
                        </summary>
                        <div class="border-t border-gray-100 px-5 pb-5 pt-4 dark:border-gray-700"><p class="text-sm text-slate-600 dark:text-gray-300">위반 일시: {new Date(item.occurredAt).toLocaleString()}</p><div class="mt-4 flex flex-wrap gap-2">{#each item.effects || [] as effect}<span class="rounded-full bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700 dark:bg-gray-900 dark:text-gray-200">{effectLabel(effect)}</span>{/each}</div><!-- Active cases can expose an appeal action here once that workflow exists. --></div>
                    </details>
                {/each}
            {/if}
        </section>
    </div>
</main>

<ToastStack {toasts} dismiss={dismissToast}/>

<style>
    :global(summary::-webkit-details-marker) { display: none; }
    :global(details > summary) { -webkit-tap-highlight-color: transparent; }
    :global(details::details-content) { block-size: 0; opacity: 0; overflow: clip; transition: content-visibility 180ms allow-discrete, block-size 180ms ease, opacity 150ms ease; }
    :global(details[open]::details-content) { block-size: auto; opacity: 1; }
    :global(details[open] > :not(summary)) { animation: sanction-panel-open 180ms ease-out both; }
    @keyframes sanction-panel-open { from { opacity: 0; transform: translateY(-5px); } to { opacity: 1; transform: translateY(0); } }
</style>
