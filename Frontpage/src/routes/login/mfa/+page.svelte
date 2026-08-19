<script>
    import {onMount, tick} from 'svelte';

    let digits = ['', '', '', '', '', ''];
    let inputs = [];
    let securityCode = '';
    let emailCode = '';
    let useSecurityCode = false;
    let useEmailBackup = false;
    let emailBackupAvailable = false;
    let error = '';
    let notice = '';
    let ready = false;
    let submitting = false;

    $: totpCode = digits.join('');

    function csrfHeaders() {
        const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
        return token ? {'X-XSRF-TOKEN': decodeURIComponent(token)} : {};
    }

    onMount(async () => {
        const [response, csrfResponse] = await Promise.all([fetch('/api/account/login/mfa'), fetch('/api/account/csrf')]);
        const status = await response.json().catch(() => ({}));
        if (!response.ok || !status.pending) {
            location.replace('/login');
            return;
        }
        emailBackupAvailable = status.email_backup_available === true;
        await csrfResponse.json().catch(() => ({}));
        ready = true;
        await tick();
        inputs[0]?.focus();
    });

    async function setDigit(index, event) {
        const value = event.currentTarget.value.replace(/\D/g, '').slice(-1);
        digits[index] = value;
        digits = [...digits];
        if (value && index < digits.length - 1) {
            await tick();
            inputs[index + 1]?.focus();
        }
    }

    async function handleKeydown(index, event) {
        if (event.key === 'Backspace' && !digits[index] && index > 0) {
            digits[index - 1] = '';
            digits = [...digits];
            await tick();
            inputs[index - 1]?.focus();
        }
    }

    async function verify() {
        error = '';
        if (!useSecurityCode && !useEmailBackup && totpCode.length !== 6) {
            error = '인증 앱의 6자리 코드를 입력해 주세요.';
            return;
        }
        if (useSecurityCode && !securityCode.trim()) {
            error = '계정 보안코드를 입력해 주세요.';
            return;
        }
        if (useEmailBackup && !emailCode.trim()) {
            error = '전자 메일로 받은 인증 코드를 입력해 주세요.';
            return;
        }
        submitting = true;
        try {
            const response = await fetch('/api/account/login/mfa', {
                method: 'POST',
                headers: {'Content-Type': 'application/json', ...csrfHeaders()},
                body: JSON.stringify(useSecurityCode
                    ? {securityCode: securityCode.trim()}
                    : useEmailBackup ? {emailCode: emailCode.trim()} : {totpCode})
            });
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                error = body.error_description || '인증 코드를 확인할 수 없습니다.';
                return;
            }
            const result = await response.json().catch(() => ({}));
            location.replace(result.redirect || '/');
        } finally {
            submitting = false;
        }
    }

    async function useTotp() {
        useSecurityCode = false;
        useEmailBackup = false;
        await tick();
        inputs[0]?.focus();
    }

    async function requestEmailBackup() {
        error = '';
        notice = '';
        const response = await fetch('/api/account/login/mfa/email', {method: 'POST', headers: csrfHeaders()});
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            error = body.error_description || '전자 메일 인증 코드를 보낼 수 없습니다.';
            return;
        }
        useSecurityCode = false;
        useEmailBackup = true;
        notice = '인증된 전자 메일 주소로 인증 코드를 보냈습니다.';
    }
</script>

<svelte:head>
    <title>끄투리오 - 2단계 인증</title>
</svelte:head>

