package com.koi.backend.audio

import com.google.gson.Gson

enum class SpatialMode(val label: String) {
    OFF("Off"),
    MODE_8D("8D"),
    MODE_16D("16D")
}

enum class AudioProfile(
    val label: String,
    val shortDescription: String,
    val fullDescription: String
) {
    BALANCED(
        label = "Balanced",
        shortDescription = "Studio clarity • Ear pleasing",
        fullDescription = "Immersive spatial audio. Sub-bass centered, pure acoustics, zero distortion or audio tears. Built for pristine fidelity."
    ),
    SPATIAL_360(
        label = "Surround 360°",
        shortDescription = "Surround sound • Gentle 360° motion",
        fullDescription = "Sit in the sweet spot between all speakers. All-around surround envelopment with gentle, smooth 360° panning motion."
    ),
    NIGHTCORE(
        label = "Nightcore",
        shortDescription = "1.25x • +3.5 semitones",
        fullDescription = "Accelerated tempo with crisp harmonic pitch-shift and bright treble clarity for high-energy spatial immersion."
    ),
    SLOWED_REVERB(
        label = "Slowed + Reverbed",
        shortDescription = "0.85x • Deep & spacious",
        fullDescription = "Relaxed tempo with dropped pitch, deep warm sub-bass, and lush spacious studio reverberation."
    )
}

enum class RotationPattern(val label: String) {
    SINE("Sine (Smooth)"),
    TRIANGLE("Triangle (Linear)"),
    SQUARE("Square (Hard Pan)")
}

enum class RotationDirection(val label: String) {
    CLOCKWISE("Clockwise"),
    COUNTER_CLOCKWISE("Counter-Clockwise")
}

enum class BassBoostFrequency(val freqHz: Int, val label: String) {
    FREQ_60HZ(60, "Sub Bass (60 Hz)"),
    FREQ_80HZ(80, "Rock Punch Bass (80 Hz)"),
    FREQ_100HZ(100, "Punch Bass (100 Hz)"),
    FREQ_110HZ(110, "Punch Bass (110 Hz)"),
    FREQ_200HZ(200, "Upper Bass (200 Hz)")
}

