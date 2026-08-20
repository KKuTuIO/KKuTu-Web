<script>
    import {onMount} from 'svelte';

    let email = '';
    let token = '';
    let password = '';
    let recoveryIdentifier = '';
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
        if (!recoveryIdentifier.trim() || !recoveryCode.trim()) {
            message = '식별번호 또는 전자 메일 주소와 일회용 비밀번호를 입력해 주세요.';
            return;
        }
        try {
            const recaptchaToken = await captcha('account_one_time_login_code');
            const response = await fetch('/api/account/recovery/one-time-login-code', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({identifier: recoveryIdentifier, code: recoveryCode, recaptchaToken})
            });
            if (response.ok) location.href = '/account';
            else {
                const error = await response.json().catch(() => ({}));
                message = error.error_description || '입력하신 정보가 이미 사용되었거나 올바르지 않습니다.';
            }
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
<main class="min-h-screen bg-gray-100 px-4 pb-16 pt-24 text-gray-900 dark:bg-gray-900 dark:text-gray-100 sm:px-6">
    <section
            class="mx-auto w-full max-w-lg overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xl shadow-slate-900/10 dark:border-gray-700 dark:bg-gray-800">
        <header class="flex items-center gap-3 border-b border-gray-200 px-6 py-5 dark:border-gray-700">
            <div class="grid h-11 w-11 place-items-center rounded-xl bg-[#e7f3e7] text-[#438c43] dark:bg-green-950">
                <span class="material-symbols-outlined">key</span></div>
            <div><h1 class="text-2xl font-bold">계정 복구</h1>
                <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-300">복구 수단을 선택해 계속하세요.</p></div>
        </header>
        <div class="p-6">
            {#if message}<p
                    class="mb-5 rounded-xl border border-sky-200 bg-sky-50 p-4 text-sm leading-6 text-sky-900 dark:border-sky-900 dark:bg-sky-950 dark:text-sky-100"
                    role="status">{message}</p>{/if}
            {#if emailLoginToken}
                <p class="rounded-xl bg-gray-50 p-4 text-sm leading-6 text-gray-600 dark:bg-gray-900 dark:text-gray-300">
                    로그인 후 계정 관리에서 필요한 로그인 수단을 다시 연동할 수 있습니다.</p>
                <button class="mt-4 w-full rounded-xl bg-[#55aa55] px-4 py-3 font-bold text-white transition hover:bg-[#438c43]"
                        on:click={confirmEmailLogin}>로그인 계속하기
                </button>
            {:else if recoveryCodeMode}
                <label class="block text-sm font-bold" for="recovery-identifier">식별번호 또는 전자 메일 주소</label><input
                    id="recovery-identifier"
                    class="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900"
                    bind:value={recoveryIdentifier} placeholder="식별번호 또는 전자 메일 주소" autocomplete="username"/>
                <label class="mt-4 block text-sm font-bold" for="recovery-code">일회용 비밀번호</label><input
                    id="recovery-code"
                    class="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3 font-mono dark:border-gray-600 dark:bg-gray-900"
                    bind:value={recoveryCode} placeholder="일회용 비밀번호" autocomplete="one-time-code"/>
                <button class="mt-4 w-full rounded-xl bg-[#55aa55] px-4 py-3 font-bold text-white transition hover:bg-[#438c43]"
                        on:click={submitRecoveryCode}>로그인
                </button>
            {:else if passwordEnabled && reset}
                <label class="block text-sm font-bold" for="reset-token">복구 토큰</label><input id="reset-token"
                                                                                             class="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900"
                                                                                             bind:value={token}
                                                                                             placeholder="복구 토큰"/><label
                    class="mt-4 block text-sm font-bold" for="reset-password">새 비밀번호</label><input id="reset-password"
                                                                                                   class="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900"
                                                                                                   type="password"
                                                                                                   bind:value={password}
                                                                                                   placeholder="새 비밀번호 (12자 이상)"
                                                                                                   autocomplete="new-password"/>
                <button class="mt-4 w-full rounded-xl bg-[#55aa55] px-4 py-3 font-bold text-white transition hover:bg-[#438c43]"
                        on:click={submitReset}>비밀번호 재설정
                </button>
            {:else}
                <label class="block text-sm font-bold" for="recovery-email">전자 메일 주소</label><input id="recovery-email"
                                                                                                   class="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3 dark:border-gray-600 dark:bg-gray-900"
                                                                                                   type="email"
                                                                                                   bind:value={email}
                                                                                                   placeholder="전자 메일 주소"
                                                                                                   autocomplete="email"/>
                <button class="mt-4 w-full rounded-xl bg-[#55aa55] px-4 py-3 font-bold text-white transition hover:bg-[#438c43]"
                        on:click={submitRequest}>{passwordEnabled ? '인증 메일 보내기' : '로그인 링크 보내기'}</button>
            {/if}
            <div class="mt-5 flex flex-wrap gap-x-4 gap-y-2 text-sm font-semibold text-[#438c43] underline underline-offset-4">
                {#if passwordEnabled}
                    <button on:click={() => { reset = !reset; recoveryCodeMode = false; }}>{reset ? '뒤로' : '일회용 비밀번호가 있나요?'}</button>
                {/if}
                <button on:click={() => { recoveryCodeMode = !recoveryCodeMode; reset = false; }}>{recoveryCodeMode ? '전자 메일 주소 사용' : '일회용 비밀번호 사용'}</button>
            </div>
        </div>
    </section>
</main>
