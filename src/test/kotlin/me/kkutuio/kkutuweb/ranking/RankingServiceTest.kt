package me.kkutuio.kkutuweb.ranking

import me.kkutuio.kkutuweb.user.UserDao
import me.kkutuio.kkutuweb.user.User
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class RankingServiceTest {
    private val rankDao = mock(RankDao::class.java)
    private val userDao = mock(UserDao::class.java)
    private val rankingService = RankingService(rankDao, userDao)

    @Test
    fun `deleted users are removed and the compacted page is returned`() {
        val firstPage = listOf(
            Rank("active", 1050, 5000),
            Rank("deleted", 1051, 4990)
        )
        val compactedPage = listOf(
            Rank("active", 1050, 5000),
            Rank("replacement", 1051, 4980)
        )

        `when`(rankDao.getPage(70, 15)).thenReturn(firstPage, compactedPage)
        `when`(userDao.getNicknames(listOf("active", "deleted")))
            .thenReturn(mapOf("active" to "활성 사용자"))
        `when`(userDao.getNicknames(listOf("active", "replacement")))
            .thenReturn(mapOf("active" to "활성 사용자", "replacement" to "대체 사용자"))
        `when`(rankDao.removeDeleted(listOf("deleted"))).thenReturn(1)
        `when`(rankDao.getSnapshotRanks(listOf("active", "replacement")))
            .thenReturn(listOf(1050, 1051))

        val response = rankingService.getRanking(70, null)

        assertEquals(70, response.page)
        assertEquals(listOf("active", "replacement"), response.data.map { it.id })
        assertEquals(listOf("활성 사용자", "대체 사용자"), response.data.map { it.name })
        verify(rankDao).removeDeleted(listOf("deleted"))
        verify(rankDao, times(2)).getPage(70, 15)
    }

    @Test
    fun `an existing user with a null nickname is not treated as deleted`() {
        val ranks = listOf(Rank("null-nickname", 0, 10))

        `when`(rankDao.getPage(0, 15)).thenReturn(ranks)
        `when`(userDao.getNicknames(listOf("null-nickname")))
            .thenReturn(mapOf("null-nickname" to null))
        `when`(rankDao.getSnapshotRanks(listOf("null-nickname"))).thenReturn(listOf(null))

        val response = rankingService.getRanking(0, null)

        assertEquals(1, response.data.size)
        assertNull(response.data.single().name)
        verify(rankDao, never()).removeDeleted(anyList())
    }

    @Test
    fun `search returns ranked nickname matches with their scores`() {
        val user = User("player-id", "플레이어", 0, JsonNodeFactory.instance.nullNode(), null,
            JsonNodeFactory.instance.nullNode(), JsonNodeFactory.instance.nullNode(), null, null, null,
            null, JsonNodeFactory.instance.nullNode(), JsonNodeFactory.instance.nullNode(), null)
        `when`(userDao.searchUsers("플레이어")).thenReturn(listOf(user))
        `when`(rankDao.getRank("player-id")).thenReturn(25)
        `when`(rankDao.getScores(listOf("player-id"))).thenReturn(mapOf("player-id" to 12345))
        `when`(rankDao.getSnapshotRanks(listOf("player-id"))).thenReturn(listOf(28))

        val response = rankingService.searchRanking(" 플레이어 ")

        assertEquals(1, response.data.size)
        assertEquals("플레이어", response.data.single().name)
        assertEquals(24, response.data.single().rank)
        assertEquals(12345, response.data.single().score)
        assertEquals("+4", response.data.single().delta)
    }
}
