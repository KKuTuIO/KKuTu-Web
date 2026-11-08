<script>
  import { onMount } from 'svelte';
  import { createPortalHost } from '$lib/game/replayHost.js';
  import RankingPanel from '$lib/game/lobby/RankingPanel.svelte';
  import ToastHost from '$lib/game/ui/ToastHost.svelte';

  /**
   * 랭킹: the game client's ranking, the same component (no connection needed). Like /records, it gets its "store" from the portal host.
   * A player's profile lives in the game client, so a click on a row goes there.
   */
  let host = $state(null);
  let failed = $state(false);

  onMount(async () => {
    try {
      host = await createPortalHost();
    } catch {
      failed = true;
    }
  });

  const openProfile = (id) => (window.location.href = `/play/0?profile=${encodeURIComponent(id)}`);
</script>

<svelte:head><title>끄투리오 - 랭킹</title></svelte:head>

<ToastHost />

<div class="mx-auto w-full max-w-5xl px-4 py-6 text-sm text-(color:--kk-text)">
  <h1 class="mb-4 text-2xl font-black">랭킹</h1>
  {#if host}
    <RankingPanel store={host} onprofile={openProfile} />
  {:else}
    <p class="py-16 text-center text-(color:--kk-muted)">{failed ? '랭킹을 불러오지 못했습니다.' : '불러오는 중...'}</p>
  {/if}
</div>
