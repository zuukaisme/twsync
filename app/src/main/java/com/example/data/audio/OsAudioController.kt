package com.example.data.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.bluetooth.DiagnosticLogger
import com.example.domain.model.LogLevel
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AudioChannelTest(val label: String) {
  LEFT("Left Only"),
  RIGHT("Right Only"),
  STEREO_SWEEP("L -> R Sweep"),
}

data class OutputAudioRoute(
  val id: Int,
  val productName: String,
  val typeLabel: String,
  val isBluetooth: Boolean,
  val channelCounts: String,
)

data class OsAudioState(
  val mediaVolume: Int = 0,
  val mediaMaxVolume: Int = 15,
  val isMediaMuted: Boolean = false,
  val callVolume: Int = 0,
  val callMaxVolume: Int = 5,
  val ringVolume: Int = 0,
  val ringMaxVolume: Int = 7,
  val ringerModeLabel: String = "Normal",
  val outputRoutes: List<OutputAudioRoute> = emptyList(),
  val activeChannelTest: AudioChannelTest? = null,
)

class OsAudioController(
  context: Context,
  private val logger: DiagnosticLogger,
) {
  private val appContext = context.applicationContext
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _audioState = MutableStateFlow(readCurrentAudioState())
  val audioState: StateFlow<OsAudioState> = _audioState.asStateFlow()

  private var toneJob: Job? = null
  private var receiverRegistered = false
  private var deviceCallbackRegistered = false

  private val audioReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
          ACTION_VOLUME_CHANGED,
          AudioManager.RINGER_MODE_CHANGED_ACTION,
          AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
            refreshAudioState()
          }
        }
      }
    }

  private val audioDeviceCallback =
    object : AudioDeviceCallback() {
      override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
        refreshAudioState()
      }

      override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
        refreshAudioState()
      }
    }

  init {
    registerListeners()
    refreshAudioState()
  }

  private fun registerListeners() {
    if (!receiverRegistered) {
      val filter =
        IntentFilter().apply {
          addAction(ACTION_VOLUME_CHANGED)
          addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
          addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
      ContextCompat.registerReceiver(appContext, audioReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
      receiverRegistered = true
    }
    if (!deviceCallbackRegistered && audioManager != null) {
      audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
      deviceCallbackRegistered = true
    }
  }

  fun refreshAudioState() {
    val activeTest = _audioState.value.activeChannelTest
    _audioState.value = readCurrentAudioState(activeTest)
  }

  private fun readCurrentAudioState(activeTest: AudioChannelTest? = null): OsAudioState {
    val am = audioManager ?: return OsAudioState(activeChannelTest = activeTest)
    val mediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    val mediaMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val callVol = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
    val callMax = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1)
    val ringVol = am.getStreamVolume(AudioManager.STREAM_RING)
    val ringMax = am.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
    val isMuted = am.isStreamMute(AudioManager.STREAM_MUSIC) || mediaVol == 0

    val ringerLabel =
      when (am.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "Silent"
        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
        else -> "Normal"
      }

    val routes =
      am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { info ->
        val typeInfo = mapAudioDeviceType(info.type)
        val channels =
          info.channelCounts.takeIf { it.isNotEmpty() }?.joinToString("/") { "${it}ch" } ?: "Stereo"
        OutputAudioRoute(
          id = info.id,
          productName = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeInfo.first,
          typeLabel = typeInfo.first,
          isBluetooth = typeInfo.second,
          channelCounts = channels,
        )
      }

    return OsAudioState(
      mediaVolume = mediaVol,
      mediaMaxVolume = mediaMax,
      isMediaMuted = isMuted,
      callVolume = callVol,
      callMaxVolume = callMax,
      ringVolume = ringVol,
      ringMaxVolume = ringMax,
      ringerModeLabel = ringerLabel,
      outputRoutes = routes,
      activeChannelTest = activeTest,
    )
  }

  fun setMediaVolume(level: Int) {
    setStreamVolumeSafe(AudioManager.STREAM_MUSIC, level, "Media")
  }

  fun toggleMediaMute() {
    val am = audioManager ?: return
    try {
      val currentlyMuted = am.isStreamMute(AudioManager.STREAM_MUSIC)
      val dir = if (currentlyMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
      am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
      refreshAudioState()
      logger.log(
        LogLevel.INFO,
        "OS_AUDIO",
        if (currentlyMuted) "Unmuted media stream" else "Muted media stream",
      )
    } catch (se: SecurityException) {
      logger.log(LogLevel.WARNING, "OS_AUDIO", "Unable to toggle mute", se.message)
    }
  }

  fun setCallVolume(level: Int) {
    setStreamVolumeSafe(AudioManager.STREAM_VOICE_CALL, level, "Voice Call")
  }

  fun setRingVolume(level: Int) {
    setStreamVolumeSafe(AudioManager.STREAM_RING, level, "Ring & Alerts")
  }

  private fun setStreamVolumeSafe(stream: Int, level: Int, label: String) {
    val am = audioManager ?: return
    try {
      val max = am.getStreamMaxVolume(stream)
      val clamped = level.coerceIn(0, max)
      am.setStreamVolume(stream, clamped, 0)
      refreshAudioState()
    } catch (se: SecurityException) {
      logger.log(
        LogLevel.WARNING,
        "OS_AUDIO",
        "Cannot adjust $label volume (Do Not Disturb policy active)",
        se.message,
      )
    }
  }

  fun playChannelTest(test: AudioChannelTest) {
    if (_audioState.value.activeChannelTest == test) {
      stopChannelTest()
      return
    }
    toneJob?.cancel()
    _audioState.value = _audioState.value.copy(activeChannelTest = test)
    logger.log(LogLevel.INFO, "AUDIO_TEST", "Playing stereo channel test: ${test.label}")

    toneJob =
      scope.launch {
        var track: AudioTrack? = null
        try {
          val sampleRate = 44_100
          val durationSec = if (test == AudioChannelTest.STEREO_SWEEP) 1.6 else 0.9
          val totalFrames = (sampleRate * durationSec).toInt()
          val samples = ShortArray(totalFrames * 2)

          for (frame in 0 until totalFrames) {
            val t = frame.toDouble() / sampleRate
            val envelope =
              when {
                frame < 800 -> frame / 800.0
                frame > totalFrames - 1200 -> (totalFrames - frame).coerceAtLeast(0) / 1200.0
                else -> 1.0
              }

            val leftGain: Double
            val rightGain: Double
            when (test) {
              AudioChannelTest.LEFT -> {
                leftGain = 0.55
                rightGain = 0.0
              }
              AudioChannelTest.RIGHT -> {
                leftGain = 0.0
                rightGain = 0.55
              }
              AudioChannelTest.STEREO_SWEEP -> {
                val half = totalFrames / 2
                if (frame < half) {
                  leftGain = 0.55
                  rightGain = 0.0
                } else {
                  leftGain = 0.0
                  rightGain = 0.55
                }
              }
            }

            val leftSample =
              (sin(2.0 * PI * 440.0 * t) * Short.MAX_VALUE * leftGain * envelope).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            val rightSample =
              (sin(2.0 * PI * 587.33 * t) * Short.MAX_VALUE * rightGain * envelope).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            samples[frame * 2] = leftSample
            samples[frame * 2 + 1] = rightSample
          }

          val byteCount = samples.size * 2
          track =
            AudioTrack.Builder()
              .setAudioAttributes(
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                  .build(),
              )
              .setAudioFormat(
                AudioFormat.Builder()
                  .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                  .setSampleRate(sampleRate)
                  .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                  .build(),
              )
              .setTransferMode(AudioTrack.MODE_STATIC)
              .setBufferSizeInBytes(byteCount)
              .build()

          track.write(samples, 0, samples.size)
          track.play()

          val waitMs = (durationSec * 1000).toLong() + 80L
          kotlinx.coroutines.delay(waitMs)
        } catch (e: Exception) {
          logger.log(LogLevel.WARNING, "AUDIO_TEST", "AudioTrack playback interrupted", e.message)
        } finally {
          try {
            track?.stop()
            track?.release()
          } catch (_: Exception) {
          }
          _audioState.value = _audioState.value.copy(activeChannelTest = null)
        }
      }
  }

  fun stopChannelTest() {
    toneJob?.cancel()
    toneJob = null
    _audioState.value = _audioState.value.copy(activeChannelTest = null)
  }

  private fun mapAudioDeviceType(type: Int): Pair<String, Boolean> {
    return when {
      type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP" to true
      type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO (Call)" to true
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        type == AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Audio Headset" to true
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Audio Speaker" to true
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        type == AudioDeviceInfo.TYPE_BLE_BROADCAST -> "LE Audio Auracast" to true
      type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker" to false
      type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone Earpiece" to false
      type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones" to false
      type == AudioDeviceInfo.TYPE_USB_DEVICE ||
        type == AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio" to false
      else -> "Audio Output ($type)" to false
    }
  }

  fun release() {
    stopChannelTest()
    scope.cancel()
    if (receiverRegistered) {
      try {
        appContext.unregisterReceiver(audioReceiver)
      } catch (_: Exception) {
      }
      receiverRegistered = false
    }
    if (deviceCallbackRegistered && audioManager != null) {
      try {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
      } catch (_: Exception) {
      }
      deviceCallbackRegistered = false
    }
  }

  companion object {
    private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
  }
}
