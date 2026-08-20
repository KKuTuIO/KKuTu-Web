<script>
    import {onMount} from 'svelte';
    import AccountModal from '$lib/AccountModal.svelte';
    import LoginMethodSelector from '$lib/LoginMethodSelector.svelte';

    export let mode = 'profile';

    let summary = null;
    let applications = [];
    let profile = null;
    let loading = true;
    let submitting = false;
    let consentService = false;
    let consentIrreversible = false;
    let modalOpen = false;
    let confirmationOpen = false;
    let confirmationInput = '';
    let reauthDialogOpen = false;
    let reauthPassword = '';
    let reauthTotpCode = '';
    let reauthMfaRequired = false;
    let reauthProviderIds = [];
    let passwordEnabled = true;
    let reauthAction = '';
    let message = '';
    let error = '';

    $: isProfile = mode === 'profile';
    $: title = isProfile ? '프로필 삭제' : '계정 탈퇴';
    $: actionLabel = isProfile ? '프로필 삭제 신청' : '계정 탈퇴 신청';
    $: scheduledAt = isProfile ? profile?.deletion_scheduled_at : summary?.deletion_scheduled_at;
    $: pending = Boolean(scheduledAt);
    $: activeProfiles = (summary?.profiles || []).filter(item => item.status === 'ACTIVE' && !item.deletion_scheduled_at);
    $: accountBlockedByProfiles = !isProfile && activeProfiles.length > 0;
    $: accountRestricted = Boolean(summary?.account_restricted);
    $: canSubmit = consentService && consentIrreversible && !submitting && !accountBlockedByProfiles && !accountRestricted;
    $: deletionSubject = isProfile ? profile : summary?.profiles?.find(item => String(item.id) === String(summary?.selected_profile_id)) || summary?.profiles?.[0];
    $: originalName = deletionSubject?.nickname || summary?.nickname || deletionSubject?.id || summary?.uuid || '현재 계정';
    $: deletionTag = deletionSubject?.nickname_tag || '';
    $: confirmationPhrase = `${originalName} ${isProfile ? '계정 탈퇴' : '프로필 삭제'}`;
    $: deletionAlias = `${isProfile ? '계정삭제대기' : '계정탈퇴대기'}${deletionTag ? `#${deletionTag}` : ''}`;

    function csrfHeaders() {
        const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
        return token ? {'X-XSRF-TOKEN': decodeURIComponent(token)} : {};
    }

    function formatDate(value) {
        if (!value) return '';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('ko-KR', {dateStyle: 'medium', timeStyle: 'short'});
    }

    function formatDeletionDate(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
    }

    function profileFromQuery(profiles) {
        const requested = new URLSearchParams(location.search).get('profile_id');
        return profiles.find(item => requested && String(item.id) === requested)
            || profiles.find(item => String(item.id) === String(summary?.selected_profile_id))
            || profiles.find(item => item.status === 'ACTIVE');
    }

    async function load() {
        loading = true;
        error = '';
        try {
            await fetch('/api/account/csrf');
            const [summaryResponse, appsResponse, identityResponse] = await Promise.all([
                fetch('/api/account/summary'),
                fetch('/api/account/connected-applications'),
                fetch('/api/account/identities')
            ]);
            if (summaryResponse.status === 401) {
                location.href = '/login';
                return;
            }
            if (!summaryResponse.ok || !appsResponse.ok || !identityResponse.ok) throw new Error('load_failed');
            summary = await summaryResponse.json();
            applications = await appsResponse.json();
            const identities = await identityResponse.json();
            passwordEnabled = summary.password_enabled !== false;
            reauthProviderIds = identities.filter(item => item.type === 'OAUTH' && item.provider_id).map(item => item.provider_id);
            profile = isProfile ? profileFromQuery(summary.profiles || []) : null;
            if (isProfile && !profile) error = '삭제할 프로필을 찾을 수 없습니다.';
        } catch (_) {
            error = '계정 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            loading = false;
        }
    }

    function requestReauthentication(action) {
        reauthAction = action;
        reauthDialogOpen = true;
    }

    function closeReauthentication() {
        reauthDialogOpen = false;
        reauthPassword = '';
        reauthTotpCode = '';
        reauthMfaRequired = false;
        reauthAction = '';
    }

    async function reauthenticate() {
        try {
            const response = await fetch('/api/account/reauthenticate/strong', {
                method: 'POST',
                headers: {'Content-Type': 'application/json', ...csrfHeaders()},
                body: JSON.stringify({password: reauthPassword, totpCode: reauthTotpCode || null})
            });
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                reauthMfaRequired = body.error === 'mfa_required';
                error = body.error_description || '본인 확인에 실패했습니다.';
                return;
            }
            const action = reauthAction;
            closeReauthentication();
            if (action === 'confirm') openConfirmation();
            if (action === 'post') await requestDeletion(true);
            if (action === 'cancel') await cancelDeletion(true);
        } catch (_) {
            error = '본인 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.';
        }
    }

    function providerUrl(provider) {
        const returnUrl = isProfile
            ? `/account/profile-delete?profile_id=${encodeURIComponent(profile?.id || '')}`
            : '/account/delete';
        return `/api/account/reauthenticate/oauth/${encodeURIComponent(provider)}/strong?return=${encodeURIComponent(returnUrl)}`;
    }

    async function cancelDeletion(retry = false) {
        if (!pending || submitting) return;
        submitting = true;
        error = '';
        try {
            await fetch('/api/account/csrf');
            const endpoint = isProfile ? `/api/account/profile/${encodeURIComponent(profile.id)}/deletion` : '/api/account/deletion';
            const response = await fetch(endpoint, {method: 'DELETE', headers: csrfHeaders()});
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                if (body.error === 'reauthentication_required' && !retry) {
                    requestReauthentication('cancel');
                    return;
                }
                error = body.error_description || '삭제 신청을 해제하지 못했습니다.';
                return;
            }
            if (isProfile) profile = {...profile, deletion_requested_at: null, deletion_scheduled_at: null};
            else summary = {...summary, deletion_requested_at: null, deletion_scheduled_at: null};
            message = `${title} 신청을 해제했습니다.`;
        } catch (_) {
            error = '삭제 신청을 해제하지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            submitting = false;
        }
    }

    async function submit() {
        if (applications.length) {
            modalOpen = true;
            return;
        }
        if (!canSubmit) return;
        error = '';
        try {
            const response = await fetch('/api/account/reauthentication/strong/status');
            const status = await response.json().catch(() => ({}));
            if (status.required) requestReauthentication('confirm');
            else openConfirmation();
        } catch (_) {
            error = '본인인증 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.';
        }
    }

    function openConfirmation() {
        if (!canSubmit) return;
        confirmationInput = '';
        error = '';
        confirmationOpen = true;
    }

    async function confirmDeletion() {
        if (confirmationInput.trim() !== confirmationPhrase) {
            error = `입력한 문구가 일치하지 않습니다. “${confirmationPhrase}”를 정확히 입력해 주세요.`;
            return;
        }
        confirmationOpen = false;
        await requestDeletion();
    }

    async function requestDeletion(retry = false) {
        submitting = true;
        error = '';
        try {
            await fetch('/api/account/csrf');
            const endpoint = isProfile ? `/api/account/profile/${encodeURIComponent(profile.id)}/deletion` : '/api/account/deletion';
            const response = await fetch(endpoint, {method: 'POST', headers: csrfHeaders()});
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                if (body.error === 'reauthentication_required' && !retry) {
                    requestReauthentication('post');
                    return;
                }
                error = body.error_description || `${title} 신청을 처리하지 못했습니다.`;
                return;
            }
            const result = await response.json();
            if (isProfile) profile = {...profile, deletion_requested_at: new Date().toISOString(), deletion_scheduled_at: result.scheduled_at};
            else summary = {...summary, deletion_requested_at: new Date().toISOString(), deletion_scheduled_at: result.scheduled_at};
            message = `${formatDate(result.scheduled_at)}에 ${isProfile ? '프로필이' : '계정이'} 삭제됩니다.`;
        } catch (_) {
            error = `${title} 신청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.`;
        } finally {
            submitting = false;
        }
    }

    onMount(load);
