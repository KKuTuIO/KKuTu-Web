<script>
    import {fly} from 'svelte/transition';

    let { toasts = [], dismiss = () => {} } = $props();

    const styles = {
        success: 'border-emerald-300/70 bg-emerald-500 text-white',
        error: 'border-rose-300/70 bg-rose-500 text-white',
        info: 'border-sky-300/70 bg-sky-500 text-white'
    };
    const icons = {success: 'check_circle', error: 'error', info: 'info'};
</script>

{#if toasts.length}
    <div class="pointer-events-none fixed bottom-6 right-4 z-[110] flex w-[min(92vw,420px)] flex-col gap-2">
        {#each toasts as toast (toast.id)}
            <div in:fly={{y: 16, duration: 170}} out:fly={{y: 16, duration: 130}} class={`pointer-events-auto flex items-start gap-3 rounded-xl border px-4 py-3 text-sm font-medium shadow-lg shadow-black/20 ${styles[toast.kind] || styles.info}`} role="status">
                <span class="material-symbols-outlined text-xl" aria-hidden="true">{icons[toast.kind] || icons.info}</span>
                <span class="flex-1 leading-6">{toast.message}</span>
                <button class="grid h-6 w-6 place-items-center rounded transition hover:bg-black/10" aria-label="닫기" onclick={() => dismiss(toast.id)}><span class="material-symbols-outlined text-base">close</span></button>
            </div>
        {/each}
    </div>
{/if}