{#if ready}
    <section class="mx-auto flex min-h-[calc(100vh-13rem)] max-w-2xl items-center px-5 py-16 sm:px-8">
        <div class="w-full text-center">
            <div class="mx-auto grid h-20 w-20 place-items-center rounded-full bg-[#e8f5e9] text-[#438c43] dark:bg-green-950 dark:text-[#8bcc8d]">
                <span class="material-symbols-outlined text-4xl" aria-hidden="true">shield_lock</span>
            </div>
            {#if useSecurityCode}
                <h1 class="mt-10 text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white sm:text-5xl">계정 보안코드를 입력하세요</h1>
                <input class="mx-auto mt-10 block w-full max-w-md rounded-2xl border border-slate-300 bg-white px-5 py-4 text-center text-lg tracking-wide text-slate-900 outline-none transition focus:border-[#55aa55] focus:ring-4 focus:ring-green-100 dark:border-gray-600 dark:bg-gray-900 dark:text-white dark:focus:ring-green-950"
                       bind:value={securityCode} autocomplete="one-time-code" placeholder="계정 보안코드 입력" on:keydown={(event) => event.key === 'Enter' && verify()}/>
            {:else if useEmailBackup}
                <h1 class="mt-10 text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white sm:text-5xl">전자 메일 인증 코드를 입력하세요</h1>
                <p class="mx-auto mt-5 max-w-xl text-base leading-7 text-slate-600 dark:text-slate-300 sm:text-lg">인증된 전자 메일 주소로 보낸 인증 코드를 입력해 주세요.</p>
                <input class="mx-auto mt-10 block w-full max-w-md rounded-2xl border border-slate-300 bg-white px-5 py-4 text-center text-lg tracking-wide text-slate-900 outline-none transition focus:border-[#55aa55] focus:ring-4 focus:ring-green-100 dark:border-gray-600 dark:bg-gray-900 dark:text-white dark:focus:ring-green-950"
                       bind:value={emailCode} autocomplete="one-time-code" placeholder="전자 메일 인증 코드" on:keydown={(event) => event.key === 'Enter' && verify()}/>
            {:else}
                <h1 class="mt-10 text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white sm:text-5xl">인증 앱의 6자리 코드를 입력하세요</h1>
                <div class="mx-auto mt-10 flex max-w-md justify-center gap-2 sm:gap-3">
                    {#each digits as digit, index}
                        <input class="h-14 w-11 rounded-xl border border-slate-300 bg-white text-center text-2xl font-bold text-slate-900 outline-none transition focus:border-[#55aa55] focus:ring-4 focus:ring-green-100 dark:border-gray-600 dark:bg-gray-900 dark:text-white dark:focus:ring-green-950 sm:h-16 sm:w-14"
                               bind:this={inputs[index]} value={digit} inputmode="numeric" autocomplete={index === 0 ? 'one-time-code' : 'off'} maxlength="1" aria-label={`${index + 1}번째 인증 숫자`}
                               on:input={(event) => setDigit(index, event)} on:keydown={(event) => handleKeydown(index, event)}/>
                    {/each}
                </div>
            {/if}
            {#if error}<p class="mt-5 text-sm font-medium text-red-600 dark:text-red-400" role="alert">{error}</p>{/if}
            {#if notice}<p class="mt-5 text-sm font-medium text-[#438c43]" role="status">{notice}</p>{/if}
            <div class="mt-8 text-sm">
                {#if useSecurityCode || useEmailBackup}
                    <button class="font-semibold text-[#438c43] underline underline-offset-4" on:click={useTotp}>2단계 인증 앱 사용</button>
                {:else}
                    {#if emailBackupAvailable}<button class="font-semibold text-[#438c43] underline underline-offset-4" on:click={requestEmailBackup}>대신 전자 메일 주소로 인증하기</button><span class="mx-2 text-slate-300">·</span>{/if}
                    <button class="font-semibold text-[#438c43] underline underline-offset-4" on:click={() => useSecurityCode = true}>인증 앱을 사용할 수 없나요? 보안코드 사용</button>
                {/if}
            </div>
            <button class="mt-10 min-w-[220px] rounded-full bg-[#55aa55] px-8 py-4 text-lg font-bold text-white shadow-md transition hover:bg-[#438c43] active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={submitting} on:click={verify}>{submitting ? '확인 중…' : '인증하기'}</button>
            <p class="mt-12"><a class="font-semibold text-[#438c43] hover:underline" href="/login">← 처음으로 돌아가기</a></p>
        </div>
    </section>
{/if}