data class AudioDspConfig(
    val masterEnabled: Boolean = true,
    val abBypass: Boolean = false,
    val safeMode: Boolean = true,
    val spatialMode: SpatialMode = SpatialMode.OFF,
    val activeProfile: AudioProfile = AudioProfile.BALANCED,
    val is8dEnabled: Boolean = false,
    val rotationSpeedSeconds: Float = 11.0f,
    val panDepth: Float = 0.80f,
    val crossoverSplitHz: Int = 220,
    val rotationPattern: RotationPattern = RotationPattern.SINE,
    val rotationDirection: RotationDirection = RotationDirection.CLOCKWISE,
    val useDualTapEcho: Boolean = false,
    val echoTap1Ms: Float = 0f,
    val echoTap2Ms: Float = 0f,
    val echoDecay1: Float = 0f,
    val echoDecay2: Float = 0f,
    val echoInGain: Float = 1.0f,
    val echoOutGain: Float = 1.0f,
    val roomSize: Float = 0.38f,
    val reverbLevel: Float = 0.05f,
    val distanceFrontBackCue: Boolean = false,
    val binauralHrtf: Boolean = true,
    val crossfeed: Boolean = false,
    val crossfeedAmount: Float = 0.0f,
    val bassBoostEnabled: Boolean = true,
    val bassBoostFreq: BassBoostFrequency = BassBoostFrequency.FREQ_80HZ,
    val bassBoostGainDb: Float = 2.8f,
    val subBassCentering: Boolean = true,
    val cleanBassProtection: Boolean = true,
    val trebleClarityDb: Float = 1.1f,
    val trebleFreqHz: Int = 8000,
    val compressorEnabled: Boolean = true,
    val limiterEnabled: Boolean = true,
    val preGainDb: Float = 2.0f,
    val volumeNormalization: Boolean = true,
    val stereoWidth: Float = 1.14f,
    val balance: Float = 0.0f,
    val playbackSpeed: Float = 1.0f,
    val pitchSemitones: Float = 0.0f,
    val lockPitchWithSpeed: Boolean = false,
    val activePresetName: String = "Balanced",
    val perTrackMemoryEnabled: Boolean = false
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun createConfig(
            spatialMode: SpatialMode = SpatialMode.MODE_8D,
            profile: AudioProfile = AudioProfile.BALANCED
        ): AudioDspConfig {
            val isEnabled = spatialMode != SpatialMode.OFF
            val speed = when (spatialMode) {
                SpatialMode.MODE_16D -> 7.5f
                SpatialMode.MODE_8D -> 13.0f
                SpatialMode.OFF -> 13.0f
            }
            val depth = when (spatialMode) {
                SpatialMode.MODE_16D -> 0.88f
                SpatialMode.MODE_8D -> 0.80f
                SpatialMode.OFF -> 0.80f
            }

            return when (profile) {
                AudioProfile.BALANCED -> AudioDspConfig(
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = when (spatialMode) {
                        SpatialMode.MODE_16D -> 7.5f
                        else -> 11.0f
                    },
                    panDepth = depth,
                    crossoverSplitHz = 220,
                    subBassCentering = isEnabled,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = false,
                    echoTap1Ms = 0f,
                    echoTap2Ms = 0f,
                    echoDecay1 = 0f,
                    echoDecay2 = 0f,
                    echoInGain = 1.0f,
                    echoOutGain = 1.0f,
                    roomSize = 0.38f,
                    reverbLevel = if (isEnabled) 0.05f else 0.0f,
                    binauralHrtf = isEnabled,
                    distanceFrontBackCue = false,
                    crossfeed = false,
                    crossfeedAmount = 0.0f,
                    bassBoostEnabled = isEnabled,
                    bassBoostFreq = BassBoostFrequency.FREQ_80HZ,
                    bassBoostGainDb = if (isEnabled) 2.8f else 0.0f,
                    cleanBassProtection = true,
                    compressorEnabled = true,
                    volumeNormalization = true,
                    limiterEnabled = isEnabled,
                    trebleClarityDb = if (isEnabled) 1.1f else 0.0f,
                    trebleFreqHz = 8000,
                    preGainDb = if (isEnabled) 2.0f else 0.0f,
                    stereoWidth = if (isEnabled) 1.14f else 1.0f,
                    playbackSpeed = 1.0f,
                    pitchSemitones = 0.0f,
                    lockPitchWithSpeed = false,
                    activePresetName = "Balanced"
                )

                AudioProfile.SPATIAL_360 -> AudioDspConfig(
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = speed * 1.15f,
                    panDepth = 0.28f,
                    crossoverSplitHz = 220,
                    subBassCentering = isEnabled,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = false,
                    echoTap1Ms = 0f,
                    echoTap2Ms = 0f,
                    echoDecay1 = 0f,
                    echoDecay2 = 0f,
                    echoInGain = 1.0f,
                    echoOutGain = 1.0f,
                    roomSize = 0.35f,
                    reverbLevel = if (isEnabled) 0.08f else 0.0f,
                    binauralHrtf = false,
                    distanceFrontBackCue = false,
                    crossfeed = isEnabled,
                    crossfeedAmount = if (isEnabled) 0.22f else 0.0f,
                    bassBoostEnabled = isEnabled,
                    bassBoostFreq = BassBoostFrequency.FREQ_80HZ,
                    bassBoostGainDb = if (isEnabled) 3.2f else 0.0f,
                    cleanBassProtection = true,
                    compressorEnabled = true,
                    volumeNormalization = true,
                    limiterEnabled = isEnabled,
                    trebleClarityDb = if (isEnabled) 1.2f else 0.0f,
                    trebleFreqHz = 8000,
                    preGainDb = if (isEnabled) -1.6f else 0.0f,
                    stereoWidth = if (isEnabled) 1.38f else 1.0f,
                    playbackSpeed = 1.0f,
                    pitchSemitones = 0.0f,
                    lockPitchWithSpeed = false,
                    activePresetName = "Surround 360°"
                )

                AudioProfile.NIGHTCORE -> AudioDspConfig(
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = speed,
                    panDepth = depth,
                    crossoverSplitHz = 250,
                    subBassCentering = true,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = false,
                    echoTap1Ms = 0f,
                    echoTap2Ms = 0f,
                    echoDecay1 = 0f,
                    echoDecay2 = 0f,
                    echoInGain = 1.0f,
                    echoOutGain = 1.0f,
                    roomSize = 0.40f,
                    reverbLevel = 0.10f,
                    binauralHrtf = true,
                    distanceFrontBackCue = true,
                    crossfeed = false,
                    crossfeedAmount = 0.0f,
                    bassBoostEnabled = false,
                    cleanBassProtection = true,
                    limiterEnabled = true,
                    trebleClarityDb = 1.5f,
                    trebleFreqHz = 8000,
                    preGainDb = -2.0f,
                    stereoWidth = 1.05f,
                    playbackSpeed = 1.25f,
                    pitchSemitones = 3.5f,
                    lockPitchWithSpeed = false,
                    activePresetName = "Nightcore"
                )

                AudioProfile.SLOWED_REVERB -> AudioDspConfig(
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = speed,
                    panDepth = depth,
                    crossoverSplitHz = 250,
                    subBassCentering = true,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = false,
                    echoTap1Ms = 0f,
                    echoTap2Ms = 0f,
                    echoDecay1 = 0f,
                    echoDecay2 = 0f,
                    echoInGain = 1.0f,
                    echoOutGain = 1.0f,
                    roomSize = 0.74f,
                    reverbLevel = 0.42f,
                    binauralHrtf = true,
                    distanceFrontBackCue = true,
                    crossfeed = true,
                    crossfeedAmount = 0.20f,
                    bassBoostEnabled = true,
                    bassBoostFreq = BassBoostFrequency.FREQ_60HZ,
                    bassBoostGainDb = 2.5f,
                    cleanBassProtection = true,
                    limiterEnabled = true,
                    trebleClarityDb = 0.5f,
                    trebleFreqHz = 8000,
                    preGainDb = -3.0f,
                    stereoWidth = 1.15f,
                    playbackSpeed = 0.85f,
                    pitchSemitones = -2.5f,
                    lockPitchWithSpeed = false,
                    activePresetName = "Slowed + Reverbed"
                )
            }
        }

        fun fromJson(json: String): AudioDspConfig? {
            return try {
                val sanitizedJson = json.replace("\"CONCERT_HALL\"", "\"BALANCED\"")
                val cfg = Gson().fromJson(sanitizedJson, AudioDspConfig::class.java) ?: return null
                var fixed = cfg
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.spatialMode == null) {
                    fixed = fixed.copy(spatialMode = if (fixed.is8dEnabled) SpatialMode.MODE_8D else SpatialMode.OFF)
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.activeProfile == null) {
                    fixed = fixed.copy(activeProfile = AudioProfile.BALANCED)
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.bassBoostFreq == null) {
                    fixed = fixed.copy(bassBoostFreq = BassBoostFrequency.FREQ_100HZ)
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.rotationPattern == null) {
                    fixed = fixed.copy(rotationPattern = RotationPattern.SINE)
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.rotationDirection == null) {
                    fixed = fixed.copy(rotationDirection = RotationDirection.CLOCKWISE)
                }
                fixed
            } catch (e: Exception) {
                null
            }
        }
    }
}