</script>

<svelte:head><title>끄투리오 - {title}</title></svelte:head>

<main class="deletion-page">
    <div class="account-shell">
        <a href="/account" class="back-link"><span class="material-symbols-outlined">arrow_back</span><span>계정 관리</span></a>
        <header class="page-intro">
            <p class="eyebrow">계정 및 보안</p>
            <h1>{title}</h1>
            <p class="lead">{isProfile ? '프로필을 삭제하시기 전 확인해 주세요.' : '계정을 탈퇴하시기 전 확인해 주세요.'}</p>
        </header>
    </div>

    <section class="deletion-card account-shell" aria-busy={loading}>
        <section class="section-block">
            <h2>이용 중인 앱</h2>
            <p class="section-description">끄투리오 계정으로 이용 중인 앱별 정책에 따라 {isProfile ? '프로필 삭제' : '계정 탈퇴'}가 불가능할 수 있습니다.</p>
            {#if applications.length}
                <div class="application-list">
                    {#each applications as application}
                        <a class="application-card" href="/account/apps">
                            <div>
                                <strong>{application.client_name}</strong>
                                <p>서비스: {application.first_party ? '끄투리오' : '연결된 앱'}</p>
                            </div>
                            <span class="application-arrow material-symbols-outlined">chevron_right</span>
                        </a>
                    {/each}
                </div>
            {:else}
                <div class="empty-apps">현재 연결된 앱이 없습니다.</div>
            {/if}
        </section>

        <section class="section-block notice-block">
            <h2>유의 사항</h2>
            {#if pending}
                <div class="pending-notice">
                    <strong>{formatDate(scheduledAt)}에 {isProfile ? '프로필이' : '계정이'} 삭제됩니다.</strong>
                    <p>삭제 유예기간에는 서비스를 이용할 수 없습니다. 삭제를 원하지 않으면 아래에서 신청을 해제하세요.</p>
                    <button class="cancel-button" on:click={cancelDeletion} disabled={submitting || accountRestricted}>삭제 신청 해제</button>
                </div>
            {:else}
                {#if accountRestricted}
                    <div class="blocking-notice">이용제한된 계정은 프로필 추가·삭제 및 계정 탈퇴를 진행할 수 없습니다.</div>
                {/if}
                {#if accountBlockedByProfiles}
                    <div class="blocking-notice">계정 탈퇴를 신청하려면 모든 프로필을 먼저 삭제해야 합니다. 프로필 관리에서 각 프로필의 삭제 신청을 진행해 주세요.</div>
                {/if}
                <label class="consent-row">
                    <span>끄투리오 {isProfile ? '프로필' : '계정'}을 삭제하면 끄투리오 {isProfile ? '프로필로 가입한' : '계정으로 이용한'} 모든 서비스에서 동시에 탈퇴 처리되어, 더 이상 해당 {isProfile ? '프로필' : '계정'}을 사용한 제휴 서비스에 접근할 수 없습니다.</span>
                    <span class="consent-control"><input type="checkbox" bind:checked={consentService}/><em>동의하기</em></span>
                </label>
                <label class="consent-row">
                    <span>탈퇴 신청 14일 후 개인정보가 삭제되면, 어떠한 방법으로도 {isProfile ? '프로필' : '계정'}을 복원할 수 없습니다.</span>
                    <span class="consent-control"><input type="checkbox" bind:checked={consentIrreversible}/><em>동의하기</em></span>
                </label>
            {/if}
        </section>

        {#if message}<p class="success-message">{message}</p>{/if}
        {#if error}<p class="error-message">{error}</p>{/if}

        <footer class="page-actions">
            <a href="/account" class="secondary-button">취소</a>
            {#if !pending}
                <button class="primary-button" class:disabled={!canSubmit} on:click={submit} disabled={!canSubmit || submitting}>{actionLabel}</button>
            {/if}
        </footer>
    </section>
</main>

<AccountModal open={modalOpen} title="연결된 앱을 먼저 해제해 주세요" on:close={() => modalOpen = false}>
    <p class="modal-copy">{title} 신청 전에 현재 연결된 앱의 이용을 종료하고 연결을 해제해야 합니다.</p>
    <a class="modal-link" href="/account/apps" on:click={() => modalOpen = false}>연결된 앱 관리</a>
</AccountModal>

<AccountModal open={reauthDialogOpen} title="본인 확인" showFooter={false} priority on:close={closeReauthentication}>
    <div class="reauth-content">
        <div class="reauth-icon"><span class="material-symbols-outlined">shield_lock</span></div>
        <p class="reauth-title">보안을 위해 다시 로그인해 주세요.</p>
        <p class="reauth-description">고객님의 정보 보호를 위해 기존 로그인 수단으로 본인 확인을 진행합니다.</p>
    </div>
    {#if passwordEnabled}
        <div class="reauth-form">
            <label for="deletion-reauth-password">비밀번호로 확인</label>
            <input id="deletion-reauth-password" type="password" bind:value={reauthPassword} autocomplete="current-password" placeholder="비밀번호"/>
            <input type="text" inputmode="numeric" bind:value={reauthTotpCode} autocomplete="one-time-code" placeholder="TOTP 인증 코드 또는 보안코드"/>
            <button class="reauth-submit" on:click={reauthenticate}>확인</button>
            {#if reauthMfaRequired}<p class="reauth-help">TOTP 또는 보안코드를 입력해 주세요.</p>{/if}
        </div>
    {/if}
    <LoginMethodSelector providerIds={reauthProviderIds} showPasskey={false} providerUrl={providerUrl}/>
</AccountModal>

<AccountModal open={confirmationOpen} title={title} wide showFooter={false} priority on:close={() => confirmationOpen = false}>
    <div class="confirmation-content">
        <h2>{title}을 진행하면 다음과 같은 작업이 실행됩니다.</h2>
        <h3>즉시 적용</h3>
        <div class="confirmation-list">
            <p><span class="material-symbols-outlined">emoji_events</span><span>{isProfile ? '회원님의 프로필이 랭킹에서 삭제됩니다.' : '회원님의 모든 프로필이 랭킹에서 삭제됩니다.'}</span></p>
            <p><span class="material-symbols-outlined">edit</span><span>{isProfile ? '회원님의 프로필 별명이' : '회원님의 계정에 속한 별명이'} <strong>{deletionAlias}</strong>(으)로 변경됩니다.</span></p>
            <p><span class="material-symbols-outlined">person_off</span><span><strong>{originalName}</strong>(으)로 더 이상 게임에 접속할 수 없습니다.</span></p>
            <p><span class="material-symbols-outlined">link_off</span><span>{isProfile ? '이 프로필을 연결한 앱' : '이 계정에 연결한 앱'} {applications.length}개의 연결이 즉시 해제됩니다.</span></p>
        </div>
        <h3>{formatDeletionDate(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000))} 이후</h3>
        <div class="confirmation-list">
            <p><span class="material-symbols-outlined">delete_forever</span><span>{isProfile ? '회원님의 전적, 경험치, 핑, 아이템 등 모든 게임 데이터가' : '회원님의 모든 프로필과 전적, 경험치, 핑, 아이템 등 모든 계정 데이터가'} 영구히 삭제됩니다.</span></p>
            <p><span class="material-symbols-outlined">badge</span><span>사용하신 원천 로그인사 식별번호는 삭제되지 않으며 30일간 재가입이 불가능합니다.</span></p>
            <p><span class="material-symbols-outlined">restore</span><span>삭제된 {isProfile ? '프로필' : '계정'}은 <strong>어떠한 경우에도 복구가 불가능</strong>합니다.</span></p>
        </div>
        <p class="confirmation-prompt">이에 동의하신다면 아래 글상자에 <strong>{confirmationPhrase}</strong>를 입력해 주세요.</p>
        <input class="confirmation-input" bind:value={confirmationInput} placeholder={confirmationPhrase} autocomplete="off" aria-label="삭제 확인 문구"/>
        {#if error}<p class="error-message">{error}</p>{/if}
        <div class="confirmation-actions">
            <button class="secondary-button" on:click={() => confirmationOpen = false}>취소</button>
            <button class="primary-button" class:disabled={confirmationInput.trim() !== confirmationPhrase || submitting} disabled={confirmationInput.trim() !== confirmationPhrase || submitting} on:click={confirmDeletion}>{submitting ? '처리 중...' : (isProfile ? '삭제' : '탈퇴')}</button>
        </div>
    </div>
</AccountModal>

<style>
    :global(body) { margin: 0; background: #f1f3f6; color: #0f172a; }
    .deletion-page { min-height: 100vh; background: #f1f3f6; padding: 6rem 1rem 5rem; }
    .account-shell { width: min(100%, 48rem); margin: 0 auto; }
    .back-link { display: inline-flex; align-items: center; gap: .5rem; padding: .5rem .7rem; border-radius: .75rem; color: #334155; font-size: 1.05rem; font-weight: 700; text-decoration: none; transition: background .15s ease; }
    .back-link:hover { background: #e2e8f0; }
    .back-link .material-symbols-outlined { font-size: 1.45rem; }
    .page-intro { margin: 2rem 0 2rem; }
    .eyebrow { margin: 0; color: #438c43; font-size: .875rem; font-weight: 800; }
    h1 { margin: .35rem 0 0; color: #0f172a; font-size: 2.2rem; line-height: 1.2; font-weight: 800; letter-spacing: -.03em; }
    .lead { margin: .75rem 0 0; color: #64748b; font-size: 1rem; line-height: 1.6; }
    .deletion-card { display: grid; gap: 1.25rem; }
    .section-block { padding: 1.5rem; border: 1px solid #e2e8f0; border-radius: 1rem; background: #fff; box-shadow: 0 1px 3px #0f172a0a; }
    .section-block h2 { margin: 0; color: #0f172a; font-size: 1.25rem; font-weight: 800; }
    .section-description { margin: .65rem 0 1.25rem; color: #64748b; font-size: .95rem; line-height: 1.6; }
    .application-list { display: grid; gap: .75rem; }
    .application-card { display: flex; justify-content: space-between; align-items: center; min-height: 5.5rem; padding: 1rem 1.15rem; border: 1px solid #fecaca; border-radius: 1rem; background: #fff; color: #0f172a; text-decoration: none; transition: background .15s ease, box-shadow .15s ease; }
    .application-card:hover { background: #fff7f7; box-shadow: 0 3px 10px #ef44441a; }
    .application-card strong { font-size: 1rem; }
    .application-card p { margin: .3rem 0 0; color: #64748b; font-size: .875rem; }
    .application-arrow { color: #ef4444; font-size: 1.5rem; }
    .empty-apps { padding: 1rem 1.15rem; border-radius: 1rem; background: #f8fafc; color: #64748b; font-size: .9rem; }
    .notice-block h2 { margin-bottom: 1.25rem; }
    .consent-row { display: flex; align-items: center; justify-content: space-between; gap: 1.5rem; min-height: 5.25rem; margin-top: .75rem; padding: 1rem 1.15rem; border: 1px solid #e2e8f0; border-radius: 1rem; background: #f8fafc; color: #334155; font-size: .9rem; font-weight: 700; line-height: 1.6; cursor: pointer; transition: background .15s ease, border-color .15s ease; }
    .consent-row:hover { border-color: #cbd5e1; background: #f1f5f9; }
    .consent-control { display: inline-flex; align-items: center; flex: 0 0 auto; gap: .6rem; white-space: nowrap; font-weight: 600; }
    .consent-control input { width: 1.2rem; height: 1.2rem; accent-color: #438c43; }
    .consent-control em { font-style: normal; }
    .blocking-notice, .pending-notice { margin-bottom: .75rem; padding: 1rem 1.15rem; border-radius: 1rem; background: #f8fafc; color: #334155; line-height: 1.65; }
    .blocking-notice { border: 1px solid #fecaca; background: #fff7f7; color: #991b1b; }
    .pending-notice { border: 1px solid #fcd34d; background: #fffbeb; }
    .pending-notice strong { font-size: 1rem; }
    .pending-notice p { margin: .45rem 0 1rem; color: #78350f; }
    .cancel-button { padding: .65rem .95rem; border: 1px solid #cbd5e1; border-radius: .75rem; background: #fff; color: #334155; font-weight: 700; cursor: pointer; transition: background .15s ease; }
    .cancel-button:hover { background: #f8fafc; }
    .page-actions { display: flex; justify-content: flex-end; gap: .75rem; padding-top: .25rem; }
    .secondary-button, .primary-button { display: inline-flex; align-items: center; justify-content: center; min-width: 7rem; min-height: 2.9rem; padding: 0 1.15rem; border-radius: .75rem; font-size: .95rem; font-weight: 700; text-decoration: none; cursor: pointer; transition: background .15s ease, transform .15s ease; }
    .secondary-button { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
    .secondary-button:hover { background: #f8fafc; }
    .primary-button { border: 1px solid #438c43; background: #55aa55; color: #fff; }
    .primary-button:hover:not(:disabled) { background: #438c43; }
    .primary-button.disabled, .primary-button:disabled { border-color: #e2e8f0; background: #e2e8f0; color: #94a3b8; cursor: not-allowed; }
    .success-message, .error-message { margin: 0; padding: .9rem 1rem; border-radius: .85rem; line-height: 1.5; }
    .success-message { background: #ecfdf5; color: #166534; }
    .error-message { background: #fff1f2; color: #b42318; }
    .modal-copy { margin: 0; line-height: 1.7; color: #475569; }
    .modal-link { display: inline-flex; margin-top: 1rem; padding: .7rem 1rem; border-radius: .75rem; background: #ecfdf5; color: #438c43; font-weight: 700; text-decoration: none; }
    .modal-link:hover { background: #dcfce7; }
    .reauth-content { text-align: center; }
    .reauth-icon { display: grid; place-items: center; width: 4.5rem; height: 4.5rem; margin: 0 auto; border-radius: 1.5rem; background: #e8f5e9; color: #438c43; }
    .reauth-icon .material-symbols-outlined { font-size: 2.4rem; }
    .reauth-title { margin: 1rem 0 0; font-weight: 800; }
    .reauth-description { margin: .4rem 0 0; color: #64748b; font-size: .9rem; line-height: 1.6; }
    .reauth-form { margin-top: 1.25rem; padding-top: 1.25rem; border-top: 1px solid #e2e8f0; }
    .reauth-form label { display: block; color: #334155; font-size: .875rem; font-weight: 700; }
    .reauth-form input { width: 100%; box-sizing: border-box; margin-top: .5rem; padding: .75rem; border: 1px solid #cbd5e1; border-radius: .75rem; background: #fff; color: #111827; outline: none; }
    .reauth-form input:focus, .confirmation-input:focus { border-color: #55aa55; box-shadow: 0 0 0 3px #55aa5526; }
    .reauth-submit { width: 100%; margin-top: .75rem; padding: .75rem; border: 0; border-radius: .75rem; background: #55aa55; color: #fff; font-weight: 800; cursor: pointer; }
    .reauth-help { margin: .75rem 0 0; color: #b42318; font-size: .8rem; }
    .confirmation-content h2 { margin: 0 0 1.5rem; color: #0f172a; font-size: 1.35rem; line-height: 1.45; }
    .confirmation-content h3 { margin: 1.75rem 0 .8rem; color: #0f172a; font-size: 1.05rem; }
    .confirmation-list { display: grid; gap: .55rem; }
    .confirmation-list p { display: flex; align-items: flex-start; gap: .7rem; margin: 0; padding: .8rem .9rem; border-radius: .85rem; background: #f8fafc; color: #334155; font-size: .9rem; line-height: 1.6; }
    .confirmation-list .material-symbols-outlined { flex: 0 0 auto; color: #64748b; font-size: 1.25rem; }
    .confirmation-prompt { margin: 1.5rem 0 .7rem; color: #334155; line-height: 1.65; }
    .confirmation-input { width: 100%; box-sizing: border-box; padding: .85rem 1rem; border: 1px solid #cbd5e1; border-radius: .75rem; background: #fff; color: #111827; outline: none; }
    .confirmation-actions { display: flex; justify-content: flex-end; gap: .7rem; margin-top: 1.1rem; }
    @media (max-width: 700px) {
        .deletion-page { padding: 2rem .75rem 3rem; }
        .back-link { font-size: .95rem; }
        .page-intro { margin: 1.5rem 0 1.25rem; }
        h1 { font-size: 1.85rem; }
        .lead { font-size: .9rem; }
        .section-block { padding: 1.1rem; border-radius: .9rem; }
        .consent-row { align-items: flex-start; flex-direction: column; gap: .8rem; font-size: .85rem; }
        .page-actions { padding-top: 0; }
        .secondary-button, .primary-button { flex: 1; }
    }
</style>
