<script>
    import { self } from 'svelte/legacy';

    import {createEventDispatcher} from 'svelte';
    import {fade, scale} from 'svelte/transition';

    /**
     * @typedef {Object} Props
     * @property {boolean} [open]
     * @property {string} [title]
     * @property {string} [closeLabel]
     * @property {boolean} [showFooter]
     * @property {boolean} [priority]
     * @property {boolean} [wide]
     * @property {import('svelte').Snippet} [children]
     */

    /** @type {Props} */
    let {
        open = false,
        title = '',
        closeLabel = '닫기',
        showFooter = true,
        priority = false,
        wide = false,
        children
    } = $props();
    const dispatch = createEventDispatcher();

    function close() {
        dispatch('close');
    }

    function keydown(event) {
        if (open && event.key === 'Escape') close();
    }
</script>

<svelte:window onkeydown={keydown}/>
{#if open}
    <div class={`fixed inset-0 ${priority ? 'z-[120]' : 'z-[100]'} grid place-items-center bg-slate-950/55 p-4 backdrop-blur-sm`} role="presentation" onclick={self(close)} in:fade={{duration: 130}} out:fade={{duration: 110}}>
        <div class={`w-full ${wide ? 'max-w-3xl' : 'max-w-md'} overflow-hidden rounded-2xl bg-white text-slate-900 shadow-2xl dark:bg-gray-800 dark:text-white`} role="dialog" aria-modal="true" aria-label={title} in:scale={{start: 0.96, duration: 150}} out:scale={{start: 0.98, duration: 100}}>
            <div class="flex items-center justify-between border-b border-gray-200 px-5 py-4 dark:border-gray-700">
                <h2 class="text-lg font-bold">{title}</h2>
                <button class="grid h-9 w-9 place-items-center rounded-full text-gray-500 transition hover:bg-gray-100 hover:text-gray-900 dark:hover:bg-gray-700 dark:hover:text-white" onclick={close} aria-label="닫기"><span class="material-symbols-outlined">close</span></button>
            </div>
            <div class="p-5">{@render children?.()}</div>
            {#if showFooter}<div class="flex justify-end border-t border-gray-200 px-5 py-4 dark:border-gray-700"><button class="rounded-xl border border-gray-300 px-4 py-2 text-sm font-bold transition hover:bg-gray-50 dark:border-gray-600 dark:hover:bg-gray-700" onclick={close}>{closeLabel}</button></div>{/if}
        </div>
    </div>
{/if}
