package me.kkutuio.kkutuweb.ranking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations

class RankDaoTest {
    @Test
    fun `deleted users are removed from current and snapshot rankings`() {
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        @Suppress("UNCHECKED_CAST")
        val zSetOperations = mock(ZSetOperations::class.java) as ZSetOperations<String, Any>

        `when`(redisTemplate.opsForZSet()).thenReturn(zSetOperations)
        `when`(zSetOperations.remove("KKuTu_Score", "deleted")).thenReturn(1)

        val removed = RankDao(redisTemplate).removeDeleted(listOf("deleted"))

        assertEquals(1, removed)
        verify(zSetOperations).remove("KKuTu_Score", "deleted")
        verify(zSetOperations).remove("KKuTu_Score_Snapshot", "deleted")
    }
}
