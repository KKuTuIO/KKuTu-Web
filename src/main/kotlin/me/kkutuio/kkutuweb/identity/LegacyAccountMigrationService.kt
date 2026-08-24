package me.kkutuio.kkutuweb.identity

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

/**
 * Legacy game-user -> Account conversion is intentionally not part of V1.
 * Friend lists, ranking references, and other legacy identifiers still need a
 * coordinated migration plan.  Keep the switch fail-closed so turning it on
 * cannot silently create partial account mappings.
 */
@Service
@ConditionalOnProperty(prefix = "idp.legacy-migration", name = ["enabled"], havingValue = "true")
class LegacyAccountMigrationService {
    @PostConstruct
    fun refuseUnsafeMigration() {
        throw IllegalStateException("잘못된 접근입니다.")
    }
}
