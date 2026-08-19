<script>
    import {onMount} from 'svelte';
    import LoginMethodSelector from '$lib/LoginMethodSelector.svelte';
    import AccountModal from '$lib/AccountModal.svelte';
    import ToastStack from '$lib/ToastStack.svelte';

    let summary = null;
    let identities = [];
    let passkeys = [];
    let mfa = null;
    let toasts = [];
    let toastId = 0;
    const toastTimers = new Map();
    let email = '';
    let nickname = '';
    let nicknamePolicy = null;
    let fixedNickname = false;
    let password = '';
    let supportPin = '';
    let securityCode = '';
    let totpSecret = '';
    let recoveryCodes = [];
    let selectedProfile = '';
    let reauthRequired = false;
    let reauthPassword = '';
    let reauthTotpCode = '';
    let reauthMfaRequired = false;
    let totpName = '';
    let passwordEnabled = false;
    let passkeySupported = false;
    let reauthProviderIds = [];
    let loading = true;
    let pendingProtectedAction = '';
    let modal = null;
    let modalTotpCode = '';
    const oauthProviders = [
        {id: 'naver', name: '네이버', icon: '/img/auth/naver.png'},
        {id: 'google', name: 'Google', icon: '/img/auth/google.png'},
        {id: 'kakao', name: '카카오', icon: '/img/auth/kakao.png'},
        {id: 'facebook', name: 'Facebook', icon: '/img/auth/facebook.png'},
        {id: 'discord', name: 'Discord', icon: '/img/auth/discord.png'},
        {id: 'daldalso', name: '달달소', icon: '/logo/daldalso.png'}
    ];

    async function load() {
        loading = true;
        try {
            await fetch('/api/account/csrf');
            const [summaryRes, identityRes, passkeyRes, nicknamePolicyRes, mfaRes] = await Promise.all([fetch('/api/account/summary'), fetch('/api/account/identities'), fetch('/api/account/passkeys'), fetch('/api/account/nickname-policy'), fetch('/api/account/mfa')]);
            if (summaryRes.status === 401) {
                location.href = '/login';
                return;
            }
            if (![summaryRes, identityRes, passkeyRes, nicknamePolicyRes, mfaRes].every(response => response.ok)) {
                throw new Error('account api request failed');
            }
            summary = await summaryRes.json();
            identities = await identityRes.json();
            passkeys = await passkeyRes.json();
            nicknamePolicy = await nicknamePolicyRes.json();
            mfa = await mfaRes.json();
            passwordEnabled = summary.password_enabled !== false;
            reauthProviderIds = identities.filter(identity => identity.type === 'OAUTH').map(identity => identity.provider.toLowerCase());
            nickname = (nicknamePolicy.nickname || summary.nickname || '').split('#')[0];
            fixedNickname = Boolean(nicknamePolicy.fixed);
            selectedProfile = summary.selected_profile_id || summary.profiles?.[0]?.id || '';
            totpName = mfa?.totp_name || '';
        } catch (_) {
            notify('계정 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'error');
        } finally {
            loading = false;
        }
    }

    function csrfHeaders() {
        const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
        return token ? {'X-XSRF-TOKEN': decodeURIComponent(token)} : {};
    }

    function notify(message, kind = 'success') {
        const text = String(message || '').trim();
        if (!text) return;
        const id = ++toastId;
        toasts = [...toasts, {id, message: text, kind}];
        toastTimers.set(id, setTimeout(() => dismissToast(id), 4200));
    }

    function dismissToast(id) {
        const timer = toastTimers.get(id);
        if (timer) clearTimeout(timer);
        toastTimers.delete(id);
        toasts = toasts.filter(toast => toast.id !== id);
    }

    function closeModal() {
        modal = null;
        modalTotpCode = '';
    }

    async function call(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: {'Content-Type': 'application/json', ...csrfHeaders(), ...(options.headers || {})}
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            reauthRequired = error.error === 'reauthentication_required';
            notify(reauthRequired ? '보호된 변경을 계속하려면 최근 인증이 필요합니다.' : (error.error_description || '요청을 완료하지 못했습니다.'), 'error');
            return null;
        }
        notify('저장했습니다.');
        return response;
    }

    function rememberProtectedAction(action) {
        pendingProtectedAction = action;
        sessionStorage.setItem('kkutu-account-protected-action', action);
    }

    function clearProtectedAction() {
        pendingProtectedAction = '';
        sessionStorage.removeItem('kkutu-account-protected-action');
    }

    async function resumeProtectedAction() {
        const action = pendingProtectedAction || sessionStorage.getItem('kkutu-account-protected-action');
        if (!action || reauthRequired) return;
        clearProtectedAction();
        if (action === 'support-pin') await issuePin();
        if (action === 'one-time-login-codes') await rotateOneTimeLoginCodes();
        if (action === 'security-code') await revealSecurityCode();
        if (action.startsWith('identity-revoke:')) {
            const identity = identities.find(item => item.id === Number(action.slice('identity-revoke:'.length)));
            if (identity) await revoke(identity, true);
        }
        if (action.startsWith('identity-link:')) await linkProvider(action.slice('identity-link:'.length), true);
    }

    function avatarUrl() {
        const seed = summary?.uuid || summary?.legacy_user_id || 'kkutuio';
        return `https://api.dicebear.com/10.x/lorelei/svg?seed=${encodeURIComponent(seed)}`;
    }

    function useFallbackAvatar(event) {
        event.currentTarget.onerror = null;
        event.currentTarget.src = 'https://cdn.kkutu.io/img/bi/bi_profile_main.png';
    }

    function linkedIdentity(provider) {
        return identities.find(identity => identity.type === 'OAUTH' && identity.provider?.toLowerCase() === provider.id);
    }

    function linkedAt(identity) {
        return identity?.created_at ? new Date(identity.created_at).toLocaleDateString() : '';
    }

    function linkedProviderCount() {
        return oauthProviders.filter(provider => linkedIdentity(provider)).length;
    }

    async function copyIdentifier() {
        const identifier = summary?.legacy_user_id;
        if (!identifier) return;
        try {
            if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(identifier);
            else {
                const input = document.createElement('textarea');
                input.value = identifier;
                input.style.position = 'fixed';
                input.style.opacity = '0';
                document.body.appendChild(input);
                input.select();
                document.execCommand('copy');
                input.remove();
            }
            notify('클립보드에 복사되었습니다.', 'info');
        } catch (_) {
            notify('식별번호를 복사하지 못했습니다.', 'error');
        }
    }

    async function saveNickname() {
        const notice = fixedNickname
            ? '100핑을 사용하여 별명을 고정합니다. 별명 고정 이후 180일 이상 게임에 접속하지 않으면 다른 회원이 별명 고정을 해제시킬 수 있습니다. 계속하시겠습니까?'
            : '비고정 별명은 무료이며 뒤에 식별번호 5자리가 붙습니다. 별명 변경은 7일에 한 번 가능합니다. 계속하시겠습니까?';
        if (!confirm(notice)) return;
        if (await call('/api/account/nickname', {
            method: 'PATCH',
            body: JSON.stringify({nickname, fixed: fixedNickname})
        })) load();
    }

    async function addEmail() {
        await call('/api/account/email/verify', {method: 'POST', body: JSON.stringify({email})});
    }

    async function removeEmail() {
        if (confirm('정말로 전자 메일 주소를 삭제할까요? 전자 메일 주소를 삭제하면 일회용 비밀번호 없이 계정 복구가 불가능합니다.') && await call('/api/account/email', {method: 'DELETE'})) load();
    }

    async function setPassword() {
        await call('/api/account/password', {method: 'POST', body: JSON.stringify({password})});
        password = '';
    }

    async function selectProfile() {
        await call('/api/account/profile', {method: 'PUT', body: JSON.stringify({profileId: selectedProfile})});
    }

    async function issuePin() {
        const r = await call('/api/account/support-pin/issue', {method: 'POST'});
        if (r) {
            clearProtectedAction();
            supportPin = (await r.json()).pin;
            modal = {type: 'support-pin', title: '지원 PIN'};
        } else if (reauthRequired) rememberProtectedAction('support-pin');
    }

    async function revealSecurityCode() {
        const r = await call('/api/account/security-code/reveal', {method: 'POST'});
        if (r) {
            clearProtectedAction();
            securityCode = (await r.json()).securityCode;
            modal = {type: 'security-code', title: '보안 코드'};
        } else if (reauthRequired) rememberProtectedAction('security-code');
    }

    async function revoke(identity, confirmed = false) {
        if (confirmed || confirm(`${identity.provider} 로그인 수단을 해제할까요?`)) {
            const response = await call(`/api/account/identities/${identity.id}/revoke`, {method: 'POST'});
            if (response) {
                clearProtectedAction();
                await load();
            } else if (reauthRequired) {
                rememberProtectedAction(`identity-revoke:${identity.id}`);
            }
        }
    }

    const b64 = value => btoa(String.fromCharCode(...new Uint8Array(value))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const unb64 = value => Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));

    async function registerPasskey() {
        if (!window.PublicKeyCredential) {
            notify('이 브라우저는 패스키를 지원하지 않습니다.', 'error');
            return;
        }
        try {
            const options = await (await fetch('/api/account/passkeys/registration/options', {
                method: 'POST',
                headers: csrfHeaders()
            })).json();
            const p = options.publicKey;
            p.challenge = unb64(p.challenge);
            p.user.id = unb64(p.user.id);
            const c = await navigator.credentials.create({publicKey: p});
            const r = await call('/api/account/passkeys/registration/complete', {
                method: 'POST',
                body: JSON.stringify({
                    operationToken: options.operation_token,
                    deviceName: 'Passkey',
                    credential: {
                        id: c.id,
                        rawId: b64(c.rawId),
                        response: {
                            clientDataJSON: b64(c.response.clientDataJSON),
                            attestationObject: b64(c.response.attestationObject)
                        }
                    }
                })
            });
            if (r) load();
        } catch (_) {
            notify('Passkey 등록에 실패했습니다.', 'error');
        }
    }

    async function removePasskey(id) {
        if (confirm('이 패스키를 삭제할까요?')) {
            await call(`/api/account/passkeys/${id}`, {method: 'DELETE'});
            load();
        }
    }

    async function setupTotp() {
        const r = await call('/api/account/mfa/totp/setup', {method: 'POST', body: JSON.stringify({name: 'TOTP 매체'})});
        if (!r) return;
        const data = await r.json();
        totpSecret = data.secret;
        modalTotpCode = '';
        modal = {type: 'totp-setup', title: '2단계 인증 설정'};
    }

    async function confirmTotp() {
        if (!modalTotpCode.trim()) {
            notify('2단계 인증 코드를 입력해 주세요.', 'error');
            return;
        }
        if (await call('/api/account/mfa/totp/confirm', {method: 'POST', body: JSON.stringify({code: modalTotpCode.trim()})})) {
            closeModal();
            totpSecret = '';
            await load();
        }
    }

    async function renameTotp() {
        if (await call('/api/account/mfa/totp', {method: 'PATCH', body: JSON.stringify({name: totpName})})) load();
    }

    async function removeTotp() {
        if (confirm('정말로 2단계 인증을 해제하시겠습니까?') && await call('/api/account/mfa/totp', {method: 'DELETE'})) load();
    }

    async function setExternalLoginMfa(enabled) {
        if (await call('/api/account/mfa/external-login', {method: 'PATCH', body: JSON.stringify({enabled})})) load();
    }

    async function rotateOneTimeLoginCodes() {
        const r = await call('/api/account/one-time-login-codes/rotate', {method: 'POST'});
        if (r) {
            clearProtectedAction();
            recoveryCodes = (await r.json()).codes || [];
            mfa = {...(mfa || {}), one_time_login_codes_remaining: recoveryCodes.length};
            modal = {type: 'one-time-codes', title: '일회용 비밀번호'};
        } else if (reauthRequired) rememberProtectedAction('one-time-login-codes');
    }

    async function reauthenticate() {
        const response = await fetch('/api/account/reauthenticate', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', ...csrfHeaders()},
            body: JSON.stringify({password: reauthPassword, totpCode: reauthTotpCode || null})
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            reauthMfaRequired = error.error === 'mfa_required';
            notify(error.error_description || '본인확인에 실패하였습니다.', 'error');
            return;
        }
        reauthRequired = false;
        reauthPassword = '';
        reauthTotpCode = '';
        notify('본인확인이 완료되었습니다.');
        await resumeProtectedAction();
    }

    async function requestReauthenticationEmailMfaCode() {
        const response = await fetch('/api/account/reauthenticate/email-mfa-code', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', ...csrfHeaders()},
            body: JSON.stringify({password: reauthPassword})
        });
        const error = await response.json().catch(() => ({}));
        notify(response.ok ? '전자 메일로 인증 코드를 보냈습니다. 확인 후 입력해 주세요.' : (error.error_description || '인증된 전자 메일 주소가 없어 메일 인증을 요청할 수 없습니다.'), response.ok ? 'info' : 'error');
    }

    async function passkeyReauthenticate() {
        if (!window.PublicKeyCredential) {
            notify('이 브라우저는 패스키를 지원하지 않습니다.', 'error');
            return;
        }
        try {
            const optionsResponse = await fetch('/api/account/passkeys/authentication/options', {method: 'POST'});
            const options = await optionsResponse.json();
            const publicKey = options.publicKey;
            publicKey.challenge = Uint8Array.from(atob(publicKey.challenge.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
            const credential = await navigator.credentials.get({publicKey});
            const encoded = value => btoa(String.fromCharCode(...new Uint8Array(value))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
            const response = await fetch('/api/account/passkeys/authentication/complete', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    operationToken: options.operation_token,
                    credential: {
                        id: credential.id,
                        rawId: encoded(credential.rawId),
                        response: {
                            clientDataJSON: encoded(credential.response.clientDataJSON),
                            authenticatorData: encoded(credential.response.authenticatorData),
                            signature: encoded(credential.response.signature),
                            userHandle: credential.response.userHandle ? encoded(credential.response.userHandle) : null
                        }
                    }
                })
            });
            if (!response.ok) throw new Error();
            location.href = '/account';
        } catch (_) {
            notify('패스키 인증에 실패했습니다. 다시 시도해 주세요.', 'error');
        }
    }

    async function linkProvider(provider, afterReauthentication = false) {
        const url = '/api/account/identities/oauth/' + encodeURIComponent(provider);
        if (afterReauthentication) {
            location.href = url;
            return;
        }
        const response = await fetch(url, {redirect: 'manual'});
        if (response.status === 401) {
            reauthRequired = true;
            rememberProtectedAction(`identity-link:${provider}`);
            notify('연동을 계속하려면 최근 인증이 필요합니다.', 'error');
            return;
        }
        if (response.type !== 'opaqueredirect' && !response.ok) {
            const error = await response.json().catch(() => ({}));
            notify(error.error_description || '연동을 시작하지 못했습니다.', 'error');
            return;
        }
        location.href = url;
    }

    onMount(async () => {
        passkeySupported = !!window.PublicKeyCredential;
        await load();
        await resumeProtectedAction();
    });
