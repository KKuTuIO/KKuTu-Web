
<script>
    import { onMount } from 'svelte';
  let deferredPrompt;
    let installButtonVisible = false;
    let output = '';
  
    onMount(() => {
      if ('BeforeInstallPromptEvent' in window) {
        showResult('⏳ BeforeInstallPromptEvent supported but not fired yet');
      } else {
        showResult('❌ BeforeInstallPromptEvent NOT supported');
      }
  
      window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        deferredPrompt = e;
        installButtonVisible = true;
        showResult('✅ BeforeInstallPromptEvent fired', true);
      });
  
      window.addEventListener('appinstalled', (e) => {
        showResult('✅ AppInstalled fired', true);
      });
    });
  
    function showResult(text, append = false) {
      if (append) {
        document.querySelector('output').innerHTML += '<br>' + text;
      } else {
        document.querySelector('output').innerHTML = text;
      }
    }
  
    async function installApp() {
      console.log('installApp button clicked');
      if (deferredPrompt) {
        deferredPrompt.prompt();
        showResult('🆗 Installation Dialog opened');
        // Find out whether the user confirmed the installation or not
        const { outcome } = await deferredPrompt.userChoice;
        // The deferredPrompt can only be used once.
        deferredPrompt = null;
        // Act on the user's choice
        if (outcome === 'accepted') {
          showResult('😀 User accepted the install prompt.', true);
        } else if (outcome === 'dismissed') {
          showResult('😟 User dismissed the install prompt');
        }
        // Hide the install button
        // installButtonVisible = false;
      }
    }
</script>
<footer class="border-t border-gray-200 p-2 dark:border-gray-800 dark:bg-gray-950">
    <div class="max-w-screen-xl mx-auto md:flex md:justify-between py-4">
      <div class="">
        <div class="text-gray-400 text-xs dark:text-gray-400">
          <div class="flex gap-x-2 lg:gap-x-5 text-gray-600 dark:text-gray-100 leading-6">
            <a target="_blank" rel="noopener" href="https://cs.kkutu.io/terms">서비스 이용약관</a>
            <a target="_blank" rel="noopener" href="https://cs.kkutu.io/operation">운영정책</a>
            <a target="_blank" rel="noopener" href="https://cs.kkutu.io/privacy-policy">개인정보처리방침</a>
            <a target="_blank" rel="noopener" href="https://cs.kkutu.io/license">저작권 안내</a>
            <a target="_blank" rel="noopener" href="https://support.kkutu.io">고객센터</a>
          </div>
          <div class="text-gray-400 text-xs hidden">
            <div class="flex gap-x-[13px]">
              <span>회사명 : 회사명입력예정</span>
              <span>대표이사 : 대표자입력예정</span>
            </div>
            <div class="flex gap-x-[13px]">
              <span>주소 : (00000) 서울특별시 대덕구 중동 392</span>
              <span>사업자등록번호 : 123-45-67890</span>
            </div>
            <div class="flex gap-x-[13px]">
              <span>이메일 : test@example.com</span>
              <span>전화 : 010-1234-5678</span>
            </div>
          </div>
          <div class="flex gap-x-2 mt-4">
           <a href="https://play.google.com/store/apps/details?id=io.kkutu.kkutuio" target="_blank">
             <img src="/img/GooglePlay.svg" class="h-8" alt="Google Play"/>
           </a>
           <a href="https://m.onestore.co.kr/mobilepoc/apps/appsDetail.omp?prodId=0000775728" target="_blank">
             <img src="/img/onestore.png" class="h-8 border border-gray-400 rounded" alt="One Store"/>
           </a>
           <img src="/img/Pwa.svg" class="h-8 cursor-pointer" on:click={installApp} alt="Pwa"/>
         </div>
          <div class="text-gray-500 dark:text-gray-100 pt-4">&copy; 끄투리오운영, 모든 권리 보유</div>
        </div>
      </div>
      <div class="">
        <div>
            <a href="https://www.grac.or.kr/Statistics/Popup/Pop_StatisticsDetails.aspx?371e798f34f8dfd4a541d1f1f3960c41a6c813a6a053e8e5ec12581d53453bb0" target="_blank">
                <img src="/img/grac.svg" class="h-12 mt-4 lg:mt-0" alt="Rating"/>
            </a>
        </div>
      </div>
    </div>
  </footer>
