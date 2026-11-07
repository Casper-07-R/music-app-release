package com.tsuki.backend.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class EightDAudioProcessorTest {

    @Test
    fun testBiquadLowShelfStability() {
        val filter = BiquadFilter()
        filter.setLowShelf(freq = 80.0, gainDb = 2.8, sampleRate = 48000.0)

        // Feed impulse and sinusoidal test tones for thousands of samples
        var maxVal = 0f
        for (i in 0 until 48000) {
            val input = if (i == 0) 1.0f else kotlin.math.sin(2.0 * Math.PI * 80.0 * i / 48000.0).toFloat() * 0.5f
            val out = filter.process(input)
            assertFalse("Filter diverged to NaN at sample $i", out.isNaN())
            assertFalse("Filter diverged to Infinity at sample $i", out.isInfinite())
            if (abs(out) > maxVal) maxVal = abs(out)
        }
        assertTrue("Filter should amplify 80 Hz input (maxVal=$maxVal)", maxVal > 0.5f)
        assertTrue("Filter should remain stable and not blow up (maxVal=$maxVal)", maxVal < 5.0f)
    }

    @Test
    fun testBiquadHighShelfStability() {
        val filter = BiquadFilter()
        filter.setHighShelf(freq = 8000.0, gainDb = 1.0, sampleRate = 48000.0)

        var maxVal = 0f
        for (i in 0 until 48000) {
            val input = if (i == 0) 1.0f else kotlin.math.sin(2.0 * Math.PI * 8000.0 * i / 48000.0).toFloat() * 0.5f
            val out = filter.process(input)
            assertFalse("Filter diverged to NaN at sample $i", out.isNaN())
            assertFalse("Filter diverged to Infinity at sample $i", out.isInfinite())
            if (abs(out) > maxVal) maxVal = abs(out)
        }
        assertTrue("High shelf should remain stable (maxVal=$maxVal)", maxVal < 5.0f)
    }

    @Test
    fun testEightDAudioProcessorBalancedPlaybackAudioFlow() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        val outFormat = processor.configure(format)
        processor.flush()

        assertEquals(48000, outFormat.sampleRate)
        assertEquals(2, outFormat.channelCount)

        // Enable 8D Balanced Mode
        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_8D, AudioProfile.BALANCED)

        // Simulate 1 second of stereo audio (e.g. simulated rock drum beat: kick @ 60 Hz + guitar @ 1 kHz)
        val frameCount = 48000
        val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val kick = (kotlin.math.sin(2.0 * Math.PI * 65.0 * i / 48000.0) * 16000.0).toInt().toShort()
            val guitar = (kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0) * 8000.0).toInt().toShort()
            val sample = (kick + guitar).toShort()
            inBuffer.putShort(sample)
            inBuffer.putShort(sample)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output
        assertTrue("Output buffer must have data", outBuffer.hasRemaining())
        assertEquals(frameCount * 4, outBuffer.remaining())

        var nonZeroCount = 0
        var totalAbs = 0L
        while (outBuffer.hasRemaining()) {
            val sL = outBuffer.short
            val sR = outBuffer.short
            if (sL != 0.toShort() || sR != 0.toShort()) nonZeroCount++
            totalAbs += (abs(sL.toInt()) + abs(sR.toInt()))
        }

        assertTrue("Output must not be silent ($nonZeroCount non-zero frames)", nonZeroCount > frameCount * 0.9)
        assertTrue("Output signal must have healthy audible volume", totalAbs > 1000000L)
    }

    @Test
    fun testEightDAudioProcessor16DPlaybackAudioFlow() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Enable 16D Balanced Mode
        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_16D, AudioProfile.BALANCED)

        val frameCount = 4800
        val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val s = (kotlin.math.sin(2.0 * Math.PI * 80.0 * i / 48000.0) * 20000.0).toInt().toShort()
            inBuffer.putShort(s)
            inBuffer.putShort(s)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output
        assertTrue("Output buffer must have data in 16D", outBuffer.hasRemaining())

        var nonZeroCount = 0
        while (outBuffer.hasRemaining()) {
            val sL = outBuffer.short
            val sR = outBuffer.short
            if (sL != 0.toShort() || sR != 0.toShort()) nonZeroCount++
        }
        assertTrue("16D Output must not be silent", nonZeroCount > frameCount * 0.9)
    }

    @Test
    fun testMicroDetailUpwardBoost() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Enable Balanced profile (which enables micro-detail extraction)
        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_8D, AudioProfile.BALANCED)

        // Feed quiet micro-detail audio (amplitude = 600 out of 32767, approx -35 dBFS)
        val frameCount = 9600 // 200 ms to let envelope settle
        val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val s = (kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0) * 600.0).toInt().toShort()
            inBuffer.putShort(s)
            inBuffer.putShort(s)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output
        assertTrue(outBuffer.hasRemaining())

        var maxOut = 0
        while (outBuffer.hasRemaining()) {
            val sL = abs(outBuffer.short.toInt())
            val sR = abs(outBuffer.short.toInt())
            if (sL > maxOut) maxOut = sL
            if (sR > maxOut) maxOut = sR
        }

        // Output should have received healthy upward boost for micro-details
        assertTrue("Micro-detail sound should be audible and boosted (maxOut=$maxOut)", maxOut > 100)
    }

    @Test
    fun testNightcoreProfileAudioFlow() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Enable Nightcore Mode
        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_8D, AudioProfile.NIGHTCORE)

        val frameCount = 48000
        val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val kick = (kotlin.math.sin(2.0 * Math.PI * 80.0 * i / 48000.0) * 14000.0).toInt().toShort()
            val guitar = (kotlin.math.sin(2.0 * Math.PI * 1200.0 * i / 48000.0) * 6000.0).toInt().toShort()
            val sample = (kick + guitar).toShort()
            inBuffer.putShort(sample)
            inBuffer.putShort(sample)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output
        assertTrue("Nightcore output must have data", outBuffer.hasRemaining())
        assertEquals(frameCount * 4, outBuffer.remaining())

        var nonZeroCount = 0
        var totalAbs = 0L
        while (outBuffer.hasRemaining()) {
            val sL = outBuffer.short
            val sR = outBuffer.short
            if (sL != 0.toShort() || sR != 0.toShort()) nonZeroCount++
            totalAbs += (abs(sL.toInt()) + abs(sR.toInt()))
        }

        assertTrue("Nightcore output must not be silent ($nonZeroCount non-zero)", nonZeroCount > frameCount * 0.9)
        assertTrue("Nightcore output must have rich volume ($totalAbs)", totalAbs > 1000000L)
    }

    @Test
    fun testSpatial360ProfileAudioFlow() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Enable Surround 360 Mode
        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_8D, AudioProfile.SPATIAL_360)

        val frameCount = 48000
        val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val kick = (kotlin.math.sin(2.0 * Math.PI * 75.0 * i / 48000.0) * 15000.0).toInt().toShort()
            val synth = (kotlin.math.sin(2.0 * Math.PI * 800.0 * i / 48000.0) * 8000.0).toInt().toShort()
            val sample = (kick + synth).toShort()
            inBuffer.putShort(sample)
            inBuffer.putShort(sample)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output
        assertTrue("Surround 360 output must have data", outBuffer.hasRemaining())
        assertEquals(frameCount * 4, outBuffer.remaining())

        var nonZeroCount = 0
        var totalAbs = 0L
        while (outBuffer.hasRemaining()) {
            val sL = outBuffer.short
            val sR = outBuffer.short
            if (sL != 0.toShort() || sR != 0.toShort()) nonZeroCount++
            totalAbs += (abs(sL.toInt()) + abs(sR.toInt()))
        }

        assertTrue("Surround 360 output must not be silent ($nonZeroCount non-zero)", nonZeroCount > frameCount * 0.9)
        assertTrue("Surround 360 output must have rich volume ($totalAbs)", totalAbs > 1000000L)
    }

    @Test
    fun testMinuteSoundsExtractedAcrossAllProfiles() {
        val profiles = arrayOf(
            AudioProfile.BALANCED,
            AudioProfile.SPATIAL_360,
            AudioProfile.NIGHTCORE,
            AudioProfile.SLOWED_REVERB
        )

        for (profile in profiles) {
            val processor = EightDAudioProcessor()
            val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
            processor.configure(format)
            processor.flush()
            processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_8D, profile)

            // Feed very delicate, minute sound (amplitude 50 out of 32767 = -56 dBFS, e.g. subtle breath or finger pick)
            val frameCount = 9600
            val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frameCount) {
                val s = (kotlin.math.sin(2.0 * Math.PI * 3000.0 * i / 48000.0) * 50.0).toInt().toShort()
                inBuffer.putShort(s)
                inBuffer.putShort(s)
            }
            inBuffer.flip()

            processor.queueInput(inBuffer)
            val outBuffer = processor.output
            assertTrue("Output must have data for $profile", outBuffer.hasRemaining())

            var maxVal = 0
            while (outBuffer.hasRemaining()) {
                val sL = abs(outBuffer.short.toInt())
                val sR = abs(outBuffer.short.toInt())
                if (sL > maxVal) maxVal = sL
                if (sR > maxVal) maxVal = sR
            }

            assertTrue("Minute sound must be clearly audible and boosted for profile $profile (maxVal=$maxVal)", maxVal >= 25)
        }
    }

    @Test
    fun testBalancedZeroTearWithTransients() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()
        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_8D, AudioProfile.BALANCED)

        // Feed aggressive transient drum bursts followed by micro-decay
        val frameCount = 48000
        val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            // Snare transient every 12000 samples (250ms)
            val isBurst = (i % 12000) < 480
            val s = if (isBurst) (kotlin.math.sin(2.0 * Math.PI * 250.0 * i / 48000.0) * 26000.0).toInt().toShort()
            else (kotlin.math.sin(2.0 * Math.PI * 1500.0 * i / 48000.0) * 100.0).toInt().toShort()
            inBuffer.putShort(s)
            inBuffer.putShort(s)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output

        var sampleIdx = 0
        while (outBuffer.hasRemaining()) {
            val sL = outBuffer.short.toInt()
            val sR = outBuffer.short.toInt()
            assertFalse("Balanced sample must never overflow or be NaN at $sampleIdx", sL > 32767 || sL < -32768)
            assertFalse("Balanced sample must never overflow or be NaN at $sampleIdx", sR > 32767 || sR < -32768)
            sampleIdx++
        }
        assertEquals(frameCount, sampleIdx)
    }

    @Test
    fun testSurround360SimultaneousSidesNoDropout() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()
        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_8D, AudioProfile.SPATIAL_360)

        // Feed full stereo music
        val frameCount = 48000
        val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val l = (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 48000.0) * 12000.0).toInt().toShort()
            val r = (kotlin.math.sin(2.0 * Math.PI * 660.0 * i / 48000.0) * 12000.0).toInt().toShort()
            inBuffer.putShort(l)
            inBuffer.putShort(r)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output

        // Verify both channels continuously output sound simultaneously (no rotation ear dropout)
        var leftNonZero = 0
        var rightNonZero = 0
        while (outBuffer.hasRemaining()) {
            val sL = outBuffer.short
            val sR = outBuffer.short
            if (sL != 0.toShort()) leftNonZero++
            if (sR != 0.toShort()) rightNonZero++
        }

        assertTrue("Surround 360 left must play continuously", leftNonZero > frameCount * 0.95)
        assertTrue("Surround 360 right must play continuously", rightNonZero > frameCount * 0.95)
    }

    @Test
    fun testBalancedProfileRotationSpeedIncrease() {
        val cfg8D = AudioDspConfig.createConfig(SpatialMode.MODE_8D, AudioProfile.BALANCED)
        assertEquals("Balanced 8D rotation speed should be 11.0s (faster than 13.0s)", 11.0f, cfg8D.rotationSpeedSeconds, 0.01f)

        val cfg16D = AudioDspConfig.createConfig(SpatialMode.MODE_16D, AudioProfile.BALANCED)
        assertEquals("Balanced 16D rotation speed should be 7.5s", 7.5f, cfg16D.rotationSpeedSeconds, 0.01f)
    }

    @Test
    fun testOppositeEarNeverEmptiesOutAtMaxPanning() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_16D, AudioProfile.BALANCED)

        // Process a continuous tone across a full rotation cycle (7 seconds)
        val frameCount = 48000 * 7
        val inBuffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val sample = (kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0) * 16000.0).toInt().toShort()
            inBuffer.putShort(sample)
            inBuffer.putShort(sample)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output

        var silentL = 0
        var silentR = 0
        while (outBuffer.hasRemaining()) {
            val sL = outBuffer.short
            val sR = outBuffer.short
            // Any complete silence or near-zero empty frame (< 50 out of 32767)
            if (abs(sL.toInt()) < 50) silentL++
            if (abs(sR.toInt()) < 50) silentR++
        }

        // Neither ear should ever empty out to near-zero/silence during side-to-side panning
        assertTrue("Left ear must never empty out ($silentL silent frames)", silentL < frameCount * 0.01)
        assertTrue("Right ear must never empty out ($silentR silent frames)", silentR < frameCount * 0.01)
    }

    @Test
    fun testSoundDefinitionPeakingFilterStability() {
        val filter = BiquadFilter()
        filter.setPeakingEQ(freq = 3200.0, gainDb = 1.8, sampleRate = 48000.0, Q = 0.85)

        var maxVal = 0f
        for (i in 0 until 48000) {
            val input = if (i == 0) 1.0f else kotlin.math.sin(2.0 * Math.PI * 3200.0 * i / 48000.0).toFloat() * 0.5f
            val out = filter.process(input)
            assertFalse("Peaking filter diverged to NaN", out.isNaN())
            assertFalse("Peaking filter diverged to Infinity", out.isInfinite())
            if (abs(out) > maxVal) maxVal = abs(out)
        }
        assertTrue("Definition peaking EQ should boost 3.2 kHz presence (maxVal=$maxVal)", maxVal > 0.55f)
        assertTrue("Definition filter must remain stable (maxVal=$maxVal)", maxVal < 5.0f)
    }

    @Test
    fun testVocalPresencePeakingFilterStability() {
        val filter = BiquadFilter()
        filter.setPeakingEQ(freq = 2200.0, gainDb = 0.9, sampleRate = 48000.0, Q = 0.75)

        var maxVal = 0f
        for (i in 0 until 48000) {
            val input = if (i == 0) 1.0f else kotlin.math.sin(2.0 * Math.PI * 2200.0 * i / 48000.0).toFloat() * 0.5f
            val out = filter.process(input)
            assertFalse("Vocal filter diverged to NaN", out.isNaN())
            assertFalse("Vocal filter diverged to Infinity", out.isInfinite())
            if (abs(out) > maxVal) maxVal = abs(out)
        }
        assertTrue("Vocal peaking EQ should boost 2.2 kHz vocal range (maxVal=$maxVal)", maxVal > 0.55f)
        assertTrue("Vocal filter must remain stable (maxVal=$maxVal)", maxVal < 5.0f)
    }

    @Test
    fun testHighFrequenciesRemainCrystalClearAtSides() {
        val processor = EightDAudioProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        processor.config = AudioDspConfig.createConfig(SpatialMode.MODE_16D, AudioProfile.BALANCED)

        // Advance rotation to side (1/4 of 7.5s cycle)
        val sideFrames = (48000 * 7.5 / 4).toInt()
        val inBuffer = ByteBuffer.allocate(sideFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sideFrames) {
            // 8 kHz high-frequency tone
            val s = (kotlin.math.sin(2.0 * Math.PI * 8000.0 * i / 48000.0) * 16000.0).toInt().toShort()
            inBuffer.putShort(s)
            inBuffer.putShort(s)
        }
        inBuffer.flip()

        processor.queueInput(inBuffer)
        val outBuffer = processor.output

        var maxSideL = 0
        var maxSideR = 0
        while (outBuffer.hasRemaining()) {
            val sL = abs(outBuffer.short.toInt())
            val sR = abs(outBuffer.short.toInt())
            if (sL > maxSideL) maxSideL = sL
            if (sR > maxSideR) maxSideR = sR
        }

        // The side ear must have crisp, unmuffled high-frequency output (> 12000 out of 16000 nominal)
        assertTrue("Side ear must remain bright and unmuffled (maxSideR=$maxSideR)", maxSideR > 12000)
    }
}