</script>

<svelte:head><title>끄투리오 - 계정 관리</title></svelte:head>
<main class="min-h-screen bg-gray-100 px-4 pb-16 pt-24 text-gray-900 dark:bg-gray-900 dark:text-gray-100 sm:px-6">
    <div class="mx-auto max-w-3xl space-y-8">
        <div class="flex items-center justify-between gap-3"><h1 class="text-3xl font-bold tracking-tight text-gray-700 dark:text-gray-100">계정 관리</h1><button class="grid h-10 w-10 place-items-center rounded-full text-gray-500 transition hover:bg-gray-200 hover:text-gray-900 disabled:opacity-50 dark:hover:bg-gray-800 dark:hover:text-white" on:click={load} disabled={loading} aria-label="새로고침"><span class:animate-spin={loading} class="material-symbols-outlined">{loading ? 'progress_activity' : 'refresh'}</span></button></div>
        {#if reauthRequired}
            <section
                    class="rounded-2xl border border-amber-200 bg-amber-50 p-5 shadow-sm dark:border-amber-900 dark:bg-amber-950">
                <h2 class="font-bold">본인확인이 필요한 업무입니다</h2>
                {#if passwordEnabled}
                    <p class="mt-1 text-sm text-amber-900 dark:text-amber-100">고객님의 개인정보 보호를 위해 로그인 정보를 다시 확인합니다.</p>
                    <input
                            class="mt-4 w-full rounded-xl border border-amber-200 bg-white p-3 text-slate-900 dark:border-amber-800 dark:bg-gray-900 dark:text-white"
                            type="password" bind:value={reauthPassword} autocomplete="current-password"
                            placeholder="비밀번호"/><input
                        class="mt-2 w-full rounded-xl border border-amber-200 bg-white p-3 text-slate-900 dark:border-amber-800 dark:bg-gray-900 dark:text-white"
                        autocomplete="one-time-code" bind:value={reauthTotpCode} placeholder="TOTP 인증 코드 또는 보안코드"/>
                    <button class="mt-3 rounded-full bg-amber-700 px-5 py-2.5 text-sm font-bold text-white"
                            on:click={reauthenticate}>확인
                    </button>
                    {#if reauthMfaRequired}
                        <div class="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-sm underline">
                            <button on:click={requestReauthenticationEmailMfaCode}>TOTP 인증 코드를 확인할 수 없나요?</button>
                            <a href="/account/recovery?mode=one-time" rel="external">일회용 비밀번호 사용</a>
                        </div>
                    {/if}
                {:else}
                    <p class="mt-1 text-sm text-amber-900 dark:text-amber-100">고객님의 개인정보 보호를 위해 로그인 정보를 다시 확인합니다.</p>
                {/if}
                <LoginMethodSelector providerIds={reauthProviderIds} showPasskey={passkeys.length > 0}
                                     {passkeySupported} onPasskey={passkeyReauthenticate}
                                     providerUrl={provider => `/api/account/reauthenticate/oauth/${encodeURIComponent(provider)}`}/>
            </section>
        {/if}
        {#if summary}
            <section class="flex items-center gap-4 rounded-2xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <img class="h-16 w-16 shrink-0 rounded-2xl bg-slate-100" src={avatarUrl()} alt="계정 아바타" on:error={useFallbackAvatar}/>
                <div class="min-w-0 flex-1">
                    <h2 class="truncate text-xl font-bold">{summary.nickname || '별명 설정 필요'}</h2>
                    <p class="mt-1 truncate text-sm text-gray-500 dark:text-gray-300">{summary.legacy_user_id}</p>
                </div>
                <label class="sr-only" for="account-profile">게임 프로필</label>
                <select id="account-profile" class="max-w-[9rem] rounded-xl border border-gray-300 bg-white px-3 py-2 text-sm font-semibold dark:border-gray-600 dark:bg-gray-900" bind:value={selectedProfile} on:change={selectProfile}>
                    {#each summary.profiles || [] as profile}
                        <option value={profile.id}>{profile.nickname || profile.legacy_user_id}</option>
                    {/each}
                </select>
            </section>

            <section>
                <h2 class="mb-3 text-2xl font-bold text-gray-700 dark:text-gray-100">계정 개요</h2>
                <div class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
                    <details open class="group border-b border-gray-200 dark:border-gray-700">
                        <summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>별명</span><span class="material-symbols-outlined text-gray-500 transition group-open:rotate-180">expand_more</span></summary>
                        <div class="px-5 pb-5">
                            <div class="grid gap-2 sm:grid-cols-[1fr_auto]"><input class="rounded-xl border border-gray-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900" maxlength="15" bind:value={nickname} placeholder="새 별명"/>
                                <button class="rounded-xl bg-[#55aa55] px-5 py-3 text-sm font-bold text-white disabled:opacity-50" disabled={nicknamePolicy && !nicknamePolicy.can_change} on:click={saveNickname}>변경</button></div>
                            <label class="mt-3 flex cursor-pointer items-center gap-2 text-sm"><input type="checkbox" bind:checked={fixedNickname}/><span>별명 고정</span></label>
                            {#if nicknamePolicy?.game_connected}<p class="mt-3 text-sm text-red-600">게임 접속 중에는 게임 내 프로필 관리 화면에서 별명을 변경해 주세요.</p>{:else if nicknamePolicy?.change_restricted}<p class="mt-3 text-sm text-red-600">운영정책 위반으로 별명 변경을 이용할 수 없습니다.</p>{:else if nicknamePolicy && !nicknamePolicy.can_change}<p class="mt-3 text-sm text-red-600">{new Date(nicknamePolicy.next_change_at).toLocaleString()} 이후 별명을 변경할 수 있습니다.</p>{/if}
                        </div>
                    </details>
                    <div class="flex items-center justify-between gap-5 p-5"><span class="font-bold">식별번호</span><div class="flex max-w-[65%] items-center gap-2"><span class="break-all text-right font-mono text-sm text-gray-500 dark:text-gray-300">{summary.legacy_user_id}</span><button class="grid h-8 w-8 shrink-0 place-items-center rounded-lg text-gray-500 transition hover:bg-gray-100 hover:text-gray-900 dark:hover:bg-gray-700 dark:hover:text-white" on:click={copyIdentifier} aria-label="식별번호 복사"><span class="material-symbols-outlined text-lg">content_copy</span></button></div></div>
                    {#if passwordEnabled}
                        <details class="group border-t border-gray-200 dark:border-gray-700">
                            <summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>전자 메일 주소</span><span class="material-symbols-outlined text-gray-500 transition group-open:rotate-180">expand_more</span></summary>
                            <div class="px-5 pb-5"><div class="flex items-center justify-between gap-3"><p class="text-sm text-gray-500 dark:text-gray-300">{summary.email || '미등록'}</p>{#if summary.email}<button class="text-sm font-bold text-red-700" on:click={removeEmail}>삭제</button>{/if}</div>
                                <div class="mt-4 grid gap-2 sm:grid-cols-[1fr_auto]"><input class="rounded-xl border border-gray-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900" type="email" placeholder="새 전자 메일 주소" bind:value={email}/><button class="rounded-xl border border-[#55aa55] px-4 py-3 text-sm font-bold text-[#438c43]" on:click={addEmail}>인증</button></div></div>
                        </details>
                    {/if}
                    <details class="group border-t border-gray-200 dark:border-gray-700">
                        <summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>계정 연동</span><span class="flex items-center gap-3 text-sm font-normal text-gray-500">{linkedProviderCount()}/{oauthProviders.length}<span class="material-symbols-outlined transition group-open:rotate-180">expand_more</span></span></summary>
                        <div class="border-t border-gray-100 px-5 dark:border-gray-700">
                            {#each oauthProviders as provider}
                                {@const identity = linkedIdentity(provider)}
                                <div class="flex items-center gap-4 border-b border-gray-100 py-4 last:border-0 dark:border-gray-700">
                                    <img src={provider.icon} class="h-10 w-10 shrink-0 object-contain" alt="{provider.name} 아이콘"/>
                                    <div class="min-w-0 flex-1">
                                        <h3 class="font-bold">{provider.name}</h3>
                                        {#if identity}
                                            <p class="mt-0.5 truncate text-sm text-gray-600 dark:text-gray-300">{identity.display_name || '연동됨'}</p>
                                            <p class="mt-1 text-xs text-gray-500">연동 일시: {linkedAt(identity)}</p>
                                        {:else}
                                            <p class="mt-1 text-sm text-gray-500 dark:text-gray-300">연동되지 않음</p>
                                        {/if}
                                    </div>
                                    {#if identity?.revocable}
                                        <button class="shrink-0 rounded-xl border border-red-300 px-4 py-2 text-sm font-bold text-red-700 dark:border-red-900" on:click={() => revoke(identity)}>연동 해제</button>
                                    {:else if identity}
                                        <span class="shrink-0 text-sm text-gray-500">기본 로그인 수단</span>
                                    {:else}
                                        <button class="shrink-0 rounded-xl border border-[#55aa55] px-4 py-2 text-sm font-bold text-[#438c43]" on:click={() => linkProvider(provider.id)}>연동하기</button>
                                    {/if}
                                </div>
                            {/each}
                        </div>
                    </details>
                </div>
            </section>

            <section>
                <h2 class="mb-3 text-2xl font-bold text-gray-700 dark:text-gray-100">로그인 수단</h2>
                <div class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
                    {#if passwordEnabled}
                        <details class="group border-b border-gray-200 dark:border-gray-700"><summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>비밀번호</span><span class="material-symbols-outlined text-gray-500 transition group-open:rotate-180">expand_more</span></summary><div class="px-5 pb-5"><div class="grid gap-2 sm:grid-cols-[1fr_auto]"><input class="rounded-xl border border-gray-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900" type="password" minlength="12" placeholder="새 비밀번호 (12자 이상)" bind:value={password}/><button class="rounded-xl bg-slate-800 px-4 py-3 text-sm font-bold text-white dark:bg-gray-700" on:click={setPassword}>{identities.some(identity => identity.type === 'PASSWORD') ? '변경' : '설정'}</button></div></div></details>
                    {/if}
                    <details class="group border-b border-gray-200 dark:border-gray-700"><summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>패스키</span><span class="flex items-center gap-3 text-sm font-normal text-gray-500">{passkeys.length}/10<span class="material-symbols-outlined transition group-open:rotate-180">expand_more</span></span></summary><div class="px-5 pb-5"><button class="rounded-xl border border-[#55aa55] px-4 py-2 text-sm font-bold text-[#438c43] disabled:opacity-50" on:click={registerPasskey} disabled={passkeys.length >= 10}>추가</button>{#each passkeys as passkey}<div class="mt-3 flex items-center justify-between gap-3 rounded-xl bg-gray-50 p-3 text-sm dark:bg-gray-900"><span><b>{passkey.device_name}</b><br/><small class="text-gray-500">{passkey.last_used_at || passkey.created_at}</small></span><button class="font-bold text-red-700" on:click={() => removePasskey(passkey.id)}>삭제</button></div>{/each}</div></details>
                    <details class="group border-b border-gray-200 dark:border-gray-700"><summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>2단계 인증</span><span class="flex items-center gap-3 text-sm font-normal text-gray-500">{mfa?.totp ? '사용 중' : '미설정'}<span class="material-symbols-outlined transition group-open:rotate-180">expand_more</span></span></summary><div class="px-5 pb-5"><div class="flex items-center justify-between gap-3">{#if mfa?.totp}<button class="rounded-xl border border-red-300 px-4 py-2 text-sm font-bold text-red-700" on:click={removeTotp}>해제</button>{:else}<button class="rounded-xl border border-[#55aa55] px-4 py-2 text-sm font-bold text-[#438c43]" on:click={setupTotp}>추가</button>{/if}</div>{#if mfa?.totp}<div class="mt-4 grid gap-2 sm:grid-cols-[1fr_auto]"><input class="rounded-xl border border-gray-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900" maxlength="100" bind:value={totpName} placeholder="TOTP 매체명"/><button class="rounded-xl border px-4 py-3 text-sm font-bold" on:click={renameTotp}>수정</button></div>{#if passwordEnabled}<div class="mt-4 flex items-center justify-between gap-4 rounded-xl bg-gray-50 px-4 py-3 dark:bg-gray-900"><span class="text-sm font-bold">외부 계정으로 로그인 시 2단계 인증 사용</span><button type="button" role="switch" aria-checked={Boolean(mfa?.external_login_mfa_enabled)} class={`relative h-7 w-12 rounded-full transition-colors ${mfa?.external_login_mfa_enabled ? 'bg-[#55aa55]' : 'bg-gray-300 dark:bg-gray-600'}`} on:click={() => setExternalLoginMfa(!mfa?.external_login_mfa_enabled)}><span class:translate-x-6={mfa?.external_login_mfa_enabled} class="absolute left-1 top-1 h-5 w-5 rounded-full bg-white shadow transition-transform"></span></button></div>{/if}{/if}</div></details>
                </div>
            </section>

            <section>
                <h2 class="mb-3 text-2xl font-bold text-gray-700 dark:text-gray-100">지원 · 복구</h2>
                <div class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
                    <details class="group border-b border-gray-200 dark:border-gray-700"><summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>지원 PIN</span><span class="material-symbols-outlined text-gray-500 transition group-open:rotate-180">expand_more</span></summary><div class="px-5 pb-5"><p class="text-sm text-gray-500 dark:text-gray-300">고객센터 상담 시 본인 확인에 사용합니다.</p><button class="mt-3 rounded-xl bg-slate-800 px-4 py-2 text-sm font-bold text-white dark:bg-gray-700" on:click={issuePin}>발급</button></div></details>
                    <details class="group border-b border-gray-200 dark:border-gray-700"><summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>일회용 비밀번호</span><span class="flex items-center gap-3 text-sm font-normal text-gray-500">{mfa?.one_time_login_codes_remaining || 0}개<span class="material-symbols-outlined transition group-open:rotate-180">expand_more</span></span></summary><div class="px-5 pb-5"><button class="rounded-xl border border-gray-300 px-4 py-2 text-sm font-bold dark:border-gray-600" on:click={rotateOneTimeLoginCodes}>새 코드 발급</button></div></details>
                    <details class="group"><summary class="flex cursor-pointer list-none items-center justify-between p-5 font-bold"><span>보안 코드</span><span class="material-symbols-outlined text-gray-500 transition group-open:rotate-180">expand_more</span></summary><div class="px-5 pb-5"><p class="text-sm text-gray-500 dark:text-gray-300">계정 복구에 필요한 보안 코드를 확인합니다.</p><button class="mt-3 rounded-xl border border-gray-300 px-4 py-2 text-sm font-bold dark:border-gray-600" on:click={revealSecurityCode}>보기</button></div></details>
                </div>
            </section>
        {:else if loading}
            <section class="rounded-lg bg-white p-8 text-center text-sm text-gray-500 shadow-md dark:bg-gray-800 dark:text-gray-300">
                계정 정보를 불러오는 중입니다.
            </section>
        {:else}
            <section class="rounded-lg bg-white p-8 text-center text-sm text-gray-500 shadow-md dark:bg-gray-800 dark:text-gray-300">
                계정 정보를 표시할 수 없습니다. 잠시 후 다시 시도해 주세요.
            </section>
        {/if}
    </div>
</main>

<AccountModal open={Boolean(modal)} title={modal?.title || ''} on:close={closeModal}>
    {#if modal?.type === 'totp-setup'}
        <p class="text-sm leading-6 text-gray-600 dark:text-gray-300">2단계 인증 앱에 아래 비밀키를 등록한 뒤 표시되는 6자리 인증번호를 입력하세요.</p>
        <p class="mt-4 break-all rounded-xl bg-amber-50 p-4 font-mono font-bold text-amber-900 dark:bg-amber-950 dark:text-amber-100">{totpSecret}</p>
        <input class="mt-4 w-full rounded-xl border border-gray-300 bg-white p-3 text-center text-lg tracking-[0.35em] dark:border-gray-600 dark:bg-gray-900" maxlength="6" inputmode="numeric" autocomplete="one-time-code" bind:value={modalTotpCode} placeholder="000000"/>
        <button class="mt-3 w-full rounded-xl bg-[#55aa55] px-4 py-3 font-bold text-white" on:click={confirmTotp}>확인</button>
    {:else if modal?.type === 'support-pin'}
        <p class="text-sm text-gray-600 dark:text-gray-300">고객센터 상담 시 이 PIN을 전달해 주세요.</p>
        <p class="mt-4 rounded-xl bg-amber-50 p-4 text-center font-mono text-3xl font-bold tracking-[0.28em] text-amber-900 dark:bg-amber-950 dark:text-amber-100">{supportPin}</p>
    {:else if modal?.type === 'one-time-codes'}
        <p class="text-sm text-gray-600 dark:text-gray-300">각 코드는 한 번만 사용할 수 있습니다. 안전한 곳에 보관해 주세요.</p>
        <div class="mt-4 grid grid-cols-2 gap-2">{#each recoveryCodes as code}<code class="rounded-xl bg-amber-50 p-3 text-center font-bold text-amber-900 dark:bg-amber-950 dark:text-amber-100">{code}</code>{/each}</div>
    {:else if modal?.type === 'security-code'}
        <p class="text-sm text-gray-600 dark:text-gray-300">계정 복구에 필요한 보안 코드입니다. 다른 사람에게 공유하지 마세요.</p>
        <p class="mt-4 break-all rounded-xl bg-amber-50 p-4 text-center font-mono text-xl font-bold text-amber-900 dark:bg-amber-950 dark:text-amber-100">{securityCode}</p>
    {/if}
</AccountModal>
<ToastStack {toasts} dismiss={dismissToast}/>

<style>
    :global(summary::-webkit-details-marker) { display: none; }
    :global(details > summary) { -webkit-tap-highlight-color: transparent; }
    :global(details::details-content) {
        block-size: 0;
        opacity: 0;
        overflow: clip;
        transition: content-visibility 180ms allow-discrete, block-size 180ms ease, opacity 150ms ease;
    }
    :global(details[open]::details-content) { block-size: auto; opacity: 1; }
    :global(details[open] > :not(summary)) { animation: account-panel-open 180ms ease-out both; }
    @keyframes account-panel-open {
        from { opacity: 0; transform: translateY(-5px); }
        to { opacity: 1; transform: translateY(0); }
    }
</style>
