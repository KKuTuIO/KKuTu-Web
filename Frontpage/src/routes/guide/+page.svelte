<script>
	import { NEW_CLIENT_URL } from '$lib/clientPref.svelte.js';
	import { modes } from './modes.js';
	import { optionGroups, systems } from './glossary.js';

	const steps = [
		{ icon: 'play_arrow', title: '게임 시작', body: '메인 화면에서 “게임 시작!”을 누르면 추천 채널로 바로 입장하며, 원한다면 다른 채널을 직접 고를 수도 있습니다.' },
		{ icon: 'meeting_room', title: '방 고르기', body: '로비에서 열려 있는 방에 들어가거나, “방 만들기”로 직접 방장이 되어 게임 유형과 규칙을 정할 수 있습니다.' },
		{ icon: 'account_circle', title: '로그인', body: '로그인하지 않아도 감자·냉이 채널에서 체험해 볼 수 있고, 로그인하면 점수와 기록이 저장됩니다.' }
	];
</script>

<svelte:head>
	<title>끄투리오 - 게임 소개</title>
</svelte:head>

<div class="kk bg-(--kk-bg) text-(color:--kk-text)">
	<div class="mx-auto max-w-screen-xl px-4 py-10 lg:px-8 lg:py-14">
		<header class="max-w-3xl">
			<h1 class="text-3xl font-black lg:text-4xl">끄투리오 게임 소개</h1>
			<p class="mt-3 text-lg leading-relaxed text-(color:--kk-muted)">
				끄투리오는 설치 없이 브라우저에서 바로 즐기는 온라인 단어 게임으로, 끝말잇기를 비롯한
				{modes.length}가지 게임 유형을 즐길 수 있습니다.
			</p>
			<a href={NEW_CLIENT_URL} rel="external" class="kk-btn kk-btn-hero mt-6 inline-flex items-center gap-2 px-6 text-lg"
				><span class="material-symbols-outlined" aria-hidden="true">play_arrow</span>지금 시작하기</a
			>
		</header>

		<section class="mt-12" aria-labelledby="start-title">
			<h2 id="start-title" class="text-2xl font-extrabold">시작하는 방법</h2>
			<ol class="mt-4 grid gap-4 md:grid-cols-3">
				{#each steps as s, i (s.title)}
					<li class="kk-card p-5">
						<div class="flex items-center gap-3">
							<span class="grid size-10 place-items-center rounded-full bg-(--kk-accent-soft) text-(color:--kk-accent-strong)">
								<span class="material-symbols-outlined" aria-hidden="true">{s.icon}</span>
							</span>
							<h3 class="text-lg font-extrabold"><span class="text-(color:--kk-accent-strong)">{i + 1}.</span> {s.title}</h3>
						</div>
						<p class="mt-3 leading-relaxed text-(color:--kk-muted)">{s.body}</p>
					</li>
				{/each}
			</ol>
		</section>

		<section class="mt-12" aria-labelledby="modes-title">
			<h2 id="modes-title" class="text-2xl font-extrabold">게임 유형</h2>
			<p class="mt-2 text-(color:--kk-muted)">방을 만들 때 원하는 유형을 고를 수 있습니다.</p>
			<ul class="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
				{#each modes as m (m.name)}
					<li class="kk-card flex flex-col p-5">
						<div class="flex items-start justify-between gap-2">
							<h3 class="text-lg font-extrabold">{m.name}</h3>
							<span class="flex shrink-0 gap-1">
								{#each m.langs as lang (lang)}<span class="kk-chip">{lang}</span>{/each}
							</span>
						</div>
						<p class="mt-2 leading-relaxed">{m.desc}</p>
						{#if m.example}
							<!-- eslint-disable-next-line svelte/no-at-html-tags -- modes.js 의 고정 문자열 -->
							<p class="mt-auto pt-3 text-sm font-bold text-(color:--kk-muted) [&_u]:text-(color:--kk-accent-strong) [&_u]:decoration-2">{@html m.example}</p>
						{/if}
					</li>
				{/each}
			</ul>
		</section>

		<section class="mt-12" aria-labelledby="glossary-title">
			<h2 id="glossary-title" class="text-2xl font-extrabold">방 옵션 용어집</h2>
			<p class="mt-2 text-(color:--kk-muted)">방을 만들 때 고를 수 있는 규칙입니다. 게임 유형에 따라 쓸 수 있는 옵션은 다를 수 있습니다.</p>
			<div class="mt-4 gap-4 lg:columns-2">
				{#each optionGroups as g (g.title)}
					<div class="kk-card mb-4 break-inside-avoid p-5">
						<h3 class="text-lg font-extrabold">{g.title}</h3>
						<dl class="mt-3 divide-y divide-(color:--kk-line)">
							{#each g.items as it (it.term)}
								<div class="grid gap-x-4 gap-y-0.5 py-2.5 sm:grid-cols-[8rem_1fr]">
									<dt id="term-{it.term}" class="font-extrabold text-(color:--kk-accent-strong)">{it.term}</dt>
									<dd class="leading-relaxed">{it.text}</dd>
								</div>
							{/each}
						</dl>
					</div>
				{/each}
			</div>
		</section>

		<section class="mt-12" aria-labelledby="systems-title">
			<h2 id="systems-title" class="text-2xl font-extrabold">게임 시스템</h2>
			<p class="mt-2 text-(color:--kk-muted)">끄투리오에서 자주 만나는 재화와 시스템을 정리했습니다.</p>
			<dl class="kk-card mt-4 divide-y divide-(color:--kk-line) p-5">
				{#each systems as s (s.term)}
					<div class="grid gap-x-4 gap-y-0.5 py-3 first:pt-0 last:pb-0 sm:grid-cols-[8rem_1fr]">
						<dt id="term-{s.term}" class="font-extrabold text-(color:--kk-accent-strong)">{s.term}</dt>
						<dd class="leading-relaxed">{s.text}</dd>
					</div>
				{/each}
			</dl>
		</section>

		<section class="kk-card mt-12 flex flex-col items-start gap-4 p-6 sm:flex-row sm:items-center sm:justify-between" aria-labelledby="ready-title">
			<div>
				<h2 id="ready-title" class="text-xl font-extrabold">준비됐나요?</h2>
				<p class="mt-1 text-(color:--kk-muted)">지금 바로 방에 들어가 친구들과 실력을 겨뤄 보세요.</p>
			</div>
			<a href={NEW_CLIENT_URL} rel="external" class="kk-btn kk-btn-hero inline-flex shrink-0 items-center gap-2 px-6 text-lg"
				><span class="material-symbols-outlined" aria-hidden="true">play_arrow</span>지금 시작하기</a
			>
		</section>
	</div>
</div>
