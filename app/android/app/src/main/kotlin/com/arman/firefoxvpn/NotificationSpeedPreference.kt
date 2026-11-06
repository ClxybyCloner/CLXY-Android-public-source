package com.arman.firefoxvpn

/** Resolves the notification-speed setting when upgrading to live-rate notifications. */
internal object NotificationSpeedPreference {
    /**
     * Existing installations migrate to enabled once, even if the old default
     * was saved as false. After migration, the saved user choice is respected.
     */
    fun resolve(savedValue: Boolean?, migrationApplied: Boolean): Boolean =
        if (migrationApplied) savedValue ?: true else true
}
