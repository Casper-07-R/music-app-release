package com.koi.ui.viewmodel

import com.koi.ui.model.Track
import org.junit.Assert.*
import org.junit.Test

class PlaybackMiniBarDismissTest {

    @Test
    fun testPlaybackUiState_miniBarDismissedDefaultIsFalse() {
        val dummyTrack = Track(
            id = "test_1",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationSeconds = 200
        )
        val state = PlaybackUiState(currentTrack = dummyTrack)
        assertFalse("Default state for isMiniBarDismissed must be false", state.isMiniBarDismissed)
    }

    @Test
    fun testPlaybackUiState_miniBarDismissedToggle() {
        val dummyTrack = Track(
            id = "test_1",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationSeconds = 200
        )
        val state = PlaybackUiState(currentTrack = dummyTrack)
        val dismissedState = state.copy(isMiniBarDismissed = true)
        assertTrue("isMiniBarDismissed must be true after dismissing", dismissedState.isMiniBarDismissed)

        val restoredState = dismissedState.copy(isMiniBarDismissed = false)
        assertFalse("isMiniBarDismissed must be false after playback resumes", restoredState.isMiniBarDismissed)
    }
}
