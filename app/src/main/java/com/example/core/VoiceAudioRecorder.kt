package com.example.core

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.min

private const val TAG = "VoiceAudioRecorder"
private const val MAX_AUDIO_BYTES = 15 * 1024 * 1024

data class RecordedAudioMemo(
    val file: File,
    val durationMs: Long,
    val bytes: ByteArray,
    val mimeType: String = "audio/mp4",
    val waveformSamples: List<Float> = emptyList()
) {
    val formattedDuration: String
        get() = formatAudioDuration(durationMs)
}

data class VoiceRecorderState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitudes: List<Float> = List(28) { 0.08f },
    val isPlayingPreview: Boolean = false,
    val playbackProgress: Float = 0f,
    val error: String? = null
)

fun formatAudioDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

class VoiceAudioRecorder(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(VoiceRecorderState())
    val state: StateFlow<VoiceRecorderState> = _state.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0L
    private var meterJob: Job? = null
    private var playbackJob: Job? = null
    private val capturedSamples = mutableListOf<Float>()

    fun startRecording(): Result<Unit> = runCatching {
        stopPlayback()
        stopAndCleanupRecorderOnly()

        val memoDir = File(context.cacheDir, "voice_memos").apply {
            if (!exists()) mkdirs()
        }
        // Clean up older temporary memos
        memoDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }

        val outFile = File(memoDir, "memo_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setMaxFileSize(MAX_AUDIO_BYTES.toLong())
            setOutputFile(outFile.absolutePath)
            prepare()
            start()
        }

        mediaRecorder = recorder
        currentOutputFile = outFile
        recordingStartTimeMs = System.currentTimeMillis()
        capturedSamples.clear()

        _state.value = VoiceRecorderState(
            isRecording = true,
            elapsedMs = 0L,
            amplitudes = List(28) { 0.1f },
            error = null
        )

        meterJob?.cancel()
        meterJob = scope.launch {
            while (isActive && _state.value.isRecording) {
                val rawAmp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                val normalized = min(1f, (rawAmp / 18000f)).coerceIn(0.08f, 1f)
                capturedSamples.add(normalized)
                if (capturedSamples.size > 120) {
                    capturedSamples.removeAt(0)
                }
                val updatedBars = (_state.value.amplitudes.drop(1) + normalized)
                _state.value = _state.value.copy(
                    elapsedMs = System.currentTimeMillis() - recordingStartTimeMs,
                    amplitudes = updatedBars
                )
                delay(70L)
            }
        }
    }.onFailure { err ->
        Log.e(TAG, "Failed to start MediaRecorder", err)
        stopAndCleanupRecorderOnly()
        _state.value = _state.value.copy(
            isRecording = false,
            error = err.localizedMessage ?: "Unable to access microphone hardware."
        )
    }

    suspend fun stopRecordingAndCollect(): Result<RecordedAudioMemo> {
        meterJob?.cancel()
        meterJob = null
        val elapsed = (System.currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(0L)
        val outFile = currentOutputFile

        val stopResult = withContext(Dispatchers.IO) {
            runCatching {
                mediaRecorder?.let { rec ->
                    try {
                        rec.stop()
                    } catch (e: RuntimeException) {
                        // Happens if stopped immediately before audio frames were flushed
                        throw IllegalStateException("Recording was too short. Hold or speak slightly longer.", e)
                    } finally {
                        rec.release()
                    }
                }
                mediaRecorder = null

                val file = outFile ?: error("No audio file created.")
                if (!file.exists() || file.length() <= 0L) {
                    error("Recorded audio file is empty.")
                }
                val bytes = file.readBytes()
                if (bytes.size > MAX_AUDIO_BYTES) {
                    error("Voice memo exceeds the 15 MB upload limit.")
                }
                RecordedAudioMemo(
                    file = file,
                    durationMs = elapsed.coerceAtLeast(1000L),
                    bytes = bytes,
                    mimeType = "audio/mp4",
                    waveformSamples = capturedSamples.takeLast(28).ifEmpty { List(20) { 0.35f } }
                )
            }
        }

        _state.value = _state.value.copy(
            isRecording = false,
            elapsedMs = 0L,
            error = stopResult.exceptionOrNull()?.localizedMessage
        )
        if (stopResult.isFailure) {
            outFile?.delete()
        }
        currentOutputFile = null
        return stopResult
    }

    fun cancelRecording() {
        meterJob?.cancel()
        meterJob = null
        stopAndCleanupRecorderOnly()
        currentOutputFile?.delete()
        currentOutputFile = null
        _state.value = _state.value.copy(
            isRecording = false,
            elapsedMs = 0L,
            amplitudes = List(28) { 0.08f },
            error = null
        )
    }

    fun togglePlayback(file: File) {
        if (_state.value.isPlayingPreview) {
            stopPlayback()
            return
        }
        if (!file.exists()) {
            _state.value = _state.value.copy(error = "Audio memo file is no longer available.")
            return
        }
        runCatching {
            stopPlayback()
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            mediaPlayer = player
            _state.value = _state.value.copy(isPlayingPreview = true, playbackProgress = 0f, error = null)
            playbackJob?.cancel()
            playbackJob = scope.launch {
                while (isActive && _state.value.isPlayingPreview) {
                    val current = mediaPlayer ?: break
                    val dur = current.duration.coerceAtLeast(1)
                    val pos = current.currentPosition.coerceIn(0, dur)
                    _state.value = _state.value.copy(playbackProgress = pos.toFloat() / dur.toFloat())
                    delay(80L)
                }
            }
        }.onFailure { err ->
            Log.e(TAG, "Audio memo playback error", err)
            stopPlayback()
            _state.value = _state.value.copy(error = err.localizedMessage ?: "Playback failed.")
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        _state.value = _state.value.copy(isPlayingPreview = false, playbackProgress = 0f)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun release() {
        cancelRecording()
        stopPlayback()
    }

    private fun stopAndCleanupRecorderOnly() {
        mediaRecorder?.let { rec ->
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            try {
                rec.release()
            } catch (_: Exception) {
            }
        }
        mediaRecorder = null
    }
}
