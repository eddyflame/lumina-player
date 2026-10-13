package org.lumina.player.core.dsp

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time audio spectrum analyzer processor for ExoPlayer.
 * Captures raw PCM audio samples directly in the playback pipeline with zero permissions,
 * computes Fast Fourier Transform (FFT) frequency bands, and outputs real-time spectrum magnitudes.
 */
class VisualizerAudioProcessor(
    val barCount: Int = 24
) : BaseAudioProcessor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _spectrumFlow = MutableStateFlow(FloatArray(barCount))
    val spectrumFlow: StateFlow<FloatArray> = _spectrumFlow.asStateFlow()

    private val fftSize = 512
    private val halfFft = fftSize / 2
    private val pcmSampleBuffer = FloatArray(fftSize)
    private var pcmSampleIndex = 0

    // Reusable FFT working buffers to avoid runtime GC allocations
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val smoothedBars = FloatArray(barCount)

    // Precomputed Hanning window coefficients
    private val hanningWindow = FloatArray(fftSize) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))).toFloat()
    }

    // Precomputed frequency band bin boundaries (logarithmic distribution)
    private val bandBinRanges = Array(barCount) { 0..0 }

    private var sampleRate = 44100
    private var channelCount = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var lastEmitTimeMs = 0L

    // Anti-pop micro soft ramp (35ms smooth raised-cosine gain fade-in on startup, unpause, and seek)
    private var totalRampFrames = (44100 * 35) / 1000
    private var rampFramesRemaining = totalRampFrames

    private var decayJob: Job? = null
    @Volatile
    private var hasActiveAudio = false

    init {
        computeBandRanges(sampleRate)
        startDecayMonitor()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        channelCount = inputAudioFormat.channelCount.takeIf { it > 0 } ?: 2
        encoding = inputAudioFormat.encoding
        computeBandRanges(sampleRate)
        totalRampFrames = ((sampleRate * 35) / 1000).coerceAtLeast(64)
        rampFramesRemaining = totalRampFrames
        return inputAudioFormat // Pass-through unaltered audio
    }

    override fun onFlush() {
        super.onFlush()
        pcmSampleIndex = 0
        rampFramesRemaining = totalRampFrames
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Pass-through audio data to downstream sink so playback remains bit-perfect
        val outputBuffer = replaceOutputBuffer(remaining)
        val sampleBytes = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()

        // Apply hardware anti-pop soft ramp envelope on audio track start / seek / unpause
        if (rampFramesRemaining > 0) {
            val bytesPerSample = when (encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_24BIT -> 3
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
                else -> 0
            }
            val bytesPerFrame = channelCount * bytesPerSample
            if (bytesPerFrame > 0 && remaining >= bytesPerFrame) {
                val totalFrames = remaining / bytesPerFrame
                val framesToRamp = totalFrames.coerceAtMost(rampFramesRemaining)
                val out = outputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                for (f in 0 until framesToRamp) {
                    val currentRampIndex = totalRampFrames - rampFramesRemaining
                    val fraction = currentRampIndex.toFloat() / totalRampFrames.toFloat()
                    // Raised cosine (Hann) window for mathematically continuous zero-derivative ramp
                    val gain = (0.5f - 0.5f * kotlin.math.cos(Math.PI * fraction).toFloat()).coerceIn(0f, 1f)
                    val frameOffset = f * bytesPerFrame

                    when (encoding) {
                        C.ENCODING_PCM_16BIT -> {
                            for (ch in 0 until channelCount) {
                                val samplePos = frameOffset + ch * 2
                                val sample = out.getShort(samplePos)
                                val ramped = (sample * gain).toInt().coerceIn(-32768, 32767).toShort()
                                out.putShort(samplePos, ramped)
                            }
                        }
                        C.ENCODING_PCM_24BIT -> {
                            for (ch in 0 until channelCount) {
                                val samplePos = frameOffset + ch * 3
                                val b0 = out.get(samplePos).toInt() and 0xFF
                                val b1 = out.get(samplePos + 1).toInt() and 0xFF
                                val b2 = out.get(samplePos + 2).toInt()
                                val sample = (b2 shl 16) or (b1 shl 8) or b0
                                val ramped = (sample * gain).toInt().coerceIn(-8388608, 8388607)
                                out.put(samplePos, (ramped and 0xFF).toByte())
                                out.put(samplePos + 1, ((ramped shr 8) and 0xFF).toByte())
                                out.put(samplePos + 2, ((ramped shr 16) and 0xFF).toByte())
                            }
                        }
                        C.ENCODING_PCM_32BIT -> {
                            for (ch in 0 until channelCount) {
                                val samplePos = frameOffset + ch * 4
                                val sample = out.getInt(samplePos)
                                val ramped = (sample * gain.toDouble()).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                                out.putInt(samplePos, ramped)
                            }
                        }
                        C.ENCODING_PCM_FLOAT -> {
                            for (ch in 0 until channelCount) {
                                val samplePos = frameOffset + ch * 4
                                val sample = out.getFloat(samplePos)
                                out.putFloat(samplePos, sample * gain)
                            }
                        }
                    }
                    rampFramesRemaining--
                }
            }
        }

        if (encoding != C.ENCODING_PCM_16BIT) return

        hasActiveAudio = true

        // Extract PCM 16-bit samples into local FFT buffer
        while (sampleBytes.remaining() >= channelCount * 2) {
            val left = sampleBytes.short / 32768.0f
            val monoSample = if (channelCount >= 2) {
                val right = sampleBytes.short / 32768.0f
                (left + right) * 0.5f
            } else {
                left
            }

            // Skip remaining channels if multichannel > 2
            if (channelCount > 2) {
                sampleBytes.position(sampleBytes.position() + (channelCount - 2) * 2)
            }

            pcmSampleBuffer[pcmSampleIndex++] = monoSample
            if (pcmSampleIndex >= fftSize) {
                pcmSampleIndex = 0
                processFftFrame()
            }
        }
    }

    /**
     * Executes in-place Radix-2 Cooley-Tukey FFT and updates frequency spectrum bars.
     */
    private fun processFftFrame() {
        // Apply Hanning window
        for (i in 0 until fftSize) {
            fftReal[i] = pcmSampleBuffer[i] * hanningWindow[i]
            fftImag[i] = 0.0f
        }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until fftSize - 1) {
            if (i < j) {
                val tempR = fftReal[i]; fftReal[i] = fftReal[j]; fftReal[j] = tempR
                val tempI = fftImag[i]; fftImag[i] = fftImag[j]; fftImag[j] = tempI
            }
            var k = fftSize shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey Radix-2 computation
        var step = 1
        while (step < fftSize) {
            val jump = step shl 1
            val deltaAngle = -PI / step
            var angle = 0.0
            for (m in 0 until step) {
                val wr = cos(angle).toFloat()
                val wi = sin(angle).toFloat()
                angle += deltaAngle

                for (i in m until fftSize step jump) {
                    val pair = i + step
                    val tr = wr * fftReal[pair] - wi * fftImag[pair]
                    val ti = wr * fftImag[pair] + wi * fftReal[pair]

                    fftReal[pair] = fftReal[i] - tr
                    fftImag[pair] = fftImag[i] - ti
                    fftReal[i] += tr
                    fftImag[i] += ti
                }
            }
            step = jump
        }

        // Calculate band magnitudes and apply dynamic attack/decay
        val currentBars = FloatArray(barCount)
        val now = System.currentTimeMillis()

        for (b in 0 until barCount) {
            val range = bandBinRanges[b]
            var sumMagnitude = 0.0f
            var count = 0
            for (bin in range) {
                val mag = sqrt(fftReal[bin] * fftReal[bin] + fftImag[bin] * fftImag[bin])
                sumMagnitude += mag
                count++
            }
            val avgMag = if (count > 0) sumMagnitude / count else 0.0f

            // Frequency weighting curve: compensate for high-frequency natural roll-off
            val freqWeight = 1.0f + (b.toFloat() / barCount) * 1.8f
            val rawNormalized = (avgMag * freqWeight * 0.12f).coerceIn(0.0f, 1.0f)
            // Perceptual logarithmic power curve to mimic human auditory loudness
            val normalized = Math.pow(rawNormalized.toDouble(), 0.75).toFloat()

            // Smooth dynamic attack (gradual organic rise) & decay (graceful liquid falloff)
            val current = smoothedBars[b]
            val smoothed = if (normalized > current) {
                current + (normalized - current) * 0.42f // Smooth organic attack
            } else {
                current * 0.89f // Graceful exponential decay
            }
            smoothedBars[b] = smoothed
            currentBars[b] = smoothed.coerceIn(0.04f, 1.0f)
        }

        // Throttle UI flow emission to ~40-60 FPS (approx every 18ms)
        if (now - lastEmitTimeMs >= 18L) {
            lastEmitTimeMs = now
            _spectrumFlow.value = currentBars
        }
    }

    /**
     * Computes logarithmic frequency boundaries matching human auditory perception.
     */
    private fun computeBandRanges(rate: Int) {
        val binResolution = rate.toFloat() / fftSize
        val minFreq = 30.0f
        val maxFreq = 16000.0f.coerceAtMost(rate / 2.0f)

        for (i in 0 until barCount) {
            val startFreq = minFreq * Math.pow((maxFreq / minFreq).toDouble(), i.toDouble() / barCount).toFloat()
            val endFreq = minFreq * Math.pow((maxFreq / minFreq).toDouble(), (i + 1).toDouble() / barCount).toFloat()

            val startBin = (startFreq / binResolution).toInt().coerceIn(1, halfFft - 1)
            val endBin = (endFreq / binResolution).toInt().coerceIn(startBin, halfFft - 1)

            bandBinRanges[i] = startBin..endBin
        }
    }

    /**
     * Monitors audio idle state to smoothly decay bars to rest when playback pauses or stops.
     */
    private fun startDecayMonitor() {
        decayJob = scope.launch {
            while (isActive) {
                delay(30)
                val now = System.currentTimeMillis()
                if (now - lastEmitTimeMs > 60L && hasActiveAudio) {
                    var anyActive = false
                    val decayed = FloatArray(barCount)
                    for (i in 0 until barCount) {
                        smoothedBars[i] = (smoothedBars[i] * 0.85f).coerceAtLeast(0.0f)
                        decayed[i] = smoothedBars[i].coerceAtLeast(0.04f)
                        if (smoothedBars[i] > 0.05f) anyActive = true
                    }
                    _spectrumFlow.value = decayed
                    if (!anyActive) {
                        hasActiveAudio = false
                    }
                }
            }
        }
    }

    override fun onReset() {
        super.onReset()
        resetSpectrum()
        pcmSampleIndex = 0
        rampFramesRemaining = totalRampFrames
    }

    /**
     * Re-arms the anti-pop soft ramp envelope so the next audio frames fade in smoothly.
     * Called whenever playback resumes, seeks, or unpauses.
     */
    fun triggerSoftRamp() {
        rampFramesRemaining = totalRampFrames
    }

    /**
     * Instantly resets all bars when playback stops.
     */
    fun resetSpectrum() {
        hasActiveAudio = false
        for (i in 0 until barCount) {
            smoothedBars[i] = 0.04f
        }
        _spectrumFlow.value = FloatArray(barCount) { 0.04f }
    }

    companion object {
        /**
         * Builds an ExoPlayer DefaultRenderersFactory wired with this spectrum audio processor.
         */
        fun createRenderersFactory(
            context: Context,
            processor: VisualizerAudioProcessor
        ): DefaultRenderersFactory {
            return object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(processor))
                        .build()
                }
            }
        }
    }
}
