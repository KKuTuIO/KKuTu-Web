<script nonce="kkutuio">
    import {onMount} from 'svelte';
    import LoginMethodSelector from '$lib/LoginMethodSelector.svelte';

    const title = '로그인';

    var loginReason = "";
    var passkeySupported = false;
    var identifier = '';
    var password = '';
    var passwordEnabled = false;
    var loading = false;

    async function loadLoginInfo() {
        loading = true;
        try {
            const response = await fetch('/api/login/reason');
            loginReason = await response.text();
            const config = await fetch('/api/account/recovery/config').then(response => response.ok ? response.json() : null).catch(() => null);
            passwordEnabled = config?.password_enabled !== false;
        } finally {
            loading = false;
        }
    }

    onMount(() => {
        passkeySupported = !!window.PublicKeyCredential;
        loadLoginInfo();
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
<div class="dark:bg-gray-900 flex min-h-screen flex-col justify-center px-6 py-12 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-sm">
        <div class="mt-10 flex items-center justify-center gap-2"><h2 class="text-center text-3xl font-bold leading-9 tracking-tight text-gray-900 dark:text-gray-100">로그인</h2><button class="grid h-9 w-9 place-items-center rounded-full text-gray-500 transition hover:bg-gray-100 disabled:opacity-50 dark:hover:bg-gray-800" on:click={loadLoginInfo} disabled={loading} aria-label="새로고침"><span class:animate-spin={loading} class="material-symbols-outlined">{loading ? 'progress_activity' : 'refresh'}</span></button></div>
    </div>

    <div class="mt-4 sm:mx-auto sm:w-full sm:max-w-sm">
        <p class="text-center text-gray-500 dark:text-gray-300">
            {loginReason}
        </p>
    </div>

    <div class="mt-4 sm:mx-auto sm:w-full ">
        <div class="sm:mx-auto sm:sm:max-w-sm">
            {#if passwordEnabled}
                <input class="mt-4 w-full border p-3 text-black" bind:value={identifier}
                                        placeholder="전자 메일 주소 또는 식별번호" autocomplete="username"/>
                <input class="mt-2 w-full border p-3 text-black" bind:value={password} type="password"
                       placeholder="비밀번호" autocomplete="current-password"/>
                <button class="text-lg bg-[#55aa55] text-white mt-2 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md"
                        on:click={passwordLogin}>로그인
                </button>
            {/if}
            <LoginMethodSelector {passkeySupported} onPasskey={passkeyLogin}/>
        </div>
        <p class="mt-10 text-center text-sm text-gray-500 dark:text-gray-300">
            계정 정보를 분실하셨나요? <a href="/account/recovery" class="link-signin">계정 복구하기</a><br/>
            로그인하면 끄투리오의 <a href="https://cs.kkutu.io/terms" target="_blank" rel="noopener"
                                     class="link-signin">서비스 이용약관</a>과 <a href="https://cs.kkutu.io/operation"
                                                                          target="_blank" rel="noopener"
                                                                          class="link-signin">운영정책</a>, <a
                href="https://cs.kkutu.io/privacy-policy" target="_blank" rel="noopener"
                class="link-signin">개인정보처리방침</a>에 동의하는 것으로 봅니다.
        </p>
    </div>
</div>
