<script>
    import {createEventDispatcher} from 'svelte';
    import AccountModal from '$lib/AccountModal.svelte';

    export let open = false;
    export let reauthVersion = 0;

    const dispatch = createEventDispatcher();
    let nickname = '';
    let policy = null;
    let loading = false;
    let submitting = false;
    let error = '';
    let wasOpen = false;
    let consumedReauthVersion = 0;

    $: if (open && !wasOpen) {
        wasOpen = true;
        nickname = '';
        error = '';
        consumedReauthVersion = reauthVersion;
        loadPolicy();
    } else if (!open) {
        wasOpen = false;
    }

    $: if (open && reauthVersion > consumedReauthVersion) {
        consumedReauthVersion = reauthVersion;
        createProfile();
    }

    function csrfHeaders() {
        const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
        return token ? {'X-XSRF-TOKEN': decodeURIComponent(token)} : {};
    }

    async function loadPolicy() {
        loading = true;
        try {
            const response = await fetch('/api/account/profile-policy', {credentials: 'same-origin'});
            if (!response.ok) throw new Error('profile policy request failed');
            policy = await response.json();
        } catch (_) {
            policy = null;
            error = '프로필 생성 정보를 불러오지 못했습니다.';
        } finally {
            loading = false;
        }
    }

    function close() {
        if (!submitting) dispatch('close');
    }

    async function createProfile() {
        const value = nickname.trim();
        if (!value) {
            error = '별명을 입력해 주세요.';
            return;
        }
        if (!policy?.can_create || submitting) return;

        submitting = true;
        error = '';
        try {
            await fetch('/api/account/csrf', {credentials: 'same-origin'});
            const response = await fetch('/api/account/profile', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {'Content-Type': 'application/json', ...csrfHeaders()},
                body: JSON.stringify({nickname: value, profileId: policy.preview_profile_id})
            });
            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                if (data.error === 'reauthentication_required') {
                    dispatch('reauthenticationRequired');
                    return;
                }
                throw new Error(data.error_description || data.error || '프로필을 만들지 못했습니다.');
            }
            const data = await response.json();
            dispatch('created', data);
            dispatch('close');
        } catch (e) {
            error = e.message || '프로필을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            submitting = false;
        }
    }
</script>

<AccountModal open={open} title="프로필 만들기" showFooter={false} on:close={close}>
    <p class="text-sm leading-6 text-gray-600 dark:text-gray-300">새 게임 프로필의 별명을 입력해 주세요.</p>
    <div class="mt-4 flex min-w-0">
        <input
            class="min-w-0 flex-1 rounded-l-xl border border-gray-300 bg-white p-3 text-slate-900 dark:border-gray-600 dark:bg-gray-900 dark:text-white"
            maxlength="15" bind:value={nickname} placeholder="새 별명" disabled={loading || submitting}
            on:keydown={(event) => event.key === 'Enter' && createProfile()}/>
        <span class="-ml-px inline-flex shrink-0 items-center rounded-r-xl border border-gray-300 bg-slate-100 px-3 font-mono text-sm font-bold text-slate-600 dark:border-gray-600 dark:bg-slate-900 dark:text-gray-300">#{policy?.nickname_tag || '00000'}</span>
    </div>
    {#if policy}
        <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">프로필 {policy.count}/{policy.limit}</p>
    {/if}
    {#if !loading && policy && !policy.can_create}
        <p class="mt-3 text-sm text-red-600 dark:text-red-400">{policy.restricted ? '이용제한된 계정은 프로필을 만들 수 없습니다.' : '프로필 생성 한도에 도달했습니다.'}</p>
    {/if}
    {#if error}
        <p class="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>
    {/if}
    <button type="button" class="mt-5 w-full rounded-xl bg-[#55aa55] px-4 py-3 font-bold text-white transition hover:bg-[#438c43] disabled:cursor-not-allowed disabled:opacity-50"
            disabled={loading || submitting || !policy?.can_create} on:click={createProfile}>
        {submitting ? '만드는 중...' : '확인'}
    </button>
</AccountModal>
