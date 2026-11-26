package com.koi.ui.components

import androidx.compose.ui.graphics.Color
import com.koi.backend.update.GitHubRelease
import com.koi.ui.theme.*
import java.util.Locale

enum class UpdateFeatureType {
    AUDIO_8D,
    SLEEP_TIMER,
    PERFORMANCE,
    PLAYLIST_CUSTOMIZATION,
    BUG_FIX,
    GENERAL
}

data class UpdateFeatureItem(
    val id: String,
    val type: UpdateFeatureType,
    val categoryLabel: String,
    val badgeColor: Color,
    val title: String,
    val subtitle: String,
    val description: String,
    val tip: String,
    val tag: String = "NEW",
    val highlightMetrics: String? = null
)

object UpdateFeatureParser {

    private val CyanAccent = Color(0xFF00B4D8)
    private val EmeraldAccent = Color(0xFF2EC4B6)
    private val RoseAccent = Color(0xFFFF758F)

    /**
     * Parses the release changelog into a list of interactive feature pages.
     * Always returns at least 2 distinct, engaging pages so the interactive
     * step-by-step update flow is rich and informative.
     */
    fun parse(release: GitHubRelease): List<UpdateFeatureItem> {
        val body = release.body?.replace("\r\n", "\n")?.trim()
        val parsedItems = mutableListOf<UpdateFeatureItem>()

        if (!body.isNullOrBlank()) {
            var currentHeader = ""
            val lines = body.lines()

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isBlank()) continue

                // Check for Markdown headers (e.g. ### Added Features, ## Bug Fixes)
                if (line.startsWith("#")) {
                    currentHeader = line.trimStart('#').trim()
                    continue
                }

                // Check for bullet items
                val bulletText = extractBulletText(line)
                if (bulletText != null && bulletText.length >= 3) {
                    val item = buildFeatureItemFromBullet(bulletText, currentHeader, parsedItems.size)
                    parsedItems.add(item)
                }
            }
        }

        // If release body was empty or parsed fewer than 2 items, enrich with showcase features
        return if (parsedItems.size >= 2) {
            parsedItems.take(5) // Keep carousel focused and engaging (max 5 pages)
        } else if (parsedItems.size == 1) {
            // Keep the 1 parsed feature, and supplement with the best spotlight feature
            val first = parsedItems.first()
            val supplements = getDefaultShowcaseFeatures().filter { it.type != first.type }
            listOf(first) + supplements.take(2)
        } else {
            getDefaultShowcaseFeatures()
        }
    }

    private fun extractBulletText(line: String): String? {
        val trimmed = line.trim()
        return when {
            trimmed.startsWith("* ") -> trimmed.removePrefix("* ").trim()
            trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim()
            trimmed.startsWith("+ ") -> trimmed.removePrefix("+ ").trim()
            trimmed.startsWith("• ") -> trimmed.removePrefix("• ").trim()
            trimmed.matches(Regex("""^\d+\.\s+.*""")) -> trimmed.replace(Regex("""^\d+\.\s+"""), "").trim()
            else -> null
        }
    }

    private fun buildFeatureItemFromBullet(
        bulletText: String,
        header: String,
        index: Int
    ): UpdateFeatureItem {
        // Strip markdown formatting like **bold** or *italic*
        val cleanText = bulletText
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .trim()

        // Check if there is a title and detail separator (e.g. "Title: Description" or "Title - Description")
        val colonSplit = cleanText.split(Regex("[:–—-]\\s+"), limit = 2)
        val rawTitle = if (colonSplit.size == 2 && colonSplit[0].length in 3..40) {
            colonSplit[0].trim()
        } else {
            cleanText
        }
        val rawDetail = if (colonSplit.size == 2 && colonSplit[0].length in 3..40) {
            colonSplit[1].trim()
        } else {
            ""
        }

        val lowerCombined = "$header $cleanText".lowercase(Locale.ROOT)
        val type = detectType(lowerCombined)

        val categoryLabel = when (type) {
            UpdateFeatureType.AUDIO_8D -> "AUDIO EXPERIENCE"
            UpdateFeatureType.SLEEP_TIMER -> "SMART SLEEP TIMER"
            UpdateFeatureType.PERFORMANCE -> "TURBO PERFORMANCE"
            UpdateFeatureType.BUG_FIX -> "STABILITY & FIX"
            UpdateFeatureType.PLAYLIST_CUSTOMIZATION -> "MUSIC EXPERIENCE"
            UpdateFeatureType.GENERAL -> if (header.isNotBlank()) header.uppercase(Locale.ROOT) else "NEW FEATURE"
        }

        val badgeColor = when (type) {
            UpdateFeatureType.AUDIO_8D -> RogGoldPrimary
            UpdateFeatureType.SLEEP_TIMER -> RogGoldChampagne
            UpdateFeatureType.PERFORMANCE -> CyanAccent
            UpdateFeatureType.BUG_FIX -> EmeraldAccent
            UpdateFeatureType.PLAYLIST_CUSTOMIZATION -> RoseAccent
            UpdateFeatureType.GENERAL -> RogGoldPrimary
        }

        val tag = when (type) {
            UpdateFeatureType.AUDIO_8D -> "HOT"
            UpdateFeatureType.SLEEP_TIMER -> "NEW"
            UpdateFeatureType.PERFORMANCE -> "FAST"
            UpdateFeatureType.BUG_FIX -> "FIXED"
            UpdateFeatureType.PLAYLIST_CUSTOMIZATION -> "UPDATED"
            UpdateFeatureType.GENERAL -> "NEW"
        }

        val title = formatTitle(rawTitle, type)
        val description = if (rawDetail.isNotBlank()) {
            rawDetail
        } else {
            getDefaultDescriptionForType(type, rawTitle)
        }

        val tip = getDefaultTipForType(type)

        return UpdateFeatureItem(
            id = "feature_$index",
            type = type,
            categoryLabel = categoryLabel,
            badgeColor = badgeColor,
            title = title,
            subtitle = getDefaultSubtitleForType(type),
            description = description,
            tip = tip,
            tag = tag,
            highlightMetrics = null
        )
    }

    private fun detectType(text: String): UpdateFeatureType {
        return when {
            text.contains("8d") || text.contains("surround") || text.contains("binaural") ||
                    text.contains("equalizer") || text.contains("dsp") || text.contains("bass") ||
                    text.contains("audio quality") || text.contains("spatial") -> UpdateFeatureType.AUDIO_8D

            text.contains("sleep") || text.contains("timer") || text.contains("bedtime") ||
                    text.contains("fade out") || text.contains("fade-out") -> UpdateFeatureType.SLEEP_TIMER

            text.contains("fix") || text.contains("crash") || text.contains("bug") ||
                    text.contains("error") || text.contains("freeze") || text.contains("resolve") ||
                    text.contains("stability") -> UpdateFeatureType.BUG_FIX

            text.contains("speed") || text.contains("buffer") || text.contains("latency") ||
                    text.contains("performance") || text.contains("battery") || text.contains("memory") ||
                    text.contains("fast") || text.contains("optimize") || text.contains("streaming") ||
                    text.contains("fallback") || text.contains("network") -> UpdateFeatureType.PERFORMANCE

            text.contains("playlist") || text.contains("lyrics") || text.contains("lrc") ||
                    text.contains("cover") || text.contains("queue") || text.contains("history") ||
                    text.contains("profile") || text.contains("theme") -> UpdateFeatureType.PLAYLIST_CUSTOMIZATION

            else -> UpdateFeatureType.GENERAL
        }
    }

    private fun formatTitle(raw: String, type: UpdateFeatureType): String {
        val trimmed = raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        if (trimmed.length > 35) {
            return trimmed.take(35).trimEnd() + "..."
        }
        return when (type) {
            UpdateFeatureType.AUDIO_8D -> if (!trimmed.contains("8D", ignoreCase = true)) "8D Audio: $trimmed" else trimmed
            UpdateFeatureType.SLEEP_TIMER -> if (!trimmed.contains("Timer", ignoreCase = true)) "Sleep Timer: $trimmed" else trimmed
            else -> trimmed
        }
    }

    private fun getDefaultSubtitleForType(type: UpdateFeatureType): String {
        return when (type) {
            UpdateFeatureType.AUDIO_8D -> "Dynamic 360° binaural spatial sound"
            UpdateFeatureType.SLEEP_TIMER -> "Drift off to music with gradual fade-out"
            UpdateFeatureType.PERFORMANCE -> "Lightning fast streaming & responsiveness"
            UpdateFeatureType.BUG_FIX -> "Smoother playback and crash prevention"
            UpdateFeatureType.PLAYLIST_CUSTOMIZATION -> "Personalized music library and design"
            UpdateFeatureType.GENERAL -> "Enhanced capabilities and polish"
        }
    }

    private fun getDefaultDescriptionForType(type: UpdateFeatureType, originalText: String): String {
        return when (type) {
            UpdateFeatureType.AUDIO_8D ->
                "Listen in a 360-degree sound field with orbital panning around your head. Easily customize orbit speed, bass boost, and room reverb for a live concert experience."

            UpdateFeatureType.SLEEP_TIMER ->
                "Set a peaceful timer for 15, 30, 45, or 60 minutes. Koi gently reduces playback volume over the final minutes before pausing, so you can fall asleep without sudden silence."

            UpdateFeatureType.PERFORMANCE ->
                "Stream buffering and track transitions are now significantly faster with intelligent prefetching and adaptive multi-tier bitrate fallback, eliminating audio tearing even in low network coverage."

            UpdateFeatureType.BUG_FIX ->
                "Resolved: '$originalText'. Optimized Android background service lifecycle and improved audio focus handling during phone calls and alerts."

            UpdateFeatureType.PLAYLIST_CUSTOMIZATION ->
                "Enjoy richer playlist personalization with custom cover art, real-time synchronized lyrics, and smoother queue management."

            UpdateFeatureType.GENERAL ->
                "$originalText. Packed with polish, UI responsiveness enhancements, and reliability updates."
        }
    }

    private fun getDefaultTipForType(type: UpdateFeatureType): String {
        return when (type) {
            UpdateFeatureType.AUDIO_8D -> "💡 Pro Tip: Open the Now Playing screen and tap the 8D Studio button to customize your soundstage."
            UpdateFeatureType.SLEEP_TIMER -> "💡 Pro Tip: Tap the Moon icon on Home or in the player menu to schedule your timer."
            UpdateFeatureType.PERFORMANCE -> "💡 Pro Tip: Songs now pre-cache seamlessly for instant gapless playback."
            UpdateFeatureType.BUG_FIX -> "💡 Pro Tip: Disable battery restrictions in Settings for uninterrupted background music."
            UpdateFeatureType.PLAYLIST_CUSTOMIZATION -> "💡 Pro Tip: Tap the three dots on any playlist to set a custom cover image."
            UpdateFeatureType.GENERAL -> "💡 Pro Tip: Explore the settings screen to tailor Koi to your listening style."
        }
    }

    /**
     * Default high-polish spotlight features shown when release notes are brief or empty.
     */
    fun getDefaultShowcaseFeatures(): List<UpdateFeatureItem> {
        return listOf(
            UpdateFeatureItem(
                id = "showcase_8d",
                type = UpdateFeatureType.AUDIO_8D,
                categoryLabel = "AUDIO STUDIO",
                badgeColor = RogGoldPrimary,
                title = "360° Binaural 8D Audio",
                subtitle = "Concert-grade spatial panning",
                description = "Transform ordinary stereo into a dynamic 360-degree spatial soundscape. Audio orbits smoothly around your head with real-time binaural filters and customizable speeds.",
                tip = "💡 Pro Tip: Open the Now Playing screen and tap the 8D Studio icon to customize orbit speed.",
                tag = "HOT"
            ),
            UpdateFeatureItem(
                id = "showcase_timer",
                type = UpdateFeatureType.SLEEP_TIMER,
                categoryLabel = "SMART TIMER",
                badgeColor = RogGoldChampagne,
                title = "Gentle Fade-Out Sleep Timer",
                subtitle = "Fall asleep peacefully to music",
                description = "Set your ideal timer duration. Koi gently reduces the music volume during the final minutes before pausing, ensuring peaceful sleep without jarring interruptions.",
                tip = "💡 Pro Tip: Tap the Moon icon on the Home screen or player menu to set a timer.",
                tag = "NEW"
            ),
            UpdateFeatureItem(
                id = "showcase_turbo",
                type = UpdateFeatureType.PERFORMANCE,
                categoryLabel = "TURBO ENGINE",
                badgeColor = CyanAccent,
                title = "Instant Stream Pre-Buffering",
                subtitle = "Zero-latency track switching",
                description = "Engineered with intelligent predictive streaming. Next tracks buffer ahead of time for instant playback without loading delays or stutter on mobile networks.",
                tip = "💡 Pro Tip: Experience instant song transitions across all playlists and albums.",
                tag = "FAST",
                highlightMetrics = "10x Faster"
            ),
            UpdateFeatureItem(
                id = "showcase_stability",
                type = UpdateFeatureType.BUG_FIX,
                categoryLabel = "STABILITY & FIX",
                badgeColor = EmeraldAccent,
                title = "Background Playback Hardening",
                subtitle = "Continuous music without interruptions",
                description = "Enhanced Android foreground service lifecycle to prevent background OS termination. Smooth audio focus resumption when phone calls and notifications finish.",
                tip = "💡 Pro Tip: You can disable OS battery optimization in Settings for maximum stability.",
                tag = "FIXED"
            )
        )
    }
}
