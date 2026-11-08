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
 * Audiophile 8D Spatial Audio & DSP Processor.
 *
 * Implements the Starboy-style MPV / FFmpeg lavfi audio filter chain:
 * lavfi=[acrossover=split=250:order=4th[lo][hi];[hi]apulsator=mode=sine:hz=0.17:amount=0.4:offset_l=0:offset_r=0.5[hip];[lo][hip]amix=inputs=2:weights=1 1:normalize=0,aecho=0.8:0.9:40|70:0.25|0.15,alimiter=limit=0.95]
 */
@OptIn(UnstableApi::class)
class EightDAudioProcessor : BaseAudioProcessor() {

    // Current dynamic configuration
    var config: AudioDspConfig = AudioDspConfig.createConfig(SpatialMode.OFF, AudioProfile.BALANCED)
        set(value) {
            field = value
            updateFilters()
        }

    // Realtime live emissions for UI Visualizers
    private val _rotationAngleDegrees = MutableStateFlow(0f)
    val rotationAngleDegrees: StateFlow<Float> = _rotationAngleDegrees.asStateFlow()

    private val _spectrumBands = MutableStateFlow(FloatArray(10) { 0f })
    val spectrumBands: StateFlow<FloatArray> = _spectrumBands.asStateFlow()

    // Internal DSP state
    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var rotationPhase: Double = 0.0

    // ITD Delay line (Interaural Time Difference buffer)
    private val itdBufferL = FloatArray(256)
    private val itdBufferR = FloatArray(256)
    private var itdWriteIndex = 0

    // Continuous Front-back pinna acoustic filter states (1-pole low pass)
    private var pinnaStateL = 0f
    private var pinnaStateR = 0f

    // Crossfeed filter states (Bauer/Chu Moy acoustic blend)
    private var xfeedStateL = 0f
    private var xfeedStateR = 0f

    // 4th-Order Linkwitz-Riley Crossover Filters (2 cascaded 2nd-order Butterworth LP + HP per channel)
    // Ensures mathematically flat magnitude sum (0 dB) with zero phase cancellation on rock kick/snare punch
    private val crossoverLp1L = BiquadFilter()
    private val crossoverLp2L = BiquadFilter()
    private val crossoverLp1R = BiquadFilter()
    private val crossoverLp2R = BiquadFilter()
    private val crossoverHp1L = BiquadFilter()
    private val crossoverHp2L = BiquadFilter()
    private val crossoverHp1R = BiquadFilter()
    private val crossoverHp2R = BiquadFilter()

    // Bass Boost Biquad Filter (Low Shelf)
    private val bassFilterL = BiquadFilter()
    private val bassFilterR = BiquadFilter()

    // Treble Clarity Biquad Filter (High Shelf at 8000 Hz)
    private val trebleFilterL = BiquadFilter()
    private val trebleFilterR = BiquadFilter()

    // Vocal Presence Biquad Filter (2200 Hz Vocal Lift & Body)
    private val vocalFilterL = BiquadFilter()
    private val vocalFilterR = BiquadFilter()

    // Sound Definition Peaking Filter (3600 Hz Upper-Mid Articulation & Transient Edge)
    private val definitionFilterL = BiquadFilter()
    private val definitionFilterR = BiquadFilter()

    // Reverb Low-Cut Filter (High-pass at 220 Hz to avoid bass mud)
    private val reverbHpFilter = BiquadFilter()

    // Feed-Forward FIR Arena Early Reflections (zero feedback = zero tearing/flutter)
    private val arenaReflBufL = FloatArray(8192)
    private val arenaReflBufR = FloatArray(8192)
    private var arenaReflIdx = 0
    private var arenaReflDampL = 0f
    private var arenaReflDampR = 0f

    // Stationary Surround 360° Speaker System (Surround sound from all sides simultaneously)
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

    // True-Peak Lookahead / Smooth-Release Limiter state
    private var peakEnvelope = 0f
    private var limiterGain = 1.0f
    private var releaseDecayCoeff = 0.9996f

