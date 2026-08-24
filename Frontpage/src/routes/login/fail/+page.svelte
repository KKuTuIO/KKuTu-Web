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

<main class="flex min-h-screen items-center justify-center bg-gray-100 px-5 pt-20 text-gray-900 dark:bg-gray-900 dark:text-gray-100">
    <section class="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-7 text-center shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <span class="material-symbols-outlined text-5xl text-[#55aa55]">error</span>
        <h1 class="mt-4 text-2xl font-bold">로그인에 실패했습니다</h1>
        <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">{reason}</p>
        <a class="mt-6 inline-flex rounded-xl bg-[#55aa55] px-5 py-3 font-bold text-white transition hover:bg-[#438c43]" href="/login">로그인으로 돌아가기</a>
    </section>
</main>
