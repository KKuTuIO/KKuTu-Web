<script>
    import {onMount} from 'svelte';
    import AccountModal from '$lib/AccountModal.svelte';
    import LoginMethodSelector from '$lib/LoginMethodSelector.svelte';
    import ToastStack from '$lib/ToastStack.svelte';

    const defaultIcon = 'https://cdn.kkutu.io/img/bi/bi_profile_main.png';
    const pendingRevokeStorageKey = 'kkutu-connected-app-revoke';
    const scopeLabels = {
        openid: '계정 식별',
        profile: '프로필 정보',
        email: '전자 메일 주소',
        account: '계정 상태',
        offline: '오프라인 접근',
        'game:kkutu': '끄투 게임 프로필'
    };

    let applications = [];
    let selected = null;
    let search = '';
    let loading = true;
    let confirmRevoke = null;
    let reauthDialogOpen = false;
    let reauthPassword = '';
    let reauthTotpCode = '';
    let reauthMfaRequired = false;
    let reauthProviderIds = [];
    let passwordEnabled = true;
    let pendingRevoke = null;
    let toasts = [];
    let toastId = 0;
    const toastTimers = new Map();

    function csrfHeaders() {
        const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
        return token ? {'X-XSRF-TOKEN': decodeURIComponent(token)} : {};
    }

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

    async function load() {
        loading = true;
        try {
            await fetch('/api/account/csrf');
            const [summaryResponse, identityResponse, applicationsResponse] = await Promise.all([
                fetch('/api/account/summary'),
                fetch('/api/account/identities'),
                fetch('/api/account/connected-applications')
            ]);
            if (summaryResponse.status === 401) {
                location.href = '/login';
                return;
            }
            if (![summaryResponse, identityResponse, applicationsResponse].every(response => response.ok)) throw new Error();
            const summary = await summaryResponse.json();
            const identities = await identityResponse.json();
            passwordEnabled = summary.password_enabled !== false;
            reauthProviderIds = identities
                .filter(identity => identity.type === 'OAUTH' && identity.provider_id)
                .map(identity => identity.provider_id);
            applications = await applicationsResponse.json();
        } catch (_) {
            notify('연결된 앱을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'error');
        } finally {
            loading = false;
        }
    }

    function logo(application) {
        return application.logo_uri || defaultIcon;
    }

    function fallbackLogo(event) {
        event.currentTarget.onerror = null;
        event.currentTarget.src = defaultIcon;
    }

    function label(scope) {
        return scopeLabels[scope] || scope;
    }

    function filteredApplications() {
        const term = search.trim().toLocaleLowerCase();
        return term ? applications.filter(application => application.client_name.toLocaleLowerCase().includes(term)) : applications;
    }

    function requestReauthentication(application) {
        pendingRevoke = application;
        sessionStorage.setItem(pendingRevokeStorageKey, application.client_id);
        reauthDialogOpen = true;
    }

    function closeReauthentication() {
        reauthDialogOpen = false;
        reauthPassword = '';
        reauthTotpCode = '';
        reauthMfaRequired = false;
        pendingRevoke = null;
        sessionStorage.removeItem(pendingRevokeStorageKey);
    }

    function providerUrl(provider) {
        return `/api/account/reauthenticate/oauth/${encodeURIComponent(provider)}?return=${encodeURIComponent('/account/apps')}`;
    }

    async function reauthenticate() {
        try {
            const response = await fetch('/api/account/reauthenticate', {
                method: 'POST',
                headers: {'Content-Type': 'application/json', ...csrfHeaders()},
                body: JSON.stringify({password: reauthPassword, totpCode: reauthTotpCode || null})
            });
            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                reauthMfaRequired = error.error === 'mfa_required';
                notify(error.error_description || '본인 확인에 실패했습니다.', 'error');
                return;
            }
            const application = pendingRevoke;
            closeReauthentication();
            if (application) await revoke(application, true);
        } catch (_) {
            notify('본인 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.', 'error');
        }
    }

    async function revoke(application, afterReauthentication = false) {
        const response = await fetch(`/api/account/connected-applications/${encodeURIComponent(application.client_id)}`, {
            method: 'DELETE', headers: csrfHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            if (error.error === 'reauthentication_required' && !afterReauthentication) {
                requestReauthentication(application);
                return;
            }
            notify(error.error_description || '연결을 해제하지 못했습니다.', 'error');
            return;
        }
        applications = applications.filter(item => item.client_id !== application.client_id);
        confirmRevoke = null;
        selected = null;
        notify(`${application.client_name} 연결을 해제했습니다.`);
    }

    async function resumePendingRevoke() {
        const clientId = sessionStorage.getItem(pendingRevokeStorageKey);
        if (!clientId) return;
        sessionStorage.removeItem(pendingRevokeStorageKey);
        const application = applications.find(item => item.client_id === clientId);
        if (application) await revoke(application, true);
    }

    onMount(async () => {
        await load();
        await resumePendingRevoke();
    });
</script>

<svelte:head><title>끄투리오 - 연결된 앱</title></svelte:head>

<main class="min-h-screen bg-gray-100 px-4 pb-16 pt-24 text-slate-900 dark:bg-gray-900 dark:text-gray-100 sm:px-6">
    <div class="mx-auto max-w-3xl">
        {#if selected}
            <button class="inline-flex items-center gap-2 rounded-xl px-2 py-2 text-lg font-bold transition hover:bg-slate-200 dark:hover:bg-gray-800"
                    on:click={() => selected = null}><span
                    class="material-symbols-outlined">arrow_back</span>{selected.client_name}</button>
            <section
                    class="mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800 sm:p-9">
                <div class="flex flex-col gap-6 sm:flex-row sm:items-center"><img
                        class="h-20 w-20 rounded-2xl border border-slate-200 bg-white object-contain p-2 dark:border-gray-600"
                        src={logo(selected)} alt="{selected.client_name} 아이콘" on:error={fallbackLogo}/>
                    <div><p class="text-sm font-bold text-[#438c43]">연결된 앱</p>
                        <h1 class="mt-1 text-3xl font-bold">{selected.client_name}</h1>
                        <!-- <p class="mt-2 text-sm text-slate-500 dark:text-gray-300"></p></div> -->
                    </div>
            </section>

            <section
                    class="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800 sm:p-8">
                <h2 class="text-xl font-bold">허용한 정보</h2>
                <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-gray-300">{selected.client_name}에서 아래 범위의 정보를
                    사용할 수 있습니다.</p>
                <div class="mt-5 space-y-3">
                    {#each selected.scopes || [] as scope}
                        <div class="flex items-center gap-3 rounded-2xl bg-slate-50 px-4 py-3 dark:bg-gray-900"><span
                                class="material-symbols-outlined text-[#438c43]">check_circle</span>
                            <div><p class="font-bold">{label(scope)}</p>
                                <p class="mt-0.5 text-xs text-slate-500 dark:text-gray-300">{scope}</p></div>
                        </div>
                    {/each}
                </div>
                <p class="mt-6 text-xs text-slate-500 dark:text-gray-300">마지막 권한
                    갱신: {selected.updated_at ? new Date(selected.updated_at).toLocaleString() : '알 수 없음'}</p>
            </section>

            <section
                    class="mt-6 rounded-2xl border border-red-100 bg-white p-6 shadow-sm dark:border-red-950 dark:bg-gray-800 sm:p-8">
                <h2 class="text-xl font-bold">연결 해제</h2>
                <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-gray-300">이 앱의 동의 시점에 발급된 접근·갱신 식별자를 모두
                    무효화합니다. 다시 사용하려면 앱에서 권한을 다시 요청해야 합니다.</p>
                <button class="mt-5 rounded-xl border border-red-300 px-4 py-2.5 text-sm font-bold text-red-700 transition hover:bg-red-50 dark:border-red-900 dark:hover:bg-red-950"
                        on:click={() => confirmRevoke = selected}>연결 해제
                </button>
            </section>
        {:else}
            <a class="inline-flex items-center gap-2 rounded-xl px-2 py-2 text-lg font-bold transition hover:bg-slate-200 dark:hover:bg-gray-800"
               href="/account"><span class="material-symbols-outlined">arrow_back</span>계정 관리</a>
            <header class="mt-7"><p class="text-sm font-bold text-[#438c43]">계정 및 보안</p>
                <h1 class="mt-1 text-3xl font-bold tracking-tight">연결된 앱</h1>
                <p class="mt-3 max-w-2xl text-slate-600 dark:text-gray-300">외부 앱이 접근하도록 허용한 개인정보를 확인하고 관리할 수 있습니다.</p>
            </header>
            <div class="mt-7 flex items-center gap-3 rounded-2xl bg-slate-200 px-4 py-3 dark:bg-gray-800"><span
                    class="material-symbols-outlined text-slate-500">search</span><input
                    class="min-w-0 flex-1 bg-transparent outline-none placeholder:text-slate-500" bind:value={search}
                    placeholder="연결된 앱 이름으로 검색" aria-label="연결된 앱 검색"/></div>
            <div class="mt-5 flex items-center justify-between gap-4"><p class="text-sm font-bold">연결된
                앱 {applications.length}개</p>
                <button class="grid h-9 w-9 place-items-center rounded-full text-slate-500 transition hover:bg-slate-200 disabled:opacity-50 dark:hover:bg-gray-800"
                        on:click={load} disabled={loading} aria-label="새로고침"><span class:animate-spin={loading}
                                                                                   class="material-symbols-outlined">refresh</span>
                </button>
            </div>
            <section
                    class="mt-4 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
                {#if loading}<p class="p-8 text-center text-sm text-slate-500">연결된 앱을 불러오는 중입니다.</p>
                {:else if filteredApplications().length === 0}<p
                        class="p-8 text-center text-sm text-slate-500">{applications.length ? '검색 결과가 없습니다.' : '연결된 앱이 없습니다.'}</p>
                {:else}
                    {#each filteredApplications() as application}
                        <button class="flex w-full items-center gap-4 border-b border-slate-100 px-5 py-5 text-left transition last:border-b-0 hover:bg-slate-50 dark:border-gray-700 dark:hover:bg-gray-700"
                                on:click={() => selected = application}><img
                                class="h-12 w-12 rounded-xl border border-slate-200 bg-white object-contain p-1.5 dark:border-gray-600"
                                src={logo(application)} alt="{application.client_name} 아이콘"
                                on:error={fallbackLogo}/><span class="min-w-0 flex-1"><span
                                class="block truncate font-bold">{application.client_name}</span><span
                                class="mt-1 block truncate text-sm text-slate-500 dark:text-gray-300">{(application.scopes || []).map(label).join(' · ')}</span></span><span
                                class="material-symbols-outlined text-slate-500">chevron_right</span></button>
                    {/each}
                {/if}
            </section>
        {/if}
    </div>
</main>

<AccountModal open={Boolean(confirmRevoke)} title="연결을 해제할까요?" showFooter={false} on:close={() => confirmRevoke = null}>
    <p class="text-sm leading-6 text-gray-600 dark:text-gray-300">{confirmRevoke?.client_name}에 허용한 접근·갱신 식별자를 모두
        무효화합니다. 이 작업은 앱에서 다시 권한을 요청할 때까지 되돌릴 수 없습니다.</p>
    <div class="mt-6 grid grid-cols-2 gap-3">
        <button class="rounded-xl border border-gray-300 px-4 py-3 font-bold transition hover:bg-gray-50 dark:border-gray-600 dark:hover:bg-gray-700"
                on:click={() => confirmRevoke = null}>취소
        </button>
        <button class="rounded-xl bg-red-600 px-4 py-3 font-bold text-white transition hover:bg-red-700"
                on:click={() => revoke(confirmRevoke)}>연결 해제
        </button>
    </div>
</AccountModal>

<AccountModal open={reauthDialogOpen} title="본인확인" showFooter={false} priority on:close={closeReauthentication}>
    <div class="text-center">
        <div class="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-[#e8f5e9] text-[#438c43]"><span
                class="material-symbols-outlined text-3xl">shield_lock</span></div>
        <p class="mt-4 font-bold">보안을 위해 다시 로그인해 주세요.</p>
        <p class="mt-1 text-sm leading-6 text-gray-500 dark:text-gray-300">고객님의 정보 보호를 위해 본인확인을 진행합니다.</p>
    </div>
    {#if passwordEnabled}
        <div class="mt-5 border-t border-gray-100 pt-5 dark:border-gray-700">
            <label class="text-sm font-bold" for="apps-reauth-password">비밀번호로 확인</label>
            <input id="apps-reauth-password"
                   class="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3 text-slate-900 dark:border-gray-600 dark:bg-gray-900 dark:text-white"
                   type="password" bind:value={reauthPassword} autocomplete="current-password" placeholder="비밀번호"/>
            <input class="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3 text-center tracking-[0.25em] text-slate-900 dark:border-gray-600 dark:bg-gray-900 dark:text-white"
                   autocomplete="one-time-code" bind:value={reauthTotpCode} placeholder="TOTP 인증 코드 또는 보안코드"/>
            <button class="mt-3 w-full rounded-xl bg-[#55aa55] px-4 py-3 font-bold text-white transition hover:bg-[#438c43]"
                    on:click={reauthenticate}>확인
            </button>
            {#if reauthMfaRequired}<p class="mt-3 text-center text-sm text-red-600 dark:text-red-400">TOTP 인증 코드 또는 보안코드를 입력해 주세요.</p>{/if}
        </div>
    {/if}
    {#if passwordEnabled && reauthProviderIds.length > 0}<p
            class="my-5 flex items-center gap-3 text-xs text-gray-400 before:h-px before:flex-1 before:bg-gray-200 after:h-px after:flex-1 after:bg-gray-200 dark:before:bg-gray-700 dark:after:bg-gray-700">
        또는</p>{/if}
    <LoginMethodSelector providerIds={reauthProviderIds} showPasskey={false} {providerUrl}/>
</AccountModal>
<ToastStack {toasts} dismiss={dismissToast}/>
