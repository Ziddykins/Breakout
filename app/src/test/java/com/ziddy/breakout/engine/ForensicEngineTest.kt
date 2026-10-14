package com.ziddy.breakout.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForensicEngineTest {
    @Test
    fun parseAccountEvent_keepsQuotedCsvFieldsTogether() {
        val method = ForensicEngine::class.java.getDeclaredMethod("parseAccountEvent", String::class.java)
        method.isAccessible = true

        val event = method.invoke(
            ForensicEngine,
            "42,action_update,2026-09-25T23:40:00Z,1001,accounts,\"user,foo\"",
        ) as? com.ziddy.breakout.model.ForensicAccountEvent

        assertNotNull(event)
        assertEquals(42, event!!.accountId)
        assertEquals("action_update", event.actionType)
        assertEquals("accounts", event.tableName)
        assertEquals("user,foo", event.key)
    }

    @Test
    fun parseAccountEvent_parsesActualAndroidAccountHistoryRows() {
        val method = ForensicEngine::class.java.getDeclaredMethod("parseAccountEvent", String::class.java)
        method.isAccessible = true

        val event = method.invoke(
            ForensicEngine,
            "25,action_clear_password,2026-09-19 05:59:02,10261,accounts,22",
        ) as? com.ziddy.breakout.model.ForensicAccountEvent

        assertNotNull(event)
        assertEquals(25, event!!.accountId)
        assertEquals("action_clear_password", event.actionType)
        assertEquals("accounts", event.tableName)
        assertEquals("22", event.key)
    }

    @Test
    fun appManifest_registersShizukuShellService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("ShellService must be registered for Shizuku binding", manifest.contains(".shell.ShellService"))
        assertTrue("ShellService must be exported so Shizuku can bind it", manifest.contains("android:name=\".shell.ShellService\""))
    }

    @Test
    fun parseAccounts_acceptsRealAndroidHistoryDump() {
        val method = ForensicEngine::class.java.getDeclaredMethod("parseAccounts", String::class.java)
        method.isAccessible = true

        val output = """
            User UserInfo{0:Owner:4c13}:
              Accounts: 4
                Account {name=jeff0012-svg, type=com.github.android}
                Account {name=Ziddy@memelife.ca, type=com.google}
              AccountId, Action_Type, timestamp, UID, TableName, Key
              Accounts History
              -1,action_called_account_add,2026-09-09 07:44:51,10261,accounts,34
              25,action_clear_password,2026-09-19 05:59:02,10261,accounts,22
        """.trimIndent()

        val parsed = method.invoke(ForensicEngine, output) as? Any
        assertNotNull(parsed)

        val eventsField = parsed!!.javaClass.getDeclaredField("events")
        eventsField.isAccessible = true
        val events = eventsField.get(parsed) as List<*>

        assertEquals(2, events.size)
    }

    @Test
    fun parseAccounts_acceptsFullDeviceAccountHistoryDump() {
        val method = ForensicEngine::class.java.getDeclaredMethod("parseAccounts", String::class.java)
        method.isAccessible = true

        val output = """
            User UserInfo{0:Owner:4c13}:
              Accounts: 4
                Account {name=jeff0012-svg, type=com.github.android}
                Account {name=Ziddy@memelife.ca, type=com.google}
              AccountId, Action_Type, timestamp, UID, TableName, Key
              Accounts History
              -1,action_called_account_add,2026-09-09 07:44:51,10261,accounts,34
              19,action_account_add,2026-09-09 15:55:57,10417,accounts,41
              25,action_clear_password,2026-09-19 05:59:02,10261,accounts,22
              29,action_account_add,2026-09-23 16:25:22,10420,accounts,33
        """.trimIndent()

        val parsed = method.invoke(ForensicEngine, output) as? Any
        assertNotNull(parsed)

        val eventsField = parsed!!.javaClass.getDeclaredField("events")
        eventsField.isAccessible = true
        val events = eventsField.get(parsed) as List<*>

        assertTrue("account history rows should be accepted from the real device dump", events.size >= 4)
    }
}
