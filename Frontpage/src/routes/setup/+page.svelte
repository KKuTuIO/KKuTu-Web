<script>
    import { onMount } from 'svelte';
    import { createEventDispatcher } from 'svelte';
    const title = '환영합니다';

  let nickname = '';
  let errors = {
    nickname: null
  };
  const dispatcher = createEventDispatcher();

  const validate = () => {
    errors.nickname = null;
    if (nickname.length < 2 || nickname.length > 15) {
      errors.nickname = { type: 'length' };
      return false;
    }
    const pattern = /^[가-힣a-zA-Z0-9][가-힣a-zA-Z0-9 _-]*[가-힣a-zA-Z0-9]$/i;
    if (!pattern.test(nickname)) {
      errors.nickname = { type: 'pattern' };
      return false;
    }
    return true;
  };

  const handleInput = (event) => {
    nickname = event.target.value;
    validate();
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (validate()) {
      try {
        const response = await fetch('/api/setup/nick', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ nick: nickname })
        });
        const result = await response.json();
        if (result.success) {
          window.location.href = '/?server=0';
        } else {
          alert('금지어가 포함되어 있습니다.'); 
        }
      } catch (error) {
        console.error('Error:', error);
      }
    }
  };
</script>
  
  <svelte:head>
      <title>끄투리오 - {title}</title>
  </svelte:head>
  <style>
    .wave {
      animation: wave 2s infinite;
    }
    @keyframes wave {
      0%, 100% {
        transform: rotate(0deg);
      }
      50% {
        transform: rotate(10deg);
      }
    }
  </style>
  <div class="dark:bg-gray-900 flex min-h-screen flex-col justify-center items-center px-6 py-12 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-sm">
      <!-- Hand wave -->
       <h3 class="text-center text-6xl wave leading-loose">&#x1F44B;</h3>
      <h2 class="mb-2 text-center text-3xl font-semibold leading-9 tracking-wider text-gray-900 dark:text-gray-100">
        신규 유저님,
      </h2>
      <h1 class="mt-2 mb-4 text-center text-5xl font-extrabold tracking-tight bg-gradient-to-r from-blue-600 to-purple-500 text-transparent bg-clip-text">
        만나서 반가워요!
      </h1>
    </div>
    <!-- Onboarding Progress bar -->
    <div class="hidden lg:flex items-center justify-center my-6 gap-x-5">
      <div class="flex items-center gap-x-2 font-bold text-gray-500 dark:text-gray-300">
        <div class="flex items-center justify-center w-6 h-6 bg-gray-200 dark:bg-gray-700 rounded-full shadow-md ml-2">
          <span class="text-gray-500 font-bold text-sm">1</span>
        </div>
        로그인하기
      </div>
      <div class="flex items-center gap-x-2 font-bold text-purple-600 dark:text-purple-300">
        <div class="flex items-center justify-center w-6 h-6 bg-gradient-to-r from-blue-600 to-purple-500 rounded-full shadow-md">
          <span class="text-white font-bold text-sm">2</span>
        </div>
        별명 설정하기
      </div>
      <div class="flex items-center gap-x-2 font-bold text-gray-500 dark:text-gray-300">
        <div class="flex items-center justify-center w-6 h-6 bg-gray-200 dark:bg-gray-700 rounded-full shadow-md ml-2">
          <span class="text-gray-500 font-bold text-sm">3</span>
        </div>
        게임 즐기기
      </div>
    </div>

    <p class="font-bold text-xl text-gray-500 dark:text-gray-300 mt-4 text-center">
      끄투리오에서 사용하실 별명을 입력해 주세요.
    </p>
    <p class="text-gray-500 dark:text-gray-300 mt-4 text-center">
      별명은 2 ~ 15글자의 영문자, 한글, 숫자, 공백, 특수문자 -, _로 설정하실 수 있습니다.<br>
      별명은 7일마다 변경하실 수 있으며, 250핑을 사용하여 고정할 수 있습니다.<br>
      욕설, 비속어 등이 포함된 별명을 사용할 경우 운영정책에 따라 제재될 수 있습니다.
    </p>

    <div class="mt-6 sm:mx-auto sm:w-full sm:max-w-sm">
      <form on:submit|preventDefault={handleSubmit}>
        <input
          placeholder="별명 입력"
          class="mt-4 w-full px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-700"
          name="nickname"
          class:nickname-error={errors.nickname}
          autocomplete="off"
          bind:value={nickname}
          on:input={handleInput}
        />
        {#if errors.nickname && errors.nickname.type === 'required'}
          <p class="text-sm text-red-500">별명을 입력해 주세요.</p>
        {/if}
        {#if errors.nickname && errors.nickname.type === 'length'}
          <p class="text-sm text-red-500">별명은 2자 또는 15자 이하여야 해요.</p>
        {/if}
        {#if errors.nickname && errors.nickname.type === 'pattern'}
          <p class="text-sm text-red-500">별명 규칙을 확인하세요.</p>
        {/if}
        <button type="submit" class="mt-4 w-full px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-500 text-white font-semibold rounded-md shadow-sm hover:from-blue-700 hover:to-purple-600 focus:outline-none focus:ring focus:ring-blue-500 focus:border-blue-500">
          시작하기
        </button>
      </form>
    </div>

  </div>