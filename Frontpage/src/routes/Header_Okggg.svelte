<script>
import { page } from '$app/stores';

let currentStatus = $state('main');

if ($page.url.pathname.includes('rank')) {
    currentStatus = 'rank';
}
</script>

<header class="top-0 fixed w-full z-10">
	<div class="bg-white shadow lg:py-2 py-3">
		<nav class="max-w-screen-xl mx-auto flex items-center justify-between px-4 lg:px-8 lg:py-0" aria-label="Global">
		<div class="flex lg:flex-1">
			<div class="flex items-center gap-x-2">
                <a href="/" rel="external">
                    <span class="sr-only">끄투리오</span>
                    <img class="h-8" src="https://cdn.kkutu.io/img/bi/bi_vertical_main.png" alt="끄투리오"/>
                </a>

                <!--<a href="/okggg.html" rel="external">
                    <span class="text-xl font-bold text-indigo-600">OKG.GG</span>
                </a>-->
			</div>
		</div>
		<div class="flex flex-1 justify-end gap-x-2">
            <!--<a href="/okggg.html" rel="external">
                <button class={currentStatus === 'main' ? 'current-header header-items' : 'header-items'}>홈</button>
            </a>-->
            <a href="/rank.html" rel="external">
                <button class={currentStatus === 'rank' ? 'current-header header-items' : 'header-items'}>랭킹</button>
            </a>
            <!--<a href="/calc.html" rel="external">
                <button class={currentStatus === 'calc' ? 'current-header header-items' : 'header-items'}>계산기</button>
            </a>-->
		</div>
		</nav>
	</div>
</header>