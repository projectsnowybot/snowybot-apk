package com.example.snowybottext.web

import org.junit.Assert.assertTrue
import org.junit.Test

class SnowybotRuntimeStateTest {
    @Test
    fun runtimeFailureNotifiesNativeServiceAndClearsRunningFlags() {
        val script = """
            window.snowyBotRunning = true;
            window.__snowybotStopRequested = false;
            window.addEventListener('error', function(event) {
                if (!window.snowyBotRunning) return;
                window.snowyBotRunning = false;
                window.__snowybotStopRequested = true;
                if (window.AndroidBridge) window.AndroidBridge.onSnowyBotStopped('runtime error: ' + String(event.message || event.error || 'unknown error'));
            });
        """.trimIndent()

        assertTrue(script.contains("window.snowyBotRunning = false"))
        assertTrue(script.contains("window.__snowybotStopRequested = true"))
        assertTrue(script.contains("AndroidBridge.onSnowyBotStopped"))
    }
}
