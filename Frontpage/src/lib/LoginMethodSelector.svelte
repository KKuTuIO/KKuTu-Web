<script>
    import Tooltip from '$lib/game/ui/Tooltip.svelte';

    const providers = {
        naver: { label: '네이버', particle: '로', color: 'bg-[#03C75A] text-white', icon: 'https://cdn.kkutu.io/logo/fusion/naver.svg' },
        facebook: { label: 'Facebook', particle: '으로', color: 'bg-[#1877F2] text-white', icon: 'https://cdn.kkutu.io/logo/fusion/facebook.svg' },
        google: { label: 'Google', particle: '로', color: 'bg-gray-100 hover:bg-gray-200 text-black', icon: 'https://cdn.kkutu.io/logo/fusion/google.svg' },
        kakao: { label: '카카오', particle: '로', color: 'bg-[#FFDE00] text-[#3C1E1E]', icon: 'https://cdn.kkutu.io/logo/fusion/kakao.svg' },
        discord: { label: 'Discord', particle: '로', color: 'bg-[#5865F2] text-white', icon: 'https://cdn.kkutu.io/logo/fusion/discord.svg' },
        daldalso: { label: '달달소', particle: '로', color: 'bg-[#20318D] text-white', icon: 'https://cdn.kkutu.io/logo/fusion/daldalso.png' }
    };

    /**
     * @typedef {Object} Props
     * @property {any} [providerIds] providers shown up front
     * @property {string[]} [restrictedIds] providers that no longer accept new registrations; tucked behind a disclosure (icons variant)
     * @property {'list'|'icons'} [variant] list = one labelled button per provider, icons = compact brand-icon row
     * @property {boolean} [showPasskey]
     * @property {boolean} [passkeySupported]
     * @property {any} [onPasskey]
     * @property {any} [providerUrl]
     */

    /** @type {Props} */
    let {
        providerIds = Object.keys(providers),
        restrictedIds = [],
        variant = 'list',
        showPasskey = true,
        passkeySupported = true,
        onPasskey = () => {},
        providerUrl = provider => `/login/${provider}`
    } = $props();

    function normalize(list) {
        return (Array.isArray(list) ? list : [])
            .map(id => String(id || '').trim().toLowerCase())
            .filter((id, index, ids) => providers[id] && ids.indexOf(id) === index);
    }
    const withMeta = ids => normalize(ids).map(id => ({ id, ...providers[id] }));
    const openProviders = $derived(withMeta(providerIds));
    const restricted = $derived(withMeta(restrictedIds).filter(p => !openProviders.some(o => o.id === p.id)));
    let showRestricted = $state(false);
</script>

{#snippet iconLink(provider)}
    <Tooltip text={`${provider.label}${provider.particle} 로그인`}>
        <a href={providerUrl(provider.id)} rel="external" aria-label={`${provider.label}${provider.particle} 로그인`}
           class={`kk-btn grid h-12 min-h-0 w-12 place-items-center border-transparent p-0 shadow-[0_3px_0_rgb(0_0_0/0.22)] hover:brightness-105 ${provider.color}`}>
            <img src={provider.icon} class="h-6 w-6 object-contain" alt=""/>
        </a>
    </Tooltip>
{/snippet}

{#snippet passkeyButton()}
    <button class="kk-btn flex min-h-12 w-full items-center justify-center gap-2 text-base" onclick={onPasskey} disabled={!passkeySupported}>
        <span class="material-symbols-outlined text-2xl" aria-hidden="true">key</span>패스키로 로그인
    </button>
{/snippet}

{#if variant === 'icons'}
    <div class="flex flex-col gap-4">
        {#if showPasskey}{@render passkeyButton()}{/if}
        {#if openProviders.length}
            <div class="flex flex-wrap justify-center gap-3">
                {#each openProviders as provider (provider.id)}{@render iconLink(provider)}{/each}
            </div>
        {/if}
        {#if restricted.length}
            <div class="text-center">
                <button type="button" class="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-sm font-bold text-(color:--kk-muted) hover:bg-(--kk-surface-2)"
                        aria-expanded={showRestricted} aria-controls="restricted-login" onclick={() => (showRestricted = !showRestricted)}>
                    기존 회원 전용 로그인
                    <span class="material-symbols-outlined transition-transform {showRestricted ? 'rotate-180' : ''}" style="font-size:18px" aria-hidden="true">expand_more</span>
                </button>
                {#if showRestricted}
                    <div id="restricted-login" class="mt-2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface-2) p-3">
                        <p class="mb-3 text-xs text-(color:--kk-muted)">신규 가입은 받지 않으며, 이미 연결된 계정만 로그인할 수 있습니다.</p>
                        <div class="flex flex-wrap justify-center gap-3">
                            {#each restricted as provider (provider.id)}{@render iconLink(provider)}{/each}
                        </div>
                    </div>
                {/if}
            </div>
        {/if}
    </div>
{:else}
    <div class="-mt-2.5">
        {#if showPasskey}
            <div class="mt-2.5">{@render passkeyButton()}</div>
        {/if}
        {#each openProviders as provider (provider.id)}
            <a href={providerUrl(provider.id)} rel="external"
               class={`kk-btn mt-2.5 flex min-h-12 w-full items-center justify-center gap-2 border-transparent text-base shadow-[0_3px_0_rgb(0_0_0/0.22)] hover:brightness-105 ${provider.color}`}>
                <img src={provider.icon} class="h-6 w-6 object-contain" alt=""/>
                <span>{provider.label}{provider.particle} 로그인</span>
            </a>
        {/each}
    </div>
{/if}
