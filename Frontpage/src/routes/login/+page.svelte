<script>
    import {onMount} from 'svelte';
    import LoginMethodSelector from '$lib/LoginMethodSelector.svelte';

    const title = '로그인';

    var loginReason = $state("");
    var passkeySupported = $state(false);
    var identifier = $state('');
    var password = $state('');
    var passwordEnabled = $state(false);
    // Login providers come from the server (/api/login/providers). If that call fails we show every known provider
    // rather than leave the page without a way in.
    var providersReady = $state(false);
    var providerIds = $state(undefined);
    var restrictedIds = $state([]);

    async function loadLoginInfo() {
        try {
            const response = await fetch('/api/login/reason');
            loginReason = await response.text();
            const config = await fetch('/api/account/recovery/config').then(response => response.ok ? response.json() : null).catch(() => null);
            passwordEnabled = config?.password_enabled !== false;
        } catch (_) {}
    }

    async function loadProviders() {
        const list = await fetch('/api/login/providers').then(response => response.ok ? response.json() : null).catch(() => null);
        if (Array.isArray(list)) {
            providerIds = list.filter(provider => provider.allow_register).map(provider => provider.id);
            restrictedIds = list.filter(provider => !provider.allow_register).map(provider => provider.id);
        }
        providersReady = true;
    }

    onMount(() => {
        passkeySupported = !!window.PublicKeyCredential;
        loadLoginInfo();
        loadProviders();
    });

    async function passkeyLogin() {
        if (!window.PublicKeyCredential) {
            loginReason = '이 브라우저는 패스키를 지원하지 않습니다.';
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
            location.href = '/';
        } catch (_) {
            loginReason = '패스키 로그인에 실패하였습니다. 다시 시도해 주세요.';
        }
    }

    async function passwordLogin() {
        const response = await fetch('/api/account/password/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({identifier, password})
        });
        if (response.status === 202) {
            location.href = '/login/mfa';
            return;
        }
        if (response.ok) {
            location.href = '/';
            return;
        }
        const error = await response.json().catch(() => ({}));
        loginReason = error.error_description || '로그인 정보가 올바르지 않습니다.';
    }
</script>

<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>
<div class="flex justify-center px-4 py-10 sm:py-16">
    <div class="kk-card w-full max-w-md p-6 sm:p-8">
        <h1 class="text-center text-2xl font-extrabold">로그인</h1>

        {#if loginReason}
            <p class="mt-4 flex items-start gap-2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface-2) px-4 py-3 text-sm font-bold" role="status">
                <span class="material-symbols-outlined shrink-0 text-(color:--kk-accent-strong)" style="font-size:20px" aria-hidden="true">info</span>
                <span class="min-w-0 break-words">{loginReason}</span>
            </p>
        {/if}

        <div class="mt-5">
            {#if passwordEnabled}
                <input class="kk-input h-12 w-full px-4" bind:value={identifier} aria-label="전자 메일 주소 또는 식별번호"
                       placeholder="전자 메일 주소 또는 식별번호" autocomplete="username"/>
                <input class="kk-input mt-2 h-12 w-full px-4" bind:value={password} type="password" aria-label="비밀번호"
                       placeholder="비밀번호" autocomplete="current-password"/>
                <button class="kk-btn kk-btn-primary mt-3 flex h-12 w-full items-center justify-center text-base" onclick={passwordLogin}>로그인</button>
                <div class="my-5 flex items-center gap-3 text-xs font-bold text-(color:--kk-muted)" aria-hidden="true">
                    <span class="h-px flex-1 bg-(--kk-line)"></span>또는<span class="h-px flex-1 bg-(--kk-line)"></span>
                </div>
            {/if}
            {#if providersReady}
                <LoginMethodSelector variant="icons" {providerIds} {restrictedIds} {passkeySupported} onPasskey={passkeyLogin}/>
            {/if}
        </div>

        <p class="mt-6 text-center text-sm leading-6 text-(color:--kk-muted)">
            계정 정보를 분실하셨나요? <a href="/account/recovery" class="whitespace-nowrap font-bold text-(color:--kk-accent-strong) hover:underline">계정 복구하기</a><br/>
            로그인하면 끄투리오의 <a href="https://cs.kkutu.io/terms" target="_blank" rel="noopener" class="whitespace-nowrap font-bold text-(color:--kk-accent-strong) hover:underline">서비스 이용약관</a>과
            <a href="https://cs.kkutu.io/operation" target="_blank" rel="noopener" class="whitespace-nowrap font-bold text-(color:--kk-accent-strong) hover:underline">운영정책</a>,
            <a href="https://cs.kkutu.io/privacy-policy" target="_blank" rel="noopener" class="whitespace-nowrap font-bold text-(color:--kk-accent-strong) hover:underline">개인정보처리방침</a>에 동의하는 것으로 봅니다.
        </p>
    </div>
</div>
