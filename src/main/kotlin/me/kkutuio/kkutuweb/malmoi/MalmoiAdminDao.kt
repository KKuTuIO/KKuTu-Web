package me.kkutuio.kkutuweb.malmoi

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** 백오피스용 조회와 조치. 사용자용 저장소([MalmoiDao])와 같은 테이블을 쓴다. */
@Component
class MalmoiAdminDao(@Autowired private val jdbc: JdbcTemplate) {
    fun books(state: String?, query: String, limit: Int): List<Map<String, Any?>> {
        val like = "%${query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        return jdbc.queryForList(
            """SELECT m.id, m.kind, m.base_theme, m.title, m.state, m.locked, m.owner_name, m.creator_name, m.updated_at,
                      (SELECT count(*) FROM malmoi_word w WHERE w.malmoi_id = m.id AND w.removed_at IS NULL) AS words,
                      (SELECT count(*) FROM malmoi_registration r WHERE r.malmoi_id = m.id) AS registrants,
                      (SELECT count(*) FROM malmoi_report p WHERE p.malmoi_id = m.id AND p.status = 'OPEN') AS reports
                 FROM malmoi m WHERE (?::text IS NULL OR m.state = ?) AND (m.title ILIKE ? ESCAPE '\\' OR ? = '%%') ORDER BY m.updated_at DESC LIMIT ?""",
            state, state, like, like, limit
        )
    }

    fun setLocked(id: Long, locked: Boolean) {
        jdbc.update("UPDATE malmoi SET locked = ?, updated_at = now() WHERE id = ?", locked, id)
    }

    fun setState(id: Long, state: String) {
        jdbc.update("UPDATE malmoi SET state = ?, updated_at = now() WHERE id = ?", state, id)
    }

    fun setOwner(id: Long, owner: UUID?, name: String?) {
        jdbc.update(
            "UPDATE malmoi SET owner_uuid = ?, owner_name = ?, updated_at = now() WHERE id = ?",
            owner,
            name,
            id
        )
    }

    // ---- reports ----
    fun reports(status: String, limit: Int): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT r.id, r.malmoi_id, m.title, r.word, r.reason, r.detail, r.status, r.created_at FROM malmoi_report r JOIN malmoi m ON m.id = r.malmoi_id WHERE r.status = ? ORDER BY r.created_at LIMIT ?",
        status,
        limit
    )

    fun addReport(id: Long, word: String?, reporter: UUID, reason: String, detail: String) {
        jdbc.update(
            "INSERT INTO malmoi_report(malmoi_id, word, reporter_uuid, reason, detail) VALUES (?, ?, ?, ?, ?)",
            id,
            word,
            reporter,
            reason,
            detail
        )
    }

    fun resolveReport(id: Long, status: String, admin: String): Int = jdbc.update(
        "UPDATE malmoi_report SET status = ?, handled_by = CAST(? AS uuid), handled_at = now() WHERE id = ? AND status = 'OPEN'",
        status,
        admin,
        id
    )

    // ---- publish reviews ----
    fun reviews(status: String): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT v.id, v.malmoi_id, m.title, m.owner_name, v.revision, v.status, v.reason, v.created_at FROM malmoi_publish_review v JOIN malmoi m ON m.id = v.malmoi_id WHERE v.status = ? ORDER BY v.created_at",
        status
    )

    fun requestReview(id: Long, revision: Long, by: UUID) {
        jdbc.update(
            "INSERT INTO malmoi_publish_review(malmoi_id, revision, requested_by) VALUES (?, ?, ?)",
            id,
            revision,
            by
        )
    }

    fun pendingReview(id: Long): Boolean = (jdbc.queryForObject(
        "SELECT count(*) FROM malmoi_publish_review WHERE malmoi_id = ? AND status = 'PENDING'",
        Int::class.java,
        id
    ) ?: 0) > 0

    fun decideReview(reviewId: Long, status: String, reason: String, admin: String): Long? = jdbc.query(
        "UPDATE malmoi_publish_review SET status = ?, reason = ?, reviewer_uuid = CAST(? AS uuid), decided_at = now() WHERE id = ? AND status = 'PENDING' RETURNING malmoi_id",
        { rs, _ -> rs.getLong(1) }, status, reason, admin, reviewId
    ).firstOrNull()

    /** 승계를 검토할 낱말: 반영된 것(승계 전)만, 오래된 순. */
    fun promotableWords(id: Long, limit: Int, offset: Int): List<Map<String, Any?>> = jdbc.queryForList(
        """SELECT w.id, w.word, w.meanings->>0 AS mean, w.source, w.author_name, COALESCE(u.uses, 0) AS uses FROM malmoi_word w
             LEFT JOIN malmoi_word_use u ON u.malmoi_id = w.malmoi_id AND u.word = w.word
            WHERE w.malmoi_id = ? AND w.removed_at IS NULL AND w.promotion IS NULL AND w.applies_at <= now() ORDER BY COALESCE(u.uses, 0) DESC, w.created_at, w.id LIMIT ? OFFSET ?""",
        id,
        limit,
        offset
    )

    // ---- 승계 신청(새 말모이) ----
    fun promotionRequests(status: String): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT r.id, r.malmoi_id, m.title, m.owner_name, r.words, r.status, r.reason, r.created_at FROM malmoi_promotion_request r JOIN malmoi m ON m.id = r.malmoi_id WHERE r.status = ? ORDER BY r.created_at",
        status
    )

    fun decidePromotionRequest(id: Long, status: String, reason: String, admin: String): Int = jdbc.update(
        "UPDATE malmoi_promotion_request SET status = ?, reason = ?, reviewer_uuid = CAST(? AS uuid), decided_at = now() WHERE id = ? AND status = 'PENDING'",
        status,
        reason,
        admin,
        id
    )

    // ---- 확장팩 ----
    fun createExt(theme: String, lang: String, title: String): Long? = jdbc.query(
        """INSERT INTO malmoi(kind, base_theme, lang, title, description, state)
           SELECT 'EXT', ?, ?, ?, '공식 사전에 아직 없는 단어를 등록해보세요!', 'PUBLIC'
            WHERE NOT EXISTS (SELECT 1 FROM malmoi WHERE kind = 'EXT' AND base_theme = ? AND lang = ?) RETURNING id""",
        { rs, _ -> rs.getLong(1) }, theme, lang, title, theme, lang
    ).firstOrNull()

    // ---- 승계 ----
    /** 확장팩 낱말들(삭제되지 않았고 승계 전인 것). */
    fun promotable(id: Long, wordIds: List<Long>): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT id, word, meanings->>0 AS mean FROM malmoi_word WHERE malmoi_id = ? AND id = ANY(CAST(? AS bigint[])) AND removed_at IS NULL AND promotion IS NULL AND applies_at <= now()",
        id, wordIds.joinToString(",", "{", "}")
    )

    fun officialExists(lang: String, word: String): Boolean =
        (jdbc.queryForObject(
            "SELECT count(*) FROM ${if (lang == "en") "kkutu_en" else "kkutu_ko"} WHERE _id = ?",
            Int::class.java,
            word
        ) ?: 0) > 0

    /** 공식 사전에 넣는다: 없는 낱말은 새로, 있는 낱말은 주제 코드만 보탠다. */
    fun addToOfficial(lang: String, word: String, mean: String, theme: String) {
        val table = if (lang == "en") "kkutu_en" else "kkutu_ko"
        if (officialExists(lang, word)) {
            jdbc.update(
                "UPDATE $table SET theme = CASE WHEN (',' || theme || ',') LIKE ? THEN theme WHEN theme = '' THEN ? ELSE theme || ',' || ? END WHERE _id = ?",
                "%,$theme,%",
                theme,
                theme,
                word
            )
        } else jdbc.update(
            "INSERT INTO $table(_id, type, mean, hit, flag, theme) VALUES (?, 'INJEONG', ?, 0, 2, ?)",
            word,
            mean,
            theme
        )
    }

    fun markPromoted(wordId: Long, result: String) {
        jdbc.update(
            "UPDATE malmoi_word SET promotion = ?, removed_at = now(), remove_at = NULL WHERE id = ?",
            result,
            wordId
        )
    }

    fun logPromotion(id: Long, word: String, theme: String?, result: String, reason: String, admin: String) {
        jdbc.update(
            "INSERT INTO malmoi_promotion(malmoi_id, word, base_theme, result, reason, reviewer_uuid) VALUES (?, ?, ?, ?, ?, CAST(? AS uuid))",
            id,
            word,
            theme,
            result,
            reason,
            admin
        )
    }
}
