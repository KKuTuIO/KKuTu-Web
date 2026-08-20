<script>
    import { createEventDispatcher } from 'svelte';
    import { onMount } from 'svelte';
    import ProfileCreateModal from '$lib/ProfileCreateModal.svelte';

    export let profileSeed = '';
    export let profileName = '계정 설정 필요';
    export let accountLabel = '';

    const dispatch = createEventDispatcher();
    let open = false;
    let loading = false;
    let switching = '';
    let summary = null;
    let error = '';
    let profilePolicy = null;
    let profileCreateOpen = false;

    $: profiles = summary?.profiles || [];
    $: selectedProfileId = summary?.selected_profile_id || profiles[0]?.id || '';
    $: selectedProfile = profiles.find(profile => String(profile.id) === String(selectedProfileId));

    function avatarUrl(seed) {
        const value = String(seed || 'kkutuio');
        return `https://api.dicebear.com/10.x/patchwork/svg?seed=${encodeURIComponent(value)}`;
    }

    function fallbackAvatar(event) {
        event.currentTarget.onerror = null;
        event.currentTarget.src = 'https://cdn.kkutu.io/img/bi/bi_profile_main.png';
    }

    function profileTitle(profile, index = 0) {
        return profile?.nickname || profile?.id || `프로필 ${index + 1}`;
    }

    function csrfHeaders() {
        const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
        return token ? {'X-XSRF-TOKEN': decodeURIComponent(token)} : {};
    }

    async function loadSummary() {
        loading = true;
        error = '';
        try {
            const [summaryResponse, policyResponse] = await Promise.all([
                fetch('/api/account/summary', {credentials: 'same-origin'}),
                fetch('/api/account/profile-policy', {credentials: 'same-origin'})
            ]);
            if (!summaryResponse.ok || !policyResponse.ok) throw new Error('account summary request failed');
            summary = await summaryResponse.json();
            profilePolicy = await policyResponse.json();
        } catch (_) {
            error = '프로필 목록을 불러오지 못했습니다.';
        } finally {
            loading = false;
        }
    }

    function openProfileCreate() {
        open = false;
        profileCreateOpen = true;
    }

    async function profileCreated() {
        profileCreateOpen = false;
        summary = null;
        await loadSummary();
    }

    async function toggleMenu() {
        open = !open;
        if (open && !summary) await loadSummary();
    }

    function closeOnEscape(event) {
        if (event.key === 'Escape' && open) open = false;
    }

    onMount(() => {
        window.addEventListener('keydown', closeOnEscape);
        return () => window.removeEventListener('keydown', closeOnEscape);
    });

    async function selectProfile(profile) {
        if (!profile?.id || String(profile.id) === String(selectedProfileId) || switching) return;
        switching = String(profile.id);
        error = '';
        try {
            await fetch('/api/account/csrf', {credentials: 'same-origin'});
            const response = await fetch('/api/account/profile', {
                method: 'PUT',
                credentials: 'same-origin',
                headers: {'Content-Type': 'application/json', ...csrfHeaders()},
                body: JSON.stringify({profileId: profile.id})
            });
            if (!response.ok) throw new Error('profile switch failed');
            dispatch('profileChanged', {profileId: profile.id});
            window.location.reload();
        } catch (_) {
            error = '프로필을 전환하지 못했습니다. 잠시 후 다시 시도해 주세요.';
            switching = '';
        }
    }

    function logout() {
        if (window.confirm('정말로 로그아웃 할까요?')) window.location.href = 'https://kkutu.io/logout';
    }
</script>

