<script>
    import {onMount, tick} from 'svelte';

    let digits = $state(['', '', '', '', '', '']);
    let inputs = $state([]);
    let securityCode = $state('');
    let emailCode = $state('');
    let useSecurityCode = $state(false);
    let useEmailBackup = $state(false);
    let emailBackupAvailable = $state(false);
    let error = $state('');
    let notice = $state('');
    let ready = $state(false);
    let submitting = $state(false);

    let totpCode = $derived(digits.join(''));

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
        const value = event.currentTarget.value.replace(/\D/g, '');
        if (value.length > 1) {
            const nextDigits = [...digits];
            value.slice(0, digits.length - index).split('').forEach((digit, offset) => {
                nextDigits[index + offset] = digit;
            });
            digits = nextDigits;
            await tick();
            inputs[Math.min(index + value.length, digits.length - 1)]?.focus();
            return;
        }
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
    <div class="flex justify-center px-4 py-10 sm:py-16">
        <section class="kk-card w-full max-w-md p-6 text-center sm:p-8">
            <span class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-(--kk-accent-soft) text-(color:--kk-accent-strong)">
                <span class="material-symbols-outlined" style="font-size:36px" aria-hidden="true">shield_lock</span>
            </span>
            {#if useSecurityCode}
                <h1 class="mt-5 text-xl font-extrabold leading-snug [word-break:keep-all]">계정 보안코드를 입력하세요</h1>
                <input class="kk-input mt-6 h-12 w-full px-4 text-center text-lg tracking-wide"
                       bind:value={securityCode} autocomplete="one-time-code" placeholder="계정 보안코드 입력" aria-label="계정 보안코드" onkeydown={(event) => event.key === 'Enter' && verify()}/>
            {:else if useEmailBackup}
                <h1 class="mt-5 text-xl font-extrabold leading-snug [word-break:keep-all]">전자 메일 인증 코드를 입력하세요</h1>
                <p class="mt-2 text-sm text-(color:--kk-muted)">인증된 전자 메일 주소로 보낸 인증 코드를 입력해 주세요.</p>
                <input class="kk-input mt-6 h-12 w-full px-4 text-center text-lg tracking-wide"
                       bind:value={emailCode} autocomplete="one-time-code" placeholder="전자 메일 인증 코드" aria-label="전자 메일 인증 코드" onkeydown={(event) => event.key === 'Enter' && verify()}/>
            {:else}
                <h1 class="mt-5 text-xl font-extrabold leading-snug [word-break:keep-all]">인증 앱의 6자리 코드를 입력하세요</h1>
                <div class="mt-6 flex justify-center gap-2">
                    {#each digits as digit, index}
                        <input class="kk-input h-14 w-11 px-0 text-center text-2xl font-extrabold sm:w-12"
                               bind:this={inputs[index]} value={digit} inputmode="numeric" autocomplete={index === 0 ? 'one-time-code' : 'off'} maxlength="6" aria-label={`${index + 1}번째 인증 숫자`}
                               oninput={(event) => setDigit(index, event)} onkeydown={(event) => handleKeydown(index, event)}/>
                    {/each}
                </div>
            {/if}
            {#if error}<p class="mt-4 flex items-start justify-center gap-1.5 rounded-xl bg-(--kk-danger-soft) px-3 py-2 text-sm font-bold text-(color:--kk-danger)" role="alert"><span class="material-symbols-outlined shrink-0" style="font-size:18px" aria-hidden="true">error</span>{error}</p>{/if}
            {#if notice}<p class="mt-4 rounded-xl bg-(--kk-accent-soft) px-3 py-2 text-sm font-bold text-(color:--kk-accent-strong)" role="status">{notice}</p>{/if}
            <button class="kk-btn kk-btn-primary mt-6 flex h-12 w-full items-center justify-center text-base" disabled={submitting} onclick={verify}>{submitting ? '확인 중…' : '인증하기'}</button>
            <div class="mt-5 flex flex-col items-center gap-1 text-sm">
                {#if useSecurityCode || useEmailBackup}
                    <button class="inline-flex rounded-lg px-1 font-bold text-(color:--kk-accent-strong) underline underline-offset-4 hover:bg-(--kk-accent-soft)" onclick={useTotp}>2단계 인증 앱 사용</button>
                {:else}
                    {#if emailBackupAvailable}<button class="inline-flex rounded-lg px-1 font-bold text-(color:--kk-accent-strong) underline underline-offset-4 hover:bg-(--kk-accent-soft)" onclick={requestEmailBackup}>대신 전자 메일 주소로 인증하기</button>{/if}
                    <button class="inline-flex rounded-lg px-1 font-bold text-(color:--kk-accent-strong) underline underline-offset-4 hover:bg-(--kk-accent-soft)" onclick={() => useSecurityCode = true}>인증 앱을 사용할 수 없나요? 보안코드 사용</button>
                {/if}
            </div>
            <p class="mt-6 text-sm"><a class="font-bold text-(color:--kk-muted) hover:text-(color:--kk-text)" href="/login">← 처음으로 돌아가기</a></p>
        </section>
    </div>
{/if}