    // Micro-dynamics detail extraction state (elevates subtle whispers, finger slides, ghost notes)
    private var detailEnvelope = 0f
    private var detailGain = 1.0f
    private var detailAttackCoeff = 0.998f
    private var detailReleaseCoeff = 0.9997f
    private var microAirFilterL = 0f
    private var microAirFilterR = 0f

    // Spectrum visualizer energy bins
    private val bandEnergies = FloatArray(10)
    private var sampleCounter = 0
    private var inputEncoding: Int = C.ENCODING_PCM_16BIT

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inputEncoding = inputAudioFormat.encoding
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channelCount = inputAudioFormat.channelCount

        // 60ms release curve for limiter: decay = exp(-1.0 / (sampleRate * 0.060))
        val releaseSamples = sampleRate * 0.060
        releaseDecayCoeff = exp(-1.0 / releaseSamples).toFloat()

        // 4ms attack, 70ms release for upward micro-dynamics detail extraction
        val detailAttackSamples = sampleRate * 0.004
        val detailReleaseSamples = sampleRate * 0.070
        detailAttackCoeff = exp(-1.0 / detailAttackSamples).toFloat()
        detailReleaseCoeff = exp(-1.0 / detailReleaseSamples).toFloat()

        updateFilters()
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remainingBytes = inputBuffer.remaining()
        if (remainingBytes == 0) return

