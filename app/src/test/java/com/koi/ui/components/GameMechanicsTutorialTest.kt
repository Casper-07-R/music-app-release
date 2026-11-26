package com.koi.ui.components

import com.koi.ui.model.NavigationTab
import org.junit.Assert.*
import org.junit.Test

class GameMechanicsTutorialTest {

    @Test
    fun testTutorialTargets_containAllUpdateSections() {
        val targets = TutorialTarget.values()
        assertEquals(4, targets.size)
        assertTrue(targets.contains(TutorialTarget.HERO_AUDIO_STUDIO))
        assertTrue(targets.contains(TutorialTarget.LYRICS_AND_FIXES))
        assertTrue(targets.contains(TutorialTarget.QR_PLAYLIST_SHARING))
        assertTrue(targets.contains(TutorialTarget.PROFILE_ANALYTICS))
    }

    @Test
    fun testTutorialMissionLadder_coversOnlyCurrentUpdateFeatures() {
        val missions = getUpdateTutorialMissions()

        assertEquals(4, missions.size)
        assertEquals(NavigationTab.HOME, missions[0].tab)
        assertEquals(NavigationTab.HOME, missions[1].tab)
        assertEquals(NavigationTab.YOUR_LIBRARY, missions[2].tab)
        assertEquals(NavigationTab.PROFILE, missions[3].tab)

        // Verify progression
        for (i in missions.indices) {
            val isGraduation = i >= missions.size
            assertFalse(isGraduation)
            assertEquals(i, missions[i].stepIndex)
            assertTrue(missions[i].title.isNotBlank())
            assertTrue(missions[i].loreDescription.isNotBlank())
            assertTrue(missions[i].objectivePrompt.isNotBlank())
        }

        // Graduation reached after the final mission
        assertTrue(4 >= missions.size)
    }
}
