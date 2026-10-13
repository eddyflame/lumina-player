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
        return inputAudioFormat // Pass-through unaltered audio
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Pass-through audio data to downstream sink so playback remains bit-perfect
        val outputBuffer = replaceOutputBuffer(remaining)
        val sampleBytes = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()

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
            val normalized = (avgMag * freqWeight * 0.12f).coerceIn(0.0f, 1.0f)

            // Dynamic attack (instant response) & decay (smooth falloff)
            val current = smoothedBars[b]
            val smoothed = if (normalized > current) {
                normalized // Instant attack
            } else {
                current * 0.82f // Smooth exponential decay
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
                        smoothedBars[i] = (smoothedBars[i] * 0.75f).coerceAtLeast(0.0f)
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
