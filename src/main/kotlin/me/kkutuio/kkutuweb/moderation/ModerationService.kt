package me.kkutuio.kkutuweb.moderation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import me.kkutuio.kkutuweb.game.GameClientManager
import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyEngine
import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyLoader
import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyPreview
import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyRegistry
import me.kkutuio.kkutuweb.moderation.policy.ResolvedPolicyEffect
import me.kkutuio.kkutuweb.ranking.RankDao
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
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.sqrt

@Service
class ModerationService(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val userDao: UserDao,
    private val rankDao: RankDao,
    private val policyLoader: ModerationPolicyLoader,
    private val policyEngine: ModerationPolicyEngine,
    private val policyRegistry: ModerationPolicyRegistry,
    private val gameClientManager: GameClientManager
) {
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
        return ModerationUserDetail(
            user = toSummary(user),
            counters = currentCounters(userId),
            history = history(userId, 50),
            reports = reports(userId, 20)
        )
    }

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

        val linkedCaseId = jdbcTemplate.query(
            "SELECT case_id FROM moderation_case_reports WHERE report_id = ? ORDER BY case_id DESC LIMIT 1",
            { rs, _ -> rs.getLong("case_id") },
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
            linkedSanctionCaseId = linkedCaseId,
            gameReferences = reportGameReferences(report),
            suspicionReferences = reportSuspicionReferences(report)
        )
    }

    @Transactional
    fun linkReportGameContext(reportId: Long, request: ReportGameContextLinkRequest, actorId: String) {
        require(request.gameId.isNotBlank() && request.gameId.length <= 64) { "올바른 경기 ID가 필요합니다." }
        require(request.reason.isNotBlank()) { "경기 연결 사유는 필수입니다." }
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
            currentCounters(request.userId),
            request.occurredAt ?: Instant.now()
        )
    }

    @Transactional
    fun issue(request: SanctionIssueRequest, actorId: String, requestIpHash: String?): SanctionIssueResponse {
        require(request.evidenceText.isNotBlank()) { "근거 자료는 필수입니다." }
        requireUser(request.userId)
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
            currentCounters(request.userId),
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
            jdbcTemplate.update(
                """
                UPDATE report_log SET status = 'ACTIONED', resolved_at = NOW(),
                    resolved_by = ?, resolution_note = ?
                WHERE report_id = ?
                """.trimIndent(),
                actorId,
                "제재 #$caseId 연결",
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
            requestIpHash
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
    fun revoke(caseId: Long, reason: String, actorId: String, requestIpHash: String?) {
        require(reason.isNotBlank()) { "철회 사유는 필수입니다." }
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
            SELECT effect_id, legacy_block_type, legacy_block_id
            FROM moderation_effects WHERE case_id = ? AND revoked_at IS NULL
            """.trimIndent(),
            { rs ->
                val table = when (rs.getString("legacy_block_type")) {
                    "USER" -> "block_user"
                    "CHAT" -> "block_chat"
                    else -> null
                }
                val legacyId = rs.getLong("legacy_block_id").takeUnless { rs.wasNull() }
                if (table != null && legacyId != null) {
                    jdbcTemplate.update("DELETE FROM $table WHERE id = ?", legacyId)
                }
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
        val subjectId = jdbcTemplate.queryForObject(
            "SELECT subject_user_id FROM moderation_cases WHERE case_id = ?",
            String::class.java,
            caseId
        )
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
        jdbcTemplate.update(
            """
            INSERT INTO moderation_case_events
                (case_id, event_type, actor_type, actor_id, payload, request_ip_hash)
            VALUES (?, 'REVOKED', 'ADMIN', ?, ?, ?)
            """.trimIndent(),
            caseId,
            actorId,
            json(mapOf("reason" to reason)),
            requestIpHash
        )
    }

    @Transactional
    fun adjustCounter(userId: String, request: CounterAdjustmentRequest, actorId: String) {
        require(request.reason.isNotBlank()) { "조정 사유는 필수입니다." }
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
    fun resolveReport(reportId: Long, request: ReportResolutionRequest, actorId: String) {
        require(request.status == "REJECTED") {
            "신고 처리는 제재 사건 발급으로만 완료할 수 있으며, 이 API는 반려만 허용합니다."
        }
        require(request.note.isNotBlank()) { "신고 처리 메모는 필수입니다." }
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
                     evidence_text, summary)
                VALUES (?, 'USER', ?, ?, ?, ?, 'ADMIN', ?, ?, ?, ?)
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
                jdbcTemplate.update(
                    """
                    UPDATE users SET
                        money = FLOOR(money * (100 - ?) / 100.0),
                        kkutu = jsonb_set(
                            kkutu::jsonb, '{score}',
                            to_jsonb(FLOOR(COALESCE((kkutu->>'score')::numeric, 0) * (100 - ?) / 100.0)::bigint)
                        )::json
                    WHERE _id = ?
                    """.trimIndent(),
                    percent,
                    percent,
                    userId
                )
                markApplied(effectId)
            }
            "NICKNAME_RESET" -> {
                val policyNickname = "이름없음#${userId.substringAfter('-', userId).take(5)}"
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

    private fun markApplied(effectId: Long) {
        jdbcTemplate.update(
            "UPDATE moderation_effects SET apply_status = 'APPLIED', applied_at = NOW() WHERE effect_id = ?",
            effectId
        )
    }

    private fun currentCounters(userId: String): Map<String, Int> {
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
              AND v.category_code <> '99'$sinceClause
            GROUP BY v.category_code
            """.trimIndent(),
            { rs, _ -> rs.getString("category_code") to rs.getInt("count") },
            *arguments.toTypedArray()
        ).toMap()
        val result = if (isAdjustment) baseline!!.counters.toMutableMap() else mutableMapOf()
        issuedCounters.forEach { (code, count) -> result[code] = (result[code] ?: 0) + count }
        return result.filterValues { it > 0 }
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

    private fun reports(userId: String, limit: Int): List<ModerationReportSummary> =
        jdbcTemplate.query(
            """
            SELECT report_id, category_code, reason, detail, status, time
            FROM report_log WHERE target_id = ?
            ORDER BY time DESC, report_id DESC LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                ModerationReportSummary(
                    rs.getLong("report_id"),
                    rs.getString("category_code"),
                    rs.getString("reason"),
                    rs.getString("detail"),
                    rs.getString("status"),
                    rs.getTimestamp("time").toInstant()
                )
            },
            userId,
            limit
        )

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

    private data class CounterBaseline(
        val counters: Map<String, Int>,
        val lastUpdatedAt: Instant?,
        val source: String,
        val resetAt: Instant?
    )
}
