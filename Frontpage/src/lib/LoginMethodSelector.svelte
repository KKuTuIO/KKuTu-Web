<script>
    const providers = {
        naver: { label: '네이버', particle: '로', color: 'bg-[#03C75A] text-white', icon: 'https://cdn.kkutu.io/logo/fusion/naver.svg' },
        facebook: { label: 'Facebook', particle: '으로', color: 'bg-[#1877F2] text-white', icon: 'https://cdn.kkutu.io/logo/fusion/facebook.svg' },
        google: { label: 'Google', particle: '로', color: 'bg-gray-100 hover:bg-gray-200 text-black', icon: 'https://cdn.kkutu.io/logo/fusion/google.svg' },
        kakao: { label: '카카오', particle: '로', color: 'bg-[#FFDE00] text-[#3C1E1E]', icon: 'https://cdn.kkutu.io/logo/fusion/kakao.svg' },
        discord: { label: 'Discord', particle: '로', color: 'bg-[#5865F2] text-white', icon: 'https://cdn.kkutu.io/logo/fusion/discord.svg' },
        daldalso: { label: '달달소', particle: '로', registrationNote: '신규 가입 불가', color: 'bg-[#20318D] text-white', icon: 'https://cdn.kkutu.io/logo/fusion/daldalso.png' }
    };

    export let providerIds = Object.keys(providers);
    export let showPasskey = true;
    export let passkeySupported = true;
    export let onPasskey = () => {};
    export let providerUrl = provider => `/login/${provider}`;

    function normalizedProviderIds() {
        return (Array.isArray(providerIds) ? providerIds : [])
            .map(id => String(id || '').trim().toLowerCase())
            .filter((id, index, ids) => providers[id] && ids.indexOf(id) === index);
    }
</script>

<div>
    {#if showPasskey}
        <button class="mt-4 flex min-h-[54px] w-full items-center justify-center bg-slate-700 p-3 text-lg font-semibold leading-6 text-white shadow-md transition duration-100 ease-in active:scale-95 disabled:opacity-50 dark:bg-slate-600"
                on:click={onPasskey} disabled={!passkeySupported}>
            <span class="material-symbols-outlined mr-2 text-2xl" aria-hidden="true">key</span>패스키로 로그인
        </button>
    {/if}
    {#each normalizedProviderIds().map(id => ({ id, ...providers[id] })) as provider}
        <a href={providerUrl(provider.id)} rel="external"
           class={`mt-4 flex min-h-[54px] w-full items-center justify-center p-3 text-lg font-semibold leading-6 shadow-md transition duration-100 ease-in active:scale-95 ${provider.color}`}>
            <img src={provider.icon} class="mr-2 h-6 w-6 object-contain" alt=""/>
            {provider.label}{provider.particle} 로그인{#if provider.registrationNote}&nbsp;<sub class="bottom-[-0.1rem]">({provider.registrationNote})</sub>{/if}
        </a>
    {/each}
</div>
