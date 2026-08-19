<script>
    import { onMount } from 'svelte';
    import LoginMethodSelector from '$lib/LoginMethodSelector.svelte';
    let summary = null;
    let identities = [];
    let passkeys = [];
    let mfa = null;
    let message = '';
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
    let totpName = '';
    let passwordEnabled = true;
    let passkeySupported = false;
    let reauthProviderIds = [];

    async function load() {
        await fetch('/api/account/csrf');
        const [summaryRes, identityRes, passkeyRes, nicknamePolicyRes, mfaRes] = await Promise.all([fetch('/api/account/summary'), fetch('/api/account/identities'), fetch('/api/account/passkeys'), fetch('/api/account/nickname-policy'), fetch('/api/account/mfa')]);
        if (summaryRes.status === 401) { location.href = '/login'; return; }
        summary = await summaryRes.json(); identities = await identityRes.json(); passkeys = await passkeyRes.json(); nicknamePolicy = await nicknamePolicyRes.json(); mfa = await mfaRes.json(); passwordEnabled = summary.password_enabled !== false; reauthProviderIds = identities.filter(identity => identity.type === 'OAUTH').map(identity => identity.provider.toLowerCase()); nickname = (nicknamePolicy.nickname || summary.nickname || '').split('#')[0]; fixedNickname = Boolean(nicknamePolicy.fixed); selectedProfile = summary.selected_profile_id || summary.profiles?.[0]?.id || ''; totpName = mfa?.totp_name || '';
    }
    function csrfHeaders() {
        const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
        return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {};
    }
    async function call(url, options = {}) {
        const response = await fetch(url, { ...options, headers: { 'Content-Type': 'application/json', ...csrfHeaders(), ...(options.headers || {}) } });
        if (!response.ok) { const error = await response.json().catch(() => ({})); reauthRequired = error.error === 'reauthentication_required'; message = reauthRequired ? '보호된 변경을 계속하려면 최근 인증이 필요합니다.' : (error.error_description || '요청을 완료하지 못했습니다.'); return null; }
        message = '저장했습니다.'; return response;
    }
    async function saveNickname() {
        const notice = fixedNickname
            ? '100핑을 사용하여 별명을 고정합니다. 별명 고정 이후 180일 이상 게임에 접속하지 않으면 다른 회원이 별명 고정을 해제시킬 수 있습니다. 계속하시겠습니까?'
            : '비고정 별명은 무료이며 뒤에 식별번호 5자리가 붙습니다. 별명 변경은 7일에 한 번 가능합니다. 계속하시겠습니까?';
        if (!confirm(notice)) return;
        if (await call('/api/account/nickname', { method: 'PATCH', body: JSON.stringify({ nickname, fixed: fixedNickname }) })) load();
    }
    async function addEmail() { await call('/api/account/email/verify', { method: 'POST', body: JSON.stringify({ email }) }); }
    async function removeEmail() { if (confirm('인증된 복구 메일 주소를 삭제할까요?') && await call('/api/account/email', { method: 'DELETE' })) load(); }
    async function setPassword() { await call('/api/account/password', { method: 'POST', body: JSON.stringify({ password }) }); password = ''; }
    async function selectProfile() { await call('/api/account/profile', { method: 'PUT', body: JSON.stringify({ profileId: selectedProfile }) }); }
    async function issuePin() { const r = await call('/api/account/support-pin/issue', { method: 'POST' }); if (r) supportPin = (await r.json()).pin; }
    async function revealSecurityCode() { const r = await call('/api/account/security-code/reveal', { method: 'POST' }); if (r) securityCode = (await r.json()).securityCode; }
    async function revoke(identity) { if (confirm(`${identity.provider} 로그인 수단을 해제할까요?`)) { await call(`/api/account/identities/${identity.id}/revoke`, { method: 'POST' }); load(); } }
    const b64 = value => btoa(String.fromCharCode(...new Uint8Array(value))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const unb64 = value => Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
    async function registerPasskey() {
        if (!window.PublicKeyCredential) { message = '이 브라우저는 패스키를 지원하지 않습니다.'; return; }
        try { const options = await (await fetch('/api/account/passkeys/registration/options', { method: 'POST', headers: csrfHeaders() })).json(); const p = options.publicKey; p.challenge = unb64(p.challenge); p.user.id = unb64(p.user.id); const c = await navigator.credentials.create({ publicKey: p }); const r = await call('/api/account/passkeys/registration/complete', { method: 'POST', body: JSON.stringify({ operationToken: options.operation_token, deviceName: prompt('기기 이름', 'Passkey') || 'Passkey', credential: { id: c.id, rawId: b64(c.rawId), response: { clientDataJSON: b64(c.response.clientDataJSON), attestationObject: b64(c.response.attestationObject) } } }) }); if (r) load(); } catch (_) { message = 'Passkey 등록에 실패했습니다.'; }
    }
    async function removePasskey(id) { if (confirm('이 패스키를 삭제할까요?')) { await call(`/api/account/passkeys/${id}`, { method: 'DELETE' }); load(); } }
    async function setupTotp() {
        const r = await call('/api/account/mfa/totp/setup', { method: 'POST', body: JSON.stringify({ name: 'TOTP 기기' }) });
        if (!r) return;
        const data = await r.json(); totpSecret = data.secret;
        const code = prompt('2단계 인증 앱에 표시된 6자리 코드를 입력하세요.');
        if (code && await call('/api/account/mfa/totp/confirm', { method: 'POST', body: JSON.stringify({ code }) })) load();
    }
    async function renameTotp() { if (await call('/api/account/mfa/totp', { method: 'PATCH', body: JSON.stringify({ name: totpName }) })) load(); }
    async function removeTotp() { if (confirm('2단계 인증을 해제할까요?') && await call('/api/account/mfa/totp', { method: 'DELETE' })) load(); }
    async function rotateOneTimeLoginCodes() { const r = await call('/api/account/one-time-login-codes/rotate', { method: 'POST' }); if (r) recoveryCodes = (await r.json()).codes || []; }
    async function reauthenticate() {
        const response = await fetch('/api/account/reauthenticate', { method: 'POST', headers: { 'Content-Type': 'application/json', ...csrfHeaders() }, body: JSON.stringify({ password: reauthPassword, totpCode: reauthTotpCode || null }) });
        if (!response.ok) { message = (await response.json().catch(() => ({}))).error_description || '최근 인증에 실패했습니다.'; return; }
        reauthRequired = false; reauthPassword = ''; reauthTotpCode = ''; message = '최근 인증을 완료했습니다. 변경하려던 작업을 다시 시도해 주세요.';
    }
    async function passkeyReauthenticate() {
        if (!window.PublicKeyCredential) { message = '이 브라우저는 패스키를 지원하지 않습니다.'; return; }
        try {
            const optionsResponse = await fetch('/api/account/passkeys/authentication/options', { method: 'POST' });
            const options = await optionsResponse.json();
            const publicKey = options.publicKey;
            publicKey.challenge = Uint8Array.from(atob(publicKey.challenge.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
            const credential = await navigator.credentials.get({ publicKey });
            const encoded = value => btoa(String.fromCharCode(...new Uint8Array(value))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
            const response = await fetch('/api/account/passkeys/authentication/complete', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ operationToken: options.operation_token, credential: { id: credential.id, rawId: encoded(credential.rawId), response: { clientDataJSON: encoded(credential.response.clientDataJSON), authenticatorData: encoded(credential.response.authenticatorData), signature: encoded(credential.response.signature), userHandle: credential.response.userHandle ? encoded(credential.response.userHandle) : null } } }) });
            if (!response.ok) throw new Error();
            location.href = '/account';
        } catch (_) { message = '패스키 인증에 실패했습니다. 다시 시도해 주세요.'; }
    }
    function linkProvider(provider) { location.href = '/api/account/identities/oauth/' + encodeURIComponent(provider); }
    onMount(() => { passkeySupported = !!window.PublicKeyCredential; load(); });
</script>

<svelte:head><title>끄투리오 - 계정 관리</title></svelte:head>
<main class="min-h-screen bg-slate-100 px-4 pb-14 pt-24 text-slate-800 dark:bg-gray-900 dark:text-gray-100 sm:px-6">
    <div class="mx-auto max-w-3xl space-y-5">
        <header class="rounded-2xl bg-gradient-to-r from-[#4a9b4b] to-[#65b166] p-6 text-white shadow-lg"><p
                class="text-sm font-medium text-white/80">끄투리오 계정</p>
            <h1 class="mt-1 text-3xl font-bold tracking-tight">계정 및 보안</h1>
            <p class="mt-2 text-sm text-white/85">로그인 수단, 복구 정보와 게임 프로필을 한 곳에서 관리합니다.</p></header>
        {#if message}<p
                class="rounded-xl border border-green-200 bg-green-50 p-4 text-sm text-green-900 shadow-sm dark:border-green-900 dark:bg-green-950 dark:text-green-100"
                role="status">{message}</p>{/if}
        {#if reauthRequired}
            <section
                    class="rounded-2xl border border-amber-200 bg-amber-50 p-5 shadow-sm dark:border-amber-900 dark:bg-amber-950">
                <h2 class="font-bold">다시 로그인해 주세요</h2>
                {#if passwordEnabled}
                <p class="mt-1 text-sm text-amber-900 dark:text-amber-100">고객님의 개인정보 보호를 위해 로그인 정보를 다시 확인합니다.</p><input
                    class="mt-4 w-full rounded-xl border border-amber-200 bg-white p-3 text-slate-900 dark:border-amber-800 dark:bg-gray-900 dark:text-white"
                    type="password" bind:value={reauthPassword} autocomplete="current-password"
                    placeholder="비밀번호"/><input
                    class="mt-2 w-full rounded-xl border border-amber-200 bg-white p-3 text-slate-900 dark:border-amber-800 dark:bg-gray-900 dark:text-white"
                    inputmode="numeric" bind:value={reauthTotpCode} placeholder="2단계 인증 코드 (설정한 경우)"/>
                <button class="mt-3 rounded-full bg-amber-700 px-5 py-2.5 text-sm font-bold text-white"
                        on:click={reauthenticate}>확인
                </button>
                {:else}
                    <p class="mt-1 text-sm text-amber-900 dark:text-amber-100">고객님의 개인정보 보호를 위해 로그인 정보를 다시 확인합니다.</p>
                {/if}
                <LoginMethodSelector providerIds={reauthProviderIds} showPasskey={passkeys.length > 0}
                                     {passkeySupported} onPasskey={passkeyReauthenticate}
                                     providerUrl={provider => `/api/account/reauthenticate/oauth/${encodeURIComponent(provider)}`}/>
            </section>
        {/if}
        {#if summary}
            <section
                    class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <label for="account-profile" class="block text-sm font-bold">연결된 게임 프로필</label><select
                    id="account-profile"
                    class="mt-3 w-full rounded-xl border border-slate-300 bg-white p-3 text-sm font-semibold dark:border-gray-600 dark:bg-gray-900"
                    bind:value={selectedProfile} on:change={selectProfile}>
                {#each summary.profiles || [] as profile}
                    <option value={profile.id}>{profile.game_key}
                        · {profile.nickname || profile.legacy_user_id}</option>
                {/each}
            </select>
                <p class="mt-2 text-xs text-slate-500 dark:text-gray-400">회원님의 게임 프로필을 한 눈에 확인할 수 있습니다.</p></section>
            <section
                    class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div class="border-b border-slate-200 px-5 py-4 dark:border-gray-700"><h2 class="font-bold">계정 개요</h2>
                </div>
                <div class="divide-y divide-slate-200 dark:divide-gray-700">
                    <div class="p-5">
                        <div class="flex flex-wrap items-start justify-between gap-3">
                            <div><h3 class="font-bold">계정명</h3>
                                <p class="mt-1 text-sm text-slate-600 dark:text-gray-300">{summary.nickname || '별명 설정 필요'}
                                    {#if summary.nickname_changed_at}<br/>마지막
                                        변경: {new Date(summary.nickname_changed_at).toLocaleString()}{/if}
                                </p></div>
                            {#if nicknamePolicy}<span
                                    class="rounded-full bg-green-50 px-3 py-1 text-xs font-bold text-green-700 dark:bg-green-950 dark:text-green-300">{nicknamePolicy.ping_balance}
                                핑</span>{/if}
                        </div>
                        <div class="mt-4 grid gap-2 sm:grid-cols-[1fr_auto]"><input
                                class="rounded-xl border border-slate-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900"
                                maxlength="15" bind:value={nickname} placeholder="새 별명"/>
                            <button class="rounded-xl bg-[#55aa55] px-4 py-3 text-sm font-bold text-white disabled:opacity-50"
                                    disabled={nicknamePolicy && !nicknamePolicy.can_change} on:click={saveNickname}>변경
                            </button>
                        </div>
                        <label class="mt-3 flex cursor-pointer items-center gap-2 text-sm"><input type="checkbox"
                                                                                                  bind:checked={fixedNickname}/><span>별명 고정</span></label>
                        <p class="mt-2 text-xs leading-5 text-slate-500 dark:text-gray-400">100핑을 사용하여 별명을 고정합니다. 180일 이상 게임에 접속하지 않을 경우 다른 회원이 고정을 해제할 수 있습니다.</p>
                        {#if nicknamePolicy?.game_connected}<p class="mt-2 text-sm text-red-600">게임 접속 중에는 게임 내 프로필 관리 화면에서
                            별명을 변경해 주세요.</p>{:else if nicknamePolicy?.change_restricted}<p
                                class="mt-2 text-sm text-red-600">운영정책 위반으로 별명 변경을 이용할 수 없습니다.</p>{:else if nicknamePolicy && !nicknamePolicy.can_change}<p
                                class="mt-2 text-sm text-red-600">{new Date(nicknamePolicy.next_change_at).toLocaleString()} 이후 별명을 변경할 수 있습니다.</p>{/if}
                    </div>
                    <div class="p-5">
                        <div class="flex items-center justify-between gap-3">
                            <div><h3 class="font-bold">전자 메일 주소</h3>
                                <p class="mt-1 text-sm text-slate-600 dark:text-gray-300">{summary.email || '미등록'}</p>
                            </div>
                            {#if summary.email}
                                <button class="rounded-full border border-red-300 px-4 py-2 text-sm font-bold text-red-700 dark:border-red-900 dark:text-red-300"
                                        on:click={removeEmail}>삭제
                                </button>
                            {/if}
                        </div>
                        <div class="mt-4 grid gap-2 sm:grid-cols-[1fr_auto]"><input
                                class="rounded-xl border border-slate-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900"
                                type="email" placeholder="새 복구 메일 주소" bind:value={email}/>
                            <button class="rounded-xl border border-[#55aa55] px-4 py-3 text-sm font-bold text-[#438c43]"
                                    on:click={addEmail}>인증
                            </button>
                        </div>
                        <p class="mt-2 text-xs text-slate-500 dark:text-gray-400">인증이 완료되면 새 주소로 변경됩니다.</p></div>
                    <div class="grid gap-4 p-5 sm:grid-cols-2">
                        <div><h3 class="font-bold">식별번호</h3>
                            <p class="mt-2 break-all font-mono text-sm text-slate-600 dark:text-gray-300">{summary.legacy_user_id}</p>
                        </div>
                        <div><h3 class="font-bold">연동된 서비스</h3>
                            <p class="mt-2 text-sm text-slate-600 dark:text-gray-300">현재 {summary.linked_services}개의 서비스가 연결되어 있습니다.</p></div>
                    </div>
                </div>
            </section>
            <section
                    class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div class="border-b border-slate-200 px-5 py-4 dark:border-gray-700"><h2 class="font-bold">로그인 수단</h2>
                </div>
                <div class="divide-y divide-slate-200 dark:divide-gray-700">
                    <div class="p-5" hidden={!passwordEnabled}>
                        <div class="flex flex-wrap items-center justify-between gap-3">
                            <div><h3 class="font-bold">비밀번호</h3>
                            <p class="mt-1 text-sm text-slate-500 dark:text-gray-400">안전한 비밀번호를 사용하여 계정을 보호하세요.</p></div>
                            <div class="mt-4 grid gap-2 sm:grid-cols-[1fr_auto]"><input
                                    class="rounded-xl border border-slate-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900"
                                    type="password" minlength="12" placeholder="새 비밀번호 (12자 이상)" bind:value={password}/>
                                <button class="rounded-xl bg-slate-800 px-4 py-3 text-sm font-bold text-white dark:bg-gray-700"
                                        on:click={setPassword}>{identities.some(identity => identity.type === 'PASSWORD') ? '변경' : '설정'}</button>
                            </div>
                    </div>
                    <div class="p-5">
                        <div class="flex flex-wrap items-center justify-between gap-3">
                            <div><h3 class="font-bold">패스키</h3>
                                <p class="mt-1 text-sm text-slate-500 dark:text-gray-400">{passkeys.length}개 등록됨</p>
                            </div>
                            <button class="rounded-full border border-[#55aa55] px-4 py-2 text-sm font-bold text-[#438c43] disabled:opacity-50"
                                    on:click={registerPasskey} disabled={passkeys.length >= 10}>추가
                            </button>
                        </div>
                        {#each passkeys as passkey}
                            <div class="mt-3 flex items-center justify-between gap-3 rounded-xl bg-slate-50 p-3 text-sm dark:bg-gray-900">
                                <span><b>{passkey.device_name}</b><br/><small
                                        class="text-slate-500">{passkey.last_used_at || passkey.created_at}{passkey.recently_used ? ' · 최근 사용' : ''}</small></span>
                                <button class="text-sm font-bold text-red-700"
                                        on:click={() => removePasskey(passkey.id)}>삭제
                                </button>
                            </div>
                        {/each}
                    </div>
                    <div class="p-5">
                        <div class="flex items-center justify-between gap-3">
                            <div><h3 class="font-bold">2단계 인증</h3>
                                <p class="mt-1 text-sm text-slate-500 dark:text-gray-400">{mfa?.totp ? mfa.totp_name || '활성' : '미설정'}</p>
                            </div>
                            {#if mfa?.totp}
                                <button class="rounded-full border border-red-300 px-4 py-2 text-sm font-bold text-red-700"
                                        on:click={removeTotp}>해제
                                </button>
                            {:else}
                                <button class="rounded-full border border-[#55aa55] px-4 py-2 text-sm font-bold text-[#438c43]"
                                        on:click={setupTotp}>추가
                                </button>
                            {/if}
                        </div>
                        {#if mfa?.totp}
                            <div class="mt-4 grid gap-2 sm:grid-cols-[1fr_auto]"><input
                                    class="rounded-xl border border-slate-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900"
                                    maxlength="100" bind:value={totpName} placeholder="TOTP 기기명"/>
                                <button class="rounded-xl border px-4 py-3 text-sm font-bold" on:click={renameTotp}>수정</button>
                            </div>
                        {/if}
                        {#if totpSecret}<p
                                class="mt-3 rounded-xl bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-100">
                            2단계 인증 앱에 아래 비밀키를 등록한 뒤 6자리 인증번호를 입력하세요.<br/><b class="font-mono">{totpSecret}</b></p>{/if}
                    </div>
                    <div class="p-5"><h3 class="font-bold">일회용 비밀번호</h3>
                        <p class="mt-1 text-sm text-slate-500 dark:text-gray-400">남은 코드: {mfa?.one_time_login_codes_remaining || 0}개</p>
                        <button class="mt-3 rounded-full border px-4 py-2 text-sm font-bold"
                                on:click={rotateOneTimeLoginCodes}>발급
                        </button>
                        {#if recoveryCodes.length}<p
                                class="mt-3 rounded-xl bg-amber-50 p-3 font-mono text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-100">{recoveryCodes.join(' ')}</p>{/if}
                    </div>
                </div>
            </section>
            <section
                    class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div class="border-b border-slate-200 px-5 py-4 dark:border-gray-700"><h2 class="font-bold">로그인 수단
                    관리</h2></div>
                <ul class="divide-y divide-slate-200 dark:divide-gray-700">
                    {#each identities as identity}
                        <li class="flex items-center justify-between gap-3 p-5">
                            <span><b>{identity.provider === 'LOCAL' ? '비밀번호' : identity.provider}</b><br/><small
                                    class="text-slate-500">{identity.is_origin ? '기본 로그인 수단' : identity.last_used_at || '연결됨'}</small></span>
                            {#if identity.revocable}
                                <button class="rounded-full border border-red-300 px-4 py-2 text-sm font-bold text-red-700"
                                        on:click={() => revoke(identity)}>연결 해제
                                </button>
                            {:else}<small class="text-slate-500">기본 로그인 수단</small>{/if}
                        </li>
                    {/each}
                </ul>
                <div class="border-t border-slate-200 p-5 dark:border-gray-700"><p class="mb-3 text-sm font-bold">외부 로그인 수단</p><span class="text-sm text-slate-500">로그인 수단 {summary.login_methods}개</span>
                <div class="mt-3 flex flex-wrap gap-2">
                        <button class="rounded-full border px-4 py-2 text-sm font-bold"
                                on:click={() => linkProvider('google')}>Google
                        </button>
                        <button class="rounded-full border px-4 py-2 text-sm font-bold"
                                on:click={() => linkProvider('naver')}>Naver
                        </button>
                        <button class="rounded-full border px-4 py-2 text-sm font-bold"
                                on:click={() => linkProvider('kakao')}>Kakao
                        </button>
                        <button class="rounded-full border px-4 py-2 text-sm font-bold"
                                on:click={() => linkProvider('facebook')}>Facebook
                        </button>
                        <button class="rounded-full border px-4 py-2 text-sm font-bold"
                                on:click={() => linkProvider('discord')}>Discord
                        </button>
                    </div>
                </div>
            </section>
            <section
                    class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div class="border-b border-slate-200 px-5 py-4 dark:border-gray-700"><h2 class="font-bold">지원 · 복구</h2>
                </div>
                <div class="space-y-5 p-5">
                    <div class="flex flex-wrap items-center justify-between gap-3">
                        <div><h3 class="font-bold">지원 PIN</h3>
                            <p class="mt-1 text-sm text-slate-500 dark:text-gray-400">{summary.support_pin_issued_at ? `발급됨 · ${new Date(summary.support_pin_issued_at).toLocaleString()} · ••••••` : '만료됨'}
                                <br/>고객센터 상담 시 고객 확인을 위해 필요합니다.</p></div>
                        <button class="rounded-full bg-slate-800 px-4 py-2 text-sm font-bold text-white dark:bg-gray-700"
                                on:click={issuePin}>발급
                        </button>
                    </div>
                    {#if supportPin}<p class="rounded-xl bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-100">
                        고객님의 지원 PIN은 <b class="font-mono">{supportPin}</b>입니다. 이 창을 닫으면 재확인이 불가능하오니 유의해 주시길 바랍니다.</p>{/if}
                    <div class="border-t border-slate-200 pt-5 dark:border-gray-700"><h3 class="font-bold">보안 코드</h3>
                        <p class="mt-1 text-sm text-slate-500 dark:text-gray-400">계정 복구에 필요한 보안 코드를 확인합니다.</p>
                        <button class="mt-3 rounded-full border px-4 py-2 text-sm font-bold"
                                on:click={revealSecurityCode}>발급
                        </button>
                        {#if securityCode}<p
                                class="mt-3 rounded-xl bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-100">
                            <b class="font-mono">{securityCode}</b></p>{/if}
                    </div>
                </div>
            </section>
        {/if}
    </div>
</main>
