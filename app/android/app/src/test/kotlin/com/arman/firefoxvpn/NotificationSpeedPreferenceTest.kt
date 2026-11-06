package com.arman.firefoxvpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSpeedPreferenceTest {
    @Test
    fun savedFalseFromPreviousReleaseMigratesToEnabled() {
        assertTrue(NotificationSpeedPreference.resolve(savedValue = false, migrationApplied = false))
    }

    @Test
    fun userCanTurnSpeedOffAfterMigration() {
        assertFalse(NotificationSpeedPreference.resolve(savedValue = false, migrationApplied = true))
    }

    @Test
    fun freshInstallDefaultsToEnabled() {
        assertTrue(NotificationSpeedPreference.resolve(savedValue = null, migrationApplied = true))
    }
}
