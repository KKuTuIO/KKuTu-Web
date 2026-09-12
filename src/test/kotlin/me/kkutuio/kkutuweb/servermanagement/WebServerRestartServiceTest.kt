package me.kkutuio.kkutuweb.servermanagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebServerRestartServiceTest {
    @Test
    fun `schedules termination after returning an acknowledgement and rejects duplicates`() {
        val terminated = CountDownLatch(1)
        val service = WebServerRestartService(ApplicationTerminator { terminated.countDown() }, 500)

        val response = service.schedule("actor")
        val duplicate = assertThrows(ResponseStatusException::class.java) { service.schedule("actor") }

        assertTrue(response.accepted)
        assertEquals(500, response.shutdownDelayMillis)
        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
        assertTrue(terminated.await(2, TimeUnit.SECONDS))
    }
}
