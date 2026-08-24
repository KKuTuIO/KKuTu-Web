
<script>
    import { onMount } from 'svelte';
    let deferredPrompt;
    let installButtonVisible = false;
  
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
<footer class="border-t border-gray-200 p-8 dark:border-gray-800 dark:bg-gray-950">
    <div class="max-w-screen-xl mx-auto md:flex md:justify-between md:items-center py-4">
      <div class="">
        <div class="pb-4 flex">
          <a href="https://cube.city" target="_blank">
            <img src="https://cdn.kkutu.io/img/front/CubeCity.png" class="h-8" alt="CubeCity">
          </a>
        </div>
        <div class="text-gray-400 text-xs dark:text-gray-400">
          <div class="flex gap-x-2 lg:gap-x-5 text-gray-600 dark:text-gray-100 leading-6">
            <a target="_blank" rel="noopener" href="https://cs.kkutu.io/terms">서비스 이용약관</a>
            <a target="_blank" rel="noopener" href="https://cs.kkutu.io/operation">운영정책</a>
            <a target="_blank" rel="noopener" href="https://cs.kkutu.io/privacy-policy">개인정보처리방침</a>
            <a target="_blank" rel="noopener" href="https://cs.kkutu.io/license">저작권 안내</a>
            <a target="_blank" rel="noopener" href="https://support.kkutu.io">고객센터</a>
          </div>
          <div class="text-gray-400 text-xs mt-4">
            <div class="flex flex-col lg:flex-row gap-x-[13px]">
              <span>상호명: 주식회사 큐브시티 (CubeCity Co., Ltd.)</span>
              <span>대표자: 김도훈</span>
              <span>개인정보관리책임자: 김도훈</span>
            </div>
            <div class="flex flex-col lg:flex-row gap-x-[13px]">
              <span>주소: 서울특별시 양천구 목동동로 411, 2004호 (목동, 부영그린타운3차)</span>
              <span>통신판매업신고번호: 제2024-서울양천-1207호</span>
            </div>
            <div class="flex gap-x-[5px] lg:items-center">
              <span>사업자등록번호: 473-86-03330</span>
              <a href="http://www.ftc.go.kr/bizCommPop.do?wrkr_no=4738603330" target="_blank" class="bg-gray-200 dark:bg-gray-800 px-1 text-xs">
                사업자정보조회
              </a>
            </div>
          </div>
          <div class="text-gray-500 dark:text-gray-100 pt-4">&copy; 주식회사 큐브시티, 모든 권리 보유.</div>
          <div class="text-gray-500 dark:text-gray-100">끄투리오는 주식회사 큐브시티의 상표입니다.</div>
        </div>
      </div>
      <div class="">
        <div class="flex flex-col items-end justify-end lg:gap-y-4 gap-y-2 mt-4 lg:mt-0">
          <div class="flex gap-x-2">
           <a href="https://play.google.com/store/apps/details?id=io.kkutu.kkutuio" target="_blank">
             <img src="https://cdn.kkutu.io/img/front/GooglePlay.svg" class="h-8" alt="Google Play"/>
           </a>
           <a href="https://m.onestore.co.kr/mobilepoc/apps/appsDetail.omp?prodId=0000775728" target="_blank">
             <img src="https://cdn.kkutu.io/img/front/onestore.png" class="h-8 border border-gray-400 rounded" alt="One Store"/>
           </a>
           {#if installButtonVisible}
             <button type="button" onclick={installApp} aria-label="끄투리오 앱 설치">
               <img src="https://cdn.kkutu.io/img/front/Pwa.svg" class="h-8" alt="PWA 설치"/>
             </button>
           {/if}
         </div>
            <a href="https://www.grac.or.kr/Statistics/Popup/Pop_StatisticsDetails.aspx?371e798f34f8dfd4a541d1f1f3960c41a6c813a6a053e8e5ec12581d53453bb0" target="_blank">
                <img src="https://cdn.kkutu.io/img/front/grac.svg" class="h-12 mt-2 lg:mt-0" alt="Rating"/>
            </a>
        </div>
      </div>
    </div>
  </footer>
