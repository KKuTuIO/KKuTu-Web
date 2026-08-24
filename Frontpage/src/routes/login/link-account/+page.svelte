<script>
    import { onMount } from 'svelte';

    let provider = $state('');
    let ready = $state(false);

    onMount(async () => {
        try {
            const response = await fetch('/api/login/link-account', { credentials: 'same-origin' });
            if (!response.ok) throw new Error();
            provider = (await response.json()).provider;
            ready = true;
        } catch (_) {
            location.replace('/login');
        }
    });

    const methods = [
        { vendor: 'naver', label: '네이버로 로그인', logo: 'naver.svg', className: 'naver' },
        { vendor: 'facebook', label: 'Facebook으로 로그인', logo: 'facebook.svg', className: 'facebook' },
        { vendor: 'google', label: 'Google로 로그인', logo: 'google.svg', className: 'google' },
        { vendor: 'kakao', label: '카카오로 로그인', logo: 'kakao.svg', className: 'kakao' }
    ];
</script>

<svelte:head>
    <title>끄투리오 - 다른 로그인 수단 선택</title>
</svelte:head>

<main class="page-shell" aria-live="polite">
    {#if ready}
        <section class="login-modal" aria-labelledby="link-title">
            <div class="badge" aria-hidden="true">!</div>
            <h1 id="link-title">{provider} 계정만으로는 신규 계정을 만들 수 없습니다.</h1>
            <p class="description">아래 로그인 수단 중 하나로 로그인하여 계속하세요.</p>
            <p class="notice">현재 로그인하신 {provider} 계정은 선택하는 로그인 수단에 자동으로 연동됩니다.</p>

            <div class="methods">
                {#each methods as method}
                    <a class:google={method.className === 'google'} class:naver={method.className === 'naver'} class:facebook={method.className === 'facebook'} class:kakao={method.className === 'kakao'} href={'/login/' + method.vendor} rel="external">
                        <img src={'https://cdn.kkutu.io/logo/fusion/' + method.logo} alt="" />
                        {method.label}
                    </a>
                {/each}
            </div>

            <a class="cancel" href="/login/logout">다른 계정으로 돌아가기</a>
        </section>
    {/if}
</main>

<style>
    :global(html) { background: #111827; }
    .page-shell { box-sizing: border-box; min-height: 100vh; display: grid; place-items: center; padding: 24px; background: radial-gradient(circle at top, #27364f 0%, #111827 55%); font-family: inherit; }
    .login-modal { width: min(100%, 470px); box-sizing: border-box; border: 1px solid rgba(255,255,255,.12); border-radius: 18px; padding: 38px 34px 30px; background: rgba(31,41,55,.97); color: #f9fafb; box-shadow: 0 24px 70px rgba(0,0,0,.38); text-align: center; }
    .badge { display: grid; place-items: center; width: 38px; height: 38px; margin: 0 auto 18px; border-radius: 50%; background: #f59e0b; color: #1f2937; font-size: 24px; font-weight: 800; }
    h1 { margin: 0; font-size: 21px; line-height: 1.45; letter-spacing: -.025em; word-break: keep-all; }
    .description { margin: 14px 0 0; color: #d1d5db; line-height: 1.6; }
    .notice { margin: 20px 0 25px; padding: 12px 14px; border-radius: 10px; background: rgba(59,130,246,.14); color: #bfdbfe; font-size: 14px; line-height: 1.55; word-break: keep-all; }
    .methods { display: grid; gap: 10px; }
    .methods a { display: flex; align-items: center; justify-content: center; min-height: 50px; border-radius: 9px; color: #fff; font-size: 16px; font-weight: 700; text-decoration: none; box-shadow: 0 2px 5px rgba(0,0,0,.16); transition: transform .12s ease, filter .12s ease; }
    .methods a:hover { filter: brightness(1.06); }
    .methods a:active { transform: scale(.985); }
    .methods img { width: 23px; height: 23px; margin-right: 10px; }
    .naver { background: #03c75a; }
    .facebook { background: #1877f2; }
    .google { background: #f8fafc; color: #1f2937 !important; }
    .kakao { background: #ffde00; color: #3c1e1e !important; }
    .cancel { display: inline-block; margin-top: 24px; color: #9ca3af; font-size: 14px; text-decoration: underline; text-underline-offset: 3px; }
    @media (max-width: 480px) { .page-shell { padding: 16px; } .login-modal { padding: 32px 22px 26px; border-radius: 15px; } h1 { font-size: 19px; } }
</style>
