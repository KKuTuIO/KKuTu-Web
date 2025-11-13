<script>
</script>

<svelte:head>
    <title>브라우저 지원 안내</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css">
</svelte:head>

<div class="page-container">
    <div class="unsupported-container">
        <div class="card">
            <i class="fas fa-exclamation-triangle warning-icon"></i>
            <h1>브라우저 지원 안내</h1>
            <p>
                현재 사용 중인 브라우저는 끄투리오에서 더 이상 지원되지 않습니다.<br>
                더 나은 서비스 경험을 위해 아래 최신 브라우저 중 하나를 사용해 주세요.
            </p>
            <div class="browsers-grid">
                <a href="https://www.google.com/chrome/" class="browser-card" target="_blank" rel="noopener noreferrer">
                    <img src="https://cdn.kkutu.io/logo/chrome.svg" alt="Google Chrome">
                    <span>Chrome</span>
                </a>
                <a href="https://www.mozilla.org/firefox/new/" class="browser-card" target="_blank" rel="noopener noreferrer">
                    <img src="https://cdn.kkutu.io/logo/firefox.svg" alt="Mozilla Firefox">
                    <span>Firefox</span>
                </a>
                <a href="https://www.naver.com/whale/" class="browser-card" target="_blank" rel="noopener noreferrer">
                    <img src="https://cdn.kkutu.io/logo/whale.svg" alt="Naver Whale">
                    <span>Whale</span>
                </a>
                <a href="https://www.opera.com/" class="browser-card" target="_blank" rel="noopener noreferrer">
                    <img src="https://cdn.kkutu.io/logo/opera.svg" alt="Opera Browser">
                    <span>Opera</span>
                </a>
                <a href="https://www.microsoft.com/edge" class="browser-card" target="_blank" rel="noopener noreferrer">
                    <img src="https://cdn.kkutu.io/logo/edge.svg" alt="Microsoft Edge">
                    <span>Edge</span>
                </a>
            </div>
        </div>
    </div>
</div>

<style>
    @font-face {
        font-family: 'NBGothic';
        src: local('Nanum Barun Gothic'),
        url(https://cdn.kkutu.io/font/NanumBarunGothic.woff2) format('woff2');
    }

    :global(body) {
        font-family: 'NBGothic', sans-serif;
        background-color: #f4f7f9;
        margin: 0;
        color: #333;
    }

    .page-container {
        display: flex;
        flex-direction: column; /* Ensure content flows vertically */
        min-height: 100vh;
    }

    .unsupported-container {
        flex-grow: 1; /* Allows this container to take up remaining space */
        display: flex;
        justify-content: center;
        align-items: center;
        width: 100%;
        padding: 20px;
    }

    .card {
        background: white;
        padding: 2rem;
        border-radius: 20px;
        box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
        text-align: center;
        max-width: 765px;
        width: 100%;
    }

    .warning-icon {
        font-size: 48px;
        color: #ffc107;
        margin-bottom: 20px;
    }

    h1 {
        font-size: 28px;
        font-weight: bold;
        margin-bottom: 15px;
        color: #2c3e50;
    }

    p {
        font-size: 16px;
        line-height: 1.6;
        color: #555;
        margin-bottom: 40px;
    }

    .browsers-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
        gap: 24px;
    }

    .browser-card {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 20px;
        border-radius: 12px;
        background-color: #f8f9fa;
        text-decoration: none;
        color: #333;
        font-weight: bold;
        transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    .browser-card:hover {
        transform: translateY(-5px);
        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
    }

    .browser-card img {
        width: 48px;
        height: 48px;
        margin-bottom: 15px;
    }
</style>