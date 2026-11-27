package dev.emassey0135.orbit

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTest {
    @Test
    fun theActivityIsStartedByItsComponentWithEachExtraAsAString() {
        assertEquals(
            "am start -n 'com.example/com.example.Main' --es 'reminder' 'task-1' --es 'at' '2026-11-26T09:00:00Z'",
            Foreground.command("com.example", "com.example.Main", linkedMapOf("reminder" to "task-1", "at" to "2026-11-26T09:00:00Z")),
        )
    }

    @Test
    fun aValueHoldingWhatTheShellReadsStaysOneWord() {
        assertEquals("'it'\\''s; rm -rf \$HOME'", Foreground.quoted("it's; rm -rf \$HOME"))
    }
}
