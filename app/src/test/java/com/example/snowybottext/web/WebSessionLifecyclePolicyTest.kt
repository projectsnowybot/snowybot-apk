package com.example.snowybottext.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionLifecyclePolicyTest {
    @Test
    fun activityAttachLoadsExactlyOnceAndBackgroundForegroundDoesNotReload() {
        val policy = WebSessionLifecyclePolicy()

        assertTrue(policy.onSessionAttached())
        assertFalse(policy.onSessionAttached())
        assertFalse(policy.onBackgroundOrForeground())
        assertFalse(policy.onBackgroundOrForeground())
        assertFalse(policy.onSessionAttached())
    }

    @Test
    fun loginAndRunReuseTheSameSessionUntilExplicitResetAll() {
        val policy = WebSessionLifecyclePolicy()
        assertTrue(policy.onSessionAttached())
        assertEquals(RunSessionAction.REUSE_EXISTING_SESSION, policy.onRunRequested())
        val beforeReset = policy.sessionGeneration()

        policy.onResetAll()

        assertEquals(beforeReset + 1, policy.sessionGeneration())
        assertEquals(RunSessionAction.CREATE_AND_LOAD_SESSION, policy.onRunRequested())
    }

    @Test
    fun loginUsesTheLoadedPageInsteadOfStartingASecondWarmupNavigation() {
        val policy = WebSessionLifecyclePolicy()
        assertTrue(policy.onSessionAttached())
        policy.onPageLoaded()

        assertFalse(policy.onSessionAttached())
        assertEquals(RunSessionAction.REUSE_EXISTING_SESSION, policy.onRunRequested())
        assertTrue(policy.isPageLoaded())
    }

    @Test
    fun domReadinessWaitsUpToExactlyThirtyFiveSeconds() {
        val policy = PageDomReadinessPolicy()
        policy.begin(nowMs = 10_000L)

        assertFalse(policy.isTimedOut(44_999L))
        assertTrue(policy.isTimedOut(45_000L))
        assertFalse(policy.isTimedOut(46_000L))
    }

    @Test
    fun domBecomingAvailableBeforeDeadlineCancelsTimeout() {
        val policy = PageDomReadinessPolicy()
        policy.begin(nowMs = 0L)

        assertTrue(policy.onDomAvailable())
        assertFalse(policy.isTimedOut(PageDomReadinessPolicy.TIMEOUT_MS))
    }
}
