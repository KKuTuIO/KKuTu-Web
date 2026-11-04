
<script>
    import { onMount } from 'svelte';
    let deferredPrompt;
    let installButtonVisible = $state(false);
    let open = $state(false);
  
    onMount(() => {
      const beforeInstall = (e) => {
        e.preventDefault();
        deferredPrompt = e;
        installButtonVisible = true;
      };
      const installed = () => {
        deferredPrompt = null;
        installButtonVisible = false;
      };
      window.addEventListener('beforeinstallprompt', beforeInstall);
      window.addEventListener('appinstalled', installed);
      return () => {
        window.removeEventListener('beforeinstallprompt', beforeInstall);
        window.removeEventListener('appinstalled', installed);
      };
    });
  
    async function installApp() {
      if (deferredPrompt) {
        deferredPrompt.prompt();
        await deferredPrompt.userChoice;
        deferredPrompt = null;
        installButtonVisible = false;
      }
    }
</script>
<footer class="kk border-t border-(color:--kk-line) bg-(--kk-surface) text-xs text-(color:--kk-muted)">
  <div class="mx-auto max-w-screen-xl px-4 py-3 lg:px-8">
    <div class="flex flex-wrap items-center justify-between gap-x-6 gap-y-1">
      <div class="flex flex-wrap items-center gap-x-4 gap-y-1">
        <span>&copy; 주식회사 큐브시티, 모든 권리 보유.</span>
        <nav class="flex flex-wrap gap-x-4 font-bold text-(color:--kk-text) [&_a:hover]:text-(color:--kk-accent-strong)" aria-label="약관">
          <a target="_blank" rel="noopener" href="https://cs.kkutu.io/terms">이용약관</a>
          <a target="_blank" rel="noopener" href="https://cs.kkutu.io/operation">운영정책</a>
          <a target="_blank" rel="noopener" href="https://cs.kkutu.io/privacy-policy">개인정보처리방침</a>
        </nav>
      </div>
      <button type="button" class="inline-flex items-center gap-1 rounded-lg px-2 py-1 font-bold hover:bg-(--kk-surface-2)" aria-expanded={open} aria-controls="footer-more" onclick={() => (open = !open)}>
        사업자 정보<span class="material-symbols-outlined transition-transform {open ? 'rotate-180' : ''}" style="font-size:18px" aria-hidden="true">expand_more</span>
      </button>
    </div>
    {#if open}
    <div id="footer-more" class="mt-3 flex flex-col gap-4 border-t border-(color:--kk-line) pt-3 md:flex-row md:items-end md:justify-between">
      <div class="leading-5">
        <a href="https://cube.city" target="_blank" class="mb-2 inline-flex"><img src="https://cdn.kkutu.io/img/front/CubeCity.png" class="h-6" alt="CubeCity"></a>
        <div class="flex gap-x-4 font-bold text-(color:--kk-text)">
          <a target="_blank" rel="noopener" href="https://cs.kkutu.io/license">저작권 안내</a>
          <a target="_blank" rel="noopener" href="https://support.kkutu.io">고객센터</a>
        </div>
        <div class="mt-2 flex flex-col lg:flex-row gap-x-3">
          <span>상호명: 주식회사 큐브시티 (CubeCity Co., Ltd.)</span>
          <span>대표자: 김도훈</span>
          <span>개인정보관리책임자: 김도훈</span>
        </div>
        <div class="flex flex-col lg:flex-row gap-x-3">
          <span>주소: 서울특별시 양천구 목동동로 411, 2004호 (목동, 부영그린타운3차)</span>
          <span>통신판매업신고번호: 제2024-서울양천-1207호</span>
        </div>
        <div class="flex items-center gap-x-2">
          <span>사업자등록번호: 473-86-03330</span>
          <a href="http://www.ftc.go.kr/bizCommPop.do?wrkr_no=4738603330" target="_blank" class="rounded-md border border-(color:--kk-line) bg-(--kk-surface-2) px-1.5 font-bold">사업자정보조회</a>
        </div>
        <div class="mt-1">끄투리오는 주식회사 큐브시티의 상표입니다.</div>
      </div>
      <div class="flex items-center gap-2">
        <a href="https://play.google.com/store/apps/details?id=io.kkutu.kkutuio" target="_blank"><img src="https://cdn.kkutu.io/img/front/GooglePlay.svg" class="h-8" alt="Google Play"/></a>
        <a href="https://m.onestore.co.kr/mobilepoc/apps/appsDetail.omp?prodId=0000775728" target="_blank"><img src="https://cdn.kkutu.io/img/front/onestore.png" class="h-8 rounded-lg border border-(color:--kk-line-strong)" alt="One Store"/></a>
        {#if installButtonVisible}
          <button type="button" onclick={installApp} aria-label="끄투리오 앱 설치"><img src="https://cdn.kkutu.io/img/front/Pwa.svg" class="h-8" alt="PWA 설치"/></button>
        {/if}
        <a href="https://www.grac.or.kr/Statistics/Popup/Pop_StatisticsDetails.aspx?371e798f34f8dfd4a541d1f1f3960c41a6c813a6a053e8e5ec12581d53453bb0" target="_blank"><img src="https://cdn.kkutu.io/img/front/grac.svg" class="h-10" alt="Rating"/></a>
      </div>
    </div>
    {/if}
  </div>
</footer>
