<script>
    import {onMount} from 'svelte';

    let email = '';
    let token = '';
    let password = '';
    let recoveryCode = '';
    let emailLoginToken = '';
    let message = '';
    let reset = false;
    let recoveryCodeMode = false;
    let recaptchaSiteKey = '';
    let passwordEnabled = true;

    function csrfHeaders() {
        const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
        return token ? {'X-XSRF-TOKEN': decodeURIComponent(token)} : {};
    }

    function loadRecaptcha(siteKey) {
        if (window.grecaptcha) return Promise.resolve();
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://www.google.com/recaptcha/api.js?render=' + encodeURIComponent(siteKey);
            script.async = true;
            script.defer = true;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    async function captcha(action) {
        if (!recaptchaSiteKey) throw new Error('captcha_not_configured');
        await loadRecaptcha(recaptchaSiteKey);
        await new Promise(resolve => window.grecaptcha.ready(resolve));
        return window.grecaptcha.execute(recaptchaSiteKey, {action});
    }

    async function submitRequest() {
        try {
            const recaptchaToken = await captcha('account_recovery_request');
            const response = await fetch('/api/account/recovery/request', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({email, recaptchaToken})
            });
            message = response.ok ? '입력하신 전자 메일 주소로 계정이 존재한다면 확인 메일을 발송하였습니다. 메일이 확인되지 않는 경우 몇 분 기다리신 후 스팸메일함을 확인해 주세요.' : '보안 검증을 완료할 수 없습니다.';
        } catch (_) {
            message = '내부 오류가 발생하였습니다.';
        }
    }

    async function submitReset() {
        try {
            const recaptchaToken = await captcha('account_recovery_reset');
            const response = await fetch('/api/account/recovery/reset', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({token, password, recaptchaToken})
            });
            message = response.ok ? '입력하신 비밀번호로 비밀번호를 재설정했습니다. 다시 로그인해 주세요.' : '인증 링크가 유효하지 않습니다. 처음부터 다시 시도해 주세요.';
        } catch (_) {
            message = '내부 오류가 발생하였습니다.';
        }
    }

    async function submitRecoveryCode() {
        try {
            const recaptchaToken = await captcha('account_one_time_login_code');
            const response = await fetch('/api/account/recovery/one-time-login-code', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({code: recoveryCode, recaptchaToken})
            });
            if (response.ok) location.href = '/account';
            else message = '유효하지 않거나 이미 사용된 일회용 비밀번호입니다.';
        } catch (_) {
            message = '내부 오류가 발생하였습니다.';
        }
    }

    async function confirmEmailLogin() {
        try {
            const response = await fetch('/account/recovery/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded', ...csrfHeaders()},
                body: new URLSearchParams({token: emailLoginToken})
            });
            if (response.ok) {
                location.replace(response.redirected ? response.url : '/account');
            } else {
                const error = await response.json().catch(() => ({}));
                message = error.error_description || '로그인 링크가 유효하지 않습니다.';
            }
        } catch (_) {
            message = '내부 오류가 발생하였습니다.';
        }
    }

    onMount(async () => {
        const params = new URLSearchParams(location.search);
        token = params.get('reset') || '';
        emailLoginToken = params.get('token') || '';
        if (emailLoginToken) history.replaceState(null, '', '/account/recovery');
        reset = Boolean(token);
        recoveryCodeMode = params.get('mode') === 'one-time';
        const verify = params.get('verify');
        if (verify) {
            const response = await fetch('/api/account/email/confirm?token=' + encodeURIComponent(verify), {method: 'POST'});
            message = response.ok ? '전자 메일 주소 인증이 완료되었습니다.' : '인증 링크가 유효하지 않습니다. 처음부터 다시 시도해 주세요.';
        }
        await fetch('/api/account/csrf').then(response => response.json()).catch(() => null);
        const config = await fetch('/api/account/recovery/config').then(response => response.ok ? response.json() : null).catch(() => null);
        recaptchaSiteKey = config?.recaptcha_site_key || '';
        passwordEnabled = config?.password_enabled !== false;
        if (!passwordEnabled) {
            reset = false;
        }
    });
</script>
<svelte:head><title>끄투리오 - 계정 복구</title></svelte:head>
<main class="flex min-h-screen items-center justify-center bg-black p-5">
    <section class="w-full max-w-md rounded-lg bg-white p-6"><h1 class="text-2xl font-bold">계정 복구</h1>
        <p class="mt-2 text-sm text-gray-600">등록하신 전자 메일 주소를 입력해 주세요.</p>
        {#if message}<p class="mt-4 rounded bg-gray-100 p-3 text-sm" role="status">{message}</p>{/if}
        {#if emailLoginToken}
            <p class="mt-5 rounded bg-gray-100 p-3 text-sm">로그인을 진행하려면 아래 단추를 눌러 주세요. 로그인 후 계정 관리에서 TOTP 매체를 재발급해 주시길
                바랍니다.</p>
            <button class="mt-3 w-full rounded bg-green-700 p-2 text-white" on:click={confirmEmailLogin}>로그인 계속하기
            </button>
        {:else if recoveryCodeMode}<input class="mt-5 w-full rounded border p-2 font-mono" bind:value={recoveryCode}
                                          placeholder="일회용 비밀번호"/>
            <button class="mt-3 w-full rounded bg-green-700 p-2 text-white" on:click={submitRecoveryCode}>로그인
            </button>
        {:else if passwordEnabled && reset}<input class="mt-5 w-full rounded border p-2" bind:value={token}
                                                  placeholder="복구 토큰"/><input class="mt-3 w-full rounded border p-2"
                                                                              type="password" bind:value={password}
                                                                              placeholder="새 비밀번호 (12자 이상)"/>
            <button class="mt-3 w-full rounded bg-green-700 p-2 text-white" on:click={submitReset}>비밀번호 재설정</button>
        {:else}<input class="mt-5 w-full rounded border p-2" type="email" bind:value={email}
                      placeholder="전자 메일 주소"/>
            <button class="mt-3 w-full rounded bg-green-700 p-2 text-white"
                    on:click={submitRequest}>{passwordEnabled ? '인증 메일 보내기' : '로그인 링크 보내기'}</button>
        {/if}
        <div class="mt-4 flex gap-3 text-sm underline">
            {#if passwordEnabled}
                <button on:click={() => { reset = !reset; recoveryCodeMode = false; }}>{reset ? '뒤로' : '일회용 비밀번호가 있나요?'}</button>
            {/if}
            <button on:click={() => { recoveryCodeMode = !recoveryCodeMode; reset = false; }}>{recoveryCodeMode ? '전자 메일 주소 사용' : '일회용 비밀번호 사용'}</button>
        </div>
    </section>
</main>
