package com.koi.backend.audio

import com.google.gson.Gson
import kotlin.math.*

enum class SpatialMode(val label: String) {
    OFF("Off"),
    MODE_8D("8D"),
    MODE_16D("16D")
}

enum class AudioProfile(
    val label: String,
    val tag: String,
    val shortDescription: String,
    val fullDescription: String,
    val highlights: List<String>,
    val bassMeter: Float,
    val stageMeter: Float,
    val clarityMeter: Float
) {
    BALANCED(
        label = "Balanced",
        tag = "Studio",
        shortDescription = "Balanced",
        fullDescription = "Balanced studio acoustics",
        highlights = listOf("Studio", "Anchor", "Clean"),
        bassMeter = 0.55f,
        stageMeter = 0.60f,
        clarityMeter = 0.70f
    ),
    IMMERSIVE_STAGE(
        label = "Immersion",
        tag = "3D Stage",
        shortDescription = "Holographic 3D",
        fullDescription = "Wide 3D holographic soundstage",
        highlights = listOf("Wide", "3D Air", "Deep"),
        bassMeter = 0.80f,
        stageMeter = 1.00f,
        clarityMeter = 0.80f
    ),
    SPATIAL_360(
        label = "Surround 360°",
        tag = "360° Dome",
        shortDescription = "360° Surround",
        fullDescription = "All-around 360° speaker dome",
        highlights = listOf("Dome", "Rear", "Orbit"),
        bassMeter = 0.70f,
        stageMeter = 0.90f,
        clarityMeter = 0.65f
    ),
    ABSOLUTE_HEARING(
        label = "Crystal Hearing",
        tag = "Clarity+",
        shortDescription = "Ultra-Clarity",
        fullDescription = "Razor-sharp vocal & micro-detail clarity",
        highlights = listOf("Vocals", "Detail", "Crisp"),
        bassMeter = 0.35f,
        stageMeter = 0.50f,
        clarityMeter = 1.00f
    ),
    NIGHT_CONCERT(
        label = "Midnight",
        tag = "Warm Hall",
        shortDescription = "Warm & Plush",
        fullDescription = "Warm velvet sub-bass & lush hall",
        highlights = listOf("Sub", "Warm", "Hall"),
        bassMeter = 0.95f,
        stageMeter = 0.85f,
        clarityMeter = 0.50f
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

enum class BassBoostFrequency(val freqHz: Int, val shortLabel: String, val label: String) {
    FREQ_60HZ(60, "60 Hz", "Sub Bass (60 Hz)"),
    FREQ_80HZ(80, "80 Hz", "Rock Punch (80 Hz)"),
    FREQ_100HZ(100, "100 Hz", "Tight Punch (100 Hz)"),
    FREQ_110HZ(110, "110 Hz", "Warm Body (110 Hz)"),
    FREQ_200HZ(200, "200 Hz", "Upper Bass (200 Hz)")
}

data class AudioDspConfig(
    val configVersion: Int = CURRENT_CONFIG_VERSION,
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
    val roomSize: Float = 0.35f,
    val reverbLevel: Float = 0.0f,
    val distanceFrontBackCue: Boolean = false,
    val binauralHrtf: Boolean = false,
    val crossfeed: Boolean = false,
    val crossfeedAmount: Float = 0.0f,
    val bassBoostEnabled: Boolean = false,
    val bassBoostFreq: BassBoostFrequency = BassBoostFrequency.FREQ_80HZ,
    val bassBoostGainDb: Float = 0.0f,
    val subBassCentering: Boolean = true,
    val cleanBassProtection: Boolean = true,
    val trebleClarityDb: Float = 0.0f,
    val trebleFreqHz: Int = 8000,
    val vocalPresenceDb: Float = 0.0f,
    val microDetailLevel: Float = 0.25f,
    val compressorEnabled: Boolean = true,
    val limiterEnabled: Boolean = true,
    val preGainDb: Float = 0.0f,
    val eqPreampDb: Float = 0.0f,
    val autoHeadroomEnabled: Boolean = true,
    val volumeNormalization: Boolean = true,
    val stereoWidth: Float = 1.0f,
    val balance: Float = 0.0f,
    val playbackSpeed: Float = 1.0f,
    val pitchSemitones: Float = 0.0f,
    val lockPitchWithSpeed: Boolean = false,
    val activePresetName: String = "Balanced",
    val perTrackMemoryEnabled: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "Flat",
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        const val CURRENT_CONFIG_VERSION = 4

        fun createConfig(
            spatialMode: SpatialMode = SpatialMode.MODE_8D,
            profile: AudioProfile = AudioProfile.BALANCED
        ): AudioDspConfig {
            val isEnabled = spatialMode != SpatialMode.OFF
            val is16D = spatialMode == SpatialMode.MODE_16D

            return when (profile) {
                AudioProfile.BALANCED -> AudioDspConfig(
                    configVersion = CURRENT_CONFIG_VERSION,
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = if (is16D) 7.0f else 9.5f,
                    panDepth = if (is16D) 0.92f else 0.88f,
                    crossoverSplitHz = 210,
                    subBassCentering = isEnabled,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = false,
                    roomSize = 0.40f,
                    reverbLevel = if (isEnabled) 0.08f else 0.0f,
                    binauralHrtf = isEnabled,
                    distanceFrontBackCue = isEnabled,
                    crossfeed = false,
                    crossfeedAmount = 0.0f,
                    bassBoostEnabled = isEnabled,
                    bassBoostFreq = BassBoostFrequency.FREQ_80HZ,
                    bassBoostGainDb = if (isEnabled) 2.8f else 0.0f,
                    cleanBassProtection = true,
                    compressorEnabled = true,
                    volumeNormalization = true,
                    limiterEnabled = true,
                    trebleClarityDb = if (isEnabled) 1.2f else 0.0f,
                    trebleFreqHz = 8000,
                    vocalPresenceDb = if (isEnabled) 1.0f else 0.0f,
                    microDetailLevel = if (isEnabled) 0.45f else 0.25f,
                    preGainDb = if (isEnabled) 1.2f else 0.0f,
                    stereoWidth = if (is16D) 1.24f else if (isEnabled) 1.16f else 1.0f,
                    activePresetName = profile.label
                )

                AudioProfile.IMMERSIVE_STAGE -> AudioDspConfig(
                    configVersion = CURRENT_CONFIG_VERSION,
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = if (is16D) 7.5f else 8.5f,
                    panDepth = if (is16D) 0.96f else 0.94f,
                    crossoverSplitHz = 190,
                    subBassCentering = isEnabled,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = true,
                    roomSize = 0.62f,
                    reverbLevel = if (isEnabled) 0.20f else 0.12f,
                    binauralHrtf = isEnabled,
                    distanceFrontBackCue = isEnabled,
                    crossfeed = !isEnabled,
                    crossfeedAmount = if (isEnabled) 0.0f else 0.18f,
                    bassBoostEnabled = true,
                    bassBoostFreq = BassBoostFrequency.FREQ_60HZ,
                    bassBoostGainDb = 4.2f,
                    cleanBassProtection = true,
                    compressorEnabled = true,
                    volumeNormalization = true,
                    limiterEnabled = true,
                    trebleClarityDb = 2.2f,
                    trebleFreqHz = 8500,
                    vocalPresenceDb = 1.4f,
                    microDetailLevel = 0.72f,
                    preGainDb = -0.3f,
                    stereoWidth = if (is16D) 1.44f else 1.38f,
                    activePresetName = profile.label
                )

                AudioProfile.SPATIAL_360 -> AudioDspConfig(
                    configVersion = CURRENT_CONFIG_VERSION,
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = if (is16D) 8.0f else 10.5f,
                    panDepth = if (is16D) 0.68f else 0.58f,
                    crossoverSplitHz = 210,
                    subBassCentering = isEnabled,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = false,
                    roomSize = 0.44f,
                    reverbLevel = if (isEnabled) 0.14f else 0.07f,
                    binauralHrtf = isEnabled,
                    distanceFrontBackCue = isEnabled,
                    crossfeed = !isEnabled,
                    crossfeedAmount = if (isEnabled) 0.0f else 0.22f,
                    bassBoostEnabled = true,
                    bassBoostFreq = BassBoostFrequency.FREQ_80HZ,
                    bassBoostGainDb = 3.4f,
                    cleanBassProtection = true,
                    compressorEnabled = true,
                    volumeNormalization = true,
                    limiterEnabled = true,
                    trebleClarityDb = 1.5f,
                    trebleFreqHz = 8000,
                    vocalPresenceDb = 0.8f,
                    microDetailLevel = 0.55f,
                    preGainDb = -1.2f,
                    stereoWidth = if (is16D) 1.42f else 1.36f,
                    activePresetName = profile.label
                )

                AudioProfile.ABSOLUTE_HEARING -> AudioDspConfig(
                    configVersion = CURRENT_CONFIG_VERSION,
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = if (is16D) 8.5f else 11.5f,
                    panDepth = if (is16D) 0.84f else 0.78f,
                    crossoverSplitHz = 180,
                    subBassCentering = isEnabled,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = false,
                    roomSize = 0.22f,
                    reverbLevel = if (isEnabled) 0.03f else 0.0f,
                    binauralHrtf = isEnabled,
                    distanceFrontBackCue = isEnabled,
                    crossfeed = !isEnabled,
                    crossfeedAmount = if (isEnabled) 0.0f else 0.20f,
                    bassBoostEnabled = true,
                    bassBoostFreq = BassBoostFrequency.FREQ_60HZ,
                    bassBoostGainDb = 1.2f,
                    cleanBassProtection = true,
                    compressorEnabled = true,
                    volumeNormalization = true,
                    limiterEnabled = true,
                    trebleClarityDb = 2.8f,
                    trebleFreqHz = 9000,
                    vocalPresenceDb = 3.2f,
                    microDetailLevel = 0.95f,
                    preGainDb = -0.5f,
                    stereoWidth = 1.14f,
                    activePresetName = profile.label
                )

                AudioProfile.NIGHT_CONCERT -> AudioDspConfig(
                    configVersion = CURRENT_CONFIG_VERSION,
                    spatialMode = spatialMode,
                    activeProfile = profile,
                    is8dEnabled = isEnabled,
                    rotationSpeedSeconds = if (is16D) 10.0f else 13.5f,
                    panDepth = if (is16D) 0.88f else 0.84f,
                    crossoverSplitHz = 210,
                    subBassCentering = isEnabled,
                    rotationPattern = RotationPattern.SINE,
                    rotationDirection = RotationDirection.CLOCKWISE,
                    useDualTapEcho = true,
                    roomSize = 0.72f,
                    reverbLevel = if (isEnabled) 0.26f else 0.14f,
                    binauralHrtf = isEnabled,
                    distanceFrontBackCue = isEnabled,
                    crossfeed = !isEnabled,
                    crossfeedAmount = if (isEnabled) 0.0f else 0.28f,
                    bassBoostEnabled = true,
                    bassBoostFreq = BassBoostFrequency.FREQ_60HZ,
                    bassBoostGainDb = 5.0f,
                    cleanBassProtection = true,
                    compressorEnabled = true,
                    volumeNormalization = true,
                    limiterEnabled = true,
                    trebleClarityDb = 0.5f,
                    trebleFreqHz = 10000,
                    vocalPresenceDb = 0.4f,
                    microDetailLevel = 0.65f,
                    preGainDb = -1.2f,
                    stereoWidth = 1.24f,
                    activePresetName = profile.label
                )
            }
        }

        fun fromJson(json: String): AudioDspConfig? {
            return try {
                val sanitizedJson = json
                    .replace("\"CONCERT_HALL\"", "\"NIGHT_CONCERT\"")
                    .replace("\"NIGHTCORE\"", "\"BALANCED\"")
                    .replace("\"SLOWED_REVERB\"", "\"BALANCED\"")
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
                    fixed = fixed.copy(bassBoostFreq = BassBoostFrequency.FREQ_80HZ)
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.rotationPattern == null) {
                    fixed = fixed.copy(rotationPattern = RotationPattern.SINE)
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.rotationDirection == null) {
                    fixed = fixed.copy(rotationDirection = RotationDirection.CLOCKWISE)
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.eqBands == null || fixed.eqBands.size != 10) {
                    fixed = fixed.copy(eqBands = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.eqPreset == null) {
                    fixed = fixed.copy(eqPreset = "Flat")
                }
                @Suppress("SENSELESS_COMPARISON")
                if (fixed.activePresetName == null) {
                    fixed = fixed.copy(activePresetName = fixed.activeProfile.label)
                }
                if (fixed.configVersion < CURRENT_CONFIG_VERSION) {
                    val defaults = createConfig(fixed.spatialMode, fixed.activeProfile)
                    fixed = fixed.copy(
                        configVersion = CURRENT_CONFIG_VERSION,
                        autoHeadroomEnabled = true,
                        limiterEnabled = true,
                        rotationSpeedSeconds = defaults.rotationSpeedSeconds,
                        panDepth = defaults.panDepth,
                        reverbLevel = defaults.reverbLevel,
                        roomSize = defaults.roomSize,
                        stereoWidth = defaults.stereoWidth,
                        crossfeed = defaults.crossfeed,
                        crossfeedAmount = defaults.crossfeedAmount,
                        distanceFrontBackCue = defaults.distanceFrontBackCue,
                        binauralHrtf = defaults.binauralHrtf,
                        useDualTapEcho = defaults.useDualTapEcho,
                        bassBoostEnabled = defaults.bassBoostEnabled,
                        bassBoostFreq = defaults.bassBoostFreq,
                        bassBoostGainDb = defaults.bassBoostGainDb,
                        trebleClarityDb = defaults.trebleClarityDb,
                        vocalPresenceDb = defaults.vocalPresenceDb,
                        microDetailLevel = defaults.microDetailLevel,
                        preGainDb = defaults.preGainDb
                    )
                }
                fixed
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class EqCategory(val label: String) {
    REFERENCE("Reference"),
    IMMERSION("Immersion"),
    HEARING("Hearing"),
    GENRE("Genre"),
    ATMOSPHERIC("Effects"),
    CUSTOM("Custom")
}

data class EqPresetSpec(
    val name: String,
    val category: EqCategory,
    val tagline: String,
    val bands: FloatArray,
    val isAtmosphericFx: Boolean = false
)

/**
 * 10-Band Precision Equalizer Presets, Proportional-Q Curve Engine & Auto-Headroom Analyzer.
 * Center frequencies: 31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz.
 */
object EqPresets {
    val FREQUENCIES = doubleArrayOf(31.0, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    val FREQUENCY_LABELS = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    const val BAND_Q = 1.41

    val SPECS: List<EqPresetSpec> = listOf(
        EqPresetSpec(
            name = "Flat",
            category = EqCategory.REFERENCE,
            tagline = "Bit-transparent neutral studio response",
            bands = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        ),
        EqPresetSpec(
            name = "Koi Immersion",
            category = EqCategory.IMMERSION,
            tagline = "Deep sub weight, clean center vocals & silky air",
            bands = floatArrayOf(3.5f, 4.0f, 2.0f, -1.0f, -0.5f, 0.5f, 2.0f, 2.5f, 3.0f, 4.0f)
        ),
        EqPresetSpec(
            name = "Harman Reference",
            category = EqCategory.REFERENCE,
            tagline = "Scientifically tuned natural monitor curve",
            bands = floatArrayOf(2.5f, 3.0f, 1.5f, 0.0f, -0.5f, 0.0f, 1.5f, 2.5f, 1.5f, 1.0f)
        ),
        EqPresetSpec(
            name = "Absolute Hearing",
            category = EqCategory.HEARING,
            tagline = "Crystal breath, lyric & micro-nuance articulation",
            bands = floatArrayOf(-1.5f, -0.5f, 0.0f, -2.0f, 0.5f, 2.0f, 4.0f, 4.5f, 3.0f, 2.5f)
        ),
        EqPresetSpec(
            name = "Late Night Comfort",
            category = EqCategory.HEARING,
            tagline = "Low-volume warmth without upper-mid harshness",
            bands = floatArrayOf(3.0f, 3.5f, 2.0f, 0.5f, 0.0f, 0.0f, -1.0f, -1.5f, 1.5f, 2.5f)
        ),
        EqPresetSpec(
            name = "Bass",
            category = EqCategory.IMMERSION,
            tagline = "Authoritative sub-60Hz punch with clean mids",
            bands = floatArrayOf(5.5f, 6.5f, 4.0f, 1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
        ),
        EqPresetSpec(
            name = "Vocal",
            category = EqCategory.HEARING,
            tagline = "Intimate forward voice & acoustic body",
            bands = floatArrayOf(-2.0f, -1.0f, 0.0f, 0.5f, 2.5f, 3.5f, 4.5f, 3.0f, 1.5f, 1.0f)
        ),
        EqPresetSpec(
            name = "Treble",
            category = EqCategory.HEARING,
            tagline = "Crystalline sparkle & open studio air",
            bands = floatArrayOf(-1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.5f, 2.5f, 4.0f, 5.5f, 6.0f)
        ),
        EqPresetSpec(
            name = "Electronic",
            category = EqCategory.GENRE,
            tagline = "Tight club sub-drop & razor synth shimmer",
            bands = floatArrayOf(5.0f, 5.5f, 2.5f, -1.5f, -1.0f, 0.5f, 2.0f, 3.5f, 4.5f, 5.0f)
        ),
        EqPresetSpec(
            name = "Pop",
            category = EqCategory.GENRE,
            tagline = "Modern glossy low-end & vocal presence",
            bands = floatArrayOf(2.0f, 3.5f, 1.5f, -0.5f, 0.0f, 1.5f, 3.0f, 2.5f, 2.0f, 2.5f)
        ),
        EqPresetSpec(
            name = "Rock",
            category = EqCategory.GENRE,
            tagline = "Snappy kick & snare punch, crisp guitar edge",
            bands = floatArrayOf(4.0f, 4.5f, 2.0f, -1.5f, -1.5f, 0.5f, 2.5f, 3.5f, 4.5f, 4.0f)
        ),
        EqPresetSpec(
            name = "Jazz",
            category = EqCategory.GENRE,
            tagline = "Warm upright bass & natural brass timbre",
            bands = floatArrayOf(2.5f, 2.5f, 1.5f, 0.5f, 0.0f, 1.0f, 2.0f, 1.5f, 2.5f, 3.0f)
        ),
        EqPresetSpec(
            name = "Classic",
            category = EqCategory.GENRE,
            tagline = "Wide orchestral stage & delicate string sheen",
            bands = floatArrayOf(1.0f, 1.5f, 1.0f, 0.5f, 0.0f, 0.5f, 1.5f, 2.5f, 3.0f, 4.0f)
        ),
        EqPresetSpec(
            name = "Nightcore",
            category = EqCategory.ATMOSPHERIC,
            tagline = "1.25x tempo • +3.5st pitch • Energetic brilliance",
            bands = floatArrayOf(-1.0f, 0.5f, 1.0f, 0.0f, 1.0f, 2.0f, 3.5f, 4.5f, 5.5f, 5.0f),
            isAtmosphericFx = true
        ),
        EqPresetSpec(
            name = "Slowed + Reverbed",
            category = EqCategory.ATMOSPHERIC,
            tagline = "0.85x tempo • -2.5st pitch • Lush hall depth",
            bands = floatArrayOf(5.0f, 4.5f, 2.5f, 0.0f, -0.5f, 0.0f, 0.5f, 0.0f, 1.0f, 2.0f),
            isAtmosphericFx = true
        )
    )

    val SPEC_MAP: Map<String, EqPresetSpec> = SPECS.associateBy { it.name }
    val PRESETS: Map<String, FloatArray> = SPECS.associate { it.name to it.bands }

    /**
     * Proportional-Q curve: subtle adjustments use a smoother musical Q (~1.18) while deep surgical
     * cuts/boosts tighten Q (~1.62). The top 16 kHz band stays slightly broader (~1.05) to resist
     * Nyquist cramping at 44.1 / 48 kHz and preserve silky studio air.
     */
    fun bandQForGain(bandIndex: Int, gainDb: Double): Double {
        val proportional = (1.18 + 0.04 * abs(gainDb)).coerceIn(1.18, 1.65)
        return if (bandIndex == 9) min(proportional, 1.05) else proportional
    }

    /**
     * Computes the exact composite EQ magnitude response in dB at [freqHz] across all active
     * 10-band peaking filters (and optionally tone shelf / presence filters when [includeToneStage] is true).
     */
    fun computeFrequencyResponseDb(
        config: AudioDspConfig,
        freqHz: Double,
        sampleRate: Double = 48000.0,
        includeToneStage: Boolean = true
    ): Double {
        val fs = sampleRate.coerceAtLeast(8000.0)
        val w = 2.0 * Math.PI * freqHz.coerceIn(10.0, fs * 0.48) / fs
        var totalDb = 0.0

        if (config.eqEnabled) {
            for (b in 0 until 10) {
                val gainDb = config.eqBands.getOrElse(b) { 0f }.toDouble()
                if (abs(gainDb) < 0.05) continue
                val centerFreq = FREQUENCIES[b].coerceIn(10.0, (fs * 0.45).coerceAtLeast(1000.0))
                val q = bandQForGain(b, gainDb)
                totalDb += peakingBiquadMagnitudeDb(w, centerFreq, gainDb, q, fs)
            }
        }

        if (includeToneStage) {
            if (config.bassBoostEnabled && abs(config.bassBoostGainDb) >= 0.05f) {
                val bassGain = if (config.safeMode) config.bassBoostGainDb.coerceIn(0f, 6f).toDouble() else config.bassBoostGainDb.toDouble()
                @Suppress("SENSELESS_COMPARISON")
                val bassFreq = (if (config.bassBoostFreq != null) config.bassBoostFreq.freqHz else 80).toDouble()
                totalDb += lowShelfMagnitudeDb(w, bassFreq, bassGain, fs)
            }
            if (abs(config.trebleClarityDb) >= 0.05f) {
                val trebleGain = if (config.safeMode) config.trebleClarityDb.coerceIn(0f, 6f).toDouble() else config.trebleClarityDb.toDouble()
                totalDb += highShelfMagnitudeDb(w, config.trebleFreqHz.toDouble().coerceIn(2000.0, fs * 0.45), trebleGain, fs)
            }
            if (abs(config.vocalPresenceDb) >= 0.05f) {
                totalDb += peakingBiquadMagnitudeDb(w, 2200.0, config.vocalPresenceDb.toDouble(), 0.80, fs)
            }
            val defGain = computeDefinitionGainDb(config)
            if (abs(defGain) >= 0.05) {
                totalDb += peakingBiquadMagnitudeDb(w, 3600.0, defGain, 0.95, fs)
            }
        }

        return totalDb
    }

    fun computeDefinitionGainDb(config: AudioDspConfig): Double {
        return (config.vocalPresenceDb * 0.55 + (config.microDetailLevel - 0.25).coerceAtLeast(0.0) * 1.4)
            .coerceIn(-2.0, 3.0)
    }

    /**
     * Generates [pointCount] log-spaced dB points from 24 Hz to 18.5 kHz for plotting the exact
     * live EQ frequency response curve in the UI.
     */
    fun computeFrequencyResponseCurve(
        config: AudioDspConfig,
        pointCount: Int = 64,
        sampleRate: Double = 48000.0,
        includeToneStage: Boolean = false
    ): FloatArray {
        val count = pointCount.coerceAtLeast(16)
        val out = FloatArray(count)
        val logMin = ln(24.0)
        val logMax = ln(18500.0)
        for (i in 0 until count) {
            val frac = i.toDouble() / (count - 1).toDouble()
            val freq = exp(logMin + (logMax - logMin) * frac)
            out[i] = computeFrequencyResponseDb(config, freq, sampleRate, includeToneStage).toFloat()
        }
        return out
    }

    /**
     * Computes peak composite positive EQ boost across the 10-band EQ curve (accounting for
     * adjacent-band overlap). Returns 0f when EQ is bypassed or cut-only.
     */
    fun computeEqPeakBoostDb(
        config: AudioDspConfig,
        sampleRate: Double = 48000.0
    ): Float {
        if (!config.eqEnabled) return 0f
        var maxDb = 0.0
        val logMin = ln(24.0)
        val logMax = ln(18500.0)
        for (i in 0 until 40) {
            val frac = i.toDouble() / 39.0
            val freq = exp(logMin + (logMax - logMin) * frac)
            val db = computeFrequencyResponseDb(config, freq, sampleRate, includeToneStage = false)
            if (db > maxDb) maxDb = db
        }
        for (f in FREQUENCIES) {
            val db = computeFrequencyResponseDb(config, f, sampleRate, includeToneStage = false)
            if (db > maxDb) maxDb = db
        }
        return maxDb.coerceIn(0.0, 18.0).toFloat()
    }

    /**
     * Transparent Auto-Headroom guard: prevents EQ boosts from slamming into the limiter
     * and pumping vocals/mids, while preserving punchy perceived loudness.
     */
    fun computeAutoHeadroomDb(
        config: AudioDspConfig,
        sampleRate: Double = 48000.0
    ): Float {
        if (!config.autoHeadroomEnabled || !config.eqEnabled) return 0f
        val peakBoost = computeEqPeakBoostDb(config, sampleRate)
        if (peakBoost <= 1.5f) return 0f
        return -((peakBoost - 1.5f) * 0.85f).coerceIn(0f, 12.0f)
    }

    private fun biquadMagnitudeDb(
        w: Double,
        b0: Double,
        b1: Double,
        b2: Double,
        a1: Double,
        a2: Double
    ): Double {
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2.0 * w)
        val sin2W = sin(2.0 * w)

        val numRe = b0 + b1 * cosW + b2 * cos2W
        val numIm = -b1 * sinW - b2 * sin2W
        val denRe = 1.0 + a1 * cosW + a2 * cos2W
        val denIm = -a1 * sinW - a2 * sin2W

        val numSq = numRe * numRe + numIm * numIm
        val denSq = (denRe * denRe + denIm * denIm).coerceAtLeast(1e-18)
        val mag = sqrt(numSq / denSq).coerceAtLeast(1e-9)
        return 20.0 * log10(mag)
    }

    private fun peakingBiquadMagnitudeDb(
        wEval: Double,
        freq: Double,
        gainDb: Double,
        q: Double,
        sampleRate: Double
    ): Double {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val a0 = 1.0 + alpha / a
        val b0 = (1.0 + alpha * a) / a0
        val b1 = (-2.0 * cosW0) / a0
        val b2 = (1.0 - alpha * a) / a0
        val a1 = (-2.0 * cosW0) / a0
        val a2 = (1.0 - alpha / a) / a0
        return biquadMagnitudeDb(wEval, b0, b1, b2, a1, a2)
    }

    private fun lowShelfMagnitudeDb(
        wEval: Double,
        freq: Double,
        gainDb: Double,
        sampleRate: Double
    ): Double {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        val b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0) / a0
        val b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0) / a0
        val a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
        return biquadMagnitudeDb(wEval, b0, b1, b2, a1, a2)
    }

    private fun highShelfMagnitudeDb(
        wEval: Double,
        freq: Double,
        gainDb: Double,
        sampleRate: Double
    ): Double {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        val b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0
        val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0) / a0
        val b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
        val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0) / a0
        val a2 = ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
        return biquadMagnitudeDb(wEval, b0, b1, b2, a1, a2)
    }
}
