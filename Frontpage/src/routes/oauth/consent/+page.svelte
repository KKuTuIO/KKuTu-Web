<script>
    import {onMount} from 'svelte';

    let consent = $state(null);
    let selectedProfile = $state('');
    let csrfToken = $state('');
    let csrfParameter = $state('_csrf');
    let error = $state('');
    const defaultAppIcon = 'https://cdn.kkutu.io/img/bi/bi_profile_main.png';

    const scopeCopy = {
        openid: ['계정 식별번호', '끄투리오 계정의 식별번호를 확인합니다.'],
        profile: ['기본 프로필', '끄투리오 계정의 기본 정보를 확인합니다.'],
        email: ['전자 메일 주소', '끄투리오 계정의 전자 메일 주소를 확인합니다.'],
        account: ['계정 상태', '끄투리오 계정의 상태를 확인합니다.'],
        offline: ['로그인 유지', '서비스가 계정의 로그인 상태를 유지할 수 있습니다.'],
        'game:kkutu': ['끄투리오 프로필', '연결된 끄투리오 게임 프로필 정보를 확인합니다.']
    };

    function useDefaultLogo(event) {
        event.currentTarget.onerror = null;
        event.currentTarget.src = defaultAppIcon;
    }

    onMount(async () => {
        try {
            const [consentResponse, csrfResponse] = await Promise.all([
                fetch('/oauth/authorize/consent'),
                fetch('/api/account/csrf')
            ]);
            if (!consentResponse.ok) throw new Error('expired');
            consent = await consentResponse.json();
            selectedProfile = consent.profiles?.[0]?.id || '';
            const csrf = await csrfResponse.json();
            csrfToken = csrf.token;
            csrfParameter = csrf.parameter || '_csrf';
        } catch (_) {
            error = 'CSRF 토큰이 일치하지 않습니다. 처음부터 다시 시도해 주세요.';
        }
    });
</script>

<svelte:head><title>끄투리오 - 접근 권한 확인</title></svelte:head>

<main class="min-h-screen bg-slate-100 px-4 pb-14 pt-24 dark:bg-gray-900 sm:px-6">
    <section class="mx-auto max-w-xl">
        {#if error}
            <div class="rounded-2xl border border-red-200 bg-white p-6 text-center shadow-sm dark:border-red-900 dark:bg-gray-800">
                <h1 class="text-xl font-bold text-slate-900 dark:text-white">만료되거나 유효하지 않은 요청입니다</h1>
                <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-gray-300">{error}</p><a
                    class="mt-5 inline-flex rounded-full bg-[#55aa55] px-5 py-2.5 text-sm font-bold text-white"
                    href="/login">로그인으로 돌아가기</a></div>
        {:else if consent}
            <div class="text-center">
                <img class="mx-auto h-14 w-14 rounded-2xl border border-slate-200 bg-white object-cover shadow-lg shadow-slate-900/10 dark:border-gray-700 dark:bg-gray-800"
                     src={consent.client_logo_uri || defaultAppIcon} alt={consent.client_name}
                     onerror={useDefaultLogo}/>
                <p class="mt-5 text-sm font-bold text-[#438c43]">끄투리오 계정</p>
                <h1 class="mt-2 text-2xl font-bold tracking-tight text-slate-900 dark:text-white"><span
                        class="text-[#438c43]">{consent.client_name}</span>에 접근 권한을 부여할까요?</h1>
                <form class="mt-7 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800"
                      method="post" action="/oauth/authorize/consent">
                    <div class="border-b border-slate-200 p-5 dark:border-gray-700">
                        <label for="consent-profile" class="text-sm font-bold text-slate-900 dark:text-white">현재 게임
                            프로필</label>
                        <select id="consent-profile" name="profile_id"
                                class="mt-3 w-full rounded-xl border border-slate-300 bg-white p-3 text-sm font-semibold text-slate-900 dark:border-gray-600 dark:bg-gray-900 dark:text-white"
                                bind:value={selectedProfile}>
                            {#each consent.profiles || [] as profile}
                                <option value={profile.id}>{profile.nickname || profile.legacy_user_id}
                                    · {profile.game_key}</option>
                            {/each}
                        </select>
                    </div>
                    <div class="p-5"><h2 class="text-base font-bold text-slate-900 dark:text-white">요청된 접근 권한</h2>
                        <div class="mt-4 divide-y divide-slate-200 overflow-hidden rounded-xl border border-slate-200 dark:divide-gray-700 dark:border-gray-700">
                            {#each consent.scopes as scope}
                                <div class="flex gap-3 p-4">
                                    <span class="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-green-50 font-bold text-[#438c43] dark:bg-green-950">✓</span>
                                    <div class="text-left">
                                        <h3 class="text-sm font-bold text-slate-900 dark:text-white">{scopeCopy[scope]?.[0] || scope}</h3>
                                        <p class="mt-1 text-sm leading-5 text-slate-600 dark:text-gray-300">{scopeCopy[scope]?.[1] || '이 서비스에서 요청된 권한입니다.'}</p>
                                    </div>
                                </div>
                            {/each}
                        </div>
                    </div>
                    <div class="border-t border-slate-200 bg-slate-50 px-5 py-4 text-xs leading-5 text-slate-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-400">
                        계속하기 전에 연결된 서비스의 이용약관과 개인정보처리방침을 검토해 보시길 바랍니다.
                    </div>
                    <div class="grid gap-3 p-5 sm:grid-cols-2">
                        <button class="rounded-xl border border-slate-300 px-4 py-3 text-sm font-bold text-slate-700 dark:border-gray-600 dark:text-gray-100"
                                type="submit" name="approve" value="false">취소
                        </button>
                        <button class="rounded-xl bg-[#55aa55] px-4 py-3 text-sm font-bold text-white shadow-sm transition hover:bg-[#438c43]"
                                type="submit" name="approve" value="true">계속
                        </button>
                    </div>
                    <input type="hidden" name={csrfParameter} value={csrfToken}>
                </form>
            </div>
        {:else}
            <div class="rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <span class="inline-block h-7 w-7 animate-spin rounded-full border-4 border-slate-200 border-t-[#55aa55]"></span>
                <p class="mt-4 text-sm text-slate-500 dark:text-gray-400">요청을 불러오는 중입니다.</p></div>
        {/if}
    </section>
</main>
