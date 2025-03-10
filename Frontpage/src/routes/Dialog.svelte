<script>
import { createBubbler, stopPropagation } from 'svelte/legacy';

const bubble = createBubbler();
import { showDialog } from './dialogStore';

const serverEmoji = ["🥔", "🌿", "🥝", "🍋", "🥭", "🌾", "🥗", "🥬", "🔞"];
const serverName = ["감자", "냉이", "다래", "레몬", "망고", "보리", "상추", "아욱", "20세 이상"];
const guestAble = [true, true, false, false, false, false, false, false, true];
let jsonDataServers = { list: [], max: 9 };

if (showDialog) {
    fetch('https://kkutu.io/servers')
        .then(response => response.json())
        .then(data => {
            jsonDataServers = data;
    });
}

function setServer(server) {
    if (jsonDataServers.list[server] === null) {
        alert('채널이 점검 중입니다. 다른 채널을 이용해주세요.');
        return;
    }
    if (document.getElementById('remember').checked) {
        localStorage.setItem('server', server);
    }
    location.href = `https://kkutu.io/?server=${server}`;
}

function randomServer() {
    const server = Math.floor(Math.random() * jsonDataServers.list.length);
    if (jsonDataServers.list[server] === null) {
        return randomServer();
    }
    location.href = `https://kkutu.io/?server=${server}`;
}
</script>

{#if $showDialog}
  <div class="z-50 fixed inset-0 dimmer bg-black/30 flex justify-center items-center">
      <div class="diag dark:text-white bg-white dark:bg-gray-800 pt-6 pb-8 px-4 lg:px-8 max-w-screen-xl">
        <div class="flex justify-between items-center">
          <h3 class="font-semibold text-2xl items-center flex justify-center">
            <span class="material-symbols-outlined mr-2">
              list_alt
          </span>
          채널 목록</h3>
          <button class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95" onclick={() => showDialog.set(false)}>
            <span class="material-symbols-outlined icons-header">close</span>
          </button>
        </div>

        <div class="mt-6">
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {#each jsonDataServers.list as serverUsers, index}
              <button class="text-left dark:bg-gray-700 bg-gray-200 cursor-pointer w-72 transform ease-in duration-100 active:scale-95"
                onclick={() => {
                  setServer(index);
                }}>
                <div class="flex justify-between items-center mt-4 px-4">
                  <h3 class="text-2xl font-bold flex items-center">{serverEmoji[index]} {serverName[index]} 채널
                    {#if guestAble[index]}
                    <span class="ml-2 text-sm font-normal bg-[#E0582B] text-white px-1">손님</span>
                    {/if}
                  </h3>
                  <span class="font-normal text-right dark:text-gray-300 text-gray-500">{serverUsers === null ? '점검 중' : `${serverUsers} / ${jsonDataServers.max}`}</span>
                </div>
                <div class="dark:bg-gray-600 bg-gray-400 h-2 mt-4">
                    <div class={`${serverUsers === null ? "bg-transparent" : "dark:bg-green-300 bg-[#55aa55]"} h-full`} style={`width: ${(serverUsers / jsonDataServers.max) * 100}%`}>
                    </div>
                  </div>
              </button>
            {/each}
          </div>

          <!-- If server is blank -->
          {#if jsonDataServers.list.length == 0}
          <div class="w-96 h-36 text-2xl font-bold flex justify-center items-center mt-4 px-4">
            채널 정보를 불러오는 중입니다.
          </div>
          {/if}
        </div>

        <div class="flex justify-between items-end mt-6">
          <div>
            <input type="checkbox" id="remember" name="remember" class="mr-1">
            <label for="remember" class="text-sm dark:text-gray-300 text-gray-500">채널 선택 기억하기</label>
          </div>

          <button class="bg-[#55aa55] hover:bg-[#51a351] text-white flex items-center font-bold py-2 px-2 transform ease-in duration-100 active:scale-95"
          onclick={() => randomServer()}>
            <span class="material-symbols-outlined icons-header mr-1">casino</span>
            랜덤 입장
          </button>
        </div>
      </div>
  </div>
{/if}
