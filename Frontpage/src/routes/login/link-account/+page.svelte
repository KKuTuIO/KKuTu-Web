<script>
    import { onMount } from 'svelte';
    import LoginMethodSelector from '$lib/LoginMethodSelector.svelte';

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

    // Same login methods as before, in the same order.
    const methodIds = ['naver', 'facebook', 'google', 'kakao'];
</script>

<svelte:head>
    <title>끄투리오 - 다른 로그인 수단 선택</title>
</svelte:head>

<div class="flex justify-center px-4 py-10 sm:py-16" aria-live="polite">
    {#if ready}
        <section class="kk-card w-full max-w-md p-6 text-center sm:p-8" aria-labelledby="link-title">
            <span class="mx-auto grid h-14 w-14 place-items-center rounded-full bg-(--kk-warn-soft) text-(color:--kk-warn)">
                <span class="material-symbols-outlined" style="font-size:32px" aria-hidden="true">link</span>
            </span>
            <h1 id="link-title" class="mt-4 text-xl font-extrabold leading-snug [word-break:keep-all]">{provider} 계정만으로는 신규 계정을 만들 수 없습니다.</h1>
            <p class="mt-3 text-(color:--kk-muted)">아래 로그인 수단 중 하나로 로그인하여 계속하세요.</p>
            <p class="mt-4 mb-5 flex items-start gap-2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface-2) px-4 py-3 text-left text-sm font-bold [word-break:keep-all]">
                <span class="material-symbols-outlined shrink-0 text-(color:--kk-accent-strong)" style="font-size:20px" aria-hidden="true">info</span>
                <span>현재 로그인하신 {provider} 계정은 선택하는 로그인 수단에 자동으로 연동됩니다.</span>
            </p>

            <LoginMethodSelector providerIds={methodIds} showPasskey={false}/>

            <a class="mt-6 inline-block text-sm font-bold text-(color:--kk-muted) underline underline-offset-4 hover:text-(color:--kk-text)" href="/login/logout">다른 계정으로 돌아가기</a>
        </section>
    {/if}
</div>
