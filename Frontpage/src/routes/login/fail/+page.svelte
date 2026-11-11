<script>
    import { onMount } from 'svelte';

    const title = '로그인에 실패했습니다';
    let reason = $state('로그인 요청이 만료되었거나 처리 중 문제가 발생했습니다. 다시 시도해 주세요.');

    onMount(async () => {
        const response = await fetch('/api/login/reason').catch(() => null);
        if (!response?.ok) return;
        const message = (await response.text()).trim();
        if (message) reason = message;
    });
</script>

<svelte:head><title>끄투리오 - {title}</title></svelte:head>

<div class="flex justify-center px-4 py-10 sm:py-16">
    <section class="kk-card w-full max-w-md p-8 text-center">
        <span class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-(--kk-danger-soft) text-(color:--kk-danger)">
            <span class="material-symbols-outlined" style="font-size:36px" aria-hidden="true">error</span>
        </span>
        <h1 class="mt-5 text-2xl font-extrabold">로그인에 실패했습니다</h1>
        <p class="mt-3 text-sm leading-6 text-(color:--kk-muted)" role="alert">{reason}</p>
        <a class="kk-btn kk-btn-primary mt-6 inline-flex items-center justify-center px-6" href="/login">로그인으로 돌아가기</a>
    </section>
</div>
