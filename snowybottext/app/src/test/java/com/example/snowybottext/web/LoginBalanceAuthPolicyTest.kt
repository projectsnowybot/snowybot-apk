package com.example.snowybottext.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginBalanceAuthPolicyTest {
    @Test
    fun positiveBalanceConfirmsRegardlessOfPreviousBalanceOrLoginFields() {
        assertEquals(LoginBalanceAuthPolicy.Decision.CONFIRMED, LoginBalanceAuthPolicy.evaluate(12.5))
        assertEquals(LoginBalanceAuthPolicy.Decision.CONFIRMED, LoginBalanceAuthPolicy.evaluate(12.5))
    }

    @Test
    fun positiveBalanceImmediatelyCompletesAndLaterPollOrTimeoutIsIgnored() {
        val policy = LoginStabilizationPolicy()

        assertEquals(LoginStabilizationPolicy.Action.COMPLETE, policy.onBalance(12.5))
        assertTrue(policy.isCompleted())
        assertEquals(LoginStabilizationPolicy.Action.IGNORE, policy.onBalance(13.0))
        assertEquals(LoginStabilizationPolicy.Action.IGNORE, policy.onTimeout())
        assertTrue(policy.isCompleted())
    }

    @Test
    fun latePositiveBalanceAfterTimeoutCannotReviveLoginCompletion() {
        val policy = LoginStabilizationPolicy()

        assertEquals(LoginStabilizationPolicy.Action.WAIT, policy.onTimeout())
        assertEquals(LoginStabilizationPolicy.Action.IGNORE, policy.onBalance(9.0))
    }

    @Test
    fun nonPositiveOrInvalidBalanceKeepsPollingUntilBoundedTimeout() {
        val policy = LoginStabilizationPolicy()

        assertEquals(LoginStabilizationPolicy.Action.WAIT, policy.onBalance(0.0))
        assertEquals(LoginStabilizationPolicy.Action.WAIT, policy.onBalance(null))
        assertEquals(LoginStabilizationPolicy.Action.WAIT, policy.onBalance(Double.NaN))
        assertFalse(policy.isCompleted())
        assertEquals(LoginStabilizationPolicy.Action.WAIT, policy.onTimeout())
        assertTrue(policy.isCompleted())
        assertEquals(LoginStabilizationPolicy.Action.IGNORE, policy.onBalance(1.0))
    }

    @Test
    fun zeroMissingAndInvalidBalancesAreRejected() {
        assertEquals(LoginBalanceAuthPolicy.Decision.WAIT, LoginBalanceAuthPolicy.evaluate(0.0))
        assertEquals(LoginBalanceAuthPolicy.Decision.WAIT, LoginBalanceAuthPolicy.evaluate(null))
        assertEquals(LoginBalanceAuthPolicy.Decision.WAIT, LoginBalanceAuthPolicy.evaluate(Double.NaN))
        assertEquals(LoginBalanceAuthPolicy.Decision.WAIT, LoginBalanceAuthPolicy.evaluate(Double.POSITIVE_INFINITY))
    }
}
