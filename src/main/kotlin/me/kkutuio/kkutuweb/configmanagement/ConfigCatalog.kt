package me.kkutuio.kkutuweb.configmanagement

/**
 * 백오피스에서 다룰 수 있는 설정 파일의 고정 목록. 파일 이름은 여기 있는 것만 받는다(경로를 사용자가 정하지 못한다).
 * `editable` 은 "수정 후보"일 뿐이며, 게임 서버 파일은 저장소에서 추적되는지를 실제로 확인한 뒤 최종 결정한다.
 */
data class ConfigFileSpec(val name: String, val editable: Boolean, val description: String)

object ConfigCatalog {
    const val WEB_TARGET = "web"
    const val GAME_TARGET_PREFIX = "game-"

    /** 파일 하나의 최대 크기. SSH 명령 인자(약 128KB)에 base64 로 실어 보내므로 여유를 둔다. */
    const val MAX_CONFIG_BYTES = 48 * 1024

    /** 웹 서버가 시작할 때 읽는 설정. OAuth·DB 접속 정보 같은 비밀 설정은 이 폴더가 아니라 배포 환경에서 주입하므로 여기에 없다. */
    val webFiles = listOf(
        ConfigFileSpec("kkutu.json", true, "웹 서버 기본 설정. 관리자 계정·게임 서버 접속 정보·비밀 값은 보호되어 수정할 수 없습니다."),
        ConfigFileSpec("games.json", true, "게임 규칙과 옵션 정의"),
        ConfigFileSpec("moremi.json", true, "모레미 부위·분류·장착 구성"),
        ConfigFileSpec("themes.json", true, "단어 주제 목록"),
    )

    private val gameLocalNames = listOf("api", "automod", "event", "generic", "network", "nick", "rule", "word")
    private val gameManagedNames = listOf("league", "level", "levelreward", "math", "reward", "riddle", "serverNames", "sokssok", "typing")

    /** 게임 서버 설정. 저장소가 관리하는 파일은 실제로 추적 중이면 보기만 가능하다. */
    val gameFiles: List<ConfigFileSpec> =
        gameLocalNames.map { ConfigFileSpec("$it.json", true, "게임 서버 설정. 저장소에서 추적하지 않는 파일만 수정할 수 있습니다.") } +
            gameManagedNames.map { ConfigFileSpec("$it.json", true, "저장소가 관리하는 설정입니다. 추적 중이면 보기만 가능하고, 변경은 저장소 커밋과 소스 업데이트로 합니다.") } +
            gameLocalNames.map { ConfigFileSpec("$it.inc.json", false, "기본값 설정입니다. 보기만 가능합니다.") }

    fun gameTargetId(channelId: Int) = "$GAME_TARGET_PREFIX$channelId"

    fun parseGameChannel(target: String): Int? =
        if (target.startsWith(GAME_TARGET_PREFIX)) target.removePrefix(GAME_TARGET_PREFIX).toIntOrNull()?.takeIf { it >= 0 } else null

    fun webFile(name: String): ConfigFileSpec? = webFiles.firstOrNull { it.name == name }
    fun gameFile(name: String): ConfigFileSpec? = gameFiles.firstOrNull { it.name == name }
}
