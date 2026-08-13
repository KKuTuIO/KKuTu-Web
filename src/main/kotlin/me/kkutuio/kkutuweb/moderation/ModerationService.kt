package me.kkutuio.kkutuweb.moderation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.core.type.TypeReference
import me.kkutuio.kkutuweb.game.GameClientManager
import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyEngine
import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyLoader
import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyPreview
import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyRegistry
import me.kkutuio.kkutuweb.moderation.policy.ResolvedPolicyEffect
import me.kkutuio.kkutuweb.ranking.RankDao
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import me.kkutuio.kkutuweb.user.User
import me.kkutuio.kkutuweb.user.UserDao
import org.postgresql.util.PGobject
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.jdbc.support.KeyHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class ModerationService(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val userDao: UserDao,
    private val rankDao: RankDao,
    private val policyLoader: ModerationPolicyLoader,
    private val policyEngine: ModerationPolicyEngine,
    private val policyRegistry: ModerationPolicyRegistry,
    private val gameClientManager: GameClientManager,
    private val ipSubjectCodec: IpSubjectCodec,
    private val kKuTuSetting: KKuTuSetting
) {
    private val logger = LoggerFactory.getLogger(ModerationService::class.java)
    private val gameLogFileFormatter = DateTimeFormatter
        .ofPattern("'game-'yyyy-MM-dd HH'.log.gz'")
        .withZone(ZoneId.of("Asia/Seoul"))

    fun policySummary(): ModerationPolicySummary {
        val loaded = policyLoader.current()
        return ModerationPolicySummary(
            loaded.document.policyId,
            loaded.digest,
            loaded.document.source.url,
            loaded.document.categories.map {
                ModerationCategorySummary(it.code, it.name, it.requiresOverride)
            }
        )
    }

    fun reloadPolicy(): ModerationPolicySummary {
        val loaded = policyLoader.reload()
        policyRegistry.register(loaded)
        return policySummary()
    }

    fun searchUsers(query: String): List<ModerationUserSummary> {
        if (query.trim().length < 2) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 2자 이상이어야 합니다.")
        return userDao.searchUsers(query.trim()).map(::toSummary)
    }

    fun getUserDetail(userId: String): ModerationUserDetail {
        val user = userDao.getUser(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        val reportsAnchor = Instant.now()
        val reportPage = reports(userId, 0, reportsAnchor)
        return ModerationUserDetail(
            user = toSummary(user),
            flags = user.flags,
            counters = currentCounters(userId),
            history = history(userId, 50),
            reports = reportPage.reports,
            reportsHasMore = reportPage.hasMore,
            reportsAnchor = reportsAnchor
        )
    }

    fun getUserReports(userId: String, window: Int, anchorMillis: Long?): ModerationReportPage {
        requireUser(userId)
        require(anchorMillis == null || anchorMillis > 0) { "올바른 신고 조회 기준 시각이 필요합니다." }
        val anchor = anchorMillis?.let(Instant::ofEpochMilli) ?: Instant.now()
        return reports(userId, window, anchor)
    }

    fun getIpDetail(subject: String): ModerationIpDetail {
        val resolved = resolveIpSubject(subject)
        val reportsAnchor = Instant.now()
        val reportPage = ipReports(resolved.ip, 0, reportsAnchor)
        val hash = ipSubjectCodec.hash(resolved.ip)
        val lastSeenAt = jdbcTemplate.query(
            "SELECT MAX(time) AS last_seen_at FROM connection_log WHERE user_ip = ?",
            { rs, _ -> rs.instant("last_seen_at") },
            resolved.ip
        ).firstOrNull()
        val geo = gameClientManager.requestIpGeo(resolved.ip)?.let(::parseIpGeoResponse)
        val network = networkOf(resolved.ip)
        return ModerationIpDetail(
            ip = resolved.ip,
            sourceGuestId = resolved.guestId,
            lastSeenAt = lastSeenAt,
            network = network,
            geo = geo,
            currentBlock = currentIpBlock(resolved.ip),
            guestIpOffenseCount = ipOffenseCount(hash),
            counters = currentIpCounters(hash),
            identities = ipIdentities(resolved.ip),
            blockedIpsInNetwork = blockedIpsInNetwork(resolved.ip, network),
            history = ipHistory(hash, 50),
            reports = reportPage.reports,
            reportsHasMore = reportPage.hasMore,
            reportsAnchor = reportsAnchor
        )
    }

    fun getIpReports(subject: String, window: Int, anchorMillis: Long?): ModerationReportPage {
        require(anchorMillis == null || anchorMillis > 0) { "올바른 신고 조회 기준 시각이 필요합니다." }
        val resolved = resolveIpSubject(subject)
        return ipReports(resolved.ip, window, anchorMillis?.let(Instant::ofEpochMilli) ?: Instant.now())
    }

    private fun parseIpGeoResponse(response: String): ModerationIpGeoInfo? = runCatching {
        val root = objectMapper.readTree(response)
        if (!root.path("ok").asBoolean(false)) return@runCatching null
        val node = root.path("geo")
        if (!node.isObject) return@runCatching null
        fun value(name: String): String? = node.path(name).takeUnless { it.isMissingNode || it.isNull }
            ?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        ModerationIpGeoInfo(
            countryCode = value("countryCode"),
            countryName = value("countryName"),
            asn = value("asn"),
            asName = value("asName"),
            isp = value("isp")
        )
    }.getOrNull()

    fun getReportDetail(reportId: Long): ModerationReportDetail {
        val report = jdbcTemplate.query(
            """
            SELECT report_id, time, reporter_id, reporter_nick, target_id, target_type,
                   category_code, reason, detail, reported_chat, file_name, room_id,
                   game_id, game_context_source, status, resolved_at, resolved_by,
                   resolution_note
            FROM report_log WHERE report_id = ?
            """.trimIndent(),
            { rs, _ ->
                ReportDetailRow(
                    reportId = rs.getLong("report_id"),
                    time = rs.instant("time")!!,
                    reporterId = rs.getString("reporter_id"),
                    reporterNick = rs.getString("reporter_nick"),
                    targetId = rs.getString("target_id"),
                    targetType = rs.getString("target_type") ?: "UNKNOWN",
                    categoryCode = rs.getString("category_code"),
                    reason = rs.getString("reason"),
                    detail = rs.getString("detail"),
                    reportedChat = rs.getString("reported_chat"),
                    fileName = rs.getString("file_name"),
                    roomId = rs.getInt("room_id").let { if (rs.wasNull()) null else it },
                    gameId = rs.getString("game_id"),
                    gameContextSource = rs.getString("game_context_source") ?: "NONE",
                    status = rs.getString("status"),
                    resolvedAt = rs.instant("resolved_at"),
                    resolvedBy = rs.getString("resolved_by"),
                    resolutionNote = rs.getString("resolution_note")
                )
            },
            reportId
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다.")

        val linkedSanction = jdbcTemplate.query(
            """
            SELECT c.case_id, c.subject_type, c.revoked_at
            FROM moderation_case_reports cr
            JOIN moderation_cases c ON c.case_id = cr.case_id
            WHERE cr.report_id = ? ORDER BY c.case_id DESC LIMIT 1
            """.trimIndent(),
            { rs, _ -> LinkedSanction(
                rs.getLong("case_id"),
                rs.getString("subject_type"),
                rs.getTimestamp("revoked_at") != null
            ) },
            reportId
        ).firstOrNull()
        val currentReporter = userDao.getUser(report.reporterId)
        val currentTarget = if (report.targetType in setOf("USER", "CHAT")) userDao.getUser(report.targetId) else null
        val categoryName = policyLoader.current().document.categories
            .firstOrNull { it.code == report.categoryCode }?.name

        return ModerationReportDetail(
            reportId = report.reportId,
            time = report.time,
            status = report.status,
            targetType = report.targetType,
            categoryCode = report.categoryCode,
            categoryName = categoryName,
            reason = report.reason,
            detail = report.detail,
            reportedChat = report.reportedChat,
            fileName = report.fileName,
            roomId = report.roomId,
            gameId = report.gameId,
            gameContextSource = report.gameContextSource,
            reporter = ModerationReportParty(
                report.reporterId,
                report.reporterNick,
                currentReporter?.nickname,
                currentReporter != null
            ),
            target = ModerationReportParty(
                report.targetId,
                null,
                currentTarget?.nickname,
                currentTarget != null
            ),
            resolvedAt = report.resolvedAt,
            resolvedBy = report.resolvedBy,
            resolutionNote = report.resolutionNote,
            linkedSanctionCaseId = linkedSanction?.caseId,
            linkedSanctionSubjectType = linkedSanction?.subjectType,
            linkedSanctionRevoked = linkedSanction?.revoked ?: false,
            gameReferences = reportGameReferences(report),
            suspicionReferences = reportSuspicionReferences(report)
        )
    }

    fun issueReportLogAccess(reportId: Long, requestedFileName: String?, actorId: String): ModerationLogAccess {
        val report = getReportDetail(reportId)
        val allowedFiles = buildSet {
            report.fileName?.let(::add)
            report.gameReferences.mapTo(this) { it.logFileName }
        }
        val fileName = requestedFileName?.trim()?.takeIf { it.isNotEmpty() }
            ?: report.gameReferences.firstOrNull { it.relation == "LINKED" }?.logFileName
            ?: report.fileName
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "신고에 연결된 게임 로그가 없습니다.")
        if (fileName !in allowedFiles) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "이 신고와 연결되지 않은 로그 파일입니다.")
        }
        if (!GAME_LOG_FILE_REGEX.matches(fileName)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "신고에 연결된 게임 로그가 없습니다.")
        }

        logger.info("관리자 {}에게 신고 #{} 로그 접근 권한을 발급했습니다. file={}", actorId, reportId, fileName)
        return createLogAccess(reportId, fileName)
    }

    fun issueManualLogAccess(requestedFileName: String, actorId: String): ModerationLogAccess {
        val fileName = requestedFileName.trim()
        if (!GAME_LOG_FILE_REGEX.matches(fileName)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 시간별 게임 로그 파일명이 필요합니다.")
        }
        logger.info("관리자 {}에게 직접 로그 접근 권한을 발급했습니다. file={}", actorId, fileName)
        return createLogAccess(0, fileName)
    }

    private fun createLogAccess(reportId: Long, fileName: String): ModerationLogAccess {
        val logServiceSetting = kKuTuSetting.getModerationLogService()
        val publicBaseUrl = logServiceSetting.publicUrl.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "로그 열람 서비스 주소가 설정되지 않았습니다.")
        val secret = logServiceSetting.signingSecret.takeIf { it.length >= 32 }
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "로그 열람 서비스 서명 키가 설정되지 않았습니다.")
        val expires = Instant.now().epochSecond + 60
        val canonical = "$reportId\n$fileName\n$expires"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return ModerationLogAccess(
            endpoint = "$publicBaseUrl/api/moderation/log-file",
            fileName = fileName,
            reportId = reportId,
            expires = expires,
            signature = signature
        )
    }

    @Transactional
    fun linkReportGameContext(reportId: Long, request: ReportGameContextLinkRequest, actorId: String) {
        require(request.gameId.isNotBlank() && request.gameId.length <= 64) { "올바른 경기 ID가 필요합니다." }
        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(hashtext(?))",
            { _, _ -> Unit },
            "request:${request.requestId}"
        )
        val existingCommand = jdbcTemplate.query(
            "SELECT command_type, target_key FROM moderation_command_requests WHERE request_id = ?",
            { rs, _ -> rs.getString("command_type") to rs.getString("target_key") },
            request.requestId
        ).firstOrNull()
        if (existingCommand != null) {
            if (existingCommand.first == "LINK_REPORT_GAME" && existingCommand.second == reportId.toString()) return
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 작업에 사용된 요청 ID입니다.")
        }
        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(hashtext(?))",
            { _, _ -> Unit },
            "report:$reportId"
        )
        val report = jdbcTemplate.query(
            "SELECT reporter_id, target_id, target_type FROM report_log WHERE report_id = ?",
            { rs, _ -> Triple(rs.getString("reporter_id"), rs.getString("target_id"), rs.getString("target_type")) },
            reportId
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다.")

        val replay = if (report.third == "ROOM") {
            val targetRoomId = report.second.toIntOrNull()
                ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "방 신고의 대상 방 번호가 올바르지 않습니다.")
            jdbcTemplate.query(
                """
                SELECT game_id, room_id FROM game_replay
                WHERE game_id = ? AND room_id = ? AND user_ids @> ARRAY[?]::TEXT[] LIMIT 1
                """.trimIndent(),
                { rs, _ -> rs.getString("game_id") to rs.getInt("room_id") },
                request.gameId.trim(), targetRoomId, report.first
            ).firstOrNull()
        } else {
            jdbcTemplate.query(
                """
                SELECT game_id, room_id FROM game_replay
                WHERE game_id = ? AND user_ids @> ARRAY[?, ?]::TEXT[] LIMIT 1
                """.trimIndent(),
                { rs, _ -> rs.getString("game_id") to rs.getInt("room_id") },
                request.gameId.trim(), report.first, report.second
            ).firstOrNull()
        } ?: throw ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "신고자와 대상(또는 대상 방)이 함께 확인되는 경기 기록이 아닙니다."
        )

        jdbcTemplate.update(
            "UPDATE report_log SET game_id = ?, room_id = ?, game_context_source = 'ADMIN_LINKED' WHERE report_id = ?",
            replay.first, replay.second, reportId
        )
        jdbcTemplate.update(
            """
            INSERT INTO moderation_command_requests
                (request_id, command_type, target_key, actor_id, response_status, response_body)
            VALUES (?, 'LINK_REPORT_GAME', ?, ?, 204, ?)
            """.trimIndent(),
            request.requestId,
            reportId.toString(),
            actorId,
            json(mapOf("gameId" to replay.first, "reason" to request.reason.trim()))
        )
    }

    fun preview(request: SanctionPreviewRequest): ModerationPolicyPreview {
        requireUser(request.userId)
        request.overrideCaseId?.let { requireOverrideSubject(it, "USER", request.userId) }
        request.custom?.let { custom ->
            require(request.categoryCodes.isEmpty()) { "정책 제재와 사용자 지정 제재를 동시에 선택할 수 없습니다." }
            return policyEngine.previewCustom(
                custom.reason,
                Instant.now(),
                custom.endsAt,
                custom.permanent
            )
        }
        return policyEngine.preview(
            request.categoryCodes,
            currentCounters(request.userId, request.overrideCaseId),
            request.occurredAt ?: Instant.now()
        )
    }

    fun previewIp(request: IpSanctionPreviewRequest): ModerationPolicyPreview {
        val resolved = resolveIpSubject(request.subject)
        val hash = ipSubjectCodec.hash(resolved.ip)
        request.overrideCaseId?.let { requireOverrideSubject(it, "IP", hash) }
        return policyEngine.previewGuestIp(
            request.categoryCodes,
            currentIpCounters(hash, request.overrideCaseId),
            ipOffenseCount(hash, request.overrideCaseId),
            request.occurredAt ?: Instant.now()
        )
    }

    @Transactional
    fun issueIp(request: IpSanctionIssueRequest, actorId: String, requestIpHash: String?): SanctionIssueResponse {
        require(request.evidenceText.isNotBlank()) { "근거 자료는 필수입니다." }
        require(request.categoryCodes.isNotEmpty()) { "위반 사유를 하나 이상 선택해야 합니다." }
        val resolved = resolveIpSubject(request.subject)
        val hash = ipSubjectCodec.hash(resolved.ip)
        existingCase(request.requestId)?.let {
            return SanctionIssueResponse(it, previewFromCase(it), true)
        }
        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(hashtext(?))",
            { _, _ -> Unit },
            "moderation-ip:$hash"
        )
        val preview = policyEngine.previewGuestIp(
            request.categoryCodes,
            currentIpCounters(hash, request.overrideCaseId),
            ipOffenseCount(hash, request.overrideCaseId),
            request.occurredAt
        )
        if (preview.requiresApproval) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이 단계는 별도 승인 절차가 필요합니다.")
        }
        val displayReason = preview.violations.firstOrNull { it.selectedAsPrimary }?.categoryName
            ?: request.summary.trim()
        require(displayReason.isNotBlank()) { "표시할 제재 사유를 결정할 수 없습니다." }
        validateIpReports(request.reportIds, resolved.ip)
        request.overrideCaseId?.let {
            validateOverrideCase(it, "IP", hash, request.reportIds)
            revoke(it, "제재 #$it 변경", actorId, requestIpHash, "IP")
        }
        policyRegistry.register(policyLoader.current())

        val caseId = try {
            insertIpCase(request, resolved.ip, hash, actorId, preview, displayReason)
        } catch (e: DuplicateKeyException) {
            val existing = existingCase(request.requestId) ?: throw e
            return SanctionIssueResponse(existing, previewFromCase(existing), true)
        }
        preview.violations.forEach { violation ->
            jdbcTemplate.update(
                """
                INSERT INTO moderation_case_violations
                    (case_id, category_code, offense_no, selected_as_primary, candidate_effects)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                caseId,
                violation.categoryCode,
                violation.offenseNo,
                violation.selectedAsPrimary,
                json(violation.candidateEffects)
            )
        }
        preview.effects.forEach { effect ->
            val effectId = insertIpEffect(caseId, effect)
            applyLegacyIpBlock(caseId, effectId, resolved.ip, displayReason, actorId, effect)
        }
        request.reportIds.distinct().forEach { reportId ->
            jdbcTemplate.update(
                "INSERT INTO moderation_case_reports(case_id, report_id) VALUES (?, ?)",
                caseId,
                reportId
            )
            val overridden = request.overrideCaseId != null
            jdbcTemplate.update(
                """
                UPDATE report_log SET status = ?, resolved_at = NOW(),
                    resolved_by = ?, resolution_note = ? WHERE report_id = ?
                """.trimIndent(),
                if (overridden) "OVERRIDDEN" else "RESOLVED",
                actorId,
                if (overridden) "IP 제재 #$caseId 로 변경 (기존 #${request.overrideCaseId})"
                else "IP 제재 #$caseId 연결",
                reportId
            )
        }
        jdbcTemplate.update(
            """
            INSERT INTO moderation_case_events
                (case_id, event_type, actor_type, actor_id, payload, request_ip_hash)
            VALUES (?, 'ISSUED', 'ADMIN', ?, ?, ?)
            """.trimIndent(),
            caseId,
            actorId,
            json(mapOf("requestId" to request.requestId, "categories" to request.categoryCodes)),
            auditRequestIpHash(requestIpHash)
        )
        jdbcTemplate.update(
            """
            INSERT INTO moderation_command_requests
                (request_id, command_type, target_key, actor_id, response_status, response_body)
            VALUES (?, 'ISSUE_IP_SANCTION', ?, ?, 201, ?)
            """.trimIndent(),
            request.requestId,
            hash,
            actorId,
            json(mapOf("caseId" to caseId))
        )
        if (request.overrideCaseId == null) {
            queueReportResolutionNotifications(caseId, request.reportIds, null, null)
        }
        val connectedGuestIds = ipIdentities(resolved.ip).filter { it.guest }.map { it.id }
        val fullIpRestriction = preview.effects.any { it.type == "IP_RESTRICTION" }
        if (fullIpRestriction || connectedGuestIds.isNotEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    if (fullIpRestriction) gameClientManager.kick("", resolved.ip)
                    else connectedGuestIds.forEach { gameClientManager.kick(it, "") }
                }
            })
        }
        return SanctionIssueResponse(caseId, preview, false)
    }

    @Transactional
    fun issue(request: SanctionIssueRequest, actorId: String, requestIpHash: String?): SanctionIssueResponse {
        require(request.evidenceText.isNotBlank()) { "근거 자료는 필수입니다." }
        val subjectUser = requireUser(request.userId)
        require((request.custom == null) xor request.categoryCodes.isEmpty()) {
            "정책 제재 또는 사용자 지정 제재 중 하나만 선택해야 합니다."
        }
        val relatedUserIds = request.relatedUserIds.distinct().filter { it != request.userId }
        if ("17" in request.categoryCodes && relatedUserIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "이용제한 우회는 연관 계정이 필요합니다.")
        }
        relatedUserIds.forEach(::requireUser)

        existingCase(request.requestId)?.let {
            return SanctionIssueResponse(it, previewFromCase(it), true)
        }

        val preview = request.custom?.let { custom ->
            policyEngine.previewCustom(
                custom.reason,
                Instant.now(),
                custom.endsAt,
                custom.permanent
            )
        } ?: policyEngine.preview(
            request.categoryCodes,
            currentCounters(request.userId, request.overrideCaseId),
            request.occurredAt
        )
        if (preview.requiresApproval) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이 단계는 별도 승인 절차가 필요합니다.")
        }
        val displayReason = request.custom?.reason?.trim()
            ?: preview.violations
                .firstOrNull { it.selectedAsPrimary }
                ?.categoryName
            ?: request.summary.trim()
        require(displayReason.isNotBlank()) { "표시할 제재 사유를 결정할 수 없습니다." }
        policyRegistry.register(policyLoader.current())
        validateReports(request.reportIds, request.userId)
        request.overrideCaseId?.let {
            validateOverrideCase(it, "USER", request.userId, request.reportIds)
            revoke(it, "제재 #$it 변경", actorId, requestIpHash, "USER")
        }

        val caseId = try {
            insertCase(request, actorId, preview, displayReason)
        } catch (e: DuplicateKeyException) {
            val existing = existingCase(request.requestId) ?: throw e
            return SanctionIssueResponse(existing, previewFromCase(existing), true)
        }

        preview.violations.forEach { violation ->
            jdbcTemplate.update(
                """
                INSERT INTO moderation_case_violations
                    (case_id, category_code, offense_no, selected_as_primary, candidate_effects)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                caseId,
                violation.categoryCode,
                violation.offenseNo,
                violation.selectedAsPrimary,
                json(violation.candidateEffects)
            )
        }
        jdbcTemplate.update(
            "INSERT INTO moderation_case_subjects(case_id, user_id, role) VALUES (?, ?, 'PRIMARY')",
            caseId,
            request.userId
        )
        relatedUserIds.forEach { relatedUserId ->
            jdbcTemplate.update(
                """
                INSERT INTO moderation_case_subjects(case_id, user_id, role, relation_reason)
                VALUES (?, ?, 'RELATED', '이용제한 우회 연관 계정')
                """.trimIndent(),
                caseId,
                relatedUserId
            )
        }

        preview.effects.forEach { effect ->
            if (effect.type == "EXTEND_RELATED_RESTRICTION") {
                relatedUserIds.forEach { relatedUserId ->
                    val extended = resolveRelatedExtension(relatedUserId, effect)
                    val effectId = insertEffect(caseId, relatedUserId, extended)
                    applyLegacyBlock(
                        "block_user", "USER", caseId, effectId, relatedUserId,
                        displayReason, actorId, extended
                    )
                }
            } else {
                val effectId = insertEffect(caseId, request.userId, effect)
                applyEffect(caseId, effectId, request.userId, displayReason, actorId, effect)
            }
        }

        request.reportIds.distinct().forEach { reportId ->
            jdbcTemplate.update(
                "INSERT INTO moderation_case_reports(case_id, report_id) VALUES (?, ?)",
                caseId,
                reportId
            )
            val overridden = request.overrideCaseId != null
            jdbcTemplate.update(
                """
                UPDATE report_log SET status = ?, resolved_at = NOW(),
                    resolved_by = ?, resolution_note = ?
                WHERE report_id = ?
                """.trimIndent(),
                if (overridden) "OVERRIDDEN" else "RESOLVED",
                actorId,
                if (overridden) "제재 #$caseId 로 변경 (기존 #${request.overrideCaseId})"
                else "제재 #$caseId 연결",
                reportId
            )
        }
        jdbcTemplate.update(
            """
            INSERT INTO moderation_case_events
                (case_id, event_type, actor_type, actor_id, payload, request_ip_hash)
            VALUES (?, 'ISSUED', 'ADMIN', ?, ?, ?)
            """.trimIndent(),
            caseId,
            actorId,
            json(mapOf("requestId" to request.requestId, "categories" to request.categoryCodes)),
            auditRequestIpHash(requestIpHash)
        )
        jdbcTemplate.update(
            """
            INSERT INTO moderation_command_requests
                (request_id, command_type, target_key, actor_id, response_status, response_body)
            VALUES (?, 'ISSUE_SANCTION', ?, ?, 201, ?)
            """.trimIndent(),
            request.requestId,
            request.userId,
            actorId,
            json(mapOf("caseId" to caseId))
        )
        if (request.overrideCaseId == null) {
            queueReportResolutionNotifications(
                caseId,
                request.reportIds,
                request.userId,
                subjectUser.nickname
            )
        }
        val kickUserIds = linkedSetOf<String>()
        if (preview.effects.any {
            it.type in setOf(
                "GAME_RESTRICTION",
                "CHAT_RESTRICTION",
                "NICKNAME_RESET",
                "NICKNAME_CHANGE_RESTRICTION"
            )
        }) {
            kickUserIds.add(request.userId)
        }
        if (preview.effects.any { it.type == "EXTEND_RELATED_RESTRICTION" }) {
            kickUserIds.addAll(relatedUserIds)
        }
        if (kickUserIds.isNotEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    kickUserIds.forEach { gameClientManager.kick(it, "") }
                }
            })
        }
        return SanctionIssueResponse(caseId, preview, false)
    }

    @Transactional
    fun revoke(
        caseId: Long,
        reason: String,
        actorId: String,
        requestIpHash: String?,
        expectedSubjectType: String
    ) {
        val subject = jdbcTemplate.query(
            "SELECT subject_type, subject_user_id, subject_ip_encrypted FROM moderation_cases WHERE case_id = ?",
            { rs, _ ->
                RevocationSubject(
                    rs.getString("subject_type"),
                    rs.getString("subject_user_id"),
                    rs.getBytes("subject_ip_encrypted")
                )
            },
            caseId
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "제재를 찾을 수 없습니다.")
        if (subject.type != expectedSubjectType) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 종류의 제재를 철회할 권한이 없습니다.")
        }
        val affectedUserIds = jdbcTemplate.query(
            "SELECT user_id FROM moderation_case_subjects WHERE case_id = ?",
            { rs, _ -> rs.getString("user_id") },
            caseId
        )
        val updated = jdbcTemplate.update(
            """
            UPDATE moderation_cases SET revoked_at = NOW(), revoked_by = ?, revoke_reason = ?
            WHERE case_id = ? AND revoked_at IS NULL
            """.trimIndent(),
            actorId,
            reason,
            caseId
        )
        if (updated == 0) throw ResponseStatusException(HttpStatus.CONFLICT, "존재하지 않거나 이미 철회된 제재입니다.")

        jdbcTemplate.query(
            """
            SELECT effect_id, effect_type, subject_user_id, parameters,
                   legacy_block_type, legacy_block_id
            FROM moderation_effects WHERE case_id = ? AND revoked_at IS NULL
            """.trimIndent(),
            { rs ->
                val table = when (rs.getString("legacy_block_type")) {
                    "USER" -> "block_user"
                    "CHAT" -> "block_chat"
                    "IP" -> "block_ip"
                    else -> null
                }
                val legacyId = rs.getLong("legacy_block_id").takeUnless { rs.wasNull() }
                if (table != null && legacyId != null) {
                    jdbcTemplate.update("DELETE FROM $table WHERE id = ?", legacyId)
                }
                reverseEffect(
                    rs.getLong("effect_id"),
                    rs.getString("effect_type"),
                    rs.getString("subject_user_id"),
                    objectMapper.readTree(rs.getString("parameters"))
                )
            },
            caseId
        )
        jdbcTemplate.update(
            "UPDATE moderation_effects SET revoked_at = NOW(), apply_status = 'REVOKED' WHERE case_id = ? AND revoked_at IS NULL",
            caseId
        )
        affectedUserIds.forEach {
            rebuildLegacyUserBlock(it)
            rebuildLegacyChatBlock(it)
        }
        if (subject.type == "IP") {
            val encryptedIp = subject.encryptedIp
                ?: throw IllegalStateException("IP 제재에 암호화된 대상 IP가 없습니다.")
            rebuildLegacyIpBlock(ipSubjectCodec.decrypt(encryptedIp))
        } else {
            val subjectId = subject.userId
                ?: throw IllegalStateException("사용자 제재에 대상 ID가 없습니다.")
            val hasActiveNicknameLimit = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS(
                    SELECT 1 FROM moderation_effects
                    WHERE subject_user_id = ? AND effect_type = 'NICKNAME_CHANGE_RESTRICTION'
                      AND revoked_at IS NULL AND (permanent OR ends_at > NOW())
                )
                """.trimIndent(),
                Boolean::class.java,
                subjectId
            )
            if (!hasActiveNicknameLimit) {
                jdbcTemplate.update(
                    "UPDATE users SET \"isLimitModifyNick\" = FALSE WHERE _id = ?",
                    subjectId
                )
            }
        }
        jdbcTemplate.update(
            """
            INSERT INTO moderation_case_events
                (case_id, event_type, actor_type, actor_id, payload, request_ip_hash)
            VALUES (?, 'REVOKED', 'ADMIN', ?, ?, ?)
            """.trimIndent(),
            caseId,
            actorId,
            json(mapOf("reason" to reason)),
            auditRequestIpHash(requestIpHash)
        )
        val disconnectedUsers = affectedUserIds.toMutableSet()
        subject.userId?.let(disconnectedUsers::add)
        if (disconnectedUsers.isNotEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    disconnectedUsers.forEach { gameClientManager.kick(it, "") }
                }
            })
        }
    }

    @Transactional
    fun adjustCounter(userId: String, request: CounterAdjustmentRequest, actorId: String) {
        require(request.value in 0..999) { "누적 횟수는 0에서 999 사이여야 합니다." }
        requireUser(userId)
        policyLoader.current().document.category(request.categoryCode)
        val alreadyProcessed = jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM moderation_command_requests WHERE request_id = ?)",
            Boolean::class.java,
            request.requestId
        )
        if (alreadyProcessed == true) return

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(hashtext(?))",
            { _, _ -> Unit },
            userId
        )
        val previousCounters = currentCounters(userId)
        val previousValue = previousCounters[request.categoryCode] ?: 0
        val adjustedCounters = previousCounters.toMutableMap().apply {
            if (request.value == 0) remove(request.categoryCode)
            else put(request.categoryCode, request.value)
        }
        jdbcTemplate.update(
            """
            INSERT INTO moderation_counter_baselines
                (subject_user_id, counters, last_updated_at, source, reset_at, reset_by)
            VALUES (?, ?, NOW(), 'ADMIN_ADJUSTMENT', NULL, ?)
            ON CONFLICT (subject_user_id) DO UPDATE SET
                counters = EXCLUDED.counters, last_updated_at = NOW(),
                source = EXCLUDED.source, reset_at = NULL, reset_by = EXCLUDED.reset_by
            """.trimIndent(),
            userId,
            json(adjustedCounters),
            actorId
        )
        jdbcTemplate.update(
            """
            INSERT INTO moderation_command_requests
                (request_id, command_type, target_key, actor_id, response_status, response_body)
            VALUES (?, 'ADJUST_COUNTER', ?, ?, 204, ?)
            """.trimIndent(),
            request.requestId,
            "$userId:${request.categoryCode}",
            actorId,
            json(
                mapOf(
                    "reason" to request.reason.trim(),
                    "categoryCode" to request.categoryCode,
                    "previousValue" to previousValue,
                    "adjustedValue" to request.value
                )
            )
        )
    }

    @Transactional
    fun changeNickname(userId: String, request: ModerationNicknameChangeRequest, actorId: String) {
        requireUser(userId)
        if (commandAlreadyProcessed(request.requestId)) return
        val nickname = request.nickname?.trim().takeUnless { it.isNullOrBlank() }
            ?: "바른별명#${userId.substringAfter('-', userId).take(5)}"
        require(nickname.isNotBlank() && nickname.length in 2..16 && nickname.none(Char::isISOControl)) {
            "별명은 제어 문자를 제외한 2~16자로 입력해 주세요."
        }
        val meanable = nickname.replace(Regex("[-_ ]*"), "").lowercase()
        val duplicated = jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM users WHERE _id <> ? AND \"meanableNick\" = ?)",
            Boolean::class.java,
            userId,
            meanable
        ) == true
        require(!duplicated) { "이미 사용 중인 별명입니다." }
        val oldNickname = jdbcTemplate.queryForObject(
            "SELECT nickname FROM users WHERE _id = ? FOR UPDATE",
            String::class.java,
            userId
        )
        jdbcTemplate.update(
            "UPDATE users SET nickname = ?, \"meanableNick\" = ?, exordial = '' WHERE _id = ?",
            nickname,
            meanable,
            userId
        )
        recordCommand(
            request.requestId,
            "CHANGE_NICKNAME",
            userId,
            actorId,
            mapOf("reason" to request.reason.trim(), "oldNickname" to oldNickname, "nickname" to nickname)
        )
        disconnectAfterCommit(setOf(userId))
    }

    @Transactional
    fun disconnectUser(userId: String, request: ModerationDisconnectRequest, actorId: String) {
        requireUser(userId)
        if (commandAlreadyProcessed(request.requestId)) return
        recordCommand(
            request.requestId,
            "DISCONNECT_USER",
            userId,
            actorId,
            mapOf("reason" to request.reason.trim())
        )
        disconnectAfterCommit(setOf(userId))
    }

    @Transactional
    fun queueNotification(userId: String, request: ModerationNotificationRequest, actorId: String) {
        requireUser(userId)
        if (commandAlreadyProcessed(request.requestId)) return
        val message = request.message.trim()
        require(message.isNotEmpty() && message.length <= 2000 && message.none { it == '\u0000' }) {
            "안내 내용은 1자 이상 2,000자 이하로 입력해 주세요."
        }
        val rawFlags = jdbcTemplate.queryForObject(
            "SELECT flags::TEXT FROM users WHERE _id = ? FOR UPDATE",
            String::class.java,
            userId
        )
        val flags = objectMapper.readTree(rawFlags)
            .takeIf { it.isObject }?.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            ?: objectMapper.createObjectNode()
        val currentEntry = flags.get("notifications")
        val currentValue = if (currentEntry?.isObject == true && currentEntry.has("value")) {
            currentEntry.get("value")
        } else currentEntry
        val queue = objectMapper.createArrayNode()
        when {
            currentValue == null || currentValue.isNull || currentValue.isMissingNode -> Unit
            currentValue.isArray -> currentValue.forEach { queue.add(it) }
            else -> queue.add(currentValue)
        }
        require(queue.size() < 50) { "예약된 안내가 너무 많습니다. 기존 안내가 전달된 후 다시 시도해 주세요." }

        val notificationId = request.requestId.toString()
        queue.add(objectMapper.createObjectNode().apply {
            put("id", notificationId)
            put("message", message)
            put("createdAt", Instant.now().epochSecond)
        })
        flags.set<JsonNode>("notifications", objectMapper.createObjectNode().apply {
            set<JsonNode>("value", queue)
            put("time", Instant.now().epochSecond)
        })
        jdbcTemplate.update(
            "UPDATE users SET flags = CAST(? AS jsonb)::json WHERE _id = ?",
            objectMapper.writeValueAsString(flags),
            userId
        )
        recordCommand(
            request.requestId,
            "SEND_USER_NOTIFICATION",
            userId,
            actorId,
            mapOf(
                "reason" to request.reason.trim(),
                "notificationId" to notificationId,
                "message" to message
            )
        )
    }

    private fun queueReportResolutionNotifications(
        caseId: Long,
        reportIds: List<Long>,
        fallbackTargetId: String?,
        fallbackTargetNickname: String?
    ) {
        val ids = reportIds.distinct()
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        val rows = jdbcTemplate.query(
            """
            SELECT r.report_id, r.reporter_id, r.target_id, r.reason,
                   COALESCE(CASE WHEN r.target_id = ? THEN ? END, u.nickname) AS target_nickname
            FROM report_log r
            JOIN users reporter ON reporter._id = r.reporter_id
            LEFT JOIN users u ON u._id = r.target_id
            WHERE r.report_id IN ($placeholders)
            ORDER BY r.report_id
            """.trimIndent(),
            { rs, _ ->
                ReportNotificationTarget(
                    reportId = rs.getLong("report_id"),
                    reporterId = rs.getString("reporter_id"),
                    targetId = rs.getString("target_id"),
                    targetNickname = rs.getString("target_nickname"),
                    reportReason = rs.getString("reason")
                )
            },
            *(listOf<Any?>(fallbackTargetId, fallbackTargetNickname) + ids).toTypedArray()
        )
        rows.filterNot { it.reporterId.startsWith("guest__") }
            .groupBy { it.reporterId }
            .forEach { (reporterId, targets) ->
                val sortedTargets = targets.sortedBy { it.reportId }
                val notificationId = UUID.nameUUIDFromBytes(
                    "moderation-report-resolution:${sortedTargets.joinToString(",") { it.reportId.toString() }}"
                        .toByteArray(Charsets.UTF_8)
                )
                val appended = appendUserNotification(
                    reporterId,
                    notificationId,
                    sortedTargets.joinToString("\n\n") { target ->
                        reportResolutionNotification(
                            target.reportReason,
                            maskedReportTarget(target.targetNickname, target.targetId)
                        )
                    }
                )
                if (!appended) return@forEach
                recordCommand(
                    notificationId,
                    "SEND_REPORT_RESOLUTION_NOTIFICATION",
                    reporterId,
                    "SYSTEM",
                    mapOf(
                        "caseId" to caseId,
                        "reportIds" to sortedTargets.map { it.reportId },
                        "items" to sortedTargets.map { target ->
                            mapOf(
                                "reportId" to target.reportId,
                                "reason" to target.reportReason,
                                "target" to maskedReportTarget(target.targetNickname, target.targetId)
                            )
                        }
                    )
                )
            }
    }

    private fun appendUserNotification(userId: String, notificationId: UUID, message: String): Boolean {
        if (commandAlreadyProcessed(notificationId)) return false
        val rawFlags = jdbcTemplate.queryForObject(
            "SELECT flags::TEXT FROM users WHERE _id = ? FOR UPDATE",
            String::class.java,
            userId
        )
        val flags = objectMapper.readTree(rawFlags)
            .takeIf { it.isObject }?.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            ?: objectMapper.createObjectNode()
        val currentEntry = flags.get("notifications")
        val currentValue = if (currentEntry?.isObject == true && currentEntry.has("value")) {
            currentEntry.get("value")
        } else currentEntry
        val queue = objectMapper.createArrayNode()
        when {
            currentValue == null || currentValue.isNull || currentValue.isMissingNode -> Unit
            currentValue.isArray -> currentValue.forEach { queue.add(it) }
            else -> queue.add(currentValue)
        }
        if (queue.size() >= 50) return false
        queue.add(objectMapper.createObjectNode().apply {
            put("id", notificationId.toString())
            put("message", message)
            put("createdAt", Instant.now().epochSecond)
        })
        flags.set<JsonNode>("notifications", objectMapper.createObjectNode().apply {
            set<JsonNode>("value", queue)
            put("time", Instant.now().epochSecond)
        })
        jdbcTemplate.update(
            "UPDATE users SET flags = CAST(? AS jsonb)::json WHERE _id = ?",
            objectMapper.writeValueAsString(flags),
            userId
        )
        return true
    }

    private fun reportResolutionNotification(reason: String, maskedTarget: String) =
        "최근 '$reason'(으)로 신고한 사용자($maskedTarget)를 확인한 결과 이용 제한 조치가 완료되었습니다.\n" +
            "앞으로도 적극적인 신고로 건전한 게임 문화 양성에 많은 참여를 부탁드립니다. 감사합니다."

    private fun maskedReportTarget(nickname: String?, targetId: String): String {
        val value = nickname?.trim().takeUnless { it.isNullOrEmpty() }
            ?: if (targetId.startsWith("guest__")) "손님" else "사용자"
        val first = value.codePoints().findFirst().orElse('*'.code)
        return String(Character.toChars(first)) + "****"
    }

    @Transactional
    fun updateFlags(userId: String, request: ModerationFlagsUpdateRequest, actorId: String) {
        requireUser(userId)
        if (commandAlreadyProcessed(request.requestId)) return
        require(request.flags.size <= 500) { "플래그는 최대 500개까지 관리할 수 있습니다." }
        val keys = request.flags.map { it.key.trim() }
        require(keys.all { it.isNotEmpty() && it.length <= 100 && it.none(Char::isISOControl) }) {
            "플래그 키는 제어 문자를 제외한 1~100자로 입력해 주세요."
        }
        require(keys.distinct().size == keys.size) { "중복된 플래그 키가 있습니다." }
        val flags = objectMapper.createObjectNode()
        request.flags.forEachIndexed { index, entry ->
            val item = objectMapper.createObjectNode()
            item.set<JsonNode>("value", entry.value)
            if (entry.timed) item.put("time", entry.time ?: Instant.now().epochSecond)
            flags.set<JsonNode>(keys[index], item)
        }
        jdbcTemplate.update(
            "UPDATE users SET flags = CAST(? AS jsonb)::json WHERE _id = ?",
            objectMapper.writeValueAsString(flags),
            userId
        )
        recordCommand(
            request.requestId,
            "UPDATE_USER_FLAGS",
            userId,
            actorId,
            mapOf("reason" to request.reason.trim(), "flagCount" to flags.size(), "keys" to keys)
        )
        disconnectAfterCommit(setOf(userId))
    }

    private fun commandAlreadyProcessed(requestId: UUID): Boolean = jdbcTemplate.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM moderation_command_requests WHERE request_id = ?)",
        Boolean::class.java,
        requestId
    ) == true

    private fun recordCommand(
        requestId: UUID,
        commandType: String,
        targetKey: String,
        actorId: String,
        responseBody: Any
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO moderation_command_requests
                (request_id, command_type, target_key, actor_id, response_status, response_body)
            VALUES (?, ?, ?, ?, 204, ?)
            """.trimIndent(),
            requestId,
            commandType,
            targetKey,
            actorId,
            json(responseBody)
        )
    }

    private fun disconnectAfterCommit(userIds: Set<String>) {
        if (userIds.isEmpty()) return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                userIds.forEach { gameClientManager.kick(it, "") }
            }
        })
    }

    @Transactional
    fun resolveReport(reportId: Long, request: ReportResolutionRequest, actorId: String) {
        require(request.status == "REJECTED") {
            "신고 처리는 제재 사건 발급으로만 완료할 수 있으며, 이 API는 반려만 허용합니다."
        }
        val updated = jdbcTemplate.update(
            """
            UPDATE report_log SET status = ?, resolved_at = NOW(),
                resolved_by = ?, resolution_note = ?
            WHERE report_id = ? AND status = 'PENDING'
            """.trimIndent(),
            request.status,
            actorId,
            request.note,
            reportId
        )
        if (updated == 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "존재하지 않거나 이미 처리된 신고입니다.")
        }
    }

    private fun insertCase(
        request: SanctionIssueRequest,
        actorId: String,
        preview: ModerationPolicyPreview,
        displayReason: String
    ): Long {
        val keyHolder: KeyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement(
                """
                INSERT INTO moderation_cases
                    (request_id, subject_type, subject_user_id, primary_category_code,
                     policy_id, policy_digest, source_service, occurred_at, issued_by,
                     evidence_text, summary, overrides_case_id)
                VALUES (?, 'USER', ?, ?, ?, ?, 'ADMIN', ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf("case_id")
            ).apply {
                setObject(1, request.requestId)
                setString(2, request.userId)
                setString(3, preview.primaryCategoryCode)
                setString(4, preview.policyId)
                setString(5, preview.policyDigest)
                setTimestamp(6, Timestamp.from(request.occurredAt))
                setString(7, actorId)
                setString(8, request.evidenceText)
                setString(9, displayReason)
                setObject(10, request.overrideCaseId)
            }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    private fun insertIpCase(
        request: IpSanctionIssueRequest,
        ip: String,
        ipHash: String,
        actorId: String,
        preview: ModerationPolicyPreview,
        displayReason: String
    ): Long {
        val keyHolder: KeyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement(
                """
                INSERT INTO moderation_cases
                    (request_id, subject_type, subject_ip_encrypted, subject_ip_hash,
                     primary_category_code, policy_id, policy_digest, source_service,
                     occurred_at, issued_by, evidence_text, summary, overrides_case_id)
                VALUES (?, 'IP', ?, ?, ?, ?, ?, 'ADMIN', ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf("case_id")
            ).apply {
                setObject(1, request.requestId)
                setBytes(2, ipSubjectCodec.encrypt(ip))
                setString(3, ipHash)
                setString(4, preview.primaryCategoryCode)
                setString(5, preview.policyId)
                setString(6, preview.policyDigest)
                setTimestamp(7, Timestamp.from(request.occurredAt))
                setString(8, actorId)
                setString(9, request.evidenceText)
                setString(10, displayReason)
                setObject(11, request.overrideCaseId)
            }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    private fun insertEffect(caseId: Long, userId: String, effect: ResolvedPolicyEffect): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement(
                """
                INSERT INTO moderation_effects
                    (case_id, subject_user_id, effect_type, starts_at, ends_at, permanent, parameters)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf("effect_id")
            ).apply {
                setLong(1, caseId)
                setString(2, userId)
                setString(3, effect.type)
                setTimestamp(4, Timestamp.from(effect.startsAt))
                setTimestamp(5, effect.endsAt?.let(Timestamp::from))
                setBoolean(6, effect.permanent)
                setObject(7, json(effect.parameters))
            }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    private fun insertIpEffect(caseId: Long, effect: ResolvedPolicyEffect): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement(
                """
                INSERT INTO moderation_effects
                    (case_id, subject_user_id, effect_type, starts_at, ends_at, permanent, parameters)
                VALUES (?, NULL, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf("effect_id")
            ).apply {
                setLong(1, caseId)
                setString(2, effect.type)
                setTimestamp(3, Timestamp.from(effect.startsAt))
                setTimestamp(4, effect.endsAt?.let(Timestamp::from))
                setBoolean(5, effect.permanent)
                setObject(6, json(effect.parameters))
            }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    private fun applyEffect(
        caseId: Long,
        effectId: Long,
        userId: String,
        reason: String,
        actorId: String,
        effect: ResolvedPolicyEffect
    ) {
        when (effect.type) {
            "GAME_RESTRICTION" -> applyLegacyBlock("block_user", "USER", caseId, effectId, userId, reason, actorId, effect)
            "CHAT_RESTRICTION" -> applyLegacyBlock("block_chat", "CHAT", caseId, effectId, userId, reason, actorId, effect)
            "RESOURCE_ADJUSTMENT" -> {
                val percent = (effect.parameters["percent"] as Number).toInt().coerceIn(0, 100)
                val before = jdbcTemplate.query(
                    """
                    SELECT money, COALESCE((kkutu->>'score')::bigint, 0) AS score
                    FROM users WHERE _id = ? FOR UPDATE
                    """.trimIndent(),
                    { rs, _ -> rs.getLong("money") to rs.getLong("score") },
                    userId
                ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
                val newMoney = BigDecimal.valueOf(before.first)
                    .multiply(BigDecimal.valueOf((100 - percent).toLong()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact()
                val newScore = BigDecimal.valueOf(before.second)
                    .multiply(BigDecimal.valueOf((100 - percent).toLong()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact()
                jdbcTemplate.update(
                    """
                    UPDATE users SET
                        money = ?,
                        kkutu = jsonb_set(kkutu::jsonb, '{score}', to_jsonb(?::bigint))::json
                    WHERE _id = ?
                    """.trimIndent(),
                    newMoney,
                    newScore,
                    userId
                )
                storeEffectRollback(effectId, mapOf(
                    "moneyDelta" to before.first - newMoney,
                    "scoreDelta" to before.second - newScore
                ))
                markApplied(effectId)
            }
            "NICKNAME_RESET" -> {
                val policyNickname = "바른별명#${userId.substringAfter('-', userId).take(5)}"
                val before = jdbcTemplate.query(
                    "SELECT nickname, \"meanableNick\", exordial FROM users WHERE _id = ? FOR UPDATE",
                    { rs, _ -> mapOf(
                        "nickname" to rs.getString("nickname"),
                        "meanableNick" to rs.getString("meanableNick"),
                        "exordial" to rs.getString("exordial"),
                        "appliedNickname" to policyNickname
                    ) },
                    userId
                ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
                jdbcTemplate.update(
                    """
                    UPDATE users SET nickname = ?, "meanableNick" = LOWER(REPLACE(?, ' ', '')),
                        exordial = ''
                    WHERE _id = ?
                    """.trimIndent(),
                    policyNickname,
                    policyNickname,
                    userId
                )
                storeEffectRollback(effectId, before)
                markApplied(effectId)
            }
            "NICKNAME_CHANGE_RESTRICTION" -> {
                jdbcTemplate.update(
                    "UPDATE users SET \"isLimitModifyNick\" = TRUE WHERE _id = ?",
                    userId
                )
                markApplied(effectId)
            }
            "WARNING" -> markApplied(effectId)
            else -> jdbcTemplate.update(
                "UPDATE moderation_effects SET apply_status = 'PENDING', failure_code = 'MANUAL_ACTION_REQUIRED' WHERE effect_id = ?",
                effectId
            )
        }
    }

    private fun resolveRelatedExtension(
        relatedUserId: String,
        effect: ResolvedPolicyEffect
    ): ResolvedPolicyEffect {
        val current = jdbcTemplate.query(
            """
            SELECT pardon_time FROM block_user WHERE user_id = ?
            ORDER BY CASE WHEN pardon_time IS NULL THEN 0 ELSE 1 END, pardon_time DESC
            LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                val timestamp = rs.getTimestamp("pardon_time")
                if (timestamp == null) Pair(true, null) else Pair(false, timestamp.toInstant())
            },
            relatedUserId
        ).firstOrNull()
        if (effect.permanent || current?.first == true) {
            return effect.copy(endsAt = null, permanent = true)
        }
        val durationSeconds = (effect.endsAt?.epochSecond ?: effect.startsAt.epochSecond) -
            effect.startsAt.epochSecond
        val base = listOfNotNull(effect.startsAt, current?.second).maxOrNull() ?: effect.startsAt
        return effect.copy(endsAt = base.plusSeconds(durationSeconds), permanent = false)
    }

    private fun applyLegacyBlock(
        table: String,
        type: String,
        caseId: Long,
        effectId: Long,
        userId: String,
        reason: String,
        actorId: String,
        effect: ResolvedPolicyEffect
    ) {
        jdbcTemplate.update("DELETE FROM $table WHERE user_id = ?", userId)
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement(
                """
                INSERT INTO $table(user_id, time, pardon_time, reason, punish_from, admin)
                VALUES (?, ?, ?, ?, 'DISCORD', ?)
                """.trimIndent(),
                arrayOf("id")
            ).apply {
                setString(1, userId)
                setTimestamp(2, Timestamp.from(effect.startsAt))
                setTimestamp(3, effect.endsAt?.let(Timestamp::from))
                setString(4, "[제재 #$caseId] $reason")
                setString(5, actorId)
            }
        }, keyHolder)
        jdbcTemplate.update(
            """
            UPDATE moderation_effects SET apply_status = 'APPLIED', applied_at = NOW(),
                legacy_block_type = ?, legacy_block_id = ?
            WHERE effect_id = ?
            """.trimIndent(),
            type,
            keyHolder.key!!.toLong(),
            effectId
        )
    }

    private fun applyLegacyIpBlock(
        caseId: Long,
        effectId: Long,
        ip: String,
        reason: String,
        actorId: String,
        effect: ResolvedPolicyEffect
    ) {
        require(effect.type in setOf("GUEST_ACCESS_RESTRICTION", "IP_RESTRICTION")) {
            "지원하지 않는 IP 제재 효과입니다: ${effect.type}"
        }
        jdbcTemplate.update("DELETE FROM block_ip WHERE ip_address = ?", ip)
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement(
                """
                INSERT INTO block_ip
                    (ip_address, time, pardon_time, reason, punish_from, admin, only_guest_punish)
                VALUES (?, ?, ?, ?, 'DISCORD', ?, ?)
                """.trimIndent(),
                arrayOf("id")
            ).apply {
                setString(1, ip)
                setTimestamp(2, Timestamp.from(effect.startsAt))
                setTimestamp(3, effect.endsAt?.let(Timestamp::from))
                setString(4, "[제재 #$caseId] $reason")
                setString(5, actorId)
                setBoolean(6, effect.type == "GUEST_ACCESS_RESTRICTION")
            }
        }, keyHolder)
        jdbcTemplate.update(
            """
            UPDATE moderation_effects SET apply_status = 'APPLIED', applied_at = NOW(),
                legacy_block_type = 'IP', legacy_block_id = ? WHERE effect_id = ?
            """.trimIndent(),
            keyHolder.key!!.toLong(),
            effectId
        )
    }

    private fun rebuildLegacyUserBlock(userId: String) {
        jdbcTemplate.update("DELETE FROM block_user WHERE user_id = ?", userId)
        val active = jdbcTemplate.query(
            """
            SELECT e.effect_id, e.case_id, e.starts_at, e.ends_at, e.permanent,
                   c.summary, c.issued_by
            FROM moderation_effects e
            JOIN moderation_cases c ON c.case_id = e.case_id
            WHERE e.subject_user_id = ?
              AND e.effect_type IN ('GAME_RESTRICTION', 'EXTEND_RELATED_RESTRICTION')
              AND e.revoked_at IS NULL AND c.revoked_at IS NULL
              AND e.starts_at <= NOW() AND (e.permanent OR e.ends_at > NOW())
            ORDER BY e.permanent DESC, e.ends_at DESC NULLS FIRST, e.effect_id DESC
            LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                RebuildBlock(
                    rs.getLong("effect_id"),
                    rs.getLong("case_id"),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at")?.toInstant(),
                    rs.getBoolean("permanent"),
                    rs.getString("summary"),
                    rs.getString("issued_by")
                )
            },
            userId
        ).firstOrNull() ?: return

        val resolved = ResolvedPolicyEffect(
            type = "GAME_RESTRICTION",
            startsAt = active.startsAt,
            endsAt = active.endsAt,
            permanent = active.permanent,
            parameters = emptyMap()
        )
        applyLegacyBlock(
            "block_user",
            "USER",
            active.caseId,
            active.effectId,
            userId,
            active.reason,
            active.actorId,
            resolved
        )
    }

    private fun rebuildLegacyChatBlock(userId: String) {
        jdbcTemplate.update("DELETE FROM block_chat WHERE user_id = ?", userId)
        val active = jdbcTemplate.query(
            """
            SELECT e.effect_id, e.case_id, e.starts_at, e.ends_at, e.permanent,
                   c.summary, c.issued_by
            FROM moderation_effects e
            JOIN moderation_cases c ON c.case_id = e.case_id
            WHERE e.subject_user_id = ? AND e.effect_type = 'CHAT_RESTRICTION'
              AND e.revoked_at IS NULL AND c.revoked_at IS NULL
              AND e.starts_at <= NOW() AND (e.permanent OR e.ends_at > NOW())
            ORDER BY e.permanent DESC, e.ends_at DESC NULLS FIRST, e.effect_id DESC
            LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                RebuildBlock(
                    rs.getLong("effect_id"),
                    rs.getLong("case_id"),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at")?.toInstant(),
                    rs.getBoolean("permanent"),
                    rs.getString("summary"),
                    rs.getString("issued_by")
                )
            },
            userId
        ).firstOrNull() ?: return
        applyLegacyBlock(
            "block_chat",
            "CHAT",
            active.caseId,
            active.effectId,
            userId,
            active.reason,
            active.actorId,
            ResolvedPolicyEffect(
                "CHAT_RESTRICTION",
                active.startsAt,
                active.endsAt,
                active.permanent,
                emptyMap()
            )
        )
    }

    private fun rebuildLegacyIpBlock(ip: String) {
        jdbcTemplate.update("DELETE FROM block_ip WHERE ip_address = ?", ip)
        val active = jdbcTemplate.query(
            """
            SELECT e.effect_id, e.case_id, e.effect_type, e.starts_at, e.ends_at,
                   e.permanent, c.summary, c.issued_by
            FROM moderation_effects e
            JOIN moderation_cases c ON c.case_id = e.case_id
            WHERE c.subject_type = 'IP' AND c.subject_ip_hash = ?
              AND e.effect_type IN ('GUEST_ACCESS_RESTRICTION', 'IP_RESTRICTION')
              AND e.revoked_at IS NULL AND c.revoked_at IS NULL
              AND e.starts_at <= NOW() AND (e.permanent OR e.ends_at > NOW())
            ORDER BY CASE WHEN e.effect_type = 'IP_RESTRICTION' THEN 1 ELSE 0 END DESC,
                     e.permanent DESC, e.ends_at DESC NULLS FIRST, e.effect_id DESC
            LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                RebuildIpBlock(
                    rs.getLong("effect_id"),
                    rs.getLong("case_id"),
                    rs.getString("effect_type"),
                    rs.instant("starts_at")!!,
                    rs.instant("ends_at"),
                    rs.getBoolean("permanent"),
                    rs.getString("summary"),
                    rs.getString("issued_by")
                )
            },
            ipSubjectCodec.hash(ip)
        ).firstOrNull() ?: return
        applyLegacyIpBlock(
            active.caseId,
            active.effectId,
            ip,
            active.reason,
            active.actorId,
            ResolvedPolicyEffect(
                active.effectType,
                active.startsAt,
                active.endsAt,
                active.permanent,
                emptyMap()
            )
        )
    }

    private fun markApplied(effectId: Long) {
        jdbcTemplate.update(
            "UPDATE moderation_effects SET apply_status = 'APPLIED', applied_at = NOW() WHERE effect_id = ?",
            effectId
        )
    }

    private fun storeEffectRollback(effectId: Long, rollback: Any) {
        jdbcTemplate.update(
            "UPDATE moderation_effects SET parameters = parameters || ? WHERE effect_id = ?",
            json(mapOf("_rollback" to rollback)),
            effectId
        )
    }

    private fun reverseEffect(effectId: Long, effectType: String, userId: String?, parameters: JsonNode) {
        val rollback = parameters.path("_rollback")
        if (rollback.isMissingNode || rollback.isNull || userId == null) return
        when (effectType) {
            "RESOURCE_ADJUSTMENT" -> {
                val moneyDelta = rollback.path("moneyDelta").asLong(0)
                val scoreDelta = rollback.path("scoreDelta").asLong(0)
                jdbcTemplate.update(
                    """
                    UPDATE users SET money = money + ?,
                        kkutu = jsonb_set(
                            kkutu::jsonb, '{score}',
                            to_jsonb(COALESCE((kkutu->>'score')::bigint, 0) + ?)
                        )::json
                    WHERE _id = ?
                    """.trimIndent(),
                    moneyDelta,
                    scoreDelta,
                    userId
                )
            }
            "NICKNAME_RESET" -> {
                val appliedNickname = rollback.path("appliedNickname").asText("")
                if (appliedNickname.isBlank()) return
                fun nullableText(field: String): String? = rollback.get(field)
                    ?.takeUnless { it.isNull }
                    ?.asText()
                jdbcTemplate.update(
                    """
                    UPDATE users SET nickname = ?, "meanableNick" = ?, exordial = ?
                    WHERE _id = ? AND nickname IS NOT DISTINCT FROM ?
                    """.trimIndent(),
                    nullableText("nickname"),
                    nullableText("meanableNick"),
                    nullableText("exordial"),
                    userId,
                    appliedNickname
                )
            }
        }
        jdbcTemplate.update(
            """
            INSERT INTO moderation_case_events
                (case_id, event_type, actor_type, payload)
            SELECT case_id, 'EFFECT_REVERSED', 'SYSTEM', ? FROM moderation_effects WHERE effect_id = ?
            """.trimIndent(),
            json(mapOf("effectId" to effectId, "effectType" to effectType)),
            effectId
        )
    }

    private fun currentCounters(userId: String, excludeCaseId: Long? = null): Map<String, Int> {
        val baseline = jdbcTemplate.query(
            """
            SELECT counters, last_updated_at, source, reset_at
            FROM moderation_counter_baselines WHERE subject_user_id = ?
            """.trimIndent(),
            { rs, _ ->
                CounterBaseline(
                    counters = objectMapper.readValue(
                        rs.getString("counters"),
                        object : TypeReference<Map<String, Int>>() {}
                    ),
                    lastUpdatedAt = rs.getTimestamp("last_updated_at")?.toInstant(),
                    source = rs.getString("source"),
                    resetAt = rs.getTimestamp("reset_at")?.toInstant()
                )
            },
            userId
        ).firstOrNull()
        val isAdjustment = baseline?.source == "ADMIN_ADJUSTMENT"
        val since = if (isAdjustment) baseline?.lastUpdatedAt else baseline?.resetAt
        val arguments = mutableListOf<Any>(userId)
        val excludeClause = if (excludeCaseId == null) "" else {
            arguments.add(excludeCaseId)
            " AND c.case_id <> ?"
        }
        val sinceClause = if (since == null) "" else {
            arguments.add(Timestamp.from(since))
            " AND c.issued_at > ?"
        }
        val issuedCounters = jdbcTemplate.query(
            """
            SELECT v.category_code, COUNT(*) AS count
            FROM moderation_case_violations v
            JOIN moderation_cases c ON c.case_id = v.case_id
            WHERE c.subject_user_id = ? AND c.revoked_at IS NULL
              AND v.category_code <> '99'$excludeClause$sinceClause
            GROUP BY v.category_code
            """.trimIndent(),
            { rs, _ -> rs.getString("category_code") to rs.getInt("count") },
            *arguments.toTypedArray()
        ).toMap()
        val result = if (isAdjustment) baseline!!.counters.toMutableMap() else mutableMapOf()
        issuedCounters.forEach { (code, count) -> result[code] = (result[code] ?: 0) + count }
        return result.filterValues { it > 0 }
    }

    private fun currentIpCounters(ipHash: String, excludeCaseId: Long? = null): Map<String, Int> {
        val arguments = mutableListOf<Any>(ipHash)
        val excludeClause = if (excludeCaseId == null) "" else {
            arguments.add(excludeCaseId)
            " AND c.case_id <> ?"
        }
        return jdbcTemplate.query(
        """
        SELECT v.category_code, COUNT(*) AS count
        FROM moderation_case_violations v
        JOIN moderation_cases c ON c.case_id = v.case_id
        WHERE c.subject_type = 'IP' AND c.subject_ip_hash = ?
          AND c.revoked_at IS NULL AND v.category_code <> '99'$excludeClause
        GROUP BY v.category_code
        """.trimIndent(),
        { rs, _ -> rs.getString("category_code") to rs.getInt("count") },
        *arguments.toTypedArray()
        ).toMap()
    }

    private fun ipOffenseCount(ipHash: String, excludeCaseId: Long? = null): Int {
        val arguments = mutableListOf<Any>(ipHash)
        val excludeClause = if (excludeCaseId == null) "" else {
            arguments.add(excludeCaseId)
            " AND case_id <> ?"
        }
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM moderation_cases
            WHERE subject_type = 'IP' AND subject_ip_hash = ? AND revoked_at IS NULL$excludeClause
            """.trimIndent(),
            Int::class.java,
            *arguments.toTypedArray()
        )
    }

    private fun history(userId: String, limit: Int): List<ModerationCaseSummary> =
        jdbcTemplate.query(
            """
            SELECT case_id, primary_category_code, summary, occurred_at,
                   issued_at, issued_by, revoked_at
            FROM moderation_cases
            WHERE subject_user_id = ?
            ORDER BY issued_at DESC, case_id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val caseId = rs.getLong("case_id")
                ModerationCaseSummary(
                    caseId,
                    rs.getString("primary_category_code"),
                    rs.getString("summary"),
                    rs.instant("occurred_at")!!,
                    rs.instant("issued_at")!!,
                    rs.getString("issued_by"),
                    rs.instant("revoked_at"),
                    effects(caseId)
                )
            },
            userId,
            limit
        )

    private fun ipHistory(ipHash: String, limit: Int): List<ModerationCaseSummary> =
        jdbcTemplate.query(
            """
            SELECT case_id, primary_category_code, summary, occurred_at,
                   issued_at, issued_by, revoked_at
            FROM moderation_cases
            WHERE subject_type = 'IP' AND subject_ip_hash = ?
            ORDER BY issued_at DESC, case_id DESC LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val caseId = rs.getLong("case_id")
                ModerationCaseSummary(
                    caseId,
                    rs.getString("primary_category_code"),
                    rs.getString("summary"),
                    rs.instant("occurred_at")!!,
                    rs.instant("issued_at")!!,
                    rs.getString("issued_by"),
                    rs.instant("revoked_at"),
                    effects(caseId)
                )
            },
            ipHash,
            limit
        )

    private fun effects(caseId: Long): List<ModerationEffectSummary> =
        jdbcTemplate.query(
            """
            SELECT effect_type, starts_at, ends_at, permanent, apply_status
            FROM moderation_effects WHERE case_id = ? ORDER BY effect_id
            """.trimIndent(),
            { rs, _ ->
                ModerationEffectSummary(
                    rs.getString("effect_type"),
                    rs.instant("starts_at")!!,
                    rs.instant("ends_at"),
                    rs.getBoolean("permanent"),
                    rs.getString("apply_status")
                )
            },
            caseId
        )

    private fun reports(userId: String, window: Int, anchor: Instant): ModerationReportPage {
        require(window in 0..100) { "신고 조회 구간은 0에서 100 사이여야 합니다." }
        val fromDays = window * 90
        val toDays = (window + 1) * 90
        val items = jdbcTemplate.query(
            """
            SELECT report_id, target_id, host(target_ip) AS target_ip,
                   category_code, reason, detail, status, time
            FROM report_log
            WHERE target_id = ?
              AND time >= CAST(? AS TIMESTAMP) - (? * INTERVAL '1 day')
              AND time < CAST(? AS TIMESTAMP) - (? * INTERVAL '1 day')
            ORDER BY time DESC, report_id DESC
            """.trimIndent(),
            { rs, _ ->
                ModerationReportSummary(
                    rs.getLong("report_id"),
                    rs.getString("category_code"),
                    rs.getString("reason"),
                    rs.getString("detail"),
                    rs.getString("status"),
                    rs.getTimestamp("time").toInstant(),
                    rs.getString("target_id"),
                    rs.getString("target_ip")
                )
            },
            userId,
            Timestamp.from(anchor),
            toDays,
            Timestamp.from(anchor),
            fromDays
        )
        val hasMore = jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM report_log WHERE target_id = ? AND time < CAST(? AS TIMESTAMP) - (? * INTERVAL '1 day'))",
            Boolean::class.java,
            userId,
            Timestamp.from(anchor),
            toDays
        ) == true
        return ModerationReportPage(window, fromDays, toDays, items, hasMore, anchor)
    }

    private fun ipReports(ip: String, window: Int, anchor: Instant): ModerationReportPage {
        require(window in 0..100) { "신고 조회 구간은 0에서 100 사이여야 합니다." }
        val fromDays = window * 90
        val toDays = (window + 1) * 90
        val relation = """
            (report_log.target_ip = CAST(? AS inet)
            OR EXISTS(
                SELECT 1 FROM connection_log cl
                WHERE cl.user_id = report_log.target_id AND cl.user_ip = ?
            ))
        """.trimIndent()
        val items = jdbcTemplate.query(
            """
            SELECT report_id, target_id, host(target_ip) AS target_ip,
                   category_code, reason, detail, status, time
            FROM report_log
            WHERE $relation
              AND time >= CAST(? AS TIMESTAMP) - (? * INTERVAL '1 day')
              AND time < CAST(? AS TIMESTAMP) - (? * INTERVAL '1 day')
            ORDER BY time DESC, report_id DESC
            """.trimIndent(),
            { rs, _ -> mapReportSummary(rs) },
            ip,
            ip,
            Timestamp.from(anchor),
            toDays,
            Timestamp.from(anchor),
            fromDays
        )
        val hasMore = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
                SELECT 1 FROM report_log
                WHERE $relation
                  AND time < CAST(? AS TIMESTAMP) - (? * INTERVAL '1 day')
            )
            """.trimIndent(),
            Boolean::class.java,
            ip,
            ip,
            Timestamp.from(anchor),
            toDays
        ) == true
        return ModerationReportPage(window, fromDays, toDays, items, hasMore, anchor)
    }

    private fun validateReports(reportIds: List<Long>, userId: String) {
        if (reportIds.isEmpty()) return
        val ids = reportIds.distinct()
        val placeholders = ids.joinToString(",") { "?" }
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM report_log WHERE target_id = ? AND report_id IN ($placeholders)",
            Int::class.java,
            *(listOf<Any>(userId) + ids).toTypedArray()
        )
        require(count == ids.size) { "대상과 일치하지 않는 신고가 포함되어 있습니다." }
    }

    private fun validateOverrideCase(
        caseId: Long,
        subjectType: String,
        subjectKey: String,
        reportIds: List<Long>
    ) {
        require(reportIds.isNotEmpty()) { "제재 변경에는 연결된 신고가 필요합니다." }
        requireOverrideSubject(caseId, subjectType, subjectKey)

        val ids = reportIds.distinct()
        val placeholders = ids.joinToString(",") { "?" }
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM report_log r
            WHERE r.report_id IN ($placeholders)
              AND r.status IN ('RESOLVED', 'OVERRIDDEN')
              AND (SELECT cr.case_id FROM moderation_case_reports cr
                   WHERE cr.report_id = r.report_id
                   ORDER BY cr.case_id DESC LIMIT 1) = ?
            """.trimIndent(),
            Int::class.java,
            *ids.toMutableList<Any>().apply { add(caseId) }.toTypedArray()
        )
        require(count == ids.size) { "현재 제재와 연결되지 않은 신고가 포함되어 있습니다." }
    }

    private fun requireOverrideSubject(caseId: Long, subjectType: String, subjectKey: String) {
        val matches = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
                SELECT 1 FROM moderation_cases
                WHERE case_id = ? AND subject_type = ? AND revoked_at IS NULL
                  AND CASE WHEN subject_type = 'USER' THEN subject_user_id = ?
                           ELSE subject_ip_hash = ? END
            )
            """.trimIndent(),
            Boolean::class.java,
            caseId,
            subjectType,
            subjectKey,
            subjectKey
        ) == true
        require(matches) { "변경하려는 기존 제재가 대상과 일치하지 않거나 이미 해제되었습니다." }
    }

    private fun validateIpReports(reportIds: List<Long>, ip: String) {
        if (reportIds.isEmpty()) return
        val ids = reportIds.distinct()
        val placeholders = ids.joinToString(",") { "?" }
        val arguments = mutableListOf<Any>().apply {
            addAll(ids)
            add(ip)
            add(ip)
        }
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM report_log r
            WHERE r.report_id IN ($placeholders)
              AND (r.target_ip = CAST(? AS inet) OR EXISTS(
                  SELECT 1 FROM connection_log cl
                  WHERE cl.user_id = r.target_id AND cl.user_ip = ?
              ))
            """.trimIndent(),
            Int::class.java,
            *arguments.toTypedArray()
        )
        require(count == ids.size) { "해당 IP와 연결되지 않은 신고가 포함되어 있습니다." }
    }

    private fun mapReportSummary(rs: ResultSet) = ModerationReportSummary(
        rs.getLong("report_id"),
        rs.getString("category_code"),
        rs.getString("reason"),
        rs.getString("detail"),
        rs.getString("status"),
        rs.getTimestamp("time").toInstant(),
        rs.getString("target_id"),
        rs.getString("target_ip")
    )

    private fun resolveIpSubject(subject: String): ResolvedIpSubject {
        val value = subject.trim()
        if (value.startsWith("guest__")) {
            require(value.length in 8..200) { "올바른 손님 식별번호가 필요합니다." }
            val ip = jdbcTemplate.query(
                """
                SELECT ip FROM (
                    SELECT regexp_replace(user_ip, '^::ffff:', '') AS ip,
                           time, id::BIGINT AS ordering
                    FROM connection_log
                    WHERE user_id = ? AND user_ip ~ '^[0-9A-Fa-f:.]+$'
                    UNION ALL
                    SELECT host(target_ip) AS ip, time, report_id AS ordering FROM report_log
                    WHERE target_id = ? AND target_ip IS NOT NULL
                ) sources
                ORDER BY time DESC, ordering DESC LIMIT 1
                """.trimIndent(),
                { rs, _ -> rs.getString("ip") },
                value,
                value
            ).firstOrNull() ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "해당 손님의 접속 IP 기록을 찾을 수 없습니다."
            )
            return ResolvedIpSubject(canonicalIp(ip), value)
        }
        return ResolvedIpSubject(canonicalIp(value), null)
    }

    private fun canonicalIp(value: String): String {
        var trimmed = value.trim().substringBefore('/')
        if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
            trimmed = trimmed.substring(1, trimmed.length - 1)
        }
        if (trimmed.startsWith("::ffff:", ignoreCase = true)
            && IPV4_REGEX.matches(trimmed.substring(7))) {
            trimmed = trimmed.substring(7)
        }
        val address = when {
            IPV4_REGEX.matches(trimmed) -> {
                val parts = trimmed.split('.').map { it.toIntOrNull() }
                if (parts.any { it == null || it !in 0..255 }) null else InetAddress.getByAddress(
                    parts.map { it!!.toByte() }.toByteArray()
                )
            }
            ':' in trimmed && IPV6_CHAR_REGEX.matches(trimmed) -> try {
                InetAddress.getByName(trimmed)
            } catch (_: Exception) {
                null
            }
            else -> null
        }
        if (address !is Inet4Address && address !is Inet6Address) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 IPv4 또는 IPv6 주소가 필요합니다.")
        }
        return address.hostAddress.substringBefore('%')
    }

    private fun networkOf(ip: String): String {
        val address = InetAddress.getByName(ip)
        val bytes = address.address.copyOf()
        val prefix = if (address is Inet4Address) 24 else 64
        val fullBytes = prefix / 8
        for (index in fullBytes until bytes.size) bytes[index] = 0
        return "${InetAddress.getByAddress(bytes).hostAddress}/$prefix"
    }

    private fun ipIdentities(ip: String): List<ModerationIpIdentity> = jdbcTemplate.query(
        """
        SELECT user_id, (ARRAY_AGG(user_name ORDER BY time DESC))[1] AS user_name,
               MAX(time) AS last_seen_at, COUNT(*) AS connection_count
        FROM connection_log WHERE user_ip = ?
        GROUP BY user_id ORDER BY last_seen_at DESC LIMIT 100
        """.trimIndent(),
        { rs, _ ->
            val id = rs.getString("user_id")
            ModerationIpIdentity(
                id,
                rs.getString("user_name"),
                id.startsWith("guest__"),
                rs.instant("last_seen_at")!!,
                rs.getInt("connection_count")
            )
        },
        ip
    )

    private fun currentIpBlock(ip: String): ModerationCurrentIpBlock? = jdbcTemplate.query(
        """
        SELECT b.id, b.time, b.pardon_time, b.reason, b.only_guest_punish,
               (SELECT e.case_id FROM moderation_effects e
                WHERE e.legacy_block_type = 'IP' AND e.legacy_block_id = b.id
                ORDER BY e.effect_id DESC LIMIT 1) AS moderation_case_id
        FROM block_ip b
        WHERE b.ip_address = ? AND (b.pardon_time IS NULL OR b.pardon_time > NOW())
        ORDER BY b.id DESC LIMIT 1
        """.trimIndent(),
        { rs, _ ->
            ModerationCurrentIpBlock(
                rs.getLong("moderation_case_id").let { if (rs.wasNull()) null else it },
                rs.getBoolean("only_guest_punish"),
                rs.instant("time")!!,
                rs.instant("pardon_time"),
                rs.getTimestamp("pardon_time") == null,
                rs.getString("reason")
            )
        },
        ip
    ).firstOrNull()

    private fun blockedIpsInNetwork(ip: String, network: String): List<ModerationNetworkBlockedIp> =
        jdbcTemplate.query(
            """
            SELECT ip_address, time, pardon_time, reason, only_guest_punish
            FROM block_ip
            WHERE ip_address <> ?
              AND CASE WHEN ip_address ~ '^[0-9A-Fa-f:.]+$'
                       THEN ip_address::inet <<= ?::cidr ELSE FALSE END
              AND (pardon_time IS NULL OR pardon_time > NOW())
            ORDER BY time DESC LIMIT 100
            """.trimIndent(),
            { rs, _ ->
                ModerationNetworkBlockedIp(
                    rs.getString("ip_address"),
                    rs.getBoolean("only_guest_punish"),
                    rs.instant("time")!!,
                    rs.instant("pardon_time"),
                    rs.getTimestamp("pardon_time") == null,
                    rs.getString("reason")
                )
            },
            ip,
            network
        )

    private fun reportGameReferences(report: ReportDetailRow): List<ModerationReportGameReference> {
        val reportMillis = report.time.toEpochMilli()
        val from = reportMillis - 60L * 24 * 60 * 60 * 1000
        val to = reportMillis + 60L * 60 * 1000
        val rows = if (report.targetType == "ROOM") {
            val roomId = report.roomId ?: report.targetId.toIntOrNull() ?: return emptyList()
            jdbcTemplate.query(
                """
                SELECT game_id, room_id, room_title, rule, started_at, ended_at
                FROM game_replay
                WHERE (CAST(? AS TEXT) IS NOT NULL AND game_id = ?)
                   OR (room_id = ? AND user_ids @> ARRAY[?]::TEXT[] AND ended_at BETWEEN ? AND ?)
                ORDER BY CASE WHEN game_id = ? THEN 0 ELSE 1 END, ended_at DESC
                LIMIT 5
                """.trimIndent(),
                ::mapReportGameReference,
                report.gameId, report.gameId, roomId, report.reporterId, from, to, report.gameId
            )
        } else {
            jdbcTemplate.query(
                """
                SELECT game_id, room_id, room_title, rule, started_at, ended_at
                FROM game_replay
                WHERE (CAST(? AS TEXT) IS NOT NULL AND game_id = ?)
                   OR (user_ids @> ARRAY[?, ?]::TEXT[] AND ended_at BETWEEN ? AND ?)
                ORDER BY CASE WHEN game_id = ? THEN 0 ELSE 1 END, ended_at DESC
                LIMIT 5
                """.trimIndent(),
                ::mapReportGameReference,
                report.gameId, report.gameId, report.reporterId, report.targetId,
                from, to, report.gameId
            )
        }
        return rows.map {
            it.copy(relation = if (it.gameId == report.gameId) "LINKED" else "SHARED_PARTICIPANTS_CANDIDATE")
        }
    }

    private fun mapReportGameReference(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) =
        Instant.ofEpochMilli(rs.getLong("started_at")).let { startedAt ->
            ModerationReportGameReference(
                gameId = rs.getString("game_id"),
                roomId = rs.getInt("room_id"),
                roomTitle = rs.getString("room_title"),
                rule = rs.getString("rule"),
                startedAt = startedAt,
                endedAt = Instant.ofEpochMilli(rs.getLong("ended_at")),
                logFileName = gameLogFileFormatter.format(startedAt),
                relation = "SHARED_PARTICIPANTS_CANDIDATE"
            )
        }

    private fun reportSuspicionReferences(report: ReportDetailRow): List<ModerationSuspicionReference> {
        if (report.targetType == "ROOM") return emptyList()
        val from = Timestamp.from(report.time.minusSeconds(15 * 60L))
        val to = Timestamp.from(report.time.plusSeconds(15 * 60L))
        return jdbcTemplate.query(
            """
            SELECT case_id, time, action, doubt, extra_info, reference
            FROM suspicion_log
            WHERE user_id = ? AND time BETWEEN ? AND ?
            ORDER BY ABS(EXTRACT(EPOCH FROM (time - ?))) ASC
            LIMIT 10
            """.trimIndent(),
            { rs, _ ->
                ModerationSuspicionReference(
                    caseId = rs.getLong("case_id"),
                    time = rs.instant("time")!!,
                    action = rs.getString("action"),
                    doubt = rs.getString("doubt"),
                    extraInfo = rs.getString("extra_info"),
                    reference = rs.getString("reference")
                )
            },
            report.targetId, from, to, Timestamp.from(report.time)
        )
    }

    private fun existingCase(requestId: UUID): Long? =
        jdbcTemplate.query(
            "SELECT case_id FROM moderation_cases WHERE request_id = ?",
            { rs, _ -> rs.getLong(1) },
            requestId
        ).firstOrNull()

    private fun previewFromCase(caseId: Long): ModerationPolicyPreview {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT policy_id, policy_digest, primary_category_code
            FROM moderation_cases WHERE case_id = ?
            """.trimIndent(),
            caseId
        )
        val policy = policyLoader.current().document
        val violations = jdbcTemplate.query(
            """
            SELECT category_code, offense_no, selected_as_primary, candidate_effects
            FROM moderation_case_violations WHERE case_id = ? ORDER BY category_code
            """.trimIndent(),
            { rs, _ ->
                val code = rs.getString("category_code")
                me.kkutuio.kkutuweb.moderation.policy.PolicyViolationPreview(
                    code,
                    policy.categories.firstOrNull { it.code == code }?.name ?: "사용자 지정",
                    rs.getInt("offense_no"),
                    rs.getBoolean("selected_as_primary"),
                    objectMapper.readValue(
                        rs.getString("candidate_effects"),
                        object : TypeReference<List<ResolvedPolicyEffect>>() {}
                    )
                )
            },
            caseId
        )
        val appliedEffects = jdbcTemplate.query(
            """
            SELECT effect_type, starts_at, ends_at, permanent, parameters
            FROM moderation_effects WHERE case_id = ? ORDER BY effect_id
            """.trimIndent(),
            { rs, _ ->
                ResolvedPolicyEffect(
                    rs.getString("effect_type"),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at")?.toInstant(),
                    rs.getBoolean("permanent"),
                    objectMapper.readValue(
                        rs.getString("parameters"),
                        object : TypeReference<Map<String, Any?>>() {}
                    )
                )
            },
            caseId
        )
        return ModerationPolicyPreview(
            row["policy_id"] as String,
            row["policy_digest"] as String,
            row["primary_category_code"] as String,
            "저장된 제재 결과",
            violations,
            appliedEffects,
            false
        )
    }

    private fun requireUser(userId: String): User =
        userDao.getUser(userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")

    private fun toSummary(user: User): ModerationUserSummary {
        val score = user.kkutu.path("score").asLong(0)
        val restricted = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
                SELECT 1 FROM moderation_effects
                WHERE subject_user_id = ?
                  AND effect_type IN ('GAME_RESTRICTION', 'EXTEND_RELATED_RESTRICTION')
                  AND revoked_at IS NULL AND starts_at <= NOW()
                  AND (permanent OR ends_at > NOW())
            )
            """.trimIndent(),
            Boolean::class.java,
            user.id
        )
        val rank = try { rankDao.getRank(user.id) } catch (_: Exception) { null }
        return ModerationUserSummary(
            user.id,
            user.nickname,
            score,
            user.money,
            rank,
            (sqrt(score.coerceAtLeast(0).toDouble() / 500.0).toInt() + 1).coerceAtLeast(1),
            restricted
        )
    }

    private fun json(value: Any): PGobject = PGobject().apply {
        type = "jsonb"
        this.value = objectMapper.writeValueAsString(value)
    }

    private fun auditRequestIpHash(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(ipSubjectCodec::hash)

    private fun ResultSet.instant(column: String): Instant? = getTimestamp(column)?.toInstant()

    private data class RebuildBlock(
        val effectId: Long,
        val caseId: Long,
        val startsAt: Instant,
        val endsAt: Instant?,
        val permanent: Boolean,
        val reason: String,
        val actorId: String
    )

    private data class RebuildIpBlock(
        val effectId: Long,
        val caseId: Long,
        val effectType: String,
        val startsAt: Instant,
        val endsAt: Instant?,
        val permanent: Boolean,
        val reason: String,
        val actorId: String
    )

    private data class RevocationSubject(
        val type: String,
        val userId: String?,
        val encryptedIp: ByteArray?
    )

    private data class ResolvedIpSubject(val ip: String, val guestId: String?)

    private data class LinkedSanction(val caseId: Long, val subjectType: String, val revoked: Boolean)

    private data class ReportDetailRow(
        val reportId: Long,
        val time: Instant,
        val reporterId: String,
        val reporterNick: String?,
        val targetId: String,
        val targetType: String,
        val categoryCode: String?,
        val reason: String,
        val detail: String?,
        val reportedChat: String?,
        val fileName: String?,
        val roomId: Int?,
        val gameId: String?,
        val gameContextSource: String,
        val status: String,
        val resolvedAt: Instant?,
        val resolvedBy: String?,
        val resolutionNote: String?
    )

    private data class ReportNotificationTarget(
        val reportId: Long,
        val reporterId: String,
        val targetId: String,
        val targetNickname: String?,
        val reportReason: String
    )

    private data class CounterBaseline(
        val counters: Map<String, Int>,
        val lastUpdatedAt: Instant?,
        val source: String,
        val resetAt: Instant?
    )

    companion object {
        private val IPV4_REGEX = Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$")
        private val IPV6_CHAR_REGEX = Regex("^[0-9A-Fa-f:.]+$")
        private val GAME_LOG_FILE_REGEX = Regex("^game-\\d{4}-\\d{2}-\\d{2} \\d{2}\\.log(?:\\.gz)?$")
    }
}
