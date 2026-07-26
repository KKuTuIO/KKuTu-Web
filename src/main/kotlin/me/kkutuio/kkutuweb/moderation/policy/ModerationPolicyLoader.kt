package me.kkutuio.kkutuweb.moderation.policy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PostConstruct

@Component
class ModerationPolicyLoader(
    private val applicationArguments: ApplicationArguments,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(ModerationPolicyLoader::class.java)
    private val loaded = AtomicReference<LoadedModerationPolicy>()

    @PostConstruct
    fun initialize() {
        loaded.set(load(resolvePolicySource()))
    }

    fun current(): LoadedModerationPolicy = loaded.get()

    @Synchronized
    fun reload(): LoadedModerationPolicy {
        val next = load(resolvePolicySource())
        val previous = loaded.get()
        if (previous != null && previous.document.policyId == next.document.policyId && previous.digest != next.digest) {
            throw IllegalStateException("Policy ${next.document.policyId} was changed in place")
        }
        loaded.set(next)
        logger.info("Moderation policy reloaded: {} ({})", next.document.policyId, next.digest)
        return next
    }

    private fun resolvePolicySource(): PolicySourceContent {
        val explicitPath = System.getenv("MODERATION_POLICY_PATH")?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitPath != null) {
            return readFile(Paths.get(explicitPath))
        }

        val settingDir = applicationArguments.getOptionValues("SETTING_DIR")?.firstOrNull()
        if (settingDir != null) {
            val settingPolicy = Paths.get(settingDir, "moderation-policy.json")
            if (Files.isRegularFile(settingPolicy)) {
                return readFile(settingPolicy)
            }
        }

        val resource = ClassPathResource("moderation-policy.default.json")
        val json = resource.inputStream.use { String(it.readBytes(), StandardCharsets.UTF_8) }
        return PolicySourceContent(json, "classpath:moderation-policy.default.json")
    }

    private fun readFile(path: Path): PolicySourceContent {
        if (!Files.isRegularFile(path)) {
            throw IllegalStateException("Moderation policy file does not exist: ${path.toAbsolutePath()}")
        }
        return PolicySourceContent(
            String(Files.readAllBytes(path), StandardCharsets.UTF_8),
            path.toAbsolutePath().normalize().toString()
        )
    }

    private fun load(source: PolicySourceContent): LoadedModerationPolicy {
        val policyNode = objectMapper.readTree(source.json)
        validateSchema(policyNode)
        val document = objectMapper.treeToValue(policyNode, ModerationPolicy::class.java)
        validateMeaning(document)
        val digest = sha256(source.json)
        logger.info("Moderation policy loaded: {} from {}", document.policyId, source.description)
        return LoadedModerationPolicy(document, digest, source.json, source.description)
    }

    private fun validateSchema(policyNode: JsonNode) {
        val schemaNode = ClassPathResource("moderation-policy.schema.json").inputStream.use {
            objectMapper.readTree(it)
        }
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(schemaNode)
        val errors = schema.validate(policyNode)
        if (errors.isNotEmpty()) {
            throw IllegalStateException(
                "Invalid moderation policy JSON:\n" + errors.joinToString("\n") { it.message }
            )
        }
    }

    private fun validateMeaning(policy: ModerationPolicy) {
        val expectedCodes = (0..18).map { it.toString().padStart(2, '0') }.toSet()
        val actualCodes = policy.categories.map { it.code }
        require(actualCodes.toSet() == expectedCodes && actualCodes.size == expectedCodes.size) {
            "Moderation categories must contain each code from 00 to 18 exactly once"
        }

        policy.categories.forEach { category ->
            category.steps.forEachIndexed { index, step ->
                require(step.offense == index + 1) {
                    "Category ${category.code} offense steps must be contiguous from 1"
                }
                step.effects.forEach { validateEffect(category.code, it) }
            }
            category.allowedOverrides.flatMap { it.effects }.forEach { validateEffect(category.code, it) }
        }

        val bypass = policy.category("17")
        require(bypass.steps.all { step ->
            step.effects.any { it.type == "EXTEND_RELATED_RESTRICTION" }
        }) {
            "Category 17 must extend a related restriction at every offense step"
        }

        val publishedAt = LocalDate.parse(policy.source.publishedAt)
        val effectiveFrom = OffsetDateTime.parse(policy.source.effectiveFrom)
        val noticeDays = Duration.between(
            publishedAt.atStartOfDay(effectiveFrom.offset).toInstant(),
            effectiveFrom.toInstant()
        ).toDays()
        require(noticeDays >= policy.noticePeriodDays) {
            "Policy effective date must honor noticePeriodDays"
        }
    }

    private fun validateEffect(categoryCode: String, effect: PolicyEffect) {
        val knownTypes = setOf(
            "WARNING",
            "CHAT_RESTRICTION",
            "GAME_RESTRICTION",
            "GUEST_ACCESS_RESTRICTION",
            "IP_RESTRICTION",
            "NICKNAME_RESET",
            "NICKNAME_CHANGE_RESTRICTION",
            "RESOURCE_ADJUSTMENT",
            "EXTEND_RELATED_RESTRICTION",
            "RELATED_SERVICE_RESTRICTION"
        )
        require(effect.type in knownTypes) { "Category $categoryCode has unknown effect ${effect.type}" }
        require(!(effect.permanent && effect.duration != null)) {
            "Category $categoryCode effect ${effect.type} cannot have duration and permanent together"
        }
        effect.duration?.let {
            PolicyTime.comparableSeconds(it, java.time.ZoneId.of("Asia/Seoul"))
        }
        if (effect.type == "RESOURCE_ADJUSTMENT") {
            val percent = (effect.parameters["percent"] as? Number)?.toInt()
                ?: throw IllegalArgumentException("Category $categoryCode resource adjustment needs percent")
            require(percent in 0..100) { "Category $categoryCode resource percent must be between 0 and 100" }
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private data class PolicySourceContent(
        val json: String,
        val description: String
    )
}
