package com.koi.backend.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Audiophile Spatial Audio, 10-Band Precision EQ & Immersive Hearing DSP Processor.
 *
 * Signal flow:
 * Input -> Smooth Pre-Amp + Auto-Headroom -> Soundstage Width (M/S) ->
 * Bass Shelf -> Treble Air Shelf -> Vocal Presence (2.2 kHz) -> Sound Definition (3.6 kHz) ->
 * 10-Band Proportional-Q Graphic EQ -> Upward Micro-Detail Extractor ->
 * 4th-Order Linkwitz-Riley Crossover (Sub-Bass Mono Anchor) ->
 * Orbital 8D/16D ITD Spatializer or 7.1.4 Surround Dome ->
 * Binaural Crossfeed -> Early Studio Reflections -> Stereo Schroeder-Moorer Reverb ->
 * True-Peak Lookahead Limiter + Soft-Knee Safety Saturation -> Live 2048-pt FFT Spectrum Analyzer.
 */
@OptIn(UnstableApi::class)
class EightDAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var config: AudioDspConfig = AudioDspConfig.createConfig(SpatialMode.OFF, AudioProfile.BALANCED)
        set(value) {
            field = value
            if (!isProcessingThreadActive) {
                updateFilters(value)
                appliedConfig = value
            }
        }

    @Volatile
    private var appliedConfig: AudioDspConfig? = null

    @Volatile
    private var isProcessingThreadActive = false

    // Realtime live emissions for UI Visualizers
    private val _rotationAngleDegrees = MutableStateFlow(0f)
    val rotationAngleDegrees: StateFlow<Float> = _rotationAngleDegrees.asStateFlow()

    private val _spectrumBands = MutableStateFlow(FloatArray(10) { 0.05f })
    val spectrumBands: StateFlow<FloatArray> = _spectrumBands.asStateFlow()

    // Internal DSP state
    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var rotationPhase: Double = 0.0

    // Smooth parameter ramping (eliminates zipper noise & clicks on live slider moves)
    private var paramSmoothCoeff: Float = 0.004f
    private var currentPreGainFactor: Float = 1.0f
    private var targetPreGainFactor: Float = 1.0f
    private var currentStereoWidth: Float = 1.0f
    private var targetStereoWidth: Float = 1.0f

    // Active-filter skip flags
    private var hasActiveEqBands: Boolean = false
    private var hasActiveBassBoost: Boolean = false
    private var hasActiveTrebleClarity: Boolean = false
    private var hasActiveVocalFilter: Boolean = false
    private var hasActiveDefFilter: Boolean = false

    // ITD Delay line (Interaural Time Difference buffer)
    private val itdBufferL = FloatArray(256)
    private val itdBufferR = FloatArray(256)
    private var itdWriteIndex = 0

    // Binaural Head-Shadow (ILD) and Pinna Front-Back Acoustic Filters
    private var headShadowStateL = 0f
    private var headShadowStateR = 0f
    private var pinnaBackStateL = 0f
    private var pinnaBackStateR = 0f

    // Crossfeed filter states (Bauer/Chu Moy acoustic blend)
    private var xfeedStateL = 0f
    private var xfeedStateR = 0f

    // 4th-Order Linkwitz-Riley Crossover Filters (2 cascaded 2nd-order Butterworth LP + HP per channel)
    private val crossoverLp1L = BiquadFilter()
    private val crossoverLp2L = BiquadFilter()
    private val crossoverLp1R = BiquadFilter()
    private val crossoverLp2R = BiquadFilter()
    private val crossoverHp1L = BiquadFilter()
    private val crossoverHp2L = BiquadFilter()
    private val crossoverHp1R = BiquadFilter()
    private val crossoverHp2R = BiquadFilter()

    // Tone & Presence Biquad Filters
    private val bassFilterL = BiquadFilter()
    private val bassFilterR = BiquadFilter()
    private val trebleFilterL = BiquadFilter()
    private val trebleFilterR = BiquadFilter()
    private val vocalFilterL = BiquadFilter()
    private val vocalFilterR = BiquadFilter()
    private val definitionFilterL = BiquadFilter()
    private val definitionFilterR = BiquadFilter()

    // 10-Band Proportional-Q Graphic Equalizer Filter Chain
    private val eqFiltersL = Array(10) { BiquadFilter() }
    private val eqFiltersR = Array(10) { BiquadFilter() }

    // Reverb Low-Cut Filter (High-pass to keep sub-bass punch clean)
    private val reverbHpFilter = BiquadFilter()

    // Feed-Forward FIR Studio Early Reflections (zero feedback = zero tearing/flutter)
    private val arenaReflBufL = FloatArray(8192)
    private val arenaReflBufR = FloatArray(8192)
    private var arenaReflIdx = 0
    private var arenaReflDampL = 0f
    private var arenaReflDampR = 0f

    // Stationary Surround 360° Speaker System
    private val surroundRearBufL = FloatArray(2048)
    private val surroundRearBufR = FloatArray(2048)
    private var surroundRearIdx = 0
    private var surroundPinnaL = 0f
    private var surroundPinnaR = 0f

    // True Stereo Decorrelated Schroeder-Moorer Reverberator
    private val combDelaysL = intArrayOf(1116, 1188, 1277, 1356)
    private val combDelaysR = intArrayOf(1139, 1211, 1301, 1379)
    private val combBuffersL = Array(4) { FloatArray(2048) }
    private val combBuffersR = Array(4) { FloatArray(2048) }
    private val combIndicesL = IntArray(4)
    private val combIndicesR = IntArray(4)
    private val combDampL = FloatArray(4)
    private val combDampR = FloatArray(4)

    private val allPassDelaysL = intArrayOf(225, 556)
    private val allPassDelaysR = intArrayOf(241, 577)
    private val allPassBuffersL = Array(2) { FloatArray(1024) }
    private val allPassBuffersR = Array(2) { FloatArray(1024) }
    private val allPassIndicesL = IntArray(2)
    private val allPassIndicesR = IntArray(2)

    // True-Peak Smooth-Release Limiter state
    private var peakEnvelope = 0f
    private var limiterGain = 1.0f
    private var releaseDecayCoeff = 0.9996f

    // Micro-dynamics detail extraction state
    private var detailEnvelope = 0f
    private var detailGain = 1.0f
    private var detailAttackCoeff = 0.998f
    private var detailReleaseCoeff = 0.9997f
    private var microAirFilterL = 0f
    private var microAirFilterR = 0f

    // Real-time 2048-point Radix-2 Cooley-Tukey FFT 10-Band Spectrum Analyzer
    private val fftSize = 2048
    private val fftMask = fftSize - 1
    private val fftRingBuffer = FloatArray(fftSize)
    private var fftWriteIdx = 0
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / (fftSize - 1)))).toFloat()
    }
    private val bitReverseTable = IntArray(fftSize)
    private val fftCosTable = FloatArray(fftSize / 2)
    private val fftSinTable = FloatArray(fftSize / 2)
    private val smoothedSpectrum = FloatArray(10) { 0.05f }

    private var sampleCounter = 0
    private var inputEncoding: Int = C.ENCODING_PCM_16BIT

    init {
        val bits = 11 // log2(2048)
        for (i in 0 until fftSize) {
            var x = i
            var rev = 0
            for (b in 0 until bits) {
                rev = (rev shl 1) or (x and 1)
                x = x ushr 1
            }
            bitReverseTable[i] = rev
        }
        for (k in 0 until fftSize / 2) {
            val angle = -2.0 * Math.PI * k / fftSize
            fftCosTable[k] = cos(angle).toFloat()
            fftSinTable[k] = sin(angle).toFloat()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inputEncoding = inputAudioFormat.encoding
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channelCount = inputAudioFormat.channelCount

        // 60ms release curve for limiter
        val releaseSamples = sampleRate * 0.060
        releaseDecayCoeff = exp(-1.0 / releaseSamples).toFloat()

        // 4ms attack, 70ms release for upward micro-dynamics detail extraction
        val detailAttackSamples = sampleRate * 0.004
        val detailReleaseSamples = sampleRate * 0.070
        detailAttackCoeff = exp(-1.0 / detailAttackSamples).toFloat()
        detailReleaseCoeff = exp(-1.0 / detailReleaseSamples).toFloat()

        // ~8ms parameter smoothing to prevent clicks during live slider drags
        paramSmoothCoeff = (1.0 - exp(-1.0 / (sampleRate * 0.008))).toFloat().coerceIn(0.0005f, 0.05f)

        val cfg = config
        updateFilters(cfg)
        appliedConfig = cfg
        currentPreGainFactor = targetPreGainFactor
        currentStereoWidth = targetStereoWidth
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remainingBytes = inputBuffer.remaining()
        if (remainingBytes == 0) return

        isProcessingThreadActive = true

        val bytesPerSample = if (inputEncoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val bytesPerFrame = channelCount * bytesPerSample
        val frameCount = remainingBytes / bytesPerFrame
        if (frameCount == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val outputBuffer = replaceOutputBuffer(frameCount * 4)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val cfg = config
        if (cfg !== appliedConfig) {
            updateFilters(cfg)
            appliedConfig = cfg
        }

        // Fast-path bit-perfect bypass
        if (!cfg.masterEnabled || cfg.abBypass) {
            if (channelCount == 2 && inputEncoding == C.ENCODING_PCM_16BIT) {
                val bytesToCopy = frameCount * 4
                val originalLimit = inputBuffer.limit()
                inputBuffer.limit(inputBuffer.position() + bytesToCopy)
                outputBuffer.put(inputBuffer)
                inputBuffer.limit(originalLimit)
                inputBuffer.position(originalLimit)
            } else {
                for (i in 0 until frameCount) {
                    val s: Short = if (inputEncoding == C.ENCODING_PCM_FLOAT) {
                        (inputBuffer.float.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                    } else {
                        inputBuffer.short
                    }
                    if (channelCount > 1) {
                        for (c in 1 until channelCount) {
                            if (inputEncoding == C.ENCODING_PCM_FLOAT) inputBuffer.float else inputBuffer.short
                        }
                    }
                    outputBuffer.putShort(s)
                    outputBuffer.putShort(s)
                }
                inputBuffer.position(inputBuffer.limit())
            }
            outputBuffer.flip()
            return
        }

        val rotationSpeedSec = cfg.rotationSpeedSeconds.coerceAtLeast(1.0f)
        val phaseDelta = (2.0 * Math.PI) / (sampleRate * rotationSpeedSec) *
                (if (cfg.rotationDirection == RotationDirection.CLOCKWISE) 1.0 else -1.0)

        val maxItdSamples = (sampleRate * 0.00075).toFloat().coerceIn(1f, 36f)
        val bufSize = itdBufferL.size
        val decay = releaseDecayCoeff
        val smooth = paramSmoothCoeff

        val useEarlyRefl = cfg.useDualTapEcho
        val reflTap1 = (sampleRate * 0.018f).toInt().coerceIn(1, arenaReflBufL.size - 1)
        val reflTap2 = (sampleRate * 0.029f).toInt().coerceIn(1, arenaReflBufR.size - 1)
        val reflTap3 = (sampleRate * 0.043f).toInt().coerceIn(1, arenaReflBufL.size - 1)
        val surroundRearDelay = (sampleRate * 0.015f).toInt().coerceIn(1, surroundRearBufL.size - 1)

        val useEq = cfg.eqEnabled && hasActiveEqBands
        val useBass = hasActiveBassBoost
        val useTreble = hasActiveTrebleClarity
        val useVocal = hasActiveVocalFilter
        val useDef = hasActiveDefFilter
        val microScale = cfg.microDetailLevel.coerceIn(0f, 1f)
        val useMicroDetail = microScale > 0.01f && (cfg.compressorEnabled || cfg.volumeNormalization || microScale > 0.05f)
        val isSurround360Profile = cfg.activeProfile == AudioProfile.SPATIAL_360

        for (i in 0 until frameCount) {
            var inL = if (inputEncoding == C.ENCODING_PCM_FLOAT) {
                inputBuffer.float.coerceIn(-1.0f, 1.0f)
            } else {
                inputBuffer.short / 32768.0f
            }
            var inR = if (channelCount >= 2) {
                if (inputEncoding == C.ENCODING_PCM_FLOAT) {
                    inputBuffer.float.coerceIn(-1.0f, 1.0f)
                } else {
                    inputBuffer.short / 32768.0f
                }
            } else {
                inL
            }
            if (channelCount > 2) {
                for (c in 2 until channelCount) {
                    if (inputEncoding == C.ENCODING_PCM_FLOAT) inputBuffer.float else inputBuffer.short
                }
            }

            // Smooth Pre-Amp & Auto-Headroom gain staging
            currentPreGainFactor += (targetPreGainFactor - currentPreGainFactor) * smooth
            inL *= currentPreGainFactor
            inR *= currentPreGainFactor

            // Soundstage Width Expansion (Mid/Side)
            currentStereoWidth += (targetStereoWidth - currentStereoWidth) * smooth
            if (abs(currentStereoWidth - 1.0f) > 0.005f) {
                val mid = (inL + inR) * 0.5f
                val side = (inL - inR) * 0.5f * currentStereoWidth
                inL = mid + side
                inR = mid - side
            }

            // Tone & Presence Filters (only executed when active)
            if (useBass) {
                inL = bassFilterL.process(inL)
                inR = bassFilterR.process(inR)
            }
            if (useTreble) {
                inL = trebleFilterL.process(inL)
                inR = trebleFilterR.process(inR)
            }
            if (useVocal) {
                inL = vocalFilterL.process(inL)
                inR = vocalFilterR.process(inR)
            }
            if (useDef) {
                inL = definitionFilterL.process(inL)
                inR = definitionFilterR.process(inR)
            }

            // 10-Band Proportional-Q Graphic Equalizer Chain
            if (useEq) {
                for (b in 0 until 10) {
                    inL = eqFiltersL[b].process(inL)
                    inR = eqFiltersR[b].process(inR)
                }
            }

            // Tunable Upward Micro-Dynamics Detail Extractor
            if (useMicroDetail) {
                val instPeak = max(abs(inL), abs(inR))
                if (instPeak > detailEnvelope) {
                    detailEnvelope = detailEnvelope * detailAttackCoeff + instPeak * (1f - detailAttackCoeff)
                } else {
                    detailEnvelope = detailEnvelope * detailReleaseCoeff + instPeak * (1f - detailReleaseCoeff)
                }

                val safeEnv = max(detailEnvelope, 1e-4f)
                val envDb = 20.0f * log10(safeEnv)
                val baseBoostDb = when {
                    envDb >= -12.0f -> 0.0f
                    envDb >= -28.0f -> ((-12.0f - envDb) / 16.0f) * 4.5f
                    envDb >= -55.0f -> 4.5f + ((-28.0f - envDb) / 27.0f) * 2.0f
                    envDb >= -72.0f -> 3.0f + ((envDb - (-72.0f)) / 17.0f) * 3.5f
                    envDb >= -80.0f -> max(0.0f, ((envDb - (-80.0f)) / 8.0f) * 3.0f)
                    else -> 0.0f
                }
                val scaledBoostDb = baseBoostDb * microScale
                val targetBoostGain = 10.0.pow(scaledBoostDb / 20.0).toFloat()
                detailGain += (targetBoostGain - detailGain) * (1f - detailAttackCoeff)

                microAirFilterL += 0.35f * (inL - microAirFilterL)
                microAirFilterR += 0.35f * (inR - microAirFilterR)
                val airL = inL - microAirFilterL
                val airR = inR - microAirFilterR
                val airLift = ((detailGain - 1.0f) * 0.16f * microScale).coerceIn(0f, 0.22f)

                inL = inL * detailGain + airL * airLift
                inR = inR * detailGain + airR * airLift
            }

            // 1. 4th-Order Linkwitz-Riley Crossover (keeps sub-bass anchored dead-center during 3D orbit)
            val centerBass = cfg.is8dEnabled && cfg.subBassCentering
            val lowL: Float
            val lowR: Float
            val hiL: Float
            val hiR: Float

            if (centerBass) {
                lowL = crossoverLp2L.process(crossoverLp1L.process(inL))
                lowR = crossoverLp2R.process(crossoverLp1R.process(inR))
                hiL = crossoverHp2L.process(crossoverHp1L.process(inL))
                hiR = crossoverHp2R.process(crossoverHp1R.process(inR))
            } else {
                lowL = 0f
                lowR = 0f
                hiL = inL
                hiR = inR
            }

            val centeredLow = (lowL + lowR) * 0.5f
            val loOutL = if (centerBass) centeredLow else lowL
            val loOutR = if (centerBass) centeredLow else lowR

            // 2. Spatial Engine (Orbital 8D/16D or 7.1 Surround 360° Dome)
            var hipL = hiL
            var hipR = hiR

            if (isSurround360Profile) {
                val frontBias: Float
                val rearBias: Float
                val panL: Float
                val panR: Float

                if (cfg.is8dEnabled) {
                    rotationPhase = (rotationPhase + phaseDelta) % (2.0 * Math.PI)
                    if (rotationPhase < 0) rotationPhase += 2.0 * Math.PI

                    val xPos = sin(rotationPhase).toFloat()
                    val yPos = cos(rotationPhase).toFloat()

                    frontBias = 1.0f + yPos * 0.18f
                    rearBias = 1.0f - yPos * 0.18f

                    val panDepth = cfg.panDepth.coerceIn(0.35f, 0.75f)
                    panL = (1.0f - panDepth) + panDepth * 0.5f * (1.0f - xPos)
                    panR = (1.0f - panDepth) + panDepth * 0.5f * (1.0f + xPos)
                } else {
                    // Stationary 7.1.4 Surround Dome in Stereo mode
                    frontBias = 1.0f
                    rearBias = 1.0f
                    panL = 1.0f
                    panR = 1.0f
                }

                val center = (hiL + hiR) * 0.5f
                val frontL = hiL
                val frontR = hiR

                val side = (hiL - hiR) * 0.5f
                val sideL = side * 1.35f
                val sideR = -side * 1.35f

                val inRearL = hiL - hiR * 0.38f
                val inRearR = hiR - hiL * 0.38f
                surroundRearBufL[surroundRearIdx] = inRearL
                surroundRearBufR[surroundRearIdx] = inRearR

                val readIdx = (surroundRearIdx - surroundRearDelay + 2048) % 2048
                val rawRearL = surroundRearBufL[readIdx]
                val rawRearR = surroundRearBufR[readIdx]
                surroundRearIdx = (surroundRearIdx + 1) % 2048

                surroundPinnaL += 0.28f * (rawRearL - surroundPinnaL)
                surroundPinnaR += 0.28f * (rawRearR - surroundPinnaR)
                val rearSpkL = rawRearL * 0.55f + surroundPinnaL * 0.45f
                val rearSpkR = rawRearR * 0.55f + surroundPinnaR * 0.45f

                val sumL = frontL * (0.65f * frontBias) + center * (0.25f * frontBias) + sideL * 0.40f + (rearSpkL * 0.35f + rearSpkR * 0.12f) * rearBias
                val sumR = frontR * (0.65f * frontBias) + center * (0.25f * frontBias) + sideR * 0.40f + (rearSpkR * 0.35f + rearSpkL * 0.12f) * rearBias

                hipL = sumL * panL
                hipR = sumR * panR
            } else if (cfg.is8dEnabled) {
                rotationPhase = (rotationPhase + phaseDelta) % (2.0 * Math.PI)
                if (rotationPhase < 0) rotationPhase += 2.0 * Math.PI

                val angle = rotationPhase
                val is16D = cfg.spatialMode == SpatialMode.MODE_16D
                val xPos: Float
                val yPos: Float
                var zElev = 0f

                if (is16D) {
                    // Dual-axis 16D figure-8 + vertical elevation ring
                    xPos = sin(angle).toFloat()
                    yPos = sin(2.0 * angle).toFloat()
                    zElev = cos(angle).toFloat()
                } else {
                    when (cfg.rotationPattern) {
                        RotationPattern.SINE -> {
                            xPos = sin(angle).toFloat()
                            yPos = cos(angle).toFloat()
                        }
                        RotationPattern.TRIANGLE -> {
                            xPos = (2.0 / Math.PI * asin(sin(angle))).toFloat()
                            yPos = (2.0 / Math.PI * asin(sin(angle + Math.PI / 2.0))).toFloat()
                        }
                        RotationPattern.SQUARE -> {
                            xPos = if (sin(angle) >= 0) 1.0f else -1.0f
                            yPos = if (cos(angle) >= 0) 1.0f else -1.0f
                        }
                    }
                }

                val minOpposite = 0.08f
                val panRange = 1.0f - minOpposite
                val panSweep = cfg.panDepth.coerceIn(0.25f, 1.0f) * panRange
                val gainL = (1.0f - panSweep) + panSweep * 0.5f * (1.0f - xPos)
                val gainR = (1.0f - panSweep) + panSweep * 0.5f * (1.0f + xPos)

                // Front-Back Pinna Depth Cue (palpable sensation of moving behind the head vs in front)
                if (cfg.distanceFrontBackCue || is16D) {
                    pinnaBackStateL += 0.40f * (hipL - pinnaBackStateL)
                    pinnaBackStateR += 0.40f * (hipR - pinnaBackStateR)
                    val backness = (-yPos).coerceIn(0f, 1f)
                    val frontness = yPos.coerceIn(0f, 1f)
                    val depthGain = (1.0f - backness * 0.16f) + frontness * 0.05f
                    val pinnaBlend = backness * 0.24f
                    hipL = (hipL * (1f - pinnaBlend) + pinnaBackStateL * pinnaBlend) * depthGain
                    hipR = (hipR * (1f - pinnaBlend) + pinnaBackStateR * pinnaBlend) * depthGain
                }

                // 16D Overhead / Proximity Swell & Dynamic Stage Breathing
                if (is16D) {
                    val mid16 = (hipL + hipR) * 0.5f
                    val side16 = (hipL - hipR) * 0.5f * (1.0f + 0.16f * zElev)
                    val prox = 1.0f + 0.10f * abs(xPos) * (0.5f + 0.5f * zElev)
                    hipL = (mid16 + side16) * prox
                    hipR = (mid16 - side16) * prox
                }

                // Head-Shadow ILD Filter on the shadowed/far ear (near ear stays 100% razor-sharp)
                headShadowStateL += 0.22f * (hipL - headShadowStateL)
                headShadowStateR += 0.22f * (hipR - headShadowStateR)
                val shadowStrength = (abs(xPos) * panSweep * 0.40f).coerceIn(0f, 0.40f)
                if (xPos > 0f) {
                    hipL = hipL * (1f - shadowStrength) + headShadowStateL * shadowStrength
                } else if (xPos < 0f) {
                    hipR = hipR * (1f - shadowStrength) + headShadowStateR * shadowStrength
                }

                // Fractional-Sample Interpolated Interaural Time Difference (ITD)
                // Linear interpolation eliminates integer-sample stepping grains during smooth rotation
                if (cfg.binauralHrtf) {
                    itdBufferL[itdWriteIndex] = hipL
                    itdBufferR[itdWriteIndex] = hipR

                    val delayFloat = (abs(xPos) * panSweep * maxItdSamples).coerceIn(0f, maxItdSamples)
                    val d0 = delayFloat.toInt()
                    val frac = delayFloat - d0
                    val d1 = (d0 + 1).coerceAtMost(bufSize - 1)
                    if (xPos > 0f) {
                        val idx0 = (itdWriteIndex - d0 + bufSize) % bufSize
                        val idx1 = (itdWriteIndex - d1 + bufSize) % bufSize
                        hipL = itdBufferL[idx0] * (1f - frac) + itdBufferL[idx1] * frac
                    } else if (xPos < 0f) {
                        val idx0 = (itdWriteIndex - d0 + bufSize) % bufSize
                        val idx1 = (itdWriteIndex - d1 + bufSize) % bufSize
                        hipR = itdBufferR[idx0] * (1f - frac) + itdBufferR[idx1] * frac
                    }
                    itdWriteIndex = (itdWriteIndex + 1) % bufSize
                }

                hipL *= gainL
                hipR *= gainR
            }

            // 3. Recombine crossover bands
            var procL = loOutL + hipL
            var procR = loOutR + hipR

            // Binaural Crossfeed (only in Stereo mode so 8D/16D ear separation stays crisp)
            if (!cfg.is8dEnabled && cfg.crossfeed && cfg.crossfeedAmount > 0.02f) {
                val alpha = 0.22f
                xfeedStateL += alpha * (procL - xfeedStateL)
                xfeedStateR += alpha * (procR - xfeedStateR)
                val blend = cfg.crossfeedAmount.coerceIn(0f, 1f) * 0.26f
                val blendedL = procL * (1f - blend) + xfeedStateR * blend
                val blendedR = procR * (1f - blend) + xfeedStateL * blend
                procL = blendedL
                procR = blendedR
            }

            // 4. Studio Early Reflections (subtle out-of-head room spatial air)
            if (useEarlyRefl) {
                val bufLen = arenaReflBufL.size
                arenaReflBufL[arenaReflIdx] = procL
                arenaReflBufR[arenaReflIdx] = procR

                val r1L = arenaReflBufL[(arenaReflIdx - reflTap1 + bufLen) % bufLen]
                val r1R = arenaReflBufR[(arenaReflIdx - reflTap2 + bufLen) % bufLen]
                val r2CrossL = arenaReflBufR[(arenaReflIdx - reflTap3 + bufLen) % bufLen]
                val r2CrossR = arenaReflBufL[(arenaReflIdx - reflTap3 + bufLen) % bufLen]

                arenaReflIdx = (arenaReflIdx + 1) % bufLen

                arenaReflDampL += 0.28f * ((r1L * 0.09f + r2CrossL * 0.05f) - arenaReflDampL)
                arenaReflDampR += 0.28f * ((r1R * 0.09f + r2CrossR * 0.05f) - arenaReflDampR)

                procL += arenaReflDampL
                procR += arenaReflDampR
            }

            // Schroeder-Moorer Decorrelated Reverb Tail
            if (cfg.reverbLevel > 0.02f) {
                val monoSpatial = (procL + procR) * 0.5f
                val reverbIn = reverbHpFilter.process(monoSpatial)
                val wet = processReverb(reverbIn, cfg.roomSize)
                val wetGain = cfg.reverbLevel.coerceIn(0f, 1f) * 0.45f
                procL += wet.first * wetGain
                procR += wet.second * wetGain
            }

            // 5. True-Peak Limiter
            if (cfg.limiterEnabled || cfg.cleanBassProtection) {
                val peak = max(abs(procL), abs(procR))
                if (peak > peakEnvelope) {
                    peakEnvelope = peak
                } else {
                    peakEnvelope = peak + (peakEnvelope - peak) * decay
                }

                val threshold = 0.95f
                val targetGain = if (peakEnvelope > threshold) threshold / peakEnvelope else 1.0f

                if (targetGain < limiterGain) {
                    limiterGain = targetGain
                } else {
                    limiterGain += (targetGain - limiterGain) * (1.0f - decay)
                }

                procL *= limiterGain
                procR *= limiterGain
            }

            // NaN/Inf safeguard
            if (procL.isNaN() || procL.isInfinite()) {
                procL = 0f
                peakEnvelope = 0f
                limiterGain = 1.0f
            }
            if (procR.isNaN() || procR.isInfinite()) {
                procR = 0f
                peakEnvelope = 0f
                limiterGain = 1.0f
            }
            if (limiterGain.isNaN() || limiterGain < 0.01f) {
                limiterGain = 1.0f
            }
            if (peakEnvelope.isNaN()) {
                peakEnvelope = 0f
            }

            // Soft-knee analog saturation ceiling above 0.96f (prevents digital hard-clipping)
            procL = softCeiling(procL).coerceIn(-1.0f, 1.0f)
            procR = softCeiling(procR).coerceIn(-1.0f, 1.0f)

            // Feed FFT ring buffer with final processed mono tap
            fftRingBuffer[fftWriteIdx] = (procL + procR) * 0.5f
            fftWriteIdx = (fftWriteIdx + 1) and fftMask

            val outShortL = (procL * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            val outShortR = (procR * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            outputBuffer.putShort(outShortL)
            outputBuffer.putShort(outShortR)

            sampleCounter++
            if (sampleCounter >= 2048) {
                sampleCounter = 0
                _rotationAngleDegrees.value = (Math.toDegrees(rotationPhase).toFloat() % 360f)
                emitFftSpectrumSnapshot()
            }
        }
        inputBuffer.position(inputBuffer.limit())
        outputBuffer.flip()
    }

    private fun softCeiling(x: Float): Float {
        val ax = abs(x)
        if (ax <= 0.96f) return x
        val excess = (ax - 0.96f) / 0.04f
        return sign(x) * (0.96f + 0.04f * tanh(excess))
    }

    private fun processReverb(inSample: Float, roomSize: Float): Pair<Float, Float> {
        val safeIn = if (inSample.isNaN() || inSample.isInfinite()) 0f else inSample.coerceIn(-1f, 1f)
        val feedback = (0.58f + roomSize * 0.24f).coerceIn(0.50f, 0.84f)
        val damp = 0.28f

        var combOutL = 0f
        var combOutR = 0f
        for (c in 0 until 4) {
            val bufSizeL = combDelaysL[c]
            val idxL = combIndicesL[c]
            var outL = combBuffersL[c][idxL]
            if (outL.isNaN() || outL.isInfinite()) outL = 0f
            combDampL[c] = outL * (1f - damp) + combDampL[c] * damp
            combBuffersL[c][idxL] = (safeIn + combDampL[c] * feedback).coerceIn(-2f, 2f)
            combIndicesL[c] = (idxL + 1) % bufSizeL
            combOutL += outL

            val bufSizeR = combDelaysR[c]
            val idxR = combIndicesR[c]
            var outR = combBuffersR[c][idxR]
            if (outR.isNaN() || outR.isInfinite()) outR = 0f
            combDampR[c] = outR * (1f - damp) + combDampR[c] * damp
            combBuffersR[c][idxR] = (safeIn + combDampR[c] * feedback).coerceIn(-2f, 2f)
            combIndicesR[c] = (idxR + 1) % bufSizeR
            combOutR += outR
        }
        combOutL *= 0.25f
        combOutR *= 0.25f

        var apOutL = combOutL
        var apOutR = combOutR
        for (a in 0 until 2) {
            val bufSizeL = allPassDelaysL[a]
            val idxL = allPassIndicesL[a]
            var bufL = allPassBuffersL[a][idxL]
            if (bufL.isNaN() || bufL.isInfinite()) bufL = 0f
            allPassBuffersL[a][idxL] = (apOutL + bufL * 0.5f).coerceIn(-2f, 2f)
            apOutL = -apOutL * 0.5f + bufL
            allPassIndicesL[a] = (idxL + 1) % bufSizeL

            val bufSizeR = allPassDelaysR[a]
            val idxR = allPassIndicesR[a]
            var bufR = allPassBuffersR[a][idxR]
            if (bufR.isNaN() || bufR.isInfinite()) bufR = 0f
            allPassBuffersR[a][idxR] = (apOutR + bufR * 0.5f).coerceIn(-2f, 2f)
            apOutR = -apOutR * 0.5f + bufR
            allPassIndicesR[a] = (idxR + 1) % bufSizeR
        }

        return Pair(
            if (apOutL.isNaN() || apOutL.isInfinite()) 0f else apOutL,
            if (apOutR.isNaN() || apOutR.isInfinite()) 0f else apOutR
        )
    }

    /**
     * Computes a real 2048-point Radix-2 Cooley-Tukey FFT on the Hann-windowed ring buffer and
     * aggregates exact logarithmic frequency energy across the 10 graphic EQ bands.
     */
    private fun emitFftSpectrumSnapshot() {
        val n = fftSize
        val start = fftWriteIdx
        for (i in 0 until n) {
            val sample = fftRingBuffer[(start + i) and fftMask]
            val revIdx = bitReverseTable[i]
            fftReal[revIdx] = sample * hannWindow[i]
            fftImag[revIdx] = 0f
        }

        // In-place iterative Cooley-Tukey FFT
        var halfSize = 1
        while (halfSize < n) {
            val step = halfSize shl 1
            val tableStep = n / step
            var i = 0
            while (i < n) {
                var k = 0
                for (j in 0 until halfSize) {
                    val twiddleIdx = k * tableStep
                    val wr = fftCosTable[twiddleIdx]
                    val wi = fftSinTable[twiddleIdx]

                    val evenIdx = i + j
                    val oddIdx = evenIdx + halfSize

                    val oddRe = fftReal[oddIdx]
                    val oddIm = fftImag[oddIdx]

                    val tRe = wr * oddRe - wi * oddIm
                    val tIm = wr * oddIm + wi * oddRe

                    val evenRe = fftReal[evenIdx]
                    val evenIm = fftImag[evenIdx]

                    fftReal[oddIdx] = evenRe - tRe
                    fftImag[oddIdx] = evenIm - tIm
                    fftReal[evenIdx] = evenRe + tRe
                    fftImag[evenIdx] = evenIm + tIm
                    k++
                }
                i += step
            }
            halfSize = step
        }

        val fs = sampleRate.toDouble()
        val normScale = 2.0f / (n * 0.5f) // Compensate FFT N/2 and Hann coherent gain 0.5
        val sqrt2 = sqrt(2.0)

        val snapshot = FloatArray(10)
        for (b in 0 until 10) {
            val fc = EqPresets.FREQUENCIES[b]
            val fLo = if (b == 0) 18.0 else fc / sqrt2
            val fHi = if (b == 9) min(fs * 0.48, 20000.0) else fc * sqrt2

            val kStart = floor(fLo * n / fs).toInt().coerceIn(1, (n / 2) - 2)
            val kEnd = ceil(fHi * n / fs).toInt().coerceIn(kStart, (n / 2) - 1)

            var maxMag = 0f
            for (bin in kStart..kEnd) {
                val re = fftReal[bin]
                val im = fftImag[bin]
                val mag = sqrt(re * re + im * im) * normScale
                if (mag > maxMag) maxMag = mag
            }

            val db = 20.0f * log10(maxMag.coerceAtLeast(1e-5f))
            // Map [-60 dBFS, -3 dBFS] -> [0.05f, 1.0f]
            val normalized = ((db + 60.0f) / 57.0f).coerceIn(0.05f, 1.0f)
            val prev = smoothedSpectrum[b]
            val coeff = if (normalized > prev) 0.58f else 0.28f
            val smoothed = (prev + (normalized - prev) * coeff).coerceIn(0.05f, 1.0f)
            smoothedSpectrum[b] = smoothed
            snapshot[b] = smoothed
        }
        _spectrumBands.value = snapshot
    }

    private fun updateFilters(cfg: AudioDspConfig) {
        val fs = sampleRate.toDouble()

        val autoHeadroomDb = EqPresets.computeAutoHeadroomDb(cfg, fs)
        val totalPreGainDb = (cfg.preGainDb + cfg.eqPreampDb + autoHeadroomDb).coerceIn(-24.0f, 12.0f)
        targetPreGainFactor = if (abs(totalPreGainDb) > 0.01f) {
            10.0.pow(totalPreGainDb / 20.0).toFloat()
        } else {
            1.0f
        }
        targetStereoWidth = cfg.stereoWidth.coerceIn(0.4f, 1.8f)

        // 4th-Order Linkwitz-Riley Crossover
        val splitFreq = cfg.crossoverSplitHz.toDouble().coerceIn(80.0, 500.0)
        crossoverLp1L.setLowPass(freq = splitFreq, sampleRate = fs)
        crossoverLp2L.setLowPass(freq = splitFreq, sampleRate = fs)
        crossoverLp1R.setLowPass(freq = splitFreq, sampleRate = fs)
        crossoverLp2R.setLowPass(freq = splitFreq, sampleRate = fs)

        crossoverHp1L.setHighPass(freq = splitFreq, sampleRate = fs)
        crossoverHp2L.setHighPass(freq = splitFreq, sampleRate = fs)
        crossoverHp1R.setHighPass(freq = splitFreq, sampleRate = fs)
        crossoverHp2R.setHighPass(freq = splitFreq, sampleRate = fs)

        // Bass Boost (Low Shelf)
        @Suppress("SENSELESS_COMPARISON")
        val bassFreq = (if (cfg.bassBoostFreq != null) cfg.bassBoostFreq.freqHz else 80).toDouble()
        val bassGain = if (cfg.bassBoostEnabled) {
            (if (cfg.safeMode) cfg.bassBoostGainDb.coerceIn(0f, 6f) else cfg.bassBoostGainDb).toDouble()
        } else 0.0
        hasActiveBassBoost = abs(bassGain) >= 0.05
        bassFilterL.setLowShelf(bassFreq, gainDb = bassGain, sampleRate = fs)
        bassFilterR.setLowShelf(bassFreq, gainDb = bassGain, sampleRate = fs)

        // Treble Clarity (High Shelf)
        val trebleFreq = cfg.trebleFreqHz.toDouble().coerceIn(2000.0, (fs * 0.45).coerceAtLeast(3000.0))
        val trebleGain = (if (cfg.safeMode) cfg.trebleClarityDb.coerceIn(0f, 6f) else cfg.trebleClarityDb).toDouble()
        hasActiveTrebleClarity = abs(trebleGain) >= 0.05
        trebleFilterL.setHighShelf(freq = trebleFreq, gainDb = trebleGain, sampleRate = fs)
        trebleFilterR.setHighShelf(freq = trebleFreq, gainDb = trebleGain, sampleRate = fs)

        // Vocal Presence Peaking Filter (2200 Hz, Q=0.80)
        val vocGain = cfg.vocalPresenceDb.toDouble().coerceIn(-6.0, 8.0)
        hasActiveVocalFilter = abs(vocGain) >= 0.05
        vocalFilterL.setPeakingEQ(freq = 2200.0, gainDb = vocGain, sampleRate = fs, Q = 0.80)
        vocalFilterR.setPeakingEQ(freq = 2200.0, gainDb = vocGain, sampleRate = fs, Q = 0.80)

        // Sound Definition Peaking Filter (3600 Hz, Q=0.95)
        val defGain = EqPresets.computeDefinitionGainDb(cfg)
        hasActiveDefFilter = abs(defGain) >= 0.05
        definitionFilterL.setPeakingEQ(freq = 3600.0, gainDb = defGain, sampleRate = fs, Q = 0.95)
        definitionFilterR.setPeakingEQ(freq = 3600.0, gainDb = defGain, sampleRate = fs, Q = 0.95)

        // 10-Band Proportional-Q Graphic Equalizer Filter Chain
        val eqBands = cfg.eqBands
        var anyEqActive = false
        for (b in 0 until 10) {
            val gainDb = if (cfg.eqEnabled) (eqBands.getOrElse(b) { 0f }).toDouble().coerceIn(-12.0, 12.0) else 0.0
            if (abs(gainDb) >= 0.05) anyEqActive = true
            val safeFreq = EqPresets.FREQUENCIES[b].coerceIn(10.0, (fs * 0.45).coerceAtLeast(1000.0))
            val q = EqPresets.bandQForGain(b, gainDb)
            eqFiltersL[b].setPeakingEQ(freq = safeFreq, gainDb = gainDb, sampleRate = fs, Q = q)
            eqFiltersR[b].setPeakingEQ(freq = safeFreq, gainDb = gainDb, sampleRate = fs, Q = q)
        }
        hasActiveEqBands = anyEqActive

        // Reverb Low-Cut Filter
        reverbHpFilter.setHighPass(freq = cfg.crossoverSplitHz.toDouble().coerceAtLeast(220.0), sampleRate = fs)
    }

    override fun onFlush() {
        rotationPhase = 0.0
        itdBufferL.fill(0f)
        itdBufferR.fill(0f)
        itdWriteIndex = 0
        headShadowStateL = 0f
        headShadowStateR = 0f
        pinnaBackStateL = 0f
        pinnaBackStateR = 0f
        xfeedStateL = 0f
        xfeedStateR = 0f
        peakEnvelope = 0f
        limiterGain = 1.0f
        detailEnvelope = 0f
        detailGain = 1.0f
        arenaReflBufL.fill(0f)
        arenaReflBufR.fill(0f)
        arenaReflIdx = 0
        arenaReflDampL = 0f
        arenaReflDampR = 0f
        surroundRearBufL.fill(0f)
        surroundRearBufR.fill(0f)
        surroundRearIdx = 0
        surroundPinnaL = 0f
        surroundPinnaR = 0f
        microAirFilterL = 0f
        microAirFilterR = 0f
        fftRingBuffer.fill(0f)
        fftWriteIdx = 0
        sampleCounter = 0
        smoothedSpectrum.fill(0.05f)
        currentPreGainFactor = targetPreGainFactor
        currentStereoWidth = targetStereoWidth
        crossoverLp1L.reset()
        crossoverLp2L.reset()
        crossoverLp1R.reset()
        crossoverLp2R.reset()
        crossoverHp1L.reset()
        crossoverHp2L.reset()
        crossoverHp1R.reset()
        crossoverHp2R.reset()
        bassFilterL.reset()
        bassFilterR.reset()
        trebleFilterL.reset()
        trebleFilterR.reset()
        vocalFilterL.reset()
        vocalFilterR.reset()
        definitionFilterL.reset()
        definitionFilterR.reset()
        for (b in 0 until 10) {
            eqFiltersL[b].reset()
            eqFiltersR[b].reset()
        }
        reverbHpFilter.reset()
        for (c in 0 until 4) {
            combBuffersL[c].fill(0f)
            combBuffersR[c].fill(0f)
            combIndicesL[c] = 0
            combIndicesR[c] = 0
            combDampL[c] = 0f
            combDampR[c] = 0f
        }
        for (a in 0 until 2) {
            allPassBuffersL[a].fill(0f)
            allPassBuffersR[a].fill(0f)
            allPassIndicesL[a] = 0
            allPassIndicesR[a] = 0
        }
    }

    override fun onReset() {
        onFlush()
    }
}

/**
 * 2nd-Order Direct Form I IIR Biquad Filter (64-bit Double internal state for sub-bass precision).
 * Implementation based on Robert Bristow-Johnson's Audio EQ Cookbook.
 */
class BiquadFilter {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    fun process(x: Float): Float {
        val xIn = x.toDouble()
        val y = b0 * xIn + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        if (y.isNaN() || y.isInfinite()) {
            reset()
            return 0f
        }
        x2 = x1
        x1 = xIn
        y2 = if (abs(y1) < 1e-15) 0.0 else y1
        y1 = if (abs(y) < 1e-15) 0.0 else y
        return y.toFloat()
    }

    fun setLowPass(freq: Double, sampleRate: Double, Q: Double = 0.7071) {
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * Q)

        val b0Raw = (1.0 - cosW0) / 2.0
        val b1Raw = 1.0 - cosW0
        val b2Raw = (1.0 - cosW0) / 2.0
        val a0Raw = 1.0 + alpha
        val a1Raw = -2.0 * cosW0
        val a2Raw = 1.0 - alpha

        b0 = b0Raw / a0Raw
        b1 = b1Raw / a0Raw
        b2 = b2Raw / a0Raw
        a1 = a1Raw / a0Raw
        a2 = a2Raw / a0Raw
    }

    fun setHighPass(freq: Double, sampleRate: Double, Q: Double = 0.7071) {
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * Q)

        val b0Raw = (1.0 + cosW0) / 2.0
        val b1Raw = -(1.0 + cosW0)
        val b2Raw = (1.0 + cosW0) / 2.0
        val a0Raw = 1.0 + alpha
        val a1Raw = -2.0 * cosW0
        val a2Raw = 1.0 - alpha

        b0 = b0Raw / a0Raw
        b1 = b1Raw / a0Raw
        b2 = b2Raw / a0Raw
        a1 = a1Raw / a0Raw
        a2 = a2Raw / a0Raw
    }

    fun setLowShelf(freq: Double, gainDb: Double, sampleRate: Double) {
        if (abs(gainDb) < 0.05) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0
            a1 = 0.0; a2 = 0.0
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)

        val b0Raw = a * ((a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrt(a) * alpha)
        val b1Raw = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val b2Raw = a * ((a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrt(a) * alpha)
        val a0Raw = (a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrt(a) * alpha
        val a1Raw = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val a2Raw = (a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrt(a) * alpha

        b0 = b0Raw / a0Raw
        b1 = b1Raw / a0Raw
        b2 = b2Raw / a0Raw
        a1 = a1Raw / a0Raw
        a2 = a2Raw / a0Raw
    }

    fun setHighShelf(freq: Double, gainDb: Double, sampleRate: Double) {
        if (abs(gainDb) < 0.05) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0
            a1 = 0.0; a2 = 0.0
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)

        val b0Raw = a * ((a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrt(a) * alpha)
        val b1Raw = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val b2Raw = a * ((a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrt(a) * alpha)
        val a0Raw = (a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrt(a) * alpha
        val a1Raw = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val a2Raw = (a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrt(a) * alpha

        b0 = b0Raw / a0Raw
        b1 = b1Raw / a0Raw
        b2 = b2Raw / a0Raw
        a1 = a1Raw / a0Raw
        a2 = a2Raw / a0Raw
    }

    fun setPeakingEQ(freq: Double, gainDb: Double, sampleRate: Double, Q: Double = 0.85) {
        if (abs(gainDb) < 0.05) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0
            a1 = 0.0; a2 = 0.0
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * Q)

        val b0Raw = 1.0 + alpha * a
        val b1Raw = -2.0 * cosW0
        val b2Raw = 1.0 - alpha * a
        val a0Raw = 1.0 + alpha / a
        val a1Raw = -2.0 * cosW0
        val a2Raw = 1.0 - alpha / a

        b0 = b0Raw / a0Raw
        b1 = b1Raw / a0Raw
        b2 = b2Raw / a0Raw
        a1 = a1Raw / a0Raw
        a2 = a2Raw / a0Raw
    }
}
