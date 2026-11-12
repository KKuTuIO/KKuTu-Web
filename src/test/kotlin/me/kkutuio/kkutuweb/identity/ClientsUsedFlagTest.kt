package me.kkutuio.kkutuweb.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class ClientsUsedFlagTest {
    private val mapper = ObjectMapper()
    private fun flags(json: String) = mapper.readTree(json)

    @Test
    fun `reads the clients the account has opened`() {
        assertEquals(listOf("legacy", "new"), AccountService.parseClientsUsed(flags("""{"clientsUsed":{"value":["legacy","new"],"time":1}}""")))
    }

    @Test
    fun `an account with no record, or a damaged one, has opened none`() {
        assertEquals(emptyList<String>(), AccountService.parseClientsUsed(flags("{}")))
        assertEquals(emptyList<String>(), AccountService.parseClientsUsed(flags("""{"clientsUsed":{"value":"legacy"}}""")))
        assertEquals(emptyList<String>(), AccountService.parseClientsUsed(flags("""{"clientsUsed":null}""")))
    }

    @Test
    fun `unknown and repeated entries are dropped`() {
        assertEquals(listOf("legacy"), AccountService.parseClientsUsed(flags("""{"clientsUsed":{"value":["legacy","legacy","mobile",7]}}""")))
    }
}
