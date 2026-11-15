package com.koi.backend.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileManagerTest {

    @Test
    fun testIsUpdate_freshInstall_returnsFalse() {
        val now = 1731135000000L
        // On clean install, firstInstallTime == lastUpdateTime
        assertFalse(UserProfileManager.isUpdate(firstInstallTime = now, lastUpdateTime = now))
    }

    @Test
    fun testIsUpdate_freshInstall_tinyJitter_returnsFalse() {
        val now = 1731135000000L
        // Within 1 second tolerance
        assertFalse(UserProfileManager.isUpdate(firstInstallTime = now, lastUpdateTime = now + 500L))
    }

    @Test
    fun testIsUpdate_appUpdate_returnsTrue() {
        val installTime = 1730000000000L
        val updateTime = 1731135000000L // 13+ days later
        assertTrue(UserProfileManager.isUpdate(firstInstallTime = installTime, lastUpdateTime = updateTime))
    }

    @Test
    fun testIsUpdate_appUpdateWithPriorData_returnsTrue() {
        val now = 1731135000000L
        // Even if installTime == updateTime, if prior user data exists on device, it's an update
        assertTrue(UserProfileManager.isUpdate(firstInstallTime = now, lastUpdateTime = now, hasPriorUserData = true))
    }

    @Test
    fun testShouldPromptForUsername_initialInstallFirstEverOpen_returnsTrue() {
        val now = 1731135000000L
        val shouldPrompt = UserProfileManager.shouldPromptForUsername(
            hasPromptedBefore = false,
            firstInstallTime = now,
            lastUpdateTime = now,
            hasPriorUserData = false
        )
        assertTrue(shouldPrompt)
    }

    @Test
    fun testShouldPromptForUsername_afterInitialPromptCompleted_returnsFalse() {
        val now = 1731135000000L
        val shouldPrompt = UserProfileManager.shouldPromptForUsername(
            hasPromptedBefore = true,
            firstInstallTime = now,
            lastUpdateTime = now,
            hasPriorUserData = false
        )
        assertFalse(shouldPrompt)
    }

    @Test
    fun testShouldPromptForUsername_duringAppUpdate_returnsFalse() {
        val installTime = 1730000000000L
        val updateTime = 1731135000000L
        val shouldPrompt = UserProfileManager.shouldPromptForUsername(
            hasPromptedBefore = false,
            firstInstallTime = installTime,
            lastUpdateTime = updateTime,
            hasPriorUserData = false
        )
        // Strictly must NOT prompt during an update!
        assertFalse(shouldPrompt)
    }

    @Test
    fun testShouldPromptForUsername_existingUserUpdatingWithPriorData_returnsFalse() {
        val now = 1731135000000L
        val shouldPrompt = UserProfileManager.shouldPromptForUsername(
            hasPromptedBefore = false,
            firstInstallTime = now,
            lastUpdateTime = now,
            hasPriorUserData = true
        )
        assertFalse(shouldPrompt)
    }
}
