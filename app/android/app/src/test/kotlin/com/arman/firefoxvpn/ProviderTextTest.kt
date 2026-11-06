package com.arman.firefoxvpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTextTest {
    @Test
    fun neutralizesNamesAndCompleteHostnames() {
        val text = ProviderText.neutralize(
            "Mozilla Firefox Accounts api.accounts.firefox.com vpn.mozilla.org " +
                "firefox.settings.services.mozilla.com FxA GeckoView",
        )
        for (name in listOf("mozilla", "firefox", "fxa", "gecko")) {
            assertFalse(text.contains(name, ignoreCase = true))
        }
        assertTrue(text.contains("[redacted]"))
    }

    @Test
    fun neutralizesNestedErrorsAndInternalStackFrames() {
        val error = IllegalStateException(
            "cannot reach vpn.mozilla.org",
            IllegalArgumentException("Firefox Accounts failed"),
        )
        error.stackTrace = arrayOf(
            StackTraceElement("com.arman.firefoxvpn.CoreService", "startCore", "CoreService.kt", 1),
        )
        val text = ProviderText.diagnostic("the core did not start", error)
        assertFalse(text.contains("mozilla", ignoreCase = true))
        assertFalse(text.contains("firefox", ignoreCase = true))
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("Caused by:"))
    }

    @Test
    fun preservesUsefulUnrelatedErrorDetails() {
        val message = "connection refused at 127.0.0.1:10810 (code 7)"
        assertEquals(message, ProviderText.error(IllegalStateException(message)))
    }
}