        val bytesPerSample = if (inputEncoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val bytesPerFrame = channelCount * bytesPerSample
        val frameCount = remainingBytes / bytesPerFrame
        if (frameCount == 0) {
            // Must always consume inputBuffer completely so AudioProcessingPipeline / AudioSink never deadlocks
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val outputBuffer = replaceOutputBuffer(frameCount * 4) // Always 2 channels * 2 bytes = 4 bytes per frame
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val cfg = config

        // Fast-path bypass
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

        // Active DSP Processing - Starboy-style MPV chain:
        val preGainFactor = if (cfg.preGainDb != 0.0f) 10.0.pow(cfg.preGainDb / 20.0).toFloat() else 1.0f

        val rotationSpeedSec = cfg.rotationSpeedSeconds.coerceAtLeast(1.0f)
        val phaseDelta = (2.0 * Math.PI) / (sampleRate * rotationSpeedSec) *
                (if (cfg.rotationDirection == RotationDirection.CLOCKWISE) 1.0 else -1.0)

        val maxItdSamples = (sampleRate * 0.00075).toFloat().coerceIn(1f, 36f)
        val bufSize = itdBufferL.size
        val decay = releaseDecayCoeff

        val useArenaRefl = cfg.useDualTapEcho
        val arenaTap1 = (sampleRate * 0.038f).toInt().coerceIn(1, arenaReflBufL.size - 1)
        val arenaTap2 = (sampleRate * 0.054f).toInt().coerceIn(1, arenaReflBufR.size - 1)
        val arenaTap3 = (sampleRate * 0.076f).toInt().coerceIn(1, arenaReflBufL.size - 1)
        val surroundRearDelay = (sampleRate * 0.015f).toInt().coerceIn(1, surroundRearBufL.size - 1)

        for (i in 0 until frameCount) {
            // Read normalized left & right samples in range [-1.0, 1.0]
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

            // Headroom scaling
            inL *= preGainFactor
            inR *= preGainFactor

            // Stereo Width Expansion (Input Soundstage Separation)
            // Separates distinct instruments across the stereo soundfield before spatialization
            // so each instrument occupies its own acoustic pocket in space
            if (cfg.stereoWidth > 0.05f && abs(cfg.stereoWidth - 1.0f) > 0.01f) {
                val mid = (inL + inR) * 0.5f
                val side = (inL - inR) * 0.5f * cfg.stereoWidth
                inL = mid + side
                inR = mid - side
            }

            // Optional Tone Shaping (if engaged)
            if (cfg.bassBoostEnabled && cfg.bassBoostGainDb > 0.05f) {
                inL = bassFilterL.process(inL)
                inR = bassFilterR.process(inR)
            }
            if (cfg.trebleClarityDb > 0.05f) {
                inL = trebleFilterL.process(inL)
                inR = trebleFilterR.process(inR)
            }
            // Vocal Presence (2.2 kHz Vocal Lift)
            inL = vocalFilterL.process(inL)
            inR = vocalFilterR.process(inR)

            // Upper-Mid Sound Definition (3.6 kHz Transient & Instrument Edge)
            inL = definitionFilterL.process(inL)
            inR = definitionFilterR.process(inR)

            // Upward Micro-Dynamics Detail Extractor:
            // Elevates even the slightest minute sounds (whispers, finger slides, breaths, cymbal tails, ghost notes)
            // with crystal-clear high-frequency air definition without squashing loud drum/kick attacks
            if (cfg.compressorEnabled || cfg.volumeNormalization) {
                val instPeak = max(abs(inL), abs(inR))
                if (instPeak > detailEnvelope) {
                    detailEnvelope = detailEnvelope * detailAttackCoeff + instPeak * (1f - detailAttackCoeff)
                } else {
                    detailEnvelope = detailEnvelope * detailReleaseCoeff + instPeak * (1f - detailReleaseCoeff)
                }

                val safeEnv = max(detailEnvelope, 1e-4f)
                val envDb = 20.0f * log10(safeEnv)
                val targetBoostDb = when {
                    envDb >= -12.0f -> 0.0f
                    envDb >= -28.0f -> ((-12.0f - envDb) / 16.0f) * 4.5f
                    envDb >= -55.0f -> 4.5f + ((-28.0f - envDb) / 27.0f) * 2.0f
                    envDb >= -72.0f -> 3.0f + ((envDb - (-72.0f)) / 17.0f) * 3.5f
                    envDb >= -80.0f -> max(0.0f, ((envDb - (-80.0f)) / 8.0f) * 3.0f)
                    else -> 0.0f
                }
                val targetBoostGain = 10.0.pow(targetBoostDb / 20.0).toFloat()
                detailGain += (targetBoostGain - detailGain) * (1f - detailAttackCoeff)

                // High-frequency air extraction for pristine clarity on quiet nuances
                microAirFilterL += 0.35f * (inL - microAirFilterL)
                microAirFilterR += 0.35f * (inR - microAirFilterR)
                val airL = inL - microAirFilterL
                val airR = inR - microAirFilterR
                val airLift = ((detailGain - 1.0f) * 0.15f).coerceIn(0f, 0.20f)

                inL = inL * detailGain + airL * airLift
                inR = inR * detailGain + airR * airLift
            }

            // Accumulate spectrum energy for live visualizer
            accumulateBandEnergy(inL, inR)

            // 1. 4th-Order Linkwitz-Riley Crossover (220 Hz split):
            // Keeps punchy kick drums & sub-bass anchored dead-center in mono during 3D rotation
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

            // Bass handling: below 220 Hz kept centered (not panned)
            // Centering low frequencies locks rock kicks & bass riffs solidly in mono center
            val centeredLow = (lowL + lowR) * 0.5f
            val loOutL = if (centerBass) centeredLow else lowL
            val loOutR = if (centerBass) centeredLow else lowR

            // 2. Spatial Engine:
            var hipL = hiL
            var hipR = hiR

            if (cfg.activeProfile == AudioProfile.SPATIAL_360 && cfg.is8dEnabled) {
                // True 360° Multi-Speaker Surround System with Subtle Panning
                // Sound comes from all sides simultaneously (Front, Center, Left Side, Right Side, Rear)
                // while gently drifting in a smooth 360° pan around the sweet spot.
                rotationPhase = (rotationPhase + phaseDelta) % (2.0 * Math.PI)
                if (rotationPhase < 0) rotationPhase += 2.0 * Math.PI

                val xPos = sin(rotationPhase).toFloat()
                val yPos = cos(rotationPhase).toFloat()

                // Front & Rear directional bias (gentle +18% / -18% swell as sound rotates front/back)
                val frontBias = 1.0f + yPos * 0.18f
                val rearBias = 1.0f - yPos * 0.18f

                // Slight lateral panning depth (subtle 0.28 depth so opposite ear never cuts out)
                val panDepth = cfg.panDepth.coerceIn(0.15f, 0.45f)
                val panL = (1.0f - panDepth) + panDepth * 0.5f * (1.0f - xPos)
                val panR = (1.0f - panDepth) + panDepth * 0.5f * (1.0f + xPos)

                val center = (hiL + hiR) * 0.5f
                val frontL = hiL
                val frontR = hiR

                val side = (hiL - hiR) * 0.5f
                val sideL = side * 1.35f
                val sideR = -side * 1.35f

                // Rear surround channels (15ms Haas delay + pinna shadow)
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

                // Multi-speaker surround summation with gentle front/rear motion
                val sumL = frontL * (0.65f * frontBias) + center * (0.25f * frontBias) + sideL * 0.40f + (rearSpkL * 0.35f + rearSpkR * 0.12f) * rearBias
                val sumR = frontR * (0.65f * frontBias) + center * (0.25f * frontBias) + sideR * 0.40f + (rearSpkR * 0.35f + rearSpkL * 0.12f) * rearBias

                // Apply slight 360° lateral panning
                hipL = sumL * panL
                hipR = sumR * panR

            } else if (cfg.is8dEnabled) {
                // Orbital 8D / 16D Mode for Balanced / Nightcore / Slowed
                rotationPhase = (rotationPhase + phaseDelta) % (2.0 * Math.PI)
                if (rotationPhase < 0) rotationPhase += 2.0 * Math.PI

                val angle = rotationPhase
                val xPos: Float
                val yPos: Float

                when (cfg.rotationPattern) {
                    RotationPattern.SINE -> {
                        xPos = sin(angle).toFloat()
                        yPos = cos(angle).toFloat()
                    }
                    RotationPattern.TRIANGLE -> {
                        val tri = (2.0 / Math.PI * asin(sin(angle))).toFloat()
                        val triY = (2.0 / Math.PI * asin(sin(angle + Math.PI / 2.0))).toFloat()
                        xPos = tri
                        yPos = triY
                    }
                    RotationPattern.SQUARE -> {
                        xPos = if (sin(angle) >= 0) 1.0f else -1.0f
                        yPos = if (cos(angle) >= 0) 1.0f else -1.0f
                    }
                }

                // apulsator formula aligned with ITD / binaural psychoacoustics
                // Guaranteed minimum sound on opposite ear when reaching maximum deflection (~15%)
                // The opposite ear NEVER empties out or cuts to silence
                val minOpposite = 0.15f
                val panRange = 1.0f - minOpposite
                val panSweep = cfg.panDepth.coerceIn(0.2f, 1.0f) * panRange
                val gainL = (1.0f - panSweep) + panSweep * 0.5f * (1.0f - xPos)
                val gainR = (1.0f - panSweep) + panSweep * 0.5f * (1.0f + xPos)

                // Front-Back Distance Depth Cue (preserves 100% crisp high-frequency brilliance with zero muffling)
                if (cfg.distanceFrontBackCue) {
                    val backness = (-yPos).coerceIn(0f, 1f)
                    val backGain = 1.0f - backness * 0.10f
                    hipL *= backGain
                    hipR *= backGain
                }

                // Interaural Time Difference (ITD) - sample-accurate time delay with zero low-pass rolloff
                if (cfg.binauralHrtf) {
                    itdBufferL[itdWriteIndex] = hipL
                    itdBufferR[itdWriteIndex] = hipR

                    val delaySamples = kotlin.math.round((abs(xPos) * panSweep * maxItdSamples)).toInt().coerceIn(0, maxItdSamples.toInt())
                    if (xPos > 0f) {
                        val readIdx = (itdWriteIndex - delaySamples + bufSize) % bufSize
                        hipL = itdBufferL[readIdx]
                    } else if (xPos < 0f) {
                        val readIdx = (itdWriteIndex - delaySamples + bufSize) % bufSize
                        hipR = itdBufferR[readIdx]
                    }
                    itdWriteIndex = (itdWriteIndex + 1) % bufSize
                }

                hipL *= gainL
                hipR *= gainR
            }

            // 3. Mix [lo] and [hip]:
            var procL = loOutL + hipL
            var procR = loOutR + hipR

            // Optional Crossfeed (if enabled)
            if (cfg.crossfeed && cfg.crossfeedAmount > 0.05f) {
                val alpha = 0.20f
                xfeedStateL += alpha * (procL - xfeedStateL)
                xfeedStateR += alpha * (procR - xfeedStateR)
                val blend = cfg.crossfeedAmount * 0.25f
                val blendedL = procL * (1f - blend) + xfeedStateR * blend
                val blendedR = procR * (1f - blend) + xfeedStateL * blend
                procL = blendedL
                procR = blendedR
            }

            // 4. Arena Wall Early Reflections:
            // Feed-forward FIR reflections from auditorium walls (zero feedback = zero flutter/tearing)
            if (useArenaRefl) {
                val bufLen = arenaReflBufL.size
                arenaReflBufL[arenaReflIdx] = procL
                arenaReflBufR[arenaReflIdx] = procR

                val r1L = arenaReflBufL[(arenaReflIdx - arenaTap1 + bufLen) % bufLen]
                val r1R = arenaReflBufR[(arenaReflIdx - arenaTap2 + bufLen) % bufLen]
                val r2CrossL = arenaReflBufR[(arenaReflIdx - arenaTap3 + bufLen) % bufLen]
                val r2CrossR = arenaReflBufL[(arenaReflIdx - arenaTap3 + bufLen) % bufLen]

                arenaReflIdx = (arenaReflIdx + 1) % bufLen

                arenaReflDampL += 0.32f * ((r1L * 0.18f + r2CrossL * 0.10f) - arenaReflDampL)
                arenaReflDampR += 0.32f * ((r1R * 0.18f + r2CrossR * 0.10f) - arenaReflDampR)

                procL += arenaReflDampL
                procR += arenaReflDampR
            }

            if (cfg.reverbLevel > 0.02f) {
                // Decorrelated Stereo Schroeder-Moorer Auditorium Reverberation Tail
                val monoSpatial = (procL + procR) * 0.5f
                val reverbIn = reverbHpFilter.process(monoSpatial)
                val wet = processReverb(reverbIn, cfg.roomSize)
                val wetGain = cfg.reverbLevel * 0.45f
                procL += wet.first * wetGain
                procR += wet.second * wetGain
            }

            // 5. Output Limiter:
            // lavfi=[alimiter=limit=0.95]
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

            // Sanitize against any NaN or Inf to guarantee audio never drops out
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

            procL = procL.coerceIn(-1.0f, 1.0f)
            procR = procR.coerceIn(-1.0f, 1.0f)

            // Write 16-bit PCM output
            val outShortL = (procL * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            val outShortR = (procR * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            outputBuffer.putShort(outShortL)
            outputBuffer.putShort(outShortR)

            // Periodically emit visualizer updates
            sampleCounter++
            if (sampleCounter >= 2048) {
                sampleCounter = 0
                _rotationAngleDegrees.value = (Math.toDegrees(rotationPhase).toFloat() % 360f)
                emitSpectrumSnapshot()
            }
        }
        inputBuffer.position(inputBuffer.limit())
        outputBuffer.flip()
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

    private fun accumulateBandEnergy(l: Float, r: Float) {
        val energy = (abs(l) + abs(r)) * 0.5f
        for (b in 0 until 10) {
            bandEnergies[b] = bandEnergies[b] * 0.985f + energy * 0.015f
        }
    }

    private fun emitSpectrumSnapshot() {
        val snapshot = FloatArray(10)
        for (b in 0 until 10) {
            val weight = 1.0f + (b * 0.15f)
            snapshot[b] = (bandEnergies[b] * 3.5f * weight).coerceIn(0.05f, 1.0f)
        }
        _spectrumBands.value = snapshot
    }

    private fun updateFilters() {
        val fs = sampleRate.toDouble()
        val cfg = config

        // 4th-Order Linkwitz-Riley Crossover at 220 Hz (2 cascaded 2nd-order Butterworth filters per channel)
        val splitFreq = cfg.crossoverSplitHz.toDouble().coerceIn(80.0, 500.0)
        crossoverLp1L.setLowPass(freq = splitFreq, sampleRate = fs)
        crossoverLp2L.setLowPass(freq = splitFreq, sampleRate = fs)
        crossoverLp1R.setLowPass(freq = splitFreq, sampleRate = fs)
        crossoverLp2R.setLowPass(freq = splitFreq, sampleRate = fs)

        crossoverHp1L.setHighPass(freq = splitFreq, sampleRate = fs)
        crossoverHp2L.setHighPass(freq = splitFreq, sampleRate = fs)
        crossoverHp1R.setHighPass(freq = splitFreq, sampleRate = fs)
        crossoverHp2R.setHighPass(freq = splitFreq, sampleRate = fs)

        // Bass Boost (Low Shelf at 80/100 Hz or selected freq)
        @Suppress("SENSELESS_COMPARISON")
        val bassFreq = (if (cfg.bassBoostFreq != null) cfg.bassBoostFreq.freqHz else 80).toDouble()
        val bassGain = if (cfg.safeMode) cfg.bassBoostGainDb.coerceIn(0f, 6f).toDouble() else cfg.bassBoostGainDb.toDouble()
        bassFilterL.setLowShelf(bassFreq, gainDb = bassGain, sampleRate = fs)
        bassFilterR.setLowShelf(bassFreq, gainDb = bassGain, sampleRate = fs)

        // Treble Clarity (High Shelf at 8000 Hz or selected freq)
        val trebleFreq = cfg.trebleFreqHz.toDouble()
        val trebleGain = if (cfg.safeMode) cfg.trebleClarityDb.coerceIn(0f, 6f).toDouble() else cfg.trebleClarityDb.toDouble()
        trebleFilterL.setHighShelf(freq = trebleFreq, gainDb = trebleGain, sampleRate = fs)
        trebleFilterR.setHighShelf(freq = trebleFreq, gainDb = trebleGain, sampleRate = fs)

        // Vocal Presence Peaking Filter (2200 Hz with Q=0.75)
        // Subtle, balanced vocal lift that sits naturally in the mix with the instruments
        val vocGain = if (cfg.activeProfile == AudioProfile.BALANCED && cfg.is8dEnabled) 0.9 else 0.0
        vocalFilterL.setPeakingEQ(freq = 2200.0, gainDb = vocGain, sampleRate = fs, Q = 0.75)
        vocalFilterR.setPeakingEQ(freq = 2200.0, gainDb = vocGain, sampleRate = fs, Q = 0.75)

        // Sound Definition Peaking Filter (3600 Hz with Q=0.90)
        // Crisp transient edge and instrument separation faithful to original recording
        val defGain = if (cfg.activeProfile == AudioProfile.BALANCED && cfg.is8dEnabled) 1.2 else 0.0
        definitionFilterL.setPeakingEQ(freq = 3600.0, gainDb = defGain, sampleRate = fs, Q = 0.90)
        definitionFilterR.setPeakingEQ(freq = 3600.0, gainDb = defGain, sampleRate = fs, Q = 0.90)

        // Reverb Low-Cut Filter (High-pass at crossover split frequency to eliminate drum/bass rumble from reverb)
        reverbHpFilter.setHighPass(freq = cfg.crossoverSplitHz.toDouble().coerceAtLeast(220.0), sampleRate = fs)
    }

    override fun onFlush() {
        rotationPhase = 0.0
        itdBufferL.fill(0f)
        itdBufferR.fill(0f)
        itdWriteIndex = 0
        pinnaStateL = 0f
        pinnaStateR = 0f
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
 * 2nd-Order Direct Form II IIR Biquad Filter.
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
        var y = b0 * xIn + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
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