<div class="relative">
    <button
        type="button"
        class="flex items-center rounded-full focus:outline-none focus:ring-2 focus:ring-[#55aa55] focus:ring-offset-2 dark:focus:ring-offset-gray-800"
        aria-label="프로필 및 계정 메뉴"
        aria-expanded={open}
        on:click={toggleMenu}
    >
        <img class="h-8 w-8 rounded-full" src={avatarUrl(profileSeed)} alt="현재 프로필" on:error={fallbackAvatar}/>
    </button>

    {#if open}
        <div
            role="dialog"
            tabindex="-1"
            class="absolute right-0 top-12 z-50 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-xl bg-white p-2 text-gray-900 shadow-xl ring-1 ring-black/5 dark:bg-gray-800 dark:text-white"
            on:click|stopPropagation
        >
            <div class="flex items-center gap-3 px-3 py-2">
                <img class="h-11 w-11 shrink-0 rounded-full" src={avatarUrl(profileSeed || accountLabel)} alt="계정 프로필" on:error={fallbackAvatar}/>
                <div class="min-w-0">
                    <div class="truncate font-bold">{summary?.email || accountLabel || '계정'}</div>
                    <div class="truncate text-sm text-gray-500 dark:text-gray-300">{selectedProfile?.nickname || profileName}</div>
                </div>
            </div>

            <div class="my-1 border-t border-gray-200 dark:border-gray-700"></div>
            <div class="px-3 pb-1 pt-2 text-xs font-bold uppercase tracking-wide text-gray-400">프로필</div>

            {#if loading}
                <div class="px-3 py-3 text-sm text-gray-500 dark:text-gray-300">프로필을 불러오는 중...</div>
            {:else if profiles.length}
                {#each profiles as profile, index (profile.id)}
                    <button
                        type="button"
                        class={`flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition hover:bg-gray-100 disabled:cursor-wait disabled:opacity-60 dark:hover:bg-gray-700 ${String(profile.id) === String(selectedProfileId) ? 'bg-gray-100 dark:bg-gray-700' : ''}`}
                        disabled={Boolean(switching)}
                        on:click={() => selectProfile(profile)}
                    >
                        <img class="h-9 w-9 shrink-0 rounded-full" src={avatarUrl(profile.id)} alt="{profileTitle(profile, index)}" on:error={fallbackAvatar}/>
                        <span class="min-w-0 flex-1">
                            <span class="block truncate font-semibold">{profileTitle(profile, index)}</span>
                            {#if profile.id && profile.nickname}
                                <span class="block truncate font-mono text-xs text-gray-500 dark:text-gray-300">{profile.id}</span>
                            {/if}
                        </span>
                        {#if String(profile.id) === String(selectedProfileId)}
                            <span class="material-symbols-outlined text-xl text-gray-700 dark:text-gray-100" aria-label="현재 프로필">check</span>
                        {/if}
                    </button>
                {/each}
            {:else}
                <div class="px-3 py-3 text-sm text-gray-500 dark:text-gray-300">사용 가능한 프로필이 없습니다.</div>
            {/if}

            <div class="my-1 flex items-center justify-between gap-3 border-t border-gray-200 px-3 pt-2 dark:border-gray-700">
                <button type="button" class="flex min-w-0 flex-1 items-center gap-3 rounded-lg py-2 text-left transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50 dark:hover:bg-gray-700"
                        disabled={!profilePolicy?.can_create || Boolean(switching)} on:click={openProfileCreate}>
                <span aria-hidden="true" class="material-symbols-outlined">add</span>
                <span class="font-semibold">프로필 만들기</span>
                </button>
                <span class="shrink-0 text-xs text-gray-500 dark:text-gray-400">{profiles.length}/{profilePolicy?.limit || 1}</span>
            </div>

            {#if error}
                <p class="px-3 py-2 text-xs text-red-600 dark:text-red-400">{error}</p>
            {/if}

            <div class="my-1 border-t border-gray-200 dark:border-gray-700"></div>
            <a href="https://kkutu.io/login" class="flex items-center gap-3 rounded-lg px-3 py-2 transition hover:bg-gray-100 dark:hover:bg-gray-700">
                <span class="material-symbols-outlined">account_circle</span>
                <span>계정 변경</span>
            </a>
            <a href="/account" rel="external" class="flex items-center gap-3 rounded-lg px-3 py-2 transition hover:bg-gray-100 dark:hover:bg-gray-700">
                <span class="material-symbols-outlined">manage_accounts</span>
                <span>계정 관리</span>
            </a>
            <button type="button" on:click={logout} class="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition hover:bg-gray-100 dark:hover:bg-gray-700">
                <span class="material-symbols-outlined">logout</span>
                <span>로그아웃</span>
            </button>
        </div>
    {/if}
</div>

<ProfileCreateModal open={profileCreateOpen} on:close={() => profileCreateOpen = false} on:created={profileCreated}/>
