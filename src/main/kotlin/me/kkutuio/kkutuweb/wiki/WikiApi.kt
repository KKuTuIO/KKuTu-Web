package me.kkutuio.kkutuweb.wiki

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.URLEncoder
import java.time.Instant

data class WikiUser(val id: Long, val name: String)

/** One edit of a wiki user: `tags` are the revision's change tags (`mw-reverted` once someone undid it). */
data class WikiEdit(val revisionId: Long, val title: String, val editedAt: Instant, val minor: Boolean, val tags: List<String>, val comment: String)

/** The wiki could not be reached or answered something unusable (the callers turn this into a retry later / a 503). */
class WikiUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** What the server reads from 리오위키 (MediaWiki's public Action API; nothing is ever sent to it except the questions). */
interface WikiApi {
    /** The wiki user called `name` (MediaWiki-normalised), or null when there is none. */
    fun findUser(name: String): WikiUser?
    /** The wikitext of `User:<name>`, or null when the page does not exist. */
    fun userPageText(name: String): String?
    /** The user's latest `limit` edits (any namespace), newest first. */
    fun recentEdits(name: String, limit: Int): List<WikiEdit>
    /** The user's mainspace edits made at or after `from`, oldest first. Pages through the whole answer. */
    fun editsSince(userId: Long, from: Instant): List<WikiEdit>
    /** The change tags of these revisions now; a revision that no longer exists (deleted) is missing from the result. */
    fun revisionTags(revisionIds: Collection<Long>): Map<Long, List<String>>
}

@Component
class HttpWikiApi(
    @Value("\${wiki.origin:https://kkutu.wiki}") origin: String
) : WikiApi {
    private val origin = origin.trim().trimEnd('/')
    private val rest = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(8_000)
    })

    private fun query(params: Map<String, String>): JsonNode {
        val pairs = (mapOf("format" to "json", "formatversion" to "2") + params).entries
            .joinToString("&") { (k, v) -> URLEncoder.encode(k, Charsets.UTF_8) + "=" + URLEncoder.encode(v, Charsets.UTF_8) }
        val body = try {
            val request = org.springframework.http.RequestEntity.get(URI.create("$origin/api.php?$pairs"))
                .header("User-Agent", "KKuTuIO-Web wiki-binding").build()
            rest.exchange(request, JsonNode::class.java).body
        } catch (error: Exception) {
            throw WikiUnavailableException("리오위키에 연결하지 못했습니다.", error)
        } ?: throw WikiUnavailableException("리오위키가 빈 응답을 보냈습니다.")
        if (body.has("error")) throw WikiUnavailableException("리오위키 응답 오류: ${body.path("error").path("code").asString("")}")
        return body
    }

    override fun findUser(name: String): WikiUser? {
        val user = query(mapOf("action" to "query", "list" to "users", "ususers" to name)).path("query").path("users").path(0)
        if (user.isMissingNode || user.has("missing") || user.has("invalid") || !user.has("userid")) return null
        return WikiUser(user.path("userid").asLong(), user.path("name").asString(name))
    }

    override fun userPageText(name: String): String? {
        val page = query(
            mapOf("action" to "query", "prop" to "revisions", "titles" to "User:$name", "rvprop" to "content", "rvslots" to "main")
        ).path("query").path("pages").path(0)
        if (page.isMissingNode || page.has("missing")) return null
        return page.path("revisions").path(0).path("slots").path("main").path("content").asString(null)
    }

    private fun JsonNode.items(): List<JsonNode> = iterator().asSequence().toList()

    private fun edit(node: JsonNode) = WikiEdit(
        node.path("revid").asLong(), node.path("title").asString(""), Instant.parse(node.path("timestamp").asString()),
        node.path("minor").asBoolean(false), node.path("tags").items().map { it.asString("") }, node.path("comment").asString("")
    )

    override fun recentEdits(name: String, limit: Int): List<WikiEdit> =
        query(
            mapOf("action" to "query", "list" to "usercontribs", "ucuser" to name, "uclimit" to limit.toString(), "ucprop" to "ids|title|timestamp|comment|tags|flags")
        ).path("query").path("usercontribs").items().map(::edit)

    override fun editsSince(userId: Long, from: Instant): List<WikiEdit> {
        val out = mutableListOf<WikiEdit>()
        var cont: String? = null
        var pages = 0
        do {
            val params = mutableMapOf(
                "action" to "query", "list" to "usercontribs", "ucuserids" to userId.toString(), "ucnamespace" to "0", "ucdir" to "newer",
                "ucstart" to from.toString(), "uclimit" to "500", "ucprop" to "ids|title|timestamp|tags|flags"
            )
            cont?.let { params["uccontinue"] = it }
            val answer = query(params)
            answer.path("query").path("usercontribs").items().forEach { out += edit(it) }
            cont = answer.path("continue").path("uccontinue").asString(null)
        } while (cont != null && ++pages < 20)
        return out
    }

    override fun revisionTags(revisionIds: Collection<Long>): Map<Long, List<String>> {
        val out = HashMap<Long, List<String>>()
        revisionIds.chunked(50).forEach { chunk ->
            val answer = query(mapOf("action" to "query", "prop" to "revisions", "revids" to chunk.joinToString("|"), "rvprop" to "ids|tags"))
            answer.path("query").path("pages").items().forEach { page ->
                page.path("revisions").items().forEach { rev -> out[rev.path("revid").asLong()] = rev.path("tags").items().map { it.asString("") } }
            }
        }
        return out
    }
}
