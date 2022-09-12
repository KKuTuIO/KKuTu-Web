# KKuTuIO-Web
##### [글자로 놀자, 끄투리오.](https://kkutu.io)
[게임 서버 소스 코드 확인하기](https://github.com/KKuTuIO/KKuTu-Game/tree/public)
> GPL 라이선스에 따라 배포되는 끄투리오의 웹 서버 소스 코드입니다.
>
> 프로젝트 외적으로 자체 개발된 일부 사항들은 포함되어있지 않을 수 있습니다.
또한, 끄투리오 내부에서만 이용 가능한 리소스가 일부 포함되어있을 수 있으니, 이용에 참고 부탁드립니다.
>
> 본 레포지토리에서 배포되는 소스 코드에는 별도의 지원이 포함되어있지 않으며, 끄투리오에서는 본 레포지토리의 일부 또는 전체를 사용함으로써 발생하는 모든 문제에 대하여 책임지지 않습니다.
>
> 아울러, 끄투리오의 소스 코드는 기존 KKuTu 소스 코드와 달리 [Affero GPL 3.0](https://github.com/KKuTuIO/KKuTu-Game/blob/public/LICENSE)으로 배포되며, 소스 코드의 공개 및 출처 표기가 의무화되어 있습니다. 자세한 사항은 라이선스를 확인해주시기 바랍니다.
<hr/>

[horyu1234](https://github.com/horyu1234)의 [KKuTu-Web](https://github.com/KKuTu-Web) 프로젝트를 기반으로 하는, [JJoriping](https://github.com/JJoriping)의 [KKuTu](https://github.com/JJoriping/KKuTu) 게임 서버와 호환 가능한 끄투 웹 서버입니다.

---

## 꼭 읽어보세요!
**본 레포지토리는 끄투리오 운영 목적으로 사용되어 끄투리오에 저작권이 존재하는 리소스들도 존재하므로 사용에 주의가 필요합니다.** 본 레포지토리를 사용할 경우, 이와 같은 내용을 반드시 제거하고 사용해주시기 바랍니다.


---

### 프로젝트 설명
본 프로젝트는 기존 KKuTu의 웹 서버를 재구현하여 차후 게임서버 재개발이나 어떠한 버그 또는 기능 패치를 진행할 때 불필요하게 소요되는 시간적 비용을 줄이고자 기획되었습니다.
본 프로젝트는 [끄투리오운영](https://kkutu.io)에서 관리합니다.

### 개발자
- [KKuTuIO](https://github.com/KKuTuIO)
- [horyu1234](https://github.com/horyu1234)
- [kdhkr](https://github.com/kdhkr)
- [Preta-Crowz](https://github.com/Preta-Crowz)
- 및 기타 기여자

### 어플리케이션 환경
- Java 8
- Kotlin
- Spring boot Framework
- Thymeleaf View Engine

### 라이선스
본 프로젝트는 여러 라이선스가 존재합니다. 프로젝트를 사용하시기 전 반드시 확인해주시기 바랍니다.

#### [저작자 필수 명시]
* **본 프로젝트를 이용할 경우 사용되는 서비스의 하단 등 눈에 쉽게 보이는 곳에 아래의 문구를 필수로 추가하셔야 합니다.**  
 * 단, 별도의 오픈 소스 법적 고지 페이지를 만들고 해당 페이지로 쉽게 이동할 수 있도록 하단 링크를 추가하는 것은 허용됩니다.

```
본 서버는 horyu1234의 KKuTu-Web을 기반으로 하는 KKuTuIO의 KKuTu-Web을 기반으로 두고 있습니다.
```

* **위 문구의 의미를 해치지 않는 선에서 아래와 같은 느낌으로 수정하는 것은 허용됩니다.**  
```
글자로 놀자! 끄투 온라인. 끄투는 JJoriping의 오픈소스 KKuTu를 기반으로 제작되었으며,
horyu1234의 KKuTu-Web을 기반으로 하는 KKuTuIO의 KKuTu-Web을 기반으로 두고 있습니다.
```

#### [라이선스]
* `src/main/resources` 경로에 존재하는 파일 중 [JJoriping/KKuTu](https://github.com/JJoriping/KKuTu)에 포함되어있는 파일은 [GNU 일반 공중 사용 라이선스](https://github.com/JJoriping/KKuTu/blob/master/LICENSE) 라이선스를 따릅니다.
* `src/main/kotlin`, `src/test` 경로의 모든 소스 코드 및 리소스는 [GNU 아페로 일반 공중 사용 라이선스](https://github.com/KKuTuIO/KKuTu-Web/blob/kkutuio/LICENSE)를 따릅니다.
  * 일부 소스 코드 및 리소스는 다른 원작자를 가질 수도 있습니다. 자세한 내용은 본 레포지토리의 기여 내역을 확인해주시기 바랍니다.
* 끄투리오운영은 끄투리오에서 특화된 서비스 운영을 위해 새롭게 추가된 일부 리소스 및 모든 이미지 및 소리에 대한 모든 권리를 가집니다.
