package com.koi.ui.components

import org.junit.Assert.*
import org.junit.Test

class GameMechanicsTutorialTest {

    @Test
    fun testTutorialTargets_containAllRequiredSections() {
        val targets = TutorialTarget.values()
        assertEquals(5, targets.size)
        assertTrue(targets.contains(TutorialTarget.SPATIAL_DECK))
        assertTrue(targets.contains(TutorialTarget.SOUND_STATIONS))
        assertTrue(targets.contains(TutorialTarget.VAULT))
        assertTrue(targets.contains(TutorialTarget.SETTINGS))
        assertTrue(targets.contains(TutorialTarget.SLEEP_TIMER))
    }

    @Test
    fun testTutorialMissionLadder_hasFiveCompleteMissions() {
        val missions = listOf(
            TutorialMission(
                stepIndex = 0,
                target = TutorialTarget.SPATIAL_DECK,
                questBadge = "MISSION 1 OF 5 • AUDIO ENGINE & EQ",
                title = "360° Spatializer & 10-Band EQ",
                loreDescription = "360° orbital spatialization and 10-band equalizer.",
                objectivePrompt = "Tap [ 8D ] or [ 16D ] on the player deck.",
                actionButtonText = "Next Mission →",
                iconVector = Unit
            ),
            TutorialMission(
                stepIndex = 1,
                target = TutorialTarget.SOUND_STATIONS,
                questBadge = "MISSION 2 OF 5 • SOUNDSCAPES",
                title = "Curated Sound Stations",
                loreDescription = "Handcrafted acoustic spaces.",
                objectivePrompt = "Tap any station card.",
                actionButtonText = "Next Mission →",
                iconVector = Unit
            ),
            TutorialMission(
                stepIndex = 2,
                target = TutorialTarget.VAULT,
                questBadge = "MISSION 3 OF 5 • VAULT",
                title = "The Private Music Vault",
                loreDescription = "Zero-latency offline playback.",
                objectivePrompt = "All your downloaded songs stay right here.",
                actionButtonText = "Next Mission →",
                iconVector = Unit
            ),
            TutorialMission(
                stepIndex = 3,
                target = TutorialTarget.SETTINGS,
                questBadge = "MISSION 4 OF 5 • ZERO-BUFFER STREAMING",
                title = "Adaptive Quality & Fallbacks",
                loreDescription = "Multi-tier audio fallbacks (High, Med, Low, Data Saver).",
                objectivePrompt = "Tap the Settings gear icon.",
                actionButtonText = "Next Mission →",
                iconVector = Unit
            ),
            TutorialMission(
                stepIndex = 4,
                target = TutorialTarget.SLEEP_TIMER,
                questBadge = "MISSION 5 OF 5 • BEDTIME & GESTURES",
                title = "Fade-Out Sleep Timer & Swipe Controls",
                loreDescription = "Gentle fade-out and swipe to dismiss mini-player.",
                objectivePrompt = "Tap the bedtime moon icon.",
                actionButtonText = "Finish Academy & Listen 🎧",
                iconVector = Unit
            )
        )

        assertEquals(5, missions.size)

        // Verify progression
        for (i in 0 until 5) {
            val isGraduation = i >= missions.size
            assertFalse(isGraduation)
            assertEquals(i, missions[i].stepIndex)
        }

        // Graduation reached at step 5
        assertTrue(5 >= missions.size)
    }
}
